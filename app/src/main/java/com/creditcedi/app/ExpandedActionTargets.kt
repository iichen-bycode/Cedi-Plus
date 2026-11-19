package com.creditcedi.app

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.roundToInt

private val ACTION_LABELS = setOf(
    "Back", "Next", "Choose", "Submit", "Confirm", "Cancel", "Confirm Agreement", "Reject and Exit",
    "Home", "Start Application Now", "My Orders", "Profile Center", "Please sign in", "+233", "Get OTP",
    "Sign In", "Privacy Policy", "About Us", "Sign Out", "Edit Now", "Receiving Account", "Feedback",
    "Write feedback", "Choose PDF", "Choose image", "Local photo album", "Photograph", "Capture selfie",
    "Upload a clear selfie", "Capture document", "Reshoot", "Submit application", "Go Apply",
    "Begin Application", "Apply Now", "Withdraw Now", "Set Up", "Repay Extension", "Repay Now", "View Order"
)

/** Routes taps on an action's visible backing surface to its existing text control. */
internal fun ViewGroup.expandActionHitAreas() {
    post {
        if (childCount == 0) return@post

        val delegate = ActionTouchDelegate(this)
        for (targetIndex in 0 until childCount) {
            val target = getChildAt(targetIndex) as? TextView ?: continue
            val label = target.tag as? String ?: continue
            if (!label.isActionLabel()) continue
            if (target.width <= 0 || target.height <= 0 || !target.isShown) continue

            val targetBounds = Rect(target.left, target.top, target.right, target.bottom)
            val backing = (0 until targetIndex)
                .map(::getChildAt)
                .filter { candidate ->
                    candidate !is TextView && candidate !is ImageView &&
                        candidate !is ViewGroup && !candidate.isClickable &&
                        candidate.background != null && candidate.isShown &&
                        candidate.width > 0 && candidate.height > 0 &&
                        candidate.left <= targetBounds.left && candidate.top <= targetBounds.top &&
                        candidate.right >= targetBounds.right && candidate.bottom >= targetBounds.bottom
                }
                .minByOrNull { it.width.toLong() * it.height }

            val hitBounds = backing?.let { Rect(it.left, it.top, it.right, it.bottom) } ?: targetBounds
            hitBounds.ensureMinimumTouchSize(
                width = (48f * resources.displayMetrics.density).roundToInt(),
                height = (48f * resources.displayMetrics.density).roundToInt(),
                parentWidth = width,
                parentHeight = height
            )
            if (hitBounds != targetBounds) delegate.add(hitBounds, target)
        }

        if (delegate.hasTargets()) touchDelegate = delegate
    }
}

private fun String.isActionLabel(): Boolean = when {
    this in ACTION_LABELS -> true
    startsWith("Order ") || startsWith("Please submit feedback", ignoreCase = true) ||
        startsWith("Please describe your problem", ignoreCase = true) ||
        contains("Wallet Account", ignoreCase = true) || contains("feedback list", ignoreCase = true) ||
        endsWith(" photo", ignoreCase = true) || matches(Regex("[0-9]+ Days")) -> true
    else -> false
}

private fun Rect.ensureMinimumTouchSize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
    if (this.width() < width) {
        val extra = width - this.width()
        left -= extra / 2
        right += extra - extra / 2
    }
    if (this.height() < height) {
        val extra = height - this.height()
        top -= extra / 2
        bottom += extra - extra / 2
    }

    if (left < 0) right = (right - left).coerceAtMost(parentWidth).also { left = 0 }
    if (top < 0) bottom = (bottom - top).coerceAtMost(parentHeight).also { top = 0 }
    if (right > parentWidth) left = (left - (right - parentWidth)).coerceAtLeast(0).also { right = parentWidth }
    if (bottom > parentHeight) top = (top - (bottom - parentHeight)).coerceAtLeast(0).also { bottom = parentHeight }
}

private class ActionTouchDelegate(host: View) : TouchDelegate(Rect(), host) {
    private data class Target(val bounds: Rect, val delegate: TouchDelegate)

    private val targets = mutableListOf<Target>()
    private var activeDelegate: TouchDelegate? = null

    fun add(bounds: Rect, target: View) {
        targets += Target(Rect(bounds), TouchDelegate(Rect(bounds), target))
    }

    fun hasTargets(): Boolean = targets.isNotEmpty()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            activeDelegate = targets.asReversed()
                .firstOrNull { it.bounds.contains(event.x.toInt(), event.y.toInt()) }
                ?.delegate
        }

        val delegate = activeDelegate ?: return false
        val handled = delegate.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            activeDelegate = null
        }
        return handled
    }
}
