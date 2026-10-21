package com.example.ramcleaner

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

/**
 * Gráfico simple de RAM usada en tiempo real: una línea que se va
 * dibujando de derecha a izquierda, estilo monitor de sistema. Se
 * actualiza solo mientras la vista está adjunta a la pantalla (se
 * detiene automáticamente al salir de la pantalla, para no gastar
 * batería de fondo).
 */
class RamGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val maxPoints = 60
    private val values = ArrayDeque<Float>()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#A855F7")
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#332A1B4D")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    var percentText: ((Int) -> Unit)? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            val percent = readUsedRamPercent()
            addValue(percent.toFloat())
            percentText?.invoke(percent)
            if (running) handler.postDelayed(this, 1500L)
        }
    }

    private fun readUsedRamPercent(): Int {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val used = info.totalMem - info.availMem
            ((used.toDouble() / info.totalMem.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            0
        }
    }

    private fun addValue(v: Float) {
        values.addLast(v)
        while (values.size > maxPoints) values.removeFirst()
        invalidate()
    }

    fun start() {
        if (running) return
        running = true
        handler.post(updateRunnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(updateRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Líneas de guía horizontales (25/50/75%)
        for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
            val y = h - (h * fraction)
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        if (values.size < 2) return

        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.parseColor("#55A855F7"), Color.parseColor("#00A855F7"),
            Shader.TileMode.CLAMP
        )

        val stepX = w / (maxPoints - 1).toFloat()
        val startIndex = maxPoints - values.size

        val linePath = Path()
        val fillPath = Path()
        var first = true

        values.forEachIndexed { i, value ->
            val x = (startIndex + i) * stepX
            val y = h - (h * (value / 100f))
            if (first) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
                first = false
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        val lastX = (startIndex + values.size - 1) * stepX
        fillPath.lineTo(lastX, h)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }
}
