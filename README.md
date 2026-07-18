# RamCleaner

App que usa Shizuku para forzar el cierre (force-stop) de todas las apps de
usuario en segundo plano, para liberar RAM.

## Cómo funciona

- Usa la API de Shizuku con un **UserService**: un proceso aparte que corre
  con la identidad de "shell" (o "root" si usas Sui), donde sí se pueden
  ejecutar comandos privilegiados como `am force-stop`.
- No usa `Shizuku.newProcess` porque está deprecado en las versiones
  recientes de Shizuku.

## Requisitos en el celular

1. Tener Shizuku instalado y corriendo (vía ADB inalámbrico o root).
2. Instalar este APK y darle el permiso cuando lo pida.

## Compilar con GitHub Actions

1. Sube esta carpeta completa como repositorio en GitHub.
2. Ve a la pestaña **Actions** del repo. El workflow `Build APK` corre solo
   al hacer push a `main`, o puedes lanzarlo manualmente con
   "Run workflow" (workflow_dispatch).
3. Cuando termine, entra al run y descarga el artefacto
   `RamCleaner-debug-apk` — ahí está el `app-debug.apk`.

## Notas

- El `applicationId` es `com.example.ramcleaner`. Puedes cambiarlo en
  `app/build.gradle` (y actualizar las referencias en `MainActivity.kt` /
  `UserService.kt` si lo haces).
- Esto es un esqueleto funcional mínimo (pedir permiso + botón para cerrar
  apps). Si luego quieres que se dispare automáticamente cuando la RAM baje
  de cierto umbral, se puede agregar un servicio en segundo plano que
  monitoree `ActivityManager.MemoryInfo` — dímelo y lo agregamos.
