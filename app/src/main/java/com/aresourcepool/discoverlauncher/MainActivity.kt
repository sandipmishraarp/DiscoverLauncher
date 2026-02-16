package com.aresourcepool.discoverlauncher

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.aresourcepool.discoverlauncher.ui.theme.DiscoverLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiscoverLauncherTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    JustTipUpdateScreen(
                        modifier = Modifier.padding(innerPadding),
                        onShowMessage = { msg ->
                            lifecycleScope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun JustTipUpdateScreen(
    modifier: Modifier = Modifier,
    onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()

    var apkUrl by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var installPermissionMissing by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (activity == null) {
            onShowMessage("Cannot install: activity not available")
            return@rememberLauncherForActivityResult
        }
        if (!JustTipUpdateHelper.canInstallApk(context)) {
            installPermissionMissing = true
            onShowMessage("Allow \"Install unknown apps\" for this app in Settings first.")
            return@rememberLauncherForActivityResult
        }
        if (JustTipUpdateHelper.installApk(activity, uri)) {
            onShowMessage("Opening installer… Tap Update to install.")
        } else {
            onShowMessage("Open Settings and allow installing from this app.")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Update JustTip",
            style = MaterialTheme.typography.headlineMedium
        )

        val installed = JustTipUpdateHelper.isJustTipInstalled(context)
        val versionName = JustTipUpdateHelper.getJustTipVersionName(context)
        val versionCode = JustTipUpdateHelper.getJustTipVersionCode(context)
        Text(
            text = if (installed) "JustTip installed: ${versionName ?: "?"} (versionCode $versionCode)" else "JustTip is not installed",
            style = MaterialTheme.typography.bodyMedium,
            color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        if (installed && versionCode != null) {
            Text(
                text = "Your new APK must have versionCode > $versionCode and be signed with the same keystore, or you'll see \"App not installed\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (installPermissionMissing || !JustTipUpdateHelper.canInstallApk(context)) {
            Text(
                "Allow this app to install APKs: Settings → Apps → DiscoverLauncher → Install unknown apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            if (activity != null) {
                Button(
                    onClick = {
                        JustTipUpdateHelper.requestInstallPermission(activity)
                        installPermissionMissing = false
                    }
                ) {
                    Text("Open install permission settings")
                }
            }
        }

        Spacer(modifier = Modifier.padding(8.dp))

        OutlinedTextField(
            value = apkUrl,
            onValueChange = { apkUrl = it },
            label = { Text("JustTip APK URL") },
            placeholder = { Text("https://yourserver.com/justtip.apk") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Button(
            onClick = {
                if (apkUrl.isBlank()) {
                    onShowMessage("Enter an APK URL")
                    return@Button
                }
                if (activity == null) return@Button
                if (!JustTipUpdateHelper.canInstallApk(context)) {
                    installPermissionMissing = true
                    onShowMessage("Allow install permission first.")
                    return@Button
                }
                isDownloading = true
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        JustTipUpdateHelper.downloadApkToCache(context, apkUrl.trim())
                    }
                    isDownloading = false
                    if (file != null && file.exists()) {
                        if (JustTipUpdateHelper.installApkFromFile(activity, file)) {
                            onShowMessage("Opening installer… Tap Update to install.")
                        } else {
                            onShowMessage("Open Settings and allow installing from this app.")
                        }
                    } else {
                        onShowMessage("Download failed or file is not a valid APK. If using Google Drive, download the APK in a browser first, then use \"Select APK file from device\".")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDownloading
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text(if (isDownloading) "Downloading…" else "Download & update JustTip")
        }

        Button(
            onClick = {
                filePickerLauncher.launch(arrayOf("application/vnd.android.package-archive"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select APK file from device")
        }

        Spacer(modifier = Modifier.padding(16.dp))
        Text(
            text = "If you see \"App not installed\": (1) Build JustTip with the SAME keystore used for the app already on the device. (2) In JustTip build.gradle set versionCode higher than the installed version (shown above).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun JustTipUpdateScreenPreview() {
    DiscoverLauncherTheme {
        JustTipUpdateScreen(modifier = Modifier.padding(24.dp))
    }
}
