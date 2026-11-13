package com.EarthWatch.myapp.feature.passes

import com.EarthWatch.myapp.core.orbit.Sgp4OrbitPropagator
import com.EarthWatch.myapp.core.orbit.TopocentricPosition
import com.EarthWatch.myapp.domain.model.ObservingLocation
import com.EarthWatch.myapp.domain.model.SatelliteOrbit
import com.EarthWatch.myapp.domain.model.SatellitePass
import java.time.Duration
import java.time.Instant

class SatellitePassPredictor(
    private val trajectoryFor: (SatelliteOrbit, ObservingLocation) -> (Instant) -> TopocentricPosition = { orbit, location ->
        val prepared = Sgp4OrbitPropagator().prepare(orbit)
        val provider: (Instant) -> TopocentricPosition = { instant ->
            prepared.topocentricAt(location.latitudeDeg, location.longitudeDeg, instant)
        }
        provider
    },
) {
    fun predict(
        orbits: List<SatelliteOrbit>,
        location: ObservingLocation,
        start: Instant,
        horizon: Duration = Duration.ofHours(24),
        minimumElevationDeg: Double = 10.0,
        sampleStep: Duration = Duration.ofSeconds(60),
        maximumPasses: Int = 40,
    ): List<SatellitePass> {
        require(minimumElevationDeg in 0.0..90.0)
        require(!sampleStep.isNegative && !sampleStep.isZero)
        val end = start.plus(horizon)
        return orbits.flatMap { orbit ->
            val topocentricAt: (Instant) -> TopocentricPosition = trajectoryFor(orbit, location)
            fun elevationAt(instant: Instant) = topocentricAt(instant).elevationDeg
            fun refineCrossing(left: Instant, right: Instant, rising: Boolean): Instant {
                var low = left
                var high = right
                repeat(16) {
                    if (Duration.between(low, high).seconds <= 1) return@repeat
                    val mid = low.plusMillis(Duration.between(low, high).toMillis() / 2)
                    val visible = elevationAt(mid) >= minimumElevationDeg
                    if (visible == rising) high = mid else low = mid
                }
                return low.plusMillis(Duration.between(low, high).toMillis() / 2)
            }

            val passes = mutableListOf<SatellitePass>()
            var previousTime = start
            var previousElevation = topocentricAt(previousTime).elevationDeg
            var aos: Instant? = if (previousElevation >= minimumElevationDeg) start else null
            var peakElevation = if (aos != null) previousElevation else Double.NEGATIVE_INFINITY
            var peakAzimuth = 0.0

            while (previousTime < end && passes.size < maximumPasses) {
                val currentTime = minOf(previousTime.plus(sampleStep), end)
                val currentLook = topocentricAt(currentTime)
                val currentElevation = currentLook.elevationDeg
                if (aos == null && previousElevation < minimumElevationDeg && currentElevation >= minimumElevationDeg) {
                    aos = refineCrossing(previousTime, currentTime, rising = true)
                    peakElevation = currentElevation
                    peakAzimuth = currentLook.azimuthDeg
                } else if (aos != null && previousElevation >= minimumElevationDeg && currentElevation < minimumElevationDeg) {
                    val los = refineCrossing(previousTime, currentTime, rising = false)
                    passes += SatellitePass(orbit.noradId, orbit.name, aos, los, peakElevation, peakAzimuth)
                    aos = null
                    peakElevation = Double.NEGATIVE_INFINITY
                } else if (aos != null && currentElevation > peakElevation) {
                    peakElevation = currentElevation
                    peakAzimuth = currentLook.azimuthDeg
                }
                previousTime = currentTime
                previousElevation = currentElevation
            }
            aos?.let { openAos ->
                passes += SatellitePass(orbit.noradId, orbit.name, openAos, end, peakElevation, peakAzimuth)
            }
            passes
        }.sortedBy(SatellitePass::aos).take(maximumPasses)
    }
}
