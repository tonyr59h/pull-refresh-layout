/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 baoyongzhang <baoyz94@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xponential.widget

import android.graphics.*
import android.util.TypedValue
import kotlin.math.cos
import kotlin.math.sin

class SmartisanDrawable(layout: PullRefreshLayout) :
    RefreshDrawable(layout) {
    private var mBounds: RectF? = null
    private var mWidth = 0f
    private var mHeight = 0f
    private var mCenterX = 0f
    private var mCenterY = 0f
    private var mPercent = 0f
    private val mMaxAngle = (180f * .85).toFloat()
    private val mRadius = dp2px(12).toFloat()
    private val mLineLength = (Math.PI / 180 * mMaxAngle * mRadius).toFloat()
    private val mLineWidth = dp2px(3).toFloat()
    private val mArrowLength = (mLineLength * .15).toInt().toFloat()
    private val mArrowAngle = (Math.PI / 180 * 25).toFloat()
    private val mArrowXSpace = (mArrowLength * sin(mArrowAngle.toDouble())).toInt().toFloat()
    private val mArrowYSpace = (mArrowLength * cos(mArrowAngle.toDouble())).toInt().toFloat()
    private val mPaint = Paint()
    private var mOffset = 0
    private var mRunning = false
    private var mDegrees = 0f

    init {
        mPaint.isAntiAlias = true
        mPaint.strokeJoin = Paint.Join.ROUND
        mPaint.strokeCap = Paint.Cap.ROUND
        mPaint.strokeWidth = mLineWidth
        mPaint.style = Paint.Style.STROKE
        mPaint.color = Color.GRAY
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        mHeight = refreshLayout.finalOffset.toFloat()
        mWidth = mHeight
        mBounds = RectF(
            bounds.width() / 2 - mWidth / 2,
            bounds.top - mHeight / 2,
            bounds.width() / 2 + mWidth / 2,
            bounds.top + mHeight / 2
        )
        mCenterX = mBounds!!.centerX()
        mCenterY = mBounds!!.centerY()
    }

    override fun setPercent(newPercent: Float) {
        mPercent = newPercent
        invalidateSelf()
    }

    override fun setColorSchemeColors(colorSchemeColors: IntArray) {
        if (colorSchemeColors.isNotEmpty()) {
            mPaint.color = colorSchemeColors[0]
        }
    }

    override fun offsetTopAndBottom(offset: Int) {
        mOffset += offset
        invalidateSelf()
    }

    override fun start() {
        mRunning = true
        mDegrees = 0f
        invalidateSelf()
    }

    override fun stop() { mRunning = false }

    override fun isRunning() = mRunning

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(0f, (mOffset / 2).toFloat())
        canvas.clipRect(mBounds!!)
        if (mOffset > mHeight && !isRunning) {
            canvas.rotate((mOffset - mHeight) / mHeight * 360, mCenterX, mCenterY)
        }
        if (isRunning) {
            canvas.rotate(mDegrees, mCenterX, mCenterY)
            mDegrees = if (mDegrees < 360) mDegrees + 10 else 0f
            invalidateSelf()
        }
        if (mPercent <= .5f) {
            val percent = mPercent / .5f

            // left
            val leftX = mCenterX - mRadius
            val leftY = mCenterY + mLineLength - mLineLength * percent
            canvas.drawLine(leftX, leftY, leftX, leftY + mLineLength, mPaint)

            // left arrow
            canvas.drawLine(leftX, leftY, leftX - mArrowXSpace, leftY + mArrowYSpace, mPaint)

            // right
            val rightX = mCenterX + mRadius
            val rightY = mCenterY - mLineLength + mLineLength * percent
            canvas.drawLine(rightX, rightY, rightX, rightY - mLineLength, mPaint)

            // right arrow
            canvas.drawLine(rightX, rightY, rightX + mArrowXSpace, rightY - mArrowYSpace, mPaint)
        } else {
            val percent = (mPercent - .5f) / .5f
            // left
            val leftX = mCenterX - mRadius
            val leftY = mCenterY
            canvas.drawLine(
                leftX,
                leftY,
                leftX,
                leftY + mLineLength - mLineLength * percent,
                mPaint
            )
            val oval = RectF(
                mCenterX - mRadius,
                mCenterY - mRadius,
                mCenterX + mRadius,
                mCenterY + mRadius
            )
            canvas.drawArc(oval, 180f, mMaxAngle * percent, false, mPaint)

            // right
            val rightX = mCenterX + mRadius
            val rightY = mCenterY
            canvas.drawLine(
                rightX,
                rightY,
                rightX,
                rightY - mLineLength + mLineLength * percent,
                mPaint
            )
            canvas.drawArc(oval, 0f, mMaxAngle * percent, false, mPaint)

            // arrow
            canvas.save()
            canvas.rotate(mMaxAngle * percent, mCenterX, mCenterY)

            // left arrow
            canvas.drawLine(leftX, leftY, leftX - mArrowXSpace, leftY + mArrowYSpace, mPaint)

            // right arrow
            canvas.drawLine(rightX, rightY, rightX + mArrowXSpace, rightY - mArrowYSpace, mPaint)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun dp2px(dp: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}
