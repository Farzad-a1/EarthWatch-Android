package com.EarthWatch.myapp.feature.passes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.EarthWatch.myapp.domain.model.SatellitePass
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

object PassAlertScheduler {
    private const val TAG_PASS_ALERT = "earthwatch-pass-alert"
    private const val ALERT_LEAD_MINUTES = 15L

    fun scheduleUpcoming(context: Context, passes: List<SatellitePass>) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val workManager = WorkManager.getInstance(context)
        val now = Instant.now()
        passes.forEach { pass ->
            val alertAt = pass.aos.minus(Duration.ofMinutes(ALERT_LEAD_MINUTES))
            val delayMillis = Duration.between(now, alertAt).toMillis()
            if (delayMillis <= 0L) return@forEach
            val key = "earthwatch-pass-${pass.satelliteId}-${pass.aos.epochSecond}"
            val data = Data.Builder()
                .putString(PassAlertWorker.KEY_NAME, pass.satelliteName)
                .putLong(PassAlertWorker.KEY_AOS_MILLIS, pass.aos.toEpochMilli())
                .putLong(PassAlertWorker.KEY_LOS_MILLIS, pass.los.toEpochMilli())
                .putLong(PassAlertWorker.KEY_SATELLITE_ID, pass.satelliteId)
                .putDouble(PassAlertWorker.KEY_MAX_ELEVATION, pass.maximumElevationDeg)
                .build()
            val request = OneTimeWorkRequestBuilder<PassAlertWorker>()
                .setInputData(data)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(TAG_PASS_ALERT)
                .build()
            workManager.enqueueUniqueWork(key, ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_PASS_ALERT)
    }
}
