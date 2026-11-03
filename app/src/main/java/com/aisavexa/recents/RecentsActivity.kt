package com.aisavexa.recents

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RecentsActivity : AppCompatActivity() {

    private var closing = false

    // Phase 1: dummy cards. Real recent apps list Phase 2 mein UsageStatsManager se aayegi.
    private val dummyApps = listOf("Chrome", "WhatsApp", "YouTube", "Gallery", "Settings", "Maps")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recents)

        val root = findViewById<FrameLayout>(R.id.root)
        val container = findViewById<LinearLayout>(R.id.cardContainer)

        dummyApps.forEach { name ->
            val card = TextView(this).apply {
                text = name
                textSize = 20f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(0xFF2A2A2A.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(220), dp(360)).apply {
                    marginEnd = dp(16)
                }
                // Card par tap card ko consume karega, blank jagah wala close nahi chalega.
                isClickable = true
            }
            container.addView(card)
        }

        // Blank jagah par tap = close with animation
        root.setOnClickListener { closeWithAnimation() }

        root.alpha = 0f
        root.translationY = dp(60).toFloat()
        root.animate().alpha(1f).translationY(0f).setDuration(260).start()
    }

    private fun closeWithAnimation() {
        if (closing) return
        closing = true
        val root = findViewById<View>(R.id.root)
        root.animate()
            .alpha(0f)
            .translationY(dp(60).toFloat())
            .setDuration(220)
            .withEndAction {
                finish()
                overridePendingTransition(0, 0)
            }
            .start()
    }

    override fun onBackPressed() {
        closeWithAnimation()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
