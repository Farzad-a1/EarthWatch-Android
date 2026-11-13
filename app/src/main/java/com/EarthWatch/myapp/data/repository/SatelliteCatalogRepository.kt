package com.EarthWatch.myapp.data.repository

import android.content.Context
import com.EarthWatch.myapp.data.local.SatelliteOrbitCache
import com.EarthWatch.myapp.data.remote.CelestrakApi
import com.EarthWatch.myapp.data.remote.createCelestrakApi
import com.EarthWatch.myapp.data.remote.toOrbit
import com.EarthWatch.myapp.domain.model.SatelliteOrbit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class SatelliteCatalog(
    val orbits: List<SatelliteOrbit>,
    val fetchedAtMillis: Long?,
    val warning: String? = null,
)

class SatelliteCatalogRepository(
    context: Context,
    private val api: CelestrakApi = createCelestrakApi(),
    private val cache: SatelliteOrbitCache = SatelliteOrbitCache(context),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Group(val code: String, val category: String)

    private val groups = listOf(
        Group("resource", "Earth Observation"),
        Group("weather", "Weather"),
        Group("tdrss", "Communication"),
    )
    private val refreshIntervalMillis = 2 * 60 * 60 * 1000L

    suspend fun loadCatalog(): SatelliteCatalog = withContext(Dispatchers.IO) {
        val records = linkedMapOf<Long, SatelliteOrbit>()
        val fetchTimes = mutableListOf<Long>()
        val warnings = mutableListOf<String>()

        var stopFetching = false
        for (group in groups) {
            if (stopFetching) break
            var cached = cache.read(group.code)
            val isFresh = cached != null && nowMillis() - cached.fetchedAtMillis < refreshIntervalMillis
            if (!isFresh) {
                try {
                    val response = api.getGroup(group.code)
                    if (response.isSuccessful) {
                        val body = response.body().orEmpty()
                        if (body.isEmpty()) {
                            warnings += "${group.category}: CelesTrak returned no records."
                        } else {
                            val fetchedAt = nowMillis()
                            cache.write(group.code, body, fetchedAt)
                            cached = com.EarthWatch.myapp.data.local.CachedOrbitGroup(body, fetchedAt)
                        }
                    } else {
                        // CelesTrak asks clients to stop on every non-200 response; don't retry here.
                        warnings += "${group.category}: CelesTrak returned HTTP ${response.code()}."
                        stopFetching = true
                    }
                } catch (error: IOException) {
                    warnings += "${group.category}: ${error.message ?: "network request failed"}."
                    stopFetching = true
                } catch (error: RuntimeException) {
                    warnings += "${group.category}: CelesTrak returned data in an unexpected format."
                    stopFetching = true
                }
            }

            cached?.let { entry ->
                fetchTimes += entry.fetchedAtMillis
                entry.records.mapNotNull { it.toOrbit(group.category) }.forEach { orbit -> records.putIfAbsent(orbit.noradId, orbit) }
            }
        }

        SatelliteCatalog(
            orbits = records.values.sortedBy { it.name.lowercase() },
            fetchedAtMillis = fetchTimes.maxOrNull(),
            warning = warnings.takeIf(List<String>::isNotEmpty)?.joinToString(" "),
        )
    }
}
