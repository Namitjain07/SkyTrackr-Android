package com.flight.flightq1

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flight.flightq1.adapter.FlightRouteAdapter
import com.flight.flightq1.adapter.HighlightedFlightAdapter
import com.flight.flightq1.db.FlightDatabase
import com.flight.flightq1.db.FlightRouteEntity
import com.flight.flightq1.viewmodel.AirportFlightViewModel
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.google.gson.Gson
import org.json.JSONException

class FlightRouteResultsActivity : AppCompatActivity() {
    
    private lateinit var viewModel: AirportFlightViewModel
    private lateinit var jsonPreviewTextView: TextView
    private lateinit var errorMessageTextView: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var routeTitleTextView: TextView
    private lateinit var jsonCard: MaterialCardView
    private lateinit var btnViewJson: ImageView
    private lateinit var cardHeader: LinearLayout
    private lateinit var flightsRecyclerView: RecyclerView
    private lateinit var highlightedFlightsRecyclerView: RecyclerView
    private lateinit var highlightedFlightsCard: MaterialCardView
    
    private lateinit var database: FlightDatabase
    private var jsonResponse: String = ""

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flight_route_results)
        
        // Initialize database
        database = FlightDatabase.getDatabase(this)
        
        // Initialize views
        jsonPreviewTextView = findViewById(R.id.tv_json_preview)
        errorMessageTextView = findViewById(R.id.tv_error_message)
        loadingIndicator = findViewById(R.id.loading_indicator)
        routeTitleTextView = findViewById(R.id.tv_route_title)
        jsonCard = findViewById(R.id.card_json_result)
        btnViewJson = findViewById(R.id.btn_view_json)
        cardHeader = findViewById(R.id.card_header)
        flightsRecyclerView = findViewById(R.id.rv_flights_list)
        highlightedFlightsRecyclerView = findViewById(R.id.rv_highlighted_flights)
        highlightedFlightsCard = findViewById(R.id.card_highlighted_flights)
        
        // Setup RecyclerViews
        flightsRecyclerView.layoutManager = LinearLayoutManager(this)
        highlightedFlightsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Setup card click listener to show bottom sheet
        jsonCard.setOnClickListener {
            if (jsonResponse.isNotEmpty()) {
                showJsonBottomSheet(jsonResponse)
            }
        }
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(AirportFlightViewModel::class.java)
        
        // Get data from intent
        val departureIata = intent.getStringExtra("departure_iata") ?: ""
        val arrivalIata = intent.getStringExtra("arrival_iata") ?: ""
        val useStoredData = intent.getBooleanExtra("use_stored_data", false)
        val storedApiResponse = intent.getStringExtra("stored_api_response")
        val storedHighlightedFlights = intent.getStringExtra("stored_highlighted_flights")
        
        if (departureIata.isEmpty() || arrivalIata.isEmpty()) {
            displayError("Missing departure or arrival IATA code")
            return
        }
        
        // Update the title
        routeTitleTextView.text = "Flights from $departureIata to $arrivalIata"
        
        // Set up observers
        setupObservers()
        
        // Check if we can use stored data
        if (useStoredData && !storedApiResponse.isNullOrEmpty()) {
            // Use stored API response instead of making a new request
            jsonResponse = storedApiResponse
            viewModel.processStoredApiResponse(storedApiResponse, storedHighlightedFlights)
            
            // Show a message that we're using stored data
            Toast.makeText(this, "Showing saved data from previous search", Toast.LENGTH_SHORT).show()
        } else {
            // Make fresh API request
            viewModel.searchFlights(departureIata, arrivalIata, this)
            
            // We'll save the route to database after getting the API response
            // Don't save route here as the API response isn't available yet
        }
    }
    
    private fun saveRouteToDatabase(departureIata: String, arrivalIata: String) {
        lifecycleScope.launch {
            try {
                // Get the current API response JSON
                val apiResponseJson = jsonResponse // Use the class variable that holds the complete response
                
                if (apiResponseJson.isEmpty()) {
                    Log.e("FlightRouteResults", "Attempted to save empty API response to database")
                    return@launch
                }
                
                // Convert selected flights to JSON string
                val gson = Gson()
                val highlightedFlightsJson = gson.toJson(viewModel.selectedFlights.value)
                Log.d("FlightRouteResults", "Highlighted flights JSON (${highlightedFlightsJson?.length ?: 0} chars): $highlightedFlightsJson")
                
                // Check if this route already exists in the database
                val existingRoute = database.flightRouteDao().getRouteByIatas(departureIata, arrivalIata)
                
                val routeEntity = if (existingRoute != null) {
                    // Update existing entry with new API response
                    existingRoute.copy(
                        apiResponse = apiResponseJson,
                        highlightedFlights = highlightedFlightsJson,
                        timestamp = if (intent.getBooleanExtra("use_stored_data", false)) 
                            existingRoute.timestamp else System.currentTimeMillis()
                    )
                } else {
                    // Create new entry
                    FlightRouteEntity(
                        departureIata = departureIata,
                        arrivalIata = arrivalIata,
                        timestamp = System.currentTimeMillis(),
                        apiResponse = apiResponseJson,
                        highlightedFlights = highlightedFlightsJson
                    )
                }
                
                Log.d("FlightRouteResults", "Saving route to database with API response length: ${apiResponseJson.length}")
                database.flightRouteDao().insertRoute(routeEntity)
            } catch (e: Exception) {
                Log.e("FlightRouteResults", "Error saving route to database", e)
            }
        }
    }
    
    private fun showJsonBottomSheet(jsonString: String) {
        val bottomSheet = JsonBottomSheetFragment.newInstance(
            jsonString, 
            "Flight Data: ${routeTitleTextView.text}"
        )
        bottomSheet.show(supportFragmentManager, "json_bottom_sheet")
    }
    
    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    private fun setupObservers() {
        viewModel.rawJson.observe(this, Observer { jsonString ->
            if (jsonString.isNotEmpty()) {
                try {
                    // Store the full JSON for the bottom sheet
                    jsonResponse = jsonString
                    
                    // Format and show a preview in the card - add more robust error handling
                    try {
                        val jsonObject = JSONObject(jsonString)
                        val formattedJson = jsonObject.toString(4)
                        val preview = if (formattedJson.length > 150) {
                            formattedJson.substring(0, 150) + "..."
                        } else {
                            formattedJson
                        }
                        jsonPreviewTextView.text = preview
                    } catch (jsonEx: JSONException) {
                        // Handle case where we don't have valid JSON
                        Log.e("FlightRouteResults", "Error parsing JSON: ${jsonEx.message}")
                        jsonPreviewTextView.text = "Click to view API response (raw format)"
                    }
                    
                    jsonCard.visibility = View.VISIBLE
                    
                    // If this came from a fresh API call, update the database with the response
                    if (!intent.getBooleanExtra("use_stored_data", false)) {
                        val departureIata = intent.getStringExtra("departure_iata") ?: ""
                        val arrivalIata = intent.getStringExtra("arrival_iata") ?: ""
                        
                        // Now we have the API response, save it to the database
                        saveRouteToDatabase(departureIata, arrivalIata)
                    }
                } catch (e: Exception) {
                    jsonPreviewTextView.text = "Click to view API response"
                    Log.e("FlightRouteResults", "Error processing JSON", e)
                }
                errorMessageTextView.visibility = View.GONE
            }
        })
        
        viewModel.errorMessage.observe(this, Observer { errorMsg ->
            if (errorMsg.isNotEmpty()) {
                displayError(errorMsg)
            }
        })
        
        viewModel.isLoading.observe(this, Observer { isLoading ->
            loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        })
        
        viewModel.flightsData.observe(this, Observer { flightsList ->
            if (flightsList.isNotEmpty()) {
                // Create and set the adapter with the flight data
                val adapter = FlightRouteAdapter(
                    flightsList,
                    viewModel
                ) { flight ->
                    // Handle flight card click - show detail view or more info
                    val flightJson = viewModel.getFlightJson(flight)
                    showJsonBottomSheet(flightJson)
                }
                flightsRecyclerView.adapter = adapter
                flightsRecyclerView.visibility = View.VISIBLE
            } else {
                flightsRecyclerView.visibility = View.GONE
            }
        })
        
        // Observe selected flights changes
        viewModel.selectedFlights.observe(this, Observer { selectedFlights ->
            if (selectedFlights.isNotEmpty()) {
                // Update the highlighted flights section
                val adapter = HighlightedFlightAdapter(
                    selectedFlights,
                    viewModel
                ) { flight ->
                    // Show flight details when clicking on a highlighted flight
                    val flightJson = viewModel.getFlightJson(flight)
                    showJsonBottomSheet(flightJson)
                }
                highlightedFlightsRecyclerView.adapter = adapter
                highlightedFlightsCard.visibility = View.VISIBLE
                
                // Also notify the main adapter to refresh checkbox states
                flightsRecyclerView.adapter?.notifyDataSetChanged()
                
                // Update the database with the new highlighted flights
                val departureIata = intent.getStringExtra("departure_iata") ?: ""
                val arrivalIata = intent.getStringExtra("arrival_iata") ?: ""
                if (departureIata.isNotEmpty() && arrivalIata.isNotEmpty()) {
                    saveRouteToDatabase(departureIata, arrivalIata)
                }
            } else {
                highlightedFlightsCard.visibility = View.GONE
                
                // If all flights are unselected, also update the database
                val departureIata = intent.getStringExtra("departure_iata") ?: ""
                val arrivalIata = intent.getStringExtra("arrival_iata") ?: ""
                if (departureIata.isNotEmpty() && arrivalIata.isNotEmpty() && jsonResponse.isNotEmpty()) {
                    saveRouteToDatabase(departureIata, arrivalIata)
                }
            }
        })
    }
    
    private fun displayError(message: String) {
        errorMessageTextView.visibility = View.VISIBLE
        errorMessageTextView.text = message
        jsonCard.visibility = View.GONE
        flightsRecyclerView.visibility = View.GONE
        highlightedFlightsCard.visibility = View.GONE
    }
}
