package com.example.multifactorauthapp

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class PatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val gridSize = 3
    private val dots = mutableListOf<PointF>()
    private val selectedDots = mutableListOf<Int>()
    private val timestamps = mutableListOf<Long>()

    private val dotRadius = 18f
    private val ringRadius = 36f
    private val hitRadius = 50f

    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false

    var onPatternComplete: ((List<Int>, List<Long>) -> Unit)? = null

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        dots.clear()
        val gapX = w / (gridSize + 1f)
        val gapY = h / (gridSize + 1f)

        for (r in 1..gridSize) {
            for (c in 1..gridSize) {
                dots.add(PointF(c * gapX, r * gapY))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (i in 0 until selectedDots.size - 1) {
            val p1 = dots[selectedDots[i]]
            val p2 = dots[selectedDots[i + 1]]
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)
        }

        if (isDrawing && selectedDots.isNotEmpty()) {
            val last = dots[selectedDots.last()]
            canvas.drawLine(last.x, last.y, currentX, currentY, linePaint)
        }

        dots.forEachIndexed { i, p ->
            canvas.drawCircle(p.x, p.y, dotRadius, dotPaint)
            if (selectedDots.contains(i)) {
                canvas.drawCircle(p.x, p.y, ringRadius, ringPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        currentX = event.x
        currentY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                isDrawing = true
                detectDot(currentX, currentY)
            }
            MotionEvent.ACTION_UP -> {
                isDrawing = false
                performClick()
                onPatternComplete?.invoke(
                    selectedDots.toList(),
                    timestamps.toList()
                )
            }
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun detectDot(x: Float, y: Float) {
        dots.forEachIndexed { index, p ->
            if (!selectedDots.contains(index)) {
                if (hypot(x - p.x, y - p.y) < hitRadius) {
                    selectedDots.add(index)
                    timestamps.add(SystemClock.elapsedRealtime())
                }
            }
        }
    }

    fun clearPattern() {
        selectedDots.clear()
        timestamps.clear()
        invalidate()
    }
}
