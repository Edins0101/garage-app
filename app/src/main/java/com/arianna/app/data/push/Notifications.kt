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

/** Canal y armado de las notificaciones de listas compartidas. */
object Notifications {
    private const val CHANNEL_LISTS = "listas"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_LISTS,
            "Listas compartidas",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Cuando alguien agrega algo a una lista que compartes" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Muestra la notificación. Si trae `listId`, al tocarla se abre esa lista
     * (deep link arianna://list/{id}). Las que tienen el mismo `tag` se reemplazan.
     */
    fun show(context: Context, title: String, body: String, listId: String?, tag: String?) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            if (listId != null) {
                action = Intent.ACTION_VIEW
                data = Uri.parse("arianna://list/$listId")
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            (tag ?: listId ?: title).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_LISTS)
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
