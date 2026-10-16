# Recordatorios: Pagos, Mi hogar y Mi carro

> Decidido con el usuario el 2026-10-16. Estado: **Pagos, Mi hogar, Mi carro y Otros hechos**, en una sola pantalla "Recordatorios".

## Idea

Pagos (luz, internet, tarjeta), Mi hogar (aire acondicionado, lavadora, fumigación) y Mi carro
(matrícula, aceite, SOAT) son lo mismo por dentro: **algo que vence cada cierto tiempo, avisa
antes y se marca como hecho**. Por eso hay un solo motor de *recordatorios* con tres secciones
(`kind` = `payments` | `home` | `car` | `other`). Cambian los textos (`KindCopy`), no la lógica.

**Una sola pantalla "Recordatorios"** (decisión del usuario, v0.6): un mosaico en el inicio y filtros
Todos · Pagos · Mi hogar · Mi carro · Otros. Al crear se elige la categoría; cada categoría es una sección
que se comparte por separado (Pagos con la pareja, Mi hogar con los roomies).

## Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Avisos | **Desde el teléfono** (WorkManager, revisión diaria ~8:00 y al abrir la app) | Funcionan sin internet y sin depender de que Render Free esté despierto. Nada de cron en el servidor. |
| Compartir | **Por sección completa**: compartes "Pagos" con tu familia y ven todos | Más simple de usar que compartir uno por uno. |
| Repetición | `every` + `unit` (día, semana, mes, año) o una sola vez | Cubre mensual, bimestral, anual, semanal… |
| Meses cortos | Se guarda el día original (`anchor_day`): 31 ene → 28 feb → 31 mar | La luz que vence el 31 no se "corre" al 28 para siempre. |
| Monto | Aproximado en el recordatorio; el real se guarda al marcarlo | La luz cambia cada mes. |
| Marcar | "Ya pagué" guarda historial (`ReminderLog`) y pasa al siguiente vencimiento | |
| Contar desde (`countFrom`) | Pagos: desde la fecha que vencía (`due`). Mi hogar y Mi carro: desde el día que se hizo (`done`). Se puede cambiar por recordatorio | Pagar tarde no corre las fechas de la luz; limpiar el aire tarde sí corre la siguiente limpieza. |
| Deshacer | Borra la última marca y vuelve a ese vencimiento | Por si se tocó sin querer. |
| Push del servidor | Solo cuando otra persona de la sección agrega o marca algo | Los vencimientos ya los avisa cada teléfono. |

## Cuándo avisa el teléfono (`ReminderRules`)

- El día elegido: el mismo día, 1, 3 o 7 días antes (por defecto 3).
- El día del vencimiento.
- Si está vencido: al día siguiente y luego una vez por semana.
- No repite el mismo aviso (clave `id|vencimiento|días`), aunque la revisión corra varias veces.
- Sin internet usa la última copia guardada (`ReminderSnapshotStore`). Se borra al cerrar sesión.

## Modelo

- Backend (`app/models.py`): `Space` (dueño + tipo, única por usuario y tipo), `SpaceMember`
  (como `ListMember`), `Reminder`, `ReminderLog`. `Invite` ahora puede apuntar a una lista **o** a una sección.
- Endpoints en [API.md](API.md) → Recordatorios. `POST /join` sirve para códigos de listas y de secciones.
- App: `RemindersRepository` (fake y remoto), `ui/reminders/`, `data/reminders/ReminderAlarms.kt`,
  `util/Recurrence.kt` (mismas reglas que `app/reminders.py`, con tests en ambos lados).

## Fases

1. ✅ **Pagos**: crear, editar, eliminar, "Ya pagué" con monto real, deshacer, historial, compartir la
   sección (correo o código), avisos locales, sección "Próximos pagos" en el inicio y en el saludo de Arianna.
2. ✅ **Mi hogar**: mantenimientos con `countFrom = done` (el siguiente se cuenta desde el día que se marcó;
   se puede cambiar a fecha fija). Ideas rápidas para crear con un toque (aire cada 3 meses, filtro de la
   lavadora cada mes, filtro de agua y fumigación cada 6 meses, refri, calefón). Pagos también tiene ideas
   (luz, agua, internet, celular, tarjeta, arriendo).
3. ✅ **Mi carro**: matrícula por último dígito de placa (calendario ANT: 1 feb … 9 oct, 0 nov; `Plates`),
   "cada 3 meses o 5.000 km" (`everyKm`, informativo) y kilometraje al marcar (`odometer`); la tarjeta muestra
   "o a los 50.300 km". Ideas: aceite, llantas, frenos, SOAT, seguro, revisión técnica.
   **Pico y placa: no se hizo** (no existe en Guayaquil y los horarios de Quito cambian; datos fijos podrían
   causar multas). Si se agrega, que sea configurable por ciudad y con fuente oficial.
4. ✅ **Otros**: cumpleaños, aniversario, cita médica, licencia, vacuna y desparasitación de la mascota.
5. Ideas: foto del comprobante (reusar las fotos de Supabase), resumen del mes, recordatorios sugeridos.
