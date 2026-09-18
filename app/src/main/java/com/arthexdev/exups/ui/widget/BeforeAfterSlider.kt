package com.arthexdev.exups.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs

class BeforeAfterSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var beforeBmp: Bitmap? = null
    private var afterBmp: Bitmap? = null

    private var dividerFraction = 0.5f
    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f

    private val minScale = 1f
    private val maxScale = 8f

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val dstRect = RectF()
    private val matrix = Matrix()

    private var lastX = 0f
    private var lastY = 0f
    private var isDraggingDivider = false
    private var isPanning = false

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = scaleFactor
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
                if (oldScale != scaleFactor) {
                    translateX -= (detector.focusX - width / 2f - translateX) *
                            (scaleFactor / oldScale - 1f)
                    translateY -= (detector.focusY - height / 2f - translateY) *
                            (scaleFactor / oldScale - 1f)
                    clampTranslate()
                    invalidate()
                }
                return true
            }
        })

    fun setBitmaps(before: Bitmap?, after: Bitmap?) {
        this.beforeBmp = before
        this.afterBmp = after
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        invalidate()
    }

    fun resetZoom() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = beforeBmp ?: return
        val a = afterBmp ?: b

        val vw = width.toFloat()
        val vh = height.toFloat()

        val bAspect = b.width.toFloat() / b.height
        val vAspect = vw / vh
        if (bAspect > vAspect) {
            dstRect.left = 0f; dstRect.right = vw
            val h = vw / bAspect
            dstRect.top = (vh - h) / 2f; dstRect.bottom = dstRect.top + h
        } else {
            dstRect.top = 0f; dstRect.bottom = vh
            val w = vh * bAspect
            dstRect.left = (vw - w) / 2f; dstRect.right = dstRect.left + w
        }

        matrix.reset()
        matrix.postScale(scaleFactor, scaleFactor, vw / 2f, vh / 2f)
        matrix.postTranslate(translateX, translateY)

        canvas.save()
        canvas.concat(matrix)

        canvas.save()
        canvas.clipRect(dstRect)
        canvas.drawBitmap(b, null, dstRect, paint)
        canvas.restore()

        val dividerX = vw * dividerFraction
        canvas.save()
        canvas.clipRect(dividerX, 0f, vw, vh)
        canvas.drawBitmap(a, null, dstRect, paint)
        canvas.restore()

        canvas.restore()

        val dx = vw * dividerFraction
        canvas.drawLine(dx, 0f, dx, vh, dividerPaint)

        val cy = vh / 2f
        canvas.drawCircle(dx, cy, 28f, handlePaint)
        canvas.drawCircle(dx, cy, 28f, handleStrokePaint)

        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(dx - 12f, cy, dx - 6f, cy - 6f, arrowPaint)
        canvas.drawLine(dx - 12f, cy, dx - 6f, cy + 6f, arrowPaint)
        canvas.drawLine(dx + 12f, cy, dx + 6f, cy - 6f, arrowPaint)
        canvas.drawLine(dx + 12f, cy, dx + 6f, cy + 6f, arrowPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                val dx = width * dividerFraction
                isDraggingDivider = abs(event.x - dx) < 80f
                isPanning = scaleFactor > 1.01f && !isDraggingDivider
                if (isDraggingDivider) parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true
                if (isDraggingDivider) {
                    dividerFraction = (event.x / width).coerceIn(0f, 1f)
                    invalidate()
                } else if (isPanning) {
                    translateX += event.x - lastX
                    translateY += event.y - lastY
                    clampTranslate()
                    invalidate()
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingDivider = false
                isPanning = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun clampTranslate() {
        if (scaleFactor <= 1.01f) {
            translateX = 0f; translateY = 0f; return
        }
        val maxTx = (width * (scaleFactor - 1f)) / 2f
        val maxTy = (height * (scaleFactor - 1f)) / 2f
        translateX = translateX.coerceIn(-maxTx, maxTx)
        translateY = translateY.coerceIn(-maxTy, maxTy)
    }
}
