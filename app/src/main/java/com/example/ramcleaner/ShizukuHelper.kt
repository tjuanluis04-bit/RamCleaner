package com.example.ramcleaner

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import rikka.shizuku.Shizuku

/**
 * Cómo se ejecutan las órdenes privilegiadas (de más a menos rápido):
 *
 * 1. UserService de Shizuku, con conexión PERSISTENTE: se conecta una vez y
 *    se reutiliza (antes se creaba y destruía en cada toque, lo que es lento
 *    y a veces nunca conectaba).
 * 2. "Modo directo": si el servicio no conecta en pocos segundos, el mismo
 *    trabajo se hace con un script de shell lanzado a través de Shizuku.
 *
 * En ambos casos el resultado SIEMPRE llega (éxito o error con mensaje) y
 * nada corre en el hilo principal.
 */
object ShizukuHelper {

    private const val CONNECT_TIMEOUT_MS = 8_000L
    private const val RETRY_AFTER_MS = 60_000L
    private const val OPERATION_TIMEOUT_MS = 60_000L

    /** "servicio" o "modo directo": con cuál se hizo la última operación. */
    @Volatile
    var lastBackend: String = ""
        private set

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()

    private var cachedService: IUserService? = null
    private var connection: ServiceConnection? = null
    private var serviceArgs: Shizuku.UserServiceArgs? = null
    private var connecting = false
    private var unreachableSince = 0L
    private var timeoutRunnable: Runnable? = null
    private val waiters = ArrayList<(IUserService?, String?) -> Unit>()

    // ---------- permisos / estado ----------

    fun hasPermission(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /** true si Shizuku está corriendo (ADB inalámbrico / root activo). */
    fun isShizukuAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    private val CRITICAL_PACKAGES = setOf(
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.google.android.gsf.login",
        ShellScripts.SHIZUKU_PACKAGE,
        ShellScripts.ASHELL_PACKAGE
    )

    fun getAutoProtectedPackages(context: Context): Set<String> {
        val protected = mutableSetOf<String>()
        protected.addAll(CRITICAL_PACKAGES)
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName?.let { protected.add(it) }
        } catch (e: Exception) {
            // ignorar
        }
        try {
            val ime = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ime?.substringBefore("/")?.takeIf { it.isNotBlank() }?.let { protected.add(it) }
        } catch (e: Exception) {
            // ignorar
        }
        return protected
    }

    private fun fullWhitelist(context: Context): Array<String> {
        return (PrefsHelper.getWhitelist(context) + getAutoProtectedPackages(context)).toTypedArray()
    }

    // ---------- conexión persistente al UserService ----------

    private fun obtainService(context: Context, callback: (IUserService?, String?) -> Unit) {
        val appContext = context.applicationContext
        var ready: IUserService? = null
        var failNow: String? = null
        var startBind = false

        synchronized(lock) {
            val existing = cachedService
            if (existing != null && existing.asBinder().pingBinder()) {
                ready = existing
            } else {
                if (existing != null) resetConnectionLocked()
                if (connecting) {
                    waiters.add(callback)
                } else if (connection != null && unreachableSince != 0L &&
                    SystemClock.elapsedRealtime() - unreachableSince < RETRY_AFTER_MS
                ) {
                    // Ya falló hace poco: no hacemos esperar otra vez al usuario.
                    failNow = "El servicio de Shizuku no respondió hace poco"
                } else {
                    if (connection != null) resetConnectionLocked()
                    connecting = true
                    startBind = true
                    waiters.add(callback)
                }
            }
        }

        val readyService = ready
        if (readyService != null) {
            callback(readyService, null)
            return
        }
        val failMessage = failNow
        if (failMessage != null) {
            callback(null, failMessage)
            return
        }
        if (startBind) {
            val timeout = Runnable { onConnectTimeout() }
            synchronized(lock) { timeoutRunnable = timeout }
            mainHandler.postDelayed(timeout, CONNECT_TIMEOUT_MS)
            bindNow(appContext)
        }
    }

    private fun bindNow(context: Context) {
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, UserService::class.java.name))
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(false)
            .version(5)

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null || !binder.pingBinder()) {
                    onConnectFailed("Shizuku devolvió un servicio vacío")
                    return
                }
                val service = IUserService.Stub.asInterface(binder)
                val toNotify = synchronized(lock) {
                    cachedService = service
                    connecting = false
                    unreachableSince = 0L
                    timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    timeoutRunnable = null
                    val copy = ArrayList(waiters)
                    waiters.clear()
                    copy
                }
                for (waiter in toNotify) waiter(service, null)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                synchronized(lock) { cachedService = null }
            }
        }

        synchronized(lock) {
            serviceArgs = args
            connection = conn
        }
        try {
            Shizuku.bindUserService(args, conn)
        } catch (e: Exception) {
            onConnectFailed(e.message ?: "no se pudo conectar con Shizuku")
        }
    }

    private fun onConnectFailed(message: String) {
        val toNotify = synchronized(lock) {
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            timeoutRunnable = null
            resetConnectionLocked()
            connecting = false
            val copy = ArrayList(waiters)
            waiters.clear()
            copy
        }
        for (waiter in toNotify) waiter(null, message)
    }

    private fun onConnectTimeout() {
        val toNotify = synchronized(lock) {
            if (!connecting) return
            connecting = false
            timeoutRunnable = null
            // La conexión se deja pendiente: si conecta tarde, se usará después.
            unreachableSince = SystemClock.elapsedRealtime()
            val copy = ArrayList(waiters)
            waiters.clear()
            copy
        }
        for (waiter in toNotify) {
            waiter(null, "El servicio de Shizuku no respondió en ${CONNECT_TIMEOUT_MS / 1000}s")
        }
    }

    private fun resetConnectionLocked() {
        val conn = connection
        val args = serviceArgs
        if (conn != null && args != null) {
            try {
                Shizuku.unbindUserService(args, conn, true)
            } catch (e: Exception) {
                // ignorar
            }
        }
        connection = null
        cachedService = null
        unreachableSince = 0L
    }

    // ---------- modo directo (script de shell vía Shizuku) ----------

    private fun shell(script: String): String {
        val method = try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
        } catch (e: NoSuchMethodException) {
            throw Exception("El modo directo no está disponible con esta versión de Shizuku")
        }
        method.isAccessible = true
        val process = try {
            method.invoke(null, arrayOf("sh", "-c", "{ $script ; } 2>/dev/null"), null, null) as Process
        } catch (e: InvocationTargetException) {
            throw Exception(e.targetException?.message ?: "falló el modo directo")
        }
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }

    private fun parseCount(output: String): Int {
        val line = output.lineSequence().firstOrNull { it.startsWith("COUNT:") }
            ?: throw Exception("El modo directo no devolvió resultado")
        return line.removePrefix("COUNT:").trim().toIntOrNull() ?: 0
    }

    private fun parseUsage(raw: String, limit: Int): List<Pair<String, Long>> {
        val totals = HashMap<String, Long>()
        for (line in raw.lineSequence()) {
            val idx = line.lastIndexOf(':')
            if (idx <= 0) continue
            val name = line.substring(0, idx).trim()
            val kb = line.substring(idx + 1).trim().toLongOrNull() ?: continue
            if (!ShellScripts.isSafeName(name) || name == ShellScripts.OWN_PACKAGE) continue
            totals[name] = (totals[name] ?: 0L) + kb
        }
        return totals.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    // ---------- ejecución genérica ----------

    private fun <T> execute(
        context: Context,
        errorValue: T,
        onResult: (T, String?) -> Unit,
        viaShell: (() -> T)?,
        viaService: (IUserService) -> T
    ) {
        val resolved = AtomicBoolean(false)

        fun finish(value: T, error: String?, backend: String = "") {
            if (resolved.compareAndSet(false, true)) {
                if (backend.isNotEmpty()) lastBackend = backend
                mainHandler.post { onResult(value, error) }
            }
        }

        if (!isShizukuAlive()) {
            finish(errorValue, "Shizuku no está corriendo (¿se desconectó el ADB inalámbrico?)")
            return
        }
        if (!hasPermission()) {
            finish(errorValue, "Sin permiso de Shizuku")
            return
        }

        mainHandler.postDelayed(
            { finish(errorValue, "La operación tardó demasiado") },
            OPERATION_TIMEOUT_MS
        )

        obtainService(context) { service, connectError ->
            executor.execute {
                var failure: String? = connectError
                if (service != null) {
                    try {
                        finish(viaService(service), null, "servicio")
                        return@execute
                    } catch (e: Exception) {
                        failure = e.message ?: "falló el servicio"
                    }
                }
                if (viaShell != null) {
                    try {
                        finish(viaShell(), null, "modo directo")
                        return@execute
                    } catch (e: Exception) {
                        failure = e.message ?: failure
                    }
                }
                finish(errorValue, failure ?: "error desconocido")
            }
        }
    }

    // ---------- API pública ----------

    fun runForceStop(context: Context, onResult: (count: Int, error: String?) -> Unit) {
        val whitelist = fullWhitelist(context)
        execute<Int>(
            context, -1, onResult,
            viaShell = { parseCount(shell(ShellScripts.cleanAll(whitelist.toList()))) },
            viaService = { it.forceStopAll(whitelist) }
        )
    }

    fun setAnimationScale(context: Context, scale: Float, onResult: (success: Boolean, error: String?) -> Unit) {
        execute<Boolean>(
            context, false, onResult,
            viaShell = { shell(ShellScripts.animationScale(scale)); true },
            viaService = { it.setAnimationScale(scale); true }
        )
    }

    fun freezeInactiveApps(context: Context, onResult: (count: Int, error: String?) -> Unit) {
        val whitelist = fullWhitelist(context)
        execute<Int>(
            context, -1, onResult,
            viaShell = { parseCount(shell(ShellScripts.freeze(whitelist.toList()))) },
            viaService = { it.freezeInactiveApps(whitelist) }
        )
    }

    fun unfreezeApps(context: Context, onResult: (count: Int, error: String?) -> Unit) {
        val whitelist = fullWhitelist(context)
        execute<Int>(
            context, -1, onResult,
            viaShell = { parseCount(shell(ShellScripts.unfreeze(whitelist.toList()))) },
            viaService = { it.unfreezeApps(whitelist) }
        )
    }

    fun forceStopSpecific(context: Context, packages: List<String>, onResult: (count: Int, error: String?) -> Unit) {
        execute<Int>(
            context, -1, onResult,
            viaShell = { parseCount(shell(ShellScripts.forceStopSpecific(packages))) },
            viaService = { it.forceStopSpecific(packages.toTypedArray()) }
        )
    }

    fun getTopMemoryUsage(context: Context, limit: Int, onResult: (List<Pair<String, Long>>?, String?) -> Unit) {
        execute<List<Pair<String, Long>>?>(
            context, null, onResult,
            viaShell = { parseUsage(shell(ShellScripts.topMemory()), limit) },
            viaService = {
                val parsed = parseUsage(it.getTopMemoryUsage(limit), limit)
                if (parsed.isEmpty()) throw Exception("el servicio devolvió una lista vacía")
                parsed
            }
        )
    }
}
