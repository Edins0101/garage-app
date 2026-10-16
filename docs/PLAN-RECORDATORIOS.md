# Recordatorios: Pagos, Mi hogar y Mi carro

> Decidido con el usuario el 2026-10-16. Estado: **fase 1 (Pagos) hecha**.

## Idea

Pagos (luz, internet, tarjeta), Mi hogar (aire acondicionado, lavadora, fumigación) y Mi carro
(matrícula, aceite, SOAT) son lo mismo por dentro: **algo que vence cada cierto tiempo, avisa
antes y se marca como hecho**. Por eso hay un solo motor de *recordatorios* con tres secciones
(`kind` = `payments` | `home` | `car`). Cambian los textos (`KindCopy`), no la lógica.

## Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Avisos | **Desde el teléfono** (WorkManager, revisión diaria ~8:00 y al abrir la app) | Funcionan sin internet y sin depender de que Render Free esté despierto. Nada de cron en el servidor. |
| Compartir | **Por sección completa**: compartes "Pagos" con tu familia y ven todos | Más simple de usar que compartir uno por uno. |
| Repetición | `every` + `unit` (día, semana, mes, año) o una sola vez | Cubre mensual, bimestral, anual, semanal… |
| Meses cortos | Se guarda el día original (`anchor_day`): 31 ene → 28 feb → 31 mar | La luz que vence el 31 no se "corre" al 28 para siempre. |
| Monto | Aproximado en el recordatorio; el real se guarda al marcarlo | La luz cambia cada mes. |
| Marcar | "Ya pagué" guarda historial (`ReminderLog`) y pasa al siguiente vencimiento desde la fecha que vencía (no desde hoy) | Pagar tarde no corre las fechas siguientes. |
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
2. ⏳ **Mi hogar**: activar `HOME` en el menú y en `ReminderWorker.ENABLED_KINDS`. Para mantenimientos,
   contar el siguiente desde la fecha en que se hizo (no desde la que vencía): agregar `countFrom`.
3. ⏳ **Mi carro**: matrícula por último dígito de placa (calendario de Ecuador), pico y placa,
   mantenimiento por kilometraje (pedir el km al marcar).
4. Ideas: foto del comprobante (reusar las fotos de Supabase), resumen del mes, recordatorios sugeridos.
