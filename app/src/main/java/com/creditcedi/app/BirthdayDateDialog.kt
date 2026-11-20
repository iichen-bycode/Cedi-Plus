package com.creditcedi.app

import android.app.Activity
import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.OverScroller
import android.widget.RelativeLayout
import android.widget.TextView
import com.creditcedi.core.R as CoreR
import com.creditcedi.identity.R as IdentityR
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal class BirthdayDateDialog(
    private val activity: Activity,
    initialValue: String,
    private val onConfirm: (String) -> Unit
) {
    private val initialDate = parseDate(initialValue) ?: DateParts(day = 1, month = 1, year = 2000)

    fun show() {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val content = activity.layoutInflater.inflate(IdentityR.layout.birthday, null, false) as RelativeLayout
        val constraints = ScreenConstraints(content)
        val pickerLayer = content.findViewById<View>(IdentityR.id.birthday_anchor_63)
        val insertionIndex = content.indexOfChild(pickerLayer).coerceAtLeast(0)

        val dayWheel = BirthdayWheelView(activity, activity.getString(CoreR.string.text_day))
        val monthWheel = BirthdayWheelView(activity, activity.getString(CoreR.string.text_month))
        val yearWheel = BirthdayWheelView(activity, activity.getString(CoreR.string.text_years))

        dayWheel.setRange(1, daysInMonth(initialDate.year, initialDate.month), initialDate.day, notify = false)
        monthWheel.setRange(1, 12, initialDate.month, notify = false)
        yearWheel.setRange(1, 9999, initialDate.year, notify = false)

        monthWheel.onValueChanged = { updateDayRange(dayWheel, monthWheel.value, yearWheel.value) }
        yearWheel.onValueChanged = { updateDayRange(dayWheel, monthWheel.value, yearWheel.value) }

        constraints.add(dayWheel, 42f, 97f, 56f, 180f)
        constraints.add(monthWheel, 145f, 97f, 84f, 180f)
        constraints.add(yearWheel, 267f, 97f, 76f, 180f)
        content.removeView(dayWheel)
        content.removeView(monthWheel)
        content.removeView(yearWheel)
        content.addView(dayWheel, insertionIndex)
        content.addView(monthWheel, insertionIndex + 1)
        content.addView(yearWheel, insertionIndex + 2)

        val cancel = actionButton(
            activity.getString(CoreR.string.text_cancel),
            CoreR.drawable.surface_b005cd41c85e
        ) { dialog.cancel() }
        val confirm = actionButton(
            activity.getString(CoreR.string.text_confirm),
            CoreR.drawable.surface_768dd66f882c
        ) {
            onConfirm(formatDate(dayWheel.value, monthWheel.value, yearWheel.value))
            dialog.dismiss()
        }
        constraints.add(cancel, 48f, 296f, 128f, 48f)
        constraints.add(confirm, 199f, 296f, 128f, 48f)

        content.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            constraints.updateWidth(view.width)
        }
        constraints.updateWidth(activity.resources.displayMetrics.widthPixels)

        dialog.setContentView(content)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.4f)
            attributes = attributes.apply { gravity = Gravity.CENTER }
        }
        dialog.show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        content.post { constraints.updateWidth(content.width) }
    }

    private fun updateDayRange(dayWheel: BirthdayWheelView, month: Int, year: Int) {
        dayWheel.setRange(1, daysInMonth(year, month), dayWheel.value)
    }

    private fun actionButton(text: String, backgroundResource: Int, onClick: () -> Unit) =
        TextView(activity).apply {
            this.text = text
            gravity = Gravity.CENTER
            includeFontPadding = false
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(18, 13, 1))
            setBackgroundResource(backgroundResource)
            minHeight = dp(48)
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            contentDescription = text
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    private data class DateParts(val day: Int, val month: Int, val year: Int)

    private fun parseDate(value: String): DateParts? {
        val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.UK).apply { isLenient = false }
        val position = ParsePosition(0)
        val date = formatter.parse(value, position) ?: return null
        if (position.index != value.length) return null
        val calendar = GregorianCalendar().apply { time = date }
        return DateParts(
            day = calendar.get(Calendar.DAY_OF_MONTH),
            month = calendar.get(Calendar.MONTH) + 1,
            year = calendar.get(Calendar.YEAR)
        )
    }

    private fun formatDate(day: Int, month: Int, year: Int) =
        String.format(Locale.UK, "%02d-%02d-%04d", day, month, year)

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private fun isLeapYear(year: Int) = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
}

private class BirthdayWheelView(
    context: Activity,
    private val label: String
) : View(context) {
    private val scroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var minimum = 1
    private var maximum = 1
    private var selectedIndex = 0
    private var scrollPosition = 0f
    private var lastTouchY = 0f
    private var downTouchY = 0f
    private var dragging = false
    private var snapAfterFling = false
    private var velocityTracker: VelocityTracker? = null
    private var lastAnnouncedValue = 1
    private val scrollAnimation = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                updateScrollPosition(scroller.currY.toFloat())
                postOnAnimation(this)
            } else if (snapAfterFling) {
                snapAfterFling = false
                snapToSelectedValue()
            }
        }
    }

    var onValueChanged: ((Int) -> Unit)? = null

    val value: Int
        get() = minimum + selectedIndex

    init {
        isFocusable = true
        isFocusableInTouchMode = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setRange(minimum: Int, maximum: Int, value: Int, notify: Boolean = true) {
        val previous = this.value
        this.minimum = minimum
        this.maximum = maximum.coerceAtLeast(minimum)
        val nextValue = value.coerceIn(this.minimum, this.maximum)
        selectedIndex = nextValue - this.minimum
        scrollPosition = selectedIndex * rowHeight()
        lastAnnouncedValue = nextValue
        removeCallbacks(scrollAnimation)
        scroller.forceFinished(true)
        snapAfterFling = false
        invalidate()
        updateAccessibilityDescription()
        if (notify && previous != nextValue) onValueChanged?.invoke(nextValue)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        scrollPosition = selectedIndex * rowHeight()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rowHeight = rowHeight()
        if (rowHeight <= 0f || height <= 0) return

        val rawIndex = scrollPosition / rowHeight
        val centerIndex = rawIndex.roundToInt().coerceIn(0, maximum - minimum)
        val offset = scrollPosition - centerIndex * rowHeight
        val centerY = height / 2f
        val scale = height / (180f * resources.displayMetrics.density)
        val normalSize = 14f * resources.displayMetrics.density * resources.configuration.fontScale * scale

        canvas.save()
        canvas.clipRect(0, 0, width, height)
        for (rowOffset in -3..3) {
            val index = centerIndex + rowOffset
            if (index !in 0..(maximum - minimum)) continue
            val y = centerY + rowOffset * rowHeight - offset
            val itemValue = minimum + index
            paint.typeface = if (rowOffset == 0) {
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
            paint.textSize = normalSize
            paint.color = Color.rgb(18, 13, 1)
            paint.alpha = when (abs(rowOffset)) {
                0, 1 -> 255
                2 -> 180
                else -> 100
            }
            val baseline = y - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(formatValue(itemValue), width / 2f, baseline, paint)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(scrollAnimation)
                scroller.forceFinished(true)
                snapAfterFling = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                lastTouchY = event.y
                downTouchY = event.y
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (abs(event.y - downTouchY) > touchSlop) dragging = true
                val delta = lastTouchY - event.y
                lastTouchY = event.y
                if (dragging) updateScrollPosition(scrollPosition + delta)
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.apply {
                    addMovement(event)
                    computeCurrentVelocity(1000)
                    val velocity = -yVelocity
                    if (dragging && abs(velocity) >= minimumFlingVelocity) {
                        scroller.fling(
                            0,
                            scrollPosition.roundToInt(),
                            0,
                            velocity.roundToInt(),
                            0,
                            0,
                            0,
                            maxScrollPosition().roundToInt()
                        )
                        snapAfterFling = true
                        postOnAnimation(scrollAnimation)
                    } else {
                        snapToSelectedValue()
                    }
                    recycle()
                }
                velocityTracker = null
                performClick()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                snapToSelectedValue()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.NumberPicker"
        info.text = formatValue(value)
        info.contentDescription = "$label, ${formatValue(value)}"
        info.isScrollable = minimum < maximum
        if (value > minimum) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (value < maximum) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> if (value < maximum) {
                updateValue(value + 1)
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
                return true
            }

            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> if (value > minimum) {
                updateValue(value - 1)
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private fun updateScrollPosition(position: Float) {
        scrollPosition = position.coerceIn(0f, maxScrollPosition())
        val nextIndex = (scrollPosition / rowHeight()).roundToInt().coerceIn(0, maximum - minimum)
        if (nextIndex != selectedIndex) {
            selectedIndex = nextIndex
            val nextValue = value
            updateAccessibilityDescription()
            onValueChanged?.invoke(nextValue)
            if (nextValue != lastAnnouncedValue) {
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
                lastAnnouncedValue = nextValue
            }
        }
        invalidate()
    }

    private fun updateValue(value: Int) {
        selectedIndex = (value - minimum).coerceIn(0, maximum - minimum)
        scrollPosition = selectedIndex * rowHeight()
        lastAnnouncedValue = this.value
        updateAccessibilityDescription()
        onValueChanged?.invoke(this.value)
        invalidate()
    }

    private fun snapToSelectedValue() {
        val target = selectedIndex * rowHeight()
        val delta = target.roundToInt() - scrollPosition.roundToInt()
        if (delta == 0) {
            scrollPosition = target
            invalidate()
            return
        }
        scroller.startScroll(0, scrollPosition.roundToInt(), 0, delta, 160)
        postOnAnimation(scrollAnimation)
    }

    private fun updateAccessibilityDescription() {
        contentDescription = "$label, ${formatValue(value)}"
        if (isAccessibilityFocused) sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    private fun maxScrollPosition() = (maximum - minimum) * rowHeight()

    private fun rowHeight() = if (height > 0) height / 5f else 36f * resources.displayMetrics.density

    private fun formatValue(value: Int): String = when (label) {
        context.getString(CoreR.string.text_day), context.getString(CoreR.string.text_month) ->
            String.format(Locale.UK, "%02d", value)
        else -> value.toString()
    }
}
