package com.mistick.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.SearchOrbView
import androidx.leanback.widget.TitleViewAdapter

/**
 * Home screen header: search orb + title on the left, live clock and date on the right
 * (TextClock updates itself every minute). Plugged into BrowseSupportFragment via
 * [TitleViewAdapter.Provider].
 */
class HomeHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), TitleViewAdapter.Provider {

    private val searchOrb: SearchOrbView by lazy { findViewById(R.id.header_search_orb) }
    private val titleView: TextView by lazy { findViewById(R.id.header_title) }

    private val adapter = object : TitleViewAdapter() {
        override fun getSearchAffordanceView(): View = searchOrb

        override fun setTitle(titleText: CharSequence?) {
            if (!titleText.isNullOrEmpty()) titleView.text = titleText
        }

        override fun setBadgeDrawable(drawable: Drawable?) = Unit

        override fun setOnSearchClickedListener(listener: OnClickListener?) {
            searchOrb.setOnOrbClickedListener(listener)
        }

        override fun setSearchAffordanceColors(colors: SearchOrbView.Colors) {
            searchOrb.orbColors = colors
        }

        override fun updateComponentsVisibility(flags: Int) {
            searchOrb.visibility =
                if (flags and SEARCH_VIEW_VISIBLE != 0) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun getTitleViewAdapter(): TitleViewAdapter = adapter
}
