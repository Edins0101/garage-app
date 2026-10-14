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
| POST | `/auth/google` | `{ idToken }` | `{ token, user: User }`: ID token de "Iniciar sesión con Google" |
| POST | `/auth/dev` | `{ email, name }` | `{ token, user: User }`: solo si `ALLOW_DEV_LOGIN=true` (desarrollo) |

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
| POST | `/invites/join` | `{ code }` | `Checklist` (agrega al usuario como miembro) |

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
