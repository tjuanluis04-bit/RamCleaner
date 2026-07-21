package com.example.ramcleaner;

interface IUserService {

    // ID de transacción reservado que Shizuku usa para pedir que el
    // servicio se cierre limpiamente. No cambiar el valor.
    void destroy() = 16777114;

    // Cierra (force-stop) todas las apps de usuario (no del sistema),
    // saltándose las que estén en "whitelist" (paquetes a no cerrar).
    // Devuelve cuántas apps se cerraron.
    int forceStopAll(in String[] whitelist) = 1;
}
