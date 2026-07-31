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

        // Protección extra: nunca cerrar la app que esté en primer plano
        // justo en este momento (evita matar el launcher/wallpaper/teclado
        // mientras están activos, que es lo que dispara "System UI no responde").
        getForegroundPackage()?.let { excluded.add(it) }

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

                    // Mete la app en el bucket "restricted" de App Standby para que
                    // Android le restrinja el auto-inicio en segundo plano y no
                    // vuelva a llenar la RAM sola apenas la cerramos.
                    try {
                        Runtime.getRuntime().exec(arrayOf("am", "set-standby-bucket", pkg, "restricted")).waitFor()
                    } catch (e: Exception) {
                        // ignorar
                    }

                    // Pausa corta entre cada cierre para no saturar CPU/IO de golpe.
                    Thread.sleep(60)
                } catch (e: Exception) {
                    // Algunas apps protegidas pueden fallar; se ignoran y se sigue con las demás.
                }
            }
        } catch (e: Exception) {
            // Si falla el listado completo, simplemente no se cierra nada.
        }
        return closed
    }

    /**
     * Usa "dumpsys window" (disponible porque este proceso corre con
     * privilegios de shell) para saber qué app está en primer plano ahora
     * mismo, y así nunca cerrarla por accidente.
     */
    private fun getForegroundPackage(): String? {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys window | grep mCurrentFocus"))
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            val regex = Regex("""([a-zA-Z0-9_.]+)/[a-zA-Z0-9_.$]+}""")
            regex.find(output)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    override fun setAnimationScale(scale: Float) {
        val value = scale.toString()
        val keys = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        for (key in keys) {
            try {
                Runtime.getRuntime().exec(arrayOf("settings", "put", "global", key, value)).waitFor()
            } catch (e: Exception) {
                // ignorar
            }
        }
    }
}
