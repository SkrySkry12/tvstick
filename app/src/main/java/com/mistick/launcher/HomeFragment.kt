package com.mistick.launcher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Main browse screen: header with clock/date + search, and rows of app cards.
 */
class HomeFragment : BrowseSupportFragment() {

    private lateinit var prefs: LauncherPrefs
    private val cardPresenter = AppCardPresenter()
    private val rowsAdapter = ArrayObjectAdapter(
        ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE).apply {
            shadowEnabled = false
            setSelectEffectEnabled(false)
        }
    )
    private var loadJob: Job? = null
    private var lastSignature: String? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(requireContext())

        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = ContextCompat.getColor(requireContext(), R.color.background)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.accent)
        adapter = rowsAdapter

        setOnSearchClickedListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            (item as? AppItem)?.let { launch(it) }
        }
    }

    /** Custom header (clock, date, search orb) instead of the default leanback TitleView. */
    override fun onInflateTitleView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_home, parent, false)

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            requireContext(), packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        reloadApps()
    }

    override fun onStop() {
        requireContext().unregisterReceiver(packageReceiver)
        super.onStop()
    }

    fun scrollToTop() {
        if (rowsAdapter.size() > 0) setSelectedPosition(0, true)
    }

    private fun reloadApps() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwnerOrSelf().lifecycleScope.launch {
            val apps = AppRepository.loadApps(requireContext().applicationContext)
            buildRows(apps)
        }
    }

    private fun viewLifecycleOwnerOrSelf() =
        if (view != null) viewLifecycleOwner else this

    private fun buildRows(apps: List<AppItem>) {
        // Skip rebuilding (and losing focus) when nothing changed since the last load.
        val signature = buildString {
            append(prefs.showSystemInMyApps).append(prefs.showAllAppsRow).append(prefs.preferBanners)
            apps.forEach { append('|').append(it.packageName).append(it.label) }
        }
        if (signature == lastSignature) return
        lastSignature = signature

        cardPresenter.preferBanners = prefs.preferBanners
        rowsAdapter.clear()
        var id = 0L

        val myApps = if (prefs.showSystemInMyApps) apps else apps.filterNot { it.isSystem }
        if (myApps.isNotEmpty()) {
            rowsAdapter.add(row(id++, getString(R.string.row_my_apps), myApps))
        }
        if (prefs.showAllAppsRow && apps.isNotEmpty()) {
            rowsAdapter.add(row(id++, getString(R.string.row_all_apps), apps))
        }
        rowsAdapter.add(row(id, getString(R.string.row_settings), settingsShortcuts()))
    }

    private fun row(id: Long, title: String, items: List<AppItem>): ListRow {
        val listAdapter = ArrayObjectAdapter(cardPresenter).apply { addAll(0, items) }
        return ListRow(HeaderItem(id, title), listAdapter)
    }

    private fun settingsShortcuts(): List<AppItem> {
        val ctx = requireContext()
        return listOf(
            AppItem(
                label = getString(R.string.shortcut_launcher_settings),
                packageName = "",
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_tune),
                banner = null,
                launchIntent = Intent(ctx, SettingsActivity::class.java),
            ),
            AppItem(
                label = getString(R.string.shortcut_system_settings),
                packageName = "",
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_settings),
                banner = null,
                launchIntent = Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ),
        )
    }

    private fun launch(item: AppItem) {
        try {
            startActivity(item.launchIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.error_cannot_launch, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), R.string.error_cannot_launch, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val TAG = "HomeFragment"
    }
}
