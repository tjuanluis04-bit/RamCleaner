package com.example.ramcleaner

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WhitelistActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var apps: List<ApplicationInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whitelist)

        listView = findViewById(R.id.whitelistListView)
        val saveButton = findViewById<Button>(R.id.saveWhitelistButton)

        val pm = packageManager
        apps = pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val checkedPackages = PrefsHelper.getWhitelist(this)

        val labels = apps.map { pm.getApplicationLabel(it).toString() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, labels)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        apps.forEachIndexed { index, appInfo ->
            if (checkedPackages.contains(appInfo.packageName)) {
                listView.setItemChecked(index, true)
            }
        }

        saveButton.setOnClickListener {
            val selected = mutableSetOf<String>()
            for (i in apps.indices) {
                if (listView.isItemChecked(i)) {
                    selected.add(apps[i].packageName)
                }
            }
            PrefsHelper.setWhitelist(this, selected)
            Toast.makeText(this, "Lista blanca guardada (${selected.size} apps)", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
