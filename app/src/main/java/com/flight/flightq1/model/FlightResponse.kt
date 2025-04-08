package com.flight.flightq1.model

import com.google.gson.annotations.SerializedName

data class FlightResponse(
    val pagination: Pagination? = null,
    val data: List<FlightData> = emptyList(),
    val error: ErrorInfo? = null
) {
    val success: Boolean
        get() = error == null
}

data class Pagination(
    val limit: Int = 0,
    val offset: Int = 0,
    val count: Int = 0,
    val total: Int = 0
)

data class ErrorInfo(
    val code: String? = null,
    val message: String? = null
)

data class FlightData(
    @SerializedName("flight_date") val flightDate: String? = null,
    @SerializedName("flight_status") val status: String? = null,
    val departure: DepartureInfo? = null,
    val arrival: ArrivalInfo? = null,
    val airline: AirlineInfo? = null,
    val flight: FlightInfo = FlightInfo("", "", "", null),
    val aircraft: AircraftInfo? = null,
    val live: LiveInfo? = null,
    val updated: String = ""
) {
    // Add a helper property to get position data from the live object
    val position: PositionInfo?
        get() = live?.let {
            PositionInfo(
                latitude = it.latitude,
                longitude = it.longitude,
                altitude = it.altitude?.toInt(),
                speed = it.speedHorizontal?.toInt()
            )
        }
}

data class DepartureInfo(
    val airport: String? = null,
    val timezone: String? = null,
    val iata: String? = null,
    val icao: String? = null,
    val terminal: String? = null,
    val gate: String? = null,
    val delay: Int? = null,
    val scheduled: String? = null,
    val estimated: String? = null,
    val actual: String? = null,
    @SerializedName("estimated_runway") val estimatedRunway: String? = null,
    @SerializedName("actual_runway") val actualRunway: String? = null
)

data class ArrivalInfo(
    val airport: String? = null,
    val timezone: String? = null,
    val iata: String? = null,
    val icao: String? = null,
    val terminal: String? = null,
    val gate: String? = null,
    val baggage: String? = null,
    val delay: Int? = null,
    val scheduled: String? = null,
    val estimated: String? = null,
    val actual: String? = null,
    @SerializedName("estimated_runway") val estimatedRunway: String? = null,
    @SerializedName("actual_runway") val actualRunway: String? = null
)

data class AirlineInfo(
    val name: String? = null,
    val iata: String? = null,
    val icao: String? = null
)

data class FlightInfo(
    val number: String? = null,
    val iata: String? = null,
    val icao: String = "",
    val codeshared: CodesharedInfo? = null
)

data class AircraftInfo(
    val registration: String? = null,
    val iata: String? = null,
    val icao: String? = null,
    val icao24: String? = null
)

data class LiveInfo(
    val updated: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val direction: Double? = null,
    @SerializedName("speed_horizontal") val speedHorizontal: Double? = null,
    @SerializedName("speed_vertical") val speedVertical: Double? = null,
    @SerializedName("is_ground") val isGround: Boolean? = null
)

data class CodesharedInfo(
    @SerializedName("airline_name") val airlineName: String? = null,
    @SerializedName("airline_iata") val airlineIata: String? = null,
    @SerializedName("airline_icao") val airlineIcao: String? = null,
    @SerializedName("flight_number") val flightNumber: String? = null,
    @SerializedName("flight_iata") val flightIata: String? = null,
    @SerializedName("flight_icao") val flightIcao: String? = null
)

data class PositionInfo(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Int? = null,
    val speed: Int? = null
)
