package com.xponential.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.Transformation
import android.widget.ImageView
import java.security.InvalidParameterException
import java.util.logging.Logger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Suppress("MemberVisibilityCanBePrivate")
open class PullRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {
    private var mTarget: View? = null
    private var mRefreshView: ImageView? = null
    private val mDecelerateInterpolator: Interpolator
    private val mTouchSlop: Int
    internal var finalOffset: Int = 0
    private val mTotalDragDistance: Int
    private var mRefreshDrawable: RefreshDrawable? = null
    private var mCurrentOffsetTop = 0
    private var mRefreshing = false
    private var mActivePointerId = 0
    private var mIsBeingDragged = false
    private var mInitialMotionY = 0f
    private var mFrom = 0
    private var mNotify = false
    private var mListener: OnRefreshListener? = null
    private var mColorSchemeColors: IntArray
    private var mDurationToStartPosition: Int
    private var mDurationToCorrectPosition: Int
    private var mInitialOffsetTop = 0
    private var mDispatchTargetTouchDown = false
    private var mDragPercent = 0f

    fun setColorSchemeColors(vararg colorSchemeColors: Int) {
        mColorSchemeColors = colorSchemeColors
        mRefreshDrawable?.setColorSchemeColors(colorSchemeColors)
    }

    @Suppress("unused")
    fun setColor(color: Int) = setColorSchemeColors(color)

    fun setRefreshStyle(type: Int) {
        setRefreshing(false)
        mRefreshDrawable = when (type) {
            STYLE_MATERIAL -> MaterialDrawable(this)
            STYLE_CIRCLES -> CirclesDrawable(this)
            STYLE_WATER_DROP -> WaterDropDrawable(this)
            STYLE_RING -> RingDrawable(this)
            STYLE_SMARTISAN -> SmartisanDrawable(this)
            else -> throw InvalidParameterException("Type does not exist")
        }
        mRefreshDrawable?.setColorSchemeColors(mColorSchemeColors)
        mRefreshView?.setImageDrawable(mRefreshDrawable)
    }

    @Suppress("unused")
    fun setRefreshDrawable(drawable: RefreshDrawable?) {
        setRefreshing(false)
        mRefreshDrawable = drawable
        mRefreshDrawable?.setColorSchemeColors(mColorSchemeColors)
        mRefreshView?.setImageDrawable(mRefreshDrawable)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthSpec = widthMeasureSpec
        var heightSpec = heightMeasureSpec
        super.onMeasure(widthSpec, heightSpec)
        ensureTarget()
        if (mTarget == null) return
        widthSpec = MeasureSpec.makeMeasureSpec(
            measuredWidth - paddingRight - paddingLeft,
            MeasureSpec.EXACTLY
        )
        heightSpec = MeasureSpec.makeMeasureSpec(
            measuredHeight - paddingTop - paddingBottom,
            MeasureSpec.EXACTLY
        )
        mTarget!!.measure(widthSpec, heightSpec)
        mRefreshView?.measure(widthSpec, heightSpec)
        //        mRefreshView?.measure(MeasureSpec.makeMeasureSpec(mRefreshViewWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(mRefreshViewHeight, MeasureSpec.EXACTLY));
    }

    private fun ensureTarget() {
        if (mTarget != null) return
        if (childCount > 0) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child !== mRefreshView) mTarget = child
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || canChildScrollUp() && !mRefreshing) return false

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!mRefreshing) {
                    setTargetOffsetTop(0)
                }
                mActivePointerId = ev.getPointerId(0)
                mIsBeingDragged = false
                val initialMotionY = getMotionEventY(ev, mActivePointerId)
                if (initialMotionY == -1f) return false
                mInitialMotionY = initialMotionY
                mInitialOffsetTop = mCurrentOffsetTop
                mDispatchTargetTouchDown = false
                mDragPercent = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                if (mActivePointerId == INVALID_POINTER) return false
                val y = getMotionEventY(ev, mActivePointerId)
                if (y == -1f) return false

                val yDiff = y - mInitialMotionY
                if (mRefreshing) {
                    mIsBeingDragged = !(yDiff < 0 && mCurrentOffsetTop <= 0)
                } else if (yDiff > mTouchSlop && !mIsBeingDragged) {
                    mIsBeingDragged = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mIsBeingDragged = false
                mActivePointerId = INVALID_POINTER
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
        }
        return mIsBeingDragged
    }

    private fun doTouchEvent(ev: MotionEvent): Boolean {
        if (!mIsBeingDragged) return super.onTouchEvent(ev)

        when (ev.action) {
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex: Int = ev.findPointerIndex(mActivePointerId)
                if (pointerIndex < 0) return false

                val y: Float = ev.getY(pointerIndex)
                val yDiff = y - mInitialMotionY
                var targetY: Int
                if (mRefreshing) {
                    targetY = (mInitialOffsetTop + yDiff).toInt()
                    if (canChildScrollUp()) {
                        targetY = -1
                        mInitialMotionY = y
                        mInitialOffsetTop = 0
                        if (mDispatchTargetTouchDown) {
                            mTarget!!.dispatchTouchEvent(ev)
                        } else {
                            val obtain = MotionEvent.obtain(ev)
                            obtain.action = MotionEvent.ACTION_DOWN
                            mDispatchTargetTouchDown = true
                            mTarget!!.dispatchTouchEvent(obtain)
                        }
                    } else {
                        if (targetY < 0) {
                            if (mDispatchTargetTouchDown) {
                                mTarget!!.dispatchTouchEvent(ev)
                            } else {
                                val obtain = MotionEvent.obtain(ev)
                                obtain.action = MotionEvent.ACTION_DOWN
                                mDispatchTargetTouchDown = true
                                mTarget!!.dispatchTouchEvent(obtain)
                            }
                            targetY = 0
                        } else if (targetY > mTotalDragDistance) {
                            targetY = mTotalDragDistance
                        } else {
                            if (mDispatchTargetTouchDown) {
                                val obtain = MotionEvent.obtain(ev)
                                obtain.action = MotionEvent.ACTION_CANCEL
                                mDispatchTargetTouchDown = false
                                mTarget!!.dispatchTouchEvent(obtain)
                            }
                        }
                    }
                } else {
                    val scrollTop = yDiff * DRAG_RATE
                    val originalDragPercent = scrollTop / mTotalDragDistance
                    if (originalDragPercent < 0) return false

                    mDragPercent = min(1f, abs(originalDragPercent))
                    val extraOS = abs(scrollTop) - mTotalDragDistance
                    val slingshotDist = finalOffset.toFloat()
                    val tensionSlingshotPercent =
                        max(0f, min(extraOS, slingshotDist * 2) / slingshotDist)
                    val tensionPercent =
                        (tensionSlingshotPercent / 4 - (tensionSlingshotPercent / 4)
                            .toDouble()
                            .pow(2.0))
                            .toFloat() * 2f
                    val extraMove = slingshotDist * tensionPercent * 2
                    targetY = (slingshotDist * mDragPercent + extraMove).toInt()
                    if (mRefreshView?.visibility != VISIBLE) {
                        mRefreshView?.visibility = VISIBLE
                    }
                    if (scrollTop < mTotalDragDistance) {
                        mRefreshDrawable?.setPercent(mDragPercent)
                    }
                }
                setTargetOffsetTop(targetY - mCurrentOffsetTop)
            }
            MotionEvent.ACTION_POINTER_DOWN -> mActivePointerId = ev.getPointerId(ev.actionIndex)
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mActivePointerId == INVALID_POINTER) return false

                if (mRefreshing) {
                    if (mDispatchTargetTouchDown) {
                        mTarget!!.dispatchTouchEvent(ev)
                        mDispatchTargetTouchDown = false
                    }
                    return false
                }
                val pointerIndex: Int = ev.findPointerIndex(mActivePointerId)
                val y: Float = ev.getY(pointerIndex)
                val overscrollTop = (y - mInitialMotionY) * DRAG_RATE
                mIsBeingDragged = false
                if (overscrollTop > mTotalDragDistance) {
                    setRefreshing(refreshing = true, notify = true)
                } else {
                    mRefreshing = false
                    animateOffsetToStartPosition()
                }
                mActivePointerId = INVALID_POINTER
                return false
            }
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return try {
            doTouchEvent(ev)
        } catch (t: Throwable) {
            Logger.getGlobal().warning("PullRefreshLayout::onTouchEvent(error=${t.message})")
            false
        }
    }

    @Suppress("unused")
    fun setDurations(durationToStartPosition: Int, durationToCorrectPosition: Int) {
        mDurationToStartPosition = durationToStartPosition
        mDurationToCorrectPosition = durationToCorrectPosition
    }

    private fun animateOffsetToStartPosition() {
        mFrom = mCurrentOffsetTop
        mAnimateToStartPosition.reset()
        mAnimateToStartPosition.duration = mDurationToStartPosition.toLong()
        mAnimateToStartPosition.interpolator = mDecelerateInterpolator
        mAnimateToStartPosition.setAnimationListener(mToStartListener)
        mRefreshView?.clearAnimation()
        mRefreshView?.startAnimation(mAnimateToStartPosition)
    }

    private fun animateOffsetToCorrectPosition() {
        mFrom = mCurrentOffsetTop
        mAnimateToCorrectPosition.reset()
        mAnimateToCorrectPosition.duration = mDurationToCorrectPosition.toLong()
        mAnimateToCorrectPosition.interpolator = mDecelerateInterpolator
        mAnimateToCorrectPosition.setAnimationListener(mRefreshListener)
        mRefreshView?.clearAnimation()
        mRefreshView?.startAnimation(mAnimateToCorrectPosition)
    }

    private val mAnimateToStartPosition: Animation = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            moveToStart(interpolatedTime)
        }
    }

    private val mAnimateToCorrectPosition: Animation = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val endTarget: Int = finalOffset
            val targetTop = mFrom + ((endTarget - mFrom) * interpolatedTime).toInt()
            val offset = targetTop - mTarget!!.top
            setTargetOffsetTop(offset)
        }
    }

    private fun moveToStart(interpolatedTime: Float) {
        val targetTop = mFrom - (mFrom * interpolatedTime).toInt()
        val offset = targetTop - mTarget!!.top
        setTargetOffsetTop(offset)
        mRefreshDrawable?.setPercent(mDragPercent * (1 - interpolatedTime))
    }

    fun setRefreshing(refreshing: Boolean) {
        if (mRefreshing != refreshing) {
            setRefreshing(refreshing, false /* notify */)
        }
    }

    private fun setRefreshing(refreshing: Boolean, notify: Boolean) {
        if (mRefreshing != refreshing) {
            mNotify = notify
            ensureTarget()
            mRefreshing = refreshing
            if (mRefreshing) {
                mRefreshDrawable?.setPercent(1f)
                animateOffsetToCorrectPosition()
            } else {
                animateOffsetToStartPosition()
            }
        }
    }

    private val mRefreshListener: AnimationListener = object : AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            mRefreshView?.visibility = VISIBLE
        }

        override fun onAnimationRepeat(animation: Animation) {}
        override fun onAnimationEnd(animation: Animation) {
            if (mRefreshing) {
                mRefreshDrawable?.start()
                if (mNotify) {
                    if (mListener != null) {
                        mListener!!.onRefresh()
                    }
                }
            } else {
                mRefreshDrawable?.stop()
                mRefreshView?.visibility = GONE
                animateOffsetToStartPosition()
            }
            mCurrentOffsetTop = mTarget!!.top
        }
    }
    private val mToStartListener: AnimationListener = object : AnimationListener {
        override fun onAnimationRepeat(animation: Animation) {}

        override fun onAnimationStart(animation: Animation) { mRefreshDrawable?.stop() }

        override fun onAnimationEnd(animation: Animation) {
            mRefreshView?.visibility = GONE
            mCurrentOffsetTop = mTarget!!.top
        }
    }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.PullRefreshLayout)
        val type = a.getInteger(R.styleable.PullRefreshLayout_refreshType, STYLE_MATERIAL)
        val colorsId = a.getResourceId(R.styleable.PullRefreshLayout_refreshColors, 0)
        val colorId = a.getResourceId(R.styleable.PullRefreshLayout_refreshColor, 0)
        a.recycle()
        mDecelerateInterpolator = DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR)
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val defaultDuration = resources.getInteger(android.R.integer.config_mediumAnimTime)
        mDurationToStartPosition = defaultDuration
        mDurationToCorrectPosition = defaultDuration
        mTotalDragDistance = dp2px(DRAG_MAX_DISTANCE)
        finalOffset = mTotalDragDistance
        mColorSchemeColors = if (colorsId > 0) {
            context.resources.getIntArray(colorsId)
        } else {
            intArrayOf(
                Color.rgb(0xC9, 0x34, 0x37),
                Color.rgb(0x37, 0x5B, 0xF1),
                Color.rgb(0xF7, 0xD2, 0x3E),
                Color.rgb(0x34, 0xA3, 0x50)
            )
        }

        if (colorId > 0) mColorSchemeColors = intArrayOf(resources.getColor(colorId, context.theme))

        mRefreshView = ImageView(context)
        setRefreshStyle(type)
        mRefreshView?.visibility = GONE
        this.addView(mRefreshView, 0)
        this.setWillNotDraw(false)
        isChildrenDrawingOrderEnabled = true
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex: Int = ev.actionIndex
        val pointerId: Int = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            mActivePointerId = ev.getPointerId(newPointerIndex)
        }
    }

    private fun getMotionEventY(ev: MotionEvent, activePointerId: Int): Float {
        val index: Int = ev.findPointerIndex(activePointerId)
        return if (index < 0) -1f else ev.getY(index)
    }

    private fun setTargetOffsetTop(offset: Int) {
        mTarget!!.offsetTopAndBottom(offset)
        mCurrentOffsetTop = mTarget!!.top
        mRefreshDrawable?.offsetTopAndBottom(offset)
    }

    private fun canChildScrollUp() = mTarget?.canScrollVertically(-1) == true

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        ensureTarget()
        if (mTarget == null) return

        val height = measuredHeight
        val width = measuredWidth
        val left = paddingLeft
        val top = paddingTop
        val right = paddingRight
        val bottom = paddingBottom
        mTarget!!.layout(
            left,
            top + mTarget!!.top,
            left + width - right,
            top + height - bottom + mTarget!!.top
        )
        mRefreshView?.layout(left, top, left + width - right, top + height - bottom)
    }

    @Suppress("SameParameterValue")
    private fun dp2px(dp: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()

    @Suppress("unused")
    fun setOnRefreshListener(listener: OnRefreshListener?) {
        mListener = listener
    }

    interface OnRefreshListener {
        fun onRefresh()
    }

    companion object {
        private const val DECELERATE_INTERPOLATION_FACTOR = 2f
        private const val DRAG_MAX_DISTANCE = 64
        private const val INVALID_POINTER = -1
        private const val DRAG_RATE = .5f
        const val STYLE_MATERIAL = 0
        const val STYLE_CIRCLES = 1
        const val STYLE_WATER_DROP = 2
        const val STYLE_RING = 3
        const val STYLE_SMARTISAN = 4
    }
}
