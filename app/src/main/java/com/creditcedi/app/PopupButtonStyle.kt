package com.creditcedi.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.widget.Button
import com.creditcedi.core.R as CoreR

/** Applies the app's bordered primary/secondary treatments to platform dialog actions. */
internal fun AlertDialog.stylePopupButtonsOnShow() {
    setOnShowListener {
        getButton(AlertDialog.BUTTON_POSITIVE)?.applyPopupButtonStyle(CoreR.drawable.surface_768dd66f882c)
        getButton(AlertDialog.BUTTON_NEGATIVE)?.applyPopupButtonStyle(CoreR.drawable.surface_b005cd41c85e)
        getButton(AlertDialog.BUTTON_NEUTRAL)?.applyPopupButtonStyle(CoreR.drawable.surface_b005cd41c85e)
    }
}

internal fun AlertDialog.showWithPopupButtonStyle() {
    stylePopupButtonsOnShow()
    show()
}

/** Call from an existing OnShowListener when the dialog also has custom behavior. */
internal fun AlertDialog.applyPopupButtonStyle() {
    getButton(AlertDialog.BUTTON_POSITIVE)?.applyPopupButtonStyle(CoreR.drawable.surface_768dd66f882c)
    getButton(AlertDialog.BUTTON_NEGATIVE)?.applyPopupButtonStyle(CoreR.drawable.surface_b005cd41c85e)
    getButton(AlertDialog.BUTTON_NEUTRAL)?.applyPopupButtonStyle(CoreR.drawable.surface_b005cd41c85e)
}

private fun Button.applyPopupButtonStyle(backgroundRes: Int) {
    val density = resources.displayMetrics.density
    fun dp(value: Int) = (value * density + 0.5f).toInt()
    val drawable = context.getDrawable(backgroundRes) ?: return
    background = InsetDrawable(drawable, 0, dp(4), 0, dp(4))
    setTextColor(Color.rgb(18, 13, 1))
    minHeight = dp(48)
    minimumWidth = dp(104)
    setPadding(dp(12), 0, dp(12), 0)
}
