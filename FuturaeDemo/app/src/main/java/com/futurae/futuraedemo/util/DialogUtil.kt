package com.futurae.futuraedemo.util

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import com.futurae.futuraedemo.R

fun Context.showInputDialog(hint : String, onValueProvided: (String) -> Unit) {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_entry, null, false)
    dialogView.findViewById<EditText>(R.id.edittext).setHint(hint)
    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .setPositiveButton("ok") { dialog, _ ->
            val input = dialogView.findViewById<AppCompatEditText>(R.id.edittext).text.toString()
                .replace(" ", "")

            onValueProvided(input)
            dialog.dismiss()
        }.create()
    dialog.show()
}