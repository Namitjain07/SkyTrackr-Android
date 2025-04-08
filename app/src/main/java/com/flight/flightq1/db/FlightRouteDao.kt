package com.flight.flightq1.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FlightRouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: FlightRouteEntity)

    @Query("SELECT * FROM flight_routes ORDER BY timestamp DESC LIMIT 10")
    fun getRecentRoutes(): LiveData<List<FlightRouteEntity>>

    @Query("SELECT * FROM flight_routes WHERE departureIata = :departureIata AND arrivalIata = :arrivalIata LIMIT 1")
    suspend fun getRouteByIatas(departureIata: String, arrivalIata: String): FlightRouteEntity?
    
    // New query to get only the most recent search for each unique route
    @Query("SELECT * FROM flight_routes fr WHERE timestamp = (SELECT MAX(timestamp) FROM flight_routes WHERE departureIata = fr.departureIata AND arrivalIata = fr.arrivalIata)")
    fun getUniqueRoutes(): LiveData<List<FlightRouteEntity>>

    // Query to get routes that have highlighted flights
    @Query("SELECT * FROM flight_routes WHERE highlightedFlights IS NOT NULL AND highlightedFlights != '' ORDER BY timestamp DESC")
    fun getRoutesWithHighlightedFlights(): LiveData<List<FlightRouteEntity>>
    
    // Non-LiveData version for background worker
    @Query("SELECT * FROM flight_routes WHERE highlightedFlights IS NOT NULL AND highlightedFlights != '' ORDER BY timestamp DESC")
    suspend fun getRoutesWithHighlightedFlightsSync(): List<FlightRouteEntity>
}
