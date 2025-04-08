package com.flight.flightq1.settings

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.flight.flightq1.FlightQApplication
import com.flight.flightq1.R
import com.google.android.material.slider.Slider

class UpdateFrequencyActivity : AppCompatActivity() {

    private lateinit var frequencySlider: Slider
    private lateinit var frequencyValueText: TextView
    private lateinit var saveButton: Button
    private lateinit var currentScheduleText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_frequency)
        
        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Update Frequency Settings"
        
        // Initialize views
        frequencySlider = findViewById(R.id.slider_update_frequency)
        frequencyValueText = findViewById(R.id.tv_frequency_value)
        saveButton = findViewById(R.id.btn_save_frequency)
        currentScheduleText = findViewById(R.id.tv_current_schedule)
        
        // Get current setting
        val prefs = getSharedPreferences(FlightQApplication.PREF_FILE, Context.MODE_PRIVATE)
        val currentFrequency = prefs.getInt(
            FlightQApplication.PREF_UPDATE_FREQUENCY, 
            FlightQApplication.DEFAULT_UPDATE_FREQUENCY_HOURS
        )
        
        // Set current values
        frequencySlider.value = currentFrequency.toFloat()
        updateFrequencyDisplay(currentFrequency)
        
        // Set up slider listener
        frequencySlider.addOnChangeListener { _, value, _ ->
            val hours = value.toInt()
            updateFrequencyDisplay(hours)
        }
        
        // Set up save button
        saveButton.setOnClickListener {
            val hours = frequencySlider.value.toInt()
            
            // Save preference
            prefs.edit {
                putInt(FlightQApplication.PREF_UPDATE_FREQUENCY, hours)
            }
            
            // Update worker schedule
            FlightQApplication.getInstance().updateRefreshFrequency(hours)
            
            // Show confirmation
            Toast.makeText(
                this,
                "Flight updates will now run every $hours hours",
                Toast.LENGTH_SHORT
            ).show()
            
            finish()
        }
    }
    
    private fun updateFrequencyDisplay(hours: Int) {
        val timeText = when {
            hours < 1 -> "Less than hourly"
            hours == 1 -> "Every hour"
            hours < 24 -> "Every $hours hours"
            hours == 24 -> "Once daily"
            hours == 48 -> "Every 2 days"
            hours == 72 -> "Every 3 days"
            hours == 168 -> "Weekly"
            else -> "Every ${hours / 24} days"
        }
        
        frequencyValueText.text = timeText
        currentScheduleText.text = "Current setting: $timeText"
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
