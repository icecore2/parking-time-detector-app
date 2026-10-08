package com.parktimedetector.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.parktimedetector.data.AppLogger
import java.io.File

/**
 * BroadcastReceiver for handling push update triggers, background update checks,
 * and user actions from update notifications.
 *
 * Supported Actions:
 * - [ACTION_PUSH_UPDATE]: Ingests a new update pushed via broadcast extras or JSON payload.
 * - [ACTION_CHECK_UPDATE]: Triggers an on-demand update check against the repository.
 * - [ACTION_DOWNLOAD_UPDATE]: Triggers download of the current available update.
 * - [ACTION_INSTALL_UPDATE]: Triggers installation of the downloaded APK.
 * - [ACTION_DISMISS_UPDATE]: Dismisses the update notifications and resets engine state.
 */
class UpdatePushReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PUSH_UPDATE = "com.parktimedetector.action.PUSH_UPDATE"
        const val ACTION_CHECK_UPDATE = "com.parktimedetector.action.CHECK_UPDATE"
        const val ACTION_DOWNLOAD_UPDATE = "com.parktimedetector.action.DOWNLOAD_UPDATE"
        const val ACTION_INSTALL_UPDATE = "com.parktimedetector.action.INSTALL_UPDATE"
        const val ACTION_DISMISS_UPDATE = "com.parktimedetector.action.DISMISS_UPDATE"

        private const val TAG = "UpdatePushReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (!AppUpdateEngine.IS_ENABLED) {
            Log.d(TAG, "Update engine is disabled; ignoring update broadcast")
            return
        }

        val action = intent?.action ?: return
        Log.d(TAG, "Received update broadcast action: $action")
        AppLogger.info(context, TAG, "Received broadcast action: $action")

        when (action) {
            ACTION_PUSH_UPDATE -> {
                val extras = intent.extras
                val updateInfo = extras?.let { AppUpdateInfo.fromBundle(it) }
                if (updateInfo != null) {
                    val autoDownload = intent.getBooleanExtra("autoDownload", false) ||
                            intent.getBooleanExtra("auto_download", false)
                    AppUpdateEngine.processPushUpdate(
                        context = context.applicationContext,
                        updateInfo = updateInfo,
                        autoDownload = autoDownload
                    )
                } else {
                    // If no explicit payload was provided, perform a repository update check
                    val repo = intent.getStringExtra("repo") ?: AppUpdateEngine.DEFAULT_REPO
                    AppUpdateEngine.checkForUpdates(
                        context = context.applicationContext,
                        repo = repo,
                        silent = false
                    )
                }
            }

            ACTION_CHECK_UPDATE -> {
                val repo = intent.getStringExtra("repo") ?: AppUpdateEngine.DEFAULT_REPO
                val silent = intent.getBooleanExtra("silent", false)
                AppUpdateEngine.checkForUpdates(
                    context = context.applicationContext,
                    repo = repo,
                    silent = silent
                )
            }

            ACTION_DOWNLOAD_UPDATE -> {
                val extras = intent.extras
                val updateInfo = extras?.let { AppUpdateInfo.fromBundle(it) }
                if (updateInfo != null) {
                    AppUpdateEngine.downloadUpdate(context.applicationContext, updateInfo)
                } else {
                    val currentState = AppUpdateEngine.updateState.value
                    if (currentState is UpdateState.Available) {
                        AppUpdateEngine.downloadUpdate(context.applicationContext, currentState.updateInfo)
                    } else {
                        Log.w(TAG, "No available update found to download")
                    }
                }
            }

            ACTION_INSTALL_UPDATE -> {
                val apkPath = intent.getStringExtra("apkPath")
                if (!apkPath.isNullOrBlank()) {
                    AppUpdateEngine.installUpdate(context.applicationContext, File(apkPath))
                } else {
                    val currentState = AppUpdateEngine.updateState.value
                    if (currentState is UpdateState.ReadyToInstall) {
                        AppUpdateEngine.installUpdate(context.applicationContext, currentState.apkFile)
                    } else {
                        Log.w(TAG, "No downloaded APK ready to install")
                    }
                }
            }

            ACTION_DISMISS_UPDATE -> {
                AppUpdateEngine.dismissUpdate(context.applicationContext)
            }
        }
    }
}
