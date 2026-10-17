package com.arianna.app.data.remote

import java.io.File
import java.security.MessageDigest

/**
 * Copia local de las últimas respuestas del servidor (una por ruta), en el almacenamiento
 * privado de la app. Se borra al cerrar sesión para no mezclar datos de otra cuenta.
 */
class ResponseCache(private val dir: File) {
    @Synchronized
    fun get(path: String): String? = file(path).takeIf { it.exists() }?.readText()

    @Synchronized
    fun put(path: String, text: String) {
        dir.mkdirs()
        val target = file(path)
        // Escribir aparte y renombrar: si la app se cierra a medias no queda un archivo roto.
        val temp = File(dir, "${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    @Synchronized
    fun clear() {
        dir.deleteRecursively()
    }

    private fun file(path: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(path.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".json")
    }
}
