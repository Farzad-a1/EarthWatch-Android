package com.EarthWatch.myapp.feature.passes

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.EarthWatch.myapp.domain.model.ObservingLocation
import com.EarthWatch.myapp.domain.model.SatelliteOrbit
import com.EarthWatch.myapp.domain.model.SatellitePass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

data class PassUiState(
    val location: ObservingLocation = ObservingLocation("Tehran, Iran", 35.6892, 51.3890),
    val passes: List<SatellitePass> = emptyList(),
    val catalogAvailable: Boolean = false,
    val isCalculating: Boolean = false,
    val errorMessage: String? = null,
    val alertsEnabled: Boolean = false,
)

class PassViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("pass_preferences", Context.MODE_PRIVATE)
    private val predictor = SatellitePassPredictor()
    private val stateFlow = MutableStateFlow(
        PassUiState(
            location = ObservingLocation(
                preferences.getString(KEY_LOCATION_NAME, "Tehran, Iran") ?: "Tehran, Iran",
                preferences.getString(KEY_LATITUDE, null)?.toDoubleOrNull() ?: 35.6892,
                preferences.getString(KEY_LONGITUDE, null)?.toDoubleOrNull() ?: 51.3890,
            ),
            alertsEnabled = preferences.getBoolean(KEY_ALERTS_ENABLED, false),
        ),
    )
    val state: StateFlow<PassUiState> = stateFlow.asStateFlow()
    private var currentOrbits: List<SatelliteOrbit> = emptyList()
    private var predictionJob: Job? = null

    fun updateCatalog(orbits: List<SatelliteOrbit>) {
        currentOrbits = orbits
        stateFlow.value = stateFlow.value.copy(catalogAvailable = orbits.isNotEmpty())
        recalculate()
    }

    fun updateLocation(location: ObservingLocation) {
        val safeLocation = location.copy(
            name = location.name.trim().ifBlank { "Selected location" },
            latitudeDeg = location.latitudeDeg.coerceIn(-90.0, 90.0),
            longitudeDeg = (((location.longitudeDeg + 180.0) % 360.0) + 360.0) % 360.0 - 180.0,
        )
        stateFlow.value = stateFlow.value.copy(location = safeLocation)
        preferences.edit()
            .putString(KEY_LOCATION_NAME, safeLocation.name)
            .putString(KEY_LATITUDE, safeLocation.latitudeDeg.toString())
            .putString(KEY_LONGITUDE, safeLocation.longitudeDeg.toString())
            .apply()
        if (stateFlow.value.alertsEnabled) PassAlertScheduler.cancelAll(getApplication())
        recalculate()
    }

    fun setAlertsEnabled(enabled: Boolean) {
        stateFlow.value = stateFlow.value.copy(alertsEnabled = enabled)
        preferences.edit().putBoolean(KEY_ALERTS_ENABLED, enabled).apply()
        if (enabled) scheduleAlerts(stateFlow.value.passes) else PassAlertScheduler.cancelAll(getApplication())
    }

    fun recalculate() {
        if (currentOrbits.isEmpty()) {
            stateFlow.value = stateFlow.value.copy(passes = emptyList(), isCalculating = false)
            return
        }
        predictionJob?.cancel()
        val selectedLocation = stateFlow.value.location
        val selectedOrbits = currentOrbits.take(MAX_ORBITS)
        predictionJob = viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(isCalculating = true, errorMessage = null)
            val result = try {
                Result.success(withContext(Dispatchers.Default) {
                    predictor.predict(selectedOrbits, selectedLocation, Instant.now(), maximumPasses = MAX_PASSES)
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            stateFlow.value = stateFlow.value.copy(
                passes = result.getOrDefault(emptyList()),
                isCalculating = false,
                errorMessage = result.exceptionOrNull()?.message,
            )
            if (stateFlow.value.alertsEnabled) scheduleAlerts(stateFlow.value.passes)
        }
    }

    private fun scheduleAlerts(passes: List<SatellitePass>) {
        PassAlertScheduler.scheduleUpcoming(getApplication(), passes.take(MAX_ALERTS))
    }

    private companion object {
        const val KEY_LOCATION_NAME = "location_name"
        const val KEY_LATITUDE = "location_latitude"
        const val KEY_LONGITUDE = "location_longitude"
        const val KEY_ALERTS_ENABLED = "alerts_enabled"
        const val MAX_ORBITS = 400
        const val MAX_PASSES = 40
        const val MAX_ALERTS = 10
    }
}
