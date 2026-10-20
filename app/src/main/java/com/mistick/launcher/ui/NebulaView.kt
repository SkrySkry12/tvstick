package com.mistick.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Slowly drifting deep-space background: base gradient, bottle-green nebulae, stars,
 * a perspective grid and a vignette. Coordinates are authored on a 1920×1080
 * canvas (same as the design) and scaled to the real view size.
 */
class NebulaView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private data class Blob(val cx: Float, val cy: Float, val w: Float, val h: Float, val color: Int, val alpha: Float)

    private val blobs = listOf(
        Blob(230f, 90f, 1300f, 1000f, 0x1B4D3E, 0.70f),
        Blob(1020f, -30f, 1100f, 900f, 0x2D6A4F, 0.45f),
        Blob(1770f, 460f, 1000f, 820f, 0x40916C, 0.28f),
        Blob(800f, 1150f, 1400f, 760f, 0x1B4D3E, 0.65f),
        Blob(1500f, 840f, 560f, 560f, 0x00C896, 0.09f),
    )

    private val basePaint = Paint()
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f; color = Color.WHITE }
    private val vignettePaint = Paint()
    private val blobShaders = ArrayList<Pair<RadialGradient, Blob>>()
    private val stars = ArrayList<FloatArray>() // x, y, radius, alpha (design coords)

    // Slow nebula drift: redraw at ~20 fps only while visible (cheap on the Mi Stick).
    private var phase = 0f
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            phase += 0.05f / 20f * 6.2832f / 3f // full cycle ≈ 60 s
            invalidate()
            if (running) postDelayed(this, 50)
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        running = visibility == VISIBLE
        removeCallbacks(tick)
        if (running) postDelayed(tick, 50)
    }

    override fun onDetachedFromWindow() {
        running = false; removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private val tmpMatrix = Matrix()

    init {
        val rnd = Random(42)
        repeat(150) {
            stars += floatArrayOf(
                rnd.nextFloat() * 1920f, rnd.nextFloat() * 1080f * 0.8f,
                0.6f + rnd.nextFloat() * 0.9f, 0.15f + rnd.nextFloat() * 0.45f,
            )
        }
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        basePaint.shader = RadialGradient(
            w / 2f, 0f, w * 1.2f,
            intArrayOf(0xFF0A1A12.toInt(), 0xFF040A07.toInt(), 0xFF020504.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        val s = w / 1920f
        blobShaders.clear()
        for (b in blobs) {
            val a = (b.alpha * 255).toInt().coerceIn(0, 255)
            val c = b.color or (a shl 24)
            val mid = b.color or ((a * 0.45f).toInt() shl 24)
            val g = RadialGradient(0f, 0f, 1f, intArrayOf(c, mid, b.color and 0x00FFFFFF),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            g.setLocalMatrix(Matrix().apply {
                setScale(b.w / 2f * s, b.h / 2f * s)
                postTranslate(b.cx * s, b.cy * s)
            })
            blobShaders += g to b
        }
        vignettePaint.shader = RadialGradient(
            w / 2f, h * 0.4f, w * 0.75f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, 0xA6000000.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        gridPaint.shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(),
            Color.TRANSPARENT, 0x14FFFFFF, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val s = w / 1920f
        canvas.drawRect(0f, 0f, w, h, basePaint)

        // Stars (furthest layer)
        for (st in stars) {
            starPaint.alpha = (st[3] * 255).toInt()
            canvas.drawCircle(st[0] * s, st[1] * s, st[2] * s, starPaint)
        }

        // Perspective grid floor
        val horizon = h * 0.45f
        val vx = w / 2f
        var i = -14
        while (i <= 14) {
            canvas.drawLine(vx + i * 40f * s, horizon, vx + i * 260f * s, h, gridPaint)
            i++
        }
        var t = 0.04f
        while (t < 1f) {
            val y = horizon + (h - horizon) * t * t
            canvas.drawLine(0f, y, w, y, gridPaint)
            t += 0.085f
        }

        // Nebulae
        for ((idx, pair) in blobShaders.withIndex()) {
            val (shader, b) = pair
            val dx = kotlin.math.sin(phase + idx * 1.7f) * 60f * s
            val dy = kotlin.math.cos(phase * 0.8f + idx * 2.3f) * 35f * s
            val breathe = 1f + 0.06f * kotlin.math.sin(phase * 1.3f + idx)
            tmpMatrix.setScale(b.w / 2f * s * breathe, b.h / 2f * s * breathe)
            tmpMatrix.postTranslate(b.cx * s + dx, b.cy * s + dy)
            shader.setLocalMatrix(tmpMatrix)
            blobPaint.shader = shader
            val hw = b.w / 2 * s * breathe; val hh = b.h / 2 * s * breathe
            canvas.drawOval(b.cx * s + dx - hw, b.cy * s + dy - hh, b.cx * s + dx + hw, b.cy * s + dy + hh, blobPaint)
        }
        canvas.drawRect(0f, 0f, w, h, vignettePaint)
    }
}
