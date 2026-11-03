package com.aisavexa.recents

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class RecentsService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var trigger: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addTrigger()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun addTrigger() {
        val metrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        val navHeight = (48 * metrics.density).toInt()

        val view = View(this).apply {
            setOnClickListener { openRecents() }
        }
        val params = WindowManager.LayoutParams(
            metrics.widthPixels / 3,
            navHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        windowManager?.addView(view, params)
        trigger = view
    }

    private fun openRecents() {
        startActivity(Intent(this, RecentsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onDestroy() {
        trigger?.let { runCatching { windowManager?.removeView(it) } }
        super.onDestroy()
    }
}
