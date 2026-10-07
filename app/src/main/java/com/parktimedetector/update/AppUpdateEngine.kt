package com.parktimedetector.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.parktimedetector.BuildConfig
import com.parktimedetector.data.AppLogger
import com.parktimedetector.notification.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed class UpdateState {
    object Idle : UpdateState()
    data class Checking(val source: String = "GitHub Releases") : UpdateState()
    data class Available(val updateInfo: AppUpdateInfo) : UpdateState()
    data class Downloading(
        val updateInfo: AppUpdateInfo,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateState()
    data class ReadyToInstall(
        val updateInfo: AppUpdateInfo,
        val apkFile: File
    ) : UpdateState()
    data class UpToDate(val latestVersion: String) : UpdateState()
    data class Error(val message: String, val throwable: Throwable? = null) : UpdateState()
}

/**
 * App Update Engine that receives push updates (via broadcast/intent or remote payload),
 * queries repository releases for new APKs, manages downloading with progress tracking,
 * and launches the Android package installer.
 */
object AppUpdateEngine {

    private const val TAG = "AppUpdateEngine"
    const val DEFAULT_REPO = "icecore2/parking-time-detector-app"

    private val scope = CoroutineScope(Dispatchers.IO)
    private var downloadJob: Job? = null
    private var activeCall: Call? = null

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Checks repository releases for an available update.
     * When [silent] is true, errors are not surfaced aggressively, and notifications are only shown
     * if a new update is found.
     */
    fun checkForUpdates(
        context: Context,
        repo: String = DEFAULT_REPO,
        silent: Boolean = false
    ) {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) {
            Log.d(TAG, "Update check skipped: already busy with state ${_updateState.value}")
            return
        }

        _updateState.value = UpdateState.Checking("GitHub ($repo)")
        AppLogger.info(context, TAG, "Checking for updates from repo: $repo")

        scope.launch {
            try {
                val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "ParkingTimeDetector-Updater/${BuildConfig.VERSION_NAME}")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    val errorMsg = "Update check failed (HTTP $code)"
                    AppLogger.warn(context, TAG, errorMsg)
                    if (!silent) {
                        _updateState.value = UpdateState.Error(errorMsg)
                    } else {
                        _updateState.value = UpdateState.Idle
                    }
                    return@launch
                }

                val bodyStr = response.body?.string() ?: ""
                response.close()

                val json = JSONObject(bodyStr)
                val updateInfo = AppUpdateInfo.fromGitHubReleaseJson(json)

                if (updateInfo == null) {
                    val errorMsg = "No installable APK found in latest release"
                    AppLogger.warn(context, TAG, errorMsg)
                    if (!silent) {
                        _updateState.value = UpdateState.Error(errorMsg)
                    } else {
                        _updateState.value = UpdateState.Idle
                    }
                    return@launch
                }

                val isNewer = VersionComparator.isUpdateAvailable(
                    currentVersion = BuildConfig.VERSION_NAME,
                    currentCode = BuildConfig.VERSION_CODE,
                    targetVersion = updateInfo.versionName,
                    targetCode = updateInfo.versionCode
                )

                if (isNewer) {
                    AppLogger.info(
                        context,
                        TAG,
                        "Found new version: ${updateInfo.versionName} (current: ${BuildConfig.VERSION_NAME})"
                    )
                    _updateState.value = UpdateState.Available(updateInfo)
                    NotificationHelper.showUpdateAvailableNotification(context, updateInfo)
                } else {
                    AppLogger.info(
                        context,
                        TAG,
                        "App is up to date: ${BuildConfig.VERSION_NAME} >= ${updateInfo.versionName}"
                    )
                    _updateState.value = UpdateState.UpToDate(updateInfo.versionName)
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                val message = "Error checking for updates: ${e.localizedMessage ?: e.javaClass.simpleName}"
                AppLogger.error(context, TAG, message)
                if (!silent) {
                    _updateState.value = UpdateState.Error(message, e)
                } else {
                    _updateState.value = UpdateState.Idle
                }
            }
        }
    }

    /**
     * Ingests a push update payload received from a broadcast receiver, remote push, or simulator.
     */
    fun processPushUpdate(
        context: Context,
        updateInfo: AppUpdateInfo,
        autoDownload: Boolean = false
    ) {
        AppLogger.info(
            context,
            TAG,
            "Received push update: version=${updateInfo.versionName}, code=${updateInfo.versionCode}, autoDownload=$autoDownload"
        )

        val isNewer = VersionComparator.isUpdateAvailable(
            currentVersion = BuildConfig.VERSION_NAME,
            currentCode = BuildConfig.VERSION_CODE,
            targetVersion = updateInfo.versionName,
            targetCode = updateInfo.versionCode
        )

        if (!isNewer) {
            AppLogger.info(
                context,
                TAG,
                "Push update ignored: version ${updateInfo.versionName} is not newer than current ${BuildConfig.VERSION_NAME}"
            )
            return
        }

        _updateState.value = UpdateState.Available(updateInfo)
        NotificationHelper.showUpdateAvailableNotification(context, updateInfo)

        if (autoDownload || updateInfo.isMandatory) {
            AppLogger.info(context, TAG, "Initiating auto-download for pushed update v${updateInfo.versionName}")
            downloadUpdate(context, updateInfo)
        }
    }

    /**
     * Downloads the APK file specified in [updateInfo] with progress reporting.
     */
    fun downloadUpdate(context: Context, updateInfo: AppUpdateInfo) {
        if (updateInfo.downloadUrl.isBlank()) {
            _updateState.value = UpdateState.Error("Missing APK download URL")
            return
        }

        downloadJob?.cancel()
        activeCall?.cancel()

        _updateState.value = UpdateState.Downloading(
            updateInfo = updateInfo,
            progressPercent = 0,
            bytesDownloaded = 0,
            totalBytes = updateInfo.fileSizeBytes ?: -1L
        )

        NotificationHelper.showUpdateProgressNotification(context, 0, updateInfo)

        downloadJob = scope.launch {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, updateInfo.fileName)
            val tempFile = File(updatesDir, "${updateInfo.fileName}.tmp")

            // If target file already exists and is non-empty, verify
            if (targetFile.exists() && targetFile.length() > 0) {
                val matchesSize = updateInfo.fileSizeBytes == null || targetFile.length() == updateInfo.fileSizeBytes
                val matchesHash = updateInfo.sha256 == null || verifySha256(targetFile, updateInfo.sha256)
                if (matchesSize && matchesHash) {
                    AppLogger.info(context, TAG, "Using existing cached APK: ${targetFile.absolutePath}")
                    NotificationHelper.cancelUpdateNotification(context)
                    _updateState.value = UpdateState.ReadyToInstall(updateInfo, targetFile)
                    NotificationHelper.showUpdateReadyNotification(context, targetFile, updateInfo)
                    return@launch
                } else {
                    targetFile.delete()
                }
            }

            try {
                if (tempFile.exists()) tempFile.delete()

                val request = Request.Builder()
                    .url(updateInfo.downloadUrl)
                    .header("User-Agent", "ParkingTimeDetector-Updater/${BuildConfig.VERSION_NAME}")
                    .build()

                val call = httpClient.newCall(request)
                activeCall = call
                val response = call.execute()

                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    throw IllegalStateException("Download failed with HTTP $code")
                }

                val responseBody = response.body ?: throw IllegalStateException("Empty response body")
                val totalLength = if (responseBody.contentLength() > 0) {
                    responseBody.contentLength()
                } else {
                    updateInfo.fileSizeBytes ?: -1L
                }

                responseBody.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloaded = 0L
                        var lastProgressUpdate = 0L
                        var lastPercent = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            val percent = if (totalLength > 0) {
                                ((downloaded * 100) / totalLength).toInt().coerceIn(0, 100)
                            } else -1

                            val now = System.currentTimeMillis()
                            if (percent != lastPercent || now - lastProgressUpdate > 300) {
                                lastPercent = percent
                                lastProgressUpdate = now
                                _updateState.value = UpdateState.Downloading(
                                    updateInfo = updateInfo,
                                    progressPercent = percent,
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalLength
                                )
                                NotificationHelper.showUpdateProgressNotification(context, percent, updateInfo)
                            }
                        }
                        output.flush()
                    }
                }
                response.close()

                // Verify SHA-256 if present
                if (updateInfo.sha256 != null && !verifySha256(tempFile, updateInfo.sha256)) {
                    tempFile.delete()
                    throw IllegalStateException("Downloaded APK failed SHA-256 verification")
                }

                // Rename temp to target
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    // Fallback copy if rename fails
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                AppLogger.success(
                    context,
                    TAG,
                    "Successfully downloaded update ${updateInfo.versionName}: ${targetFile.length()} bytes"
                )

                NotificationHelper.cancelUpdateNotification(context)
                _updateState.value = UpdateState.ReadyToInstall(updateInfo, targetFile)
                NotificationHelper.showUpdateReadyNotification(context, targetFile, updateInfo)

            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                if (e is CancellationException) {
                    Log.d(TAG, "Download cancelled by user")
                    NotificationHelper.cancelUpdateNotification(context)
                    _updateState.value = UpdateState.Available(updateInfo)
                    return@launch
                }
                val msg = "Download failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
                AppLogger.error(context, TAG, msg)
                NotificationHelper.cancelUpdateNotification(context)
                _updateState.value = UpdateState.Error(msg, e)
            } finally {
                activeCall = null
            }
        }
    }

    /**
     * Cancels an in-progress download and restores state to Available or Idle.
     */
    fun cancelDownload(context: Context) {
        activeCall?.cancel()
        downloadJob?.cancel()
        activeCall = null
        downloadJob = null

        NotificationHelper.cancelUpdateNotification(context)
        val current = _updateState.value
        _updateState.value = if (current is UpdateState.Downloading) {
            UpdateState.Available(current.updateInfo)
        } else {
            UpdateState.Idle
        }
    }

    /**
     * Verifies permissions and triggers APK installation.
     */
    fun installUpdate(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            _updateState.value = UpdateState.Error("APK file not found on disk")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            AppLogger.info(context, TAG, "Requesting unknown sources permission for APK installation")
            openInstallPermissionSettings(context)
            return
        }

        try {
            val installIntent = getInstallIntent(context, apkFile)
            context.startActivity(installIntent)
            AppLogger.info(context, TAG, "Launched Android PackageInstaller for: ${apkFile.name}")
        } catch (e: Exception) {
            val errorMsg = "Failed to launch package installer: ${e.localizedMessage}"
            AppLogger.error(context, TAG, errorMsg)
            _updateState.value = UpdateState.Error(errorMsg, e)
        }
    }

    /**
     * Creates an [Intent.ACTION_VIEW] intent with FileProvider content URI to install an APK.
     */
    fun getInstallIntent(context: Context, apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Checks if the app has permission to install unknown apps.
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens system Settings screen to grant unknown apps installation permission.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Resets the update state back to Idle and clears notifications.
     */
    fun dismissUpdate(context: Context? = null) {
        context?.let { NotificationHelper.cancelUpdateNotification(it) }
        _updateState.value = UpdateState.Idle
    }

    private fun verifySha256(file: File, expectedHash: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            actual.equals(expectedHash.trim(), ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }
}
