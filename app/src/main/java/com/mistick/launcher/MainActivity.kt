package com.mistick.launcher

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.lifecycle.lifecycleScope
import com.mistick.launcher.ui.TileAdapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** KiełtykaTV home screen (HOME + LEANBACK_LAUNCHER). */
class MainActivity : FragmentActivity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var audio: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val d by lazy { resources.displayMetrics.density }

    private lateinit var scroll: ScrollView
    private lateinit var myRow: HorizontalGridView
    private lateinit var allRow: HorizontalGridView
    private lateinit var heroOpen: View
    private lateinit var myAdapter: TileAdapter
    private lateinit var allAdapter: TileAdapter

    private var apps: List<AppItem> = emptyList()
    private var heroItems: List<AppItem> = emptyList()
    private var heroIndex = 0
    private var lastMoveAt = 0L
    private var colonOn = true
    private var appsDirty = true
    private var kenBurns: ValueAnimator? = null
    private var dialog: Dialog? = null

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        prefs = LauncherPrefs(this)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager

        scroll = findViewById(R.id.home_scroll)
        myRow = findViewById(R.id.my_apps_row)
        allRow = findViewById(R.id.all_apps_row)
        heroOpen = findViewById(R.id.hero_open)

        setupRows()
        setupHeader()
        setupHero()
        setupQuickAccess()
        setupFooter()
        setupScrollFollow()
        playEntrance()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goTop()
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(this, volumeReceiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockTick)
        handler.postDelayed(heroRotate, HERO_INTERVAL)
        startKenBurns()
        refreshStatus()
        if (appsDirty) loadApps() else buildHero()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        kenBurns?.pause()
        if (myAdapter.movingPackage != null) finishMove()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        runCatching { unregisterReceiver(volumeReceiver) }
        kenBurns?.cancel()
        dialog?.dismiss()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dialog?.dismiss()
        goTop() // HOME pressed while already on the launcher
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            appsDirty = true
            loadApps()
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshVolume(animate = true)
    }

    // ---------------------------------------------------------------- data

    private fun loadApps() {
        lifecycleScope.launch {
            apps = AppRepository.loadApps(this@MainActivity)
            appsDirty = false
            bindRows()
            buildHero()
            if (currentFocus == null) focusInitial()
        }
    }

    /** TV apps (+ anything the user pinned), ordered by the user's saved order. */
    private fun myApps(): List<AppItem> {
        val order = prefs.appOrder
        val base = apps.filter { it.isTvApp || it.packageName in order }
            .filter { prefs.showSystemInMyApps || !it.isSystem || it.packageName in order }
            .ifEmpty { apps }
        val ordered = order.mapNotNull { pkg -> base.firstOrNull { it.packageName == pkg } }
        return ordered + base.filter { it.packageName !in order }
    }

    private fun bindRows() {
        myAdapter.preferBanners = prefs.preferBanners
        allAdapter.preferBanners = prefs.preferBanners
        val mine = myApps()
        myAdapter.submit(mine)
        allAdapter.submit(apps)
        val showAll = prefs.showAllAppsRow
        findViewById<View>(R.id.all_apps_title).visibility = if (showAll) View.VISIBLE else View.GONE
        findViewById<View>(R.id.all_apps_container).visibility = if (showAll) View.VISIBLE else View.GONE
        updateMyAppsMeta()
    }

    private fun updateMyAppsMeta() {
        val meta = findViewById<TextView>(R.id.my_apps_meta)
        if (myAdapter.movingPackage != null) {
            meta.text = getString(R.string.move_hint)
            meta.setTextColor(0xFF00C896.toInt())
            return
        }
        val n = myAdapter.itemCount
        meta.text = android.text.SpannableStringBuilder()
            .append(resources.getQuantityString(R.plurals.apps_count, n, n)).append(" · ")
            .append(getText(R.string.apps_hint))
        meta.setTextColor(0x80FFFFFF.toInt())
    }

    // ---------------------------------------------------------------- rows

    private fun setupRows() {
        myAdapter = TileAdapter(::launch, ::showActions)
        allAdapter = TileAdapter(::launch, ::showActions)
        for ((grid, adapter) in listOf(myRow to myAdapter, allRow to allAdapter)) {
            grid.adapter = adapter
            grid.setHorizontalSpacing((14 * d).toInt())
            grid.windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
            grid.windowAlignmentOffsetPercent = 0f
            grid.windowAlignmentOffset = ((48 + 130) * d).toInt()
            grid.itemAlignmentOffsetPercent = 0f
            grid.itemAnimator?.moveDuration = 180
        }
    }

    private fun focusInitial() {
        if (myAdapter.itemCount > 0) {
            myRow.selectedPosition = 0
            myRow.requestFocus()
        } else heroOpen.requestFocus()
    }

    private fun goTop() {
        if (myAdapter.movingPackage != null) { finishMove(); return }
        scroll.smoothScrollTo(0, 0)
        heroOpen.requestFocus()
    }

    /** Keep the focused section on screen: hero area → top, all apps → bottom. */
    private fun setupScrollFollow() {
        scroll.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            newFocus ?: return@addOnGlobalFocusChangeListener
            when {
                isInside(newFocus, allRow) -> scroll.smoothScrollTo(0, scroll.getChildAt(0).height)
                // Design: header, hero, quick panel and "My apps" fit on one screen.
                isInside(newFocus, scroll) -> scroll.smoothScrollTo(0, 0)
            }
        }
    }

    private fun isInside(v: View, parent: View): Boolean {
        var p: Any? = v
        while (p is View) { if (p === parent) return true; p = p.parent }
        return false
    }

    private fun focusedTile(): Pair<TileAdapter, AppItem>? {
        val f = currentFocus ?: return null
        for ((grid, adapter) in listOf(myRow to myAdapter, allRow to allAdapter)) {
            if (!isInside(f, grid)) continue
            val holder = grid.findContainingViewHolder(f) ?: return null
            val pos = holder.adapterPosition
            return adapter.currentItems.getOrNull(pos)?.let { adapter to it }
        }
        return null
    }

    // ---------------------------------------------------------------- keys

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        // Move mode: ◀ ▶ moves, OK / Back confirms.
        if (myAdapter.movingPackage != null) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        // Holding the key repeats very fast: allow at most one step per animation.
                        val now = android.os.SystemClock.uptimeMillis()
                        if (event.repeatCount > 0 && now - lastMoveAt < MOVE_STEP_MS) return true
                        lastMoveAt = now
                        val to = myAdapter.moveBy(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        if (to >= 0) myRow.setSelectedPositionSmooth(to)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    if (event.action == KeyEvent.ACTION_UP) finishMove()
                    return true
                }
            }
        }
        if (code in OPTION_KEYS) {
            if (event.action == KeyEvent.ACTION_UP) {
                val tile = focusedTile()
                if (tile != null) showActions(tile.second)
                else if (currentFocus?.id == R.id.hero_open || currentFocus?.id == R.id.hero_info) {
                    heroItems.getOrNull(heroIndex)?.let(::showActions)
                } else openLauncherSettings()
            }
            return true
        }
        if (code == KeyEvent.KEYCODE_SEARCH) {
            if (event.action == KeyEvent.ACTION_UP) openSearch()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------------------------------------------------------------- header

    private fun setupHeader() {
        findViewById<View>(R.id.status_pill).setOnClickListener {
            openFirst(Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        }
        findViewById<View>(R.id.avatar).setOnClickListener { openLauncherSettings() }
        listOf(R.id.status_pill, R.id.avatar).forEach { springOnFocus(findViewById(it), 1.08f) }
        findViewById<TextView>(R.id.footer_device).text =
            "${Build.MODEL} · Android ${Build.VERSION.RELEASE} · KiełtykaTV ${appVersion()}"
    }

    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    private fun updateClock() {
        val now = Date()
        val h = SimpleDateFormat("HH", Locale.getDefault()).format(now)
        val m = SimpleDateFormat("mm", Locale.getDefault()).format(now)
        colonOn = !colonOn
        val text = SpannableString("$h:$m")
        text.setSpan(ForegroundColorSpan(if (colonOn) Color.WHITE else 0x40FFFFFF),
            h.length, h.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        findViewById<TextView>(R.id.clock).text = text
        val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
        findViewById<TextView>(R.id.date).text =
            date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        findViewById<TextView>(R.id.greeting).setText(when {
            hour in 5..11 -> R.string.greeting_morning
            hour in 12..17 -> R.string.greeting_day
            else -> R.string.greeting_evening
        })
    }

    private fun refreshNetwork() {
        val label = findViewById<TextView>(R.id.net_label)
        val icon = findViewById<ImageView>(R.id.net_icon)
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> {
                label.setText(R.string.net_ethernet); icon.alpha = 0.8f
            }
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                label.setText(R.string.net_wifi); icon.alpha = 0.8f
            }
            else -> { label.setText(R.string.net_none); icon.alpha = 0.35f }
        }
    }

    // ---------------------------------------------------------------- hero

    private fun setupHero() {
        findViewById<View>(R.id.hero).clipToOutline = true
        heroOpen.setOnClickListener { heroItems.getOrNull(heroIndex)?.let(::launch) }
        findViewById<View>(R.id.hero_info).setOnClickListener {
            heroItems.getOrNull(heroIndex)?.let(::showActions)
        }
        findViewById<View>(R.id.hero_all).setOnClickListener {
            if (prefs.showAllAppsRow && allAdapter.itemCount > 0) {
                allRow.selectedPosition = 0; allRow.requestFocus()
            } else openSearch()
        }
        listOf(R.id.hero_open, R.id.hero_info, R.id.hero_all).forEach { springOnFocus(findViewById(it), 1.08f) }
    }

    private fun buildHero() {
        val byPkg = apps.associateBy { it.packageName }
        val recent = prefs.recentPackages.mapNotNull { byPkg[it] }
        val suggested = apps.filter { it.isTvApp && it.banner != null && it !in recent }
        val newItems = (recent + suggested + apps).distinct().take(4)
        val changed = newItems.map { it.packageName } != heroItems.map { it.packageName }
        heroItems = newItems
        if (changed) heroIndex = 0
        showHero(heroIndex, animate = false)
    }

    private val heroRotate = object : Runnable {
        override fun run() {
            // Don't rotate while the user is on the hero buttons.
            val f = currentFocus
            val onHero = f != null && isInside(f, findViewById(R.id.hero))
            if (heroItems.size > 1 && !onHero) showHero((heroIndex + 1) % heroItems.size, animate = true)
            handler.postDelayed(this, HERO_INTERVAL)
        }
    }

    private fun showHero(index: Int, animate: Boolean) {
        val title = findViewById<TextView>(R.id.hero_title)
        val desc = findViewById<TextView>(R.id.hero_desc)
        val badge = findViewById<TextView>(R.id.hero_badge)
        val badgeIcon = findViewById<ImageView>(R.id.hero_badge_icon)
        val fading = listOf<View>(title, desc, badge, badgeIcon, findViewById(R.id.hero_overline))

        val apply = {
            heroIndex = index
            val item = heroItems.getOrNull(index)
            val isRecent = item != null && item.packageName in prefs.recentPackages
            title.text = item?.label ?: getString(R.string.hero_empty_title)
            desc.setText(if (isRecent) R.string.hero_desc_recent else R.string.hero_desc_suggested)
            badge.setText(if (isRecent) R.string.hero_badge_recent else R.string.hero_badge_suggested)
            badgeIcon.setImageDrawable(item?.icon)
            badgeIcon.visibility = if (item?.icon != null) View.VISIBLE else View.GONE
            bindHeroArt(item)
            buildDots()
        }
        if (!animate) { apply(); return }
        // Crossfade: slide old text out to the left, new text in from the right.
        fading.forEachIndexed { i, v ->
            v.animate().alpha(0f).translationX(-16 * d).setDuration(220).setStartDelay(i * 25L)
                .setInterpolator(AccelerateInterpolator()).withEndAction(if (i == fading.lastIndex) Runnable {
                    apply()
                    fading.forEachIndexed { j, w ->
                        w.translationX = 20 * d
                        w.animate().alpha(1f).translationX(0f).setDuration(420).setStartDelay(j * 45L)
                            .setInterpolator(DecelerateInterpolator(1.8f)).withEndAction(null).start()
                    }
                } else null).start()
        }
        val art = findViewById<View>(R.id.hero_art)
        art.animate().alpha(0.55f).setDuration(260).withEndAction {
            art.animate().alpha(1f).setDuration(600).withEndAction(null).start()
        }.start()
    }

    /** Hero artwork comes from the selected app: its TV banner, else its icon, else the stock picture. */
    /** Own copy of a drawable: sharing one instance between views makes them fight over its bounds. */
    private fun fresh(d: android.graphics.drawable.Drawable) =
        d.constantState?.newDrawable(resources)?.mutate() ?: d

    private fun bindHeroArt(item: AppItem?) {
        val art = findViewById<ImageView>(R.id.hero_art)
        val banner = item?.banner
        val icon = item?.icon
        when {
            banner != null -> {
                art.setPadding(0, 0, 0, 0)
                art.scaleType = ImageView.ScaleType.CENTER_CROP
                art.setImageDrawable(fresh(banner))
            }
            icon != null -> {
                // No banner: show the icon on the right, clear of the text block.
                art.setPadding((220 * d).toInt(), (40 * d).toInt(), (48 * d).toInt(), (40 * d).toInt())
                art.scaleType = ImageView.ScaleType.FIT_CENTER
                art.setImageDrawable(fresh(icon))
            }
            else -> {
                art.setPadding(0, 0, 0, 0)
                art.scaleType = ImageView.ScaleType.CENTER_CROP
                art.setImageResource(R.drawable.hero_art)
            }
        }
    }

    private fun buildDots() {
        val dots = findViewById<LinearLayout>(R.id.hero_dots)
        dots.removeAllViews()
        val n = heroItems.size
        findViewById<TextView>(R.id.hero_counter).text = if (n > 0) "${heroIndex + 1} / $n" else ""
        repeat(n) { i ->
            val active = i == heroIndex
            dots.addView(View(this).apply {
                setBackgroundResource(if (active) R.drawable.dot_active else R.drawable.dot_idle)
                layoutParams = LinearLayout.LayoutParams(((if (active) 14 else 4) * d).toInt(), (4 * d).toInt())
                    .apply { marginStart = (3 * d).toInt() }
            })
        }
    }

    /** Ken Burns: very slow zoom + pan of the hero artwork. */
    private fun startKenBurns() {
        kenBurns?.let { if (it.isPaused) { it.resume(); return } }
        val art = findViewById<View>(R.id.hero_art)
        kenBurns = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                val e = (1 - kotlin.math.cos(t * Math.PI).toFloat()) / 2f // ease in-out
                art.scaleX = 1.02f + 0.08f * e
                art.scaleY = art.scaleX
                art.translationX = -14 * d * e
                art.translationY = -6 * d * e
            }
            start()
        }
    }

    // ---------------------------------------------------------------- quick access

    private fun setupQuickAccess() {
        bindQuick(R.id.qa_settings, R.drawable.kic_settings, R.string.qa_settings) {
            openFirst(Intent(Settings.ACTION_SETTINGS))
        }.setOnLongClickListener { openLauncherSettings(); true }
        bindQuick(R.id.qa_search, R.drawable.kic_search, R.string.qa_search) { openSearch() }
        bindQuick(R.id.qa_display, R.drawable.kic_tv, R.string.qa_display) {
            openFirst(Intent(Settings.ACTION_DISPLAY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        }

        val volume = findViewById<View>(R.id.volume_box)
        springOnFocus(volume, 1.03f)
        volume.setOnKeyListener { _, keyCode, event ->
            if (audio.isVolumeFixed || event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val dir = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> AudioManager.ADJUST_LOWER
                KeyEvent.KEYCODE_DPAD_RIGHT -> AudioManager.ADJUST_RAISE
                else -> return@setOnKeyListener false
            }
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
            refreshVolume(animate = true)
            true
        }

        val airplay = findViewById<View>(R.id.row_airplay)
        airplay.findViewById<ImageView>(R.id.sr_icon).setImageResource(R.drawable.kic_cast_connected)
        airplay.findViewById<TextView>(R.id.sr_title).setText(R.string.airplay)
        airplay.setOnClickListener {
            val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(AIRSCREEN)
                ?: packageManager.getLaunchIntentForPackage(AIRSCREEN)
            if (launchIntent != null) safeStart(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else openFirst(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$AIRSCREEN")))
        }
        val storage = findViewById<View>(R.id.row_storage)
        storage.findViewById<ImageView>(R.id.sr_icon).setImageResource(R.drawable.kic_storage)
        storage.findViewById<TextView>(R.id.sr_title).setText(R.string.storage)
        storage.setOnClickListener {
            openFirst(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        }
        listOf(airplay, storage).forEach { springOnFocus(it, 1.03f) }
    }

    private fun bindQuick(id: Int, icon: Int, label: Int, action: () -> Unit): View {
        val v = findViewById<View>(id)
        v.findViewById<ImageView>(R.id.qt_icon).setImageResource(icon)
        v.findViewById<TextView>(R.id.qt_label).setText(label)
        v.setOnClickListener { action() }
        springOnFocus(v, 1.08f)
        return v
    }

    private fun refreshStatus() {
        refreshNetwork()
        refreshVolume(animate = false)
        // AirPlay receiver (AirScreen)
        val installed = runCatching { packageManager.getApplicationInfo(AIRSCREEN, 0) }.isSuccess
        val airplay = findViewById<View>(R.id.row_airplay)
        airplay.findViewById<TextView>(R.id.sr_sub)
            .setText(if (installed) R.string.airplay_ready else R.string.airplay_missing)
        val dot = airplay.findViewById<View>(R.id.sr_dot)
        dot.visibility = if (installed) View.VISIBLE else View.GONE
        if (installed) ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.3f).apply {
            duration = 1200; repeatMode = ValueAnimator.REVERSE; repeatCount = 4; start()
        }
        // Storage
        val storage = findViewById<View>(R.id.row_storage)
        runCatching {
            val st = StatFs(Environment.getDataDirectory().path)
            val total = st.totalBytes; val free = st.availableBytes
            storage.findViewById<TextView>(R.id.sr_sub).text = getString(R.string.storage_value,
                Formatter.formatShortFileSize(this, free), Formatter.formatShortFileSize(this, total))
            val bar = storage.findViewById<View>(R.id.sr_bar)
            bar.visibility = View.VISIBLE
            val fill = storage.findViewById<View>(R.id.sr_bar_fill)
            val frac = if (total > 0) (total - free).toFloat() / total else 0f
            fill.layoutParams = fill.layoutParams.apply { width = (32 * d * frac).toInt() }
        }
    }

    private fun refreshVolume(animate: Boolean) {
        val value = findViewById<TextView>(R.id.volume_value)
        val hint = findViewById<TextView>(R.id.volume_hint)
        val track = findViewById<View>(R.id.volume_track)
        val fill = findViewById<View>(R.id.volume_fill)
        val thumb = findViewById<View>(R.id.volume_thumb)
        val fixed = audio.isVolumeFixed
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val frac = if (fixed) 1f else cur.toFloat() / max
        value.text = if (fixed) "TV" else "${(frac * 100).toInt()}%"
        hint.setText(if (fixed) R.string.volume_fixed else R.string.volume_hint)
        track.post {
            val w = track.width
            if (w == 0) return@post
            val target = (w * frac).toInt()
            val thumbX = (w * frac - thumb.width / 2f).coerceIn(0f, (w - thumb.width).toFloat())
            if (!animate) {
                fill.layoutParams = fill.layoutParams.apply { width = target }
                thumb.translationX = thumbX
                return@post
            }
            ValueAnimator.ofInt(fill.width, target).apply {
                duration = 180
                addUpdateListener { a -> fill.layoutParams = fill.layoutParams.apply { width = a.animatedValue as Int } }
                start()
            }
            thumb.animate().translationX(thumbX).setDuration(180).start()
        }
    }

    // ---------------------------------------------------------------- footer

    private fun setupFooter() {
        val box = findViewById<LinearLayout>(R.id.footer_hints)
        fun hint(icon: Int?, chip: String?, label: String) {
            val v = LayoutInflater.from(this).inflate(R.layout.item_key_hint, box, false)
            val iv = v.findViewById<ImageView>(R.id.kh_icon)
            val tv = v.findViewById<TextView>(R.id.kh_text)
            if (icon != null) iv.setImageResource(icon) else iv.visibility = View.GONE
            if (chip != null) { tv.text = chip; tv.visibility = View.VISIBLE }
            v.findViewById<TextView>(R.id.kh_label).text = label
            box.addView(v)
        }
        hint(R.drawable.kic_arrow_back, null, getString(R.string.hint_back))
        hint(null, "OK", getString(R.string.hint_ok))
        hint(R.drawable.kic_menu, null, "${getString(R.string.hint_hold)} / ${getString(R.string.hint_menu)} · ${getString(R.string.hint_options)}")
        hint(R.drawable.kic_search, null, getString(R.string.hint_search))
    }

    // ---------------------------------------------------------------- animations

    /** Cascading entrance: every section fades/slides up with a small delay. */
    private fun playEntrance() {
        val content = findViewById<ViewGroup>(R.id.home_content)
        val targets = (0 until content.childCount).map { content.getChildAt(it) } +
            listOf((findViewById<View>(R.id.footer_hints).parent as View).parent as View)
        targets.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 22 * d
            v.animate().alpha(1f).translationY(0f).setStartDelay(120L + i * 80L).setDuration(560)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    /** Springy scale on focus for pills / small tiles. */
    private fun springOnFocus(v: View, scale: Float) {
        val previous = v.onFocusChangeListener
        v.setOnFocusChangeListener { view, has ->
            previous?.onFocusChange(view, has)
            view.animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f)
                .setDuration(if (has) 300 else 180)
                .setInterpolator(if (has) OvershootInterpolator(2.2f) else DecelerateInterpolator())
                .start()
        }
    }

    // ---------------------------------------------------------------- actions dialog

    private fun showActions(item: AppItem) {
        dialog?.dismiss()
        val inMyRow = myAdapter.currentItems.any { it.packageName == item.packageName }
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_app_actions, null)
        view.findViewById<ImageView>(R.id.dlg_icon).setImageDrawable(item.icon)
        view.findViewById<TextView>(R.id.dlg_title).text = item.label
        view.findViewById<TextView>(R.id.dlg_sub)
            .setText(if (item.isSystem) R.string.act_system_note else R.string.act_user_note)
        val list = view.findViewById<LinearLayout>(R.id.dlg_actions)

        val dlg = Dialog(this)
        fun action(icon: Int, label: Int, run: () -> Unit): View {
            val row = LayoutInflater.from(this).inflate(R.layout.item_dialog_action, list, false)
            row.findViewById<ImageView>(R.id.da_icon).setImageResource(icon)
            row.findViewById<TextView>(R.id.da_label).setText(label)
            row.setOnClickListener { dlg.dismiss(); run() }
            springOnFocus(row, 1.04f)
            list.addView(row)
            return row
        }
        val first = action(R.drawable.kic_open_in_new, R.string.act_open) { launch(item) }
        if (inMyRow) action(R.drawable.kic_menu, R.string.act_move) { startMove(item) }
        action(R.drawable.kic_add, R.string.act_pin) { pinToStart(item) }
        action(R.drawable.kic_info, R.string.act_info) { openAppInfo(item) }
        if (item.isSystem) action(R.drawable.kic_delete, R.string.act_disable) { openAppInfo(item) }
        else action(R.drawable.kic_delete, R.string.act_uninstall) {
            safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${item.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dlg.setContentView(view)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.55f)
        }
        dlg.setOnShowListener {
            val root = view.findViewById<View>(R.id.dlg_root)
            root.alpha = 0f; root.scaleX = 0.88f; root.scaleY = 0.88f; root.translationY = 12 * d
            root.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(320)
                .setInterpolator(OvershootInterpolator(1.4f)).start()
            for (i in 0 until list.childCount) {
                val c = list.getChildAt(i)
                c.alpha = 0f; c.translationX = 14 * d
                c.animate().alpha(1f).translationX(0f).setStartDelay(90L + i * 45L).setDuration(260).start()
            }
            first.requestFocus()
        }
        dlg.setOnDismissListener { if (dialog === dlg) dialog = null }
        dialog = dlg
        dlg.show()
    }

    private fun pinToStart(item: AppItem) {
        val current = myAdapter.currentItems.map { it.packageName }.filter { it != item.packageName }
        prefs.appOrder = listOf(item.packageName) + current
        bindRows()
        myRow.selectedPosition = 0
        myRow.requestFocus()
    }

    private fun startMove(item: AppItem) {
        val pos = myAdapter.currentItems.indexOfFirst { it.packageName == item.packageName }
        if (pos < 0) return
        myRow.selectedPosition = pos
        myRow.requestFocus()
        myAdapter.movingPackage = item.packageName
        updateMyAppsMeta()
    }

    private fun finishMove() {
        myAdapter.movingPackage = null
        prefs.appOrder = myAdapter.currentItems.map { it.packageName }
        updateMyAppsMeta()
        Toast.makeText(this, R.string.move_saved, Toast.LENGTH_SHORT).show()
    }

    private fun openAppInfo(item: AppItem) = safeStart(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${item.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    // ---------------------------------------------------------------- navigation helpers

    private fun launch(item: AppItem) {
        if (safeStart(item.launchIntent)) prefs.markLaunched(item.packageName)
    }

    private fun openSearch() = safeStart(Intent(this, SearchActivity::class.java))

    private fun openLauncherSettings() = safeStart(Intent(this, SettingsActivity::class.java))

    private fun openFirst(vararg intents: Intent) {
        for (i in intents) if (safeStart(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), quiet = true)) return
        Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show()
    }

    private fun safeStart(intent: Intent, quiet: Boolean = false): Boolean = try {
        startActivity(intent); true
    } catch (e: Exception) {
        if (!quiet) Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show()
        false
    }

    private fun appVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull() ?: "1.0"

    private companion object {
        const val HERO_INTERVAL = 8000L
        const val MOVE_STEP_MS = 220L
        const val AIRSCREEN = "com.ionitech.airscreen"
        val OPTION_KEYS = setOf(
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_GUIDE,
        )
    }
}