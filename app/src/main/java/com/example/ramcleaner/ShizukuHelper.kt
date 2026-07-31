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
        .version(2)

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
        "com.google.android.gms",           // Google Play Services
        "com.google.android.gsf",           // Google Services Framework
        "com.android.vending",              // Play Store
        "com.google.android.gsf.login"
    )

    /**
     * Apps que siempre se deben proteger sin que el usuario tenga que
     * agregarlas manualmente: el launcher actual, el teclado actual, y
     * los paquetes críticos de Google. Cerrarlos de golpe es lo que suele
     * provocar que se trabe el fondo de pantalla o salga "System UI no responde".
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

    /**
     * Se conecta al UserService, ejecuta forceStopAll respetando la whitelist
     * guardada en PrefsHelper (más el launcher/teclado, protegidos siempre),
     * y entrega el resultado por callback.
     * onResult(count, null) en éxito, onResult(-1, "mensaje") en error.
     */
    fun runForceStop(context: Context, onResult: (count: Int, error: String?) -> Unit) {
        if (!hasPermission()) {
            onResult(-1, "Sin permiso de Shizuku")
            return
        }
        val whitelist = (PrefsHelper.getWhitelist(context) + getAutoProtectedPackages(context)).toTypedArray()
        val args = userServiceArgs(context)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    val service = IUserService.Stub.asInterface(binder)
                    val count = service.forceStopAll(whitelist)
                    onResult(count, null)
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

    /**
     * Ajusta la escala de animaciones del sistema (1.0 normal, 0.5 rápidas, 0 desactivadas).
     */
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
}
