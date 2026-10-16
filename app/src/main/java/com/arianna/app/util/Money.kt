package com.arianna.app.util

import java.util.Locale

/** Montos en dólares (Ecuador). */
object Money {
    fun format(amount: Double): String = "$" + String.format(Locale.US, "%,.2f", amount)

    /** Texto con 2 decimales y punto, como lo espera la API ("5.50"). */
    fun toApi(amount: Double): String = String.format(Locale.US, "%.2f", amount)

    /** Acepta "5", "5.5", "5,50" o "$5.50". null si no es un monto válido (o es negativo). */
    fun parse(text: String): Double? =
        text.trim().removePrefix("$").replace(',', '.').toDoubleOrNull()
            ?.takeIf { it >= 0 && it < 100_000 }
            ?.let { Math.round(it * 100) / 100.0 }
}
