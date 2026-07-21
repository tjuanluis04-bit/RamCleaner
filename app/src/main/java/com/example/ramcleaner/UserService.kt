package com.example.ramcleaner

/**
 * Este código NO corre dentro de tu app normal. Shizuku lo lanza en un
 * proceso aparte con la identidad de "shell" (o "root" si usas Sui/root),
 * por eso aquí SÍ podemos ejecutar comandos privilegiados directamente
 * con Runtime.exec, sin pasar por Shizuku.newProcess (que está deprecado).
 */
class UserService : IUserService.Stub() {

    override fun destroy() {
        // Shizuku llama esto para pedir que el servicio termine.
        System.exit(0)
    }

    override fun forceStopAll(whitelist: Array<String>): Int {
        var closed = 0
        val excluded = whitelist.toHashSet()
        try {
            // "pm list packages -3" = solo apps de terceros (no del sistema)
            val listProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", "pm list packages -3"))
            val packages = listProcess.inputStream.bufferedReader().readLines()
                .mapNotNull { line ->
                    line.removePrefix("package:").trim().takeIf { it.isNotBlank() }
                }
            listProcess.waitFor()

            val myPackage = "com.example.ramcleaner"

            for (pkg in packages) {
                if (pkg == myPackage) continue
                if (excluded.contains(pkg)) continue
                try {
                    val stopProcess = Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg))
                    stopProcess.waitFor()
                    closed++
                } catch (e: Exception) {
                    // Algunas apps protegidas pueden fallar; se ignoran y se sigue con las demás.
                }
            }
        } catch (e: Exception) {
            // Si falla el listado completo, simplemente no se cierra nada.
        }
        return closed
    }
}
