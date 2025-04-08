package com.flight.flightq1.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flight.flightq1.R
import com.flight.flightq1.db.FlightStatsEntity
import com.google.android.material.card.MaterialCardView

class FlightStatsAdapter : 
    ListAdapter<FlightStatsEntity, FlightStatsAdapter.FlightStatsViewHolder>(FlightStatsDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlightStatsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_flight_stats, parent, false)
        return FlightStatsViewHolder(view)
    }

    override fun onBindViewHolder(holder: FlightStatsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FlightStatsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val flightIdTextView: TextView = itemView.findViewById(R.id.tv_flight_id)
        private val flightRouteTextView: TextView = itemView.findViewById(R.id.tv_flight_route)
        private val avgDelayTextView: TextView = itemView.findViewById(R.id.tv_avg_delay)
        private val lastUpdatedTextView: TextView = itemView.findViewById(R.id.tv_last_updated)
        private val cardView: MaterialCardView = itemView as MaterialCardView

        @SuppressLint("SetTextI18n")
        fun bind(flightStats: FlightStatsEntity) {
            // Set the flight ID and airline
            flightIdTextView.text = "${flightStats.airlineName} (${flightStats.flightIata ?: flightStats.flightIcao})"
            
            // Set the route
            flightRouteTextView.text = flightStats.getRouteString()
            
            // Set the average delay information and flight time
            val depDelay = "%.1f".format(flightStats.avgDepartureDelay)
            val arrDelay = "%.1f".format(flightStats.avgArrivalDelay)
            avgDelayTextView.text = "Avg Delay: DEP $depDelay min / ARR $arrDelay min\nFlight Time: ${flightStats.getFormattedFlightTime()}"
            
            // Set the last updated time
            lastUpdatedTextView.text = "Last updated: ${flightStats.getLastUpdatedFormatted()}"
            
            // Set card border color based on delay status
            val context = itemView.context
            val strokeColor = when (flightStats.getDelayStatusColor()) {
                0 -> ContextCompat.getColor(context, R.color.status_active) // Green
                1 -> ContextCompat.getColor(context, R.color.status_delayed) // Yellow
                else -> ContextCompat.getColor(context, R.color.status_canceled) // Red
            }
            
            cardView.strokeColor = strokeColor
        }
    }

    class FlightStatsDiffCallback : DiffUtil.ItemCallback<FlightStatsEntity>() {
        override fun areItemsTheSame(oldItem: FlightStatsEntity, newItem: FlightStatsEntity): Boolean {
            return oldItem.flightIcao == newItem.flightIcao
        }

        override fun areContentsTheSame(oldItem: FlightStatsEntity, newItem: FlightStatsEntity): Boolean {
            return oldItem == newItem
        }
    }
}
