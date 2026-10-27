package com.example.screenrecorder

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class TouchHighlightService : AccessibilityService() {

    private var wm: WindowManager? = null

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val t = event.eventType
        if (t != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            t != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) return
        val node = event.source ?: return
        val r = Rect()
        node.getBoundsInScreen(r)
        flash(r)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun flash(r: Rect) {
        if (r.width() <= 0 || r.height() <= 0) return
        val v = View(this)
        v.background = GradientDrawable().apply {
            setColor(Color.argb(120, 255, 235, 0))
            setStroke(dp(4), Color.parseColor("#FF1744"))
            cornerRadius = dp(12).toFloat()
        }
        val lp = WindowManager.LayoutParams(
            r.width(), r.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = r.left
            y = r.top
        }
        try {
            wm?.addView(v, lp)
        } catch (e: Exception) {
            return
        }
        v.animate().alpha(0f).setDuration(700).withEndAction {
            try { wm?.removeView(v) } catch (e: Exception) { }
        }.start()
    }

    override fun onInterrupt() { }
}
