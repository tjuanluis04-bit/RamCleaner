# RamCleaner

App que usa Shizuku para gestionar apps de usuario en segundo plano y
liberar RAM.

## Funciones

- **Cerrar todas las apps**: force-stop paralelo (4 a la vez) de apps de
  terceros, con feedback de cuántas se cerraron y cuántos MB de RAM se
  liberaron.
- **Congelar apps inactivas / Descongelar todo**: mete a las apps elegibles
  en el bucket "restricted" de App Standby (limita su actividad en segundo
  plano) sin cerrarlas de golpe; reversible con un botón.
- **Apps que más RAM consumen**: lista basada en `dumpsys meminfo`.
- **Lista blanca**: apps que nunca se tocan.
- **Tile en el panel rápido**.
- **Auto-activación por umbral de RAM** (opcional, con notificación
  mientras está activo).
- **Animaciones rápidas**: pone la escala de animaciones del sistema a
  0.5x.

## Protecciones automáticas (siempre activas, sin configurar nada)

- El launcher actual.
- El teclado (IME) actual.
- La app en primer plano en el momento exacto de actuar.
- Google Play Services, Play Store y el Google Services Framework — en
  algunos equipos estos aparecen como "de terceros" y cerrarlos causa
  inestabilidad grave del sistema (tiles del panel rápido en "no
  disponible", toques fantasma, "Google Play Services se detuvo").

## Nota sobre cuánta RAM se libera

Android usa a propósito la RAM libre para mantener apps recientes
cacheadas — no es un desperdicio, es una función de rendimiento. No es
posible ni deseable dejar la RAM "como nueva"; si se libera casi toda,
reabrir apps se vuelve más lento porque Android tiene que recargarlas
desde cero. Es normal que limpiezas sucesivas liberen cada vez menos: es
señal de que ya hay poco corriendo, no de que la app dejó de funcionar.

## Cómo funciona por dentro

Usa un **UserService** de Shizuku (proceso con identidad shell/root) para
ejecutar comandos privilegiados (`am force-stop`, `am set-standby-bucket`,
`settings put global`, `dumpsys meminfo`) sin pasar por
`Shizuku.newProcess` (deprecado).

## Compilar con GitHub Actions

1. Sube esta carpeta completa como repositorio en GitHub.
2. Pestaña **Actions** → workflow "Build APK" → correr manualmente o con
   push a `main`.
3. Descarga el artefacto `RamCleaner-debug-apk`.

## Notas

- `applicationId`: `com.example.ramcleaner`.
- La interfaz AIDL está en versión 3 (`ShizukuHelper.userServiceArgs`);
  si agregas más métodos al `.aidl`, sube ese número para que Shizuku
  reinicie el proceso privilegiado con el código nuevo.
