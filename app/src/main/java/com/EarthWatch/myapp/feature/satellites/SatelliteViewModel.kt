package com.EarthWatch.myapp.feature.satellites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.EarthWatch.myapp.core.orbit.Sgp4OrbitPropagator
import com.EarthWatch.myapp.data.repository.SatelliteCatalogRepository
import com.EarthWatch.myapp.domain.model.GroundPosition
import com.EarthWatch.myapp.domain.model.GroundTrack
import com.EarthWatch.myapp.domain.model.SatelliteOrbit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

data class SatelliteUiState(
    val orbits: List<SatelliteOrbit> = emptyList(),
    val positions: List<GroundPosition> = emptyList(),
    val tracks: List<GroundTrack> = emptyList(),
    val fetchedAtMillis: Long? = null,
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

class SatelliteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SatelliteCatalogRepository(application)
    private val propagator = Sgp4OrbitPropagator()
    private val mutableState = MutableStateFlow(SatelliteUiState())
    val state: StateFlow<SatelliteUiState> = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private var positionJob: Job? = null
    private var positionTickerJob: Job? = null

    init {
        refreshCatalog()
    }

    fun refreshCatalog() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true)
            val catalog = runCatching { repository.loadCatalog() }.getOrElse { error ->
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    errorMessage = error.message ?: "The satellite catalog could not be loaded.",
                )
                return@launch
            }
            mutableState.value = mutableState.value.copy(
                orbits = catalog.orbits,
                fetchedAtMillis = catalog.fetchedAtMillis,
                loading = false,
                errorMessage = catalog.warning,
            )
            refreshPositions(includeTracks = true)
            if (positionTickerJob?.isActive != true) {
                positionTickerJob = viewModelScope.launch {
                    while (true) {
                        delay(POSITION_REFRESH_MILLIS)
                        refreshPositions(includeTracks = false)
                    }
                }
            }
        }
    }

    private fun refreshPositions(includeTracks: Boolean) {
        if (positionJob?.isActive == true) return
        val currentOrbits = mutableState.value.orbits.take(MAP_SATELLITE_LIMIT)
        positionJob = viewModelScope.launch {
            val now = Instant.now()
            val result = withContext(Dispatchers.Default) {
                val positions = currentOrbits.mapNotNull { orbit -> runCatching { propagator.positionAt(orbit, now) }.getOrNull() }
                val tracks = if (includeTracks) {
                    currentOrbits.take(GROUND_TRACK_LIMIT).mapNotNull { orbit -> runCatching { propagator.groundTrack(orbit, now) }.getOrNull() }
                } else {
                    mutableState.value.tracks
                }
                positions to tracks
            }
            mutableState.value = mutableState.value.copy(positions = result.first, tracks = result.second)
        }
    }

    companion object {
        private const val POSITION_REFRESH_MILLIS = 30_000L
        private const val MAP_SATELLITE_LIMIT = 80
        private const val GROUND_TRACK_LIMIT = 8
    }
}
