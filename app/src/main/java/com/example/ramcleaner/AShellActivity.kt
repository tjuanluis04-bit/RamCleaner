package com.example.ramcleaner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AShellActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ashell)

        val textCommand = findViewById<TextView>(R.id.textCommand)
        textCommand.text = AShellHelper.shortCommand(this)
            ?: "No se pudo crear el archivo. Usa \"Copiar script completo\"."

        findViewById<Button>(R.id.buttonCopyShort).setOnClickListener {
            val command = AShellHelper.shortCommand(this)
            if (command == null) {
                toast("No se pudo crear limpiar.sh; usa el script completo")
            } else {
                textCommand.text = command
                copy(command, "Comando corto copiado")
            }
        }

        findViewById<Button>(R.id.buttonCopyFull).setOnClickListener {
            copy(AShellHelper.fullScript(this), "Script completo copiado")
        }

        findViewById<Button>(R.id.buttonOpenAShell).setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage(ShellScripts.ASHELL_PACKAGE)
            if (intent == null) {
                toast("aShell no está instalado")
            } else {
                startActivity(intent)
            }
        }
    }

    private fun copy(text: String, message: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("RamCleaner", text))
        toast(message)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
