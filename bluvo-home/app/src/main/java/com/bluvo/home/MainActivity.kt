package com.bluvo.home

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Date

data class AppEntry(
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val banner: Drawable?,
    val icon: Drawable?,
    val isGame: Boolean,
    val isVideo: Boolean
)

class MainActivity : Activity() {

    private val bg = Color.parseColor("#0B1020")
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var scroll: ScrollView
    private lateinit var page: LinearLayout
    private lateinit var clock: TextView

    private val tabNames = listOf("الصفحة الرئيسية", "أفلام ومسلسلات", "ألعاب", "التطبيقات")
    private val tabViews = ArrayList<TextView>()
    private val sideViews = ArrayList<Pair<ImageView, Int>>()
    private var currentTab = 0

    // remote button mapping
    private var captureMode = false
    private var pendingKey: Int? = null
    private var captureLabel: TextView? = null
    private val blockedKeys = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_UNKNOWN
    )

    private var apps: List<AppEntry> = emptyList()
    private var firstCard: View? = null

    private var heroApps: List<AppEntry> = emptyList()
    private var heroIndex = 0
    private var heroTitle: TextView? = null
    private var heroIcon: ImageView? = null
    private var heroDots: LinearLayout? = null

    private val videoHints = listOf(
        "netflix", "youtube", "disney", "amazon.avod", "primevideo", "shahid", "osn", "hbo",
        "wbd", "plex", "kodi", "vlc", "iptv", "video", "movie", "tod", "starz", "crunchyroll",
        "apple.atve", "mbc", "weyyak", "jawwy", "twitch", "player"
    )

    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 20_000)
        }
    }

    private val heroTick = object : Runnable {
        override fun run() {
            if (heroApps.size > 1) {
                heroIndex = (heroIndex + 1) % heroApps.size
                updateHero(true)
            }
            handler.postDelayed(this, 8_000)
        }
    }

    // ---------- helpers ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dpf(v: Float): Float = v * resources.displayMetrics.density
    private fun prefs() = getSharedPreferences("bluvo_home", Context.MODE_PRIVATE)

    private fun rounded(color: Int, radius: Float, strokeW: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpf(radius)
            if (strokeW > 0) setStroke(dp(strokeW), strokeColor)
        }

    private fun gradient(o: GradientDrawable.Orientation, vararg colors: String): GradientDrawable =
        GradientDrawable(o, colors.map { Color.parseColor(it) }.toIntArray())

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val root = FrameLayout(this)
        root.layoutDirection = View.LAYOUT_DIRECTION_LTR
        root.setBackgroundColor(bg)

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
        }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(48))
        }
        scroll.addView(page, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        root.addView(buildSidebar(), FrameLayout.LayoutParams(dp(72), -1))
        root.addView(buildTopBar(), FrameLayout.LayoutParams(-1, dp(68)))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        showTab(currentTab, true)
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(heroTick)
        handler.post(clockTick)
        handler.postDelayed(heroTick, 8_000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(heroTick)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        currentTab = 0
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (captureMode) {
            if (event.action == KeyEvent.ACTION_UP) handleCapture(event.keyCode)
            return true
        }
        // Fallback when the background service is off: mapped keys still work on the home screen.
        if (!KeyMap.serviceEnabled(this)) {
            val pkg = KeyMap.get(this, event.keyCode)
            if (pkg != null) {
                if (event.action == KeyEvent.ACTION_UP) KeyMap.launch(this, pkg)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentTab == 5) showTab(4, true)
        else if (currentTab != 0) showTab(0, true)
        // Home screen: never close on Back.
    }

    // ---------- apps ----------

    private fun loadApps() {
        val pm = packageManager
        val hidden = setOf(
            packageName,
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.google.android.tungsten.setupwraith"
        )
        val map = LinkedHashMap<String, AppEntry>()

        fun add(list: List<ResolveInfo>) {
            for (ri in list) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName
                if (pkg in hidden || map.containsKey(pkg)) continue
                val banner = try { ai.loadBanner(pm) } catch (e: Exception) { null }
                val icon = try { ai.loadIcon(pm) } catch (e: Exception) { null }
                val label = try { ri.loadLabel(pm).toString() } catch (e: Exception) { pkg }
                val info = ai.applicationInfo
                val cat = if (Build.VERSION.SDK_INT >= 26) info.category else -1
                @Suppress("DEPRECATION")
                val game = cat == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                val lower = pkg.lowercase()
                val video = !game && (cat == ApplicationInfo.CATEGORY_VIDEO || videoHints.any { lower.contains(it) })
                map[pkg] = AppEntry(label, pkg, ComponentName(pkg, ai.name), banner, icon, game, video)
            }
        }

        try {
            add(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0))
            add(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
        } catch (e: Exception) {
        }

        val p = prefs()
        apps = map.values.sortedWith(
            compareByDescending<AppEntry> { p.getInt("c_" + it.pkg, 0) }
                .thenByDescending { it.isVideo }
                .thenBy { it.label.lowercase() }
        )
        heroApps = apps.take(5)
        if (heroIndex >= heroApps.size) heroIndex = 0
    }

    private fun launch(app: AppEntry) {
        val p = prefs()
        p.edit().putInt("c_" + app.pkg, p.getInt("c_" + app.pkg, 0) + 1).apply()
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            val fallback = packageManager.getLeanbackLaunchIntentForPackage(app.pkg)
                ?: packageManager.getLaunchIntentForPackage(app.pkg)
            if (fallback != null) {
                try { startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (ignored: Exception) {}
            }
        }
    }

    private fun safeStart(vararg intents: Intent) {
        for (i in intents) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (ignored: Exception) {
            }
        }
    }

    private fun openSettings() = safeStart(Intent(Settings.ACTION_SETTINGS))

    private fun openSearch() = safeStart(
        Intent("android.search.action.GLOBAL_SEARCH"),
        Intent(Intent.ACTION_ASSIST),
        Intent(Intent.ACTION_WEB_SEARCH)
    )

    private fun openAppInfo(pkg: String) = safeStart(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
    )

    // ---------- chrome: top bar + sidebar ----------

    private fun iconButton(res: Int, sizeDp: Int, padDp: Int, onClick: () -> Unit): ImageView =
        ImageView(this).apply {
            setImageResource(res)
            setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp))
            isFocusable = true
            isClickable = true
            alpha = 0.85f
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) rounded(Color.parseColor("#40FFFFFF"), (sizeDp / 2).toFloat()) else null
                v.alpha = if (f) 1f else 0.85f
            }
        }

    private fun buildTopBar(): View {
        val bar = FrameLayout(this).apply {
            background = gradient(GradientDrawable.Orientation.TOP_BOTTOM, "#CC0B1020", "#000B1020")
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        clock = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        left.addView(clock, LinearLayout.LayoutParams(-2, -2))
        left.addView(ImageView(this).apply { setImageResource(R.drawable.ic_wifi) },
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(22) })
        left.addView(iconButton(R.drawable.ic_settings, 36, 7) { openSettings() },
            LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(12) })
        bar.addView(left, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL)
            .apply { marginStart = dp(30) })

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabViews.clear()
        tabNames.forEachIndexed { i, name ->
            val tv = TextView(this).apply {
                text = name
                textSize = 15f
                setPadding(dp(18), dp(7), dp(18), dp(7))
                isFocusable = true
                isClickable = true
                setOnClickListener { showTab(i, false) }
                setOnFocusChangeListener { _, _ -> styleTabs() }
            }
            tabViews.add(tv)
            tabs.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
        bar.addView(tabs, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        bar.addView(iconButton(R.drawable.ic_search, 40, 8) { openSearch() },
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END or Gravity.CENTER_VERTICAL)
                .apply { marginEnd = dp(30) })
        return bar
    }

    private fun buildSidebar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(92), 0, 0)
            background = gradient(GradientDrawable.Orientation.LEFT_RIGHT, "#F20B1020", "#800B1020", "#000B1020")
        }
        val items = listOf(
            Triple(R.drawable.ic_home, 0, { showTab(0, false) }),
            Triple(R.drawable.ic_tv, 3, { showTab(3, false) }),
            Triple(R.drawable.ic_movie, 1, { showTab(1, false) }),
            Triple(R.drawable.ic_game, 2, { showTab(2, false) }),
            Triple(R.drawable.ic_more, 4, { showTab(4, false) })
        )
        sideViews.clear()
        for ((res, tab, action) in items) {
            val iv = ImageView(this).apply {
                setImageResource(res)
                setPadding(dp(11), dp(11), dp(11), dp(11))
                isFocusable = true
                isClickable = true
                setOnClickListener { action() }
                setOnFocusChangeListener { _, _ -> styleSide() }
            }
            sideViews.add(iv to tab)
            bar.addView(iv, LinearLayout.LayoutParams(dp(46), dp(46)).apply { bottomMargin = dp(16) })
        }
        return bar
    }

    private fun styleTabs() {
        tabViews.forEachIndexed { i, tv ->
            val focused = tv.isFocused
            val selected = i == currentTab
            tv.background = when {
                focused -> rounded(Color.WHITE, 20f)
                selected -> rounded(Color.parseColor("#40FFFFFF"), 20f)
                else -> null
            }
            tv.setTextColor(
                when {
                    focused -> bg
                    selected -> Color.WHITE
                    else -> Color.parseColor("#B3FFFFFF")
                }
            )
        }
    }

    private fun styleSide() {
        for ((iv, tab) in sideViews) {
            val focused = iv.isFocused
            val selected = tab == currentTab
            iv.background = when {
                focused -> rounded(Color.WHITE, 14f)
                selected -> rounded(Color.parseColor("#33FFFFFF"), 14f)
                else -> null
            }
            if (focused) iv.setColorFilter(bg, PorterDuff.Mode.SRC_IN) else iv.clearColorFilter()
            iv.alpha = if (focused || selected) 1f else 0.7f
        }
    }

    private fun updateClock() {
        val fmt = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm"
        clock.text = DateFormat.format(fmt, Date())
    }

    // ---------- pages ----------

    private fun showTab(i: Int, focusContent: Boolean) {
        currentTab = i
        page.removeAllViews()
        firstCard = null
        heroTitle = null
        heroIcon = null
        heroDots = null

        when (i) {
            0 -> {
                page.addView(buildHero())
                page.addView(appRow(apps.take(10)))
                page.addView(sectionTitle("كل التطبيقات"))
                page.addView(grid(apps))
            }
            1 -> {
                page.addView(spacer(76))
                page.addView(sectionTitle("أفلام ومسلسلات"))
                page.addView(grid(apps.filter { it.isVideo }))
            }
            2 -> {
                page.addView(spacer(76))
                page.addView(sectionTitle("ألعاب"))
                page.addView(grid(apps.filter { it.isGame }))
            }
            3 -> {
                page.addView(spacer(76))
                page.addView(sectionTitle("التطبيقات"))
                page.addView(grid(apps))
            }
            4 -> buildButtonsPage()
            else -> {
                val key = pendingKey
                page.addView(spacer(76))
                page.addView(sectionTitle("اختر التطبيق الذي يفتحه زر " + KeyMap.keyName(key ?: 0)))
                page.addView(grid(apps) { app ->
                    if (key != null) KeyMap.set(this, key, app.pkg)
                    pendingKey = null
                    showTab(4, true)
                })
            }
        }
        styleTabs()
        styleSide()
        scroll.scrollTo(0, 0)
        if (focusContent) {
            page.post {
                firstCard?.requestFocus()
                scroll.scrollTo(0, 0)
            }
        }
    }

    private fun spacer(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(h)) }

    private fun sectionTitle(text: String): View = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 19f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(88), dp(18), dp(40), dp(2))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun buildHero(): View {
        val hero = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(290))
            clipChildren = false
        }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.hero)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(-1, -1))
        hero.addView(View(this).apply {
            background = gradient(GradientDrawable.Orientation.LEFT_RIGHT, "#F20B1020", "#A60B1020", "#200B1020", "#000B1020")
        }, FrameLayout.LayoutParams(-1, -1))
        hero.addView(View(this).apply {
            background = gradient(GradientDrawable.Orientation.TOP_BOTTOM, "#000B1020", "#000B1020", "#FF0B1020")
        }, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        heroIcon = ImageView(this)
        col.addView(heroIcon, LinearLayout.LayoutParams(dp(42), dp(42)).apply { bottomMargin = dp(8) })
        heroTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 40f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(12f, 0f, 2f, Color.parseColor("#99000000"))
        }
        col.addView(heroTitle, LinearLayout.LayoutParams(dp(520), -2))
        col.addView(TextView(this).apply {
            text = "شاهد ما تحب، بلا إعلانات"
            setTextColor(Color.parseColor("#D9FFFFFF"))
            textSize = 16f
            setShadowLayer(8f, 0f, 1f, Color.parseColor("#99000000"))
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })

        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(24), dp(10))
            isFocusable = true
            isClickable = true
            background = rounded(Color.parseColor("#33FFFFFF"), 24f)
        }
        val play = ImageView(this).apply { setImageResource(R.drawable.ic_play) }
        val btnText = TextView(this).apply {
            text = "مشاهدة الآن"
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        btn.addView(play, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) })
        btn.addView(btnText, LinearLayout.LayoutParams(-2, -2))
        btn.setOnClickListener { heroApps.getOrNull(heroIndex)?.let { launch(it) } }
        btn.setOnFocusChangeListener { v, f ->
            v.background = rounded(if (f) Color.WHITE else Color.parseColor("#33FFFFFF"), 24f)
            btnText.setTextColor(if (f) bg else Color.WHITE)
            if (f) play.setColorFilter(bg, PorterDuff.Mode.SRC_IN) else play.clearColorFilter()
            v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(120).start()
        }
        col.addView(btn, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(22) })

        hero.addView(col, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL)
            .apply { marginStart = dp(92); topMargin = dp(36) })

        heroDots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hero.addView(heroDots, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = dp(16) })

        updateHero(false)
        return hero
    }

    private fun updateHero(animate: Boolean) {
        val app = heroApps.getOrNull(heroIndex)
        heroTitle?.text = app?.label ?: "Bluvo"
        heroIcon?.setImageDrawable(app?.icon)
        heroIcon?.visibility = if (app?.icon != null) View.VISIBLE else View.INVISIBLE
        if (animate) {
            heroTitle?.alpha = 0f
            heroTitle?.animate()?.alpha(1f)?.setDuration(400)?.start()
            heroIcon?.alpha = 0f
            heroIcon?.animate()?.alpha(1f)?.setDuration(400)?.start()
        }
        heroDots?.let { d ->
            d.removeAllViews()
            for (i in heroApps.indices) {
                val dot = View(this)
                dot.background = rounded(if (i == heroIndex) Color.WHITE else Color.parseColor("#66FFFFFF"), 4f)
                d.addView(dot, LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginStart = dp(5); marginEnd = dp(5) })
            }
        }
    }

    private fun appRow(list: List<AppEntry>): View {
        val hs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(88), dp(12), dp(48), dp(12))
        }
        for (a in list) {
            val c = appCard(a)
            if (firstCard == null) firstCard = c
            row.addView(c, LinearLayout.LayoutParams(dp(156), dp(88)).apply { marginEnd = dp(16) })
        }
        hs.addView(row)
        return hs
    }

    private fun grid(list: List<AppEntry>, onPick: ((AppEntry) -> Unit)? = null): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(88), dp(6), dp(40), 0)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        if (list.isEmpty()) {
            wrap.addView(TextView(this).apply {
                text = "لا توجد تطبيقات هنا بعد"
                setTextColor(Color.parseColor("#99FFFFFF"))
                textSize = 16f
                setPadding(0, dp(20), 0, 0)
            })
            return wrap
        }
        val density = resources.displayMetrics.density
        val avail = resources.displayMetrics.widthPixels / density - 88 - 40
        val cardW = 156
        val gap = 16
        val perRow = maxOf(1, ((avail + gap) / (cardW + gap)).toInt())
        for (chunk in list.chunked(perRow)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
                setPadding(0, dp(10), 0, dp(10))
            }
            for (a in chunk) {
                val c = appCard(a, onPick)
                if (firstCard == null) firstCard = c
                row.addView(c, LinearLayout.LayoutParams(dp(cardW), dp(88)).apply { marginEnd = dp(gap) })
            }
            wrap.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        return wrap
    }

    private fun appCard(app: AppEntry, onPick: ((AppEntry) -> Unit)? = null): View {
        val card = FrameLayout(this)
        card.isFocusable = true
        card.isClickable = true
        card.isLongClickable = true
        card.background = rounded(Color.parseColor("#1C2438"), 14f)
        card.clipToOutline = true
        card.outlineProvider = ViewOutlineProvider.BACKGROUND

        if (app.banner != null) {
            card.addView(ImageView(this).apply {
                setImageDrawable(app.banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            col.addView(ImageView(this).apply { setImageDrawable(app.icon) },
                LinearLayout.LayoutParams(dp(38), dp(38)))
            col.addView(TextView(this).apply {
                text = app.label
                setTextColor(Color.WHITE)
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), 0)
            }, LinearLayout.LayoutParams(-1, -2))
            card.addView(col, FrameLayout.LayoutParams(-1, -1))
        }

        val border = rounded(Color.TRANSPARENT, 14f, 3, Color.WHITE)
        card.setOnFocusChangeListener { v, f ->
            v.animate()
                .scaleX(if (f) 1.1f else 1f)
                .scaleY(if (f) 1.1f else 1f)
                .translationZ(if (f) dpf(12f) else 0f)
                .setDuration(140)
                .start()
            card.foreground = if (f) border else null
        }
        card.setOnClickListener { if (onPick != null) onPick(app) else launch(app) }
        card.setOnLongClickListener {
            openAppInfo(app.pkg)
            true
        }
        return card
    }

    // ---------- remote buttons page ----------

    private fun handleCapture(code: Int) {
        if (code == KeyEvent.KEYCODE_BACK) {
            captureMode = false
            showTab(4, true)
            return
        }
        if (code in blockedKeys) {
            captureLabel?.text = "هذا الزر مخصص للتنقل، اضغط زراً آخر (أو رجوع للإلغاء)"
            return
        }
        captureMode = false
        pendingKey = code
        showTab(5, true)
    }

    private fun settingRow(title: String, sub: String?, onClick: (() -> Unit)?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(14), dp(22), dp(14))
            background = rounded(Color.parseColor("#1C2438"), 14f)
            isFocusable = true
            isClickable = onClick != null
        }
        val t = TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(Color.WHITE)
        }
        row.addView(t)
        var s: TextView? = null
        if (sub != null) {
            s = TextView(this).apply {
                text = sub
                textSize = 13f
                setTextColor(Color.parseColor("#B3FFFFFF"))
                setPadding(0, dp(4), 0, 0)
            }
            row.addView(s)
        }
        row.setOnFocusChangeListener { v, f ->
            v.background = rounded(if (f) Color.WHITE else Color.parseColor("#1C2438"), 14f)
            t.setTextColor(if (f) bg else Color.WHITE)
            s?.setTextColor(if (f) Color.parseColor("#B30B1020") else Color.parseColor("#B3FFFFFF"))
        }
        if (onClick != null) row.setOnClickListener { onClick() }
        if (firstCard == null) firstCard = row
        row.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        return row
    }

    private fun buildButtonsPage() {
        page.addView(spacer(76))
        page.addView(sectionTitle("تخصيص أزرار الريموت"))
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(88), dp(12), dp(88), 0)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        page.addView(box)

        if (captureMode) {
            val row = settingRow("اضغط الآن الزر الذي تريد تخصيصه في الريموت…", "زر الرجوع للإلغاء", null)
            captureLabel = (row as LinearLayout).getChildAt(0) as TextView
            box.addView(row)
        } else {
            captureLabel = null
            box.addView(settingRow("+ إضافة زر جديد", "اختر زراً في الريموت ثم اختر التطبيق الذي يفتحه") {
                captureMode = true
                showTab(4, true)
            })
        }

        val enabled = KeyMap.serviceEnabled(this)
        box.addView(settingRow(
            if (enabled) "✓ الأزرار تعمل في كل مكان" else "الأزرار تعمل الآن من الواجهة فقط",
            if (enabled) "خدمة Bluvo للأزرار مفعّلة"
            else "لتعمل من داخل أي تطبيق: فعّل خدمة \"Bluvo أزرار الريموت\" من إعدادات إمكانية الوصول (اضغط هنا)"
        ) {
            safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        val maps = KeyMap.all(this)
        if (maps.isNotEmpty()) {
            box.addView(TextView(this).apply {
                text = "الأزرار المخصّصة (اضغط على أي واحد لحذفه)"
                setTextColor(Color.parseColor("#99FFFFFF"))
                textSize = 14f
                setPadding(0, dp(10), 0, dp(10))
            })
        }
        for ((code, pkg) in maps) {
            val label = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
            box.addView(settingRow("زر " + KeyMap.keyName(code) + "  ←  " + label, null) {
                KeyMap.remove(this, code)
                showTab(4, true)
            })
        }
    }
}
