package com.EarthWatch.myapp.data.local

import android.content.Context
import com.EarthWatch.myapp.data.remote.CelestrakOmmDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class CachedOrbitGroup(
    val records: List<CelestrakOmmDto>,
    val fetchedAtMillis: Long,
)

class SatelliteOrbitCache(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("orbit_catalog_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun read(group: String): CachedOrbitGroup? {
        val json = preferences.getString("$group.records", null) ?: return null
        val fetchedAt = preferences.getLong("$group.fetched_at", 0L)
        val type = object : TypeToken<List<CelestrakOmmDto>>() {}.type
        val records = runCatching { gson.fromJson<List<CelestrakOmmDto>>(json, type) }.getOrNull() ?: return null
        return CachedOrbitGroup(records, fetchedAt)
    }

    fun write(group: String, records: List<CelestrakOmmDto>, fetchedAtMillis: Long) {
        preferences.edit()
            .putString("$group.records", gson.toJson(records))
            .putLong("$group.fetched_at", fetchedAtMillis)
            .apply()
    }
}
