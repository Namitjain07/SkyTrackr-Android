package com.flight.flightq1

data class FlightData(
    val flight: FlightInfo,
    val status: String,
    val departure: DepartureInfo?,
    val arrival: ArrivalInfo?,
    val position: PositionInfo?,
    val updated: String
)

data class FlightInfo(
    val icao: String,
    val iata: String
)

data class DepartureInfo(
    val airport: String,
    val timezone: String,
    val scheduled: String
)

data class ArrivalInfo(
    val airport: String,
    val timezone: String,
    val scheduled: String
)

data class PositionInfo(
    val latitude: Double,
    val longitude: Double,
    val altitude: Int,
    val speed: Int
)
