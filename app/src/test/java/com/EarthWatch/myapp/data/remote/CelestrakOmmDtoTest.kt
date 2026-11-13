package com.EarthWatch.myapp.data.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CelestrakOmmDtoTest {
    @Test
    fun parsesOmmJsonAndKeepsExtendedCatalogIdentifiers() {
        val json = """{"OBJECT_NAME":"Test object","OBJECT_ID":"2024-001A","NORAD_CAT_ID":123456,"EPOCH":"2025-01-01T00:00:00.000Z","MEAN_MOTION":14.2,"MEAN_MOTION_DOT":0.00001,"MEAN_MOTION_DDOT":0.0,"ECCENTRICITY":0.001,"INCLINATION":51.6,"RA_OF_ASC_NODE":120.0,"ARG_OF_PERICENTER":80.0,"MEAN_ANOMALY":10.0,"BSTAR":0.0001,"ELEMENT_SET_NO":3,"REV_AT_EPOCH":4}"""
        val record = Gson().fromJson(json, CelestrakOmmDto::class.java)
        val orbit = record.toOrbit("Test")
        assertNotNull(orbit)
        assertEquals(123456L, orbit?.noradId)
        assertEquals("Test object", orbit?.name)
        assertEquals(14.2, orbit?.meanMotionRevPerDay ?: 0.0, 0.0)
        assertEquals("Test", orbit?.category)
    }

    @Test
    fun interpretsCelestrakTimezoneOmittedEpochAsUtc() {
        val orbit = CelestrakOmmDto(
            noradCatalogId = 7L,
            epoch = "2026-11-13T01:58:47.586144",
            meanMotion = 1.0,
            eccentricity = 0.1,
            inclination = 10.0,
            rightAscensionOfAscendingNode = 20.0,
            argumentOfPericenter = 30.0,
            meanAnomaly = 40.0,
        ).toOrbit("Test")
        assertEquals("2026-11-13T01:58:47.586144Z", orbit?.epoch)
    }

    @Test
    fun rejectsRecordsMissingRequiredOrbitalElements() {
        assertNull(CelestrakOmmDto(noradCatalogId = 1L, objectName = "Incomplete").toOrbit("Test"))
        assertNull(CelestrakOmmDto(noradCatalogId = 2L, epoch = "2025-01-01T00:00:00Z", meanMotion = 2.0, eccentricity = 1.0, inclination = 30.0, rightAscensionOfAscendingNode = 0.0, argumentOfPericenter = 0.0, meanAnomaly = 0.0).toOrbit("Test"))
    }
}
