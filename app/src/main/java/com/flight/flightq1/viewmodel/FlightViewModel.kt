package com.flight.flightq1.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flight.flightq1.api.FlightApiService
import com.flight.flightq1.api.NoConnectivityException
import com.flight.flightq1.api.ServerUnavailableException
import com.flight.flightq1.model.FlightData
import com.flight.flightq1.repository.FlightRepository
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class FlightViewModel : ViewModel() {
    private val apiService = FlightApiService.create()
    private val repository = FlightRepository(apiService)
    
    private val _flightData = MutableLiveData<FlightData?>()
    val flightData: LiveData<FlightData?> = _flightData
    
    private val _allFlightData = MutableLiveData<List<FlightData>>()
    val allFlightData: LiveData<List<FlightData>> = _allFlightData
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private var trackingJob: Job? = null

    // Add lastSearchedFlightNumber to keep track of what flight we're looking at
    private var lastSearchedFlightNumber: String? = null

    fun checkServerReachability(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val isReachable = withContext(Dispatchers.IO) {
                FlightApiService.isServerReachable()
            }
            callback(isReachable)
        }
    }

    fun startTracking(flightNumber: String) {
        stopTracking() // Cancel any ongoing tracking
        
        _isLoading.value = true
        _errorMessage.value = ""
        _flightData.value = null
        _allFlightData.value = emptyList()
        
        // Store the flight number we're tracking
        lastSearchedFlightNumber = flightNumber
        
        // Skip the server reachable check to avoid double connection attempts
        // Just start tracking directly
        beginTracking(flightNumber)
    }
    
    // Add method to resume tracking after activity recreation
    fun resumeLastTracking(flightNumber: String) {
        // Check if data is already loaded
        if (!_flightData.value?.flight?.icao.isNullOrEmpty() || 
            !_allFlightData.value.isNullOrEmpty()) {
            // Data is already restored, just update the last tracked number
            lastSearchedFlightNumber = flightNumber
            return
        }
        
        // Otherwise, resume tracking with minimal UI disruption
        _isLoading.value = false
        lastSearchedFlightNumber = flightNumber
        beginTracking(flightNumber)
    }
    
    private fun beginTracking(flightNumber: String) {
        trackingJob = viewModelScope.launch {
            var retryCount = 0
            val maxRetries = 3
            
            try {
                repository.trackFlightMinuteByMinute(flightNumber)
                    .catch { e ->
                        when (e) {
                            is UnknownHostException -> {
                                _errorMessage.value = "Unable to resolve host. Please check your internet connection."
                            }
                            is NoConnectivityException -> _errorMessage.value = e.message ?: "Network connection error"
                            is ServerUnavailableException -> _errorMessage.value = e.message ?: "Server is currently unavailable"
                            is SocketTimeoutException -> _errorMessage.value = "Connection timed out. The server might be overloaded."
                            is HttpException -> _errorMessage.value = "HTTP Error: ${e.code()}. ${getReadableErrorMessage(e.code())}"
                            is JsonSyntaxException -> _errorMessage.value = "Invalid data received from server. Please try again later."
                            else -> _errorMessage.value = "Error: ${e.message ?: "Unknown error occurred"}"
                        }
                        _isLoading.value = false
                    }
                    .collect { response ->
                        _isLoading.value = false
                        
                        if (response.isSuccessful) {
                            val flightResponse = response.body()
                            if (flightResponse != null) {
                                if (flightResponse.data.isNotEmpty()) {
                                    // Store all flight data
                                    _allFlightData.value = flightResponse.data
                                    
                                    // Take the first flight (most current) for immediate display
                                    _flightData.value = flightResponse.data[0]
                                    
                                    // Log the flight data to help with debugging
                                    Log.d("FlightViewModel", "Received flight data: ${flightResponse.data[0]}")
                                    Log.d("FlightViewModel", "Total flights received: ${flightResponse.data.size}")
                                } else {
                                    // Handle empty data array case
                                    _errorMessage.value = "No flight found with number \"$flightNumber\". Please check the flight number and try again."
                                }
                            } else {
                                _errorMessage.value = "Server returned empty response."
                                
                                // Retry logic for empty responses
                                if (retryCount < maxRetries) {
                                    retryCount++
                                    _errorMessage.value = "Server returned empty response. Retrying... (${retryCount}/${maxRetries})"
                                    delay(3000) // Wait 3 seconds before retry
                                    beginTracking(flightNumber)
                                } else {
                                    _errorMessage.value = "Server returned empty data after multiple tries. Please try again later."
                                }
                            }
                        } else {
                            when (response.code()) {
                                401 -> _errorMessage.value = "API access denied. Please check API key."
                                404 -> _errorMessage.value = "Flight not found. Please check the flight number and try again."
                                429 -> _errorMessage.value = "Too many requests. API rate limit exceeded."
                                500, 502, 503, 504 -> {
                                    if (retryCount < maxRetries) {
                                        retryCount++
                                        _errorMessage.value = "Server error. Retrying... (${retryCount}/${maxRetries})"
                                        delay(3000) // Wait 3 seconds before retry
                                        beginTracking(flightNumber)
                                    } else {
                                        _errorMessage.value = "Server is experiencing issues. Please try again later."
                                    }
                                }
                                else -> _errorMessage.value = "Server error (${response.code()}). Please try again later."
                            }
                        }
                    }
            } catch (e: Exception) {
                _isLoading.value = false
                // Check if this is a cancellation exception
                if (e is kotlinx.coroutines.CancellationException) {
                    // Don't show error message for normal cancellation
                    return@launch
                }
                _errorMessage.value = "Unexpected error: ${e.message ?: "Unknown error occurred"}"
            }
        }
    }
    
    private fun getReadableErrorMessage(code: Int): String {
        return when (code) {
            400 -> "Bad request. Check flight number format."
            401 -> "Unauthorized access."
            403 -> "Access forbidden."
            404 -> "Flight not found."
            429 -> "Too many requests. Please try again later."
            500 -> "Internal server error."
            502 -> "Bad gateway."
            503 -> "Service unavailable."
            504 -> "Gateway timeout."
            else -> "Unknown error."
        }
    }
    
    fun stopTracking() {
        trackingJob?.cancel()
        lastSearchedFlightNumber = null
        _flightData.value = null
        _allFlightData.value = emptyList()
    }
    
    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}
