package com.aresourcepool.discoverlauncher.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aresourcepool.discoverlauncher.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

const val EXTRA_DOWNLOAD_URL = "download_url"
const val EXTRA_PACKAGE_NAME = "package_name"
const val EXTRA_APK_FILE_NAME = "apk_file_name"

const val ACTION_START_DOWNLOAD = "start_download"
const val ACTION_CANCEL_DOWNLOAD = "cancel_download"

const val NOTIFICATION_CHANNEL_ID = "apk_download_channel"
const val NOTIFICATION_ID = 1001

/**
 * Foreground service that downloads an APK and reports progress via broadcast.
 * Progress is sent with action [DOWNLOAD_PROGRESS_ACTION].
 */
class ApkDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_DOWNLOAD_URL)
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "app"
                val fileName = intent.getStringExtra(EXTRA_APK_FILE_NAME) ?: "update.apk"
                if (!url.isNullOrBlank()) {
                    startForeground(NOTIFICATION_ID, createProgressNotification(0, 100))
                    downloadJob = scope.launch {
                        runDownload(url, packageName, fileName)
                    }
                } else {
                    stopSelf()
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                downloadJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun runDownload(url: String, packageName: String, fileName: String) {
        val dir = File(cacheDir, "apk").apply { mkdirs() }
        val file = File(dir, fileName)
        try {
            var connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connect()
            var redirectCount = 0
            while (redirectCount < 5 && connection.responseCode in 301..308) {
                val location = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                connection = URL(location).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.requestMethod = "GET"
                connection.connect()
                redirectCount++
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                sendBroadcast(DownloadReceiver.intentFor(DownloadReceiver.STATUS_FAILED, packageName, 0, 100, "HTTP ${connection.responseCode}"))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            var readBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var read = input.read(buffer)
                    while (scope.isActive && read != -1) {
                        output.write(buffer, 0, read)
                        readBytes += read
                        if (totalBytes > 0) {
                            val progress = (100 * readBytes / totalBytes).toInt().coerceIn(0, 100)
                            sendBroadcast(DownloadReceiver.intentFor(DownloadReceiver.STATUS_PROGRESS, packageName, progress, 100, null))
                            updateNotification(progress, 100)
                        }
                        read = input.read(buffer)
                    }
                }
            }
            if (!scope.isActive) {
                file.delete()
                sendBroadcast(DownloadReceiver.intentFor(DownloadReceiver.STATUS_CANCELLED, packageName, 0, 100, null))
            } else if (isLikelyApk(file)) {
                sendBroadcast(DownloadReceiver.intentFor(DownloadReceiver.STATUS_COMPLETED, packageName, 100, 100, file.absolutePath))
                updateNotification(100, 100, "Download complete")
            } else {
                file.delete()
                sendBroadcast(DownloadReceiver.intentFor(DownloadReceiver.STATUS_FAILED, packageName, 0, 100, "Invalid APK file"))
            }
        } catch (e: Exception) {
            file.delete()
            sendBroadcast(DownloadReceiver.intentFor(DownloadReceiver.STATUS_FAILED, packageName, 0, 100, e.message ?: "Download failed"))
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun isLikelyApk(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return file.inputStream().use { it.read() == 0x50 && it.read() == 0x4B }
    }

    private fun createProgressNotification(progress: Int, max: Int, title: String = "Downloading APK"): android.app.Notification {
        createChannel()
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(max, progress, max == 0)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun updateNotification(progress: Int, max: Int, title: String = "Downloading APK") {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, createProgressNotification(progress, max, title))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "APK Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
