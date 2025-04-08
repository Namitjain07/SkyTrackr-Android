package com.flight.flightq1.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FlightRouteEntity::class, FlightStatsEntity::class],
    version = 3,  // Increment version number
    exportSchema = false
)
abstract class FlightDatabase : RoomDatabase() {
    abstract fun flightRouteDao(): FlightRouteDao
    abstract fun flightStatsDao(): FlightStatsDao

    companion object {
        @Volatile
        private var INSTANCE: FlightDatabase? = null

        fun getDatabase(context: Context): FlightDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FlightDatabase::class.java,
                    "flight_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)  // Add migrations
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        // Migration from version 1 to version 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to the existing table
                database.execSQL("ALTER TABLE flight_routes ADD COLUMN apiResponse TEXT")
                database.execSQL("ALTER TABLE flight_routes ADD COLUMN highlightedFlights TEXT")
            }
        }
        
        // Migration from version 2 to version 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the flight stats table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS flight_stats (
                        flightIcao TEXT PRIMARY KEY NOT NULL,
                        flightIata TEXT,
                        airlineName TEXT NOT NULL,
                        departureIata TEXT NOT NULL,
                        arrivalIata TEXT NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        updateCount INTEGER NOT NULL,
                        lastDepartureDelay INTEGER NOT NULL,
                        lastArrivalDelay INTEGER NOT NULL,
                        avgDepartureDelay REAL NOT NULL,
                        avgArrivalDelay REAL NOT NULL
                    )
                    """
                )
            }
        }
    }
}
