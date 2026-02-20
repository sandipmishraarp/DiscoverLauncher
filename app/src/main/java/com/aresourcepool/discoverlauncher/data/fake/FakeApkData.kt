package com.aresourcepool.discoverlauncher.data.fake

import com.aresourcepool.discoverlauncher.domain.model.ApkItem

/**
 * Static fake JSON-style data for UI development.
 * Replace with API call in [ApkRepository.fetchApkList] when backend is ready.
 */
object FakeApkData {

    fun getFakeApkList(installedVersionOverrides: Map<String, Long> = emptyMap()): List<ApkItem> {
        val items = listOf(
            ApkItem(
                packageName = "com.socialconnect.app",
                appName = "SocialConnect",
                currentVersionCode = 3,
                latestVersionCode = 4,
                versionName = "2.1.0",
                apkDownloadUrl = "https://example.com/socialconnect.apk",
                apkSize = 47_432_000L,
                changelog = "- New stories feature\n- Performance improvements",
                iconUrl = null,
                playStoreUrl = null,
                installedVersionCode = installedVersionOverrides["com.socialconnect.app"] ?: 3L,
                developerName = "SocialConnect Inc.",
                rating = 4.5f,
                userCount = "100M+"
            ),
            ApkItem(
                packageName = "com.musicstream.app",
                appName = "MusicStream",
                currentVersionCode = 5,
                latestVersionCode = 6,
                versionName = "3.2.1",
                apkDownloadUrl = "https://example.com/musicstream.apk",
                apkSize = 71_778_000L,
                changelog = "- Hi-Fi audio support\n- Bug fixes",
                iconUrl = null,
                playStoreUrl = null,
                installedVersionCode = installedVersionOverrides["com.musicstream.app"] ?: 5L,
                developerName = "MusicStream Ltd.",
                rating = 4.7f,
                userCount = "500M+"
            ),
            ApkItem(
                packageName = "com.fittrack.app",
                appName = "FitTrack",
                currentVersionCode = 8,
                latestVersionCode = 8,
                versionName = "3.1.5",
                apkDownloadUrl = "https://example.com/fittrack.apk",
                apkSize = 34_408_000L,
                changelog = "",
                iconUrl = null,
                playStoreUrl = null,
                installedVersionCode = installedVersionOverrides["com.fittrack.app"] ?: 8L,
                developerName = "FitTrack Studios",
                rating = 4.3f,
                userCount = "50M+"
            ),
            ApkItem(
                packageName = "com.newsreader.app",
                appName = "NewsReader",
                currentVersionCode = 2,
                latestVersionCode = 3,
                versionName = "1.5.0",
                apkDownloadUrl = "https://example.com/newsreader.apk",
                apkSize = 28_000_000L,
                changelog = "- Dark mode\n- Offline reading",
                iconUrl = null,
                playStoreUrl = null,
                installedVersionCode = installedVersionOverrides["com.newsreader.app"] ?: 2L,
                developerName = "NewsReader Co.",
                rating = 4.4f,
                userCount = "10M+"
            ),
            ApkItem(
                packageName = "com.photoedit.app",
                appName = "PhotoEdit",
                currentVersionCode = 4,
                latestVersionCode = 5,
                versionName = "2.0.0",
                apkDownloadUrl = "https://example.com/photoedit.apk",
                apkSize = 52_000_000L,
                changelog = "- New filters\n- Export in HD",
                iconUrl = null,
                playStoreUrl = null,
                installedVersionCode = installedVersionOverrides["com.photoedit.app"] ?: 4L,
                developerName = "PhotoEdit Labs",
                rating = 4.6f,
                userCount = "80M+"
            )
        )
        return items
    }
}
