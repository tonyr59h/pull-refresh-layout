package com.xponential.widget

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable

abstract class RefreshDrawable(
    protected val refreshLayout: PullRefreshLayout
) : Drawable(), Drawable.Callback, Animatable {

    val context: Context
        get() = refreshLayout.context

    abstract fun setPercent(newPercent: Float)

    abstract fun setColorSchemeColors(colorSchemeColors: IntArray)

    abstract fun offsetTopAndBottom(offset: Int)

    override fun invalidateDrawable(who: Drawable) { callback?.invalidateDrawable(this) }

    override fun scheduleDrawable(who: Drawable, what: Runnable, cuando: Long) {
        callback?.scheduleDrawable(this, what, cuando)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        callback?.unscheduleDrawable(this, what)
    }

    @Deprecated("No longer used for optimizations", ReplaceWith("N/A"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(cf: ColorFilter?) {}
}
