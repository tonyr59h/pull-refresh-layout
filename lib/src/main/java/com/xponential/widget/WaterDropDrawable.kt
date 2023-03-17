package com.xponential.widget

import android.graphics.*
import android.os.Handler
import android.os.Looper
import java.security.InvalidParameterException
import kotlin.math.max

internal class WaterDropDrawable(layout: PullRefreshLayout) : RefreshDrawable(layout), Runnable {
    private var mLevel = 0
    private val p1 = Point()
    private val p2 = Point()
    private val p3 = Point()
    private val p4 = Point()
    private val mPaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL}
    private val mPath = Path()
    private var mHeight = 0
    private var mWidth = 0
    private var mTop = 0
    private var mColorSchemeColors = intArrayOf()
    private var mCurrentState: ProgressStates? = null
    private val mHandler = Handler(Looper.getMainLooper())
    private var isRunning = false

    @Suppress("unused")
    private enum class ProgressStates {
        ONE, TWO, TREE, FOUR
    }

    init {
        mPaint.color = Color.BLUE
        mPaint.style = Paint.Style.FILL
        mPaint.isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(0f, if (mTop > 0) mTop.toFloat() else 0f)
        mPath.reset()
        mPath.moveTo(p1.x.toFloat(), p1.y.toFloat())
        mPath.cubicTo(
            p3.x.toFloat(),
            p3.y.toFloat(),
            p4.x.toFloat(),
            p4.y.toFloat(),
            p2.x.toFloat(),
            p2.y.toFloat()
        )
        canvas.drawPath(mPath, mPaint)
        canvas.restore()
    }

    override fun onBoundsChange(bounds: Rect) {
        mWidth = bounds.width()
        updateBounds()
        super.onBoundsChange(bounds)
    }

    private fun updateBounds() {
        val height = max(mHeight, refreshLayout.finalOffset)
        val width = mWidth
        val percent = height.toFloat() / refreshLayout.finalOffset
        val offsetX = (width / 2 * percent).toInt()
        val offsetY = 0
        p1[offsetX] = offsetY
        p2[width - offsetX] = offsetY
        p3[width / 2 - height] = height
        p4[width / 2 + height] = height
    }

    override fun setColorSchemeColors(colorSchemeColors: IntArray) {
        if (colorSchemeColors.size < 4) {
            throw InvalidParameterException("The color scheme length must be 4")
        }
        mPaint.color = colorSchemeColors[0]
        mColorSchemeColors = colorSchemeColors
    }

    override fun setPercent(newPercent: Float) {
        mPaint.color = evaluate(newPercent, mColorSchemeColors[0], mColorSchemeColors[1])
    }

    private fun updateLevel(level: Int) {
        val animationLevel = if (level.toFloat() == MAX_LEVEL) 0 else level
        val stateForLevel = (animationLevel / MAX_LEVEL_PER_CIRCLE).toInt()
        mCurrentState = WaterDropDrawable.ProgressStates.values()[stateForLevel]
        val percent = level % 2500 / 2500f
        val startColor = mColorSchemeColors[stateForLevel]
        val endColor =
            mColorSchemeColors[(stateForLevel + 1) % WaterDropDrawable.ProgressStates.values().size]
        mPaint.color = evaluate(percent, startColor, endColor)
    }

    override fun offsetTopAndBottom(offset: Int) {
        mHeight += offset
        mTop = mHeight - refreshLayout.finalOffset
        updateBounds()
        invalidateSelf()
    }

    override fun start() {
        mLevel = 2500
        isRunning = true
        mHandler.postDelayed(this, 20)
    }

    override fun stop() = mHandler.removeCallbacks(this)

    override fun isRunning() = isRunning

    override fun run() {
        mLevel += 60
        if (mLevel > MAX_LEVEL) mLevel = 0
        if (isRunning) {
            mHandler.postDelayed(this, 20)
            updateLevel(mLevel)
            invalidateSelf()
        }
    }

    private fun evaluate(fraction: Float, startValue: Int, endValue: Int): Int {
        val startA = startValue shr 24 and 0xff
        val startR = startValue shr 16 and 0xff
        val startG = startValue shr 8 and 0xff
        val startB = startValue and 0xff
        val endA = endValue shr 24 and 0xff
        val endR = endValue shr 16 and 0xff
        val endG = endValue shr 8 and 0xff
        val endB = endValue and 0xff
        return startA + (fraction * (endA - startA)).toInt() shl 24 or
                (startR + (fraction * (endR - startR)).toInt() shl 16) or
                (startG + (fraction * (endG - startG)).toInt() shl 8) or startB + (fraction * (endB - startB)).toInt()
    }

    companion object {
        private const val MAX_LEVEL = 10000f
        private val CIRCLE_COUNT: Float = WaterDropDrawable.ProgressStates.values().size.toFloat()
        private val MAX_LEVEL_PER_CIRCLE = MAX_LEVEL / CIRCLE_COUNT
    }
}
