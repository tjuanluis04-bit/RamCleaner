# RamCleaner

App que usa Shizuku para forzar el cierre (force-stop) de apps de usuario en
segundo plano, para liberar RAM.

## Funciones

- **Botón manual**: cierra todas las apps de terceros y muestra cuántas se
  cerraron y cuántos MB de RAM se liberaron.
- **Lista blanca**: elige qué apps NUNCA se deben cerrar (ej. WhatsApp,
  Spotify, tu launcher).
- **Tile en el panel rápido**: agrega el tile "RamCleaner" a tu panel de
  notificaciones (desliza dos veces desde arriba → editar tiles → arrástralo)
  para limpiar sin abrir la app.
- **Auto-activación por umbral (opcional)**: activa el switch en la app y
  elige el % de RAM libre a partir del cual se dispara solo el force-stop.
  Corre como servicio en primer plano con una notificación (obligatoria por
  Android) mientras está activo.

## Cómo funciona por dentro

- Usa la API de Shizuku con un **UserService**: un proceso aparte que corre
  con la identidad de "shell" (o "root" si usas Sui), donde sí se pueden
  ejecutar comandos privilegiados como `am force-stop`.
- No usa `Shizuku.newProcess` porque está deprecado en las versiones
  recientes de Shizuku.

## Requisitos en el celular

1. Tener Shizuku instalado y corriendo (vía ADB inalámbrico o root).
2. Instalar este APK y darle el permiso cuando lo pida.
3. Si activas el monitoreo automático en Android 13+, acepta el permiso de
   notificaciones cuando se lo pida (es obligatorio para el servicio en
   primer plano).

## Compilar con GitHub Actions

1. Sube esta carpeta completa como repositorio en GitHub.
2. Ve a la pestaña **Actions** del repo. El workflow `Build APK` corre solo
   al hacer push a `main`, o puedes lanzarlo manualmente con
   "Run workflow" (workflow_dispatch).
3. Cuando termine, entra al run y descarga el artefacto
   `RamCleaner-debug-apk` — ahí está el `app-debug.apk`.

## Notas

- El `applicationId` es `com.example.ramcleaner`.
- El intervalo de chequeo del monitoreo automático es cada 30 segundos
  (`checkIntervalMs` en `RamMonitorService.kt`); se puede ajustar ahí si lo
  quieres más o menos frecuente.
