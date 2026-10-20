package com.mistick.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Soft bottle-green halo drawn *outside* a rounded rectangle inset by [inset].
 * The shape itself is clipped out, so only the glow is visible around the card.
 */
class GlowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    var inset = 20f * d
    var radius = 12f * d

    private val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A1A13.toInt()
        setShadowLayer(16f * d, 0f, 0f, 0x9900C896.toInt())
    }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A1A13.toInt()
        setShadowLayer(6f * d, 0f, 2f * d, 0x8040916C.toInt())
    }
    private val rect = RectF()
    private val path = Path()

    init { alpha = 0f }

    override fun onDraw(canvas: Canvas) {
        rect.set(inset, inset, width - inset, height - inset)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipOutPath(path)
        canvas.drawRoundRect(rect, radius, radius, outer)
        canvas.drawRoundRect(rect, radius, radius, inner)
        canvas.restore()
    }
}
