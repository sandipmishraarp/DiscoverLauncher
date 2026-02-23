package com.aresourcepool.discoverlauncher.domain.model

/**
 * Domain model for an APK in the store.
 * [installedVersionCode] is null when app is not installed.
 */
data class ApkItem(
    val packageName: String,
    val appName: String,
    val currentVersionCode: Long,
    val latestVersionCode: Long,
    val versionName: String,
    val apkDownloadUrl: String,
    val apkSize: Long,
    val changelog: String,
    val iconUrl: String?,
    val playStoreUrl: String?,
    val installedVersionCode: Long?,
    val developerName: String = "",
    val rating: Float = 0f,
    val userCount: String = ""
) {
    val action: ApkAction
        get() = when {
            installedVersionCode == null -> ApkAction.Install
            latestVersionCode > installedVersionCode -> ApkAction.Update
            else -> ApkAction.Open
        }

    val isInstalled: Boolean get() = installedVersionCode != null
    val hasUpdate: Boolean get() = (installedVersionCode != null && latestVersionCode > installedVersionCode)
}

enum class ApkAction {
    Install,
    Update,
    Open
}
