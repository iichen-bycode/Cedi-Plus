package com.creditcedi.credit

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class PayoutFailedDashedDividerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE3E3E3.toInt()
        style = Paint.Style.STROKE
        strokeWidth = density
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val halfStroke = dividerPaint.strokeWidth / 2f
        val y = height / 2f
        canvas.drawLine(halfStroke, y, width - halfStroke, y, dividerPaint)
    }
}
