package com.EarthWatch.myapp.data.repository

import android.content.Context
import com.EarthWatch.myapp.data.local.CachedOrbitGroup
import com.EarthWatch.myapp.data.local.SatelliteOrbitCache
import com.EarthWatch.myapp.data.local.SatelliteOrbitGroupCache
import com.EarthWatch.myapp.data.remote.CelestrakApi
import com.EarthWatch.myapp.data.remote.CelestrakOmmDto
import com.EarthWatch.myapp.data.remote.createCelestrakApi
import com.EarthWatch.myapp.data.remote.toOrbit
import com.EarthWatch.myapp.domain.model.SatelliteOrbit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class SatelliteCatalog(
    val orbits: List<SatelliteOrbit>,
    val fetchedAtMillis: Long?,
    val warning: String? = null,
)

class SatelliteCatalogRepository(
    private val api: CelestrakApi,
    private val cache: SatelliteOrbitGroupCache,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val retryDelayMillis: Long = RETRY_DELAY_MILLIS,
) {
    constructor(context: Context) : this(createCelestrakApi(), SatelliteOrbitCache(context))

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
            var cached = cache.read(group.code)
            val isFresh = cached != null && nowMillis() - cached.fetchedAtMillis < refreshIntervalMillis
            if (!isFresh && stopFetching) {
                warnings += "${group.category}: skipped network request after an earlier CelesTrak error. ${cachedStatus(cached)}"
            } else if (!isFresh) {
                try {
                    val response = getGroupWithBoundedRetry(group.code)
                    if (response.code() == 200) {
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
                        warnings += "${group.category}: CelesTrak returned HTTP ${response.code()}; stopped requests as required. ${cachedStatus(cached)}"
                        stopFetching = true
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: IOException) {
                    val reason = if (error.hasTimeoutCause()) "request timed out" else "network request failed"
                    warnings += "${group.category}: $reason after $MAX_ATTEMPTS attempts. ${cachedStatus(cached)}"
                    stopFetching = true
                } catch (error: RuntimeException) {
                    warnings += "${group.category}: CelesTrak returned data in an unexpected format. ${cachedStatus(cached)}"
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

    private suspend fun getGroupWithBoundedRetry(groupCode: String): Response<List<CelestrakOmmDto>> {
        var lastFailure: IOException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return api.getGroup(groupCode)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                lastFailure = error
                if (attempt == MAX_ATTEMPTS || !error.isTransientNetworkFailure()) break
                delay(retryDelayMillis)
            }
        }
        throw lastFailure ?: IOException("CelesTrak request failed")
    }

    private fun cachedStatus(cached: CachedOrbitGroup?): String = if (cached == null) {
        "No cached copy is available."
    } else {
        "Showing cached data retrieved ${((nowMillis() - cached.fetchedAtMillis).coerceAtLeast(0L) / 60_000L)} minutes ago."
    }

    private fun Throwable.hasTimeoutCause(): Boolean = generateSequence(this) { it.cause }
        .any { it is SocketTimeoutException }

    private fun IOException.isTransientNetworkFailure(): Boolean =
        this is SocketTimeoutException || this is ConnectException || this is SocketException || this is UnknownHostException

    companion object {
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MILLIS = 750L
    }
}
