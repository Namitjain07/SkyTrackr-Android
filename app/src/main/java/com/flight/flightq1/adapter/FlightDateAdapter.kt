package com.flight.flightq1.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.flight.flightq1.R
import com.flight.flightq1.model.FlightData

class FlightDateAdapter(
    private val flights: List<FlightData>,
    private val onItemClick: (FlightData) -> Unit
) : RecyclerView.Adapter<FlightDateAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.flight_date_text)
        val statusText: TextView = view.findViewById(R.id.flight_status_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.flight_date_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val flight = flights[position]
        holder.dateText.text = flight.flightDate ?: "Unknown Date"
        holder.statusText.text = flight.status ?: "Unknown"
        
        // Apply appropriate status styling
        val context = holder.itemView.context
        when (flight.status?.lowercase()) {
            "active", "en route", "in air" -> {
                holder.statusText.setTextAppearance(context, R.style.FlightStatus_Active)
            }
            "delayed", "late" -> {
                holder.statusText.setTextAppearance(context, R.style.FlightStatus_Delayed)
            }
            "cancelled", "diverted" -> {
                holder.statusText.setTextAppearance(context, R.style.FlightStatus_Canceled)
            }
            else -> {
                holder.statusText.setTextAppearance(context, R.style.FlightStatus_Unknown)
            }
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(flight)
        }
    }

    override fun getItemCount() = flights.size
}
