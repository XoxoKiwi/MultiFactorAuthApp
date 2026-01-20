package com.example.multifactorauthapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.pow
import kotlin.math.sqrt

class PatternView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    // Interface to talk to Activity
    interface OnPatternListener {
        fun onPatternDetected(ids: List<Int>, timestamps: List<Long>)
    }

    private var listener: OnPatternListener? = null
    private val dots = ArrayList<Dot>()
    private val path = ArrayList<Dot>() // Ordered list of connected dots
    private val currentPathIds = ArrayList<Int>() // IDs for quick lookup
    private val currentPathTimes = ArrayList<Long>()

    // 1. The Solid White Dot
    private val paintDot = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 2. The Cyan Line
    private val paintLine = Paint().apply {
        color = Color.CYAN
        strokeWidth = 10f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // 3. NEW: The Outer Ring Effect (Visual Feedback)
    private val paintRing = Paint().apply {
        color = Color.parseColor("#AA00FFFF") // Semi-transparent Cyan
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    fun setOnPatternListener(listener: OnPatternListener) {
        this.listener = listener
    }

    fun clearPattern() {
        path.clear()
        currentPathIds.clear()
        currentPathTimes.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        dots.clear()

        // Calculate grid positions
        val spacing = w / 4f
        val startY = (h - w) / 2f

        var id = 1
        for (row in 1..3) {
            for (col in 1..3) {
                val x = col * spacing
                val y = startY + (row * spacing)
                dots.add(Dot(id++, x, y))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // A. Draw Lines connecting the dots
        if (path.isNotEmpty()) {
            for (i in 0 until path.size - 1) {
                canvas.drawLine(path[i].x, path[i].y, path[i+1].x, path[i+1].y, paintLine)
            }

            // Draw line from last dot to current finger position (Optional, improves feel)
            // requires tracking touch X/Y globally, but skipping for simplicity here.
        }

        // B. Draw Dots
        for (dot in dots) {
            // 1. Draw the standard white dot
            canvas.drawCircle(dot.x, dot.y, 20f, paintDot)

            // 2. NEW: If this dot is selected, draw the "Outer Ring"
            if (currentPathIds.contains(dot.id)) {
                // Radius 50f creates a nice ring around the 20f dot
                canvas.drawCircle(dot.x, dot.y, 50f, paintRing)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                for (dot in dots) {
                    // If dot is not already in path and finger is close enough
                    if (!currentPathIds.contains(dot.id) && isTouching(dot, x, y)) {
                        path.add(dot)
                        currentPathIds.add(dot.id)
                        currentPathTimes.add(SystemClock.elapsedRealtime())

                        // Trigger haptic feedback (vibration) if available
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

                        invalidate() // Redraw to show the new line/ring
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (currentPathIds.isNotEmpty()) {
                    listener?.onPatternDetected(ArrayList(currentPathIds), ArrayList(currentPathTimes))
                }
                return true
            }
        }
        return false
    }

    private fun isTouching(dot: Dot, x: Float, y: Float): Boolean {
        val dist = sqrt((x - dot.x).pow(2) + (y - dot.y).pow(2))
        return dist < 60 // Touch target size
    }

    data class Dot(val id: Int, val x: Float, val y: Float)
}