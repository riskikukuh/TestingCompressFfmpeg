package com.rikkuu.testingcompressffmpeg

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

object DialogUtil {

    fun createProgressDialog(context: Context, text: String ) : AlertDialog {
        val builder = AlertDialog.Builder(context)
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater?
        inflater?.let {
            val dialogView = inflater.inflate(R.layout.progress_dialog_layout, null)
            builder.setView(dialogView)
            val textView = dialogView.findViewById<TextView>(R.id.progressDialogText)
            if (textView != null) {
                textView.text = text
            }
        }
        builder.setCancelable(false)
        return builder.create()
    }

}