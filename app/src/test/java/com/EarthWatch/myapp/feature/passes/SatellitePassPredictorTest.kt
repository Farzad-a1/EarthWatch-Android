package com.EarthWatch.myapp.feature.passes

import com.EarthWatch.myapp.core.orbit.TopocentricPosition
import com.EarthWatch.myapp.domain.model.ObservingLocation
import com.EarthWatch.myapp.domain.model.SatelliteOrbit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SatellitePassPredictorTest {
    @Test
    fun findsAndRefinesAboveThresholdWindow() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val orbit = SatelliteOrbit(25544, "Test object", null, "Test", start.toString(), 15.0, 0.0, 0.0, 0.001, 51.0, 20.0, 30.0, 40.0, 0.0, 1, 1)
        val predictor = SatellitePassPredictor(trajectoryFor = { _, _ ->
            val provider: (Instant) -> TopocentricPosition = { instant ->
                val seconds = Duration.between(start, instant).seconds
                val elevation = when (seconds) {
                    in 120L..600L -> if (seconds == 360L) 42.0 else 20.0
                    else -> 0.0
                }
                TopocentricPosition(azimuthDeg = 90.0, elevationDeg = elevation, rangeKm = 600.0)
            }
            provider
        })

        val passes = predictor.predict(
            listOf(orbit), ObservingLocation("Site", 35.0, 51.0), start,
            horizon = Duration.ofMinutes(15), sampleStep = Duration.ofMinutes(1),
        )

        assertEquals(1, passes.size)
        assertTrue(kotlin.math.abs(Duration.between(start.plusSeconds(120), passes.single().aos).seconds) <= 1L)
        assertTrue(kotlin.math.abs(Duration.between(start.plusSeconds(600), passes.single().los).seconds) <= 1L)
        assertEquals(42.0, passes.single().maximumElevationDeg, 0.0)
        assertTrue(passes.single().los.isAfter(passes.single().aos))
    }
}
