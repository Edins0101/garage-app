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
  - [x] Unirse con código y deep link `garajeapp://join/CODIGO`
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
- [ ] Ver cuándo vence la base gratis de Render (30 días) y migrar a Neon antes de esa fecha
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
- [ ] Sincronización en tiempo real de las listas compartidas (FCM)
- [ ] Copia local en la app para funcionar sin conexión
- [ ] Firma y build de release (`isMinifyEnabled` ya está activo; revisar las reglas de ProGuard con serialization)

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
