package com.arianna.app.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.arianna.app.MainActivity
import com.arianna.app.R

/** Canales y armado de las notificaciones (listas compartidas y recordatorios). */
object Notifications {
    const val CHANNEL_LISTS = "listas"
    const val CHANNEL_REMINDERS = "recordatorios"

    fun listLink(listId: String) = "arianna://list/$listId"
    fun remindersLink(kind: String) = "arianna://reminders/$kind"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LISTS, "Listas compartidas", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Cuando alguien agrega algo a una lista que compartes" },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Recordatorios", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Pagos y mantenimientos por vencer o vencidos, y cuando alguien marca uno" },
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Muestra la notificación. Si trae `link` (ej. arianna://list/{id}), al tocarla se abre esa
     * pantalla. Las que tienen el mismo `tag` se reemplazan.
     */
    fun show(
        context: Context,
        title: String,
        body: String,
        link: String?,
        tag: String?,
        channel: String = CHANNEL_LISTS,
    ) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            if (link != null) {
                action = Intent.ACTION_VIEW
                data = Uri.parse(link)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            (tag ?: link ?: title).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_brand)
            .setColor(0xFF6A4BE0.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag, 0, notification)
        } catch (_: SecurityException) {
            // El usuario quitó el permiso entre la comprobación y el aviso.
        }
    }
}
