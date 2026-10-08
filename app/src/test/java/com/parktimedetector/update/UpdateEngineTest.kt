package com.parktimedetector.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateEngineTest {

    @Test
    fun testVersionComparator_basicIncrements() {
        // v2 is newer than v1 -> returns > 0
        assertTrue(VersionComparator.compare("1.0", "1.1") > 0)
        assertTrue(VersionComparator.compare("1.0.0", "1.0.1") > 0)
        assertTrue(VersionComparator.compare("1.0", "2.0") > 0)
        assertTrue(VersionComparator.compare("1.0.9", "1.0.10") > 0)
        assertTrue(VersionComparator.compare("1.0.2.14", "1.0.2.15") > 0)
    }

    @Test
    fun testVersionComparator_prefixesAndSuffixes() {
        // "v" prefix stripped
        assertTrue(VersionComparator.compare("v1.0.0", "v1.0.1") > 0)
        assertTrue(VersionComparator.compare("V1.0", "1.1") > 0)
        assertEquals(0, VersionComparator.compare("v1.0.0", "1.0.0"))

        // Suffixes stripped
        assertEquals(0, VersionComparator.compare("1.0.0-beta", "1.0.0-release"))
        assertTrue(VersionComparator.compare("1.0.0-beta", "1.0.1-rc1") > 0)
    }

    @Test
    fun testVersionComparator_equalAndOlderVersions() {
        assertEquals(0, VersionComparator.compare("1.0.0", "1.0.0"))
        assertEquals(0, VersionComparator.compare("1.0", "1.0.0"))

        // v2 is older than v1 -> returns < 0
        assertTrue(VersionComparator.compare("1.1", "1.0") < 0)
        assertTrue(VersionComparator.compare("2.0.0", "1.9.99") < 0)
        assertTrue(VersionComparator.compare("1.0.10", "1.0.9") < 0)
    }

    @Test
    fun testIsUpdateAvailable_versionCodePriority() {
        // If target versionCode is higher, update available regardless of string
        assertTrue(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0",
                currentCode = 1,
                targetVersion = "1.0",
                targetCode = 2
            )
        )

        // If target versionCode is equal, version string breaks the tie
        assertTrue(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0",
                currentCode = 2,
                targetVersion = "1.1",
                targetCode = 2
            )
        )

        assertFalse(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0",
                currentCode = 2,
                targetVersion = "1.0",
                targetCode = 2
            )
        )

        // If target versionCode is lower, it's a downgrade
        assertFalse(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0",
                currentCode = 5,
                targetVersion = "2.0",
                targetCode = 3
            )
        )
    }

    @Test
    fun testIsUpdateAvailable_fallbackToSemver() {
        // When versionCode is null or 0, fallback to version string
        assertTrue(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0.0",
                currentCode = 1,
                targetVersion = "1.0.1",
                targetCode = null
            )
        )

        assertFalse(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0.1",
                currentCode = 1,
                targetVersion = "1.0.0",
                targetCode = null
            )
        )

        assertFalse(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0.0",
                currentCode = 1,
                targetVersion = "1.0.0",
                targetCode = null
            )
        )
    }

    @Test
    fun testAppUpdateInfo_fromJson() {
        val json = JSONObject().apply {
            put("versionName", "1.2.0")
            put("versionCode", 5)
            put("title", "New Parking Features")
            put("changelog", "Added background updates and notifications")
            put("downloadUrl", "https://example.com/parking-1.2.0.apk")
            put("fileName", "parking-1.2.0.apk")
            put("fileSizeBytes", 12345678L)
            put("isMandatory", true)
            put("sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        }

        val info = AppUpdateInfo.fromJson(json)
        assertEquals("1.2.0", info.versionName)
        assertEquals(5, info.versionCode)
        assertEquals("New Parking Features", info.title)
        assertEquals("Added background updates and notifications", info.changelog)
        assertEquals("https://example.com/parking-1.2.0.apk", info.downloadUrl)
        assertEquals("parking-1.2.0.apk", info.fileName)
        assertEquals(12345678L, info.fileSizeBytes)
        assertTrue(info.isMandatory)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", info.sha256)
    }

    @Test
    fun testAppUpdateInfo_fromGitHubReleaseJson_prefersReleaseApk() {
        val githubJson = JSONObject().apply {
            put("tag_name", "v1.1.0")
            put("name", "Release v1.1.0")
            put("body", "- Enhanced timer\n- Added update engine")
            put("published_at", "2026-10-07T08:00:00Z")

            val assets = JSONArray().apply {
                // Debug APK first
                put(
                    JSONObject().apply {
                        put("name", "ParkingTimeDetector-v1.1.0-debug.apk")
                        put("size", 21000000L)
                        put("browser_download_url", "https://github.com/icecore2/releases/download/v1.1.0/ParkingTimeDetector-v1.1.0-debug.apk")
                    }
                )
                // Release APK second
                put(
                    JSONObject().apply {
                        put("name", "ParkingTimeDetector-v1.1.0.apk")
                        put("size", 20500000L)
                        put("browser_download_url", "https://github.com/icecore2/releases/download/v1.1.0/ParkingTimeDetector-v1.1.0.apk")
                    }
                )
            }
            put("assets", assets)
        }

        val info = AppUpdateInfo.fromGitHubReleaseJson(githubJson)
        assertNotNull(info)
        info!!
        assertEquals("v1.1.0", info.versionName)
        assertEquals("Release v1.1.0", info.title)
        assertTrue(info.changelog.contains("update engine"))
        // Release APK should be preferred over debug APK
        assertEquals("ParkingTimeDetector-v1.1.0.apk", info.fileName)
        assertEquals("https://github.com/icecore2/releases/download/v1.1.0/ParkingTimeDetector-v1.1.0.apk", info.downloadUrl)
        assertEquals(20500000L, info.fileSizeBytes)
    }

    @Test
    fun testAppUpdateInfo_fromGitHubReleaseJson_noApkAssetReturnsNull() {
        val githubJson = JSONObject().apply {
            put("tag_name", "v1.0.0")
            put("name", "Source only")
            put("assets", JSONArray())
        }

        val info = AppUpdateInfo.fromGitHubReleaseJson(githubJson)
        assertNull(info)
    }

    @Test
    fun testIsUpdateAvailable_whenTargetIsAlreadyInstalled_returnsFalse() {
        // Target version equals currently installed version -> must return false (no update card/badge)
        assertFalse(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0",
                currentCode = 1,
                targetVersion = "1.0",
                targetCode = 1
            )
        )

        // Target version with prefix equals current
        assertFalse(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0",
                currentCode = 1,
                targetVersion = "v1.0",
                targetCode = 1
            )
        )

        // Target version is older than currently installed (downgrade scenario)
        assertFalse(
            VersionComparator.isUpdateAvailable(
                currentVersion = "2.0.0",
                currentCode = 20,
                targetVersion = "1.9.5",
                targetCode = 19
            )
        )

        // Target versionCode is lower even if versionName string might look higher
        assertFalse(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0",
                currentCode = 10,
                targetVersion = "2.0",
                targetCode = 9
            )
        )
    }

    @Test
    fun testIsUpdateAvailable_whenTargetIsStrictlyNewer_returnsTrue() {
        // Higher versionCode
        assertTrue(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0",
                currentCode = 1,
                targetVersion = "1.0",
                targetCode = 2
            )
        )

        // Same versionCode, newer semantic version
        assertTrue(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0.0",
                currentCode = 5,
                targetVersion = "1.0.1",
                targetCode = 5
            )
        )

        // Newer version without versionCode
        assertTrue(
            VersionComparator.isUpdateAvailable(
                currentVersion = "1.0",
                currentCode = 1,
                targetVersion = "1.1",
                targetCode = null
            )
        )
    }

    @Test
    fun testPushPayload_triggersClearanceWhenAlreadyInstalled() {
        // Simulate push update JSON payload
        val pushJson = JSONObject().apply {
            put("versionName", "1.0")
            put("versionCode", 1)
            put("title", "Push Update")
            put("changelog", "Bug fixes")
            put("downloadUrl", "https://example.com/update-1.0.apk")
            put("fileName", "update-1.0.apk")
        }

        val pushInfo = AppUpdateInfo.fromJson(pushJson)

        // When current installed is 1.0 (code 1), push update should NOT be active
        val shouldShowCard = VersionComparator.isUpdateAvailable(
            currentVersion = "1.0",
            currentCode = 1,
            targetVersion = pushInfo.versionName,
            targetCode = pushInfo.versionCode
        )

        // Verifies the card/badge will NOT show when target version is already installed
        assertFalse("Update card must not show if version is already installed", shouldShowCard)
    }

    @Test
    fun testPushPayload_activatesWhenNewerVersionPushed() {
        val pushJson = JSONObject().apply {
            put("versionName", "1.1.0")
            put("versionCode", 2)
            put("title", "New Release")
            put("changelog", "Added background parking timer updates")
            put("downloadUrl", "https://example.com/update-1.1.0.apk")
            put("fileName", "update-1.1.0.apk")
        }

        val pushInfo = AppUpdateInfo.fromJson(pushJson)

        val shouldShowCard = VersionComparator.isUpdateAvailable(
            currentVersion = "1.0.0",
            currentCode = 1,
            targetVersion = pushInfo.versionName,
            targetCode = pushInfo.versionCode
        )

        assertTrue("Update card must show when newer version is pushed", shouldShowCard)
    }

    @Test
    fun testUpdateEngine_whenDisabled_reportsNoUpdateActive() {
        // Master flag must be disabled
        assertFalse("AppUpdateEngine must be disabled", AppUpdateEngine.IS_ENABLED)

        // isUpdateActiveAndNewer must return false when engine is disabled
        assertFalse(AppUpdateEngine.isUpdateActiveAndNewer())
    }
}
