package com.EarthWatch.myapp.data.remote

import com.EarthWatch.myapp.domain.model.SatelliteOrbit
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface CelestrakApi {
    @GET("gp.php")
    suspend fun getGroup(
        @Query("GROUP") group: String,
        @Query("FORMAT") format: String = "JSON",
    ): Response<List<CelestrakOmmDto>>
}

data class CelestrakOmmDto(
    @SerializedName("OBJECT_NAME") val objectName: String? = null,
    @SerializedName("OBJECT_ID") val objectId: String? = null,
    @SerializedName("NORAD_CAT_ID") val noradCatalogId: Long? = null,
    @SerializedName("EPOCH") val epoch: String? = null,
    @SerializedName("MEAN_MOTION") val meanMotion: Double? = null,
    @SerializedName("MEAN_MOTION_DOT") val meanMotionDot: Double? = null,
    @SerializedName("MEAN_MOTION_DDOT") val meanMotionDdot: Double? = null,
    @SerializedName("ECCENTRICITY") val eccentricity: Double? = null,
    @SerializedName("INCLINATION") val inclination: Double? = null,
    @SerializedName("RA_OF_ASC_NODE") val rightAscensionOfAscendingNode: Double? = null,
    @SerializedName("ARG_OF_PERICENTER") val argumentOfPericenter: Double? = null,
    @SerializedName("MEAN_ANOMALY") val meanAnomaly: Double? = null,
    @SerializedName("BSTAR") val bstar: Double? = null,
    @SerializedName("ELEMENT_SET_NO") val elementSetNumber: Int? = null,
    @SerializedName("REV_AT_EPOCH") val revolutionNumber: Int? = null,
    @SerializedName("EPHEMERIS_TYPE") val ephemerisType: Int? = null,
    @SerializedName("CLASSIFICATION_TYPE") val classificationType: String? = null,
)

fun CelestrakOmmDto.toOrbit(category: String): SatelliteOrbit? {
    val id = noradCatalogId ?: return null
    val title = objectName?.trim()?.takeIf(String::isNotEmpty) ?: "Catalog object $id"
    val epochValue = epoch?.trim()?.let { timestamp ->
        if (timestamp.endsWith("Z", ignoreCase = true) || timestamp.matches(Regex(".*[+-]\\d{2}:\\d{2}$"))) timestamp else "${timestamp}Z"
    } ?: return null
    val motion = meanMotion ?: return null
    val eccentricityValue = eccentricity ?: return null
    val inclinationValue = inclination ?: return null
    val raan = rightAscensionOfAscendingNode ?: return null
    val pericenter = argumentOfPericenter ?: return null
    val anomaly = meanAnomaly ?: return null
    if (motion <= 0.0 || eccentricityValue !in 0.0..<1.0 || inclinationValue !in 0.0..180.0) return null
    return SatelliteOrbit(
        noradId = id,
        name = title,
        internationalDesignator = objectId,
        category = category,
        epoch = epochValue,
        meanMotionRevPerDay = motion,
        meanMotionDotRevPerDay2 = meanMotionDot ?: 0.0,
        meanMotionDdotRevPerDay3 = meanMotionDdot ?: 0.0,
        eccentricity = eccentricityValue,
        inclinationDeg = inclinationValue,
        raanDeg = raan,
        argumentOfPericenterDeg = pericenter,
        meanAnomalyDeg = anomaly,
        bstar = bstar ?: 0.0,
        elementSetNumber = elementSetNumber ?: 0,
        revolutionNumber = revolutionNumber ?: 0,
    )
}

fun createCelestrakApi(): CelestrakApi {
    val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    return Retrofit.Builder()
        .baseUrl("https://celestrak.org/NORAD/elements/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CelestrakApi::class.java)
}
