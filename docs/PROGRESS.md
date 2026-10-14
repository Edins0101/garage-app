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
- [ ] Desplegar la API en Render (subir el repo a GitHub → Blueprint con `render.yaml`)
- [ ] App: pantalla de login (Google; `/auth/dev` en debug), guardar el token y `USE_FAKES=false`
- [ ] Configurar OAuth en Google Cloud (Client ID Web + Android) y `GOOGLE_CLIENT_ID`
- [ ] Rotar la contraseña de la base (se compartió en un chat)
- [ ] Ver cuándo vence la base gratis de Render (30 días) y migrar a Neon antes de esa fecha
- [ ] Sincronización en tiempo real de las listas compartidas (FCM)
- [ ] Copia local en la app para funcionar sin conexión
- [ ] Firma y build de release (`isMinifyEnabled` ya está activo; revisar las reglas de ProGuard con serialization)

## Bitácora

### 2026-10-14
- Primero se hizo un prototipo en React Native/Expo. El usuario prefirió **Kotlin nativo**, así que se rehízo aquí con un diseño nuevo basado en su imagen de referencia. El prototipo se eliminó.
- Se generó el wrapper de Gradle desde la distribución oficial 9.8.1 (checksum verificado) y se fijó `distributionSha256Sum`.
- Arreglos de UI tras probar en el emulador: íconos claros en las barras del sistema; `Scaffold` sin insets automáticos; `ScrollingSheetLayout` para que la hoja blanca llegue al borde inferior; franja bajo la barra de estado; el filtro "Compartidas" pasó a llamarse "Grupo" porque no cabía.
- Se creó el backend en FastAPI y se conectó a Postgres 18 en Render (Oregon). Se aplicó la migración inicial y se probó crear lista y elemento contra la base real. El esquema y las reglas de permisos quedaron en API.md.
