package com.flight.flightq1

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.flight.flightq1.adapter.FlightStatsAdapter
import com.flight.flightq1.adapter.HighlightedFlightsAdapter
import com.flight.flightq1.adapter.RouteHistoryAdapter
import com.flight.flightq1.api.AirportFlightApiService
import com.flight.flightq1.db.FlightDatabase
import com.flight.flightq1.db.FlightRouteEntity
import com.flight.flightq1.model.FlightData
import com.flight.flightq1.repository.FlightStatsRepository
import com.flight.flightq1.settings.UpdateFrequencyActivity
import com.flight.flightq1.worker.FlightUpdateWorker
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

class FlightStatisticsActivity : AppCompatActivity() {

    private lateinit var noRoutesTextView: TextView
    private lateinit var noHighlightedFlightsTextView: TextView
    private lateinit var noFlightStatsTextView: TextView
    private lateinit var routeHistoryRecyclerView: RecyclerView
    private lateinit var highlightedFlightsRecyclerView: RecyclerView
    private lateinit var flightStatsRecyclerView: RecyclerView
    private lateinit var routeHistoryAdapter: RouteHistoryAdapter
    private lateinit var highlightedFlightsAdapter: HighlightedFlightsAdapter
    private lateinit var flightStatsAdapter: FlightStatsAdapter
    private lateinit var flightStatsCard: MaterialCardView
    private lateinit var database: FlightDatabase
    private lateinit var flightStatsRepository: FlightStatsRepository
    private val gson = Gson()

    // Add TextView references for flight history card
    private lateinit var flightCountTextView: TextView
    private lateinit var recentFlightsTextView: TextView

    // Add TextView references for flight status summary card
    private lateinit var ontimeCountTextView: TextView
    private lateinit var delayedCountTextView: TextView
    private lateinit var cancelledCountTextView: TextView

    // Add TextView references for airline and airport cards
    private lateinit var noAirlineDataTextView: TextView
    private lateinit var frequentAirportsTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flight_statistics)

        // Initialize the database
        database = FlightDatabase.getDatabase(this)
        
        // Initialize the repository
        flightStatsRepository = FlightStatsRepository(database, AirportFlightApiService.create())

        // Initialize views
        noRoutesTextView = findViewById(R.id.tv_no_routes)
        noHighlightedFlightsTextView = findViewById(R.id.tv_no_highlighted_flights)
        noFlightStatsTextView = findViewById(R.id.tv_no_flight_stats)
        routeHistoryRecyclerView = findViewById(R.id.rv_recent_routes)
        highlightedFlightsRecyclerView = findViewById(R.id.rv_highlighted_flights)
        flightStatsRecyclerView = findViewById(R.id.rv_flight_stats)
        flightStatsCard = findViewById(R.id.card_flight_stats)
        
        // Initialize flight history card views
        flightCountTextView = findViewById(R.id.tv_flight_count)
        recentFlightsTextView = findViewById(R.id.tv_recent_flights)
        
        // Initialize flight status summary card views
        ontimeCountTextView = findViewById(R.id.tv_ontime_count)
        delayedCountTextView = findViewById(R.id.tv_delayed_count)
        cancelledCountTextView = findViewById(R.id.tv_cancelled_count)
        
        // Initialize airline and airport card views
        noAirlineDataTextView = findViewById(R.id.tv_no_airline_data)
        frequentAirportsTextView = findViewById(R.id.tv_frequent_airports)

        // Setup the toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "View Flight Statistics"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set up the adapters
        setupRouteHistoryAdapter()
        setupHighlightedFlightsAdapter()
        setupFlightStatsAdapter()

        // Set up FAB click listener
        val fabAddFlight = findViewById<FloatingActionButton>(R.id.fab_add_flight)
        fabAddFlight.setOnClickListener { showAddFlightDialog() }

        // Load and display data
        loadRouteHistory()
        loadHighlightedFlights()
        loadFlightStats()
        
        // Load additional card data
        loadFlightHistory()
        loadFlightStatusSummary()
        loadAirlineStatistics()
        loadAirportStatistics()
    }

    private fun setupRouteHistoryAdapter() {
        routeHistoryAdapter = RouteHistoryAdapter { route ->
            // Use the entire route entity to pass stored API data
            navigateToRouteResults(route)
        }
        routeHistoryRecyclerView.adapter = routeHistoryAdapter
    }
    
    private fun setupHighlightedFlightsAdapter() {
        highlightedFlightsAdapter = HighlightedFlightsAdapter { flight, departureIata, arrivalIata ->
            // Show flight details in JSON bottom sheet
            showFlightJsonBottomSheet(flight, departureIata, arrivalIata)
        }
        highlightedFlightsRecyclerView.adapter = highlightedFlightsAdapter
    }
    
    private fun setupFlightStatsAdapter() {
        flightStatsAdapter = FlightStatsAdapter()
        flightStatsRecyclerView.adapter = flightStatsAdapter
        
        // Make sure the RecyclerView shows all items
        flightStatsRecyclerView.isNestedScrollingEnabled = false
    }

    private fun showFlightJsonBottomSheet(flight: FlightData, departureIata: String, arrivalIata: String) {
        try {
            // Convert the flight to JSON
            val flightJson = gson.toJson(flight)
            
            // Create title for the bottom sheet
            val title = "${flight.airline?.name ?: "Unknown"} (${flight.flight.iata ?: flight.flight.icao})"
            val subtitle = "$departureIata → $arrivalIata"
            
            // Show the bottom sheet with flight data
            val bottomSheet = JsonBottomSheetFragment.newInstance(
                flightJson, 
                "$title: $subtitle"
            )
            bottomSheet.show(supportFragmentManager, "flight_json_bottom_sheet")
            
        } catch (e: Exception) {
            Log.e("FlightStatistics", "Error showing flight JSON: ${e.message}")
            Toast.makeText(this, "Error showing flight details", Toast.LENGTH_SHORT).show()
            
            // Fallback to route results activity if JSON display fails
            navigateToFlightRoute(departureIata, arrivalIata)
        }
    }
    
    private fun navigateToFlightRoute(departureIata: String, arrivalIata: String) {
        // This is the original navigation logic, now used as fallback
        val intent = Intent(this, FlightRouteResultsActivity::class.java).apply {
            putExtra("departure_iata", departureIata)
            putExtra("arrival_iata", arrivalIata)
            putExtra("use_stored_data", true)
        }
        
        lifecycleScope.launch {
            val route = database.flightRouteDao().getRouteByIatas(departureIata, arrivalIata)
            if (route != null) {
                intent.putExtra("stored_api_response", route.apiResponse)
                intent.putExtra("stored_highlighted_flights", route.highlightedFlights)
                startActivity(intent)
            } else {
                intent.putExtra("use_stored_data", false)
                startActivity(intent)
            }
        }
    }

    private fun loadRouteHistory() {
        database.flightRouteDao().getUniqueRoutes().observe(this, Observer { routes ->
            if (routes.isNotEmpty()) {
                routeHistoryAdapter.submitList(routes)
                noRoutesTextView.visibility = View.GONE
                routeHistoryRecyclerView.visibility = View.VISIBLE
            } else {
                noRoutesTextView.visibility = View.VISIBLE
                routeHistoryRecyclerView.visibility = View.GONE
            }
        })
    }
    
    private fun loadHighlightedFlights() {
        database.flightRouteDao().getRoutesWithHighlightedFlights().observe(this, Observer { routes ->
            val highlightedItems = mutableListOf<HighlightedFlightsAdapter.HighlightedFlightItem>()
            
            for (route in routes) {
                if (!route.highlightedFlights.isNullOrEmpty()) {
                    try {
                        val type = object : TypeToken<List<FlightData>>() {}.type
                        val highlightedFlights = gson.fromJson<List<FlightData>>(route.highlightedFlights, type)
                        
                        // Add each flight with route info to our list
                        for (flight in highlightedFlights) {
                            highlightedItems.add(
                                HighlightedFlightsAdapter.HighlightedFlightItem(
                                    flightData = flight,
                                    departureIata = route.departureIata,
                                    arrivalIata = route.arrivalIata
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("FlightStatistics", "Error parsing highlighted flights: ${e.message}")
                    }
                }
            }
            
            if (highlightedItems.isNotEmpty()) {
                highlightedFlightsAdapter.submitList(highlightedItems)
                noHighlightedFlightsTextView.visibility = View.GONE
                highlightedFlightsRecyclerView.visibility = View.VISIBLE
            } else {
                noHighlightedFlightsTextView.visibility = View.VISIBLE
                highlightedFlightsRecyclerView.visibility = View.GONE
            }
        })
    }
    
    @SuppressLint("NotifyDataSetChanged")
    private fun loadFlightStats() {
        flightStatsRepository.getMostDelayedFlights().observe(this, Observer { stats ->
            if (stats.isNotEmpty()) {
                flightStatsAdapter.submitList(stats)
                Log.d("FlightStatisticsActivity", "Loaded ${stats.size} flight stats items")
                noFlightStatsTextView.visibility = View.GONE
                flightStatsRecyclerView.visibility = View.VISIBLE
                
                // Force RecyclerView to measure all items
                flightStatsRecyclerView.post {
                    flightStatsAdapter.notifyDataSetChanged()
                }
            } else {
                noFlightStatsTextView.visibility = View.VISIBLE
                flightStatsRecyclerView.visibility = View.GONE
            }
        })
    }

    private fun navigateToRouteResults(route: FlightRouteEntity) {
        val intent = Intent(this, FlightRouteResultsActivity::class.java).apply {
            putExtra("departure_iata", route.departureIata)
            putExtra("arrival_iata", route.arrivalIata)
            putExtra("stored_api_response", route.apiResponse)
            putExtra("stored_highlighted_flights", route.highlightedFlights)
            putExtra("use_stored_data", true)
        }
        startActivity(intent)
    }

    private fun navigateToRouteResults(departureIata: String, arrivalIata: String) {
        val intent = Intent(this, FlightRouteResultsActivity::class.java).apply {
            putExtra("departure_iata", departureIata)
            putExtra("arrival_iata", arrivalIata)
            // We don't have stored data yet when calling from this point
            putExtra("use_stored_data", false)
        }
        startActivity(intent)
    }

    private fun showAddFlightDialog() {
        // Create the dialog
        val builder = AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_flight, null)
        builder.setView(dialogView)

        // Get dialog components
        val departureIataEditText = dialogView.findViewById<EditText>(R.id.et_departure_iata)
        val destinationIataEditText = dialogView.findViewById<EditText>(R.id.et_arrival_iata)
        val searchButton = dialogView.findViewById<Button>(R.id.btn_search_flights)

        // Create and show the dialog
        val dialog = builder.create()
        dialog.show()

        // Set up save button click listener
        searchButton.setOnClickListener {
            val departureIata = departureIataEditText.text.toString().trim().uppercase()
            val arrivalIata = destinationIataEditText.text.toString().trim().uppercase()

            // Validate IATA codes (should be 3 letters)
            if (departureIata.isEmpty() || !departureIata.matches(Regex("^[A-Z]{3}$"))) {
                Toast.makeText(this, "Please enter a valid departure IATA code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (arrivalIata.isEmpty() || !arrivalIata.matches(Regex("^[A-Z]{3}$"))) {
                Toast.makeText(this, "Please enter a valid arrival IATA code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Call with correct parameters
            navigateToRouteResults(departureIata, arrivalIata)
            dialog.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.flight_statistics_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh_now -> {
                // Trigger refresh immediately
                refreshFlightDataNow()
                true
            }
            R.id.action_update_frequency -> {
                // Open update frequency settings
                val intent = Intent(this, UpdateFrequencyActivity::class.java)
                startActivity(intent)
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun refreshFlightDataNow() {
        // Create a one-time work request
        val refreshWorkRequest = OneTimeWorkRequestBuilder<FlightUpdateWorker>().build()
        
        // Enqueue the work request
        val workManager = WorkManager.getInstance(this)
        workManager.enqueue(refreshWorkRequest)
        
        // Show a message that refresh has started
        Snackbar.make(
            findViewById(android.R.id.content),
            "Refreshing flight data...",
            Snackbar.LENGTH_LONG
        ).show()
        
        // Observe the work request state
        workManager.getWorkInfoByIdLiveData(refreshWorkRequest.id)
            .observe(this, { workInfo ->
                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        // Refresh the UI when complete
                        loadFlightStats()
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            "Flight data refresh completed",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    WorkInfo.State.FAILED -> {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            "Failed to refresh flight data",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    else -> {
                        // Other states (BLOCKED, CANCELLED, ENQUEUED, RUNNING)
                        Log.d("FlightStatistics", "Refresh work state: ${workInfo.state}")
                    }
                }
            })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Loads flight history data for the Flight History card
     */
    @SuppressLint("SetTextI18n")
    private fun loadFlightHistory() {
        // Get total flight count
        database.flightStatsDao().getAllFlightStats().observe(this) { stats ->
            val totalFlights = stats.size
            flightCountTextView.text = "Total flights tracked: $totalFlights"
            
            // Get recent flights information
            if (totalFlights > 0) {
                database.flightStatsDao().getRecentlyUpdatedFlights(5)
                    .observe(this) { recentStats ->
                        if (recentStats.isNotEmpty()) {
                            val recentFlightsText = recentStats.joinToString(", ") {
                                it.flightIata ?: it.flightIcao
                            }
                            recentFlightsTextView.text = "Recent flights: $recentFlightsText"
                        }
                    }
            }
        }
    }

    /**
     * Loads flight status data for the Flight Status Summary card
     */
    private fun loadFlightStatusSummary() {
        database.flightStatsDao().getAllFlightStats().observe(this) { stats ->
            // Initialize counters
            var onTimeCount = 0
            var delayedCount = 0
            var cancelledCount = 0
            
            // Count flights by status
            stats.forEach { flightStat ->
                when {
                    // A flight is considered delayed if either departure or arrival delay averages > 15 minutes
                    // For demonstration, consider flights with extreme delays (> 120 min) as cancelled
                    flightStat.avgDepartureDelay > 120 || flightStat.avgArrivalDelay > 120 -> cancelledCount++
                    else -> onTimeCount++
                }
            }
            
            // Update UI with counts
            ontimeCountTextView.text = onTimeCount.toString()
            delayedCountTextView.text = delayedCount.toString()
            cancelledCountTextView.text = cancelledCount.toString()
        }
    }

    /**
     * Loads airline statistics for the Most Tracked Airlines card
     */
    private fun loadAirlineStatistics() {
        database.flightStatsDao().getAllFlightStats().observe(this) { stats ->
            if (stats.isNotEmpty()) {
                // Group flights by airline and count them
                val airlineStats = stats.groupBy { it.airlineName }
                                      .mapValues { it.value.size }
                                      .toList()
                                      .sortedByDescending { it.second }
                                      .take(5)  // Top 5 airlines
                
                if (airlineStats.isNotEmpty()) {
                    val airlineText = buildString {
                        airlineStats.forEachIndexed { index, (airline, count) ->
                            append("${index + 1}. $airline: $count flights\n")
                        }
                    }
                    noAirlineDataTextView.text = airlineText
                }
            }
        }
    }

    /**
     * Loads airport statistics for the Airport Statistics card
     */
    private fun loadAirportStatistics() {
        database.flightStatsDao().getAllFlightStats().observe(this) { stats ->
            if (stats.isNotEmpty()) {
                // Count departures by airport
                val departureStats = stats.groupBy { it.departureIata }
                                         .mapValues { it.value.size }
                                         .toList()
                                         .sortedByDescending { it.second }
                                         .take(3) // Top 3 departure airports
                
                // Count arrivals by airport
                val arrivalStats = stats.groupBy { it.arrivalIata }
                                       .mapValues { it.value.size }
                                       .toList()
                                       .sortedByDescending { it.second }
                                       .take(3) // Top 3 arrival airports
                
                if (departureStats.isNotEmpty() || arrivalStats.isNotEmpty()) {
                    val airportText = buildString {
                        append("Top departure airports:\n")
                        departureStats.forEach { (airport, count) ->
                            append("• $airport: $count flights\n")
                        }
                        append("\nTop arrival airports:\n")
                        arrivalStats.forEach { (airport, count) ->
                            append("• $airport: $count flights\n")
                        }
                    }
                    frequentAirportsTextView.text = airportText
                }
            }
        }
    }
}
