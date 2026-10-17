package com.arianna.app.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Un aviso que hay que mostrar hoy. */
data class ReminderNotice(val key: String, val title: String, val body: String, val reminder: Reminder)

/** Un aviso con hora exacta (recordatorios con hora): se programa con AlarmManager. */
data class ReminderAlarm(val key: String, val at: LocalDateTime, val title: String, val body: String, val reminder: Reminder)

/**
 * Qué avisar hoy (sin depender de Android, para poder probarlo):
 * - el día que el usuario eligió ("3 días antes"),
 * - el día del vencimiento,
 * - si está vencido: al día siguiente y luego una vez por semana.
 * Los recordatorios con hora avisan a su hora (ver [alarms]); en la mañana solo se recuerdan si están vencidos.
 */
object ReminderRules {
    /** Cuántos días hacia adelante se programan las alarmas (se reprograman cada día y al abrir la app). */
    const val ALARM_WINDOW_DAYS = 8L

    fun notices(reminders: List<Reminder>, today: LocalDate): List<ReminderNotice> = reminders
        .filterNot { it.finished }
        .mapNotNull { reminder ->
            val days = Recurrence.daysUntil(reminder, today)
            if (reminder.time != null && days >= 0) return@mapNotNull null
            val title = when {
                days == 0L -> "Hoy vence «${reminder.title}»"
                days > 0 && days == reminder.notifyDaysBefore.toLong() ->
                    if (days == 1L) "«${reminder.title}» vence mañana" else "«${reminder.title}» vence en $days días"
                days < 0 && (days == -1L || -days % 7 == 0L) ->
                    "«${reminder.title}» está vencido desde el ${Dates.shortDay(reminder.dueDate).lowercase()}"
                else -> return@mapNotNull null
            }
            ReminderNotice("${reminder.id}|${reminder.nextDue}|$days", title, body(reminder), reminder)
        }

    private fun body(reminder: Reminder): String {
        val action = if (reminder.kind == ReminderKind.PAYMENTS) "Toca para marcarlo como pagado" else "Toca para marcarlo como hecho"
        return listOfNotNull(reminder.amount?.let(Money::format), action).joinToString(" · ")
    }

    /**
     * Avisos con hora de los próximos días. [listInfo] da el texto de la lista adjunta
     * (ej. "Faltan 8 cosas en «Súper»") o null si no tiene o no la puedo ver.
     */
    fun alarms(
        reminders: List<Reminder>,
        now: LocalDateTime,
        listInfo: (Reminder) -> String? = { null },
    ): List<ReminderAlarm> = reminders
        .filterNot { it.finished }
        .mapNotNull { reminder ->
            val time = reminder.time ?: return@mapNotNull null
            val due = reminder.dueDate.atTime(time)
            val minutes = (reminder.alertMinutes ?: 0).toLong()
            val at = due.minusMinutes(minutes)
            if (!at.isAfter(now) || at.isAfter(now.plusDays(ALARM_WINDOW_DAYS))) return@mapNotNull null
            val hour = Dates.time(time)
            val title = when {
                minutes == 0L -> "Es hora: «${reminder.title}»"
                at.toLocalDate() == due.toLocalDate() -> "«${reminder.title}» a las $hour"
                at.toLocalDate().plusDays(1) == due.toLocalDate() -> "«${reminder.title}» mañana a las $hour"
                else -> "«${reminder.title}» el ${Dates.shortDay(due.toLocalDate()).lowercase()} a las $hour"
            }
            val body = listOfNotNull(listInfo(reminder), body(reminder)).joinToString(" · ")
            ReminderAlarm("${reminder.id}|${reminder.nextDue}|$time|$minutes", at, title, body, reminder)
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
        val reminders = runCatching { container.reminders.observeReminders().first() }
            .onSuccess { store.save(userId, it) }
            .getOrElse {
                Log.w(TAG, "Sin conexión: uso la última copia guardada", it)
                store.load(userId)
            }
        val lists = runCatching { container.lists.observeLists().first() }.getOrDefault(emptyList())
        val alarms = ReminderRules.alarms(reminders, LocalDateTime.now()) { reminder ->
            lists.find { it.id == reminder.listId }?.let { list ->
                when (val pending = list.pendingCount) {
                    0 -> "«${list.title}» está completa"
                    1 -> "Falta 1 cosa en «${list.title}»"
                    else -> "Faltan $pending cosas en «${list.title}»"
                }
            }
        }
        ReminderAlarmScheduler.sync(applicationContext, alarms)
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

        /** Vuelve a revisar ya mismo (ej. después de crear o editar un recordatorio). */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build())
        }

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
            ReminderAlarmScheduler.sync(context, emptyList())
        }
    }
}

/** Última copia de los recordatorios (por usuario) y avisos ya mostrados, para no repetirlos. */
class ReminderSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("recordatorios", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Reminder.serializer())

    fun save(userId: String, reminders: List<Reminder>) =
        prefs.edit { putString("snapshot-$userId", json.encodeToString(serializer, reminders)) }

    fun load(userId: String): List<Reminder> =
        prefs.getString("snapshot-$userId", null)
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

/**
 * Programa los avisos con hora en AlarmManager. Si Android no deja usar alarmas exactas
 * (permiso "Alarmas y recordatorios" apagado), usa una ventana de 10 minutos.
 */
object ReminderAlarmScheduler {
    private const val PREFS = "recordatorios-alarmas"
    private const val KEYS = "programadas"
    private const val WINDOW_MS = 10 * 60 * 1000L

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Pantalla de ajustes donde se activan las alarmas exactas (Android 12+). */
    fun exactAlarmSettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))

    private fun intent(context: Context, key: String) =
        // La URI distingue un PendingIntent de otro (los extras no cuentan para eso).
        Intent(context, ReminderAlarmReceiver::class.java)
            .setAction("com.arianna.app.AVISO")
            .setData(Uri.parse("arianna-alarm://aviso/${Uri.encode(key)}"))
            .putExtra(ReminderAlarmReceiver.EXTRA_KEY, key)

    /** Deja programadas exactamente estas alarmas y cancela las que ya no van. */
    @Synchronized
    fun sync(context: Context, alarms: List<ReminderAlarm>) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wanted = alarms.associateBy { it.key }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        prefs.getStringSet(KEYS, emptySet()).orEmpty().filterNot { it in wanted }.forEach { key ->
            manager.cancel(PendingIntent.getBroadcast(context, 0, intent(context, key), flags))
        }
        val exact = canScheduleExact(context)
        alarms.forEach { alarm ->
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                intent(context, alarm.key)
                    .putExtra(ReminderAlarmReceiver.EXTRA_TITLE, alarm.title)
                    .putExtra(ReminderAlarmReceiver.EXTRA_BODY, alarm.body)
                    .putExtra(ReminderAlarmReceiver.EXTRA_KIND, alarm.reminder.kind.apiName)
                    .putExtra(ReminderAlarmReceiver.EXTRA_ID, alarm.reminder.id),
                flags,
            )
            val at = alarm.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                // Sin permiso de alarmas exactas: dentro de los 10 minutos siguientes.
                manager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pending)
            }
        }
        prefs.edit { putStringSet(KEYS, wanted.keys) }
    }
}

/** Muestra el aviso de un recordatorio con hora (una sola vez por vencimiento). */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        if (!ReminderSnapshotStore(context).markSent("alarm|$key")) return
        Notifications.show(
            context,
            intent.getStringExtra(EXTRA_TITLE) ?: return,
            intent.getStringExtra(EXTRA_BODY).orEmpty(),
            link = Notifications.remindersLink(intent.getStringExtra(EXTRA_KIND) ?: "all"),
            tag = "reminder-${intent.getStringExtra(EXTRA_ID)}",
            channel = Notifications.CHANNEL_REMINDERS,
        )
    }

    companion object {
        const val EXTRA_KEY = "clave"
        const val EXTRA_TITLE = "titulo"
        const val EXTRA_BODY = "texto"
        const val EXTRA_KIND = "categoria"
        const val EXTRA_ID = "recordatorio"
    }
}

/** Al reiniciar el teléfono (o actualizar la app) Android borra las alarmas: se vuelven a programar. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        ) {
            ReminderWorker.refreshNow(context)
        }
    }
}
