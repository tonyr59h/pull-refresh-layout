package com.xponential.widget

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import java.security.InvalidParameterException
import kotlin.math.min

internal class CirclesDrawable(layout: PullRefreshLayout) : RefreshDrawable(layout), Runnable {
    private var mFstHalfPaint: Paint? = null
    private var mSecondHalfPaint: Paint? = null
    private var mAbovePaint: Paint? = null
    private val mOval = RectF()
    private var mDiameter = 0
    private var mPath = Path()
    private var mHalf = 0
    private var mCurrentState: ProgressStates? = null
    private var mControlPointMinimum = 0
    private var mControlPointMaximum = 0
    private var mAxisValue = 0
    private var mColorFilter: ColorFilter? = null
    private var fstColor = 0
    private var scndColor = 0
    private var goesBackward = false
    private val mHandler = Handler(Looper.getMainLooper())
    private var mLevel = 0
    private var isRunning = false
    private var mTop = 0
    private var mDrawWidth = 0
    private var mDrawHeight = 0
    private var mBounds: Rect? = null
    override fun start() {
        mLevel = 2500
        isRunning = true
        mHandler.postDelayed(this, 10)
    }

    override fun stop() {
        isRunning = false
        mHandler.removeCallbacks(this)
    }

    override fun isRunning(): Boolean {
        return isRunning
    }

    override fun setColorSchemeColors(colorSchemeColors: IntArray) {
        initCirclesProgress(colorSchemeColors)
    }

    override fun setPercent(newPercent: Float) = updateLevel((2500 * newPercent).toInt())

    private fun updateLevel(level: Int) {
        val animationLevel = if (level.toFloat() == MAX_LEVEL) 0 else level
        val stateForLevel = (animationLevel / MAX_LEVEL_PER_CIRCLE).toInt()
        mCurrentState = CirclesDrawable.ProgressStates.values()[stateForLevel]
        resetColor(mCurrentState)
        var levelForCircle = (animationLevel % MAX_LEVEL_PER_CIRCLE).toInt()
        val halfPassed: Boolean
        if (!goesBackward) {
            halfPassed = levelForCircle != (animationLevel % (MAX_LEVEL_PER_CIRCLE / 2)).toInt()
        } else {
            halfPassed = levelForCircle == (animationLevel % (MAX_LEVEL_PER_CIRCLE / 2)).toInt()
            levelForCircle = (MAX_LEVEL_PER_CIRCLE - levelForCircle).toInt()
        }
        mFstHalfPaint!!.color = fstColor
        mSecondHalfPaint!!.color = scndColor
        if (!halfPassed) {
            mAbovePaint!!.color = mSecondHalfPaint!!.color
        } else {
            mAbovePaint!!.color = mFstHalfPaint!!.color
        }
        mAbovePaint!!.alpha = 200 + (55 * (levelForCircle / MAX_LEVEL_PER_CIRCLE)).toInt()
        mAxisValue =
            (mControlPointMinimum + (mControlPointMaximum - mControlPointMinimum) * (levelForCircle / MAX_LEVEL_PER_CIRCLE)).toInt()
    }

    override fun offsetTopAndBottom(offset: Int) {
        mTop += offset
        invalidateSelf()
    }

    override fun run() {
        mLevel += 80
        if (mLevel > MAX_LEVEL) mLevel = 0
        if (isRunning) {
            mHandler.postDelayed(this, 20)
            updateLevel(mLevel)
            invalidateSelf()
        }
    }

    private enum class ProgressStates {
        FOLDING_DOWN, FOLDING_LEFT, FOLDING_UP, FOLDING_RIGHT
    }

    private fun initCirclesProgress(colors: IntArray) {
        initColors(colors)
        mPath = Path()
        val basePaint = Paint()
        basePaint.isAntiAlias = true
        mFstHalfPaint = Paint(basePaint)
        mSecondHalfPaint = Paint(basePaint)
        mAbovePaint = Paint(basePaint)
        colorFilter = mColorFilter
    }

    private fun initColors(colors: IntArray?) {
        if (colors == null || colors.size < 4) {
            throw InvalidParameterException("The color scheme length must be 4")
        }
        mColor1 = colors[0]
        mColor2 = colors[1]
        mColor3 = colors[2]
        mColor4 = colors[3]
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        mDrawWidth = dp2px(40)
        mDrawHeight = mDrawWidth
        mTop = -mDrawHeight - (refreshLayout.finalOffset - mDrawHeight) / 2
        mBounds = bounds
        measureCircleProgress(mDrawWidth, mDrawHeight)
    }

    private fun resetColor(currentState: ProgressStates?) {
        when (currentState) {
            ProgressStates.FOLDING_DOWN -> {
                fstColor = mColor1
                scndColor = mColor2
                goesBackward = false
            }
            ProgressStates.FOLDING_LEFT -> {
                fstColor = mColor1
                scndColor = mColor3
                goesBackward = true
            }
            ProgressStates.FOLDING_UP -> {
                fstColor = mColor3
                scndColor = mColor4
                goesBackward = true
            }
            ProgressStates.FOLDING_RIGHT -> {
                fstColor = mColor2
                scndColor = mColor4
                goesBackward = false
            }
            else -> {}
        }
    }

    override fun draw(canvas: Canvas) {
        if (mCurrentState != null) {
            canvas.save()
            canvas.translate((mBounds!!.width() / 2 - mDrawWidth / 2).toFloat(), mTop.toFloat())
            makeCirclesProgress(canvas)
            canvas.restore()
        }
    }

    private fun measureCircleProgress(width: Int, height: Int) {
        mDiameter = min(width, height)
        mHalf = mDiameter / 2
        mOval[0f, 0f, mDiameter.toFloat()] = mDiameter.toFloat()
        mControlPointMinimum = -mDiameter / 6
        mControlPointMaximum = mDiameter + mDiameter / 6
    }

    private fun makeCirclesProgress(canvas: Canvas) {
        when (mCurrentState) {
            ProgressStates.FOLDING_DOWN, ProgressStates.FOLDING_UP -> drawYMotion(canvas)
            ProgressStates.FOLDING_RIGHT, ProgressStates.FOLDING_LEFT -> drawXMotion(canvas)
            else -> {}
        }
        canvas.drawPath(mPath, mAbovePaint!!)
    }

    private fun drawXMotion(canvas: Canvas) {
        canvas.drawArc(mOval, 90f, 180f, true, mFstHalfPaint!!)
        canvas.drawArc(mOval, -270f, -180f, true, mSecondHalfPaint!!)
        mPath.reset()
        mPath.moveTo(mHalf.toFloat(), 0f)
        mPath.cubicTo(
            mAxisValue.toFloat(),
            0f,
            mAxisValue.toFloat(),
            mDiameter.toFloat(),
            mHalf.toFloat(),
            mDiameter.toFloat()
        )
    }

    private fun drawYMotion(canvas: Canvas) {
        canvas.drawArc(mOval, 0f, -180f, true, mFstHalfPaint!!)
        canvas.drawArc(mOval, -180f, -180f, true, mSecondHalfPaint!!)
        mPath.reset()
        mPath.moveTo(0f, mHalf.toFloat())
        mPath.cubicTo(
            0f,
            mAxisValue.toFloat(),
            mDiameter.toFloat(),
            mAxisValue.toFloat(),
            mDiameter.toFloat(),
            mHalf.toFloat()
        )
    }

    override fun setColorFilter(cf: ColorFilter?) {
        mColorFilter = cf
        mFstHalfPaint!!.colorFilter = cf
        mSecondHalfPaint!!.colorFilter = cf
        mAbovePaint!!.colorFilter = cf
    }

    private fun dp2px(@Suppress("SameParameterValue") dp: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    companion object {
        private const val MAX_LEVEL = 10000f
        private val CIRCLE_COUNT: Float = CirclesDrawable.ProgressStates.values().size.toFloat()
        private val MAX_LEVEL_PER_CIRCLE = MAX_LEVEL / CIRCLE_COUNT
        private var mColor1 = 0
        private var mColor2 = 0
        private var mColor3 = 0
        private var mColor4 = 0
    }
}
