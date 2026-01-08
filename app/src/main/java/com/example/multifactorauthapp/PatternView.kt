package com.example.multifactorauthapp

import android.content.Context
import android.graphics.*
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

    private val dotRadius = 18f
    private val ringRadius = 36f
    private val hitRadius = 50f

    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false

    var onPatternComplete: ((List<Int>) -> Unit)? = null

    // Paints
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
        super.onSizeChanged(w, h, oldw, oldh)
        dots.clear()

        val gapX = w / (gridSize + 1f)
        val gapY = h / (gridSize + 1f)

        for (row in 1..gridSize) {
            for (col in 1..gridSize) {
                dots.add(PointF(col * gapX, row * gapY))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw connecting lines
        for (i in 0 until selectedDots.size - 1) {
            val p1 = dots[selectedDots[i]]
            val p2 = dots[selectedDots[i + 1]]
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)
        }

        // Draw finger-following line
        if (isDrawing && selectedDots.isNotEmpty()) {
            val last = dots[selectedDots.last()]
            canvas.drawLine(last.x, last.y, currentX, currentY, linePaint)
        }

        // Draw dots + rings
        dots.forEachIndexed { index, point ->
            canvas.drawCircle(point.x, point.y, dotRadius, dotPaint)

            if (selectedDots.contains(index)) {
                canvas.drawCircle(point.x, point.y, ringRadius, ringPaint)
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
                onPatternComplete?.invoke(selectedDots.toList())
            }
        }
        invalidate()
        return true
    }

    private fun detectDot(x: Float, y: Float) {
        dots.forEachIndexed { index, point ->
            if (!selectedDots.contains(index)) {
                val d = hypot(x - point.x, y - point.y)
                if (d < hitRadius) {
                    selectedDots.add(index)
                }
            }
        }
    }

    fun clearPattern() {
        selectedDots.clear()
        invalidate()
    }
}
