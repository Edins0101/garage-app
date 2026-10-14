package com.garaje.app.data.remote

import com.garaje.app.domain.repository.DataException
import kotlinx.coroutines.Dispatchers
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
) {

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun <T> request(
        method: String,
        path: String,
        body: String? = null,
        responseSerializer: KSerializer<T>?,
    ): T? = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                // El plan gratis de Render se duerme: la primera petición puede tardar ~1 minuto.
                connectTimeout = 75_000
                readTimeout = 75_000
                setRequestProperty("Accept", "application/json")
                tokenProvider()?.let { setRequestProperty("Authorization", "Bearer $it") }
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
            json.decodeFromString(responseSerializer, text)
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
    }
}
