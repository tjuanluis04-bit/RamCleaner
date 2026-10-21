package com.example.ramcleaner

import java.util.Locale

/**
 * Scripts de shell equivalentes a lo que hace el UserService. Se usan para:
 *  1) el "modo directo" (respaldo cuando el servicio de Shizuku no conecta), y
 *  2) el modo aShell: el usuario pega el comando en aShell y se ejecuta igual.
 *
 * En el código se escribe "§" en lugar de "$" para no pelear con las
 * plantillas de Kotlin; fix() lo convierte al final.
 */
object ShellScripts {

    const val OWN_PACKAGE = "com.example.ramcleaner"
    const val ASHELL_PACKAGE = "in.sunilpaulmathew.ashell"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private val SAFE_NAME = Regex("^[A-Za-z0-9._]+$")

    fun isSafeName(name: String): Boolean = SAFE_NAME.matches(name)

    private fun fix(script: String): String = script.replace('§', '$')

    private fun skipPattern(excluded: Collection<String>): String {
        val names = sortedSetOf(OWN_PACKAGE, ASHELL_PACKAGE, SHIZUKU_PACKAGE)
        excluded.filterTo(names) { isSafeName(it) }
        return (listOf("\"§fg\"") + names).joinToString("|")
    }

    /**
     * Recorre las apps de usuario (pm list packages -3), se salta las
     * excluidas y la app en primer plano, y ejecuta "action" (usa §p) en
     * grupos de 4 en paralelo. Al final imprime "COUNT:<n>".
     */
    private fun eligibleLoop(excluded: Collection<String>, action: String): String {
        val script = "fg=§(dumpsys window | grep mCurrentFocus | sed 's/.*[ ]\\([A-Za-z0-9_.]*\\)\\/.*/\\1/'); " +
            "n=0; for p in §(pm list packages -3 | cut -d: -f2); do " +
            "case \"§p\" in ${skipPattern(excluded)}) ;; " +
            "*) $action n=§((n+1)); [ §((n%4)) -eq 0 ] && wait;; esac; done; wait; echo \"COUNT:§n\""
        return fix(script)
    }

    fun cleanAll(excluded: Collection<String>): String =
        eligibleLoop(excluded, "(am force-stop \"§p\"; am set-standby-bucket \"§p\" restricted) &")

    fun freeze(excluded: Collection<String>): String =
        eligibleLoop(excluded, "am set-standby-bucket \"§p\" restricted &")

    fun unfreeze(excluded: Collection<String>): String =
        eligibleLoop(excluded, "am set-standby-bucket \"§p\" active &")

    fun forceStopSpecific(packages: Collection<String>): String {
        val safe = packages.filter { isSafeName(it) }
        if (safe.isEmpty()) return "echo COUNT:0"
        val script = "n=0; for p in ${safe.joinToString(" ")}; do " +
            "(am force-stop \"§p\") & n=§((n+1)); [ §((n%4)) -eq 0 ] && wait; done; wait; echo \"COUNT:§n\""
        return fix(script)
    }

    fun animationScale(scale: Float): String {
        val v = String.format(Locale.US, "%.2f", scale)
        return "settings put global window_animation_scale $v; " +
            "settings put global transition_animation_scale $v; " +
            "settings put global animator_duration_scale $v"
    }

    /** Imprime líneas "paquete:kB" leyendo /proc con builtins (sin forks). */
    fun topMemory(): String = fix(
        "for d in /proc/[0-9]*; do c=; read -r c < §d/cmdline 2>/dev/null; " +
            "case \"§c\" in *.*) " +
            "while read -r k v u; do if [ \"§k\" = \"VmRSS:\" ]; then echo \"§{c%%:*}:§v\"; break; fi; done < §d/status 2>/dev/null;; " +
            "esac; done"
    )
}
