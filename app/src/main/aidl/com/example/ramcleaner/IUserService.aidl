package com.example.ramcleaner;

interface IUserService {

    // ID de transacción reservado que Shizuku usa para pedir que el
    // servicio se cierre limpiamente. No cambiar el valor.
    void destroy() = 16777114;

    // Cierra (force-stop) todas las apps de usuario (no del sistema).
    // Devuelve cuántas apps se cerraron.
    int forceStopAll() = 1;
}
