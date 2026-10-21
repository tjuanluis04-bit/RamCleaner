package com.example.ramcleaner

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TopMemoryActivity : AppCompatActivity() {

    private data class UsageEntry(val packageName: String, val label: String, val mb: Long)

    private var entries: List<UsageEntry> = emptyList()
    private lateinit var adapter: Adapter
    private lateinit var textStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_memory)

        textStatus = findViewById(R.id.textStatus)
        val listView = findViewById<ListView>(R.id.listApps)
        adapter = Adapter()
        listView.adapter = adapter

        load()
    }

    private fun load() {
        textStatus.text = getString(R.string.loading)
        val pm = packageManager
        ShizukuHelper.getTopMemoryUsage(this, 30) { result, error ->
            if (error != null) {
                textStatus.text = "✗ $error"
                return@getTopMemoryUsage
            }
            val list = result.orEmpty().mapNotNull { (pkg, kb) ->
                try {
                    val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
                    UsageEntry(pkg, label, kb / 1024)
                } catch (e: Exception) {
                    UsageEntry(pkg, pkg, kb / 1024)
                }
            }
            entries = list
            textStatus.text = if (list.isEmpty()) "No se pudo leer el uso de RAM" else "Actualizado"
            adapter.notifyDataSetChanged()
        }
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@TopMemoryActivity)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            val entry = entries[position]
            view.findViewById<TextView>(android.R.id.text1).text = entry.label
            view.findViewById<TextView>(android.R.id.text2).text = "${entry.mb} MB · ${entry.packageName}"
            return view
        }
    }
}
