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
import androidx.appcompat.app.AppCompatActivity

class WhitelistActivity : AppCompatActivity() {

    private data class AppEntry(
        val packageName: String,
        val label: String,
        var checked: Boolean
    )

    private lateinit var allApps: List<AppEntry>
    private var filteredApps: List<AppEntry> = emptyList()
    private lateinit var listView: ListView
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whitelist)

        listView = findViewById(R.id.listApps)
        val editSearch = findViewById<EditText>(R.id.editSearch)

        val whitelist = PrefsHelper.getWhitelist(this)
        val pm = packageManager
        allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName }
            .map { info: ApplicationInfo ->
                AppEntry(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    checked = whitelist.contains(info.packageName)
                )
            }
            .sortedBy { it.label.lowercase() }

        filteredApps = allApps
        adapter = Adapter()
        listView.adapter = adapter

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        filteredApps = if (q.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
        adapter.notifyDataSetChanged()
    }

    private fun persist() {
        PrefsHelper.setWhitelist(this, allApps.filter { it.checked }.map { it.packageName }.toSet())
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount(): Int = filteredApps.size
        override fun getItem(position: Int): Any = filteredApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView as? android.widget.CheckedTextView) ?: LayoutInflater.from(this@WhitelistActivity)
                .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false) as android.widget.CheckedTextView
            val entry = filteredApps[position]
            view.text = "${entry.label}\n${entry.packageName}"
            view.textSize = 14f
            view.isChecked = entry.checked

            view.setOnClickListener {
                entry.checked = !entry.checked
                persist()
                notifyDataSetChanged()
            }

            return view
        }
    }
}
