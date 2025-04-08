package com.flight.flightq1.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility class for network-related operations
 */
object NetworkUtils {

    /**
     * Checks if the device has an active internet connection
     * @param context Application context
     * @return true if network is available, false otherwise
     */
    @SuppressLint("ObsoleteSdkInt")
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    /**
     * Switch to mock data mode if real API is unreachable
     */
    suspend fun checkAndSwitchToMockIfNeeded(context: Context) {
        val useRealApi = isNetworkAvailable(context) && withContext(Dispatchers.IO) {
            try {
                val connection = URL("https://api.aviationstack.com").openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "HEAD"
                val responseCode = connection.responseCode
                responseCode in 200..399
            } catch (_: Exception) {
                false
            }
        }
        
        // If we can't reach the real API, switch to mock mode
        if (!useRealApi) {
            com.flight.flightq1.api.FlightApiService.enableMockMode(true)
        } else {
            com.flight.flightq1.api.FlightApiService.enableMockMode(false)
        }
    }

}
