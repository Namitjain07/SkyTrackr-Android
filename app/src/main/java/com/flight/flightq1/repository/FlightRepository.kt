package com.flight.flightq1.repository

import com.flight.flightq1.api.FlightApiService
import com.flight.flightq1.model.FlightResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response

class FlightRepository(private val apiService: FlightApiService) {
    
    suspend fun getFlightData(flightNumber: String): Response<FlightResponse> {
        // Determine if the flight number is in IATA or ICAO format
        // ICAO format is typically 3 letters followed by numbers (e.g. BAW123)
        // IATA format is typically 2 letters followed by numbers (e.g. BA123)
        val isIcao = flightNumber.matches(Regex("^[A-Z]{3}\\d+$", RegexOption.IGNORE_CASE))
        
        // Include limit parameter to get multiple days of data (up to 100)
        return if (isIcao) {
            apiService.trackFlight(flightIcao = flightNumber, limit = 100)
        } else {
            apiService.trackFlight(flightIata = flightNumber, limit = 100)
        }
    }
    
    // Track flight location every minute
    fun trackFlightMinuteByMinute(flightNumber: String): Flow<Response<FlightResponse>> = flow {
        try {
            // First immediate attempt
            val response = getFlightData(flightNumber)
            emit(response)
            
            // Then periodic updates
            while (true) {
                delay(60000) // Delay for 1 minute before next update
                val updatedResponse = getFlightData(flightNumber)
                emit(updatedResponse)
            }
        } catch (e: Exception) {
            throw e
        }
    }
}
