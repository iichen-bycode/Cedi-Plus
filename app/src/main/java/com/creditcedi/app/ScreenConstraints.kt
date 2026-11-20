package com.creditcedi.app

import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.roundToInt

/** Scales design spacing while native containers resolve their own layout rules. */
internal class ScreenConstraints(
    val layout: ViewGroup,
    private val referenceWidth: Float,
    val referenceHeight: Float
) {
    constructor(layout: ViewGroup) : this(
        layout,
        (layout.tag as String).substringBefore(',').toFloat(),
        (layout.tag as String).substringAfter(',').toFloat()
    )

    private data class Spacing(
        val left: Float,
        var right: Float,
        var top: Float,
        val textSize: Float?,
        val width: Int,
        val height: Int,
        var anchorTop: Float = 0f
    )

    private val density = layout.resources.displayMetrics.density
    private val spacing = (0 until layout.childCount).associate { index ->
        val child = layout.getChildAt(index)
        val params = child.layoutParams as ViewGroup.MarginLayoutParams
        child to newSpacing(child, params.leftMargin / density, params.rightMargin / density, params.topMargin / density)
    }.toMutableMap()

    init {
        val resolved = mutableMapOf<View, Float>()
        fun resolvedTop(child: View): Float = resolved.getOrPut(child) {
            val params = child.layoutParams
            val (anchorId, below) = when (params) {
                is ConstraintLayout.LayoutParams -> if (params.topToBottom != -1) {
                    params.topToBottom to true
                } else params.topToTop to false
                is RelativeLayout.LayoutParams -> if (params.getRule(RelativeLayout.BELOW) != 0) {
                    params.getRule(RelativeLayout.BELOW) to true
                } else params.getRule(RelativeLayout.ALIGN_TOP) to false
                else -> 0 to false
            }
            val anchor = if (anchorId > 0) layout.findViewById<View>(anchorId) else null
            val position = spacing.getValue(child)
            if (anchor != null && anchor in spacing) {
                position.anchorTop = resolvedTop(anchor) + if (below) height(anchor) else 0f
            }
            position.top += position.anchorTop
            position.top
        }
        spacing.keys.forEach { resolvedTop(it) }
    }

    private var widthPx = 0
    private var height = referenceHeight

    fun top(child: View): Float = spacing.getValue(child).top
    fun left(child: View): Float = spacing.getValue(child).left
    fun width(child: View): Float {
        val position = spacing.getValue(child)
        return if (position.width > 0) position.width / density else referenceWidth - position.left - position.right
    }

    fun height(child: View): Float {
        val position = spacing.getValue(child)
        if (position.height > 0) return position.height / density
        val params = child.layoutParams as? ConstraintLayout.LayoutParams ?: return 0f
        val ratio = params.dimensionRatio?.removePrefix("H,")?.split(':')
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 2 && it[0] > 0f }
            ?: return 0f
        return width(child) * ratio[1] / ratio[0]
    }

    fun moveTo(child: View, top: Float) {
        val position = spacing.getValue(child)
        position.top = top
        updateMargins(child, position)
    }

    fun setWidth(child: View, width: Float) {
        val position = spacing.getValue(child)
        val params = child.layoutParams as? ConstraintLayout.LayoutParams
        val ratio = params?.dimensionRatio
        if (ratio?.startsWith("H,") == true) {
            params?.dimensionRatio = "H,$width:${ratio.substringAfter(':')}"
        }
        position.right = referenceWidth - position.left - width
        updateMargins(child, position)
    }

    fun setContentHeight(height: Float) {
        this.height = height
        updateMinimumHeight()
    }

    fun add(child: View, left: Float, top: Float, width: Float, height: Float) {
        val params = if (layout is RelativeLayout) RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (height * density).roundToInt()
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
        } else ConstraintLayout.LayoutParams(0, 0).apply {
            leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            dimensionRatio = "H,$width:$height"
        }
        layout.addView(child, params)
        val position = newSpacing(child, left, referenceWidth - left - width, top)
        spacing[child] = position
        apply(child, position)
    }

    fun detachFooter(footerHeight: Int): ScreenConstraints {
        val panel = ConstraintLayout(layout.context)
        panel.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        val footer = ScreenConstraints(panel, referenceWidth, footerHeight.toFloat())
        val boundary = referenceHeight - footerHeight
        val children = (0 until layout.childCount).map(layout::getChildAt)
        for (child in children) {
            val position = spacing.getValue(child)
            if (position.top < boundary - 0.5f / density) continue
            val params = child.layoutParams
            layout.removeView(child)
            spacing.remove(child)
            position.top -= boundary
            if (position.anchorTop != 0f) position.anchorTop -= boundary
            footer.spacing[child] = position
            panel.addView(child, params)
        }
        setContentHeight(height - footerHeight)
        return footer
    }

    fun updateWidth(width: Int) {
        if (width <= 0 || width == widthPx) return
        widthPx = width
        updateMinimumHeight()
        spacing.forEach(::apply)
    }

    fun widthFittingHeight(width: Int, height: Int): Int =
        minOf(width, (height * referenceWidth / referenceHeight).toInt())

    private fun scaled(value: Float): Int =
        (value * if (widthPx > 0) widthPx / referenceWidth else density).roundToInt()

    private fun newSpacing(child: View, left: Float, right: Float, top: Float): Spacing =
        Spacing(left, right, top, (child as? TextView)?.textSize, child.layoutParams.width, child.layoutParams.height)

    private fun updateMinimumHeight() {
        if (layout is ConstraintLayout) layout.minHeight = scaled(height)
        else layout.minimumHeight = scaled(height)
    }

    private fun updateMargins(child: View, position: Spacing) {
        val params = child.layoutParams as ViewGroup.MarginLayoutParams
        params.leftMargin = scaled(position.left)
        params.rightMargin = scaled(position.right)
        params.topMargin = scaled(position.top - position.anchorTop)
        if (params.width > 0 && position.width > 0) params.width = scaled(position.width / density)
        if (params.height > 0 && position.height > 0) params.height = scaled(position.height / density)
        child.layoutParams = params
    }

    private fun apply(child: View, position: Spacing) {
        updateMargins(child, position)
        if (child !is TextView || position.textSize == null) return
        val size = position.textSize * if (widthPx > 0) widthPx / (referenceWidth * density) else 1f
        child.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        if (Build.VERSION.SDK_INT >= 26 && child.javaClass == TextView::class.java &&
            child.layoutParams.height >= 0 && child.text.isNotEmpty()) {
            val maximum = size.roundToInt().coerceAtLeast(2)
            val minimum = (size * 0.8f).roundToInt().coerceIn(1, maximum - 1)
            child.setAutoSizeTextTypeUniformWithConfiguration(minimum, maximum, 1, TypedValue.COMPLEX_UNIT_PX)
        }
    }
}
