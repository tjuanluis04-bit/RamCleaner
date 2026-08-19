package com.example.ramcleaner;

interface IUserService {

    // ID de transacción reservado que Shizuku usa para pedir que el
    // servicio se cierre limpiamente. No cambiar el valor.
    void destroy() = 16777114;

    // Cierra (force-stop) todas las apps de usuario (no del sistema),
    // saltándose las que estén en "whitelist" (paquetes a no cerrar).
    // Devuelve cuántas apps se cerraron.
    int forceStopAll(in String[] whitelist) = 1;

    // Ajusta la escala global de animaciones del sistema
    // (1.0 = normal, 0.5 = rápidas, 0.0 = desactivadas).
    void setAnimationScale(float scale) = 2;

    // Mete en el bucket "restricted" de App Standby a todas las apps
    // elegibles (sin cerrarlas). Devuelve cuántas se marcaron.
    int freezeInactiveApps(in String[] whitelist) = 3;

    // Devuelve las apps elegibles al bucket "active" (revierte el congelado).
    // Devuelve cuántas se revirtieron.
    int unfreezeApps(in String[] whitelist) = 4;

    // Devuelve las apps que más RAM consumen ahora mismo, como texto
    // "paquete:kilobytes" separado por saltos de línea (ya ordenado desc).
    String getTopMemoryUsage(int limit) = 5;

    // Cierra (force-stop) solo los paquetes indicados explícitamente.
    // Devuelve cuántos se cerraron.
    int forceStopSpecific(in String[] packages) = 6;
}
