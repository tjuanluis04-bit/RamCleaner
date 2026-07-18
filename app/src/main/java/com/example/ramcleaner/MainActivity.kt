package com.example.ramcleaner

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private var userService: IUserService? = null
    private val permissionRequestCode = 1001

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("service")
        .debuggable(BuildConfig.DEBUG)
        .version(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            userService = IUserService.Stub.asInterface(binder)
            Toast.makeText(this@MainActivity, "Servicio conectado", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == permissionRequestCode) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindUserService()
            } else {
                Toast.makeText(this, "Permiso de Shizuku denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Shizuku.addRequestPermissionResultListener(permissionListener)

        val statusText = findViewById<TextView>(R.id.statusText)
        val requestButton = findViewById<Button>(R.id.requestPermissionButton)
        val killButton = findViewById<Button>(R.id.killAllButton)

        requestButton.setOnClickListener { requestShizukuPermission() }

        killButton.setOnClickListener {
            val service = userService
            if (service == null) {
                Toast.makeText(this, "El servicio aún no está listo. Pide el permiso primero.", Toast.LENGTH_SHORT).show()
            } else {
                val count = service.forceStopAll()
                statusText.text = "Se cerraron $count apps"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (e: Exception) {
            // Puede fallar si nunca se llegó a conectar; se ignora.
        }
    }

    private fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku no está corriendo. Ábrelo primero.", Toast.LENGTH_LONG).show()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bindUserService()
        } else {
            Shizuku.requestPermission(permissionRequestCode)
        }
    }

    private fun bindUserService() {
        Shizuku.bindUserService(userServiceArgs, serviceConnection)
    }
}
