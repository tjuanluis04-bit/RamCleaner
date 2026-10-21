package com.example.ramcleaner

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var textPermissionStatus: TextView
    private lateinit var textRamPercent: TextView
    private lateinit var textResult: TextView
    private lateinit var ramGraphView: RamGraphView
    private lateinit var switchAutoEnabled: Switch
    private lateinit var textThresholdValue: TextView
    private lateinit var seekBarThreshold: SeekBar

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textPermissionStatus = findViewById(R.id.textPermissionStatus)
        textRamPercent = findViewById(R.id.textRamPercent)
        textResult = findViewById(R.id.textResult)
        ramGraphView = findViewById(R.id.ramGraphView)
        switchAutoEnabled = findViewById(R.id.switchAutoEnabled)
        textThresholdValue = findViewById(R.id.textThresholdValue)
        seekBarThreshold = findViewById(R.id.seekBarThreshold)

        ramGraphView.percentText = { percent ->
            textRamPercent.text = "$percent%"
        }

        findViewById<Button>(R.id.buttonCleanNow).setOnClickListener { cleanNow() }
        findViewById<Button>(R.id.buttonWhitelist).setOnClickListener {
            startActivity(Intent(this, WhitelistActivity::class.java))
        }
        findViewById<Button>(R.id.buttonCloseSpecific).setOnClickListener {
            startActivity(Intent(this, CloseAppsActivity::class.java))
        }
        findViewById<Button>(R.id.buttonTopMemory).setOnClickListener {
            startActivity(Intent(this, TopMemoryActivity::class.java))
        }
        findViewById<Button>(R.id.buttonAShell).setOnClickListener {
            startActivity(Intent(this, AShellActivity::class.java))
        }

        // Umbral: SeekBar va de 0..60, representa 40%..100% (pasos de 1%)
        val savedThreshold = PrefsHelper.getThreshold(this)
        seekBarThreshold.progress = (savedThreshold - 40).coerceIn(0, 60)
        textThresholdValue.text = "Umbral: ${savedThreshold}%"

        switchAutoEnabled.isChecked = PrefsHelper.isAutoEnabled(this)
        switchAutoEnabled.setOnCheckedChangeListener { _, isChecked ->
            PrefsHelper.setAutoEnabled(this, isChecked)
            if (isChecked) {
                RamMonitorService.start(this)
            } else {
                RamMonitorService.stop(this)
            }
        }

        seekBarThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val threshold = 40 + progress
                textThresholdValue.text = "Umbral: ${threshold}%"
                if (fromUser) PrefsHelper.setThreshold(this@MainActivity, threshold)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        Shizuku.addRequestPermissionResultListener(permissionListener)
        ensurePermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        AShellHelper.writeScript(this)
    }

    private fun ensurePermission() {
        if (!ShizukuHelper.isShizukuAlive()) {
            textPermissionStatus.text = "⚠ Shizuku no está corriendo. Ábrelo y activa el servicio (ADB inalámbrico o root)."
            return
        }
        if (!ShizukuHelper.hasPermission()) {
            try {
                Shizuku.requestPermission(0)
            } catch (e: Exception) {
                textPermissionStatus.text = "⚠ No se pudo pedir permiso de Shizuku."
            }
        } else {
            updatePermissionStatus()
        }
    }

    private fun updatePermissionStatus() {
        textPermissionStatus.text = when {
            !ShizukuHelper.isShizukuAlive() -> "⚠ Shizuku no está corriendo"
            !ShizukuHelper.hasPermission() -> "⚠ Falta permiso de Shizuku"
            else -> "✓ Shizuku conectado"
        }
    }

    private fun cleanNow() {
        textResult.text = "Cerrando apps..."
        ShizukuHelper.runForceStop(this) { count, error ->
            textResult.text = if (error != null) {
                "✗ $error\nPuedes usar \"Activar por aShell\" como alternativa."
            } else {
                val mode = if (ShizukuHelper.lastBackend == "modo directo") " (modo directo)" else ""
                "✓ $count apps cerradas$mode"
            }
        }
    }
}
