package com.EarthWatch.myapp.data.local

import android.content.Context
import com.EarthWatch.myapp.data.local.db.EarthWatchDatabase
import com.EarthWatch.myapp.data.local.db.SatelliteCacheEntity
import com.EarthWatch.myapp.data.remote.CelestrakOmmDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class CachedOrbitGroup(
    val records: List<CelestrakOmmDto>,
    val fetchedAtMillis: Long,
)

interface SatelliteOrbitGroupCache {
    fun read(group: String): CachedOrbitGroup?
    fun write(group: String, records: List<CelestrakOmmDto>, fetchedAtMillis: Long)
}

class SatelliteOrbitCache(context: Context) : SatelliteOrbitGroupCache {
    private val cacheDao = EarthWatchDatabase.getInstance(context).satelliteCacheDao()
    private val legacyPreferences = context.applicationContext.getSharedPreferences("orbit_catalog_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    override fun read(group: String): CachedOrbitGroup? {
        val type = object : TypeToken<List<CelestrakOmmDto>>() {}.type
        val cached = cacheDao.get(group) ?: run {
            val legacyGroup = when (group) {
                "resource" -> "EARTH-RESOURCES"
                "weather" -> "WEATHER"
                "tdrss" -> "TDRSS"
                else -> group
            }
            val legacyJson = legacyPreferences.getString("$legacyGroup.records", null) ?: return null
            val legacyTime = legacyPreferences.getLong("$legacyGroup.fetched_at", 0L)
            val migrated = runCatching { gson.fromJson<List<CelestrakOmmDto>>(legacyJson, type) }.getOrNull() ?: return null
            cacheDao.put(SatelliteCacheEntity(group, legacyJson, legacyTime))
            return CachedOrbitGroup(migrated, legacyTime)
        }
        val records = runCatching { gson.fromJson<List<CelestrakOmmDto>>(cached.recordsJson, type) }.getOrNull() ?: return null
        return CachedOrbitGroup(records, cached.fetchedAtMillis)
    }

    override fun write(group: String, records: List<CelestrakOmmDto>, fetchedAtMillis: Long) {
        cacheDao.put(SatelliteCacheEntity(group, gson.toJson(records), fetchedAtMillis))
    }
}
