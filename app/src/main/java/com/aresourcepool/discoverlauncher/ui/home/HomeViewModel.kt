package com.aresourcepool.discoverlauncher.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aresourcepool.discoverlauncher.data.repository.ApiException
import com.aresourcepool.discoverlauncher.data.repository.ApkRepository
import com.aresourcepool.discoverlauncher.data.repository.NetworkException
import com.aresourcepool.discoverlauncher.domain.model.ApkItem
import com.aresourcepool.discoverlauncher.service.ACTION_START_DOWNLOAD
import com.aresourcepool.discoverlauncher.service.ApkDownloadService
import com.aresourcepool.discoverlauncher.service.DownloadReceiver
import com.aresourcepool.discoverlauncher.service.EXTRA_APK_FILE_NAME
import com.aresourcepool.discoverlauncher.service.EXTRA_DOWNLOAD_URL
import com.aresourcepool.discoverlauncher.service.EXTRA_PACKAGE_NAME
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val apks: List<ApkItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadProgress: Map<String, DownloadProgress> = emptyMap(),
    val installPermissionMissing: Boolean = false
)

data class DownloadProgress(
    val progress: Int,
    val max: Int,
    val status: String,
    val completedPath: String? = null,
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ApkRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadApks(autoCheck: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.fetchApkList()
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(
                        apks = list,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { e ->
                    val message = when (e) {
                        is NetworkException -> "Network error: ${e.message}"
                        is ApiException -> "Server error: ${e.message}"
                        else -> e.message ?: "Failed to load apps"
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = message
                    )
                }
        }
    }

    fun refresh() = loadApks(autoCheck = false)

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setInstallPermissionMissing(value: Boolean) {
        _uiState.value = _uiState.value.copy(installPermissionMissing = value)
    }

    fun startDownload(apk: ApkItem) {
        val app = getApplication<Application>()
        val intent = Intent(app, ApkDownloadService::class.java).apply {
            action = ACTION_START_DOWNLOAD
            putExtra(EXTRA_DOWNLOAD_URL, apk.apkDownloadUrl)
            putExtra(EXTRA_PACKAGE_NAME, apk.packageName)
            putExtra(EXTRA_APK_FILE_NAME, "${apk.packageName.replace(".", "_")}_update.apk")
        }
        app.startForegroundService(intent)
        _uiState.value = _uiState.value.copy(
            downloadProgress = _uiState.value.downloadProgress + (apk.packageName to DownloadProgress(0, 100, DownloadReceiver.STATUS_PROGRESS))
        )
    }

    fun onDownloadEvent(status: String, packageName: String, progress: Int, max: Int, messageOrPath: String?) {
        val current = _uiState.value.downloadProgress
        when (status) {
            DownloadReceiver.STATUS_PROGRESS -> {
                _uiState.value = _uiState.value.copy(
                    downloadProgress = current + (packageName to DownloadProgress(progress, max, status))
                )
            }
            DownloadReceiver.STATUS_COMPLETED -> {
                _uiState.value = _uiState.value.copy(
                    downloadProgress = current + (packageName to DownloadProgress(100, 100, status, completedPath = messageOrPath))
                )
            }
            DownloadReceiver.STATUS_FAILED, DownloadReceiver.STATUS_CANCELLED -> {
                _uiState.value = _uiState.value.copy(
                    downloadProgress = current + (packageName to DownloadProgress(progress, max, status, errorMessage = messageOrPath))
                )
            }
        }
    }

    fun clearDownloadState(packageName: String) {
        _uiState.value = _uiState.value.copy(
            downloadProgress = _uiState.value.downloadProgress - packageName
        )
    }

    /** Start download for all apps that have updates (one after another or in parallel as needed). */
    fun updateAll(onEach: (ApkItem) -> Unit) {
        _uiState.value.apks.filter { it.hasUpdate }.forEach(onEach)
    }
}
