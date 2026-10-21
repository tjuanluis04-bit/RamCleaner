package com.example.ramcleaner

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CloseAppsActivity : AppCompatActivity() {

    private data class AppEntry(val packageName: String, val label: String)

    private lateinit var allApps: List<AppEntry>
    private var filteredApps: List<AppEntry> = emptyList()
    private lateinit var adapter: Adapter
    private lateinit var textStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_close_apps)

        textStatus = findViewById(R.id.textStatus)
        val listView = findViewById<ListView>(R.id.listApps)
        val editSearch = findViewById<EditText>(R.id.editSearch)

        val pm = packageManager
        allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName }
            .map { info: ApplicationInfo ->
                AppEntry(info.packageName, pm.getApplicationLabel(info).toString())
            }
            .sortedBy { it.label.lowercase() }

        filteredApps = allApps
        adapter = Adapter()
        listView.adapter = adapter

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase().orEmpty()
                filteredApps = if (q.isEmpty()) allApps else allApps.filter {
                    it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
                }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun closeApp(entry: AppEntry) {
        textStatus.text = "Cerrando ${entry.label}..."
        ShizukuHelper.forceStopSpecific(this, listOf(entry.packageName)) { count, error ->
            textStatus.text = if (error != null) "✗ $error" else "✓ ${entry.label} cerrada"
        }
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount(): Int = filteredApps.size
        override fun getItem(position: Int): Any = filteredApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@CloseAppsActivity)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            val entry = filteredApps[position]
            val textView = view.findViewById<TextView>(android.R.id.text1)
            textView.text = "${entry.label}\n${entry.packageName}"
            textView.textSize = 14f
            view.setOnClickListener { closeApp(entry) }
            return view
        }
    }
}
