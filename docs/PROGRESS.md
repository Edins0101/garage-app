# Avance del proyecto

> Actualizar este archivo al final de cada sesión de trabajo: qué se hizo, qué quedó
> pendiente y cualquier decisión nueva (que también va en CONTEXT.md).

## Estado actual: v0.1, MVP nativo con datos falsos ✅

### Hecho

- [x] Proyecto Kotlin + Compose (AGP 9.4.1, Gradle 9.8.1 con wrapper verificado por checksum, Kotlin 2.4.21, Compose BOM 2026.09.00)
- [x] Diseño "noche violeta" basado en la imagen de referencia (fondo con montañas, mosaicos, hoja blanca, tarjetas boleto)
- [x] Ícono adaptativo propio (carro sobre fondo violeta)
- [x] Capa de datos: interfaces, repositorios **fake** (archivo JSON) y **remote** (HTTP) listos
- [x] **Inicio**: saludo, buscador de listas, menú de 6 mosaicos, agenda semanal (marcar/quitar el día) y listas recientes
- [x] **Mi garaje**
  - [x] Calendario mensual propio (lunes a domingo, en español)
  - [x] Modos: Día (marcar/quitar), Rango (primer y último día) y Nota
  - [x] Notas por día (toque largo o desde la lista de días del mes); quitar un día
  - [x] Contador de días del mes y tarjetas de los días marcados
- [x] **Listas**
  - [x] Filtros: Todas, Compras, Tareas y Grupo (compartidas), más búsqueda por título o elemento
  - [x] Crear (compras/tareas), renombrar y eliminar (toque largo)
  - [x] Agregar elementos (con cantidad en compras), marcar, eliminar y limpiar los completados
  - [x] Compartir: enviar como texto (real), código de invitación, invitar por correo con rol (fake) y quitar miembros
  - [x] Unirse con código y deep link `arianna://join/CODIGO`
- [x] Probado en el emulador `Medium_Phone_API_37.0`: navegación, agregar y marcar, generar código, deep link válido e inválido, filtros

### Pendiente / ideas siguientes

- [ ] Probar en un teléfono físico (activar depuración USB) y ajustar tamaños
- [ ] Editar el texto o la cantidad de un elemento existente
- [ ] "Deshacer" en el Snackbar al eliminar un elemento o una lista
- [ ] Estadísticas del garaje: días por año, racha actual y gráfico mensual
- [ ] Recordatorios (notificación diaria: "¿el carro está en el garaje hoy?")
- [ ] Respetar el rol `VIEWER` en la UI (deshabilitar la edición)
- [ ] Tests unitarios: `Dates`, repositorios fake y ViewModels
- [x] **Backend** (`D:\Projects\garaje-backend`): FastAPI + Postgres 18 en Render, todos los endpoints de [API.md](API.md), permisos, JWT, 7 tests en verde y migración aplicada
- [x] API desplegada en Render: https://garage-back-me4r.onrender.com (`/health`, `/docs`)
- [x] App conectada a producción (`USE_FAKES=false`): login simple con nombre y correo, sesión guardada, cerrar sesión y cierre automático ante un 401
- [x] Crear cuenta e iniciar sesión con correo y contraseña (scrypt), en el backend y en la app
- [ ] Poner `ALLOW_DEV_LOGIN=false` en Render: la app ya no usa `/auth/dev`
- [ ] (Opcional) Login con Google
- [x] Código de 6 dígitos por correo (OTP) para crear cuenta y recuperar contraseña (backend + app)
- [ ] Configurar Brevo en Render (`EMAIL_PROVIDER=brevo`, `BREVO_API_KEY`, `EMAIL_FROM`). Mientras tanto, el código aparece en Render → Logs
- [ ] Configurar OAuth en Google Cloud (Client ID Web + Android) y `GOOGLE_CLIENT_ID`
- [ ] Rotar la contraseña de la base (se compartió en un chat)
- [ ] **La base de Render vence el 2026-11-13** (plan Free): migrar a Neon antes de esa fecha
- [x] Tarifa diaria (cada día guarda la suya), cargos extra por día, pagos y resumen "pendiente hasta hoy"
- [x] Marcado automático diario con la tarifa por defecto (sin cron; los días quitados no reaparecen)
- [x] Inicio personalizable: el usuario elige qué ver primero (Garaje, Compras o Tareas), puede reordenar y ocultar secciones
- [x] Color principal configurable ("Apariencia"): violeta, azul, turquesa, verde, ámbar, coral y rosa
- [x] Mosaicos del menú principal más compactos
- [ ] Guardar las preferencias del inicio y el color en el backend (hoy quedan en el teléfono)
- [ ] Rotar la contraseña de la base y la clave de Brevo (se vieron en el chat y en una captura)
- [x] Fotos en las listas con catálogo de productos, fases 1–4 ([PLAN-FOTOS.md](PLAN-FOTOS.md))
- [x] Pantallas de carga con esqueletos animados, aviso de "despertando el servidor" y botón Reintentar si falla
- [ ] Configurar en Render las variables `STORAGE_*` (endpoint, región, bucket, claves de Supabase)
- [ ] Rotar las claves S3 de Supabase y la contraseña de su base (se compartieron en el chat)
- [x] Notificaciones push (FCM): aviso al agregar elementos o unirse a una lista compartida ([NOTIFICACIONES.md](NOTIFICACIONES.md))
- [x] Firebase configurado (proyecto `garage-21c4c`): notificaciones probadas de punta a punta en producción
- [ ] Copia local en la app para funcionar sin conexión
- [ ] Firma y build de release (`isMinifyEnabled` ya está activo; revisar las reglas de ProGuard con serialization)

- [x] Nuevo nombre: **Arianna** (paquete `com.arianna.app`, deep links `arianna://`, ícono "A" con estrella, saludo con resumen de pendientes)
- [x] Registrar `com.arianna.app` en Firebase y poner el nuevo `google-services.json` en `app/`
- [ ] Cambiar `EMAIL_FROM_NAME` a `Arianna` en Render (si estaba puesto como `Garaje`)
- [x] Pagos recurrentes con recordatorios ([PLAN-RECORDATORIOS.md](PLAN-RECORDATORIOS.md)): sección compartible, "Ya pagué", historial, deshacer y avisos desde el teléfono
- [x] Mi hogar (mantenimientos: aire, lavadora, fumigación) sobre el mismo motor de recordatorios
- [x] Contadores por día: el garaje es un contador más; niñera, expreso, gimnasio, días trabajados (me pagan)
- [x] Compartir un contador con "Puede editar" (ej. la pareja) o "Solo ver" (ej. la niñera)
- [x] Módulo "Mi carro" (matrícula por placa, mantenimiento por fecha o km). Pico y placa: no (ver PLAN-RECORDATORIOS)
- [x] Una sola pantalla "Recordatorios" con filtros y categoría "Otros"

## Bitácora

### 2026-10-14
- Primero se hizo un prototipo en React Native/Expo. El usuario prefirió **Kotlin nativo**, así que se rehízo aquí con un diseño nuevo basado en su imagen de referencia. El prototipo se eliminó.
- Se generó el wrapper de Gradle desde la distribución oficial 9.8.1 (checksum verificado) y se fijó `distributionSha256Sum`.
- Arreglos de UI tras probar en el emulador: íconos claros en las barras del sistema; `Scaffold` sin insets automáticos; `ScrollingSheetLayout` para que la hoja blanca llegue al borde inferior; franja bajo la barra de estado; el filtro "Compartidas" pasó a llamarse "Grupo" porque no cabía.
- Se creó el backend en FastAPI y se conectó a Postgres 18 en Render (Oregon). Se aplicó la migración inicial y se probó crear lista y elemento contra la base real. El esquema y las reglas de permisos quedaron en API.md.
- Se desplegó en Render (`garage-back-me4r`) y se conectó la app. Probado en el emulador: login, marcar un día y crear una lista quedaron guardados en Postgres. Los datos de prueba se borraron después.
- Se agregó registro e inicio de sesión con contraseña. Probado en el emulador contra producción: crear cuenta, cerrar sesión, contraseña incorrecta y entrar de nuevo. Bug corregido: el ícono de mostrar contraseña robaba el foco al tocar "Siguiente" en el teclado.
- Se agregaron códigos OTP por correo: verificación al crear cuenta y "¿Olvidaste tu contraseña?". Las casillas del código se vacían si el servidor lo rechaza. Render Free bloquea SMTP, así que el envío es por la API HTTP de Brevo. Mientras no esté configurado, el código sale en los logs.
- Garaje con cuenta: tarifa por día guardada en cada día, cargos extra (varios por día), pagos y resumen con el pendiente hasta hoy. También el marcado automático diario. Probado contra producción (API) y en el emulador con datos locales: tarifa, auto, pago parcial y cargo.
- Inicio personalizable: bienvenida "¿Qué es lo más importante para ti?", secciones Garaje/Compras/Tareas en el orden del usuario, y "Personalizar inicio" para reordenar u ocultar. Las preferencias se guardan por usuario en el teléfono (`HomePrefsStore`).
- Apariencia: `Palette` ahora se calcula desde un tono base (`ThemeState` + `AccentPalette`), así que al elegir otro color toda la UI se redibuja sola. Los colores claros (turquesa, verde, ámbar) se oscurecen para mantener el contraste del texto blanco. El color se guarda en el teléfono (`AppearanceStore`). Los mosaicos del menú son unos 25% más bajos.
- Fotos: catálogo de productos (autocompletar), foto del producto (galería, cámara o URL) y foto propia por elemento, guardadas en Supabase Storage con subida directa por enlace firmado. La app comprime a 1280 px y quita el EXIF. Probado: 26 tests del backend, subida y lectura firmadas contra Supabase real, y en el emulador (con datos locales) cámara, miniatura, visor y autocompletar.
- Loadings: `BaseViewModel.loadingState` (estados LOADING/READY/ERROR y `retry()`) en Inicio, Mi garaje, Mis listas y el detalle de la lista; esqueletos en `ui/components/Loading.kt`. Probado en el emulador con una demora artificial de 6 s.

### 2026-10-15
- Primer APK de release firmado (v0.1.0, ~2 MB con R8). La llave de firma quedó en `D:\Projects\garaje-keys` (fuera del repo, hay que respaldarla). Probado en el emulador contra producción: inicio de sesión, inicio, marcar día, crear lista y agregar elemento.
- Pantallas pequeñas y letra grande: los mosaicos ya no tienen proporción fija y sus textos se achican solos (`TextAutoSize`) en vez de cortarse. Lo mismo en el botón de tarifa del garaje y en las pestañas del acceso. Probado a 720×1280 y con la letra del sistema al 130 %.
- Notificaciones push con FCM: backend (tabla devices, envío en segundo plano, 4 tests nuevos, 30 en total) y app (registro, servicio, deep link a la lista, permiso en Android 13+). Probado sin Firebase: la app funciona igual y el deep link abre la lista. Falta probar el envío real cuando exista el proyecto de Firebase.
- v0.2.0: notificaciones funcionando en producción (Firebase `garage-21c4c`). Probado: Beto agrega «Huevos» a una lista compartida, a Ana le llega la notificación y al tocarla se abre la lista. Los avisos de API obsoleta de firebase-messaging 25.1+ se silenciaron con una nota (migración a FID pendiente).

### 2026-10-16
- Cambio de nombre a **Arianna** (v0.3.0): paquete y `applicationId` `com.arianna.app`, deep links `arianna://`, ícono nuevo ("A" blanca con estrella dorada, también en la barra de notificaciones), marca en el inicio y el acceso, y saludo de Arianna con el resumen de pendientes según las secciones visibles. Backend: correos con el nombre nuevo y enlaces de invitación `arianna://join/`. El `google-services.json` viejo (paquete `com.garaje.app`) se movió a `D:\Projects\garaje-keys\google-services.garaje-old.json`.
- Firebase: `com.arianna.app` registrada en el proyecto `garage-21c4c` (el json trae las dos apps). Probado en producción con el APK release: Beto agrega «Pan» y a Ana le llega "Súper compartido — Beto agregó «Pan»".
- Pagos (v0.4.0): motor de recordatorios para Pagos, Mi hogar y Mi carro (por ahora solo se ve Pagos). Backend: tablas spaces, space_members, reminders y reminder_logs (migración `8b3e1d7a9c20`, probada en un Postgres 18 local con Docker), endpoints `/spaces`, `/reminders` y `POST /join` para códigos de listas y de secciones; 8 tests nuevos (38 en total). App: pantalla Pagos (vencidos, próximos 7 días, más adelante), "Ya pagué" con monto real, deshacer, historial, compartir la sección, sección "Próximos pagos" en el inicio y en el saludo, avisos locales con WorkManager (canal "Recordatorios de pagos"). Primeros tests unitarios de la app (`RemindersTest`). Nuevo: `-Parianna.fakes=true` para compilar con datos falsos. Probado en el emulador con datos falsos: aviso «Luz» vence en 2 días, pagar, deshacer, crear y compartir.
- v0.4.0 probado en producción con el APK release: Beto comparte sus Pagos con Ana; a Ana le llega el aviso local «Internet» vence mañana, lo ve como "De Beto" y lo marca pagado; Beto lo ve en su historial; Beto marca otro y a Ana le llega el push "Beto marcó «Internet» como pagado". Usuarios de prueba borrados.
- Mi hogar (v0.5.0): sección de mantenimientos sobre el mismo motor. Nuevo `countFrom` (migración `c4d2a6f81e35`, probada en Postgres local): los mantenimientos cuentan el siguiente desde el día que se marcan. Ideas rápidas en Pagos y Mi hogar, mosaico y sección en el inicio (con más de 6 mosaicos el menú pasa a 4 columnas), avisos locales también para Mi hogar. 39 tests en el backend y 4 en la app. Probado en el emulador con datos falsos: crear "Limpiar el aire" desde una idea, marcar hecho (siguiente: 16 ene) y abrir desde el deep link.
- v0.5.0 probado en producción con el APK release: "Limpiar el aire" cada 3 meses que vencía el 10 oct, marcado el 16 oct → siguiente 16 ene. Usuario de prueba borrado.
- Recordatorios + Mi carro (v0.6.0): Pagos y Mi hogar pasan a una sola pantalla "Recordatorios" con filtros (Todos, Pagos, Mi hogar, Mi carro, Otros) y un solo mosaico en el inicio (las preferencias viejas se migran solas). Mi carro: matrícula por último dígito de la placa, `everyKm` y `odometer` (migración `e7a91b3c5d02`), ideas rápidas por categoría, "Otros" para cumpleaños y citas. El backend acepta `kind` opcional y la app trae todo en una consulta. 40 tests en el backend, 5 en la app. Probado en el emulador con datos falsos: placa 0 → matrícula 1 nov; aceite marcado a los 45.300 km → "16 ene o a los 50.300 km".
- v0.6.0 probado en producción con el APK release: las 4 secciones se crean solas, la pantalla Recordatorios muestra Mi carro y Otros juntos con su categoría. Usuario de prueba borrado.
- Contadores por día: tabla `trackers` (migración `a3f5c8d1e947`, probada con datos en Postgres local y aplicada en producción: el garaje existente quedó como "Mi garaje"). Rutas `/trackers/{id}/…`; las `/garage*` siguen sobre el primer contador. App: pantalla Contadores (total por pagar y te deben), crear/editar/eliminar con ideas rápidas e íconos, "yo pago" o "me pagan" (textos Te deben / Cobré / Cobros), el calendario del garaje sirve para cualquier contador y el inicio muestra la agenda con selector de contador. El modo demo convierte el garaje anterior en "Mi garaje". 43 tests en el backend. Probado en el emulador (demo) y contra producción con un usuario temporal ("Días trabajados" 2 días × $3,50 = $7).
- Compartir contadores: tabla `tracker_members` e `invites.tracker_id` (migración `b8e2d4f6a013`), permisos por ruta (ver / editar / dueño), invitar por correo con rol o por código (entra como "solo ver"), salir de un contador ajeno, push "Beto registró un pago de $25.00" que abre el contador. App: compartir desde el contador (dueño), modo solo ver (sin Pagar ni modos; avisa "Solo puedes ver este contador"), "De Beto" en la lista y los totales cuentan solo mis contadores. 44 tests en el backend. Probado contra producción con dos usuarios temporales (borrados).
- APK release v0.7.0 (contadores y compartir contadores). Compilado y con tests en verde; no se abrió en el emulador porque estaba cerrado.

### 2026-10-17
- Grupos: tablas `groups`, `group_members`, `group_shares` e `invites.group_id` (migración `e4b8f2c6a719`). Un grupo (Familia, Roomies…) se crea una vez; se comparte una lista, una categoría de recordatorios o un contador con el grupo ("Puede editar" o "Solo ver") y lo ven todos sus integrantes, también quienes entren después (por correo o con código). El permiso es el mayor entre la invitación directa y los grupos; las respuestas traen `role` y `groups`. App: mosaico "Grupos" en el inicio, pantallas Grupos y Grupo (integrantes, código, lo compartido), sección "Con un grupo" en todas las hojas de compartir.
- Recordatorios con hora y anticipación (a la hora, 15 min, 1 hora o el día anterior): `dueTime` y `alertMinutes` (migración `d1c7e9a2f430`). La app programa alarmas con `AlarmManager` (receptor `ReminderAlarmReceiver`; se reprograman al reiniciar el teléfono, al abrir la app y tras cada cambio). Si Android no permite alarmas exactas, el diálogo ofrece "Permitir".
- Lista adjunta a recordatorios y contadores (`listId`): el aviso dice "Faltan 3 cosas en «Supermercado»" y la tarjeta muestra el avance y la abre.
- 48 tests en el backend (también corridos contra Postgres 18) y 6 en la app. Probado en el emulador (demo): crear "Familia", agregar a alguien, compartir una lista con el grupo, recordatorio a las 8:00 p. m. con lista (la alarma llegó a la hora exacta con "Faltan 3 cosas en «Supermercado»") y contador con lista adjunta.
- Cuentas por día (antes "Contadores"): nuevo nombre en la app. El calendario pinta en verde los días pagados, en morado los pendientes y con un anillo dorado los días en que se registró un pago (con leyenda). Se quitó la lista de días: tocar un día libre lo marca y tocar un día marcado (o de pago) abre su detalle. Se quitó el modo DETALLE (quedan DÍA y RANGO). Lo pagado se cierra: `paidUntil` en la cuenta, 409 en el backend al cambiar esos días y detalle solo de lectura en la app (también en el modo demo). 49 tests en el backend. Probado en el emulador: 3 días × $10 con un pago de $25 → 14 y 15 pagados, el 16 sigue pendiente y el 13 avisa que ya está pagado.
- La agenda del inicio usa los mismos colores (pagado en verde, día de pago con anillo dorado). Al elegir un día pagado, la barra dice "Pagado" y no deja quitarlo.
- Inicio más limpio: se quitaron el botón de filtros y el buscador (cada pantalla tiene los suyos; Listas ya busca listas y elementos). La marca ahora se lee "A✦RIANNA": el ícono es la primera letra del nombre.
