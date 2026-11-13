package com.EarthWatch.myapp.domain.model

data class SatelliteOrbit(
    val noradId: Long,
    val name: String,
    val internationalDesignator: String?,
    val category: String,
    val epoch: String,
    val meanMotionRevPerDay: Double,
    val meanMotionDotRevPerDay2: Double,
    val meanMotionDdotRevPerDay3: Double,
    val eccentricity: Double,
    val inclinationDeg: Double,
    val raanDeg: Double,
    val argumentOfPericenterDeg: Double,
    val meanAnomalyDeg: Double,
    val bstar: Double,
    val elementSetNumber: Int,
    val revolutionNumber: Int,
)

data class GroundPosition(
    val noradId: Long,
    val name: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeKm: Double,
)

data class GroundTrack(
    val noradId: Long,
    val points: List<Pair<Double, Double>>,
)

data class ObservingLocation(
    val name: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
)

data class SatellitePass(
    val satelliteId: Long,
    val satelliteName: String,
    val aos: java.time.Instant,
    val los: java.time.Instant,
    val maximumElevationDeg: Double,
    val azimuthAtMaximumDeg: Double,
)
