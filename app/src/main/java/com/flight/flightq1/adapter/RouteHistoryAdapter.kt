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
import com.flight.flightq1.db.FlightRouteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RouteHistoryAdapter(
    private val onItemClick: (FlightRouteEntity) -> Unit
) : ListAdapter<FlightRouteEntity, RouteHistoryAdapter.RouteViewHolder>(RouteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_history, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = getItem(position)
        holder.bind(route)
        holder.itemView.setOnClickListener { onItemClick(route) }
    }

    class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRoute: TextView = itemView.findViewById(R.id.tv_route)
        private val tvSearchTime: TextView = itemView.findViewById(R.id.tv_search_time)

        @SuppressLint("SetTextI18n")
        fun bind(route: FlightRouteEntity) {
            tvRoute.text = "${route.departureIata} → ${route.arrivalIata}"
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val date = Date(route.timestamp)
            
            // Indicate if this route has highlighted flights
            val highlightedInfo = if (!route.highlightedFlights.isNullOrEmpty()) {
                " (Has highlighted flights)"
            } else {
                ""
            }
            
            tvSearchTime.text = "Searched on ${dateFormat.format(date)}$highlightedInfo"
        }
    }

    class RouteDiffCallback : DiffUtil.ItemCallback<FlightRouteEntity>() {
        override fun areItemsTheSame(oldItem: FlightRouteEntity, newItem: FlightRouteEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FlightRouteEntity, newItem: FlightRouteEntity): Boolean {
            return oldItem.id == newItem.id &&
                   oldItem.departureIata == newItem.departureIata &&
                   oldItem.arrivalIata == newItem.arrivalIata &&
                   oldItem.timestamp == newItem.timestamp &&
                   oldItem.apiResponse == newItem.apiResponse &&
                   oldItem.highlightedFlights == newItem.highlightedFlights
        }
    }
}
