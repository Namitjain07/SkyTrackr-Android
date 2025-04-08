package com.flight.flightq1

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.flight.flightq1.worker.FlightUpdateWorker
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

class FlightQApplication : Application() {

    companion object {
        // Worker names
        const val FLIGHT_UPDATE_WORK_NAME = "flight_data_refresh"
        
        // Notification channel IDs
        const val CHANNEL_FLIGHT_UPDATES = "flight_updates_channel"
        
        // Default preferences
        const val DEFAULT_UPDATE_FREQUENCY_HOURS = 24 // Default to daily updates
        const val PREF_UPDATE_FREQUENCY = "update_frequency_hours"
        const val PREF_FILE = "flight_app_prefs"
        
        // Singleton instance
        private lateinit var instance: FlightQApplication
        
        fun getInstance(): FlightQApplication {
            return instance
        }
    }
    
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize preferences
        prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE)
        
        // Create notification channels
        createNotificationChannels()
        
        Log.d("FlightQApplication", "Application starting, scheduling workers")
        
        // Schedule flight data refresh based on user preferences
        scheduleFlightDataRefresh()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the flight updates notification channel
            val flightUpdatesChannel = NotificationChannel(
                CHANNEL_FLIGHT_UPDATES,
                "Flight Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for flight data updates"
                enableLights(true)
                enableVibration(true)
            }
            
            // Register the channel with the system
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(flightUpdatesChannel)
            
            Log.d("FlightQApplication", "Notification channels created")
        }
    }
    
    fun scheduleFlightDataRefresh() {
        // Get user-defined update frequency (in hours)
        val updateFrequencyHours = prefs.getInt(PREF_UPDATE_FREQUENCY, DEFAULT_UPDATE_FREQUENCY_HOURS)
        Log.d("FlightQApplication", "Scheduling flight updates every $updateFrequencyHours hours")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Require network connectivity
            .setRequiresBatteryNotLow(true)                // Don't run when battery is low
            .build()
        
        // Create periodic work request that runs based on user preference
        val flightUpdateWorkRequest = PeriodicWorkRequestBuilder<FlightUpdateWorker>(
            updateFrequencyHours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS) // Start after 1 hour by default
            .build()
        
        // Schedule the work request
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            FLIGHT_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE, // Replace existing work to apply new schedule
            flightUpdateWorkRequest
        )
        
        Log.d("FlightQApplication", "Flight update worker scheduled to run every $updateFrequencyHours hours")
    }
    
    fun updateRefreshFrequency(hours: Int) {
        // Save the new frequency
        prefs.edit() { putInt(PREF_UPDATE_FREQUENCY, hours) }
        
        // Reschedule the worker with new frequency
        scheduleFlightDataRefresh()
        
        Log.d("FlightQApplication", "Updated flight update frequency to $hours hours")
    }

}
