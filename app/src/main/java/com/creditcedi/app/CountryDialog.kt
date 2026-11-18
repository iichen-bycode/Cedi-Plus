package com.creditcedi.app

import android.app.Dialog
import android.app.DialogFragment
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RadioButton
import com.creditcedi.access.R as AccessR
import com.creditcedi.core.R as CoreR

class CountryDialog : DialogFragment() {
    private var selected = "Ghana"

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val host = activity as MainActivity
        selected = savedInstanceState?.getString("selection") ?: host.selectedCountry
        val dialog = Dialog(host)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val backdrop = FrameLayout(host)
        backdrop.setBackgroundColor(0x66120d01)
        val panel = host.layoutInflater.inflate(AccessR.layout.country, backdrop, false)
        backdrop.addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        dialog.setContentView(backdrop)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(MATCH_PARENT, MATCH_PARENT)
        }

        val ghana = panel.findViewById<RadioButton>(AccessR.id.ghana)
        val tanzania = panel.findViewById<RadioButton>(AccessR.id.tanzania)
        fun select(country: String) {
            selected = country
            ghana.isChecked = country == "Ghana"
            tanzania.isChecked = country == "Tanzania"
            fun indicator(checked: Boolean) = if (checked) CoreR.drawable.art_d00d2983e176 else CoreR.drawable.art_64ff3ec4ca06
            panel.findViewById<ImageView>(AccessR.id.ghana_indicator).setImageResource(indicator(ghana.isChecked))
            panel.findViewById<ImageView>(AccessR.id.tanzania_indicator).setImageResource(indicator(tanzania.isChecked))
        }
        select(selected)
        ghana.setOnClickListener { select("Ghana") }
        tanzania.setOnClickListener { select("Tanzania") }
        panel.findViewById<View>(AccessR.id.country_submit).setOnClickListener {
            host.applyCountry(selected)
            dismiss()
        }
        panel.findViewById<View>(AccessR.id.country_cancel).setOnClickListener { dismiss() }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selection", selected)
    }
}
