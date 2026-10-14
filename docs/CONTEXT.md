# Contexto del proyecto: Garaje

> Documento vivo: explica **qué** es la app y **por qué** se tomó cada decisión.
> El avance del día a día está en [PROGRESS.md](PROGRESS.md).

## Visión

App personal para Android con dos funciones principales:

1. **Calendario del garaje**: registrar los días en que el carro está en el garaje,
   uno por uno o por rango, con una nota opcional por día (ej. "cambio de aceite").
2. **Listas**: listas de **compras** (con cantidades) o de **tareas**, cuyos elementos
   se marcan como hechos. Se pueden **compartir** con otra persona para que también
   vea y edite lo que falta.

## Alcance actual

- **App + backend propio** (`garaje-backend`, FastAPI en Render, Postgres 18). Los datos viven en la nube y se comparten de verdad.
  Los repositorios falsos siguen disponibles (`USE_FAKES=true`) para desarrollar sin internet.
- Formas de compartir:
  - **Enviar la lista como texto** con la hoja de compartir de Android (WhatsApp, SMS…). Ya funciona de verdad.
  - **Código de invitación** y deep link `garajeapp://join/CODIGO`. Se simulan en el teléfono.
  - **Invitar por correo** con rol `editor` o `viewer`. Se guarda en el teléfono; no se envía nada.
- La sincronización real entre dos personas llegará cuando exista el backend.

## Diseño visual: "noche violeta"

Está basado en una imagen de referencia que dio el usuario (app de viajes con estética violeta):

- Fondo degradado azul-morado muy oscuro, con estrellas y dos capas de montañas (`CosmicBackground`).
- "Menú principal" de mosaicos violeta con íconos de línea blancos y etiquetas en mayúsculas (`MenuTile`).
- Buscador en forma de píldora translúcida junto a un botón cuadrado blanco (`GlassField`, `SquareIconButton`).
- Hoja blanca inferior con "manija", donde va el contenido principal (`WhiteSheet`, `ScrollingSheetLayout`).
- Calendario con banda violeta de días de la semana y días marcados en círculos violeta (`WeekStrip`, `MonthCalendar`).
- Tarjetas "boleto" lavanda con etiqueta superior y barra violeta inferior (`TicketCard`).
- Paneles translúcidos con borde suave para los controles sobre el fondo (`GlassCard`).
- Tema siempre oscuro, con íconos claros en las barras del sistema.
- **Color principal configurable** (Apariencia): toda la paleta se deriva de un tono HSL (`ui/theme/Theme.kt`).
  Para agregar un color nuevo basta con sumar una entrada a `AccentColor` (con `darken` si es un tono claro).

## Decisiones técnicas

| Decisión | Motivo |
|---|---|
| **Kotlin + Jetpack Compose** (nativo) | El usuario prefirió nativo. Hubo un prototipo previo en React Native/Expo que se descartó y se eliminó. |
| **AGP 9.4 con Kotlin integrado**, Gradle 9.8, compileSdk/targetSdk 37, minSdk 26 | Versiones estables más recientes, compatibles con el SDK instalado (API 37) y con `java.time`. |
| **Navigation Compose con rutas tipadas** (`@Serializable`) | Argumentos seguros por tipo y deep links declarativos. |
| **Interfaces de repositorio + implementaciones fake y remote** | La UI no sabe si habla con datos locales o con HTTP. Se cambia con `USE_FAKES` en `build.gradle.kts`. |
| **JSON en archivo + kotlinx.serialization** en lugar de Room | Más simple para un servidor simulado y sin plugin KSP. Si se necesita caché offline real con el backend, se evalúa Room. |
| **Cliente HTTP con `HttpURLConnection`** | Sin dependencias extra. Si crece, se puede cambiar por Retrofit o Ktor solo dentro de `data/remote`. |
| **Inyección de dependencias manual** (`AppContainer`) | Suficiente para este tamaño; Hilt sería sobrecarga. |
| **Calendario propio en Compose** | Control total del estilo de la imagen de referencia, sin librerías externas. |
| **Flows reactivos** (`observe*`) | Las pantallas se actualizan solas cuando cambian los datos. |

## Glosario

- **Día de garaje** (`GarageDay`): una fecha `yyyy-MM-dd` marcada, con nota opcional.
- **Lista** (`Checklist`): de tipo `SHOPPING` (compras, admite cantidad) o `TODO` (tareas).
- **Miembro** (`SharedMember`): persona con quien se compartió la lista; rol `EDITOR`/`VIEWER`, estado `PENDING`/`ACCEPTED`.
- **Invitación** (`ShareInvite`): código de 6 caracteres y URL `garajeapp://join/CODIGO`; vence a los 7 días.
