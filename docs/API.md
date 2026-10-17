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
| GET | `/me/preferences` | `{ home: { order, hidden, configured } \| null, accent: string \| null }` |
| PUT | `/me/preferences` | Mismo cuerpo; lo que venga en `null` queda como estaba. Devuelve lo guardado |

- Preferencias de la app en la cuenta (se ven igual en cualquier teléfono): `home.order` y `home.hidden` son nombres de secciones del inicio (`GARAGE`, `REMINDERS`, `SHOPPING`, `TASKS`) y `accent` el color principal (`VIOLET`, `BLUE`…). La app las trae al entrar y sube cada cambio (`PreferencesSync`).

## Días de garaje

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/garage-days?from=YYYY-MM-DD&to=YYYY-MM-DD` | — | `GarageDay[]` ordenados por fecha (el rango es opcional) |
| PUT | `/garage-days/{date}` | `{ note?: string }` | `GarageDay` (crea o actualiza) |
| DELETE | `/garage-days/{date}` | — | 204 |

## Contadores por día (en la app: «Mis calendarios»)

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
- `Counter` incluye `ownerId`, `ownerName`, `role` (`owner` \| `editor` \| `viewer`) y `members`. `GET /trackers` trae
  los propios y, después, los compartidos conmigo.
- Permisos: **viewer** solo lee; **editor** marca días, cargos y pagos; **dueño** además cambia nombre/tarifa/marcado
  automático, borra y comparte. Sin acceso: 404; permiso insuficiente: 403.
- Al registrar un pago se avisa por push a los demás (`data: { type: 'tracker_payment', trackerId }` → `arianna://counter/{id}`).

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| POST | `/trackers/{id}/members` | `{ email, role }` | `SharedMember` (solo el dueño; si ya existe, cambia el rol) |
| DELETE | `/trackers/{id}/members/{email}` | — | 204 (el dueño quita a cualquiera; un miembro puede salirse) |
| POST | `/trackers/{id}/invites` | — | `ShareInvite` con `trackerId`. Con el código se entra como **viewer** |
| POST | `/join` | `{ code }` | `JoinResult` con `type: 'tracker'` y `trackerId` |
- Las rutas de abajo (`/garage*`, `/garage-days`, `/payments`) son de versiones anteriores de la app y
  trabajan sobre el **primer** contador (si no hay, crean "Mi garaje").

- `GarageSummary.paidUntil`: último día pagado por completo. Los pagos cubren primero los días más antiguos;
  un día cubierto solo en parte sigue pendiente. Hasta esa fecha (incluida) no se marcan, quitan ni editan días
  ni cargos: el backend responde **409** "Ese día ya está pagado…". Al borrar el pago se vuelven a abrir.

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

- Editar un elemento: `PATCH …/items/{itemId}` con `text` y/o `quantity`; `quantity: ""` quita la cantidad.
- Borrar con "Deshacer": la app oculta la lista o el elemento y manda el `DELETE` pasados 11 s si no se deshizo (`UndoableLists`, solo en la app).
- Sin conexión: las respuestas de los `GET` se guardan en el teléfono (`ResponseCache`) y se muestran cuando no hay internet; los cambios necesitan conexión.
- Los elementos tienen `section` (opcional, máx. 60): sección dentro de la lista (Baño, Patio, Perros…). `POST /lists/{id}/items` la acepta y `PATCH …/items/{itemId}` la cambia; `null` la quita.

## Compartir

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/lists/{id}/members` | — | `SharedMember[]` |
| POST | `/lists/{id}/members` | `{ email, role: 'editor' \| 'viewer' }` | `SharedMember` (si ya existe, actualiza el rol) |
| DELETE | `/lists/{id}/members/{email}` | — | 204 (el dueño quita a cualquiera; un miembro puede salirse él mismo) |
| POST | `/lists/{id}/invites` | — | `ShareInvite` (reutiliza uno vigente si existe) |
| POST | `/invites/join` | `{ code }` | `Checklist` (solo listas; lo usaban las versiones ≤ 0.3 de la app) |
| POST | `/join` | `{ code }` | `JoinResult { type: 'list' \| 'space' \| 'tracker' \| 'group', listId?, spaceId?, trackerId?, groupId?, kind? }`: sirve para todos los códigos |

`ShareInvite` trae uno solo de `listId`, `spaceId`, `trackerId` o `groupId`.

`Checklist`, `ReminderSpace` y `Counter` traen `groups: GroupShare[]` (`{ groupId, groupName, role }`) y `role`
(`owner` \| `editor` \| `viewer`): mi permiso, el mayor entre la invitación directa y los grupos donde estoy.

## Grupos

Un grupo (Familia, Roomies…) junta personas; lo que se comparte con el grupo lo ven todos sus integrantes,
también quienes entren después. Solo quien lo creó agrega o quita gente; un integrante puede salirse.

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/groups` | — | `Group[]` (primero los que creé) |
| POST | `/groups` | `{ name }` | `Group` (201) |
| PATCH | `/groups/{id}` | `{ name }` | `Group` (solo quien lo creó) |
| DELETE | `/groups/{id}` | — | 204 (lo compartido sigue siendo de cada dueño) |
| POST | `/groups/{id}/members` | `{ email }` | `GroupMember` (si ya tiene cuenta entra de inmediato; si no, al registrarse) |
| DELETE | `/groups/{id}/members/{email}` | — | 204 (quien lo creó quita a cualquiera; un integrante puede salirse) |
| POST | `/groups/{id}/invites` | — | `ShareInvite` con `groupId` (se usa con `POST /join`) |
| PUT | `/{lists\|spaces\|trackers\|appliances}/{id}/groups/{groupId}` | `{ role }` | 204: comparte con el grupo (o cambia el permiso). Solo el dueño y estando en el grupo |
| DELETE | `/{lists\|spaces\|trackers\|appliances}/{id}/groups/{groupId}` | — | 204: deja de compartir |

- `Group`: `id, name, ownerId, ownerName, role ('owner'|'member'), members: GroupMember[], shared: GroupItem[], sharedAppliances: GroupItem[], createdAt`.
  Los equipos de "¿Está libre?" van en `sharedAppliances` (tipo `appliance`): la app v0.8 no conoce ese tipo y fallaría si llegara en `shared`.
- `GroupMember`: `email, name, userId, status ('pending'|'accepted'), invitedAt`.
- `GroupItem`: `type ('list'|'space'|'tracker'|'appliance'), id, title, kind, ownerId, ownerName, role` (`kind`: tipo de lista, categoría o ícono del contador).
- Las notificaciones push de una lista, sección o contador también llegan a los integrantes de sus grupos.

## ¿Está libre? (equipos compartidos)

La lavadora, la secadora, el baño, el carro… Cada equipo está libre u ocupado por alguien. Solo se comparten con
grupos: `editor` = "Puede usarlo" (ocupar y liberar), `viewer` = solo ve el estado. Solo el dueño edita, borra o comparte.

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/appliances` | — | `Appliance[]` (míos primero y los de mis grupos) |
| POST | `/appliances` | `{ name, icon?, defaultMinutes? }` | `Appliance` (201) |
| GET | `/appliances/{id}` | — | `Appliance` |
| PATCH | `/appliances/{id}` | `{ name?, icon?, defaultMinutes? }` (`null` quita la duración) | `Appliance` |
| DELETE | `/appliances/{id}` | — | 204 |
| POST | `/appliances/{id}/start` | `{ minutes? }` (sin minutos usa `defaultMinutes`) | `Appliance`; 409 `"Beto lo está usando"` si lo tiene otra persona. Si ya es mío, cambia la hora estimada |
| POST | `/appliances/{id}/finish` | — | `Appliance` libre (cualquiera con permiso de uso, por si alguien se olvidó) |
| PUT | `/appliances/{id}/notify` | `{ mode: 'all' \| 'free' \| 'none' }` | `Appliance` (mis avisos de ese equipo) |
| GET | `/appliances/{id}/history` | — | `ApplianceUse[]` (últimos 30, el más reciente primero) |

- `Appliance`: `id, name, icon ('washer'|'dryer'|'bath'|'car'|'kitchen'|'tv'|'computer'|'other'), defaultMinutes, ownerId, ownerName,
  role, busy, busyById, busyByName, busySince, expectedEnd, notify, groups: GroupShare[]`.
- `ApplianceUse`: `id, userName, startedAt, endedAt, endedByName, auto` (`auto`: se liberó solo).
- Avisos (push, `data: { type: 'appliance', applianceId }`): al ocuparlo ("Beto empezó a usar Lavadora · libre aprox. a las 3:55 p. m.")
  y al liberarlo, a todos menos quien lo hizo; con `notify = 'free'` solo al liberarse y con `'none'` ninguno.
- Se libera solo si nadie lo hace: 30 min después de `expectedEnd`, o 12 h después de `busySince` si no tiene hora. Se revisa al consultar.
- En la app, quien lo ocupa con tiempo recibe un aviso local al terminar ("¿Ya terminaste con Lavadora?", WorkManager).

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
| POST | `/reminders` | `{ kind, spaceId?, title, amount?, every?, unit?, nextDue, notifyDaysBefore, countFrom?, everyKm?, dueTime?, alertMinutes?, listId?, note? }` | `Reminder` (201) |
| PATCH | `/reminders/{id}` | mismos campos; `null` borra `amount`, `note`, la hora, la lista o la repetición | `Reminder` |
| DELETE | `/reminders/{id}` | — | 204 |
| POST | `/reminders/{id}/done` | `{ amount?, note?, odometer? }` | `Reminder` ya en el siguiente vencimiento (o `finished` si es de una sola vez) |
| POST | `/reminders/{id}/undo` | — | `Reminder` (vuelve al vencimiento de la última marca; 409 si no hay) |
| GET | `/reminders/{id}/history` | — | `ReminderLog[]`, el más reciente primero |

- `Reminder`: `id, spaceId, kind, title, amount, every, unit ('day'|'week'|'month'|'year'), nextDue, notifyDaysBefore, countFrom ('due'|'done'), everyKm, dueTime, alertMinutes, listId, note, finished, lastDone, createdAt, updatedAt`.
- `endsOn` (opcional, solo con repetición): rango de fechas. Se repite desde `nextDue` hasta `endsOn` (incluido); al marcar el último queda `finished`. 422 si `endsOn` < `nextDue`. `unit` admite `day` ("Cada día").
- `dueTime` (`"20:00:00"`, hora local) es opcional; con hora, `alertMinutes` dice cuánto antes avisar (0 = a la hora, 60, 1440 = el día anterior) y `notifyDaysBefore` no se usa. Sin hora, `alertMinutes` es `null`.
- `listId`: lista adjunta (ej. "Compras del mes" con la lista del súper). Solo se puede adjuntar una lista que yo vea (si no, 404); si la lista se borra queda `null`. Los contadores también tienen `listId` (`POST`/`PATCH /trackers`).
- `ReminderLog`: `id, dueDate, amount, note, odometer, doneBy, doneByName, createdAt`. `everyKm`/`odometer` son para Mi carro.
- `countFrom`: `due` = el siguiente se cuenta desde la fecha que vencía; `done` = desde el día que se marcó (zona horaria de Ecuador). Por defecto `due` en pagos y `done` en `home`/`car`.
  `every` y `unit` van juntos o ambos `null` (una sola vez).
- Meses: se respeta el día original (vence el 31 → 28 feb → 31 mar).
- Permisos como en las listas: dueño y editores crean, editan y marcan; viewer solo ve; sin acceso, 404.
- Al crear o marcar, el backend avisa por push a los demás de la sección
  (`data: { type: 'reminder_added' | 'reminder_done', kind, reminderId }`).
- Los avisos de vencimiento **no** salen del backend: los calcula cada teléfono (`ReminderWorker`). Los que tienen hora
  se programan con `AlarmManager` (exacta si el usuario permite "Alarmas y recordatorios"; si no, en una ventana de 10 min).

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
