package com.EarthWatch.myapp.data.repository

import com.EarthWatch.myapp.data.local.CachedOrbitGroup
import com.EarthWatch.myapp.data.local.SatelliteOrbitGroupCache
import com.EarthWatch.myapp.data.remote.CelestrakApi
import com.EarthWatch.myapp.data.remote.CelestrakOmmDto
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.net.SocketTimeoutException

class SatelliteCatalogRepositoryTest {
    @Test
    fun retriesTransientTimeoutOnceThenUsesCachedData() = runBlockingTest {
        val cache = FakeCache().apply {
            groups["resource"] = CachedOrbitGroup(listOf(validRecord()), 1_000L)
            groups["weather"] = CachedOrbitGroup(listOf(validRecord(12345L, "WEATHER TEST")), 2_000L)
        }
        var calls = 0
        val api = fakeApi { _, _ ->
            calls++
            throw SocketTimeoutException("read timed out")
        }

        val catalog = SatelliteCatalogRepository(api, cache, nowMillis = { 10_000_000L }, retryDelayMillis = 0L).loadCatalog()

        assertEquals(2, calls)
        assertEquals(2, catalog.orbits.size)
        assertTrue(catalog.orbits.any { it.noradId == 25544L })
        assertTrue(catalog.orbits.any { it.noradId == 12345L })
        assertTrue(catalog.warning.orEmpty().contains("timed out after 2 attempts"))
        assertTrue(catalog.warning.orEmpty().contains("Showing cached data"))
    }

    @Test
    fun stopsImmediatelyOnHttpErrorAndKeepsCache() = runBlockingTest {
        val cache = FakeCache().apply { groups["resource"] = CachedOrbitGroup(listOf(validRecord()), 1_000L) }
        var calls = 0
        val api = fakeApi { _, _ ->
            calls++
            Response.error(503, "temporarily unavailable".toResponseBody())
        }

        val catalog = SatelliteCatalogRepository(api, cache, nowMillis = { 10_000_000L }, retryDelayMillis = 0L).loadCatalog()

        assertEquals(1, calls)
        assertEquals(25544L, catalog.orbits.single().noradId)
        assertTrue(catalog.warning.orEmpty().contains("HTTP 503"))
        assertTrue(catalog.warning.orEmpty().contains("Showing cached data"))
    }

    private fun fakeApi(block: suspend (String, String) -> Response<List<CelestrakOmmDto>>) = object : CelestrakApi {
        override suspend fun getGroup(group: String, format: String): Response<List<CelestrakOmmDto>> = block(group, format)
    }

    private fun validRecord(noradId: Long = 25544L, name: String = "ISS (ZARYA)") = CelestrakOmmDto(
        objectName = name,
        objectId = "1998-067A",
        noradCatalogId = noradId,
        epoch = "2026-11-13T12:00:00Z",
        meanMotion = 15.5,
        eccentricity = 0.0005,
        inclination = 51.6,
        rightAscensionOfAscendingNode = 40.0,
        argumentOfPericenter = 30.0,
        meanAnomaly = 20.0,
    )

    private class FakeCache : SatelliteOrbitGroupCache {
        val groups = mutableMapOf<String, CachedOrbitGroup>()
        override fun read(group: String): CachedOrbitGroup? = groups[group]
        override fun write(group: String, records: List<CelestrakOmmDto>, fetchedAtMillis: Long) {
            groups[group] = CachedOrbitGroup(records, fetchedAtMillis)
        }
    }

    private fun runBlockingTest(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }
}
