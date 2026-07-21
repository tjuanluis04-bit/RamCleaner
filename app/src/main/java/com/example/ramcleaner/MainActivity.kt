package com.example.ramcleaner

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private val permissionRequestCode = 1001
    private val notificationPermissionRequestCode = 2001

    private lateinit var statusText: TextView
    private lateinit var autoModeSwitch: Switch
    private lateinit var thresholdSeekBar: SeekBar
    private lateinit var thresholdValueText: TextView

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == permissionRequestCode) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso de Shizuku concedido", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permiso de Shizuku denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Shizuku.addRequestPermissionResultListener(permissionListener)

        statusText = findViewById(R.id.statusText)
        val requestButton = findViewById<Button>(R.id.requestPermissionButton)
        val killButton = findViewById<Button>(R.id.killAllButton)
        val whitelistButton = findViewById<Button>(R.id.whitelistButton)
        autoModeSwitch = findViewById(R.id.autoModeSwitch)
        thresholdSeekBar = findViewById(R.id.thresholdSeekBar)
        thresholdValueText = findViewById(R.id.thresholdValueText)

        requestButton.setOnClickListener { requestShizukuPermission() }
        killButton.setOnClickListener { performForceStop() }
        whitelistButton.setOnClickListener {
            startActivity(Intent(this, WhitelistActivity::class.java))
        }

        setupAutoModeControls()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    // ---------- Permiso de Shizuku ----------

    private fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku no está corriendo. Ábrelo primero.", Toast.LENGTH_LONG).show()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Ya tienes el permiso", Toast.LENGTH_SHORT).show()
        } else {
            Shizuku.requestPermission(permissionRequestCode)
        }
    }

    // ---------- Botón manual ----------

    private fun performForceStop() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memBefore = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memBefore)

        statusText.text = "Cerrando apps…"

        ShizukuHelper.runForceStop(this) { count, error ->
            runOnUiThread {
                if (error != null) {
                    statusText.text = "Error: $error"
                    return@runOnUiThread
                }
                val memAfter = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memAfter)
                val freedMb = (memAfter.availMem - memBefore.availMem) / (1024 * 1024)
                val freedText = if (freedMb > 0) "$freedMb MB liberados" else "sin cambio medible en RAM libre"
                statusText.text = "Se cerraron $count apps · $freedText"
            }
        }
    }

    // ---------- Auto-activación por umbral ----------

    private fun setupAutoModeControls() {
        val threshold = PrefsHelper.getThreshold(this)
        thresholdSeekBar.max = PrefsHelper.MAX_THRESHOLD - PrefsHelper.MIN_THRESHOLD
        thresholdSeekBar.progress = threshold - PrefsHelper.MIN_THRESHOLD
        thresholdValueText.text = "$threshold%"
        autoModeSwitch.isChecked = PrefsHelper.isAutoEnabled(this)

        thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percent = progress + PrefsHelper.MIN_THRESHOLD
                thresholdValueText.text = "$percent%"
                if (fromUser) {
                    PrefsHelper.setThreshold(this@MainActivity, percent)
                    if (autoModeSwitch.isChecked) {
                        // reinicia el servicio para que tome el nuevo umbral de inmediato
                        startForegroundService(Intent(this@MainActivity, RamMonitorService::class.java))
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        autoModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            PrefsHelper.setAutoEnabled(this, isChecked)
            if (isChecked) {
                ensureNotificationPermission()
                startForegroundService(Intent(this, RamMonitorService::class.java))
                Toast.makeText(this, "Monitoreo automático activado", Toast.LENGTH_SHORT).show()
            } else {
                stopService(Intent(this, RamMonitorService::class.java))
                Toast.makeText(this, "Monitoreo automático desactivado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationPermissionRequestCode)
            }
        }
    }
}
