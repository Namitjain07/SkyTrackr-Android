package com.flight.flightq1.repository

import android.util.Log
import androidx.lifecycle.LiveData
import com.flight.flightq1.api.AirportFlightApiService
import com.flight.flightq1.db.FlightDatabase
import com.flight.flightq1.db.FlightStatsEntity
import com.flight.flightq1.model.FlightData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FlightStatsRepository(
    private val database: FlightDatabase,
    private val apiService: AirportFlightApiService
) {
    private val TAG = "FlightStatsRepository"

    // Get most delayed flights
    fun getMostDelayedFlights(): LiveData<List<FlightStatsEntity>> {
        return database.flightStatsDao().getMostDelayedFlights()
    }

    // Fetch updated flight data from API
    suspend fun fetchUpdatedFlightData(flight: FlightData, departureIata: String, arrivalIata: String): FlightData? {
        return withContext(Dispatchers.IO) {
            try {
                val flightId = flight.flight.icao
                if (true) {
                    Log.d(TAG, "Fetching updated data for flight $flightId, route: $departureIata-$arrivalIata")
                    
                    // Search flights on this route with increased limit
                    val response = apiService.searchFlights(
                        departureIata = departureIata,
                        arrivalIata = arrivalIata, 
                        limit = 100 // Increased from 30 to 100
                    )
                    
                    if (response.isSuccessful) {
                        val flightsData = response.body()?.data
                        Log.d(TAG, "API returned ${flightsData?.size ?: 0} flights for route $departureIata-$arrivalIata")
                        
                        // Find the matching flight
                        val matchedFlight = flightsData?.find { it.flight.icao == flight.flight.icao || it.flight.iata == flight.flight.iata }
                        
                        if (matchedFlight != null) {
                            Log.d(TAG, "Found matching flight $flightId in API results")
                            matchedFlight
                        } else {
                            Log.d(TAG, "No matching flight found for $flightId in API results")
                            null
                        }
                    } else {
                        Log.e(TAG, "API Error: ${response.code()} - ${response.message()}")
                        null
                    }
                } else {
                    Log.e(TAG, "No flight ID available")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching updated flight data", e)
                null
            }
        }
    }
}
