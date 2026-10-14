package com.garaje.app.util

import java.util.UUID
import kotlin.random.Random

object Ids {
    fun create(prefix: String = ""): String = prefix + UUID.randomUUID().toString().take(12)

    /** Código corto y legible (sin 0/O/1/I para evitar confusiones). */
    fun shareCode(length: Int = 6): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..length).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }
}
