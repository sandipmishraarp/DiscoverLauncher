# DiscoverLauncher

## Updating JustTip from this app

This app can install or update the **JustTip** app (`com.aresourcepool.justtip`) when you have a new APK (e.g. after making changes to JustTip).

### Requirements

- The new JustTip APK must have the **same package name** (`com.aresourcepool.justtip`) and be signed with the **same signing key** as the already installed JustTip app. Then Android will show **Update** instead of **Install**.
- On first use, allow **Install unknown apps** for DiscoverLauncher: Settings → Apps → DiscoverLauncher → Install unknown apps.

### How to use

1. **Download & update**: Put your new JustTip APK on a URL (your server, Google Drive link, etc.), enter the URL in the app, and tap **Download & update JustTip**. The system installer will open; tap **Update**.
2. **Select APK file**: If the APK is already on the device (e.g. in Downloads), tap **Select APK file from device**, choose the APK, and tap **Update** in the system dialog.

### Building and signing JustTip updates

1. In your JustTip project, ensure `build.gradle` (or `build.gradle.kts`) has a **higher `versionCode`** than the installed app (e.g. 2, 3, …).
2. Build a release APK signed with your **existing** JustTip keystore:
   - **Android Studio**: Build → Generate Signed Bundle / APK → APK → use your existing keystore and key alias.
   - **Command line** (after building an unsigned release APK):
     ```bash
     jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore your-justtip-key.keystore app-release-unsigned.apk your-key-alias
     zipalign -v 4 app-release-unsigned.apk justtip-update.apk
     ```
3. Use that signed `justtip-update.apk` as the file or host it at a URL and use DiscoverLauncher to update as above.

**Note:** Truly **silent** install/update (no user tap) is not allowed on normal Android without root or device-owner (MDM) mode. The user must always confirm with **Update** in the system dialog.
