# RamCleaner

App para Android que usa Shizuku (ADB inalámbrico o root) para cerrar
rápidamente apps en segundo plano y liberar RAM.

## Compilar

Este repo no necesita Android Studio: cada push a `main`/`master` dispara
el workflow `.github/workflows/build.yml` en GitHub Actions, que compila
el APK de debug y lo deja descargable como "artifact" en la pestaña
**Actions** del repo.

## Requisitos para usar la app

- Tener [Shizuku](https://shizuku.rikka.app/) instalado y activo (vía ADB
  inalámbrico o root/Sui).
- Dar permiso a RamCleaner la primera vez que lo pida (aparece un diálogo
  de Shizuku).

## Qué hace cada pantalla

- **Principal**: gráfico de RAM en tiempo real, botón "Limpiar ahora",
  auto-activación por umbral de RAM configurable.
- **Elegir apps a excluir**: lista blanca de apps que nunca se cierran
  (con buscador).
- **Cerrar apps específicas**: cierra una app puntual con un toque (con
  buscador).
- **Apps que más RAM consumen**: ranking en vivo, leído directamente de
  `/proc` para que cargue rápido.
- **Tile en el panel rápido**: desliza desde arriba, el tile de
  RamCleaner cierra todo con un toque.
- **Widget de pantalla de inicio**: tarjeta morada que limpia la RAM al
  tocarla, sin necesidad de abrir la app.
