package com.EarthWatch.myapp.feature.passes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.EarthWatch.myapp.R
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PassAlertWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val name = inputData.getString(KEY_NAME) ?: return Result.failure()
        val aosMillis = inputData.getLong(KEY_AOS_MILLIS, -1L)
        val losMillis = inputData.getLong(KEY_LOS_MILLIS, -1L)
        if (aosMillis <= 0L || losMillis < aosMillis || Instant.now().toEpochMilli() > losMillis) return Result.success()
        val passTime = DateTimeFormatter.ofPattern("HH:mm z", Locale.getDefault())
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(aosMillis))
        val minutesUntil = Duration.between(Instant.now(), Instant.ofEpochMilli(aosMillis)).toMinutes().coerceAtLeast(0)
        val maximumElevation = inputData.getDouble(KEY_MAX_ELEVATION, 0.0).toInt()
        val satelliteId = inputData.getLong(KEY_SATELLITE_ID, 0L)
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Satellite pass alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(applicationContext, aosMillis.toInt(), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pass_notification)
            .setContentTitle(if (minutesUntil > 0) "Satellite pass in $minutesUntil min" else "Satellite pass is starting")
            .setContentText("$name rises around $passTime · peak ${maximumElevation}°")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$name is predicted to rise above 10° around $passTime, with a peak elevation near ${maximumElevation}°. Pass times can shift as orbital elements are updated."))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify((aosMillis xor satelliteId).toInt(), notification) }
        return Result.success()
    }

    companion object {
        const val KEY_NAME = "satellite_name"
        const val KEY_AOS_MILLIS = "aos_millis"
        const val KEY_LOS_MILLIS = "los_millis"
        const val KEY_SATELLITE_ID = "satellite_id"
        const val KEY_MAX_ELEVATION = "maximum_elevation"
        private const val CHANNEL_ID = "satellite_pass_alerts"
    }
}
