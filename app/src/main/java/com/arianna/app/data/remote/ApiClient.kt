package com.arianna.app.data.remote

import com.arianna.app.domain.repository.DataException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP mínimo (sin dependencias extra) para el backend de docs/API.md.
 * Si el proyecto crece se puede cambiar por Retrofit o Ktor sin tocar los repositorios.
 */
class ApiClient(
    private val baseUrl: String,
    /** Token de la sesión actual (null sin sesión). */
    private val tokenProvider: () -> String?,
    /** Se llama cuando el servidor responde 401 (token vencido o inválido). */
    private val onUnauthorized: () -> Unit,
    /** Copia local de las respuestas para funcionar sin conexión (null = sin copia). */
    private val cache: ResponseCache? = null,
    /** false si el teléfono no tiene internet: se usa la copia sin esperar al servidor. */
    private val hasInternet: () -> Boolean = { true },
) {

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val _offline = MutableStateFlow(false)
    /** true mientras se muestran datos guardados porque no hay conexión. */
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    /**
     * Las lecturas (GET) se guardan en la copia local; sin conexión se responde con ella.
     * Los cambios necesitan internet: sin conexión fallan con un mensaje claro.
     */
    suspend fun <T> request(
        method: String,
        path: String,
        body: String? = null,
        responseSerializer: KSerializer<T>?,
        authToken: String? = null,
    ): T? {
        val cacheable = method == "GET" && cache != null && responseSerializer != null && !path.startsWith("/auth/")
        if (!hasInternet()) {
            if (cacheable) cached(path, responseSerializer!!)?.let { return it }
            _offline.value = true
            throw DataException(if (method == "GET") OFFLINE_EMPTY else OFFLINE_WRITE, DataException.Kind.NETWORK)
        }
        return try {
            send(method, path, body, responseSerializer, authToken) { text -> if (cacheable) cache!!.put(path, text) }
                .also { _offline.value = false }
        } catch (e: DataException) {
            if (e.kind != DataException.Kind.NETWORK) throw e
            if (cacheable) cached(path, responseSerializer!!)?.let { return it }
            throw if (method == "GET") e else DataException(OFFLINE_WRITE, DataException.Kind.NETWORK)
        }
    }

    private suspend fun <T> cached(path: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        val text = cache?.get(path) ?: return@withContext null
        runCatching { json.decodeFromString(serializer, text) }.getOrNull()?.also { _offline.value = true }
    }

    private suspend fun <T> send(
        method: String,
        path: String,
        body: String?,
        responseSerializer: KSerializer<T>?,
        /** Token a usar en vez del de la sesión (p. ej. al cerrar sesión, cuando ya se borró). */
        authToken: String?,
        onBody: (String) -> Unit,
    ): T? = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                // El plan gratis de Render se duerme: la primera petición puede tardar ~1 minuto.
                connectTimeout = 75_000
                readTimeout = 75_000
                setRequestProperty("Accept", "application/json")
                (authToken ?: tokenProvider())?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
        } catch (e: IOException) {
            throw DataException(NETWORK_MESSAGE, DataException.Kind.NETWORK)
        }

        try {
            val status = try {
                connection.responseCode
            } catch (e: IOException) {
                throw DataException(NETWORK_MESSAGE, DataException.Kind.NETWORK)
            }
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                val message = errorBody
                    ?.let { runCatching { json.parseToJsonElement(it) as JsonObject }.getOrNull() }
                    ?.get("message")?.jsonPrimitive?.content
                if (status == 401 && !path.startsWith("/auth/")) onUnauthorized()
                throw DataException(message ?: "Error $status", kindFor(status))
            }
            if (status == 204 || responseSerializer == null) return@withContext null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(responseSerializer, text).also { onBody(text) }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Sube bytes a un enlace firmado del almacenamiento (Supabase). No lleva el token de la API:
     * el permiso va dentro del propio enlace.
     */
    suspend fun uploadBytes(url: String, bytes: ByteArray, contentType: String) = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.setRequestProperty("Content-Type", contentType)
            connection.outputStream.use { it.write(bytes) }
            if (connection.responseCode !in 200..299) {
                throw DataException("No se pudo subir la foto (error ${connection.responseCode})", DataException.Kind.UNKNOWN)
            }
        } catch (e: IOException) {
            throw DataException("No se pudo subir la foto. Revisa tu internet.", DataException.Kind.NETWORK)
        } finally {
            connection.disconnect()
        }
    }

    private fun kindFor(status: Int) = when (status) {
        400, 422 -> DataException.Kind.INVALID
        401 -> DataException.Kind.UNAUTHORIZED
        403 -> DataException.Kind.FORBIDDEN
        404 -> DataException.Kind.NOT_FOUND
        else -> DataException.Kind.UNKNOWN
    }

    private companion object {
        const val NETWORK_MESSAGE = "No se pudo conectar con el servidor. Revisa tu internet e intenta de nuevo."
        const val OFFLINE_WRITE = "Sin conexión: este cambio no se guardó. Inténtalo cuando vuelva el internet."
        const val OFFLINE_EMPTY = "Sin conexión y todavía no hay datos guardados en este teléfono."
    }
}
