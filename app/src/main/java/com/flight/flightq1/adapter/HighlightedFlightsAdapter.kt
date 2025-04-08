package com.flight.flightq1.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flight.flightq1.R
import com.flight.flightq1.model.FlightData
import java.util.Locale

class HighlightedFlightsAdapter(
    private val onItemClick: (FlightData, String, String) -> Unit
) : ListAdapter<HighlightedFlightsAdapter.HighlightedFlightItem, HighlightedFlightsAdapter.ViewHolder>(HighlightedFlightDiffCallback()) {

    // Data class to hold flight data with route information
    data class HighlightedFlightItem(
        val flightData: FlightData,
        val departureIata: String,
        val arrivalIata: String
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val flightNumberText: TextView = view.findViewById(R.id.tv_flight_number)
        val flightRouteText: TextView = view.findViewById(R.id.tv_flight_route)
        val flightStatusText: TextView = view.findViewById(R.id.tv_flight_status)

        @SuppressLint("SetTextI18n")
        fun bind(item: HighlightedFlightItem) {
            // Set flight number and airline info
            val airlineName = item.flightData.airline?.name ?: "Unknown Airline"
            val flightNumber = item.flightData.flight.iata ?: item.flightData.flight.icao
            flightNumberText.text = "$airlineName ($flightNumber)"
            
            // Set route info
            val depIata = item.departureIata
            val arrIata = item.arrivalIata
            flightRouteText.text = "$depIata → $arrIata"
            
            // Set status
            val status = formatStatus(item.flightData)
            flightStatusText.text = status
            
            // Apply style based on flight status
            val context = itemView.context
            when (item.flightData.status?.lowercase()) {
                "active", "en route", "in air" -> {
                    flightStatusText.setTextAppearance(context, R.style.FlightStatus_Active)
                }
                "delayed" -> {
                    flightStatusText.setTextAppearance(context, R.style.FlightStatus_Delayed)
                }
                "cancelled", "diverted" -> {
                    flightStatusText.setTextAppearance(context, R.style.FlightStatus_Canceled)
                }
                else -> {
                    flightStatusText.setTextAppearance(context, R.style.FlightStatus_Unknown)
                }
            }
        }
        
        private fun formatStatus(flight: FlightData): String {
            return flight.status?.let {
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
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_highlighted_flight, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        
        // Make it visually clear that the item is clickable
        holder.itemView.setOnClickListener { 
            onItemClick(item.flightData, item.departureIata, item.arrivalIata)
        }
        
        // Add a hint that tapping shows more details
        holder.flightNumberText.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, 0, R.drawable.ic_info, 0
        )
    }

    class HighlightedFlightDiffCallback : DiffUtil.ItemCallback<HighlightedFlightItem>() {
        override fun areItemsTheSame(oldItem: HighlightedFlightItem, newItem: HighlightedFlightItem): Boolean {
            return oldItem.flightData.flight.icao == newItem.flightData.flight.icao &&
                   oldItem.departureIata == newItem.departureIata &&
                   oldItem.arrivalIata == newItem.arrivalIata
        }

        override fun areContentsTheSame(oldItem: HighlightedFlightItem, newItem: HighlightedFlightItem): Boolean {
            return oldItem == newItem
        }
    }
}
