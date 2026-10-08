package com.parktimedetector.update

import android.os.Bundle
import com.parktimedetector.BuildConfig
import org.json.JSONObject

/**
 * Metadata representing an application update payload received via
 * push broadcast or fetched from GitHub Releases API.
 */
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int? = null,
    val title: String = "",
    val changelog: String = "",
    val downloadUrl: String,
    val fileName: String = "ParkingTimeDetector-${versionName}.apk",
    val fileSizeBytes: Long? = null,
    val publishedAt: String? = null,
    val isMandatory: Boolean = false,
    val sha256: String? = null
) {
    companion object {

        fun fromJson(json: JSONObject): AppUpdateInfo {
            val version = json.optString("versionName").ifEmpty {
                json.optString("version", "1.0.0")
            }
            val downloadUrl = json.optString("downloadUrl").ifEmpty {
                json.optString("apkUrl", "")
            }
            val fileName = json.optString("fileName").ifEmpty {
                "ParkingTimeDetector-${version}.apk"
            }
            return AppUpdateInfo(
                versionName = version,
                versionCode = if (json.has("versionCode")) json.optInt("versionCode") else null,
                title = json.optString("title", "Parking Time Detector Update"),
                changelog = json.optString("changelog").ifEmpty {
                    json.optString("notes", "")
                },
                downloadUrl = downloadUrl,
                fileName = fileName,
                fileSizeBytes = if (json.has("fileSizeBytes")) json.optLong("fileSizeBytes") else null,
                publishedAt = json.optString("publishedAt").takeIf { it.isNotEmpty() },
                isMandatory = json.optBoolean("isMandatory", false) || json.optBoolean("mandatory", false),
                sha256 = json.optString("sha256").takeIf { it.isNotEmpty() }
            )
        }

        fun fromBundle(bundle: Bundle): AppUpdateInfo? {
            // Check for raw JSON payload extra
            val payload = bundle.getString("payload") ?: bundle.getString("update_json")
            if (!payload.isNullOrBlank()) {
                return try {
                    fromJson(JSONObject(payload))
                } catch (_: Exception) {
                    null
                }
            }

            val version = bundle.getString("version") ?: bundle.getString("versionName") ?: return null
            val downloadUrl = bundle.getString("downloadUrl") ?: bundle.getString("apkUrl") ?: ""
            val versionCode = if (bundle.containsKey("versionCode")) bundle.getInt("versionCode") else null
            val changelog = bundle.getString("changelog") ?: bundle.getString("notes") ?: ""
            val title = bundle.getString("title") ?: "Parking Time Detector Update"
            val fileName = bundle.getString("fileName") ?: "ParkingTimeDetector-$version.apk"
            val fileSizeBytes = if (bundle.containsKey("fileSizeBytes")) bundle.getLong("fileSizeBytes") else null
            val isMandatory = bundle.getBoolean("isMandatory", false) || bundle.getBoolean("mandatory", false)
            val sha256 = bundle.getString("sha256")

            return AppUpdateInfo(
                versionName = version,
                versionCode = versionCode,
                title = title,
                changelog = changelog,
                downloadUrl = downloadUrl,
                fileName = fileName,
                fileSizeBytes = fileSizeBytes,
                publishedAt = null,
                isMandatory = isMandatory,
                sha256 = sha256
            )
        }

        fun fromGitHubReleaseJson(
            json: JSONObject,
            preferDebug: Boolean = BuildConfig.DEBUG
        ): AppUpdateInfo? {
            val tagName = json.optString("tag_name")
            if (tagName.isEmpty()) return null

            val name = json.optString("name", tagName)
            val body = json.optString("body", "")
            val publishedAt = json.optString("published_at")
            val assets = json.optJSONArray("assets") ?: return null

            // Find APK asset matching current build variant (debug vs release)
            var selectedAsset: JSONObject? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    val isAssetDebug = assetName.contains("debug", ignoreCase = true)
                    if (preferDebug == isAssetDebug) {
                        selectedAsset = asset
                        break
                    }
                }
            }

            // Fallback: if preferred variant asset is not found, take any APK asset
            if (selectedAsset == null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        selectedAsset = asset
                        break
                    }
                }
            }

            val asset = selectedAsset ?: return null
            val downloadUrl = asset.optString("browser_download_url")
            val fileName = asset.optString("name")
            val size = asset.optLong("size", -1L)

            return AppUpdateInfo(
                versionName = tagName,
                versionCode = null,
                title = name,
                changelog = body,
                downloadUrl = downloadUrl,
                fileName = fileName,
                fileSizeBytes = if (size > 0) size else null,
                publishedAt = publishedAt.takeIf { it.isNotEmpty() },
                isMandatory = false,
                sha256 = null
            )
        }
    }
}
