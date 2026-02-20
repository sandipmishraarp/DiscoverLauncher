# DiscoverLauncher

In-app APK update and distribution app (Play-Store-like) for internal or enterprise use. It fetches a list of APKs from your backend, shows Update / Install / Open / Uninstall per app, and handles download and install with progress and “What’s New” changelog.

## Features

- **Home screen**: List/grid of APKs from your backend API
- **Per-app actions**: **Update** (when a newer version is available), **Install** (not installed), **Open** (installed and up to date), **Uninstall**
- **Download**: Background foreground service with progress and notification
- **Install**: Uses system installer (PackageInstaller / Intent); handles Android 8+ “Install unknown apps” permission
- **What’s New**: Changelog dialog before Update/Install
- **Auto-check**: Fetches APK list on launch; manual refresh in the app bar
- **Error handling**: Network and install errors surfaced in the UI

## Backend API

The app expects a **GET** request to your base URL + **`apks`** (e.g. `https://your-api.example.com/apks`) that returns a **JSON array** of APK items.

### Base URL

Set in **BuildConfig**:

- In `app/build.gradle.kts`, under `defaultConfig`:
  ```kotlin
  buildConfigField("String", "APK_STORE_BASE_URL", "\"https://your-api.example.com/\"")
  ```
- Ensure the base URL ends with a slash; the app appends `apks` to it.

### Response format

Each element of the array should look like this (all fields except `playStoreUrl` are required for full behavior):

```json
{
  "appId": "com.example.myapp",
  "packageName": "com.example.myapp",
  "appName": "My App",
  "currentVersionCode": 1,
  "latestVersionCode": 2,
  "versionName": "1.1.0",
  "apkDownloadUrl": "https://your-cdn.example.com/apps/myapp-v1.1.0.apk",
  "apkSize": 15728640,
  "changelog": "- Bug fixes\n- New feature X",
  "iconUrl": "https://your-cdn.example.com/icons/myapp.png",
  "playStoreUrl": "https://play.google.com/store/apps/details?id=com.example.myapp"
}
```

| Field              | Type   | Description |
|--------------------|--------|-------------|
| `appId` / `packageName` | string | Package name (e.g. `com.example.myapp`). Either is used to match installed app. |
| `appName`          | string | Display name |
| `currentVersionCode` | number | Deprecated / legacy; prefer `latestVersionCode` |
| `latestVersionCode` | number | Version code of the APK at `apkDownloadUrl` |
| `versionName`      | string | User-visible version (e.g. `1.1.0`) |
| `apkDownloadUrl`   | string | Direct URL to the APK file |
| `apkSize`          | number | Size in bytes (optional, for display) |
| `changelog`        | string | Shown in “What’s New” (optional) |
| `iconUrl`          | string | URL to app icon image (optional) |
| `playStoreUrl`     | string | Optional; e.g. Play Store link |

A minimal item can be:

```json
{
  "packageName": "com.aresourcepool.justtip",
  "appName": "JustTip",
  "latestVersionCode": 2,
  "versionName": "1.1",
  "apkDownloadUrl": "https://yourserver.com/justtip.apk",
  "apkSize": 0,
  "changelog": ""
}
```

## Android setup

1. **Install unknown apps**  
   On first install/update, allow **Install unknown apps** for DiscoverLauncher:  
   **Settings → Apps → DiscoverLauncher → Install unknown apps.**

2. **Package visibility (Android 11+)**  
   To show correct **Update / Open** state, add each distributable app’s package name under `<queries>` in `app/src/main/AndroidManifest.xml`:

   ```xml
   <queries>
     <package android:name="com.aresourcepool.justtip" />
     <package android:name="com.example.myapp" />
   </queries>
   ```

3. **Notifications (Android 13+)**  
   The app requests **POST_NOTIFICATIONS** so the download progress notification can be shown.

## Building and running

- Set `APK_STORE_BASE_URL` in `app/build.gradle.kts` to your API base URL.
- Build and run as usual; the home screen loads the APK list from `{baseUrl}apks` and shows Update/Install/Open/Uninstall per app.

## Architecture (overview)

- **Data**: `data/api` (DTOs, Retrofit), `data/repository` (ApkRepository).
- **Domain**: `domain/model` (ApkItem, ApkAction).
- **UI**: `ui/home` (HomeScreen, HomeViewModel), grid of cards with actions and changelog dialog.
- **Download**: `service/ApkDownloadService` (foreground service), `DownloadReceiver` for progress.
- **Install / Open / Uninstall**: `util/ApkInstallHelper` (install from file, open app, uninstall intent).

Updates and installs always go through the **system installer**; the user must confirm. Silent install is not supported on normal devices without root/MDM.
