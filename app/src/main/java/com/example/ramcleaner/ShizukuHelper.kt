package com.example.ramcleaner

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import java.util.concurrent.atomic.AtomicBoolean
import rikka.shizuku.Shizuku

object ShizukuHelper {

    /**
     * Tiempo máximo de espera por una respuesta del UserService antes de
     * darla por fallida. Antes, si Shizuku se desconectaba a medio camino
     * (ADB inalámbrico que se cae, el servicio muere, etc.), la llamada se
     * quedaba esperando para siempre sin avisar nada — eso es lo que hacía
     * que el tile pareciera "no responder" o que la pantalla de RAM se
     * quedara en "Cargando..." indefinidamente.
     */
    private const val TIMEOUT_MS = 8000L

    private fun userServiceArgs(context: Context) = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, UserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("service")
        .debuggable(false)
        .version(4)

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
        "com.google.android.gsf.login"
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

    /**
     * Envoltura genérica: conecta al UserService y ejecuta "block". Pase lo
     * que pase (éxito, error, o que nunca llegue respuesta) "onResult" se
     * llama UNA sola vez, antes de TIMEOUT_MS. Así ningún llamador (tile,
     * widget, pantallas) se queda esperando para siempre.
     */
    private fun <T> callService(
        context: Context,
        timeoutValue: T,
        onResult: (T, String?) -> Unit,
        block: (IUserService) -> Pair<T, String?>
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        val resolved = AtomicBoolean(false)

        fun finish(value: T, error: String?) {
            if (resolved.compareAndSet(false, true)) {
                mainHandler.post { onResult(value, error) }
            }
        }

        if (!isShizukuAlive()) {
            finish(timeoutValue, "Shizuku no está corriendo (¿se desconectó el ADB inalámbrico?)")
            return
        }
        if (!hasPermission()) {
            finish(timeoutValue, "Sin permiso de Shizuku")
            return
        }

        val args = userServiceArgs(context)
        lateinit var connection: ServiceConnection
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    val service = IUserService.Stub.asInterface(binder)
                    val (value, error) = block(service)
                    finish(value, error)
                } catch (e: Exception) {
                    finish(timeoutValue, e.message ?: "error desconocido")
                } finally {
                    try {
                        Shizuku.unbindUserService(args, connection, false)
                    } catch (e: Exception) {
                        // ignorar
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                finish(timeoutValue, "El servicio privilegiado se desconectó")
            }
        }

        mainHandler.postDelayed({
            finish(timeoutValue, "Tiempo de espera agotado (¿Shizuku sigue activo?)")
        }, TIMEOUT_MS)

        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            finish(timeoutValue, e.message ?: "no se pudo conectar con Shizuku")
        }
    }

    fun runForceStop(context: Context, onResult: (count: Int, error: String?) -> Unit) {
        callService(context, -1, onResult) { service ->
            service.forceStopAll(fullWhitelist(context)) to null
        }
    }

    fun setAnimationScale(context: Context, scale: Float, onResult: (success: Boolean, error: String?) -> Unit) {
        callService(context, false, onResult) { service ->
            service.setAnimationScale(scale)
            true to null
        }
    }

    fun freezeInactiveApps(context: Context, onResult: (count: Int, error: String?) -> Unit) {
        callService(context, -1, onResult) { service ->
            service.freezeInactiveApps(fullWhitelist(context)) to null
        }
    }

    fun unfreezeApps(context: Context, onResult: (count: Int, error: String?) -> Unit) {
        callService(context, -1, onResult) { service ->
            service.unfreezeApps(fullWhitelist(context)) to null
        }
    }

    fun forceStopSpecific(context: Context, packages: List<String>, onResult: (count: Int, error: String?) -> Unit) {
        callService(context, -1, onResult) { service ->
            service.forceStopSpecific(packages.toTypedArray()) to null
        }
    }

    fun getTopMemoryUsage(context: Context, limit: Int, onResult: (List<Pair<String, Long>>?, String?) -> Unit) {
        callService(context, null, onResult) { service ->
            val raw = service.getTopMemoryUsage(limit)
            val parsed = raw.lineSequence()
                .mapNotNull { line ->
                    val idx = line.lastIndexOf(':')
                    if (idx <= 0) return@mapNotNull null
                    val pkg = line.substring(0, idx)
                    val kb = line.substring(idx + 1).toLongOrNull() ?: return@mapNotNull null
                    pkg to kb
                }
                .toList()
            parsed to null
        }
    }
}
