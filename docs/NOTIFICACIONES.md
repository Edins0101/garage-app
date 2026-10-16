# Notificaciones push

## Qué avisa

- Alguien **agrega un elemento** a una lista compartida → "Ana agregó «Leche»" (título = nombre de la lista).
- Alguien **se une** a una lista con código → "Beto se unió a la lista".
- Nunca se avisa a quien hizo el cambio. Las listas sin compartir no generan avisos.
- Varias cosas seguidas en la misma lista **reemplazan** la notificación (tag por lista) en vez de acumularse.
- Al tocarla se abre la lista (`arianna://list/{id}`).
- **Recordatorios de pagos**: los avisos de vencimiento los arma el propio teléfono (`ReminderWorker`, canal
  "Recordatorios de pagos"), sin servidor. El backend solo manda push cuando otra persona de la sección agrega o
  marca un pago (`kind` en los datos → abre `arianna://reminders/{kind}`). Ver [PLAN-RECORDATORIOS.md](PLAN-RECORDATORIOS.md).

## Cómo funciona

```
App (FirebaseMessaging) ─token─▶ POST /api/devices ─▶ tabla devices
Cambio en lista ─▶ backend busca teléfonos de los demás miembros ─▶ FCM HTTP v1 ─▶ teléfonos
```

- Backend: `app/push.py` (envío en segundo plano, borra tokens inválidos) y `app/routers/devices.py`.
- App: `data/push/` → `PushRegistrar` (registro y baja), `AriannaMessagingService` (recibe), `Notifications` (canal y aviso).
- Mensajes **solo de datos**: la app arma la notificación también con la app abierta.
- Android 13+: la app pide el permiso de notificaciones al entrar con sesión.
- Al cerrar sesión: DELETE /devices/{token} con el token de la sesión que se cierra y `deleteToken()` de Firebase.

## Configuración (una sola vez)

1. **console.firebase.google.com** → Crear proyecto (Analytics no es necesario).
2. Agregar app **Android** con paquete `com.arianna.app` (antes `com.garaje.app`; el proyecto de Firebase puede tener las dos) → descargar `google-services.json` → copiarlo a
   `garaje-android/app/google-services.json` (está en .gitignore). Sin ese archivo la app compila, pero sin notificaciones.
3. **Configuración del proyecto → Cuentas de servicio → Generar nueva clave privada** → descarga un JSON.
4. En Render: `PUSH_PROVIDER=fcm` y `FIREBASE_SERVICE_ACCOUNT` = el contenido de ese JSON.
5. Recompilar la app (`./gradlew assembleRelease`).

Mientras `PUSH_PROVIDER=console`, los avisos solo se escriben en los logs de Render.

## Pendiente

- Migrar al registro por **ID de instalación (FID)** de firebase-messaging 25.1+ (`onRegistered()` / `register()`),
  que reemplaza a `getToken`/`onNewToken`/`deleteToken` (hoy obsoletos pero funcionales). Requiere revisar cómo
  se envía desde el servidor a un FID antes de cambiar el backend.

