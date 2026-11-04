package com.aisavexa.recents

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class RecentsService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var trigger: View? = null

    // Settings screen se reload broadcast aane par overlay naye size/position se banega
    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            removeTrigger()
            addTrigger()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ContextCompat.registerReceiver(
            this,
            reloadReceiver,
            IntentFilter(Prefs.ACTION_RELOAD),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        addTrigger()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun addTrigger() {
        if (trigger != null) return

        val w = dp(Prefs.getInt(this, Prefs.KEY_W, Prefs.DEF_W))
        val h = dp(Prefs.getInt(this, Prefs.KEY_H, Prefs.DEF_H))
        val offX = dp(Prefs.getInt(this, Prefs.KEY_X, Prefs.DEF_X))
        val offY = dp(Prefs.getInt(this, Prefs.KEY_Y, Prefs.DEF_Y))

        val view = View(this).apply {
            setOnClickListener { openRecents() }
        }
        val params = WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = offX
            y = offY
        }
        windowManager?.addView(view, params)
        trigger = view
    }

    private fun removeTrigger() {
        trigger?.let { runCatching { windowManager?.removeView(it) } }
        trigger = null
    }

    private fun openRecents() {
        startActivity(Intent(this, RecentsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        runCatching { unregisterReceiver(reloadReceiver) }
        removeTrigger()
        super.onDestroy()
    }
}
