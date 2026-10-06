package com.parktimedetector.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ParkingWidgetManager {

    private const val TAG = "ParkingWidgetManager"

    fun updateAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ParkingGlanceWidget().updateAll(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update Glance widgets", e)
            }
        }
    }
}
