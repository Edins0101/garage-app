/*
 * Nota (firebase-messaging 25.1+): token/deleteToken/onNewToken están marcados como obsoletos a favor
 * del registro por ID de instalación (onRegistered/register). Siguen funcionando; se migrará cuando
 * la documentación del envío desde el servidor con FID esté completa (ver docs/NOTIFICACIONES.md).
 */
@file:Suppress("DEPRECATION")

package com.arianna.app.data.push

import com.arianna.app.AriannaApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recibe los mensajes de FCM. El backend manda mensajes solo de datos (title, body, listId, tag),
 * así la notificación se arma igual con la app abierta o cerrada.
 */
class AriannaMessagingService : FirebaseMessagingService() {

    @Deprecated("Ver nota al inicio del archivo")
    override fun onNewToken(token: String) {
        (application as AriannaApp).container.push.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body.orEmpty()
        Notifications.show(this, title, body, listId = data["listId"], tag = data["tag"])
    }
}
