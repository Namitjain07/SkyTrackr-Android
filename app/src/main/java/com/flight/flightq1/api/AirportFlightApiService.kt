package com.flight.flightq1.api

import com.flight.flightq1.model.FlightResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface AirportFlightApiService {
    @GET("flights")
    suspend fun searchFlights(
        @Query("access_key") accessKey: String = API_KEY,
        @Query("dep_iata") departureIata: String,
        @Query("arr_iata") arrivalIata: String,
        @Query("limit") limit: Int = 10
    ): Response<FlightResponse>

    companion object {
        // API endpoint
        private const val BASE_URL = "https://api.aviationstack.com/v1/"
        private const val API_KEY = "ea2a75f9f20ea63d1fc3a6cedb1ba739"

        fun create(): AirportFlightApiService {
            val logger = HttpLoggingInterceptor().apply { 
                level = HttpLoggingInterceptor.Level.BODY 
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .addInterceptor(NetworkConnectionInterceptor())
                .dns(CustomDns())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
                
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(AirportFlightApiService::class.java)
        }
    }
}
