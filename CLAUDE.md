# Arianna (Android nativo)

App Android nativa en **Kotlin + Jetpack Compose** (paquete `com.arianna.app`). Antes se llamaba
"Garaje": las carpetas y repos (`garaje-android`, `garaje-backend`, `garaje-keys`) conservan ese nombre.
Hoy tiene un calendario del garaje con cuenta por pagar y listas de compras/tareas compartidas;
la idea es crecer hacia pagos recurrentes y otros contadores (ver CONTEXT.md → Nombre y rumbo).
Backend propio en `D:\Projects\garaje-backend` (FastAPI en Render).

## Lee primero

- [docs/CONTEXT.md](docs/CONTEXT.md): visión, alcance, diseño y decisiones
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): paquetes, flujo de datos y cómo pasar al backend
- [docs/API.md](docs/API.md): contrato REST que esperan los repositorios remotos
- [docs/PROGRESS.md](docs/PROGRESS.md): qué está hecho, qué falta y la bitácora
- [docs/NOTIFICACIONES.md](docs/NOTIFICACIONES.md): notificaciones push (Firebase)
- [docs/PLAN-FOTOS.md](docs/PLAN-FOTOS.md): diseño de las fotos en las listas (catálogo de productos)

## Comandos (desde la raíz del proyecto)

```bash
./gradlew assembleDebug        # compila el APK de debug
./gradlew installDebug         # compila e instala en el emulador o teléfono conectado
./gradlew assembleRelease      # APK firmado → app/build/outputs/apk/release/app-release.apk
```

En terminal, si `java` no se encuentra: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`.

## Reglas del proyecto

- UI, comentarios y documentación en **español**.
- La UI y los ViewModels dependen solo de las interfaces de `domain/repository`. Nunca importan `data/fake` ni `data/remote` directamente; la elección se hace en `di/AppContainer.kt`.
- Todo método nuevo de repositorio va en la interfaz y en **ambas** implementaciones (fake y remote), y se documenta en `docs/API.md`.
- Los colores salen de `ui/theme/Palette` y los componentes visuales de `ui/components`. Mantener el estilo "noche violeta" (ver CONTEXT.md).
- Al terminar una tarea: `./gradlew assembleDebug` sin errores ni advertencias, probar en el emulador y actualizar `docs/PROGRESS.md` (checklist y bitácora con fecha).

## APK de release

- Firma: llave en `D:\Projects\garaje-keys\` (`garaje-release.jks` + `keystore.properties`), **fuera del repo**.
  `app/build.gradle.kts` la lee desde ahí (o desde la variable `GARAJE_KEYSTORE`). Sin esa llave no se pueden publicar actualizaciones.
- Antes de cada release nuevo: subir `versionCode` (+1) y `versionName` en `app/build.gradle.kts`.
- R8 (minify + shrink) está activo: probar el APK release en el emulador antes de distribuirlo (la serialización es lo primero que se rompe).
- Las copias para compartir quedan en `releases/` (ignorada por git).

