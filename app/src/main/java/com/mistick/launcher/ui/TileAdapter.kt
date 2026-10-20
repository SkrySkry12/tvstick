package com.mistick.launcher.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mistick.launcher.AppItem
import com.mistick.launcher.R

/**
 * Glass app tiles with the "lifted + green glow" focus effect from the design.
 * Supports a "move mode" in which the focused tile pulses and can be dragged with ◀ ▶.
 */
class TileAdapter(
    private val onClick: (AppItem) -> Unit,
    private val onLongClick: (AppItem) -> Unit,
) : RecyclerView.Adapter<TileAdapter.Holder>() {

    var preferBanners = true
    private val items = ArrayList<AppItem>()

    /** Package currently being moved, or null. */
    var movingPackage: String? = null
        set(value) {
            val old = field
            field = value
            items.indexOfFirst { it.packageName == old }.takeIf { it >= 0 }?.let { notifyItemChanged(it, PAYLOAD_STATE) }
            items.indexOfFirst { it.packageName == value }.takeIf { it >= 0 }?.let { notifyItemChanged(it, PAYLOAD_STATE) }
        }

    val currentItems: List<AppItem> get() = items

    fun submit(newItems: List<AppItem>) {
        items.clear(); items.addAll(newItems)
        notifyDataSetChanged()
    }

    /** Swaps the moving item with its neighbour. Returns the new index or -1. */
    fun moveBy(delta: Int): Int {
        val from = items.indexOfFirst { it.packageName == movingPackage }
        val to = from + delta
        if (from < 0 || to < 0 || to >= items.size) return -1
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
        return to
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_STATE)) holder.refresh() else holder.bind(items[position])
    }

    override fun onViewRecycled(holder: Holder) = holder.stopLoops()

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val d = view.resources.displayMetrics.density
        private val card: View = view.findViewById(R.id.tile_card)
        private val glow: View = view.findViewById(R.id.tile_glow)
        private val banner: ImageView = view.findViewById(R.id.tile_banner)
        private val icon: ImageView = view.findViewById(R.id.tile_icon)
        private val sheen: View = view.findViewById(R.id.tile_sheen)
        private val label: TextView = view.findViewById(R.id.tile_label)
        private val indicator: View = view.findViewById(R.id.tile_indicator)
        private var anim: AnimatorSet? = null
        private var breathe: ValueAnimator? = null
        private var item: AppItem? = null

        init {
            card.clipToOutline = true
            view.setOnFocusChangeListener { _, has -> applyState(has, animate = true) }
        }

        fun bind(item: AppItem) {
            this.item = item
            val useBanner = preferBanners && item.banner != null
            banner.visibility = if (useBanner) View.VISIBLE else View.GONE
            icon.visibility = if (useBanner) View.GONE else View.VISIBLE
            if (useBanner) banner.setImageDrawable(item.banner) else icon.setImageDrawable(item.icon)
            label.text = item.label
            itemView.contentDescription = item.label
            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener { onLongClick(item); true }
            applyState(itemView.isFocused, animate = false)
        }

        fun refresh() = applyState(itemView.isFocused, animate = true)

        private val isMoving get() = item != null && item?.packageName == movingPackage

        fun stopLoops() {
            breathe?.cancel(); breathe = null
            anim?.cancel()
        }

        private fun applyState(focused: Boolean, animate: Boolean) {
            card.isSelected = focused
            label.setTextColor(if (focused) 0xFFFFFFFF.toInt() else 0x8CFFFFFF.toInt())
            label.textSize = if (focused) 9f else 8f
            itemView.translationZ = if (focused) 10f else 0f

            val moving = focused && isMoving
            val scale = when { moving -> 1.18f; focused -> 1.12f; else -> 1f }
            val lift = when { moving -> -10f * d; focused -> -5f * d; else -> 0f }
            val alpha = if (focused) 1f else 0f
            val labelDy = if (focused) 6f * d else 0f
            anim?.cancel()
            breathe?.cancel(); breathe = null
            if (!animate) {
                listOf(card, glow).forEach { it.scaleX = scale; it.scaleY = scale; it.translationY = lift }
                glow.alpha = alpha; sheen.alpha = alpha; indicator.alpha = alpha
                label.translationY = labelDy; indicator.translationY = labelDy
                if (focused) startBreathing(moving)
                return
            }
            anim = AnimatorSet().apply {
                duration = if (focused) 320 else 200
                // Springy grow on focus, smooth settle on blur.
                interpolator = if (focused) OvershootInterpolator(1.6f) else DecelerateInterpolator(1.6f)
                playTogether(
                    ObjectAnimator.ofFloat(card, View.SCALE_X, scale),
                    ObjectAnimator.ofFloat(card, View.SCALE_Y, scale),
                    ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, lift),
                    ObjectAnimator.ofFloat(glow, View.SCALE_X, scale),
                    ObjectAnimator.ofFloat(glow, View.SCALE_Y, scale),
                    ObjectAnimator.ofFloat(glow, View.TRANSLATION_Y, lift),
                    ObjectAnimator.ofFloat(glow, View.ALPHA, alpha),
                    ObjectAnimator.ofFloat(sheen, View.ALPHA, alpha),
                    ObjectAnimator.ofFloat(indicator, View.ALPHA, alpha),
                    ObjectAnimator.ofFloat(label, View.TRANSLATION_Y, labelDy),
                    ObjectAnimator.ofFloat(indicator, View.TRANSLATION_Y, labelDy),
                )
                if (focused) doOnEndCompat { if (itemView.isFocused) startBreathing(moving) }
                start()
            }
        }

        /** Slow "breathing" of the green glow (fast pulse while moving). */
        private fun startBreathing(moving: Boolean) {
            breathe?.cancel()
            breathe = ValueAnimator.ofFloat(1f, if (moving) 0.35f else 0.6f).apply {
                duration = if (moving) 520 else 1600
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { glow.alpha = it.animatedValue as Float }
                start()
            }
        }
    }

    private inline fun AnimatorSet.doOnEndCompat(crossinline block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
            override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) block() }
        })
    }

    companion object { private const val PAYLOAD_STATE = "state" }
}
