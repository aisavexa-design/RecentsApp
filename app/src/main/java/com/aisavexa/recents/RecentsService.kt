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
    private val excluded = mutableSetOf<String>()

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
        computeExcluded()
        addTrigger()
    }

    // Launcher, keyboard, system UI aur apni app ko recent list mein record nahi karna
    private fun computeExcluded() {
        val pm = packageManager
        excluded.clear()
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName?.let { excluded.add(it) }
        pm.queryIntentServices(Intent("android.view.InputMethod"), 0)
            .forEach { excluded.add(it.serviceInfo.packageName) }
        excluded.add(packageName)
        excluded.add("com.android.systemui")
        excluded.add("android")
    }

    // Jab bhi koi app screen par aati hai, uska naam Prefs mein turant save hota hai
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in excluded) return
        Prefs.addRecentPkg(this, pkg)
    }

    override fun onInterrupt() {}

    private fun addTrigger() {
        if (trigger != null) return

        val density = resources.displayMetrics.density
        val w = (Prefs.getW(this) * density).toInt()
        val h = (Prefs.getH(this) * density).toInt()
        val x = (Prefs.getX(this) * density).toInt()
        val y = (Prefs.getY(this) * density).toInt()
        val calibrating = Prefs.isCalibrating(this)

        val view = View(this).apply {
            setOnClickListener { openRecents() }
            if (calibrating) setBackgroundColor(0xAAFF3B30.toInt())
        }

        val params = WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            this.x = x
            this.y = y
        }
        windowManager?.addView(view, params)
        trigger = view
    }

    private fun removeTrigger() {
        trigger?.let { runCatching { windowManager?.removeView(it) } }
        trigger = null
    }

    private fun openRecents() {
        startActivity(
            Intent(this, RecentsActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(reloadReceiver) }
        removeTrigger()
        super.onDestroy()
    }
}
