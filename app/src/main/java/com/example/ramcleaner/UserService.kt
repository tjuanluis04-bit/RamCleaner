package com.example.ramcleaner

import java.io.File
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

    override fun forceStopSpecific(packages: Array<String>): Int {
        val counter = AtomicInteger(0)
        runParallel(packages.toList()) { pkg ->
            try {
                val ok = Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg)).waitFor() == 0
                if (ok) counter.incrementAndGet()
            } catch (e: Exception) {
                // ignorar
            }
        }
        return counter.get()
    }

    /**
     * Versión RÁPIDA: antes usaba "dumpsys meminfo", que calcula PSS exacto
     * para cada proceso y puede tardar varios segundos (o mucho más en
     * equipos con muchas apps) porque recorre regiones de memoria compartida
     * una por una. Ahora lee directamente /proc/<pid>/status (campo VmRSS),
     * que es una simple lectura de archivo — sin exec, sin dumpsys — y
     * normalmente termina en milisegundos. El costo es que RSS no es tan
     * exacto como PSS (puede contar un poco de memoria compartida de más),
     * pero para "qué app está usando más RAM ahora" es más que suficiente
     * y la diferencia de velocidad es enorme.
     */
    override fun getTopMemoryUsage(limit: Int): String {
        return try {
            val procDir = File("/proc")
            val pidDirs = procDir.listFiles { f -> f.isDirectory && f.name.toIntOrNull() != null }
                ?: return ""

            val totals = HashMap<String, Long>()

            for (pidDir in pidDirs) {
                try {
                    val cmdlineFile = File(pidDir, "cmdline")
                    if (!cmdlineFile.exists()) continue
                    val rawCmdline = cmdlineFile.readBytes().toString(Charsets.ISO_8859_1)
                    val cmdline = rawCmdline.trim('\u0000', ' ')
                    if (cmdline.isBlank() || !cmdline.contains('.')) continue

                    val statusFile = File(pidDir, "status")
                    if (!statusFile.exists()) continue

                    var rssKb: Long? = null
                    statusFile.forEachLine { line ->
                        if (rssKb == null && line.startsWith("VmRSS:")) {
                            rssKb = line.removePrefix("VmRSS:").trim().removeSuffix("kB").trim().toLongOrNull()
                        }
                    }
                    val kb = rssKb ?: continue

                    // "pkg:proceso_secundario" -> nos quedamos con el paquete base
                    val pkg = cmdline.substringBefore(':')
                    if (pkg == myPackage) continue

                    totals[pkg] = (totals[pkg] ?: 0L) + kb
                } catch (e: Exception) {
                    // el proceso puede haber muerto entre el listado y la lectura; se ignora
                }
            }

            totals.entries
                .sortedByDescending { it.value }
                .take(limit)
                .joinToString("\n") { "${it.key}:${it.value}" }
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
