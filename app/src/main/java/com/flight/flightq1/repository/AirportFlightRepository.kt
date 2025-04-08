package com.flight.flightq1.repository

import com.flight.flightq1.api.AirportFlightApiService
import com.flight.flightq1.model.FlightResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response

class AirportFlightRepository(private val apiService: AirportFlightApiService) {
    
    suspend fun getFlightsByRoute(departureIata: String, arrivalIata: String): Response<FlightResponse> {
        return apiService.searchFlights(
            departureIata = departureIata,
            arrivalIata = arrivalIata,
            limit = 100
        )
    }
    
    fun searchFlights(departureIata: String, arrivalIata: String): Flow<Response<FlightResponse>> = flow {
        val response = getFlightsByRoute(departureIata, arrivalIata)
        emit(response)
    }
}
