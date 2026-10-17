package com.arianna.app

import com.arianna.app.domain.model.Appliance
import com.arianna.app.util.ApplianceStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ApplianceStatusTest {
    private val zone = ZoneId.of("America/Guayaquil")
    // 17 oct 2026, 3:10 p. m. en Guayaquil.
    private val since = Instant.parse("2026-10-17T20:10:00Z")
    private val washer = Appliance(id = "a", name = "Lavadora", ownerId = "ana", ownerName = "Ana")

    @Test
    fun libre() {
        assertEquals("Libre", ApplianceStatus.line(washer, "ana", since, zone))
    }

    @Test
    fun enUsoPorOtraPersonaConHoraEstimada() {
        val busy = washer.copy(
            busy = true, busyById = "beto", busyByName = "Beto",
            busySince = since.toString(), expectedEnd = since.plus(Duration.ofMinutes(45)).toString(),
        )
        assertEquals(
            "En uso por Beto · desde 3:10 p. m. · libre aprox. 3:55 p. m. (en 33 min)",
            ApplianceStatus.line(busy, "ana", since.plus(Duration.ofMinutes(12)), zone),
        )
        assertEquals(
            "En uso por ti · desde 3:10 p. m. · ya debía terminar",
            ApplianceStatus.line(busy, "beto", since.plus(Duration.ofMinutes(50)), zone),
        )
    }

    @Test
    fun duraciones() {
        assertEquals("45 min", ApplianceStatus.duration(45))
        assertEquals("2 h", ApplianceStatus.duration(120))
        assertEquals("1 h 30 min", ApplianceStatus.duration(90))
        assertEquals("en 1 h 05 min", ApplianceStatus.remaining(Duration.ofMinutes(65)))
        assertEquals("en menos de 1 min", ApplianceStatus.remaining(Duration.ofSeconds(20)))
    }
}
