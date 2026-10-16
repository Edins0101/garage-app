/*
 * Nota (firebase-messaging 25.1+): token/deleteToken/onNewToken están marcados como obsoletos a favor
 * del registro por ID de instalación (onRegistered/register). Siguen funcionando; se migrará cuando
 * la documentación del envío desde el servidor con FID esté completa (ver docs/NOTIFICACIONES.md).
 */
@file:Suppress("DEPRECATION")

package com.arianna.app.data.push

import android.content.Context
import android.util.Log
import com.arianna.app.data.remote.ApiClient
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * Registra el token de notificaciones de este teléfono en el backend (POST /devices)
 * y lo da de baja al cerrar sesión. Si Firebase no está configurado (no hay
 * google-services.json), no hace nada: la app funciona igual, sin notificaciones.
 */
class PushRegistrar(
    private val context: Context,
    private val api: ApiClient?,
    /** Token de sesión actual, o null si no hay sesión. */
    private val sessionToken: () -> String?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Serializable
    private data class DeviceBody(val token: String, val platform: String = "android")

    val available: Boolean
        get() = api != null && FirebaseApp.getApps(context).isNotEmpty()

    /** Pide el token a Firebase y lo registra (al iniciar sesión o abrir la app con sesión). */
    fun sync() {
        if (!available || sessionToken() == null) return
        scope.launch {
            runCatching { register(FirebaseMessaging.getInstance().token.await()) }
                .onFailure { Log.w(TAG, "No se pudo registrar el dispositivo", it) }
        }
    }

    /** Firebase generó un token nuevo (ver AriannaMessagingService.onNewToken). */
    fun onNewToken(token: String) {
        if (!available || sessionToken() == null) return
        scope.launch {
            runCatching { register(token) }.onFailure { Log.w(TAG, "No se pudo registrar el token nuevo", it) }
        }
    }

    private suspend fun register(token: String) {
        val body = Json.encodeToString(DeviceBody.serializer(), DeviceBody(token))
        api!!.request<Unit>("POST", "/devices", body, null)
    }

    /**
     * Al cerrar sesión: avisa al backend con el token de la sesión que se cierra y borra el
     * token de Firebase, para que este teléfono no reciba avisos de esa cuenta.
     */
    fun unregister(closingSessionToken: String?) {
        if (!available) return
        scope.launch {
            runCatching {
                val messaging = FirebaseMessaging.getInstance()
                val token = messaging.token.await()
                if (closingSessionToken != null) {
                    val path = "/devices/" + URLEncoder.encode(token, "UTF-8")
                    api!!.request<Unit>("DELETE", path, null, null, authToken = closingSessionToken)
                }
                messaging.deleteToken().await()
            }.onFailure { Log.w(TAG, "No se pudo dar de baja el dispositivo", it) }
        }
    }

    private companion object {
        const val TAG = "PushRegistrar"
    }
}
