package com.creditcedi.credit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

internal class DeclinedSummaryCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x12, 0x0d, 0x01)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaleX = width / 343f
        val scaleY = height / 232f
        val path = Path().apply {
            moveTo(17f * scaleX, 1f * scaleY)
            lineTo(326f * scaleX, 1f * scaleY)
            cubicTo(335f * scaleX, 1f * scaleY, 342f * scaleX, 8f * scaleY, 342f * scaleX, 17f * scaleY)
            lineTo(342f * scaleX, 82f * scaleY)
            cubicTo(324f * scaleX, 82f * scaleY, 324f * scaleX, 112f * scaleY, 342f * scaleX, 112f * scaleY)
            lineTo(342f * scaleX, 215f * scaleY)
            cubicTo(342f * scaleX, 224f * scaleY, 335f * scaleX, 231f * scaleY, 326f * scaleX, 231f * scaleY)
            lineTo(17f * scaleX, 231f * scaleY)
            cubicTo(8f * scaleX, 231f * scaleY, 1f * scaleX, 224f * scaleY, 1f * scaleX, 215f * scaleY)
            lineTo(1f * scaleX, 112f * scaleY)
            cubicTo(19f * scaleX, 112f * scaleY, 19f * scaleX, 82f * scaleY, 1f * scaleX, 82f * scaleY)
            lineTo(1f * scaleX, 17f * scaleY)
            cubicTo(1f * scaleX, 8f * scaleY, 8f * scaleX, 1f * scaleY, 17f * scaleX, 1f * scaleY)
            close()
        }

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, outlinePaint)
    }
}
