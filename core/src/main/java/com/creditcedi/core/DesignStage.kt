package com.creditcedi.core

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.roundToInt

/** Layout bounds are measured in the original artboard coordinates, at the available width. */
class DesignStage(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {
    private var canvas = (tag as? String ?: "375,0").split(",").map(String::toFloat)
    val artboardHeight get() = canvas[1]
    private val textSizes = mutableMapOf<TextView, Float>()
    private var ratio = 1f

    override fun generateLayoutParams(attrs: AttributeSet) = MarginLayoutParams(context, attrs)
    override fun generateDefaultLayoutParams() = MarginLayoutParams(0, 0)
    override fun generateLayoutParams(source: LayoutParams) = MarginLayoutParams(source)

    /** Artboards include fixed actions beneath the scrollable content. */
    fun detachFooter(height: Int): DesignStage {
        val footer = DesignStage(context, null)
        val boundary = canvas[1] - height
        val top = (boundary * resources.displayMetrics.density).roundToInt()
        footer.canvas = listOf(canvas[0], height.toFloat())
        canvas = listOf(canvas[0], boundary)
        val footerViews = (0 until childCount).map(::getChildAt).filter {
            (it.layoutParams as MarginLayoutParams).topMargin >= top
        }
        for (view in footerViews) {
            val params = view.layoutParams as MarginLayoutParams
            removeView(view)
            params.topMargin -= top
            footer.addView(view, params)
        }
        return footer
    }

    fun setArtboardHeight(height: Float) {
        canvas = listOf(canvas[0], height)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        ratio = width / (canvas[0] * resources.displayMetrics.density)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams
            if (child is TextView) {
                val size = textSizes.getOrPut(child) { child.textSize }
                child.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * ratio)
            }
            child.measure(
                MeasureSpec.makeMeasureSpec(scale(params.width), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(scale(params.height), MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(width, (width * canvas[1] / canvas[0]).roundToInt())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as MarginLayoutParams
            val x = scale(params.leftMargin)
            val y = scale(params.topMargin)
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }

    private fun scale(value: Int) = (value * ratio).roundToInt()
}
