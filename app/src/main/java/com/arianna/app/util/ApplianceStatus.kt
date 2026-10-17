package com.arianna.app.util

import com.arianna.app.domain.model.Appliance
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Textos del estado de un equipo de "¿Está libre?" (frases neutras: sirven para "la lavadora" y "el baño"). */
object ApplianceStatus {

    /** "En uso por Beto · desde 3:10 p. m. · libre aprox. 3:55 p. m. (en 12 min)". */
    fun line(appliance: Appliance, myId: String?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        if (!appliance.busy) return "Libre"
        val who = if (appliance.busyById == myId) "En uso por ti" else "En uso por ${appliance.busyByName ?: "alguien"}"
        val since = appliance.busySinceInstant?.let { " · desde ${clock(it, zone)}" }.orEmpty()
        return who + since + endPart(appliance, now, zone)
    }

    private fun endPart(appliance: Appliance, now: Instant, zone: ZoneId): String {
        val end = appliance.expectedEndInstant ?: return ""
        val left = Duration.between(now, end)
        return if (left.isNegative) {
            " · ya debía terminar"
        } else {
            " · libre aprox. ${clock(end, zone)} (${remaining(left)})"
        }
    }

    /** "en 12 min", "en 1 h 05 min", "en menos de 1 min". */
    fun remaining(left: Duration): String {
        val minutes = left.toMinutes()
        return when {
            minutes < 1 -> "en menos de 1 min"
            minutes < 60 -> "en $minutes min"
            else -> "en ${minutes / 60} h %02d min".format(minutes % 60)
        }
    }

    /** "45 min", "1 h 30 min", "2 h". */
    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    fun clock(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        Dates.time(instant.atZone(zone).toLocalTime())
}
