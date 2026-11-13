package com.EarthWatch.myapp.core.orbit

import com.EarthWatch.myapp.domain.model.SatelliteOrbit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class Sgp4OrbitPropagatorTest {
    @Test
    fun matchesValladoSatellite00005TemeEpochPosition() {
        val orbit = SatelliteOrbit(
            noradId = 5,
            name = "VANGUARD 1",
            internationalDesignator = "1958-002B",
            category = "Test",
            epoch = "2000-06-27T18:50:19.733568Z",
            meanMotionRevPerDay = 10.82419157,
            meanMotionDotRevPerDay2 = 0.00000023,
            meanMotionDdotRevPerDay3 = 0.0,
            eccentricity = 0.1859667,
            inclinationDeg = 34.2682,
            raanDeg = 348.7242,
            argumentOfPericenterDeg = 331.7664,
            meanAnomalyDeg = 19.3264,
            bstar = 0.000028098,
            elementSetNumber = 475,
            revolutionNumber = 0,
        )
        val result = Sgp4OrbitPropagator().temePositionAt(orbit, Instant.parse(orbit.epoch))
        assertEquals(7022.46529266, result.x, 0.01)
        assertEquals(-1400.08296755, result.y, 0.01)
        assertEquals(0.03995155, result.z, 0.01)
    }
}
