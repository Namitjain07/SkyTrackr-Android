package com.flight.flightq1.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FlightStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFlightStats(flightStats: FlightStatsEntity)
    
    @Query("SELECT * FROM flight_stats ORDER BY lastUpdated DESC")
    fun getAllFlightStats(): LiveData<List<FlightStatsEntity>>
    
    @Query("SELECT * FROM flight_stats WHERE flightIcao = :flightIcao")
    suspend fun getFlightStats(flightIcao: String): FlightStatsEntity?
    
    @Query("SELECT * FROM flight_stats ORDER BY avgDepartureDelay + avgArrivalDelay DESC LIMIT 30")
    fun getMostDelayedFlights(): LiveData<List<FlightStatsEntity>>
    
    @Query("SELECT * FROM flight_stats ORDER BY lastUpdated DESC LIMIT :limit")
    fun getRecentlyUpdatedFlights(limit: Int = 10): LiveData<List<FlightStatsEntity>>
}
