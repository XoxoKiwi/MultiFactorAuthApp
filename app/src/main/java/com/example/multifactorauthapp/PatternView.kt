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

    interface OnPatternListener {
        fun onPatternDetected(ids: List<Int>, timestamps: List<Long>)
    }

    private var listener: OnPatternListener? = null
    private val dots = ArrayList<Dot>()
    private val path = ArrayList<Dot>()
    private val currentPathIds = ArrayList<Int>()
    private val currentPathTimes = ArrayList<Long>()

    // 1. Dots remain BLACK for contrast
    private val paintDot = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 2. Lines changed to LIGHT BLUE (Deep Sky Blue)
    private val paintLine = Paint().apply {
        color = Color.parseColor("#00BFFF") // Light Blue
        strokeWidth = 10f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // 3. Ring changed to Semi-Transparent Light Blue
    private val paintRing = Paint().apply {
        color = Color.parseColor("#8800BFFF") // Transparent Light Blue
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
        if (path.isNotEmpty()) {
            for (i in 0 until path.size - 1) {
                canvas.drawLine(path[i].x, path[i].y, path[i+1].x, path[i+1].y, paintLine)
            }
        }
        for (dot in dots) {
            canvas.drawCircle(dot.x, dot.y, 20f, paintDot)
            if (currentPathIds.contains(dot.id)) {
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
                    if (!currentPathIds.contains(dot.id) && isTouching(dot, x, y)) {
                        path.add(dot)
                        currentPathIds.add(dot.id)
                        currentPathTimes.add(SystemClock.elapsedRealtime())
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        invalidate()
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
        return dist < 60
    }

    data class Dot(val id: Int, val x: Float, val y: Float)
}