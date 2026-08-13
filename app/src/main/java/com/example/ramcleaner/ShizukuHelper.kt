package com.example.ramcleaner

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Settings
import rikka.shizuku.Shizuku

object ShizukuHelper {

    private fun userServiceArgs(context: Context) = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, UserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("service")
        .debuggable(false)
        .version(3)

    fun hasPermission(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Paquetes que jamás se deben cerrar ni restringir, sin importar lo
     * que reporte "pm list packages -3". En algunos equipos Google Play
     * Services / Play Store aparecen ahí como si fueran de terceros, y
     * forzar su cierre es lo que provoca que se caiga la interfaz del
     * sistema (tiles en "no disponible", toques fantasma, "Google Play
     * Services se detuvo").
     */
    private val CRITICAL_PACKAGES = setOf(
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.google.android.gsf.login"
    )

    /**
     * Apps que siempre se deben proteger sin que el usuario tenga que
     * agregarlas manualmente: el launcher actual, el teclado actual, y
     * los paquetes críticos de Google.
     */
    private fun getAutoProtectedPackages(context: Context): Set<String> {
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

    /** Se conecta al UserService, ejecuta forceStopAll respetando la whitelist. */
    fun runForceStop(context: Context, onResult: (count: Int, error: String?) -> Unit) {
        if (!hasPermission()) {
            onResult(-1, "Sin permiso de Shizuku")
            return
        }
        val args = userServiceArgs(context)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    val service = IUserService.Stub.asInterface(binder)
                    onResult(service.forceStopAll(fullWhitelist(context)), null)
                } catch (e: Exception) {
                    onResult(-1, e.message ?: "error desconocido")
                } finally {
                    try {
                        Shizuku.unbindUserService(args, this, false)
                    } catch (e: Exception) {
                        // ignorar
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        Shizuku.bindUserService(args, connection)
    }

    fun setAnimationScale(context: Context, scale: Float, onResult: (success: Boolean, error: String?) -> Unit) {
        if (!hasPermission()) {
            onResult(false, "Sin permiso de Shizuku")
            return
        }
        val args = userServiceArgs(context)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    val service = IUserService.Stub.asInterface(binder)
                    service.setAnimationScale(scale)
                    onResult(true, null)
                } catch (e: Exception) {
                    onResult(false, e.message ?: "error desconocido")
                } finally {
                    try {
                        Shizuku.unbindUserService(args, this, false)
                    } catch (e: Exception) {
                        // ignorar
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        Shizuku.bindUserService(args, connection)
    }

    /** Congela (restricted standby bucket) todas las apps elegibles, sin cerrarlas. */
    fun freezeInactiveApps(context: Context, onResult: (count: Int, error: String?) -> Unit) {
        if (!hasPermission()) {
            onResult(-1, "Sin permiso de Shizuku")
            return
        }
        val args = userServiceArgs(context)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    val service = IUserService.Stub.asInterface(binder)
                    onResult(service.freezeInactiveApps(fullWhitelist(context)), null)
                } catch (e: Exception) {
                    onResult(-1, e.message ?: "error desconocido")
                } finally {
                    try {
                        Shizuku.unbindUserService(args, this, false)
                    } catch (e: Exception) {
                        // ignorar
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        Shizuku.bindUserService(args, connection)
    }

    /** Revierte el congelado de todas las apps elegibles. */
    fun unfreezeApps(context: Context, onResult: (count: Int, error: String?) -> Unit) {
        if (!hasPermission()) {
            onResult(-1, "Sin permiso de Shizuku")
            return
        }
        val args = userServiceArgs(context)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    val service = IUserService.Stub.asInterface(binder)
                    onResult(service.unfreezeApps(fullWhitelist(context)), null)
                } catch (e: Exception) {
                    onResult(-1, e.message ?: "error desconocido")
                } finally {
                    try {
                        Shizuku.unbindUserService(args, this, false)
                    } catch (e: Exception) {
                        // ignorar
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        Shizuku.bindUserService(args, connection)
    }

    /** Devuelve pares (paquete, KB usados) ordenados de mayor a menor consumo. */
    fun getTopMemoryUsage(context: Context, limit: Int, onResult: (List<Pair<String, Long>>?, String?) -> Unit) {
        if (!hasPermission()) {
            onResult(null, "Sin permiso de Shizuku")
            return
        }
        val args = userServiceArgs(context)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    val service = IUserService.Stub.asInterface(binder)
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
                    onResult(parsed, null)
                } catch (e: Exception) {
                    onResult(null, e.message ?: "error desconocido")
                } finally {
                    try {
                        Shizuku.unbindUserService(args, this, false)
                    } catch (e: Exception) {
                        // ignorar
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        Shizuku.bindUserService(args, connection)
    }
}
