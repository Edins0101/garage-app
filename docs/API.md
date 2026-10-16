# Contrato de API

Es la API que la app espera. El servidor está en `D:\Projects\garaje-backend` (FastAPI).
Del lado de la app está implementada en
`data/remote/RemoteRepositories.kt` y simulada en `data/fake/FakeRepositories.kt`.
Los modelos JSON son los de `domain/model/Models.kt`.

- Base URL: `API_URL` en `app/build.gradle.kts` (por defecto `http://10.0.2.2:8000/api`, que en el emulador apunta al localhost de tu PC)
- Todas las rutas van bajo el prefijo `/api` (por eso `API_URL` termina en `/api`). Fuera de él solo está `GET /health`.
- Auth: `Authorization: Bearer <token>` en todas las rutas salvo `/auth/*`. El token (JWT) dura 30 días.
- Errores: código HTTP con cuerpo `{ "message": "texto legible" }`.
  La app mapea 400/422 → `invalid`, 403 → `forbidden`, 404 → `not_found`.
  Sin sesión o con el token vencido responde 401.
- Fechas: `date` siempre en formato `YYYY-MM-DD`; los timestamps en ISO 8601 UTC terminados en `Z`.
- JSON en camelCase (`ownerId`, `sharedWith`, `createdAt`…).

## Sesión

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| POST | `/auth/codes` | `{ email, purpose: 'register' \| 'reset' }` | 202 `{ message }`. Envía un código de 6 dígitos al correo. `register`: 409 si ya hay cuenta. `reset`: responde igual exista o no la cuenta. 429 si se pide antes de 60 s o más de 5 por hora |
| POST | `/auth/register` | `{ name, email, password, code }` | 201 `{ token, user: User }`. Contraseña de 8 a 128 caracteres; 400 si el código es incorrecto, venció o se agotaron los 5 intentos; 409 si el correo ya tiene cuenta |
| POST | `/auth/password/reset` | `{ email, code, password }` | `{ token, user: User }`: cambia la contraseña e inicia sesión |
| POST | `/auth/login` | `{ email, password }` | `{ token, user: User }`; 401 "Correo o contraseña incorrectos" (no revela si el correo existe) |
| POST | `/auth/google` | `{ idToken }` | `{ token, user: User }`: ID token de "Iniciar sesión con Google" |
| POST | `/auth/dev` | `{ email, name }` | `{ token, user: User }`: entra **sin contraseña**; solo si `ALLOW_DEV_LOGIN=true` (desarrollo y tests, nunca en producción) |

Las contraseñas se guardan como hash scrypt (`app/passwords.py`), nunca en texto plano.
Los códigos de correo (`app/otp.py`) se guardan como HMAC, vencen en 10 minutos y son de un solo uso.

Al iniciar sesión, las invitaciones por correo pendientes a ese email se aceptan solas.

## Usuario

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/me` | `User` |

## Días de garaje

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/garage-days?from=YYYY-MM-DD&to=YYYY-MM-DD` | — | `GarageDay[]` ordenados por fecha (el rango es opcional) |
| PUT | `/garage-days/{date}` | `{ note?: string }` | `GarageDay` (crea o actualiza) |
| DELETE | `/garage-days/{date}` | — | 204 |

## Contadores por día

Cada usuario puede tener varios contadores (el garaje, la niñera, el expreso, días trabajados…). Cada uno
tiene su tarifa, marcado automático, días, cargos y pagos; `direction` = `pay` (yo pago) o `earn` (me pagan).

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/trackers` | — | `Counter[]` con `summary` (cuenta hasta hoy), en orden de creación |
| POST | `/trackers` | `{ name, icon, direction, dailyRate?, autoMark? }` | `Counter` (201) |
| PATCH | `/trackers/{id}` | mismos campos, opcionales (+ `applyToDaysWithoutRate`) | `Counter` |
| DELETE | `/trackers/{id}` | — | 204 (borra sus días, cargos y pagos) |
| GET | `/trackers/{id}/summary` | — | `GarageSummary` |
| GET | `/trackers/{id}/days?from&to` | — | `GarageDay[]` |
| PUT / DELETE | `/trackers/{id}/days/{date}` | `{ note?, rate? }` | `GarageDay` / 204 |
| POST | `/trackers/{id}/days/{date}/charges` | `{ description, amount }` | `GarageCharge` |
| DELETE | `/trackers/{id}/charges/{chargeId}` | — | 204 |
| GET / POST | `/trackers/{id}/payments` | `{ date, amount, note? }` | `Payment[]` / `Payment` |
| DELETE | `/trackers/{id}/payments/{paymentId}` | — | 204 |

- `icon` = `car` \| `child` \| `bus` \| `gym` \| `work` \| `pet` \| `home` \| `other`.
- Solo el dueño ve sus contadores (sin acceso: 404). Compartirlos queda pendiente.
- Las rutas de abajo (`/garage*`, `/garage-days`, `/payments`) son de versiones anteriores de la app y
  trabajan sobre el **primer** contador (si no hay, crean "Mi garaje").

## Tarifa, cargos y pagos del garaje (rutas anteriores: primer contador)

Montos en dólares: se envían como texto con 2 decimales (`"5.50"`) y se devuelven como número.
"Hoy" se calcula en `America/Guayaquil` (`APP_TIMEZONE`).

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/garage/settings` | — | `{ dailyRate, autoMark, today }` |
| PUT | `/garage/settings` | `{ dailyRate?, autoMark?, applyToDaysWithoutRate? = true }` | Igual que GET. Cambiar la tarifa no altera los días que ya tienen una; los días sin tarifa la toman |
| GET | `/garage/summary?until=YYYY-MM-DD` | — | `{ until, dailyRate, days, daysWithoutRate, daysTotal, chargesTotal, total, paid, pending }` hasta `until` (por defecto hoy); los días futuros no cuentan |
| POST | `/garage-days/{date}/charges` | `{ description, amount }` | 201 `GarageCharge`. Si el día no estaba marcado, lo marca |
| DELETE | `/garage-charges/{id}` | — | 204 |
| GET | `/payments` | — | `Payment[]` (más recientes primero) |
| POST | `/payments` | `{ date, amount, note? }` | 201 `Payment` |
| DELETE | `/payments/{id}` | — | 204 |

- `GarageDay` incluye `rate` (tarifa de ese día, copiada de la vigente al marcarlo), `charges[]` y `total`.
- `PUT /garage-days/{date}` acepta `{ note?, rate? }`; los campos que no se envían no cambian.
- **Marcado automático** (`autoMark`): no hay cron. Al consultar días, resumen o configuración, el servidor
  agrega los días que faltan hasta hoy con la tarifa por defecto, desde `garage_auto_filled_until`.
  Los días que el usuario quite no vuelven a aparecer.

## Catálogo y fotos

Ver [PLAN-FOTOS.md](PLAN-FOTOS.md). `ChecklistItem` incluye `productId` e `image: { url, key, source: 'item' | 'product' }`.

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/lists/{id}/products?q=` | — | `Product[]` del catálogo del dueño de la lista (autocompletar) |
| POST | `/uploads` | `{ listId, contentType? }` | `{ uploadUrl, key, contentType }`: enlace firmado (10 min) para subir con PUT directo al almacenamiento |
| PUT | `/lists/{id}/products/{productId}/image` | `{ key }` o `{ url }` (https) | `Product` |
| DELETE | `/lists/{id}/products/{productId}/image` | — | `Product` |
| PUT | `/lists/{id}/items/{itemId}/image` | `{ key }` | `ChecklistItem` (foto propia) |
| DELETE | `/lists/{id}/items/{itemId}/image` | — | `ChecklistItem` |

Límites: imagen de máximo 2 MB y 200 MB por catálogo. Sin almacenamiento configurado, las rutas de fotos responden 503.

## Listas

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/lists` | — | `Checklist[]` (propias y compartidas conmigo) |
| GET | `/lists/{id}` | — | `Checklist` |
| POST | `/lists` | `{ title, kind: 'shopping' \| 'todo' }` | `Checklist` (201) |
| PATCH | `/lists/{id}` | `{ title }` | `Checklist` |
| DELETE | `/lists/{id}` | — | 204 |
| POST | `/lists/{id}/items` | `{ text, quantity? }` | `ChecklistItem` (201) |
| PATCH | `/lists/{id}/items/{itemId}` | `{ text?, quantity?, done? }` | `ChecklistItem` |
| DELETE | `/lists/{id}/items/{itemId}` | — | 204 |
| POST | `/lists/{id}/items/clear-completed` | — | `Checklist` |

## Compartir

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/lists/{id}/members` | — | `SharedMember[]` |
| POST | `/lists/{id}/members` | `{ email, role: 'editor' \| 'viewer' }` | `SharedMember` (si ya existe, actualiza el rol) |
| DELETE | `/lists/{id}/members/{email}` | — | 204 (el dueño quita a cualquiera; un miembro puede salirse él mismo) |
| POST | `/lists/{id}/invites` | — | `ShareInvite` (reutiliza uno vigente si existe) |
| POST | `/invites/join` | `{ code }` | `Checklist` (solo listas; lo usaban las versiones ≤ 0.3 de la app) |
| POST | `/join` | `{ code }` | `JoinResult { type: 'list' \| 'space', listId?, spaceId?, kind? }`: sirve para códigos de listas y de secciones |

`ShareInvite` trae `listId` **o** `spaceId` (uno de los dos).

## Recordatorios (Pagos, Mi hogar, Mi carro)

Diseño en [PLAN-RECORDATORIOS.md](PLAN-RECORDATORIOS.md). `kind` = `payments` \| `home` \| `car` \| `other`.
En `GET /spaces` y `GET /reminders` el `kind` es opcional: sin él vienen todas las categorías (la app usa eso).
Cada usuario tiene una **sección** por tipo (se crea sola) y la comparte completa.

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/spaces?kind=payments` | — | `ReminderSpace[]`: la propia primero y luego las compartidas conmigo |
| POST | `/spaces/{id}/members` | `{ email, role }` | `SharedMember` (solo el dueño) |
| DELETE | `/spaces/{id}/members/{email}` | — | 204 (el dueño quita a cualquiera; un miembro puede salirse) |
| POST | `/spaces/{id}/invites` | — | `ShareInvite` con `spaceId` (solo el dueño) |
| GET | `/reminders?kind=payments` | — | `Reminder[]` de todas mis secciones de ese tipo, por `nextDue` |
| POST | `/reminders` | `{ kind, spaceId?, title, amount?, every?, unit?, nextDue, notifyDaysBefore, countFrom?, everyKm?, note? }` | `Reminder` (201) |
| PATCH | `/reminders/{id}` | mismos campos; `null` borra `amount`, `note` o la repetición | `Reminder` |
| DELETE | `/reminders/{id}` | — | 204 |
| POST | `/reminders/{id}/done` | `{ amount?, note?, odometer? }` | `Reminder` ya en el siguiente vencimiento (o `finished` si es de una sola vez) |
| POST | `/reminders/{id}/undo` | — | `Reminder` (vuelve al vencimiento de la última marca; 409 si no hay) |
| GET | `/reminders/{id}/history` | — | `ReminderLog[]`, el más reciente primero |

- `Reminder`: `id, spaceId, kind, title, amount, every, unit ('day'|'week'|'month'|'year'), nextDue, notifyDaysBefore, countFrom ('due'|'done'), everyKm, note, finished, lastDone, createdAt, updatedAt`.
- `ReminderLog`: `id, dueDate, amount, note, odometer, doneBy, doneByName, createdAt`. `everyKm`/`odometer` son para Mi carro.
- `countFrom`: `due` = el siguiente se cuenta desde la fecha que vencía; `done` = desde el día que se marcó (zona horaria de Ecuador). Por defecto `due` en pagos y `done` en `home`/`car`.
  `every` y `unit` van juntos o ambos `null` (una sola vez).
- Meses: se respeta el día original (vence el 31 → 28 feb → 31 mar).
- Permisos como en las listas: dueño y editores crean, editan y marcan; viewer solo ve; sin acceso, 404.
- Al crear o marcar, el backend avisa por push a los demás de la sección
  (`data: { type: 'reminder_added' | 'reminder_done', kind, reminderId }`).
- Los avisos de vencimiento **no** salen del backend: los calcula cada teléfono (`ReminderWorker`).

## Reglas de negocio esperadas en el backend

Implementadas en `garaje-backend/app/permissions.py` y cubiertas por tests:

- Cada usuario tiene **su propio** calendario de garaje.
- **Dueño**: todo. **Editor**: renombrar la lista y crear, editar o borrar elementos. **Viewer**: solo leer.
- Solo el dueño puede borrar la lista, invitar y generar códigos.
- Sin acceso a una lista se responde **404** (no se revela que existe); con acceso insuficiente, **403**.
- Invitar a un correo ya registrado da acceso de inmediato (`accepted`); si no, queda `pending` hasta que esa persona inicie sesión.
- Unirse con código agrega al usuario como `editor` y `accepted`; repetirlo no duplica al miembro.
- `doneBy` se llena con el usuario que marcó el elemento.
- Las invitaciones vencen a los 7 días.
- **Tiempo real (siguiente fase):** para que la otra persona vea los cambios al instante, el plan es usar WebSocket/SSE o notificaciones push. Hoy la app recarga al volver a enfocar la pantalla.
