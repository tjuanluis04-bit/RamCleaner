package com.example.ramcleaner

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CloseAppsActivity : AppCompatActivity() {

    private lateinit var searchBox: EditText
    private lateinit var listView: ListView
    private lateinit var statusText: TextView

    private lateinit var allApps: List<ApplicationInfo>
    private var filteredApps: List<ApplicationInfo> = emptyList()
    private val checkedPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_close_apps)

        searchBox = findViewById(R.id.searchBox)
        listView = findViewById(R.id.closeAppsListView)
        statusText = findViewById(R.id.closeAppsStatus)
        val closeButton = findViewById<Button>(R.id.closeSelectedButton)

        val pm = packageManager
        val protectedPkgs = ShizukuHelper.getAutoProtectedPackages(this)
        allApps = pm.getInstalledApplications(0)
            .filter {
                (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    it.packageName != packageName &&
                    !protectedPkgs.contains(it.packageName)
            }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        applyFilter("")

        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = filteredApps[position].packageName
            if (listView.isItemChecked(position)) {
                checkedPackages.add(pkg)
            } else {
                checkedPackages.remove(pkg)
            }
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString().orEmpty())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        closeButton.setOnClickListener {
            if (checkedPackages.isEmpty()) {
                Toast.makeText(this, "Selecciona al menos una app", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            statusText.text = "Cerrando…"
            val toClose = checkedPackages.toList()
            ShizukuHelper.forceStopSpecific(this, toClose) { count, error ->
                runOnUiThread {
                    if (error != null) {
                        statusText.text = "Error: $error"
                    } else {
                        statusText.text = "$count apps cerradas"
                        checkedPackages.clear()
                        applyFilter(searchBox.text?.toString().orEmpty())
                    }
                }
            }
        }
    }

    private fun applyFilter(query: String) {
        val pm = packageManager
        filteredApps = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter { pm.getApplicationLabel(it).toString().contains(query, ignoreCase = true) }
        }
        val labels = filteredApps.map { pm.getApplicationLabel(it).toString() }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, labels)
        filteredApps.forEachIndexed { index, app ->
            listView.setItemChecked(index, checkedPackages.contains(app.packageName))
        }
    }
}
