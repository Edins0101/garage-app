package com.garaje.app.data.push

import com.garaje.app.GarajeApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recibe los mensajes de FCM. El backend manda mensajes solo de datos (title, body, listId, tag),
 * así la notificación se arma igual con la app abierta o cerrada.
 */
class GarajeMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        (application as GarajeApp).container.push.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body.orEmpty()
        Notifications.show(this, title, body, listId = data["listId"], tag = data["tag"])
    }
}
