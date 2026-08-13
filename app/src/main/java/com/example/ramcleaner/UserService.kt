package com.example.ramcleaner

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Este código NO corre dentro de tu app normal. Shizuku lo lanza en un
 * proceso aparte con la identidad de "shell" (o "root" si usas Sui/root),
 * por eso aquí SÍ podemos ejecutar comandos privilegiados directamente
 * con Runtime.exec, sin pasar por Shizuku.newProcess (que está deprecado).
 */
class UserService : IUserService.Stub() {

    private val myPackage = "com.example.ramcleaner"

    override fun destroy() {
        System.exit(0)
    }

    override fun forceStopAll(whitelist: Array<String>): Int {
        val excluded = whitelist.toHashSet()
        getForegroundPackage()?.let { excluded.add(it) }
        val eligible = getEligiblePackages(excluded)
        val counter = AtomicInteger(0)

        runParallel(eligible) { pkg ->
            try {
                val ok = Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg)).waitFor() == 0
                if (ok) {
                    counter.incrementAndGet()
                    try {
                        Runtime.getRuntime().exec(arrayOf("am", "set-standby-bucket", pkg, "restricted")).waitFor()
                    } catch (e: Exception) {
                        // ignorar
                    }
                }
            } catch (e: Exception) {
                // Algunas apps protegidas pueden fallar; se ignoran y se sigue con las demás.
            }
        }
        return counter.get()
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

    override fun freezeInactiveApps(whitelist: Array<String>): Int {
        return setStandbyBucketForEligible(whitelist, "restricted")
    }

    override fun unfreezeApps(whitelist: Array<String>): Int {
        return setStandbyBucketForEligible(whitelist, "active")
    }

    override fun getTopMemoryUsage(limit: Int): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys meminfo"))
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

            val startIdx = output.indexOf("Total PSS by process:")
            if (startIdx == -1) return ""
            val section = output.substring(startIdx)
            val regex = Regex("""^\s*([\d,]+)K:\s+([a-zA-Z0-9_.]+)""")

            val result = StringBuilder()
            var count = 0
            for (rawLine in section.lineSequence().drop(1)) {
                if (count >= limit) break
                val line = rawLine.trimEnd()
                if (line.isBlank()) continue
                if (!line.trimStart().firstOrNull().let { it != null && (it.isDigit()) }) break
                val m = regex.find(line) ?: continue
                val kb = m.groupValues[1].replace(",", "").toLongOrNull() ?: continue
                val pkg = m.groupValues[2]
                if (pkg == myPackage) continue
                result.append(pkg).append(":").append(kb).append("\n")
                count++
            }
            result.toString()
        } catch (e: Exception) {
            ""
        }
    }

    // ---------- helpers internos ----------

    private fun setStandbyBucketForEligible(whitelist: Array<String>, bucket: String): Int {
        val excluded = whitelist.toHashSet()
        getForegroundPackage()?.let { excluded.add(it) }
        val eligible = getEligiblePackages(excluded)
        val counter = AtomicInteger(0)

        runParallel(eligible) { pkg ->
            try {
                val ok = Runtime.getRuntime().exec(arrayOf("am", "set-standby-bucket", pkg, bucket)).waitFor() == 0
                if (ok) counter.incrementAndGet()
            } catch (e: Exception) {
                // ignorar
            }
        }
        return counter.get()
    }

    private fun getEligiblePackages(excluded: Set<String>): List<String> {
        return try {
            val listProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", "pm list packages -3"))
            val packages = listProcess.inputStream.bufferedReader().readLines()
                .mapNotNull { line -> line.removePrefix("package:").trim().takeIf { it.isNotBlank() } }
            listProcess.waitFor()
            packages.filter { it != myPackage && !excluded.contains(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Ejecuta "action" para cada paquete en paralelo (4 hilos a la vez) en
     * vez de uno por uno con pausas — esto es lo que hace que cerrar/congelar
     * todas las apps sea notablemente más rápido.
     */
    private fun runParallel(packages: List<String>, action: (String) -> Unit) {
        if (packages.isEmpty()) return
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = packages.map { pkg -> pool.submit { action(pkg) } }
            futures.forEach { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    /**
     * Usa "dumpsys window" (disponible porque este proceso corre con
     * privilegios de shell) para saber qué app está en primer plano ahora
     * mismo, y así nunca cerrarla/congelarla por accidente.
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
}
