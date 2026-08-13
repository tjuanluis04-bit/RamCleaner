package com.example.ramcleaner

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TopMemoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_memory)

        val listView = findViewById<ListView>(R.id.topMemoryListView)
        val statusText = findViewById<TextView>(R.id.topMemoryStatus)
        statusText.text = "Cargando…"

        ShizukuHelper.getTopMemoryUsage(this, 15) { results, error ->
            runOnUiThread {
                if (error != null || results == null) {
                    statusText.text = "Error: ${error ?: "sin datos"}"
                    return@runOnUiThread
                }
                if (results.isEmpty()) {
                    statusText.text = "No se pudo leer el uso de memoria en este equipo"
                    return@runOnUiThread
                }
                statusText.text = ""
                val pm = packageManager
                val rows = results.map { (pkg, kb) ->
                    val label = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                    val mb = kb / 1024
                    "$label — $mb MB"
                }
                listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
            }
        }
    }
}
