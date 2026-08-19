package com.example.ramcleaner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Gráfico simple de área/línea con el historial reciente de % de RAM usada.
 * No necesita Shizuku: usa ActivityManager.MemoryInfo, que cualquier app
 * puede leer sin permisos especiales.
 */
class RamGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val samples = ArrayDeque<Float>()
    private val maxSamples = 40

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3338BDF8")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22000000")
        strokeWidth = 1f
    }

    fun addSample(percentUsed: Float) {
        samples.addLast(percentUsed.coerceIn(0f, 100f))
        while (samples.size > maxSamples) samples.removeFirst()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // líneas guía cada 25%
        for (i in 0..4) {
            val y = h - (h * i / 4f)
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        if (samples.size < 2) return

        val stepX = w / (maxSamples - 1).toFloat()
        val startIndexOffset = maxSamples - samples.size

        val path = Path()
        val fillPath = Path()

        samples.forEachIndexed { index, value ->
            val x = (startIndexOffset + index) * stepX
            val y = h - (h * (value / 100f))
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        val lastX = (startIndexOffset + samples.size - 1) * stepX
        fillPath.lineTo(lastX, h)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
