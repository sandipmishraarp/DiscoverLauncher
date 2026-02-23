package com.aresourcepool.discoverlauncher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

const val DOWNLOAD_PROGRESS_ACTION = "com.aresourcepool.discoverlauncher.DOWNLOAD_PROGRESS"

object DownloadReceiver {
    const val KEY_STATUS = "status"
    const val KEY_PACKAGE = "package"
    const val KEY_PROGRESS = "progress"
    const val KEY_MAX = "max"
    const val KEY_MESSAGE_OR_PATH = "message_or_path"

    const val STATUS_PROGRESS = "progress"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_FAILED = "failed"
    const val STATUS_CANCELLED = "cancelled"

    fun intentFor(status: String, packageName: String, progress: Int, max: Int, messageOrPath: String?): Intent {
        return Intent(DOWNLOAD_PROGRESS_ACTION).apply {
            setPackage("com.aresourcepool.discoverlauncher")
            putExtra(KEY_STATUS, status)
            putExtra(KEY_PACKAGE, packageName)
            putExtra(KEY_PROGRESS, progress)
            putExtra(KEY_MAX, max)
            messageOrPath?.let { putExtra(KEY_MESSAGE_OR_PATH, it) }
        }
    }

    fun register(context: Context, onEvent: (status: String, packageName: String, progress: Int, max: Int, messageOrPath: String?) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != DOWNLOAD_PROGRESS_ACTION) return
                val status = intent.getStringExtra(KEY_STATUS) ?: return
                val pkg = intent.getStringExtra(KEY_PACKAGE) ?: ""
                val progress = intent.getIntExtra(KEY_PROGRESS, 0)
                val max = intent.getIntExtra(KEY_MAX, 100)
                val msg = intent.getStringExtra(KEY_MESSAGE_OR_PATH)
                onEvent(status, pkg, progress, max, msg)
            }
        }
        val flag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        context.registerReceiver(receiver, IntentFilter(DOWNLOAD_PROGRESS_ACTION), flag)
        return receiver
    }
}
