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
import okhttp3.Interceptor
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import android.util.Log
import java.net.UnknownHostException
import okhttp3.Dns
import java.net.InetAddress

interface FlightApiService {
    @GET("flights")
    suspend fun trackFlight(
        @Query("access_key") accessKey: String = API_KEY,
        @Query("flight_icao") flightIcao: String? = null,
        @Query("flight_iata") flightIata: String? = null,
        @Query("limit") limit: Int = 10
    ): Response<FlightResponse>

    companion object {
        // API endpoint
        private const val BASE_URL = "https://api.aviationstack.com/v1/"
        private const val API_KEY = "ea2a75f9f20ea63d1fc3a6cedb1ba739"
        private const val TAG = "FlightApiService"
        
        // Known IP addresses for the API server (can be updated if they change)
        // These were obtained by running nslookup api.aviationstack.com
        private val AVIATIONSTACK_IPS = arrayOf(
            "35.167.208.187", // Primary IP - subject to change
            "54.213.58.231"   // Secondary IP - subject to change
        )
        
        private var useMockData = false
        
        fun create(): FlightApiService {
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
                .create(FlightApiService::class.java)
        }
        
        fun enableMockMode(enable: Boolean) {
            // Mock mode is disabled as per requirements
            useMockData = false
        }

        fun isServerReachable(): Boolean {
            return try {
                // Try DNS resolution first
                try {
                    val addresses = InetAddress.getAllByName("api.aviationstack.com")
                    if (addresses.isNotEmpty()) {
                        Log.d(TAG, "DNS resolution successful: ${addresses.joinToString { it.hostAddress ?: "unknown" }}")
                        
                        // Try to connect to the first resolved address
                        val socket = Socket()
                        socket.connect(InetSocketAddress(addresses[0], 443), 3000)
                        socket.close()
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Standard DNS resolution failed: ${e.message}")
                }
                
                // If DNS fails, try with hardcoded IPs
                for (ip in AVIATIONSTACK_IPS) {
                    try {
                        Log.d(TAG, "Trying hardcoded IP: $ip")
                        val socket = Socket()
                        socket.connect(InetSocketAddress(ip, 443), 3000)
                        socket.close()
                        Log.d(TAG, "Connection successful with IP: $ip")
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to connect using IP $ip: ${e.message}")
                    }
                }
                
                false
            } catch (e: Exception) {
                Log.e(TAG, "Server connectivity check failed: ${e.javaClass.simpleName} - ${e.message}")
                false
            }
        }
    }
}

/**
 * Custom DNS resolver to handle DNS issues with fallbacks
 */
class CustomDns : Dns {
    private val TAG = "CustomDns"
    
    // Hardcoded IP addresses for aviation stack API
    private val AVIATION_STACK_IPS = mapOf(
        "api.aviationstack.com" to listOf(
            "35.167.208.187", // Primary IP - subject to change
            "54.213.58.231"   // Backup IP - subject to change
        )
    )
    
    override fun lookup(hostname: String): List<InetAddress> {
        // Log attempt to resolve hostname
        Log.d(TAG, "Attempting to resolve hostname: $hostname")
        
        // For the specific aviation stack domain, try our hardcoded IPs first
        if (hostname == "api.aviationstack.com") {
            try {
                // Try system DNS first
                Log.d(TAG, "Trying system DNS for $hostname")
                val systemAddresses = Dns.SYSTEM.lookup(hostname)
                if (systemAddresses.isNotEmpty()) {
                    Log.d(TAG, "Successfully resolved $hostname via system DNS to: " +
                          systemAddresses.joinToString { "${it.hostName}/${it.hostAddress}" })
                    return systemAddresses
                }
            } catch (e: Exception) {
                Log.e(TAG, "System DNS lookup failed for $hostname: ${e.message}")
            }
            
            // If system DNS fails, use our hardcoded IPs
            Log.d(TAG, "Using hardcoded IPs for $hostname")
            val hardcodedAddresses = AVIATION_STACK_IPS[hostname]?.mapNotNull { ip ->
                try {
                    InetAddress.getByName(ip)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create InetAddress for $ip: ${e.message}")
                    null
                }
            } ?: emptyList()
            
            if (hardcodedAddresses.isNotEmpty()) {
                Log.d(TAG, "Using hardcoded IPs: ${hardcodedAddresses.joinToString { it.hostAddress ?: "unknown" }}")
                return hardcodedAddresses
            }
            
            throw UnknownHostException("Could not resolve host $hostname using any method")
        }
        
        // For other hostnames, use the system DNS
        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            Log.d(TAG, "Successfully resolved $hostname to ${addresses.size} addresses")
            return addresses
        } catch (e: UnknownHostException) {
            Log.e(TAG, "DNS lookup failed for $hostname: ${e.message}")
            throw e
        }
    }
}

/**
 * Custom interceptor to handle network connectivity issues
 */
class NetworkConnectionInterceptor : Interceptor {
    private val TAG = "NetworkInterceptor"

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request: Request = chain.request()
        
        try {
            // Just attempt to proceed with the request
            return chain.proceed(request)
        } catch (e: Exception) {
            Log.e(TAG, "Network error during API call: ${e.javaClass.simpleName} - ${e.message}")
            
            // Only throw NoConnectivityException for specific network errors
            when (e) {
                is UnknownHostException -> {
                    val detailedMsg = "DNS resolution failed for ${request.url.host}. " +
                                     "Please check your internet connection and DNS settings."
                    Log.e(TAG, "DNS Error: $detailedMsg")
                    throw NoConnectivityException(detailedMsg, e)
                }
                is java.net.ConnectException -> 
                    throw NoConnectivityException("Failed to connect to server. The flight tracking service may be temporarily unavailable.", e)
                is java.net.SocketTimeoutException -> 
                    throw NoConnectivityException("Connection timed out. The server might be overloaded.", e)
                else -> throw e
            }
        }
    }
}

class NoConnectivityException(message: String, cause: Throwable) : 
    IOException(message, cause)

class ServerUnavailableException(message: String) : 
    IOException(message)
