package com.EarthWatch.myapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.EarthWatch.myapp.data.local.db.EarthWatchDatabase
import com.EarthWatch.myapp.data.local.db.SatelliteCacheEntity
import com.EarthWatch.myapp.data.local.db.SavedLocationEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EarthWatchDatabaseTest {
    private lateinit var database: EarthWatchDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, EarthWatchDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() { database.close() }

    @Test
    fun savesLocationAndCatalogGroupAcrossDaoReads() {
        val savedDao = database.savedLocationDao()
        savedDao.insert(SavedLocationEntity("tehran", "Tehran", 35.6892, 51.3890, 10L))
        assertEquals("Tehran", savedDao.getAll().single().name)
        assertEquals(35.6892, savedDao.getAll().single().latitudeDeg, 1e-8)

        val cacheDao = database.satelliteCacheDao()
        cacheDao.put(SatelliteCacheEntity("resource", "[]", 20L))
        assertEquals("[]", cacheDao.get("resource").recordsJson)
        assertEquals(20L, cacheDao.get("resource").fetchedAtMillis)
    }
}
