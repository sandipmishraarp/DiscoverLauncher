package com.aresourcepool.discoverlauncher.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.aresourcepool.discoverlauncher.BuildConfig
import com.aresourcepool.discoverlauncher.data.api.ApkApiService
import com.aresourcepool.discoverlauncher.data.api.ApkDto
import com.aresourcepool.discoverlauncher.domain.model.ApkItem
import com.aresourcepool.discoverlauncher.network.RelaxedSslClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.UnknownHostException

/**
 * Single source of truth for APK list.
 * Fetches from API: GET {baseUrl}apk-list (wrapped response with type, message, data).
 */
class ApkRepository(private val context: Context) {

    private val api: ApkApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.APK_STORE_BASE_URL)
            .client(RelaxedSslClient.okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApkApiService::class.java)
    }

    private val packageManager: PackageManager get() = context.packageManager

    suspend fun fetchApkList(): Result<List<ApkItem>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getApkList()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    ApiException("HTTP ${response.code()}: ${response.message()}")
                )
            }
            val body = response.body()
            val dtos = body?.data ?: emptyList()
            val items = dtos.mapNotNull { dto -> dto.toApkItem(packageManager) }
            Result.success(items)
        } catch (e: UnknownHostException) {
            Result.failure(NetworkException("No internet or host unreachable", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getInstalledVersionCode(packageName: String): Long? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun ApkDto.toApkItem(pm: PackageManager): ApkItem? {
        val pkg = packageNameOrAppId()
        if (pkg.isBlank()) return null
        val latest = latestVersionCode ?: currentVersionCode ?: 0L
        val installed = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(pkg, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).versionCode.toLong()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return ApkItem(
            packageName = pkg,
            appName = appName ?: pkg,
            currentVersionCode = currentVersionCode ?: 0L,
            latestVersionCode = latest,
            versionName = versionName ?: "",
            apkDownloadUrl = apkDownloadUrl ?: "",
            apkSize = apkSize ?: 0L,
            changelog = changelog ?: "",
            iconUrl = iconUrl,
            playStoreUrl = playStoreUrl,
            installedVersionCode = installed,
            developerName = developerName ?: "",
            rating = rating ?: 0f,
            userCount = userCount ?: ""
        )
    }
}

class ApiException(message: String) : Exception(message)
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
