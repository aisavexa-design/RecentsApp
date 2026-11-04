package com.aisavexa.recents

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs

class RecentsActivity : AppCompatActivity() {

    private data class RecentApp(val pkg: String, val label: String, val icon: Drawable)
    private data class Item(val wrap: LinearLayout, val card: View, val app: RecentApp)

    private val dayMs = 24L * 3600_000L
    private val foregroundEvent = 1

    private var closing = false
    private var opening = false
    private var blur = 0
    private var cardW = 0
    private var cardH = 0
    private var gap = 0
    private val items = mutableListOf<Item>()
    private var recentList: List<RecentApp> = emptyList()

    private lateinit var root: FrameLayout
    private lateinit var bgImage: ImageView
    private lateinit var scroll: HorizontalScrollView
    private lateinit var container: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var closeAllButton: TextView
    private lateinit var creditText: TextView
    private lateinit var menuButton: TextView
    private lateinit var recentRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: status bar aur navigation bar transparent, navigation bar ka black scrim band
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_recents)

        root = findViewById(R.id.root)
        bgImage = findViewById(R.id.bgImage)
        scroll = findViewById(R.id.cardScroll)
        container = findViewById(R.id.cardContainer)
        emptyText = findViewById(R.id.emptyText)
        closeAllButton = findViewById(R.id.closeAllButton)
        creditText = findViewById(R.id.creditText)
        menuButton = findViewById(R.id.menuButton)
        recentRow = findViewById(R.id.recentRow)

        // Samsung jaisa pill button
        closeAllButton.background = GradientDrawable().apply {
            setColor(0x33FFFFFF)
            cornerRadius = dp(28).toFloat()
        }

        gap = dp(20)
        cardW = dp(240)
        cardH = dp(400)
        val pad = (resources.displayMetrics.widthPixels - cardW) / 2
        scroll.setPadding(pad, 0, pad, 0)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            applyInsets(insets)
            insets
        }

        loadBackground()

        root.setOnClickListener { closeWithAnimation() }
        closeAllButton.setOnClickListener { closeAllCards() }
        menuButton.setOnClickListener { openSettings() }

        val apps = loadRecentApps()
        recentList = apps ?: emptyList()
        if (apps == null) {
            showMessage("Usage access OFF hai. Yahan tap karke ON karo")
            emptyText.isClickable = true
            emptyText.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            closeAllButton.visibility = View.GONE
        } else if (apps.isEmpty()) {
            showMessage("Koi recent app nahi")
            closeAllButton.visibility = View.GONE
        } else {
            apps.forEach { addItem(it) }
            scroll.post { applyPager() }
        }
        buildRecentRow()

        scroll.setOnScrollChangeListener { _, _, _, _, _ -> applyPager() }

        root.alpha = 0f
        root.animate().alpha(1f).setDuration(260).start()
        animateBlur(60, 280)
    }

    // Cards ko top se neeche rakhna, Close all unke neeche, icons sabse neeche
    private fun applyInsets(insets: WindowInsetsCompat) {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val scrollTop = bars.top + dp(110)
        (scroll.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.TOP
            topMargin = scrollTop
        }
        val cardsBottom = scrollTop + dp(56) + dp(16) + cardH
        (closeAllButton.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = cardsBottom + dp(28)
        }
        (recentRow.layoutParams as FrameLayout.LayoutParams).bottomMargin = bars.bottom + dp(20)
        (menuButton.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + dp(8)
        (creditText.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + dp(48)
        listOf<View>(scroll, closeAllButton, recentRow, menuButton, creditText).forEach { it.requestLayout() }
    }

    private fun openSettings() {
        if (opening) return
        opening = true
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
        overridePendingTransition(0, 0)
    }

    private fun setBlur(radius: Int) {
        blur = radius
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(radius)
        }
    }

    private fun animateBlur(to: Int, duration: Long, onEnd: (() -> Unit)? = null) {
        ValueAnimator.ofInt(blur, to).apply {
            this.duration = duration
            addUpdateListener { setBlur(it.animatedValue as Int) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun loadBackground() {
        val dim = findViewById<View>(R.id.bgDim)
        val file = Prefs.bgFile(this)
        if (!file.exists()) {
            bgImage.visibility = View.GONE
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
        bgImage.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath, opts))
        bgImage.visibility = View.VISIBLE
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
        val events = usm.queryEvents(now - 2 * dayMs, now)
        val event = UsageEvents.Event()
        val lastUsed = HashMap<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == foregroundEvent) {
                val prev = lastUsed[event.packageName] ?: 0L
                if (event.timeStamp > prev) lastUsed[event.packageName] = event.timeStamp
            }
        }

        val pm = packageManager
        val homePkg = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        )?.activityInfo?.packageName

        return lastUsed.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter {
                it != packageName &&
                    it != homePkg &&
                    it != "com.android.systemui" &&
                    pm.getLaunchIntentForPackage(it) != null
            }
            .take(10)
            .mapNotNull { pkg ->
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    RecentApp(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
                }.getOrNull()
            }
    }

    // Neeche ke 4 recent icons (bina label ke)
    private fun buildRecentRow() {
        recentRow.removeAllViews()
        recentList.take(4).forEach { app ->
            val iv = ImageView(this)
            iv.setImageDrawable(app.icon)
            iv.isClickable = true
            iv.setOnClickListener { zoomToApp(iv, app) }
            recentRow.addView(iv, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
    }

    // Har item: upar app ka icon, neeche card
    private fun addItem(app: RecentApp) {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(cardW, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = gap
            }
        }
        wrap.addView(
            ImageView(this).apply { setImageDrawable(app.icon) },
            LinearLayout.LayoutParams(dp(56), dp(56)).apply { bottomMargin = dp(16) }
        )
        val card = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFF2A2A2A.toInt())
                cornerRadius = dp(28).toFloat()
            }
            isClickable = true
        }
        wrap.addView(card, LinearLayout.LayoutParams(cardW, cardH))
        card.setOnTouchListener(CardTouch(wrap, card, app))

        items.add(Item(wrap, card, app))
        container.addView(wrap)
    }

    // Carousel: center se door hone par card chhota aur halka
    private fun pagerDistance(wrap: View): Float {
        val centerX = scroll.scrollX + scroll.width / 2f
        val cx = wrap.left + wrap.width / 2f
        return (abs(cx - centerX) / (cardW + gap)).coerceAtMost(1f)
    }

    private fun applyPager() {
        items.forEach { item ->
            val d = pagerDistance(item.wrap)
            item.wrap.scaleX = 1f - d * 0.12f
            item.wrap.scaleY = 1f - d * 0.12f
            item.wrap.alpha = 1f - d * 0.3f
        }
    }

    // Vertical drag = card upar (chhota + fade). Horizontal drag = carousel scroll.
    private inner class CardTouch(
        private val wrap: View,
        private val card: View,
        private val app: RecentApp
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var baseScale = 1f
        private var mode = 0 // 0 = abhi tay nahi, 1 = vertical, 2 = horizontal

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            if (opening) return true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    mode = 0
                    baseScale = 1f - pagerDistance(wrap) * 0.12f
                    wrap.animate().cancel()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (mode == 0 && (abs(dx) > dp(12) || abs(dy) > dp(12))) {
                        mode = if (abs(dy) > abs(dx)) 1 else 2
                        // Vertical lock: scroll view ko touch na chhine
                        if (mode == 1) wrap.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    if (mode == 1) {
                        val up = minOf(0f, dy)
                        wrap.translationX = 0f
                        wrap.translationY = up
                        val f = (-up / dp(300).toFloat()).coerceIn(0f, 1f)
                        val s = baseScale * (1f - f * 0.3f)
                        wrap.scaleX = s
                        wrap.scaleY = s
                        wrap.alpha = 1f - f * 0.6f
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    when {
                        mode == 0 && abs(dx) < dp(12) && abs(dy) < dp(12) -> zoomToApp(card, app)
                        mode == 1 && -dy > dp(80) -> dismissUp(wrap, baseScale)
                        else -> resetWrap(wrap)
                    }
                }
                MotionEvent.ACTION_CANCEL -> resetWrap(wrap)
            }
            return true
        }
    }

    private fun resetWrap(wrap: View) {
        val d = pagerDistance(wrap)
        wrap.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f - d * 0.12f)
            .scaleY(1f - d * 0.12f)
            .alpha(1f - d * 0.3f)
            .setDuration(180)
            .start()
    }

    // Usi jagah par chhota hokar fade hota hai, phir recents se hat jata hai
    private fun dismissUp(wrap: View, base: Float) {
        wrap.animate()
            .translationY(wrap.translationY - dp(30))
            .scaleX(base * 0.6f)
            .scaleY(base * 0.6f)
            .alpha(0f)
            .setDuration(240)
            .withEndAction { removeItem(wrap) }
            .start()
    }

    private fun removeItem(wrap: View) {
        items.removeAll { it.wrap === wrap }
        container.removeView(wrap)
        applyPager()
        checkEmpty()
    }

    private fun closeAllCards() {
        items.toList().forEachIndexed { i, item ->
            item.wrap.postDelayed({
                if (items.any { it.wrap === item.wrap }) dismissUp(item.wrap, item.wrap.scaleX)
            }, i * 80L)
        }
    }

    private fun checkEmpty() {
        if (items.isEmpty()) {
            showMessage("Sab recent apps clear ho gaye")
            closeAllButton.visibility = View.GONE
            root.postDelayed({ closeWithAnimation() }, 500)
        }
    }

    // Tapped item poori screen tak zoom hota hai, baaki sab fade out, phir app khulta hai
    private fun zoomToApp(from: View, app: RecentApp) {
        if (opening) return
        opening = true
        val launch = packageManager.getLaunchIntentForPackage(app.pkg)

        val rootLoc = IntArray(2).also { root.getLocationOnScreen(it) }
        val fromLoc = IntArray(2).also { from.getLocationOnScreen(it) }
        val parentScale = (from.parent as? View)?.scaleX ?: 1f
        val startL = (fromLoc[0] - rootLoc[0]).toFloat()
        val startT = (fromLoc[1] - rootLoc[1]).toFloat()
        val startW = from.width * from.scaleX * parentScale
        val startH = from.height * from.scaleY * parentScale
        val corner = dp(28).toFloat()

        val bg = GradientDrawable().apply {
            setColor(0xFF2A2A2A.toInt())
            cornerRadius = corner
        }
        val zoom = FrameLayout(this).apply { background = bg }
        val icon = ImageView(this).apply { setImageDrawable(app.icon) }
        zoom.addView(icon, FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER))

        val lp = FrameLayout.LayoutParams(startW.toInt(), startH.toInt(), Gravity.TOP or Gravity.START).apply {
            leftMargin = startL.toInt()
            topMargin = startT.toInt()
        }
        root.addView(zoom, lp)

        listOf<View>(scroll, closeAllButton, creditText, emptyText, recentRow, menuButton).forEach {
            it.animate().alpha(0f).setDuration(200).start()
        }

        val rootW = root.width.toFloat()
        val rootH = root.height.toFloat()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedFraction
                lp.width = lerp(startW, rootW, t).toInt()
                lp.height = lerp(startH, rootH, t).toInt()
                lp.leftMargin = lerp(startL, 0f, t).toInt()
                lp.topMargin = lerp(startT, 0f, t).toInt()
                zoom.layoutParams = lp
                bg.cornerRadius = corner * (1f - t)
                icon.alpha = (1f - t * 3f).coerceIn(0f, 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    launch?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        startActivity(it)
                    }
                    finish()
                    overridePendingTransition(0, 0)
                }
            })
            start()
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    // Fade-out + blur-out, koi slide nahi
    private fun closeWithAnimation() {
        if (closing) return
        closing = true
        root.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction {
                finish()
                overridePendingTransition(0, 0)
            }
            .start()
        animateBlur(0, 220)
    }

    override fun onBackPressed() {
        closeWithAnimation()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
