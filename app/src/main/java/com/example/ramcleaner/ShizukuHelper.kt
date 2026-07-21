package com.example.ramcleaner

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

object ShizukuHelper {

    private fun userServiceArgs(context: Context) = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, UserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("service")
        .debuggable(false)
        .version(1)

    fun hasPermission(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Se conecta al UserService, ejecuta forceStopAll respetando la whitelist
     * guardada en PrefsHelper, y entrega el resultado por callback.
     * onResult(count, null) en éxito, onResult(-1, "mensaje") en error.
     */
    fun runForceStop(context: Context, onResult: (count: Int, error: String?) -> Unit) {
        if (!hasPermission()) {
            onResult(-1, "Sin permiso de Shizuku")
            return
        }
        val whitelist = PrefsHelper.getWhitelist(context).toTypedArray()
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
}
