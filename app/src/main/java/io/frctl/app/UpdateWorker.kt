package io.frctl.app

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.frctl.app.data.MarketplaceRepository
import java.util.concurrent.TimeUnit

private const val UPDATE_WORK = "frctl-daily-updates"
private const val UPDATE_CHANNEL = "frctl-updates"

class FrctlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<CatalogUpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UPDATE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

class CatalogUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val names = MarketplaceRepository(applicationContext).checkTrackedUpdates()
        if (names.isNotEmpty()) notifyUpdates(names)
        Result.success()
    }.getOrElse { Result.retry() }

    private fun notifyUpdates(names: List<String>) {
        if (Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(UPDATE_CHANNEL, applicationContext.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(applicationContext, UPDATE_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.updates_available))
            .setContentText(names.take(3).joinToString())
            .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString()))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(7101, notification)
    }
}
