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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.max

class RecentsActivity : AppCompatActivity() {

    private data class RecentApp(val pkg: String, val label: String, val icon: Drawable)

    private val white = 0xFFFFFFFF.toInt()
    private val dayMs = 24L * 3600_000L
    private val foregroundEvent = 1

    private var closing = false
    private var opening = false
    private var blur = 0
    private var recentList: List<RecentApp> = emptyList()
    private val stack = mutableListOf<View>() // index 0 = sabse upar wala card

    private lateinit var root: FrameLayout
    private lateinit var bgImage: ImageView
    private lateinit var cardStack: FrameLayout
    private lateinit var emptyText: TextView
    private lateinit var closeAllButton: Button
    private lateinit var recentRow: LinearLayout
    private lateinit var creditText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recents)

        root = findViewById(R.id.root)
        bgImage = findViewById(R.id.bgImage)
        cardStack = findViewById(R.id.cardStack)
        emptyText = findViewById(R.id.emptyText)
        closeAllButton = findViewById(R.id.closeAllButton)
        recentRow = findViewById(R.id.recentRow)
        creditText = findViewById(R.id.creditText)

        loadBackground()

        root.setOnClickListener { closeWithAnimation() }
        closeAllButton.setOnClickListener { closeAllCards() }

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
            buildStack(apps)
        }
        buildRecentRow()

        root.alpha = 0f
        root.animate().alpha(1f).setDuration(260).start()
        animateBlur(60, 280)
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

    // Stack: sabse recent app upar, baaki peeche chhote hokar
    private fun buildStack(apps: List<RecentApp>) {
        cardStack.removeAllViews()
        stack.clear()
        apps.forEach { app ->
            val card = makeCard(app)
            stack.add(card)
            cardStack.addView(card)
        }
        layoutStack(animate = false)
    }

    private fun makeCard(app: RecentApp): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(0xFF2A2A2A.toInt())
                cornerRadius = dp(24).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(dp(260), dp(420), Gravity.CENTER)
            isClickable = true
        }
        card.addView(
            ImageView(this).apply { setImageDrawable(app.icon) },
            LinearLayout.LayoutParams(dp(80), dp(80))
        )
        card.addView(TextView(this).apply {
            text = app.label
            setTextColor(white)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })
        card.setOnTouchListener(CardTouch(card, app))
        return card
    }

    private fun layoutStack(animate: Boolean = true) {
        val n = stack.size
        stack.forEachIndexed { i, card ->
            val ty = dp(16 * i).toFloat()
            val s = (1f - i * 0.06f).coerceAtLeast(0.6f)
            val a = if (i < 4) 1f else 0f
            card.elevation = dp(2 * (n - i)).toFloat()
            if (animate) {
                card.animate()
                    .translationX(0f)
                    .translationY(ty)
                    .scaleX(s)
                    .scaleY(s)
                    .alpha(a)
                    .setDuration(220)
                    .start()
            } else {
                card.translationX = 0f
                card.translationY = ty
                card.scaleX = s
                card.scaleY = s
                card.alpha = a
            }
        }
    }

    // Neeche ka row: sirf icon, label nahi
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

    // Tap = zoom karke app khule
    // Upar swipe = card screen ke bahar upar chala jaye
    // Left/right swipe = card side mein hat jaye
    private inner class CardTouch(private val card: View, private val app: RecentApp) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var baseY = 0f

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            if (opening) return true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    baseY = card.translationY
                    card.animate().cancel()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    card.translationX = dx
                    card.translationY = baseY + dy
                }
                MotionEvent.ACTION_UP -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    when {
                        abs(dx) < dp(10) && abs(dy) < dp(10) -> zoomToApp(card, app)
                        dy < -dp(60) && abs(dy) > abs(dx) -> dismissUp(card)
                        abs(dx) > dp(120) -> dismissSideways(card, if (dx < 0) -1 else 1)
                        else -> layoutStack()
                    }
                }
                MotionEvent.ACTION_CANCEL -> layoutStack()
            }
            return true
        }
    }

    // Card ko itna upar le jao ki uska niche wala hissa screen ke top se bahar ho jaye
    private fun dismissUp(card: View) {
        val loc = IntArray(2).also { card.getLocationOnScreen(it) }
        val bottom = loc[1] + card.height * card.scaleY
        val delta = -bottom - dp(40)
        card.animate()
            .translationY(card.translationY + delta)
            .alpha(0f)
            .setDuration(260)
            .withEndAction { removeCard(card) }
            .start()
    }

    private fun dismissSideways(card: View, dir: Int) {
        card.animate()
            .translationX(dir * root.width.toFloat())
            .alpha(0f)
            .setDuration(240)
            .withEndAction { removeCard(card) }
            .start()
    }

    private fun removeCard(card: View) {
        stack.remove(card)
        cardStack.removeView(card)
        layoutStack()
        checkEmpty()
    }

    private fun closeAllCards() {
        stack.toList().forEachIndexed { i, card ->
            card.postDelayed({ if (card in stack) dismissUp(card) }, i * 60L)
        }
    }

    private fun checkEmpty() {
        if (stack.isEmpty()) {
            showMessage("Sab recent apps clear ho gaye")
            closeAllButton.visibility = View.GONE
            root.postDelayed({ closeWithAnimation() }, 500)
        }
    }

    // Tile/card poori screen tak zoom hoti hai, baaki sab fade out,
    // phir wahi zoom app ki screen ban kar app khulta hai
    private fun zoomToApp(from: View, app: RecentApp) {
        if (opening) return
        opening = true
        val launch = packageManager.getLaunchIntentForPackage(app.pkg)

        val rootLoc = IntArray(2).also { root.getLocationOnScreen(it) }
        val fromLoc = IntArray(2).also { from.getLocationOnScreen(it) }
        val startL = (fromLoc[0] - rootLoc[0]).toFloat()
        val startT = (fromLoc[1] - rootLoc[1]).toFloat()
        val startW = max(1f, from.width * from.scaleX)
        val startH = max(1f, from.height * from.scaleY)
        val corner = dp(24).toFloat()

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

        listOf<View>(cardStack, recentRow, closeAllButton, creditText, emptyText).forEach {
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
