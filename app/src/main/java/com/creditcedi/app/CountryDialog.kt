package com.creditcedi.app

import android.app.Dialog
import android.app.DialogFragment
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
    private var hostNavigationBarColor: Int? = null
    private var hostNavigationBarContrast: Boolean? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val host = activity as MainActivity
        selected = savedInstanceState?.getString("selection") ?: host.selectedCountry
        hostNavigationBarColor = host.window.navigationBarColor
        if (Build.VERSION.SDK_INT >= 29) {
            hostNavigationBarContrast = host.window.isNavigationBarContrastEnforced
            host.window.isNavigationBarContrastEnforced = false
        }
        host.window.navigationBarColor = Color.rgb(160, 158, 153)
        val dialog = Dialog(host)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val backdrop = FrameLayout(host)
        val panel = host.layoutInflater.inflate(AccessR.layout.country, backdrop, false) as ViewGroup
        val constraints = ScreenConstraints(panel)
        constraints.updateWidth(host.resources.displayMetrics.widthPixels)
        backdrop.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            constraints.updateWidth(view.width)
        }
        backdrop.addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        dialog.setContentView(backdrop)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.4f }
            setLayout(MATCH_PARENT, MATCH_PARENT)
            navigationBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility = decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = false
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

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            navigationBarColor = Color.rgb(160, 158, 153)
            if (Build.VERSION.SDK_INT >= 26) {
                decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = false
        }
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)
        (activity as? MainActivity)?.window?.let { window ->
            hostNavigationBarColor?.let { window.navigationBarColor = it }
            if (Build.VERSION.SDK_INT >= 29) {
                hostNavigationBarContrast?.let { window.isNavigationBarContrastEnforced = it }
            }
        }
        hostNavigationBarColor = null
        hostNavigationBarContrast = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selection", selected)
    }
}
