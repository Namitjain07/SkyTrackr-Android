package com.flight.flightq1

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flight.flightq1.adapter.FlightDateAdapter
import com.flight.flightq1.model.FlightData
import com.flight.flightq1.viewmodel.FlightViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.view.isVisible

class MainActivity : AppCompatActivity() {
    private lateinit var flightNumberInput: EditText
    private lateinit var trackButton: Button
    private lateinit var flightInfoTextView: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var flightDatesRecyclerView: RecyclerView
    private lateinit var flightInfoScrollView: View
    private lateinit var flightDetailsHeader: View
    private lateinit var backToDatesButton: Button
    private lateinit var flightDateTitle: TextView
    private lateinit var datesListTitle: TextView
    private lateinit var viewModel: FlightViewModel
    private var isTracking = false
    private var currentFlightList: List<FlightData> = emptyList()
    private var isShowingFlightDetails = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Add support for action bar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Initialize views
        flightNumberInput = findViewById(R.id.flight_number_input)
        trackButton = findViewById(R.id.track_button)
        flightInfoTextView = findViewById(R.id.flight_info_textview)
        loadingIndicator = findViewById(R.id.loading_indicator)
        flightDatesRecyclerView = findViewById(R.id.flight_dates_recyclerview)
        flightInfoScrollView = findViewById(R.id.flight_info_scrollview)
        flightDetailsHeader = findViewById(R.id.flight_details_header)
        backToDatesButton = findViewById(R.id.back_to_dates_button)
        flightDateTitle = findViewById(R.id.flight_date_title)
        datesListTitle = findViewById(R.id.dates_list_title)

        // Setup RecyclerView
        flightDatesRecyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(FlightViewModel::class.java)

        // Setup button click listeners
        trackButton.setOnClickListener {
            val flightNumber = flightNumberInput.text.toString().trim()

            if (!isTracking) {
                if (validateFlightNumber(flightNumber)) {
                    if (isNetworkAvailable()) {
                        startTracking(flightNumber)
                    } else {
                        showError("No internet connection available. Please check your network settings.")
                    }
                } else {
                    showToast(getString(R.string.invalid_flight_number))
                }
            } else {
                stopTracking()
            }
        }

        // Setup back button listener
        backToDatesButton.setOnClickListener {
            showFlightDatesList()
        }

        // Observe LiveData from ViewModel
        setupObservers()

        // Restore state if available
        savedInstanceState?.let { bundle ->
            val savedFlightNumber = bundle.getString("flightNumber")
            isTracking = bundle.getBoolean("isTracking", false)
            isShowingFlightDetails = bundle.getBoolean("isShowingFlightDetails", false)
            val lastTrackedFlight = bundle.getString("lastTrackedFlight")

            savedFlightNumber?.let {
                if (it.isNotEmpty()) {
                    flightNumberInput.setText(it)
                    if (isTracking && lastTrackedFlight != null) {
                        trackButton.text = getString(R.string.stop_tracking)
                        lifecycleScope.launch {
                            viewModel.resumeLastTracking(lastTrackedFlight)
                        }
                    }
                }
            }

            val showingFlightDatesList = bundle.getBoolean("showingFlightDatesList", false)
            if (showingFlightDatesList && (viewModel.allFlightData.value?.size ?: 0) > 1) {
                showFlightDatesList()
            }
        }
    }

    private fun validateFlightNumber(flightNumber: String): Boolean {
        val flightNumberRegex = """^[A-Z]{2,3}\d{1,4}$""".toRegex(RegexOption.IGNORE_CASE)
        return flightNumberRegex.matches(flightNumber)
    }

    private fun setupObservers() {
        viewModel.flightData.observe(this, Observer { flightData ->
            flightData?.let {
                displayFlightInfo(it)
            }
        })

        viewModel.allFlightData.observe(this, Observer { allFlightData ->
            currentFlightList = allFlightData

            if (allFlightData.size > 1) {
                datesListTitle.text = getString(R.string.multiple_dates_found, allFlightData.size)
                datesListTitle.visibility = View.VISIBLE

                showFlightDatesList()

                val adapter = FlightDateAdapter(allFlightData) { selectedFlight ->
                    showFlightDetails(selectedFlight)
                }
                flightDatesRecyclerView.adapter = adapter
            } else {
                datesListTitle.visibility = View.GONE
                flightDatesRecyclerView.visibility = View.GONE
                flightDetailsHeader.visibility = View.GONE
                flightInfoScrollView.visibility = View.VISIBLE
            }
        })

        viewModel.errorMessage.observe(this, Observer { errorMsg ->
            if (errorMsg.isNotEmpty()) {
                showNoFlightInfoAvailable(errorMsg)
                flightDatesRecyclerView.visibility = View.GONE
                flightInfoScrollView.visibility = View.VISIBLE

                if (isTracking) {
                    stopTracking()
                }
            }
        })

        viewModel.isLoading.observe(this, Observer { isLoading ->
            loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE

            if (isLoading && flightInfoTextView.text.isEmpty()) {
                flightInfoScrollView.visibility = View.GONE
            }
        })
    }

    private fun showFlightDatesList() {
        flightDatesRecyclerView.visibility = View.VISIBLE
        datesListTitle.visibility = View.VISIBLE
        flightInfoScrollView.visibility = View.GONE
        flightDetailsHeader.visibility = View.GONE
        isShowingFlightDetails = false
    }

    @SuppressLint("SetTextI18n")
    private fun showFlightDetails(flightData: FlightData) {
        flightDatesRecyclerView.visibility = View.GONE
        datesListTitle.visibility = View.GONE
        flightInfoScrollView.visibility = View.VISIBLE
        flightDetailsHeader.visibility = View.VISIBLE
        isShowingFlightDetails = true

        flightDateTitle.text = "Flight on ${flightData.flightDate ?: "Unknown Date"}"
        displayFlightInfo(flightData)
    }

    @SuppressLint("SetTextI18n")
    private fun startTracking(flightNumber: String) {
        isTracking = true
        trackButton.text = getString(R.string.stop_tracking)
        flightInfoTextView.text = "Connecting to flight tracking service..."
        flightDatesRecyclerView.visibility = View.GONE
        datesListTitle.visibility = View.GONE
        flightDetailsHeader.visibility = View.GONE
        flightInfoScrollView.visibility = View.VISIBLE
        loadingIndicator.visibility = View.VISIBLE
        isShowingFlightDetails = false

        performDnsDiagnosis()

        lifecycleScope.launch {
            viewModel.startTracking(flightNumber)
        }
    }

    private fun stopTracking() {
        isTracking = false
        trackButton.text = getString(R.string.track_flight)
        viewModel.stopTracking()
        showToast(getString(R.string.tracking_stopped))
    }

    private fun displayFlightInfo(flightData: FlightData) {
        val infoBuilder = StringBuilder()
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val enteredFlightNumber = flightNumberInput.text.toString().trim().uppercase()

        infoBuilder.append("📡 FLIGHT TRACKING UPDATE (as of $currentTime) 📡\n\n")

        flightData.flightDate?.let { date ->
            infoBuilder.append("📅 Date: $date\n\n")
        }

        val displayFlightNumber = if (enteredFlightNumber.isNotEmpty()) {
            enteredFlightNumber
        } else {
            flightData.flight.icao
        }

        infoBuilder.append("✈️ Flight: $displayFlightNumber (${flightData.flight.icao})\n")
        infoBuilder.append("🚥 Status: ${formatFlightStatus(flightData.status)}\n\n")

        flightData.departure?.let { dep ->
            infoBuilder.append("🛫 FROM: ${dep.airport ?: "N/A"} (${dep.iata ?: "N/A"})\n")
            infoBuilder.append("   Gate: ${dep.gate ?: "N/A"}, Terminal: ${dep.terminal ?: "N/A"}\n")
            val scheduledDep = formatApiTime(dep.scheduled)
            val actualDep = formatApiTime(dep.actual ?: dep.estimated)
            val delayText = if (dep.delay != null && dep.delay > 0) " (Delayed by ${dep.delay} min)" else ""
            infoBuilder.append("   Scheduled: $scheduledDep\n")
            infoBuilder.append("   Actual: $actualDep$delayText\n\n")
        } ?: infoBuilder.append("🛫 FROM: N/A\n\n")

        flightData.arrival?.let { arr ->
            infoBuilder.append("🛬 TO: ${arr.airport ?: "N/A"} (${arr.iata ?: "N/A"})\n")
            infoBuilder.append("   Gate: ${arr.gate ?: "N/A"}, Terminal: ${arr.terminal ?: "N/A"}\n")
            val scheduledArr = formatApiTime(arr.scheduled)
            val estimatedArr = formatApiTime(arr.estimated ?: arr.scheduled)
            val delayText = if (arr.delay != null && arr.delay > 0) " (Delayed by ${arr.delay} min)" else ""
            infoBuilder.append("   Scheduled: $scheduledArr\n")
            infoBuilder.append("   Estimated: $estimatedArr$delayText\n\n")
        } ?: infoBuilder.append("🛬 TO: N/A\n\n")

        flightData.aircraft?.let { aircraft ->
            infoBuilder.append("✈️ Aircraft: ${aircraft.iata ?: "N/A"} (${aircraft.registration ?: "N/A"})\n\n")
        }

        infoBuilder.append("📊 FLIGHT DATA:\n")
        if (flightData.live != null) {
            infoBuilder.append("• Altitude: ${formatAltitude(flightData.position?.altitude)}\n")
            infoBuilder.append("• Speed: ${formatSpeed(flightData.position?.speed)}\n")
            infoBuilder.append("• Location: ${formatCoordinates(flightData.position?.latitude, flightData.position?.longitude)}\n")
            infoBuilder.append("• Last updated: ${formatApiTime(flightData.live.updated)}\n")
        } else {
            infoBuilder.append("• Live tracking data not available\n")
            if (flightData.status == "scheduled") {
                infoBuilder.append("• Flight has not departed yet\n")
            } else if (flightData.status == "landed") {
                infoBuilder.append("• Flight has already landed\n")
            }
        }

        flightInfoTextView.text = infoBuilder.toString()
    }

    private fun formatFlightStatus(status: String?): String {
        return when (status?.lowercase()) {
            "scheduled" -> "Scheduled"
            "active" -> "In Flight"
            "landed" -> "Landed"
            "cancelled" -> "Cancelled"
            "diverted" -> "Diverted"
            "delayed" -> "Delayed"
            else -> status ?: "Unknown"
        }
    }

    private fun formatApiTime(apiTime: String?): String {
        if (apiTime.isNullOrEmpty()) return "N/A"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            val outputFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
            val date = inputFormat.parse(apiTime)
            date?.let { outputFormat.format(it) } ?: "N/A"
        } catch (_: Exception) {
            apiTime
        }
    }

    private fun formatAltitude(altitude: Int?): String {
        return if (altitude != null && altitude > 0) {
            "$altitude ft" + if (altitude > 30000) " (Cruising)" else " (Climbing/Descending)"
        } else "N/A"
    }

    private fun formatSpeed(speed: Int?): String {
        return if (speed != null && speed > 0) {
            "$speed knots (${(speed * 1.852).toInt()} km/h)"
        } else "N/A"
    }

    @SuppressLint("DefaultLocale")
    private fun formatCoordinates(latitude: Double?, longitude: Double?): String {
        return if (latitude != null && longitude != null) {
            String.format("%.4f° N, %.4f° E", latitude, longitude)
        } else "Position unavailable"
    }

    private fun showError(message: String) {
        showToast(message)
        showNoFlightInfoAvailable(message)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    @SuppressLint("SetTextI18n")
    private fun showNoFlightInfoAvailable(errorReason: String) {
        flightInfoTextView.text = "No flight info available.\n\nReason: $errorReason"
        flightInfoScrollView.visibility = View.VISIBLE
        flightDetailsHeader.visibility = View.GONE
        flightDatesRecyclerView.visibility = View.GONE
        datesListTitle.visibility = View.GONE
        isShowingFlightDetails = false
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo != null && networkInfo.isConnected
        }
    }

    override fun onResume() {
        super.onResume()
        if (isNetworkAvailable()) {
            viewModel.checkServerReachability { isReachable ->
                if (!isReachable) {
                    runOnUiThread {
                        showToast("Note: Flight tracking server appears to be unreachable. Using alternative connection methods.")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTracking) {
            viewModel.stopTracking()
        }
    }

    private fun performDnsDiagnosis() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                try {
                    val addresses = InetAddress.getAllByName("api.aviationstack.com")
                    // Log successful lookup
                    addresses.joinToString { "${it.hostName}/${it.hostAddress}" }
                } catch (_: Exception) {
                    // Log failure
                }
                try {
                    val process = Runtime.getRuntime().exec("nslookup api.aviationstack.com")
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val output = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                } catch (_: Exception) {
                    // Log nslookup failure
                }
                val isReachable = com.flight.flightq1.api.FlightApiService.isServerReachable()
                if (!isReachable) {
                    // Log use of hardcoded IPs
                }
            } catch (_: Exception) {
                // Log diagnosis error
            }
        }
    }

    override fun onBackPressed() {
        if (isShowingFlightDetails && currentFlightList.size > 1) {
            showFlightDatesList()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val themeItem = menu.findItem(R.id.action_toggle_theme)
        if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            themeItem.setIcon(R.drawable.ic_light_mode)
        } else {
            themeItem.setIcon(R.drawable.ic_dark_mode)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_theme -> {
                val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                when (currentNightMode) {
                    android.content.res.Configuration.UI_MODE_NIGHT_NO -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }
                    else -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                }
                true
            }
            R.id.action_view_statistics -> {
                val intent = Intent(this, FlightStatisticsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("flightNumber", flightNumberInput.text.toString())
        outState.putBoolean("isTracking", isTracking)
        outState.putBoolean("isShowingFlightDetails", isShowingFlightDetails)
        outState.putBoolean("showingFlightDatesList", flightDatesRecyclerView.isVisible)
        if (isTracking) {
            val currentFlightNumber = flightNumberInput.text.toString().trim()
            outState.putString("lastTrackedFlight", currentFlightNumber)
        }
    }
}
