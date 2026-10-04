package com.parktimedetector.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parktimedetector.service.DetectionApprovalManager

class PendingDetectionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_APPROVE_DETECTION = "com.parktimedetector.action.APPROVE_DETECTION"
        const val ACTION_DISMISS_DETECTION = "com.parktimedetector.action.DISMISS_DETECTION"
        const val ACTION_APPROVE_STOP_DETECTION = "com.parktimedetector.action.APPROVE_STOP_DETECTION"
        const val ACTION_DISMISS_STOP_DETECTION = "com.parktimedetector.action.DISMISS_STOP_DETECTION"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_APPROVE_DETECTION -> {
                DetectionApprovalManager.approveCurrentDetection(context.applicationContext)
            }
            ACTION_DISMISS_DETECTION -> {
                DetectionApprovalManager.dismissCurrentDetection(context.applicationContext)
            }
            ACTION_APPROVE_STOP_DETECTION -> {
                DetectionApprovalManager.approveCurrentStopDetection(context.applicationContext)
            }
            ACTION_DISMISS_STOP_DETECTION -> {
                DetectionApprovalManager.dismissCurrentStopDetection(context.applicationContext)
            }
        }
    }
}
