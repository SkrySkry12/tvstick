package com.mistick.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter

/**
 * Renders an [AppItem] as a rounded 16:9 card: banner (if available) or a large
 * centered icon, plus the app name. Focused cards scale up with an accent outline.
 */
class AppCardPresenter : Presenter() {

    var preferBanners: Boolean = true

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_card, parent, false)
        val frame = view.findViewById<View>(R.id.card_frame)
        val label = view.findViewById<TextView>(R.id.card_label)
        frame.clipToOutline = true // rounded corners for banners/icons
        val res = parent.resources
        val focusedScale = res.getFraction(R.fraction.card_focus_scale, 1, 1)
        val duration = res.getInteger(R.integer.card_anim_duration).toLong()
        val interpolator = DecelerateInterpolator()

        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) focusedScale else 1f
            v.animate().scaleX(scale).scaleY(scale)
                .setDuration(duration).setInterpolator(interpolator).start()
            frame.isSelected = hasFocus
            label.setTextColor(
                ContextCompat.getColor(
                    v.context, if (hasFocus) R.color.text_primary else R.color.text_secondary
                )
            )
            v.translationZ = if (hasFocus) 8f else 0f
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val app = item as? AppItem ?: return
        val root = viewHolder.view
        val bannerView = root.findViewById<ImageView>(R.id.card_banner)
        val iconView = root.findViewById<ImageView>(R.id.card_icon)
        val label = root.findViewById<TextView>(R.id.card_label)

        label.text = app.label
        root.contentDescription = app.label

        val banner = if (preferBanners) app.banner else null
        if (banner != null) {
            bannerView.setImageDrawable(banner)
            bannerView.visibility = View.VISIBLE
            iconView.visibility = View.GONE
        } else {
            iconView.setImageDrawable(app.icon)
            iconView.visibility = View.VISIBLE
            bannerView.visibility = View.GONE
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.findViewById<ImageView>(R.id.card_banner).setImageDrawable(null)
        viewHolder.view.findViewById<ImageView>(R.id.card_icon).setImageDrawable(null)
    }
}
