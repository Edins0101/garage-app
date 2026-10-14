# Arquitectura

## Paquetes (`app/src/main/java/com/garaje/app`)

```
GarajeApp.kt                  # Application: crea el AppContainer
MainActivity.kt               # Edge-to-edge, tema y NavHost; reenvía deep links (onNewIntent)
di/AppContainer.kt            # Inyección manual: elige repositorios fake o remote
domain/
├── model/Models.kt           # Modelos serializables (mismo JSON que el backend)
└── repository/Repositories.kt# Interfaces de repositorio + DataException
data/
├── fake/                     # Servidor simulado
│   ├── FakeStore.kt          # Archivo JSON + Mutex + StateFlow + latencia simulada
│   ├── FakeSeed.kt           # Datos de ejemplo y usuario actual
│   └── FakeRepositories.kt   # Reglas de negocio simuladas
└── remote/                   # Listo para el backend real
    ├── ApiClient.kt          # HTTP + JSON + manejo de errores
    └── RemoteRepositories.kt # Endpoints de docs/API.md
ui/
├── theme/Theme.kt            # Palette (colores), tipografía y MaterialTheme
├── components/               # Piezas visuales del diseño "noche violeta"
│   ├── CosmicBackground.kt   # Fondo con degradado, estrellas y montañas
│   ├── Common.kt             # TopBar, MenuTile, GlassField/Card, WhiteSheet, ScrollingSheetLayout, TicketCard, PillButton…
│   ├── Calendars.kt          # PeriodHeader, WeekStrip, MonthCalendar
│   └── Modifiers.kt          # combinedClickableCompat (toque + toque largo)
├── common/
│   ├── BaseViewModel.kt      # launchSafely, mensajes al Snackbar, appViewModel {}
│   └── Dialogs.kt            # TextInputDialog, ConfirmDialog, NewListDialog
├── navigation/AppNavHost.kt  # Rutas tipadas y deep link garajeapp://join/{code}
├── home/                     # Inicio: saludo, menú, agenda semanal, listas recientes
├── garage/                   # Calendario mensual, modos Día/Rango/Nota, notas
└── lists/                    # Índice con filtros, detalle, compartir, unirse
util/                         # Dates (es-EC, semanas de lunes a domingo), Ids
```

## Flujo de datos

```
Pantalla (Compose) ─▶ ViewModel (StateFlow) ─▶ interfaz de repositorio
                                               ├─▶ Fake*Repository ─▶ FakeStore (archivo JSON)
                                               └─▶ Remote*Repository ─▶ ApiClient (HTTP)
```

- Los repositorios exponen `observe*()` como `Flow`. Los ViewModels los combinan con su
  estado de UI (`combine` + `stateIn`) y las pantallas los leen con `collectAsStateWithLifecycle()`.
- Las acciones pasan por `launchSafely {}`: cualquier excepción se convierte en un mensaje de Snackbar.
- Los ViewModels se crean con `appViewModel { container -> … }`. Para leer argumentos de
  navegación se usa `createSavedStateHandle().toRoute<…>()`.

## Pantallas y rutas

| Ruta | Pantalla | Notas |
|---|---|---|
| `HomeRoute` | Inicio | Menú de 6 mosaicos, agenda semanal con marcar/quitar y listas recientes |
| `GarageRoute` | Mi garaje | Modos Día/Rango/Nota; mantener presionado un día abre su nota |
| `ListsRoute(filter, query, create, join)` | Mis listas | Los mosaicos del inicio abren esta ruta con distintos parámetros |
| `ListDetailRoute(id)` | Detalle | Agregar, marcar, eliminar (toque largo), limpiar, renombrar y compartir |
| `JoinRoute(code)` | Unirse | También desde el deep link `garajeapp://join/{code}` |

## Cómo pasar de datos falsos a backend real

1. En `app/build.gradle.kts`: `USE_FAKES` → `false` y `API_URL` → la URL del backend.
2. Implementar en el backend los endpoints de [API.md](API.md).
3. Cuando exista login, asignar `ApiClient.authToken`.
4. Para HTTP sin TLS en desarrollo hace falta permitir cleartext (network security config); en producción, usar HTTPS.

## Cómo agregar una función nueva

1. Modelo en `domain/model/Models.kt`.
2. Método en la interfaz de `domain/repository/Repositories.kt`.
3. Implementarlo en `FakeRepositories.kt` **y** `RemoteRepositories.kt` (el compilador avisa si falta alguno).
4. Documentar el endpoint en `docs/API.md`.
5. ViewModel y pantalla en `ui/<función>/`, reutilizando `ui/components`.
6. Si es una pantalla nueva, agregar su ruta en `AppNavHost.kt`.
7. Actualizar `docs/PROGRESS.md`.

## Datos de prueba

- Archivo: `/data/data/com.garaje.app/files/fake-db.json`. Para verlo: `adb exec-out run-as com.garaje.app cat files/fake-db.json`.
- Para volver a los datos de ejemplo, borra los datos de la app (Ajustes → Apps → Garaje → Almacenamiento) o desinstálala.
