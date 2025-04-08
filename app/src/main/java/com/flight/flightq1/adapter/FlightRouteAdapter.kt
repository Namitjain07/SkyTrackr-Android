package com.flight.flightq1.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.flight.flightq1.R
import com.flight.flightq1.model.FlightData
import com.flight.flightq1.viewmodel.AirportFlightViewModel
import java.util.Locale

class FlightRouteAdapter(
    private val flights: List<FlightData>,
    private val viewModel: AirportFlightViewModel,
    private val onItemClick: (FlightData) -> Unit
) : RecyclerView.Adapter<FlightRouteAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val flightNumberText: TextView = view.findViewById(R.id.tv_flight_number)
        val flightRouteText: TextView = view.findViewById(R.id.tv_flight_route)
        val flightStatusText: TextView = view.findViewById(R.id.tv_flight_status)
        val checkboxHighlight: CheckBox = view.findViewById(R.id.checkbox_highlight)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_flight, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val flight = flights[position]
        
        // Set flight number and airline info
        val airlineName = flight.airline?.name ?: "Unknown Airline"
        val flightNumber = flight.flight.iata ?: flight.flight.icao
        holder.flightNumberText.text = "$airlineName ($flightNumber)"
        
        // Set route info
        val depIata = flight.departure?.iata ?: "---"
        val arrIata = flight.arrival?.iata ?: "---"
        holder.flightRouteText.text = "$depIata → $arrIata"
        
        // Set status and format it with appropriate styling
        val status = formatStatus(flight)
        holder.flightStatusText.text = status
        
        // Apply style based on flight status
        val context = holder.itemView.context
        when (flight.status?.lowercase()) {
            "active", "en route", "in air" -> {
                holder.flightStatusText.setTextAppearance(context, R.style.FlightStatus_Active)
            }
            "delayed" -> {
                holder.flightStatusText.setTextAppearance(context, R.style.FlightStatus_Delayed)
            }
            "cancelled", "diverted" -> {
                holder.flightStatusText.setTextAppearance(context, R.style.FlightStatus_Canceled)
            }
            else -> {
                holder.flightStatusText.setTextAppearance(context, R.style.FlightStatus_Unknown)
            }
        }
        
        // Set checkbox state without triggering the listener
        holder.checkboxHighlight.setOnCheckedChangeListener(null)
        holder.checkboxHighlight.isChecked = viewModel.isFlightSelected(flight)
        
        // Set checkbox click listener
        holder.checkboxHighlight.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && viewModel.getSelectedFlightsCount() >= viewModel.MAX_SELECTED_FLIGHTS) {
                // If trying to select more than the limit
                if (!viewModel.isFlightSelected(flight)) {
                    // Prevent selection and show message
                    holder.checkboxHighlight.isChecked = false
                    Toast.makeText(
                        holder.itemView.context,
                        "Maximum ${viewModel.MAX_SELECTED_FLIGHTS} flights can be highlighted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // Toggle selection in ViewModel
                viewModel.toggleFlightSelection(flight)
            }
        }
        
        // Set item click listener (separate from checkbox)
        holder.itemView.setOnClickListener {
            onItemClick(flight)
        }
    }
    
    private fun formatStatus(flight: FlightData): String {
        val status = flight.status?.let {
            when (it.lowercase()) {
                "scheduled" -> "Scheduled"
                "active" -> "In Flight"
                "landed" -> "Landed"
                "cancelled" -> "Cancelled"
                "diverted" -> "Diverted"
                "delayed" -> "Delayed"
                else -> it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        } ?: "Unknown"
        
        // Add delay information if available
        val depDelay = flight.departure?.delay
        val arrDelay = flight.arrival?.delay
        
        return when {
            depDelay != null && depDelay > 0 -> 
                "$status (Departure delayed: $depDelay min)"
            arrDelay != null && arrDelay > 0 -> 
                "$status (Arrival delayed: $arrDelay min)"
            else -> status
        }
    }

    override fun getItemCount() = flights.size
}
