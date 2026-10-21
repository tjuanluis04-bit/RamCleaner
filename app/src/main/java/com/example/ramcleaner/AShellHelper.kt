package com.example.ramcleaner

import android.content.Context
import java.io.File

/**
 * Soporte para activar la limpieza desde aShell (terminal con Shizuku):
 * genera el script con tu lista blanca actual y lo deja listo para copiar,
 * o guardado en un archivo para ejecutarlo con un comando corto.
 */
object AShellHelper {

    fun excluded(context: Context): List<String> =
        (PrefsHelper.getWhitelist(context) + ShizukuHelper.getAutoProtectedPackages(context)).toList()

    /** Script completo en una sola línea, listo para pegar en aShell. */
    fun fullScript(context: Context): String = ShellScripts.cleanAll(excluded(context))

    private fun scriptFile(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: return null
        return File(dir, "limpiar.sh")
    }

    /** Guarda el script (con la lista blanca actual) y devuelve el archivo. */
    fun writeScript(context: Context): File? {
        return try {
            val file = scriptFile(context) ?: return null
            file.writeText(fullScript(context) + "\n")
            file.setReadable(true, false)
            file
        } catch (e: Exception) {
            null
        }
    }

    /** Comando corto: sh /ruta/limpiar.sh */
    fun shortCommand(context: Context): String? {
        val file = writeScript(context) ?: return null
        return "sh ${file.absolutePath}"
    }
}
