package com.aresourcepool.discoverlauncher.data.api

import com.google.gson.annotations.SerializedName

/**
 * API response DTO for a single APK item from the backend.
 */
data class ApkDto(
    @SerializedName("appId") val appId: String? = null,
    @SerializedName("packageName") val packageName: String? = null,
    @SerializedName("appName") val appName: String? = null,
    @SerializedName("currentVersionCode") val currentVersionCode: Long? = null,
    @SerializedName("latestVersionCode") val latestVersionCode: Long? = null,
    @SerializedName("versionName") val versionName: String? = null,
    @SerializedName("apkDownloadUrl") val apkDownloadUrl: String? = null,
    @SerializedName("apkSize") val apkSize: Long? = null,
    @SerializedName("changelog") val changelog: String? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("playStoreUrl") val playStoreUrl: String? = null,
    @SerializedName("developerName") val developerName: String? = null,
    @SerializedName("rating") val rating: Float? = null,
    @SerializedName("userCount") val userCount: String? = null
) {
    fun packageNameOrAppId(): String = packageName ?: appId ?: ""
}
