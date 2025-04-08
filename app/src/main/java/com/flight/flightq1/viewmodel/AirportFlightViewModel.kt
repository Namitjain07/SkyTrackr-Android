package com.flight.flightq1.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flight.flightq1.api.AirportFlightApiService
import com.flight.flightq1.model.FlightData
import com.flight.flightq1.model.FlightResponse
import com.flight.flightq1.repository.AirportFlightRepository
import com.flight.flightq1.utils.NetworkUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AirportFlightViewModel : ViewModel() {
    private val repository = AirportFlightRepository(AirportFlightApiService.create())
    
    private val _flightsData = MutableLiveData<List<FlightData>>()
    val flightsData: LiveData<List<FlightData>> = _flightsData
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _rawJson = MutableLiveData<String>()
    val rawJson: LiveData<String> = _rawJson
    
    // Add selected flights tracking
    private val _selectedFlights = MutableLiveData<List<FlightData>>(emptyList())
    val selectedFlights: LiveData<List<FlightData>> = _selectedFlights
    
    private var searchJob: Job? = null
    private val gson = Gson()
    
    // Maximum number of flights that can be selected
    val MAX_SELECTED_FLIGHTS = 3
    
    fun searchFlights(departureIata: String, arrivalIata: String, context: Context) {
        // Cancel any ongoing job
        searchJob?.cancel()
        
        _isLoading.value = true
        _errorMessage.value = ""
        
        searchJob = viewModelScope.launch {
            try {
                // First check network connectivity and switch to mock if needed
                withContext(Dispatchers.IO) {
                    NetworkUtils.checkAndSwitchToMockIfNeeded(context)
                }
                
                repository.searchFlights(departureIata, arrivalIata)
                    .collect { response -> 
                        _isLoading.value = false
                        
                        if (response.isSuccessful) {
                            val flightResponse = response.body()
                            
                            // Store the raw JSON for display - FIX: Get the raw JSON string, not toString()
                            try {
                                if (flightResponse != null) {
                                    _rawJson.value = gson.toJson(flightResponse)
                                    Log.d("AirportFlightViewModel", "Stored JSON response with length: ${_rawJson.value?.length}")
                                } else {
                                    _rawJson.value = "{\"error\": \"Empty response\"}"
                                    Log.e("AirportFlightViewModel", "Empty flight response")
                                }
                            } catch (e: Exception) {
                                Log.e("AirportFlightViewModel", "Error serializing response", e)
                                _rawJson.value = "{\"error\": \"${e.message}\"}"
                            }
                            
                            if (flightResponse?.success == true) {
                                if (flightResponse.data.isNotEmpty()) {
                                    _flightsData.value = flightResponse.data
                                    Log.d("AirportFlightViewModel", "Received flights data: ${flightResponse.data.size} flights")
                                } else {
                                    _errorMessage.value = "No flights found between $departureIata and $arrivalIata."
                                }
                            } else {
                                _errorMessage.value = flightResponse?.error?.message ?: "Unknown error occurred"
                            }
                        } else {
                            when (response.code()) {
                                401 -> _errorMessage.value = "API access denied. Please check API key."
                                404 -> _errorMessage.value = "No flights found. Please check the airport codes."
                                429 -> _errorMessage.value = "Too many requests. API rate limit exceeded."
                                in 500..599 -> _errorMessage.value = "Server is experiencing issues. Please try again later."
                                else -> _errorMessage.value = "Server error (${response.code()}). Please try again later."
                            }
                        }
                    }
            } catch (e: Exception) {
                _isLoading.value = false
                if (e is CancellationException) {
                    // Job was cancelled, no need to show error
                } else {
                    _errorMessage.value = "Error: ${e.message ?: "Unknown error"}"
                    Log.e("AirportFlightViewModel", "Error searching flights", e)
                }
            }
        }
    }
    
    // Method to get JSON for a specific flight
    fun getFlightJson(flight: FlightData): String {
        return gson.toJson(flight)
    }
    
    // Methods to handle flight selection
    fun toggleFlightSelection(flight: FlightData): Boolean {
        val currentList = _selectedFlights.value?.toMutableList() ?: mutableListOf()
        
        return if (currentList.contains(flight)) {
            // If already selected, remove it
            currentList.remove(flight)
            _selectedFlights.value = currentList
            Log.d("AirportFlightViewModel", "Removed flight from selection. Now have ${currentList.size} flights selected.")
            true
        } else {
            // If not selected and we haven't reached the limit, add it
            if (currentList.size < MAX_SELECTED_FLIGHTS) {
                currentList.add(flight)
                _selectedFlights.value = currentList
                Log.d("AirportFlightViewModel", "Added flight to selection. Now have ${currentList.size} flights selected.")
                true
            } else {
                // We've reached the selection limit
                Log.d("AirportFlightViewModel", "Cannot select more flights. Limit reached (${currentList.size}).")
                false
            }
        }
    }
    
    fun isFlightSelected(flight: FlightData): Boolean {
        return _selectedFlights.value?.contains(flight) ?: false
    }
    
    fun getSelectedFlightsCount(): Int {
        return _selectedFlights.value?.size ?: 0
    }

    // Process stored API response without making network requests
    fun processStoredApiResponse(jsonResponse: String, highlightedFlightsJson: String?) {
        viewModelScope.launch {
            try {
                _isLoading.value = false
                _rawJson.value = jsonResponse
                
                Log.d("AirportFlightViewModel", "Processing stored response with length: ${jsonResponse.length}")
                if (!highlightedFlightsJson.isNullOrEmpty()) {
                    Log.d("AirportFlightViewModel", "Highlighted flights JSON found (${highlightedFlightsJson.length} chars)")
                } else {
                    Log.d("AirportFlightViewModel", "No highlighted flights JSON found")
                }
                
                // Parse the stored API response
                val flightResponse = gson.fromJson(jsonResponse, FlightResponse::class.java)
                
                if (flightResponse != null && flightResponse.data.isNotEmpty()) {
                    _flightsData.value = flightResponse.data
                    Log.d("AirportFlightViewModel", "Loaded flights from stored data: ${flightResponse.data.size} flights")
                    
                    // Process highlighted flights if available
                    if (!highlightedFlightsJson.isNullOrEmpty()) {
                        try {
                            val type = object : TypeToken<List<FlightData>>() {}.type
                            val highlightedFlightsList = gson.fromJson<List<FlightData>>(
                                highlightedFlightsJson, 
                                type
                            )
                            
                            // Reload highlighted flights
                            _selectedFlights.value = highlightedFlightsList.mapNotNull { highlightedFlight ->
                                // Find matching flight in current data
                                flightResponse.data.find { flight ->  
                                    flight.flight.icao == highlightedFlight.flight.icao ||
                                    flight.flight.iata == highlightedFlight.flight.iata 
                                }
                            }
                            
                            Log.d("AirportFlightViewModel", "Loaded ${_selectedFlights.value?.size} highlighted flights from storage")
                        } catch (e: Exception) {
                            Log.e("AirportFlightViewModel", "Error parsing highlighted flights: ${e.message}", e)
                        }
                    }
                } else {
                    _errorMessage.value = "No flights found in stored data."
                    Log.e("AirportFlightViewModel", "No flights found in stored data")
                }
            } catch (e: Exception) {
                Log.e("AirportFlightViewModel", "Error processing stored data: ${e.message}", e)
                _errorMessage.value = "Error processing stored data: ${e.message}"
            }
        }
    }
}
