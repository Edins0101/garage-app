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
class ApiClient(private val baseUrl: String) {

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Token de sesión; se asignará cuando exista login real. */
    @Volatile
    var authToken: String? = null

    suspend fun <T> request(
        method: String,
        path: String,
        body: String? = null,
        responseSerializer: KSerializer<T>?,
    ): T? = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                authToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
        } catch (e: IOException) {
            throw DataException("No se pudo conectar con el servidor", DataException.Kind.NETWORK)
        }

        try {
            val status = try {
                connection.responseCode
            } catch (e: IOException) {
                throw DataException("No se pudo conectar con el servidor", DataException.Kind.NETWORK)
            }
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                val message = errorBody
                    ?.let { runCatching { json.parseToJsonElement(it) as JsonObject }.getOrNull() }
                    ?.get("message")?.jsonPrimitive?.content
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
        403 -> DataException.Kind.FORBIDDEN
        404 -> DataException.Kind.NOT_FOUND
        else -> DataException.Kind.UNKNOWN
    }
}
