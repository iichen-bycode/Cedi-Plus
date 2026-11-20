package com.creditcedi.app

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

class StatusBarBackground(context: Context, attrs: AttributeSet) : View(context, attrs) {
    var page: ViewGroup? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipRect(0, 0, width, height)
        page?.let { layout ->
            for (index in 0 until layout.childCount) {
                val child = layout.getChildAt(index)
                // Narrow artwork fragments can contain the design's sample status icons.
                if (child.visibility != VISIBLE || child.top >= 0 || child.bottom <= 0 ||
                    child.left > 1 || child.right < layout.width - 1) continue

                val layer = canvas.save()
                canvas.translate(child.left.toFloat(), height.toFloat())
                canvas.scale(1f, maxOf(1f, height / -child.top.toFloat()))
                canvas.translate(0f, child.top.toFloat())
                if (child.alpha < 1f) {
                    canvas.saveLayerAlpha(
                        0f, 0f, child.width.toFloat(), child.height.toFloat(),
                        (255 * child.alpha).roundToInt()
                    )
                }
                child.draw(canvas)
                canvas.restoreToCount(layer)
            }
        }
        canvas.restoreToCount(save)
    }
}
