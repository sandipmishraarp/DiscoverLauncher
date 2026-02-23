package com.aresourcepool.discoverlauncher.data.api

import com.google.gson.annotations.SerializedName

/**
 * Wrapper for APK list API response: { "type", "message", "data": [ ... ] }.
 */
data class ApkListResponse(
    @SerializedName("type") val type: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<ApkDto>? = null
)
