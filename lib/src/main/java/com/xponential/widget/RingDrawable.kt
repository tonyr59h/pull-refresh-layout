package com.xponential.widget

import android.graphics.*
import android.util.TypedValue

internal class RingDrawable(layout: PullRefreshLayout) : RefreshDrawable(layout) {
    private var isRunning = false
    private var mBounds: RectF? = null
    private var mWidth = 0
    private var mHeight = 0
    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mPath: Path
    private var mAngle = 0f
    private var mColorSchemeColors = intArrayOf()
    private var mLevel = 0
    private var mDegrees = 0f

    init {
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeWidth = dp2px(3).toFloat()
        mPaint.strokeCap = Paint.Cap.ROUND
        mPath = Path()
    }

    override fun setPercent(newPercent: Float) {
        mPaint.color = evaluate(newPercent, mColorSchemeColors[0], mColorSchemeColors[1])
        mAngle = 340 * newPercent
    }

    override fun setColorSchemeColors(colorSchemeColors: IntArray) {
        mColorSchemeColors = colorSchemeColors
    }

    override fun offsetTopAndBottom(offset: Int) = invalidateSelf()

    override fun start() {
        mLevel = 50
        isRunning = true
        invalidateSelf()
    }

    private fun updateLevel(level: Int) {
        val animationLevel = if (level == MAX_LEVEL) 0 else level
        val stateForLevel = animationLevel / 50
        val percent = level % 50 / 50f
        val startColor = mColorSchemeColors[stateForLevel]
        val endColor = mColorSchemeColors[(stateForLevel + 1) % mColorSchemeColors.size]
        mPaint.color = evaluate(percent, startColor, endColor)
        mDegrees = 360 * percent
    }

    override fun stop() {
        isRunning = false
        mDegrees = 0f
    }

    override fun isRunning() = isRunning

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        mWidth = refreshLayout.finalOffset
        mHeight = mWidth
        mBounds = RectF(
            (bounds.width() / 2 - mWidth / 2).toFloat(),
            bounds.top.toFloat(),
            (bounds.width() / 2 + mWidth / 2).toFloat(),
            (bounds.top + mHeight).toFloat()
        )
        mBounds!!.inset(dp2px(15).toFloat(), dp2px(15).toFloat())
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.rotate(mDegrees, mBounds!!.centerX(), mBounds!!.centerY())
        drawRing(canvas)
        canvas.restore()
        if (isRunning) {
            mLevel = if (mLevel >= MAX_LEVEL) 0 else mLevel + 1
            updateLevel(mLevel)
            invalidateSelf()
        }
    }

    private fun drawRing(canvas: Canvas) {
        mPath.reset()
        mPath.arcTo(mBounds!!, 270f, mAngle, true)
        canvas.drawPath(mPath, mPaint)
    }

    private fun dp2px(dp: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()

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
        private const val MAX_LEVEL = 200
    }
}
