package com.aresourcepool.discoverlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Helper to update the JustTip app (com.aresourcepool.justtip) with a new APK.
 * The new APK must be signed with the same key as the installed app for update to work.
 */
object JustTipUpdateHelper {

    const val JUSTTIP_PACKAGE = "com.aresourcepool.justtip"

    fun isJustTipInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(JUSTTIP_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Returns installed JustTip version name, or null if not installed.
     */
    fun getJustTipVersionName(context: Context): String? {
        return try {
            context.packageManager.getPackageInfo(JUSTTIP_PACKAGE, 0).versionName ?: "unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Returns installed JustTip versionCode (for update: new APK must be higher). Null if not installed.
     */
    fun getJustTipVersionCode(context: Context): Long? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(JUSTTIP_PACKAGE, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(JUSTTIP_PACKAGE, 0).versionCode.toLong()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Android 8+ requires the app to have "Install unknown apps" permission for our package.
     * Returns true if we can install; false if user must grant permission.
     */
    fun canInstallApk(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /**
     * Opens system settings so user can allow "Install unknown apps" for this app.
     * Call this when [canInstallApk] returns false.
     */
    fun requestInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        }
    }

    /**
     * Install an APK from a content URI (e.g. from file picker or from our cache).
     * User will see system "Install" / "Update" dialog — cannot be silent without root/device owner.
     */
    fun installApk(activity: Activity, apkUri: Uri): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            requestInstallPermission(activity)
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
        return true
    }

    /**
     * Install APK from a file (e.g. in cache dir). Uses FileProvider to get a content URI.
     */
    fun installApkFromFile(activity: Activity, apkFile: File): Boolean {
        val authority = "${activity.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(activity, authority, apkFile)
        return installApk(activity, uri)
    }

    /**
     * Converts a Google Drive share URL to a direct download URL.
     * Share URL like: https://drive.google.com/file/d/FILE_ID/view?usp=sharing
     * Direct: https://drive.google.com/uc?export=download&id=FILE_ID
     */
    fun toDirectDownloadUrl(url: String): String {
        val trimmed = url.trim()
        // Match: drive.google.com/file/d/ID/view  or  drive.google.com/open?id=ID
        val idFromFile = Pattern.compile("drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)").matcher(trimmed)
        if (idFromFile.find()) {
            return "https://drive.google.com/uc?export=download&id=${idFromFile.group(1)}"
        }
        val idFromOpen = Pattern.compile("drive\\.google\\.com/open\\?id=([a-zA-Z0-9_-]+)").matcher(trimmed)
        if (idFromOpen.find()) {
            return "https://drive.google.com/uc?export=download&id=${idFromOpen.group(1)}"
        }
        return trimmed
    }

    /** APK files are ZIP-based; first bytes are "PK" (0x50 0x4B). */
    private fun isLikelyApk(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return file.inputStream().use { it.read() == 0x50 && it.read() == 0x4B }
    }

    /**
     * Download APK from [url] into app cache and return the file, or null on failure.
     * Converts Google Drive share URLs to direct download. Run on a background thread.
     */
    fun downloadApkToCache(context: Context, url: String): File? {
        return try {
            val downloadUrl = toDirectDownloadUrl(url)
            var connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connect()
            // Follow redirects manually for older behavior
            var redirectCount = 0
            while (redirectCount < 5 && connection.responseCode in 301..308) {
                val location = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                connection = URL(location).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.connect()
                redirectCount++
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val dir = File(context.cacheDir, "apk").apply { mkdirs() }
            val file = File(dir, "justtip_update.apk")
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            if (!isLikelyApk(file)) {
                file.delete()
                return null // e.g. Google Drive returned HTML
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
