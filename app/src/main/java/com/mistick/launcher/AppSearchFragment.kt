package com.mistick.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

/**
 * Search over installed apps. Matching is case- and diacritics-insensitive
 * ("zdjecia" finds "Zdjęcia"). Voice input uses the system recognizer, so the
 * launcher itself never needs the microphone permission.
 */
class AppSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val cardPresenter = AppCardPresenter()
    private var allApps: List<AppItem> = emptyList()
    private var pendingQuery = ""

    private val voiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) setSearchQuery(result.data, true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        title = getString(R.string.search_hint)

        @Suppress("DEPRECATION")
        setSpeechRecognitionCallback {
            try {
                voiceLauncher.launch(recognizerIntent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.error_no_voice, Toast.LENGTH_SHORT).show()
            }
        }

        setOnItemViewClickedListener(OnItemViewClickedListener { _, item, _, _ ->
            val app = item as? AppItem ?: return@OnItemViewClickedListener
            try {
                startActivity(app.launchIntent)
                activity?.finish()
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.error_cannot_launch, Toast.LENGTH_SHORT).show()
            }
        })

        lifecycleScope.launch {
            allApps = AppRepository.loadApps(requireContext().applicationContext)
            cardPresenter.preferBanners = LauncherPrefs(requireContext()).preferBanners
            filter(pendingQuery)
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String): Boolean {
        filter(newQuery)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        filter(query)
        return true
    }

    private fun filter(query: String) {
        pendingQuery = query
        rowsAdapter.clear()
        val needle = normalize(query)
        if (needle.isEmpty()) return

        val matches = allApps.filter {
            normalize(it.label).contains(needle) || it.packageName.lowercase(Locale.ROOT).contains(needle)
        }
        val header = if (matches.isEmpty()) {
            HeaderItem(getString(R.string.search_no_results, query))
        } else {
            HeaderItem(getString(R.string.search_results, matches.size))
        }
        rowsAdapter.add(ListRow(header, ArrayObjectAdapter(cardPresenter).apply { addAll(0, matches) }))
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace('ł', 'l')
}
