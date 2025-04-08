package com.flight.flightq1.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flight_routes")
data class FlightRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val departureIata: String,
    val arrivalIata: String,
    val timestamp: Long = System.currentTimeMillis(),
    val apiResponse: String? = null,        // Store the raw JSON response
    val highlightedFlights: String? = null  // Store highlighted flights as JSON
)
