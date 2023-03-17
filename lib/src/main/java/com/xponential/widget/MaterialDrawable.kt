/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xponential.widget

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.TypedValue
import android.view.View
import android.view.animation.*
import android.view.animation.Animation.AnimationListener
import android.view.animation.Interpolator
import androidx.annotation.IntDef
import com.xponential.widget.MaterialDrawable.*
import kotlin.math.*

/**
 * Fancy progress indicator for Material theme.
 *
 * @hide
 */
internal class MaterialDrawable(layout: PullRefreshLayout) : RefreshDrawable(layout), Animatable {

    @Retention(AnnotationRetention.BINARY)
    @IntDef(LARGE, DEFAULT)
    annotation class ProgressDrawableSize

    /** The list of animators operating on this drawable. */
    private val mAnimators = ArrayList<Animation>()

    /** The indicator ring, used to manage animation state. */
    private val mRing: Ring

    /** Canvas rotation in degrees. */
    private var mRotation = 0f
    private val mResources: Resources
    private val mParent: View
    private var mAnimation: Animation? = null
    private var mRotationCount = 0f
    private var mWidth = 0.0
    private var mHeight = 0.0
    private var mFinishAnimation: Animation? = null
    private var mShadowRadius = 0
    private var mPadding = 0
    private var mCircle: ShapeDrawable? = null
    private var mTop: Int
    private val mDiameter: Int
    private fun createCircleDrawable() {
        val radius = (CIRCLE_DIAMETER / 2).toFloat()
        val density = context.resources.displayMetrics.density
        val diameter = (radius * density * 2).toInt()
        val shadowYOffset = (density * Y_OFFSET).toInt()
        val shadowXOffset = (density * X_OFFSET).toInt()
        mShadowRadius = (density * SHADOW_RADIUS).toInt()
        val oval: OvalShape = OvalShadow(mShadowRadius, diameter)
        mCircle = ShapeDrawable(oval)
        mCircle!!.paint.setShadowLayer(
            mShadowRadius.toFloat(), shadowXOffset.toFloat(), shadowYOffset.toFloat(),
            KEY_SHADOW_COLOR
        )
        mPadding = mShadowRadius
        mCircle!!.paint.color = Color.WHITE
    }

    private inner class OvalShadow(shadowRadius: Int, circleDiameter: Int) : OvalShape() {
        private val mRadialGradient: RadialGradient
        private val mShadowRadius: Int
        private val mShadowPaint = Paint()
        private val mCircleDiameter: Int

        init {
            mShadowRadius = shadowRadius
            mCircleDiameter = circleDiameter
            mRadialGradient = RadialGradient(
                (mCircleDiameter / 2).toFloat(), (mCircleDiameter / 2).toFloat(),
                mShadowRadius.toFloat(), intArrayOf(
                    FILL_SHADOW_COLOR, Color.TRANSPARENT
                ), null, Shader.TileMode.CLAMP
            )
            mShadowPaint.shader = mRadialGradient
        }

        override fun draw(canvas: Canvas, paint: Paint) {
            val x: Int = this@MaterialDrawable.bounds.centerX()
            val y: Int = this@MaterialDrawable.bounds.centerY()
            canvas.drawCircle(
                x.toFloat(), y.toFloat(), (mCircleDiameter / 2 + mShadowRadius).toFloat(),
                mShadowPaint
            )
            canvas.drawCircle(x.toFloat(), y.toFloat(), (mCircleDiameter / 2).toFloat(), paint)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun setSizeParameters(
        progressCircleWidth: Double,
        progressCircleHeight: Double,
        centerRadius: Double,
        strokeWidth: Double,
        arrowWidth: Float,
        arrowHeight: Float
    ) {
        val ring = mRing
        val metrics = mResources.displayMetrics
        val screenDensity = metrics.density
        mWidth = progressCircleWidth * screenDensity
        mHeight = progressCircleHeight * screenDensity
        ring.strokeWidth = strokeWidth.toFloat() * screenDensity
        ring.centerRadius = centerRadius * screenDensity
        ring.setColorIndex(0)
        ring.setArrowDimensions(arrowWidth * screenDensity, arrowHeight * screenDensity)
        ring.setInsets(mWidth.toInt(), mHeight.toInt())
    }

    /**
     * Set the dimensions for the progress spinner. Updates radius and stroke width of the ring.
     */
    @Suppress("MemberVisibilityCanBePrivate,SameParameterValue")
    fun updateSizes(@ProgressDrawableSize size: Int) {
        if (size == LARGE) {
            setSizeParameters(
                CIRCLE_DIAMETER_LARGE.toDouble(),
                CIRCLE_DIAMETER_LARGE.toDouble(),
                CENTER_RADIUS_LARGE.toDouble(),
                STROKE_WIDTH_LARGE.toDouble(),
                ARROW_WIDTH_LARGE_DP.toFloat(),
                ARROW_HEIGHT_LARGE_DP.toFloat()
            )
        } else {
            setSizeParameters(
                CIRCLE_DIAMETER.toDouble(),
                CIRCLE_DIAMETER.toDouble(),
                CENTER_RADIUS.toDouble(),
                STROKE_WIDTH.toDouble(),
                ARROW_WIDTH_DP.toFloat(),
                ARROW_HEIGHT_DP.toFloat()
            )
        }
    }

    /**
     * @param show Set to true to display the arrowhead on the progress spinner.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun showArrow(show: Boolean) {
        mRing.setShowArrow(show)
    }

    /**
     * @param scale Set the scale of the arrowhead for the spinner.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun setArrowScale(scale: Float) {
        mRing.setArrowScale(scale)
    }

    /**
     * Set the start and end trim for the progress spinner arc.
     *
     * @param startAngle start angle
     * @param endAngle   end angle
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun setStartEndTrim(startAngle: Float, endAngle: Float) {
        mRing.startTrim = startAngle
        mRing.endTrim = endAngle
    }

    /**
     * @param rotation Rotation is from [0..1]
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun setProgressRotation(rotation: Float) {
        mRing.rotation = rotation
    }

    /** Update the background color of the circle image view. */
    @Suppress("MemberVisibilityCanBePrivate")
    fun setBackgroundColor(color: Int) {
        mRing.setBackgroundColor(color)
    }

    override fun setPercent(newPercent: Float) {
        var percent = newPercent
        if (percent < .4f) return
        percent = (percent - .4f) / .6f
        ringAlpha = (MAX_ALPHA * percent).toInt()
        showArrow(true)
        val strokeStart = percent * .8f
        setStartEndTrim(0f, MAX_PROGRESS_ANGLE.coerceAtMost(strokeStart))
        setArrowScale(1f.coerceAtMost(percent))
        val rotation = if (percent < .8f) 0f else (percent - .8f) / .2f * .25f
        setProgressRotation(rotation)
    }

    /**
     * @param colorSchemeColors the colors used in the progress animation from color resources.
     * The first color will be considered primary and will be utilized in other child views.
     */
    override fun setColorSchemeColors(colorSchemeColors: IntArray) {
        mRing.setColors(colorSchemeColors)
        mRing.setColorIndex(0)
    }

    override fun offsetTopAndBottom(offset: Int) {
        mTop += offset
        invalidateSelf()
    }

    override fun draw(c: Canvas) {
        val bounds: Rect = bounds
        val saveCount = c.save()
        c.translate(0f, mTop.toFloat())
        mCircle!!.draw(c)
        c.rotate(mRotation, bounds.exactCenterX(), bounds.exactCenterY())
        mRing.draw(c, bounds)
        c.restoreToCount(saveCount)
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        super.setBounds(w / 2 - mDiameter / 2, top, w / 2 + mDiameter / 2, mDiameter + top)
    }

    @Suppress("SameParameterValue")
    private fun dp2px(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    @Suppress("MemberVisibilityCanBePrivate")
    var ringAlpha: Int
        get() = mRing.alpha
        set(alpha) {
            mRing.alpha = alpha
        }

    override fun setColorFilter(cf: ColorFilter?) {
        mRing.setColorFilter(cf)
    }

    private var rotation: Float
        get() = mRotation
        set(rotation) {
            mRotation = rotation
            invalidateSelf()
        }

    override fun isRunning(): Boolean {
        for (animator in mAnimators) {
            if (animator.hasStarted() && !animator.hasEnded()) {
                return true
            }
        }
        return false
    }

    override fun start() {
        mAnimation!!.reset()
        mRing.storeOriginals()

        // Already showing some part of the ring
        if (mRing.endTrim != mRing.startTrim) {
            mParent.startAnimation(mFinishAnimation)
        } else {
            mRing.setColorIndex(0)
            mRing.resetOriginals()
            mParent.startAnimation(mAnimation)
        }
    }

    override fun stop() {
        mParent.clearAnimation()
        rotation = 0f
        mRing.setShowArrow(false)
        mRing.setColorIndex(0)
        mRing.resetOriginals()
    }

    private fun setupAnimators() {
        val ring = mRing
        val finishRingAnimation: Animation = object : Animation() {
            public override fun applyTransformation(t: Float, transform: Transformation) {
                // complete a full rotation before starting a new one, normalized degrees in [0..1]
                val targetRotation = floor((ring.startingRotation / MAX_PROGRESS_ARC)) + 1f
                ring.startTrim =
                    ((ring.startingStartTrim + (ring.startingEndTrim - ring.startingStartTrim))) * t
                ring.rotation =
                    (ring.startingRotation + (targetRotation - ring.startingRotation) * t)
                ring.setArrowScale(1 - t)
            }
        }

        finishRingAnimation.interpolator = EASE_INTERPOLATOR
        finishRingAnimation.duration = (ANIMATION_DURATION_MS / 2).toLong()
        finishRingAnimation.setAnimationListener(object : AnimationListener {
            override fun onAnimationStart(animation: Animation) {}

            override fun onAnimationEnd(animation: Animation) {
                ring.goToNextColor()
                ring.storeOriginals()
                ring.setShowArrow(false)
                mParent.startAnimation(mAnimation)
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })

        val animation: Animation = object : Animation() {
            public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                // The minProgressArc is calculated to create an angle that matches stroke width
                val minProgressArc = Math.toRadians(
                    ring.strokeWidth
                            / (2 * Math.PI * ring.centerRadius)
                ).toFloat()
                val startingEndTrim = ring.startingEndTrim
                val startingTrim = ring.startingStartTrim
                val startingRotation = ring.startingRotation

                // Offset the minProgressArc to where the endTrim is located.
                val minArc = MAX_PROGRESS_ARC - minProgressArc
                val endTrim = (startingEndTrim
                        + (minArc * START_CURVE_INTERPOLATOR.getInterpolation(interpolatedTime)))
                ring.endTrim = endTrim
                val startTrim = startingTrim +
                    (MAX_PROGRESS_ARC * END_CURVE_INTERPOLATOR.getInterpolation(interpolatedTime))
                ring.startTrim = startTrim
                val rotation = startingRotation + (0.25f * interpolatedTime)
                ring.rotation = rotation
                val groupRotation = (((720.0f / NUM_POINTS) * interpolatedTime)
                        + (720.0f * (mRotationCount / NUM_POINTS)))
                this@MaterialDrawable.rotation = groupRotation
            }
        }
        animation.repeatCount = Animation.INFINITE
        animation.repeatMode = Animation.RESTART
        animation.interpolator = LINEAR_INTERPOLATOR
        animation.duration = ANIMATION_DURATION_MS.toLong()
        animation.setAnimationListener(object : AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                mRotationCount = 0f
            }

            override fun onAnimationEnd(animation: Animation) {
                // do nothing
            }

            override fun onAnimationRepeat(animation: Animation) {
                ring.storeOriginals()
                ring.goToNextColor()
                ring.startTrim = ring.endTrim
                mRotationCount = (mRotationCount + 1) % (NUM_POINTS)
            }
        })
        mFinishAnimation = finishRingAnimation
        mAnimation = animation
    }

    private val mCallback: Callback = object : Callback {
        override fun invalidateDrawable(d: Drawable) { invalidateSelf()  }

        override fun unscheduleDrawable(d: Drawable, what: Runnable) { unscheduleSelf(what) }

        override fun scheduleDrawable(d: Drawable, what: Runnable, cuando: Long) {
            scheduleSelf(what, cuando)
        }
    }

    init {
        mParent = layout
        mResources = context.resources
        mRing = Ring(mCallback)
        mRing.setColors(intArrayOf(Color.BLACK))
        updateSizes(DEFAULT)
        setupAnimators()
        createCircleDrawable()
        setBackgroundColor(CIRCLE_BG_LIGHT)
        mDiameter = dp2px(40)
        mTop = -mDiameter - (refreshLayout.finalOffset - mDiameter) / 2
    }

    inner class Ring(private val mCallback: Callback) {
        private val mTempBounds = RectF()
        private val mPaint = Paint()
        private val mArrowPaint = Paint()
        private var mStartTrim = 0.0f
        private var mEndTrim = 0.0f
        private var mRotation = 0.0f
        private var mStrokeWidth = 5.0f
        private var insets = 2.5f
        private var mColors: IntArray = intArrayOf()

        // mColorIndex represents the offset into the available mColors that the
        // progress circle should currently display. As the progress circle is
        // animating, the mColorIndex moves by one to the next available color.
        private var mColorIndex = 0
        var startingStartTrim = 0f
            private set
        var startingEndTrim = 0f
            private set

        /**
         * @return The amount the progress spinner is currently rotated, between [0..1].
         */
        var startingRotation = 0f
            private set

        private var mShowArrow = false
        private var mArrow: Path = Path().apply { fillType = Path.FillType.EVEN_ODD }
        private var mArrowScale = 0f

        /** Inner radius in px of the circle the progress spinner arc traces. */
        var centerRadius = 0.0

        private var mArrowWidth = 0
        private var mArrowHeight = 0

        /** Current alpha of the progress spinner and arrowhead. */
        var alpha = 0
        private val mCirclePaint = Paint()
        private var mBackgroundColor = 0

        init {
            mPaint.strokeCap = Paint.Cap.SQUARE
            mPaint.isAntiAlias = true
            mPaint.style = Paint.Style.STROKE
            mArrowPaint.style = Paint.Style.FILL
            mArrowPaint.isAntiAlias = true
        }

        fun setBackgroundColor(color: Int) { mBackgroundColor = color }

        /**
         * @param width  Width of the hypotenuse of the arrow head
         * @param height Height of the arrow point
         */
        fun setArrowDimensions(width: Float, height: Float) {
            mArrowWidth = width.toInt()
            mArrowHeight = height.toInt()
        }

        fun draw(c: Canvas, bounds: Rect) {
            val arcBounds = mTempBounds
            arcBounds.set(bounds)
            arcBounds.inset(insets, insets)
            val startAngle = (mStartTrim + mRotation) * 360
            val endAngle = (mEndTrim + mRotation) * 360
            val sweepAngle = endAngle - startAngle
            mPaint.color = mColors[mColorIndex]
            c.drawArc(arcBounds, startAngle, sweepAngle, false, mPaint)
            drawTriangle(c, startAngle, sweepAngle, bounds)
            if (alpha < 255) {
                mCirclePaint.color = mBackgroundColor
                mCirclePaint.alpha = 255 - alpha
                c.drawCircle(
                    bounds.exactCenterX(), bounds.exactCenterY(), (bounds.width() / 2).toFloat(),
                    mCirclePaint
                )
            }
        }

        private fun drawTriangle(c: Canvas, startAngle: Float, sweepAngle: Float, bounds: Rect) {
            if (!mShowArrow) return
            mArrow.reset()

            // Adjust the position of the triangle so that it is centered on the arc
            val inset = insets.toInt() / 2 * mArrowScale
            val x = (centerRadius * cos(0.0) + bounds.exactCenterX()).toFloat()
            val y = (centerRadius * sin(0.0) + bounds.exactCenterY()).toFloat()

            // Update the path each time. This works around an issue in SKIA
            // where concatenating a rotation matrix to a scale matrix
            // ignored a starting negative rotation. This appears to have
            // been fixed as of API 21.
            mArrow.moveTo(0f, 0f)
            mArrow.lineTo(mArrowWidth * mArrowScale, 0f)
            mArrow.lineTo((mArrowWidth * mArrowScale / 2), ((mArrowHeight * mArrowScale)))
            mArrow.offset(x - inset, y)
            mArrow.close()

            // draw a triangle
            mArrowPaint.color = mColors[mColorIndex]
            c.rotate(
                startAngle + sweepAngle - ARROW_OFFSET_ANGLE,
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )
            c.drawPath(mArrow, mArrowPaint)
        }

        /**
         * Set the colors the progress spinner alternates between.
         *
         * @param colors Array of integers describing the colors
         */
        fun setColors(colors: IntArray) {
            mColors = colors
            setColorIndex(0)
        }

        /**
         * @param index Index into the color array of the color to display in the progress spinner.
         */
        fun setColorIndex(index: Int) {
            mColorIndex = index
        }

        /** Proceed to the next available ring color, wraps automatically */
        fun goToNextColor() {
            mColorIndex = (mColorIndex + 1) % (mColors.size)
        }

        fun setColorFilter(filter: ColorFilter?) {
            mPaint.colorFilter = filter
            invalidateSelf()
        }

        /** Set the stroke width of the progress spinner in pixels */
        var strokeWidth: Float
            get() = mStrokeWidth
            set(strokeWidth) {
                mStrokeWidth = strokeWidth
                mPaint.strokeWidth = strokeWidth
                invalidateSelf()
            }

        var startTrim: Float
            get() = mStartTrim
            set(startTrim) {
                mStartTrim = startTrim
                invalidateSelf()
            }

        var endTrim: Float
            get() = mEndTrim
            set(endTrim) {
                mEndTrim = endTrim
                invalidateSelf()
            }

        var rotation: Float
            get() = mRotation
            set(rotation) {
                mRotation = rotation
                invalidateSelf()
            }

        fun setInsets(width: Int, height: Int) {
            val minEdge = width.coerceAtMost(height).toFloat()
            this.insets = if (centerRadius <= 0 || minEdge < 0) {
                ceil((mStrokeWidth / 2.0f).toDouble()).toFloat()
            } else {
                (minEdge / 2.0f - centerRadius).toFloat()
            }
        }

        /**
         * @param show Set to true to show the arrow head on the progress spinner.
         */
        fun setShowArrow(show: Boolean) {
            if (mShowArrow != show) {
                mShowArrow = show
                invalidateSelf()
            }
        }

        /**
         * @param scale Set the scale of the arrowhead for the spinner.
         */
        fun setArrowScale(scale: Float) {
            if (scale != mArrowScale) {
                mArrowScale = scale
                invalidateSelf()
            }
        }

        /** Store the start/end trim so that animation starts from that offset */
        fun storeOriginals() {
            startingStartTrim = mStartTrim
            startingEndTrim = mEndTrim
            startingRotation = mRotation
        }

        /** Reset the progress spinner to default rotation, start and end angles. */
        fun resetOriginals() {
            startingStartTrim = 0f
            startingEndTrim = 0f
            startingRotation = 0f
            startTrim = 0f
            endTrim = 0f
            rotation = 0f
        }

        private fun invalidateSelf() { mCallback.invalidateDrawable(this@MaterialDrawable) }
    }

    /** Squishes the interpolation curve into the second half of the animation. */
    private class EndCurveInterpolator : AccelerateDecelerateInterpolator() {
        override fun getInterpolation(input: Float) = super.getInterpolation(
            max(0f, (input - 0.5f) * 2.0f)
        )
    }

    /** Squishes the interpolation curve into the first half of the animation. */
    private class StartCurveInterpolator : AccelerateDecelerateInterpolator() {
        override fun getInterpolation(input: Float) = super.getInterpolation(max(1f, input * 2.0f))
    }

    companion object {
        private val LINEAR_INTERPOLATOR: Interpolator = LinearInterpolator()
        private val END_CURVE_INTERPOLATOR: Interpolator = EndCurveInterpolator()
        private val START_CURVE_INTERPOLATOR: Interpolator = StartCurveInterpolator()
        private val EASE_INTERPOLATOR: Interpolator = AccelerateDecelerateInterpolator()

        // Maps to ProgressBar.Large style
        const val LARGE = 0

        // Maps to ProgressBar default style
        const val DEFAULT = 1

        // Maps to ProgressBar default style
        private const val CIRCLE_DIAMETER = 40
        private const val CENTER_RADIUS = 8.75f //should add up to 10 when + stroke_width
        private const val STROKE_WIDTH = 2.5f

        // Maps to ProgressBar.Large style
        private const val CIRCLE_DIAMETER_LARGE = 56
        private const val CENTER_RADIUS_LARGE = 12.5f
        private const val STROKE_WIDTH_LARGE = 3f
        private const val MAX_PROGRESS_ANGLE = .8f
        private const val CIRCLE_BG_LIGHT = -0x50506
        private const val MAX_ALPHA = 255

        private const val ANIMATION_DURATION_MS = 1000 * 80 / 60

        /** The number of points in the progress "star" */
        private const val NUM_POINTS = 5f

        private const val ARROW_WIDTH_DP = 10
        private const val ARROW_HEIGHT_DP = 5
        private const val ARROW_OFFSET_ANGLE = 5f

        private const val ARROW_WIDTH_LARGE_DP = 12
        private const val ARROW_HEIGHT_LARGE_DP = 6
        private const val MAX_PROGRESS_ARC = .8f

        /** Circle Drawable */
        private const val KEY_SHADOW_COLOR = 0x1E000000
        private const val FILL_SHADOW_COLOR = 0x3D000000

        // PX
        private const val X_OFFSET = 0f
        private const val Y_OFFSET = 1.75f
        private const val SHADOW_RADIUS = 3.5f
    }
}
