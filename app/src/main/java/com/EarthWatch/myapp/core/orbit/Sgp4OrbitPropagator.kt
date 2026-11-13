package com.EarthWatch.myapp.core.orbit

import com.EarthWatch.myapp.domain.model.GroundPosition
import com.EarthWatch.myapp.domain.model.GroundTrack
import com.EarthWatch.myapp.domain.model.SatelliteOrbit
import org.hipparchus.geometry.euclidean.threed.Vector3D
import org.orekit.propagation.analytical.tle.TLE
import org.orekit.propagation.analytical.tle.TLEPropagator
import org.orekit.time.AbsoluteDate
import org.orekit.time.TimeScalesFactory
import java.util.Date
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class TopocentricPosition(val azimuthDeg: Double, val elevationDeg: Double, val rangeKm: Double)

class Sgp4OrbitPropagator {
    fun prepare(orbit: SatelliteOrbit): PreparedOrbit = PreparedOrbit(orbit, createPropagator(orbit))

    fun positionAt(orbit: SatelliteOrbit, instant: Instant): GroundPosition = prepare(orbit).positionAt(instant)

    /** SGP4 TEME position in kilometers, useful for frame-aware calculations and reference tests. */
    fun temePositionAt(orbit: SatelliteOrbit, instant: Instant): Vector3D = prepare(orbit).temePositionAt(instant)

    fun groundTrack(orbit: SatelliteOrbit, instant: Instant, samples: Int = 25): GroundTrack {
        val periodSeconds = 86_400.0 / orbit.meanMotionRevPerDay
        val start = instant.minusMillis((periodSeconds * 500.0).toLong())
        val trajectory = prepare(orbit)
        val points = (0 until samples).map { index ->
            val sampleTime = start.plusMillis((periodSeconds * 1000.0 * index / (samples - 1).coerceAtLeast(1)).toLong())
            val position = trajectory.positionAt(sampleTime)
            position.latitudeDeg to position.longitudeDeg
        }
        return GroundTrack(orbit.noradId, points)
    }

    inner class PreparedOrbit internal constructor(
        private val orbit: SatelliteOrbit,
        private val propagator: TLEPropagator,
    ) {
        fun temePositionAt(instant: Instant): Vector3D {
            val date = AbsoluteDate(Date.from(instant), TimeScalesFactory.getTAI())
            return propagator.propagate(date).position.scalarMultiply(1.0e-3)
        }

        fun positionAt(instant: Instant): GroundPosition = groundPosition(orbit, temePositionAt(instant), instant)

        fun topocentricAt(latitudeDeg: Double, longitudeDeg: Double, instant: Instant): TopocentricPosition {
            val ecef = temeToEcef(temePositionAt(instant), instant)
            val latitude = Math.toRadians(latitudeDeg)
            val longitude = Math.toRadians(longitudeDeg)
            val sinLat = sin(latitude)
            val cosLat = cos(latitude)
            val sinLon = sin(longitude)
            val cosLon = cos(longitude)
            val normalRadius = WGS84_A / sqrt(1.0 - WGS84_E2 * sinLat * sinLat)
            val siteX = normalRadius * cosLat * cosLon
            val siteY = normalRadius * cosLat * sinLon
            val siteZ = normalRadius * (1.0 - WGS84_E2) * sinLat
            val dx = ecef.x - siteX
            val dy = ecef.y - siteY
            val dz = ecef.z - siteZ
            val east = -sinLon * dx + cosLon * dy
            val north = -sinLat * cosLon * dx - sinLat * sinLon * dy + cosLat * dz
            val up = cosLat * cosLon * dx + cosLat * sinLon * dy + sinLat * dz
            return TopocentricPosition(
                azimuthDeg = ((Math.toDegrees(atan2(east, north)) % 360.0) + 360.0) % 360.0,
                elevationDeg = Math.toDegrees(atan2(up, hypot(east, north))),
                rangeKm = sqrt(dx * dx + dy * dy + dz * dz),
            )
        }
    }

    private fun createPropagator(orbit: SatelliteOrbit): TLEPropagator {
        // OMM epochs and app Instants are UTC/POSIX timestamps. Treat both uniformly as TAI
        // coordinates so elapsed seconds match the UTC catalog convention without external
        // Orekit UTC history resources; SGP4 uses the resulting elapsed duration only here.
        val timeScale = TimeScalesFactory.getTAI()
        val epoch = AbsoluteDate(Date.from(Instant.parse(orbit.epoch)), timeScale)
        val tle = TLE(
            orbit.noradId.toInt(),
            'U',
            0,
            0,
            "",
            TLE.DEFAULT,
            orbit.elementSetNumber,
            epoch,
            orbit.meanMotionRevPerDay * 2.0 * Math.PI / SECONDS_PER_DAY,
            orbit.meanMotionDotRevPerDay2 * 2.0 * Math.PI / (SECONDS_PER_DAY * SECONDS_PER_DAY),
            orbit.meanMotionDdotRevPerDay3 * 2.0 * Math.PI / (SECONDS_PER_DAY * SECONDS_PER_DAY * SECONDS_PER_DAY),
            orbit.eccentricity,
            Math.toRadians(orbit.inclinationDeg),
            Math.toRadians(orbit.argumentOfPericenterDeg),
            Math.toRadians(orbit.raanDeg),
            Math.toRadians(orbit.meanAnomalyDeg),
            orbit.revolutionNumber,
            orbit.bstar,
            timeScale,
        )
        return TLEPropagator.selectExtrapolator(tle)
    }

    private fun groundPosition(orbit: SatelliteOrbit, teme: Vector3D, instant: Instant): GroundPosition {
        val ecef = temeToEcef(teme, instant)
        val x = ecef.x
        val y = ecef.y
        val z = ecef.z
        val longitude = normalizeLongitude(Math.toDegrees(atan2(y, x)))
        val latitude = geodeticLatitude(Math.toDegrees(atan2(z, hypot(x, y))), hypot(x, y), z)
        val altitude = altitudeKm(latitude, hypot(x, y), z)
        return GroundPosition(orbit.noradId, orbit.name, latitude, longitude, altitude)
    }

    private fun temeToEcef(teme: Vector3D, instant: Instant): Vector3D {
        val julianDate = 2_440_587.5 + (instant.toEpochMilli() / 86_400_000.0)
        val gmst = greenwichSiderealAngle(julianDate)
        val cosTheta = cos(gmst)
        val sinTheta = sin(gmst)
        val x = cosTheta * teme.x + sinTheta * teme.y
        val y = -sinTheta * teme.x + cosTheta * teme.y
        val z = teme.z
        return Vector3D(x, y, z)
    }

    private fun greenwichSiderealAngle(julianDate: Double): Double {
        val daysSinceJ2000 = julianDate - 2_451_545.0
        val centuries = daysSinceJ2000 / 36_525.0
        val degrees = 280.46061837 + 360.98564736629 * daysSinceJ2000 +
            0.000387933 * centuries * centuries - centuries * centuries * centuries / 38_710_000.0
        return Math.toRadians(((degrees % 360.0) + 360.0) % 360.0)
    }

    private fun geodeticLatitude(geocentricLatitudeDeg: Double, radiusKm: Double, zKm: Double): Double {
        val latitude = Math.toRadians(geocentricLatitudeDeg)
        var result = latitude
        repeat(6) {
            val sinLat = sin(result)
            val normalRadius = WGS84_A / sqrt(1.0 - WGS84_E2 * sinLat * sinLat)
            result = atan2(zKm + WGS84_E2 * normalRadius * sinLat, radiusKm)
        }
        return Math.toDegrees(result)
    }

    private fun altitudeKm(latitudeDeg: Double, radiusKm: Double, zKm: Double): Double {
        val latitude = Math.toRadians(latitudeDeg)
        val sinLat = sin(latitude)
        val normalRadius = WGS84_A / sqrt(1.0 - WGS84_E2 * sinLat * sinLat)
        return if (kotlin.math.abs(cos(latitude)) > 1.0e-8) {
            radiusKm / cos(latitude) - normalRadius
        } else {
            zKm / sin(latitude) - normalRadius * (1.0 - WGS84_E2)
        }
    }

    private fun normalizeLongitude(degrees: Double): Double = ((degrees + 540.0) % 360.0) - 180.0

    private companion object {
        const val SECONDS_PER_DAY = 86_400.0
        const val WGS84_A = 6_378.137
        const val WGS84_E2 = 6.69437999014e-3
    }
}
