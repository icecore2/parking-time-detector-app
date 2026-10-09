package com.parktimedetector

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.parktimedetector.data.ParkingDatabase
import com.parktimedetector.notification.NotificationHelper
import com.parktimedetector.service.DetectionApprovalManager

class ParkingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        ParkingDatabase.getDatabase(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var runningActivities = 0

            override fun onActivityStarted(activity: Activity) {
                runningActivities++
                DetectionApprovalManager.setAppInForeground(true)
            }

            override fun onActivityStopped(activity: Activity) {
                runningActivities = (runningActivities - 1).coerceAtLeast(0)
                if (runningActivities == 0) {
                    DetectionApprovalManager.setAppInForeground(false)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {
                DetectionApprovalManager.setAppInForeground(true)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
