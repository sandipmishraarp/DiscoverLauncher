package com.aresourcepool.discoverlauncher.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Helper for installing, opening, and uninstalling any app by package name.
 * Handles Android 8+ "Install unknown apps" permission.
 */
object ApkInstallHelper {

    fun canInstallApk(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    fun requestInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        }
    }

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

    fun installApkFromFile(activity: Activity, apkFile: File): Boolean {
        val authority = "${activity.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(activity, authority, apkFile)
        return installApk(activity, uri)
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Opens the app if installed; returns false if not installed. */
    fun openApp(context: Context, packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Starts the system uninstall flow for the given package. */
    fun uninstallApp(activity: Activity, packageName: String) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$packageName")
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        activity.startActivity(intent)
    }
}
