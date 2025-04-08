package com.flight.flightq1.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

@Entity(tableName = "flight_stats")
data class FlightStatsEntity(
    @PrimaryKey
    val flightIcao: String,
    val flightIata: String?,
    val airlineName: String,
    val departureIata: String,
    val arrivalIata: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val updateCount: Int = 0,
    val lastDepartureDelay: Int = 0,
    val lastArrivalDelay: Int = 0,
    val avgDepartureDelay: Double = 0.0,
    val avgArrivalDelay: Double = 0.0,
    val avgFlightTime: Double = 0.0 // Added average flight time in minutes
) {
    fun getLastUpdatedFormatted(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(lastUpdated))
    }
    
    fun getRouteString(): String {
        return "$departureIata → $arrivalIata"
    }
    
    fun getDelayStatusColor(): Int {
        return when {
            avgDepartureDelay > 60 || avgArrivalDelay > 60 -> 2 // Red (severe)
            avgDepartureDelay > 15 || avgArrivalDelay > 15 -> 1 // Yellow (moderate)
            else -> 0 // Green (on time)
        }
    }
    
    // Format flight time as hours and minutes
    fun getFormattedFlightTime(): String {
        val hours = (avgFlightTime / 60).toInt()
        val minutes = (avgFlightTime % 60).toInt()
        return when {
            hours > 0 -> "$hours h $minutes min"
            else -> "$minutes min"
        }
    }
}
