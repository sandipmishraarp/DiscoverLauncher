package com.aresourcepool.discoverlauncher.data.api

import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit API for fetching the list of available APKs.
 * Base URL: http://65.1.252.120/justtip/v1/
 * Returns wrapped response: { "type", "message", "data": [ ApkDto ] }.
 */
interface ApkApiService {

    @GET("apk-list")
    suspend fun getApkList(): Response<ApkListResponse>
}
