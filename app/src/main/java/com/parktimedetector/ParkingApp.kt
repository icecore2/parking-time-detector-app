package com.parktimedetector

import android.app.Application
import com.parktimedetector.data.ParkingDatabase
import com.parktimedetector.notification.NotificationHelper

class ParkingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        ParkingDatabase.getDatabase(this)
    }
}
