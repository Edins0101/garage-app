package com.arianna.app.data.appliances

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arianna.app.data.push.Notifications
import com.arianna.app.domain.model.Appliance
import com.arianna.app.domain.repository.AppliancesRepository
import java.time.Duration
import java.time.Instant

/**
 * Aviso en MI teléfono cuando termina el tiempo que dije ("¿Ya terminaste con la Lavadora?"),
 * para que la libere y los demás se enteren. Se cancela si la libero antes.
 */
object ApplianceEndReminder {
    private fun name(applianceId: String) = "appliance-end-$applianceId"

    fun schedule(context: Context, appliance: Appliance) {
        val end = appliance.expectedEndInstant ?: return cancel(context, appliance.id)
        val delay = Duration.between(Instant.now(), end).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<ApplianceEndWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf(KEY_ID to appliance.id, KEY_NAME to appliance.name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(appliance.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, applianceId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(name(applianceId))
    }

    internal const val KEY_ID = "id"
    internal const val KEY_NAME = "name"
}

class ApplianceEndWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getString(ApplianceEndReminder.KEY_ID) ?: return Result.success()
        val name = inputData.getString(ApplianceEndReminder.KEY_NAME) ?: "el equipo"
        Notifications.show(
            applicationContext,
            title = "¿Ya terminaste con $name?",
            body = "Se acabó el tiempo que dijiste. Toca para liberarlo y que los demás sepan que está libre.",
            link = Notifications.appliancesLink(),
            tag = "appliance-end-$id",
            channel = Notifications.CHANNEL_APPLIANCES,
        )
        return Result.success()
    }
}

/** Programa o cancela el aviso de fin según lo que pase con el equipo. */
fun AppliancesRepository.withEndReminder(context: Context): AppliancesRepository {
    val delegate = this
    return object : AppliancesRepository by delegate {
        override suspend fun start(appliance: Appliance, minutes: Int?): Appliance =
            delegate.start(appliance, minutes).also { ApplianceEndReminder.schedule(context, it) }

        override suspend fun finish(appliance: Appliance): Appliance =
            delegate.finish(appliance).also { ApplianceEndReminder.cancel(context, appliance.id) }

        override suspend fun delete(appliance: Appliance) =
            delegate.delete(appliance).also { ApplianceEndReminder.cancel(context, appliance.id) }
    }
}
