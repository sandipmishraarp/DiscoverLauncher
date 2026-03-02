package com.aresourcepool.discoverlauncher.ui.home

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aresourcepool.discoverlauncher.domain.model.ApkAction
import com.aresourcepool.discoverlauncher.domain.model.ApkItem
import com.aresourcepool.discoverlauncher.service.DownloadReceiver
import com.aresourcepool.discoverlauncher.ui.theme.GreenBanner
import com.aresourcepool.discoverlauncher.ui.theme.GreenPrimary
import com.aresourcepool.discoverlauncher.util.ApkInstallHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()

    val downloadReceiver = remember {
        DownloadReceiver.register(context) { status, packageName, progress, max, messageOrPath ->
            viewModel.onDownloadEvent(status, packageName, progress, max, messageOrPath)
        }
    }
    DisposableEffect(Unit) {
        onDispose { context.unregisterReceiver(downloadReceiver) }
    }

    LaunchedEffect(Unit) {
        viewModel.loadApks(autoCheck = true)
    }

    LaunchedEffect(uiState.downloadProgress) {
        uiState.downloadProgress.forEach { (pkg, prog) ->
            when (prog.status) {
                DownloadReceiver.STATUS_COMPLETED -> {
                    prog.completedPath?.let { path ->
                        val file = java.io.File(path)
                        if (file.exists() && activity != null) {
                            if (ApkInstallHelper.installApkFromFile(activity, file)) {
                                onShowMessage("Opening installer… Tap Update to install.")
                            } else {
                                onShowMessage("Allow installing from this app in Settings.")
                            }
                        }
                        viewModel.clearDownloadState(pkg)
                    }
                }
                DownloadReceiver.STATUS_FAILED -> {
                    onShowMessage(prog.errorMessage ?: "Download failed")
                    viewModel.clearDownloadState(pkg)
                }
                DownloadReceiver.STATUS_CANCELLED -> viewModel.clearDownloadState(pkg)
                else -> { }
            }
        }
    }

    if (uiState.installPermissionMissing || !ApkInstallHelper.canInstallApk(context)) {
        LaunchedEffect(Unit) { viewModel.setInstallPermissionMissing(true) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(SnackbarHostState()) }
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = padding.calculateLeftPadding(layoutDirection),
                    top = 12.dp,
                    end = padding.calculateRightPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding()
                )
        ) {
            if (uiState.installPermissionMissing || !ApkInstallHelper.canInstallApk(context)) {
                PermissionBanner(
                    onOpenSettings = {
                        activity?.let { ApkInstallHelper.requestInstallPermission(it) }
                        viewModel.setInstallPermissionMissing(false)
                    }
                )
            }

            if (uiState.error != null) {
                ErrorBanner(
                    message = uiState.error!!,
                    onDismiss = { viewModel.clearError() }
                )
            }

            when {
                uiState.isLoading && uiState.apks.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GreenPrimary)
                    }
                }
                uiState.apks.isEmpty() && !uiState.isLoading -> {
                    EmptyStateContent(
                        hasError = uiState.error != null,
                        onRefresh = { viewModel.refresh() }
                    )
                }
                else -> {
                    var changelogApk by remember { mutableStateOf<ApkItem?>(null) }
                    val installedCount = uiState.apks.count { it.isInstalled }
                    val updatesCount = uiState.apks.count { it.hasUpdate }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            MyAppsHeader(
                                installedCount = installedCount,
                                updatesCount = updatesCount,
                                onRefresh = { viewModel.refresh() },
                                refreshing = uiState.isLoading,
                                onUpdateAll = {
                            if (!ApkInstallHelper.canInstallApk(context)) {
                                viewModel.setInstallPermissionMissing(true)
                                onShowMessage("Allow install permission first.")
                                return@MyAppsHeader
                            }
                                    viewModel.updateAll { apk -> viewModel.startDownload(apk) }
                                }
                            )

                            if (updatesCount > 0) {
                                UpdatesBanner(updatesCount = updatesCount)
                            }

                            PullToRefreshBox(
                                isRefreshing = uiState.isLoading,
                                onRefresh = { viewModel.refresh() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                items(uiState.apks) { apk ->
                                    ApkListCard(
                                        apk = apk,
                                        downloadProgress = uiState.downloadProgress[apk.packageName],
                                        onAction = { a ->
                                            when (a) {
                                                ApkAction.Update -> changelogApk = apk
                                                ApkAction.Install -> changelogApk = apk
                                                ApkAction.Open -> {
                                                    if (!ApkInstallHelper.openApp(context, apk.packageName)) {
                                                        onShowMessage("Could not open app")
                                                    }
                                                }
                                            }
                                        },
                                        onStartDownload = {
                                            if (!ApkInstallHelper.canInstallApk(context)) {
                                                viewModel.setInstallPermissionMissing(true)
                                                onShowMessage("Allow install permission first.")
                                                return@ApkListCard
                                            }
                                            viewModel.startDownload(apk)
                                        },
                                        onDetails = { changelogApk = apk }
                                    )
                                }
                            }
                            }
                        }

                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = GreenPrimary)
                            }
                        }
                    }

                    changelogApk?.let { apk ->
                        ChangelogDialog(
                                apk = apk,
                                onDismiss = { changelogApk = null },
                                onInstallOrUpdate = {
                                    changelogApk = null
                                    if (!ApkInstallHelper.canInstallApk(context)) {
                                        viewModel.setInstallPermissionMissing(true)
                                        onShowMessage("Allow install permission first.")
                                        return@ChangelogDialog
                                    }
                                    viewModel.startDownload(apk)
                                },
                                onOpen = {
                                    changelogApk = null
                                    ApkInstallHelper.openApp(context, apk.packageName)
                                }
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun MyAppsHeader(
    installedCount: Int,
    updatesCount: Int,
    onRefresh: () -> Unit,
    refreshing: Boolean = false,
    onUpdateAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = "My Apps",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$installedCount apps installed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Text(
                text = "$updatesCount updates available",
                style = MaterialTheme.typography.bodyMedium,
                color = GreenPrimary,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.widthIn(min = 120.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = GreenPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh list",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (refreshing) "Refreshing…" else "Refresh", maxLines = 1)
            }
            if (updatesCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onUpdateAll,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = androidx.compose.ui.graphics.Color.White)
                ) {
                    Text("Update All ($updatesCount)", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun UpdatesBanner(updatesCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GreenBanner)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = GreenPrimary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$updatesCount app${if (updatesCount == 1) "" else "s"} can be updated. Keep your apps up to date for the latest features and security improvements.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ApkListCard(
    apk: ApkItem,
    downloadProgress: DownloadProgress?,
    onAction: (ApkAction) -> Unit,
    onStartDownload: () -> Unit,
    onDetails: () -> Unit
) {
    val isDownloading = downloadProgress != null && downloadProgress.status == DownloadReceiver.STATUS_PROGRESS
    val hasUpdate = apk.hasUpdate

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = apk.action == ApkAction.Open) {
                if (apk.action == ApkAction.Open) onAction(ApkAction.Open)
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = apk.iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = apk.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StatusPill(hasUpdate = hasUpdate, isInstalled = apk.isInstalled)
                    }
                    if (apk.developerName.isNotBlank()) {
                        Text(
                            text = apk.developerName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (apk.apkSize > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(formatSize(apk.apkSize), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (apk.isInstalled) {
                        Text(
                            text = if (hasUpdate) "Installed: v${apk.installedVersionCode} → Latest: ${apk.versionName.ifEmpty { "v${apk.latestVersionCode}" }}"
                            else "Installed: ${apk.versionName.ifEmpty { "v${apk.installedVersionCode}" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (downloadProgress != null) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = GreenPrimary
                )
                Text("${downloadProgress.progress}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val buttonTextStyle = MaterialTheme.typography.labelSmall
                val compactPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                OutlinedButton(
                    onClick = onDetails,
                    modifier = Modifier.weight(1f),
                    contentPadding = compactPadding,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Details", style = buttonTextStyle, maxLines = 1)
                }
                if (apk.action == ApkAction.Install) {
                    Button(
                        onClick = {
                            if (apk.changelog.isNotBlank()) onAction(ApkAction.Install) else onStartDownload()
                        },
                        enabled = !isDownloading,
                        modifier = Modifier.weight(1f),
                        contentPadding = compactPadding,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = androidx.compose.ui.graphics.Color.White)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isDownloading) "Downloading…" else "Install",
                            style = buttonTextStyle,
                            maxLines = 1
                        )
                    }
                }
                if (hasUpdate) {
                    Button(
                        onClick = {
                            if (apk.changelog.isNotBlank()) onAction(ApkAction.Update) else onStartDownload()
                        },
                        enabled = !isDownloading,
                        modifier = Modifier.weight(1f),
                        contentPadding = compactPadding,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = androidx.compose.ui.graphics.Color.White)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isDownloading) "Downloading…" else "Update",
                            style = buttonTextStyle,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(hasUpdate: Boolean, isInstalled: Boolean = true) {
    val (bg, label) = when {
        hasUpdate -> GreenPrimary to "Update Available"
        !isInstalled -> MaterialTheme.colorScheme.surfaceVariant to "Not installed"
        else -> MaterialTheme.colorScheme.surfaceVariant to "Up to date"
    }
    val textColor = if (hasUpdate) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun PermissionBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Allow this app to install APKs: Settings → Apps → DiscoverLauncher → Install unknown apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.widthIn(min = 120.dp)
            ) {
                Text("Open settings", maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyStateContent(
    hasError: Boolean,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (hasError) "Could not load apps" else "No apps available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (hasError) "Check your connection and try again." else "No updates available right now. Pull down or tap Refresh to check again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRefresh,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onDismiss, modifier = Modifier.widthIn(min = 88.dp)) {
                Text("Dismiss", maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChangelogDialog(
    apk: ApkItem,
    onDismiss: () -> Unit,
    onInstallOrUpdate: () -> Unit,
    onOpen: () -> Unit
) {
    val (confirmText, onConfirmClick) = when {
        apk.hasUpdate -> "Update" to onInstallOrUpdate
        !apk.isInstalled -> "Install" to onInstallOrUpdate
        else -> "Open" to onOpen
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's New — ${apk.appName}") },
        text = {
            Column {
                Text("Version ${apk.versionName.ifEmpty { apk.latestVersionCode.toString() }}", style = MaterialTheme.typography.labelMedium, color = GreenPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = apk.changelog.ifBlank { "No changelog provided." }, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(onClick = onConfirmClick, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                Text(confirmText)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
