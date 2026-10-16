package com.arianna.app.data.reminders

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arianna.app.AriannaApp
import com.arianna.app.data.push.Notifications
import com.arianna.app.domain.model.Reminder
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.util.Dates
import com.arianna.app.util.Money
import com.arianna.app.util.Recurrence
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Un aviso que hay que mostrar hoy. */
data class ReminderNotice(val key: String, val title: String, val body: String, val reminder: Reminder)

/**
 * Qué avisar hoy (sin depender de Android, para poder probarlo):
 * - el día que el usuario eligió ("3 días antes"),
 * - el día del vencimiento,
 * - si está vencido: al día siguiente y luego una vez por semana.
 */
object ReminderRules {
    fun notices(reminders: List<Reminder>, today: LocalDate): List<ReminderNotice> = reminders
        .filterNot { it.finished }
        .mapNotNull { reminder ->
            val days = Recurrence.daysUntil(reminder, today)
            val title = when {
                days == 0L -> "Hoy vence «${reminder.title}»"
                days > 0 && days == reminder.notifyDaysBefore.toLong() ->
                    if (days == 1L) "«${reminder.title}» vence mañana" else "«${reminder.title}» vence en $days días"
                days < 0 && (days == -1L || -days % 7 == 0L) ->
                    "«${reminder.title}» está vencido desde el ${Dates.shortDay(reminder.dueDate).lowercase()}"
                else -> return@mapNotNull null
            }
            val action = if (reminder.kind == ReminderKind.PAYMENTS) "Toca para marcarlo como pagado" else "Toca para marcarlo como hecho"
            val body = listOfNotNull(reminder.amount?.let(Money::format), action).joinToString(" · ")
            ReminderNotice("${reminder.id}|${reminder.nextDue}|$days", title, body, reminder)
        }
}

/**
 * Revisa los recordatorios una vez al día y muestra los avisos. Si no hay internet usa la
 * última copia guardada, así el aviso llega igual (los recordatorios se calculan en el teléfono).
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AriannaApp).container
        val userId = container.auth.session.value?.user?.id ?: return Result.success()
        val store = ReminderSnapshotStore(applicationContext)
        val reminders = ENABLED_KINDS.flatMap { kind ->
            runCatching { container.reminders.observeReminders(kind).first() }
                .onSuccess { store.save(userId, kind, it) }
                .getOrElse {
                    Log.w(TAG, "Sin conexión: uso la última copia de ${kind.apiName}", it)
                    store.load(userId, kind)
                }
        }
        ReminderRules.notices(reminders, LocalDate.now())
            .filter { store.markSent(it.key) }
            .forEach { notice ->
                Notifications.show(
                    applicationContext,
                    notice.title,
                    notice.body,
                    link = Notifications.remindersLink(notice.reminder.kind.apiName),
                    tag = "reminder-${notice.reminder.id}",
                    channel = Notifications.CHANNEL_REMINDERS,
                )
            }
        return Result.success()
    }

    companion object {
        private const val TAG = "ReminderWorker"
        private const val DAILY = "recordatorios-diario"
        private const val NOW = "recordatorios-ahora"

        /** Por ahora la app solo muestra Pagos; Mi hogar y Mi carro se suman aquí. */
        val ENABLED_KINDS = listOf(ReminderKind.PAYMENTS)

        /** Revisión diaria cerca de las 8:00 y una revisión ya mismo (al abrir la app). */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(8, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val daily = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, daily)
            manager.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build())
        }

        fun cancel(context: Context) {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(DAILY)
            manager.cancelUniqueWork(NOW)
        }
    }
}

/** Última copia de los recordatorios (por usuario) y avisos ya mostrados, para no repetirlos. */
class ReminderSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("recordatorios", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Reminder.serializer())

    fun save(userId: String, kind: ReminderKind, reminders: List<Reminder>) =
        prefs.edit { putString("snapshot-$userId-${kind.apiName}", json.encodeToString(serializer, reminders)) }

    fun load(userId: String, kind: ReminderKind): List<Reminder> =
        prefs.getString("snapshot-$userId-${kind.apiName}", null)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    /** true si es la primera vez que se muestra este aviso. */
    @Synchronized
    fun markSent(key: String): Boolean {
        val sent = prefs.getStringSet("sent", emptySet()).orEmpty()
        if (key in sent) return false
        // Basta con recordar los últimos avisos: la clave incluye la fecha de vencimiento.
        prefs.edit { putStringSet("sent", (sent.toList().takeLast(300) + key).toSet()) }
        return true
    }

    fun clear() = prefs.edit { clear() }
}
