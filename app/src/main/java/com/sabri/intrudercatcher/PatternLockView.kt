package com.sabri.intrudercatcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * شبكة نمط ٣×٣ بسيطة، مرسومة يدويًا بدون أي مكتبة خارجية - بنفس أسلوب
 * قفل النمط التقليدي بأندرويد. المستخدم يسحب إصبعه بين النقاط، وعند
 * رفع الإصبع تُستدعى [onPatternComplete] بترتيب النقاط اللي تم ربطها
 * (كل نقطة برقمها من ٠ إلى ٨، من أعلى اليسار لأسفل اليمين).
 */
class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val GRID_SIZE = 3
        private const val MIN_DOTS = 4
    }

    /** يُستدعى برفع الإصبع لو تم ربط ٤ نقاط على الأقل */
    var onPatternComplete: ((List<Int>) -> Unit)? = null

    /** يُستدعى لو رُفع الإصبع بأقل من ٤ نقاط - النمط غير صالح */
    var onPatternTooShort: (() -> Unit)? = null

    private val dotRadius = 16f
    private val selectedDotRadius = 24f
    private val touchSlopRadius = 70f

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#665C99")
        style = Paint.Style.FILL
    }
    private val selectedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9C6BFF")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9C6BFF")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val errorPaint = Paint(linePaint).apply {
        color = Color.parseColor("#F44336")
    }

    private val dotCenters = Array(GRID_SIZE * GRID_SIZE) { floatArrayOf(0f, 0f) }
    private val selectedDots = mutableListOf<Int>()
    private var currentX = 0f
    private var currentY = 0f
    private var isError = false
    private var isDrawing = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cellW = w / GRID_SIZE.toFloat()
        val cellH = h / GRID_SIZE.toFloat()
        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                val index = row * GRID_SIZE + col
                dotCenters[index][0] = cellW * col + cellW / 2f
                dotCenters[index][1] = cellH * row + cellH / 2f
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                isDrawing = true
                handleTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentX = event.x
                    currentY = event.y
                    handleTouch(event.x, event.y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    isDrawing = false
                    if (selectedDots.size >= MIN_DOTS) {
                        val result = selectedDots.toList()
                        invalidate()
                        onPatternComplete?.invoke(result)
                    } else {
                        isError = true
                        invalidate()
                        onPatternTooShort?.invoke()
                        postDelayed({ reset() }, 400)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(x: Float, y: Float) {
        currentX = x
        currentY = y
        for (i in dotCenters.indices) {
            if (selectedDots.contains(i)) continue
            val dx = x - dotCenters[i][0]
            val dy = y - dotCenters[i][1]
            if (hypot(dx, dy) <= touchSlopRadius) {
                selectedDots.add(i)
                invalidate()
                break
            }
        }
    }

    /** يمسح النمط الحالي المرسوم ويعيد الشبكة لحالتها الافتراضية */
    fun reset() {
        selectedDots.clear()
        isError = false
        invalidate()
    }

    /** يعرض حالة "خطأ" (خطوط حمراء) لحظيًا ثم يمسح النمط تلقائيًا - يُستدعى من الخارج عند فشل التحقق */
    fun showError() {
        isError = true
        invalidate()
        postDelayed({ reset() }, 400)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val paintLine = if (isError) errorPaint else linePaint

        for (i in 0 until selectedDots.size - 1) {
            val from = dotCenters[selectedDots[i]]
            val to = dotCenters[selectedDots[i + 1]]
            canvas.drawLine(from[0], from[1], to[0], to[1], paintLine)
        }

        if (isDrawing && selectedDots.isNotEmpty()) {
            val last = dotCenters[selectedDots.last()]
            canvas.drawLine(last[0], last[1], currentX, currentY, paintLine)
        }

        for (i in dotCenters.indices) {
            val isSelected = selectedDots.contains(i)
            val paint = when {
                isError && isSelected -> errorPaint
                isSelected -> selectedDotPaint
                else -> dotPaint
            }
            val radius = if (isSelected) selectedDotRadius else dotRadius
            canvas.drawCircle(dotCenters[i][0], dotCenters[i][1], radius, paint)
        }
    }
}
