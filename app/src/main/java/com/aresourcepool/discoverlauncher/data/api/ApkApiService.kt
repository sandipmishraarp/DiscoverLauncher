package com.aresourcepool.discoverlauncher.data.api

import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit API for fetching the list of available APKs.
 * Backend should return JSON array of [ApkDto].
 */
interface ApkApiService {

    @GET("apks")
    suspend fun getApkList(): Response<List<ApkDto>>
}
