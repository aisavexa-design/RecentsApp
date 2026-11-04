package com.aisavexa.recents

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class RecentsActivity : AppCompatActivity() {

    private data class RecentApp(val pkg: String, val label: String, val icon: Drawable)

    private val white = 0xFFFFFFFF.toInt()
    private var closing = false
    private var opening = false

    private lateinit var root: View
    private lateinit var scroll: HorizontalScrollView
    private lateinit var container: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var closeAllButton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recents)

        root = findViewById(R.id.root)
        scroll = findViewById(R.id.cardScroll)
        container = findViewById(R.id.cardContainer)
        emptyText = findViewById(R.id.emptyText)
        closeAllButton = findViewById(R.id.closeAllButton)

        loadBackground()

        root.setOnClickListener { closeWithAnimation() }
        closeAllButton.setOnClickListener { closeAllCards() }

        val apps = loadRecentApps()
        if (apps == null) {
            showMessage("Usage access OFF hai. Yahan tap karke ON karo")
            emptyText.isClickable = true
            emptyText.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            closeAllButton.visibility = View.GONE
        } else if (apps.isEmpty()) {
            showMessage("Koi recent app nahi")
            closeAllButton.visibility = View.GONE
        } else {
            apps.forEach { addCard(it) }
        }

        scroll.setOnScrollChangeListener { _, _, _, _, _ -> applyPagerEffect() }
        scroll.post { applyPagerEffect() }

        root.alpha = 0f
        root.translationY = dp(60).toFloat()
        root.animate().alpha(1f).translationY(0f).setDuration(260).start()
    }

    private fun loadBackground() {
        val bg = findViewById<ImageView>(R.id.bgImage)
        val dim = findViewById<View>(R.id.bgDim)
        val file = Prefs.bgFile(this)
        if (!file.exists()) {
            bg.visibility = View.GONE
            dim.visibility = View.GONE
            return
        }
        val metrics = resources.displayMetrics
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= metrics.widthPixels &&
            bounds.outHeight / (sample * 2) >= metrics.heightPixels
        ) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        bg.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath, opts))
        bg.visibility = View.VISIBLE
        dim.visibility = View.VISIBLE
    }

    private fun showMessage(text: String) {
        emptyText.text = text
        emptyText.visibility = View.VISIBLE
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    // null = permission nahi hai, empty = koi app nahi
    private fun loadRecentApps(): List<RecentApp>? {
        if (!hasUsageAccess()) return null

        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, now - 3 * 24 * 3600_000L, now
        )
        val pm = packageManager
        val homePkg = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        )?.activityInfo?.packageName

        return stats
            .filter {
                it.packageName != packageName &&
                    it.packageName != homePkg &&
                    it.lastTimeUsed > 0 &&
                    pm.getLaunchIntentForPackage(it.packageName) != null
            }
            .sortedByDescending { it.lastTimeUsed }
            .take(10)
            .mapNotNull { s ->
                runCatching {
                    val info = pm.getApplicationInfo(s.packageName, 0)
                    RecentApp(
                        s.packageName,
                        pm.getApplicationLabel(info).toString(),
                        pm.getApplicationIcon(info)
                    )
                }.getOrNull()
            }
    }

    private fun addCard(app: RecentApp) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(0xFF2A2A2A.toInt())
                cornerRadius = dp(24).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(380)).apply {
                marginEnd = dp(16)
            }
            isClickable = true
        }
        card.addView(
            ImageView(this).apply { setImageDrawable(app.icon) },
            LinearLayout.LayoutParams(dp(72), dp(72))
        )
        card.addView(TextView(this).apply {
            text = app.label
            setTextColor(white)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })
        card.setOnTouchListener(CardTouch(card, app))
        container.addView(card)
    }

    // Upar swipe / tap = app masking ke saath khule
    // Left/right swipe = card recents se hat jaye
    private inner class CardTouch(private val card: View, private val app: RecentApp) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            if (opening) return true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    card.translationX = dx
                    card.translationY = if (dy < 0) dy else 0f
                    card.alpha = (1f - abs(dy) / dp(400).toFloat()).coerceIn(0.3f, 1f)
                }
                MotionEvent.ACTION_UP -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    when {
                        abs(dx) < dp(10) && abs(dy) < dp(10) -> openWithMask(card, app)
                        dy < -dp(30) && abs(dy) > abs(dx) -> openWithMask(card, app)
                        abs(dx) > dp(120) -> dismissSideways(card, if (dx < 0) -1 else 1)
                        else -> resetCard(card)
                    }
                }
                MotionEvent.ACTION_CANCEL -> resetCard(card)
            }
            return true
        }
    }

    private fun resetCard(card: View) {
        card.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(150).start()
    }

    // Masking effect: card phail kar fade hota hai, background bhi fade hota hai, phir app khulta hai
    private fun openWithMask(card: View, app: RecentApp) {
        if (opening) return
        opening = true
        val launch = packageManager.getLaunchIntentForPackage(app.pkg)

        card.animate()
            .scaleX(1.6f)
            .scaleY(1.6f)
            .alpha(0f)
            .setDuration(260)
            .start()

        for (i in 0 until container.childCount) {
            val other = container.getChildAt(i)
            if (other !== card) other.animate().alpha(0f).setDuration(200).start()
        }

        root.animate()
            .alpha(0f)
            .setDuration(260)
            .withEndAction {
                launch?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    startActivity(it)
                }
                finish()
                overridePendingTransition(0, 0)
            }
            .start()
    }

    private fun dismissSideways(card: View, dir: Int) {
        card.animate()
            .translationX(dir * dp(600).toFloat())
            .alpha(0f)
            .setDuration(220)
            .withEndAction {
                container.removeView(card)
                checkEmpty()
            }
            .start()
    }

    private fun closeAllCards() {
        val cards = (0 until container.childCount).map { container.getChildAt(it) }
        cards.forEachIndexed { i, card ->
            card.postDelayed({
                card.animate()
                    .translationY(-dp(600).toFloat())
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction {
                        container.removeView(card)
                        checkEmpty()
                    }
                    .start()
            }, i * 40L)
        }
    }

    private fun checkEmpty() {
        if (container.childCount == 0) {
            showMessage("Sab recent apps clear ho gaye")
            closeAllButton.visibility = View.GONE
            root.postDelayed({ closeWithAnimation() }, 500)
        }
    }

    // Pager effect: center wala card bada, bahar wale chhote aur halke
    private fun applyPagerEffect() {
        val viewportCenter = scroll.scrollX + scroll.width / 2f
        for (i in 0 until container.childCount) {
            val card = container.getChildAt(i)
            val cardCenter = card.left + card.width / 2f
            val dist = (abs(cardCenter - viewportCenter) / scroll.width.toFloat()).coerceAtMost(1f)
            val scale = 1f - dist * 0.25f
            card.scaleX = scale
            card.scaleY = scale
            card.alpha = 1f - dist * 0.5f
        }
    }

    private fun closeWithAnimation() {
        if (closing) return
        closing = true
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
