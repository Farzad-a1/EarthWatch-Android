package com.EarthWatch.myapp.feature.saved

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.EarthWatch.myapp.data.local.db.EarthWatchDatabase
import com.EarthWatch.myapp.data.local.db.SavedLocationEntity
import com.EarthWatch.myapp.domain.model.SavedPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class SavedLocationsState(
    val locations: List<SavedPlace> = emptyList(),
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

class SavedLocationsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = EarthWatchDatabase.getInstance(application).savedLocationDao()
    private val mutableState = MutableStateFlow(SavedLocationsState())
    val state: StateFlow<SavedLocationsState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.IO) { dao.getAll().map { it.toSavedPlace() } }
            }.onSuccess { locations ->
                mutableState.value = SavedLocationsState(locations, loading = false)
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(loading = false, errorMessage = error.message ?: "Saved locations could not be loaded.")
            }
        }
    }

    fun save(name: String, latitude: Double, longitude: Double) {
        if (name.isBlank() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dao.insert(SavedLocationEntity(UUID.randomUUID().toString(), name.trim(), latitude, longitude, System.currentTimeMillis()))
                    dao.getAll().map { it.toSavedPlace() }
                }
            }.onSuccess { locations -> mutableState.value = SavedLocationsState(locations, loading = false) }
                .onFailure { error -> mutableState.value = mutableState.value.copy(errorMessage = error.message ?: "Location could not be saved.") }
        }
    }

    fun delete(locationId: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dao.delete(locationId)
                    dao.getAll().map { it.toSavedPlace() }
                }
            }.onSuccess { locations -> mutableState.value = SavedLocationsState(locations, loading = false) }
                .onFailure { error -> mutableState.value = mutableState.value.copy(errorMessage = error.message ?: "Location could not be removed.") }
        }
    }

    private fun SavedLocationEntity.toSavedPlace() = SavedPlace(locationId, name, latitudeDeg, longitudeDeg)
}
