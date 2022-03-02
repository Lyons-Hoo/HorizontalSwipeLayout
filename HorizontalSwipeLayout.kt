package com.bean.airecordmodule.ui.widget

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.widget.LinearLayout
import androidx.core.animation.addListener
import androidx.core.view.children
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import com.bean.base.screen.utils.ScreenUtil
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Describe: 侧滑容器
 * Created by huyi on 2022/01/14.
 * 使用方式：将此容器作为可滑动item的容器，第一个子view为显示出来的view（折叠状态的view），
 * 后面的其他子view则作为菜单项（展开后显示的view）。
 */
typealias ExpandStateChangeAction = (swipeLayout : HorizontalSwipeLayout, isExpanded : Boolean) -> Unit
class HorizontalSwipeLayout(context: Context?, attrs: AttributeSet? = null) :
    ViewGroup(context, attrs, 0) {

    private var mCanSliding = false // 标识是否可以滑动
    private var mScaledTouchSlop = 0 // 移动距离超过阈值则自动滑动
    private var mMaximumFlingVelocity = 0f // 最大滑动速度
//    private var mRealWidth = 0 // 记录实际的宽度(可能大于屏幕宽度)
    private var mMenuWidth = 0 // 记录除第一个子view外其他view（菜单项）的宽度和，避免频繁计算

    /*++++++++++++++++++++++++++++++++外部可选配置++++++++++++++++++++++++++++++++*/
    /*控制点击菜单时是否自动折叠，默认否*/
    var isClickFirstChildToCollapsed : Boolean
        get() = hasOnClickListeners()
        set(value) {
            if(value) {
                setOnClickListener {
                    collapse()
                }
            } else setOnClickListener(null)
        }
    /*控制点击菜单项，是否折叠菜单。默认是*/
    var isClickMenuToCollapse = true
    /*展开状态改变时执行动作（监听）*/
    var expandStateChangeBeforeAction : ExpandStateChangeAction? = null
    var expandStateChangeAction : ExpandStateChangeAction? = null
    /*++++++++++++++++++++++++++++++++外部可选配置++++++++++++++++++++++++++++++++*/

    init {
        this.mScaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
        this.mMaximumFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity * 1.0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        d("-------------onMeasure------------")
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var selfWidth = 0 // 自身宽度
        var selfHeight = 0 // 自身高度
//        this.mRealWidth = 0
        this.mMenuWidth = 0

        var childHeightHasMatchParentMode = false // 子view的高度是否有设置match_parent
        val selfHeightMode = MeasureSpec.getMode(heightMeasureSpec) // 自身高度模式（layout_height）

        this.children.forEachIndexed { index, child ->
            // 测量当前子View
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            // 计算当前子View的实际所占空间（宽度、高度、Margin信息）
            var childSpaceWidth = child.measuredWidth
            var childSpaceHeight = child.measuredHeight

            val childLayoutParams = child.layoutParams
            // 非精确模式（指定了具体大小或match_parent）
            if(selfHeightMode != MeasureSpec.EXACTLY && childLayoutParams.height == LayoutParams.MATCH_PARENT) {
                childHeightHasMatchParentMode = true // 需要将子view的高度设置为容器的高度
            }
            // 获取子View的Margin信息
            if(childLayoutParams is MarginLayoutParams) {
                childSpaceWidth += childLayoutParams.leftMargin + childLayoutParams.rightMargin
                childSpaceHeight += childLayoutParams.topMargin + childLayoutParams.bottomMargin
            }
            if(index == 0) selfWidth = childSpaceWidth // 整个容器的宽度取决于第一个子view的宽度
            else this.mMenuWidth += childSpaceWidth // 菜单项的宽度
//            this.mRealWidth += childRealWidth // 实际宽度是所有子view的宽度之和
            selfHeight = max(selfHeight, childSpaceHeight) // 高度取最高子view的高度
        }
        // 容器宽高不能超出屏幕
        val screenSize = ScreenUtil.getScreenSize(this.context)
        val measuredWidth = min(this.paddingLeft + selfWidth + this.paddingRight, screenSize[0])
        val measuredHeight = min(this.paddingTop + selfHeight + this.paddingBottom, screenSize[1])
        setMeasuredDimension(measuredWidth, measuredHeight)
        if(childHeightHasMatchParentMode) makeChildHeightToMatchParent(widthMeasureSpec)
    }

    /**
     * 支持子view设置高度为match_parent
     * 参考 [LinearLayout.forceUniformHeight]
     */
    private fun makeChildHeightToMatchParent(widthMeasureSpec: Int) {
        val uniformMeasureSpec = MeasureSpec.makeMeasureSpec(
            measuredHeight,
            MeasureSpec.EXACTLY
        )
        this.children.forEach { child ->
            if (child.isVisible) {
                val childLayoutParams = child.layoutParams
                if (childLayoutParams.height == LayoutParams.MATCH_PARENT) {
                    // Temporarily force children to reuse their old measured width
                    // FIXME: this may not be right for something like wrapping text?
                    val oldWidth = childLayoutParams.width
                    childLayoutParams.width = child.measuredWidth

                    // Remeasure with new dimensions
                    var horizontalPadding = this.paddingLeft + this.paddingRight
                    var verticalPadding = this.paddingTop + this.paddingBottom
                    if(childLayoutParams is MarginLayoutParams) {
                        horizontalPadding += childLayoutParams.leftMargin + childLayoutParams.rightMargin
                        verticalPadding += childLayoutParams.topMargin + childLayoutParams.bottomMargin
                    }
                    val childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, horizontalPadding, childLayoutParams.width)
                    val childHeightMeasureSpec = getChildMeasureSpec(uniformMeasureSpec, verticalPadding, childLayoutParams.height)
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec)

                    childLayoutParams.width = oldWidth
                }
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var endX = 0 // X轴布局到哪里
        this.mCanSliding = this.childCount > 1 // 至少两个子view才可以滑动
        this.children.forEach { child ->
            if(child.isVisible) {
                val childMeasureWidth = child.measuredWidth
                val childMeasureHeight = child.measuredHeight

                // 如果子View宽高有设置MATCH_PARENT模式，将容器的宽高赋给它
                val layoutParams = child.layoutParams
                // 计算子ViewX轴从哪里开始布局
                var startX = endX
                // 计算子ViewX轴布局到哪里
                endX = startX + childMeasureWidth

                // 计算子ViewY轴从哪里开始布局
                var startY = 0
                // 计算子ViewY轴布局到哪里
                var endY = startY + childMeasureHeight

                // 获取子View的Margin信息
                if(layoutParams is MarginLayoutParams) {
                    startX += layoutParams.leftMargin
                    endX -= layoutParams.rightMargin
                    startY += layoutParams.topMargin
                    endY -= layoutParams.bottomMargin
                }
                // 子View的位置都确定了，可以进行布局了
                child.layout(startX, startY, endX, endY)
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // 不可滑动或动画执行中不再往下分发事件
        val animator = this.mAnimatorCache
        val condition = when {
            animator == null -> true
            animator.isPaused -> true
            !animator.isStarted -> true
            else -> false
        }
        return if(this.mCanSliding || condition) {
            val isChildConsumed = super.dispatchTouchEvent(ev)
            if(this.isClickMenuToCollapse) {
                // 事件被非第一个子view消耗了，并且目前处于展开状态，则自动收起
                when (ev?.action) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isChildConsumed && !isFirstChildConsumeEvent(ev)) collapse()
                    }
                }
            }
            isChildConsumed
        } else false
    }

    /**
     * 是否第一个子view消耗了事件
     */
    private fun isFirstChildConsumeEvent(event : MotionEvent) : Boolean {
        if(isNotEmpty()) {
            val x = event.rawX.toInt()
            val y = event.rawY.toInt()
            val firstChildView = getChildAt(0)
            val location = IntArray(2)
            firstChildView.getLocationOnScreen(location)
            val left = location[0]
            val top = location[1]
            val right = left + firstChildView.measuredWidth
            val bottom: Int = top + firstChildView.measuredHeight
            if (y in top..bottom && x >= left && x <= right) {
                return true
            }
        }
        return false
    }

//    private val mScroller by lazy { Scroller(context) } // 滚动器
    private var mTracker : VelocityTracker? = null // 速率器（指定时间内运动的像素值）
    private var mTouchPointX = 0f // 记录触摸的x点坐标
    /**
     * 处理分发下来的事件
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if(null == this.mTracker) this.mTracker = VelocityTracker.obtain() // 实例化速率器
        when(event?.action) {
            MotionEvent.ACTION_DOWN -> {
                this.mTouchPointX = event.x
            }
            MotionEvent.ACTION_MOVE -> {
//                d("event.x:${event.x}, touchPointX:$mTouchPointX")
                if(abs(event.x - this.mTouchPointX) >= 20) { // 横向滑动
//                    d("scrollX:$scrollX, menuWidth:$mMenuWidthCache")
                    this.mTracker?.let {
                        it.addMovement(event) // 将事件传递给速率器
                        it.computeCurrentVelocity(40, this.mMaximumFlingVelocity) // 设置速率器时间（单位为毫秒）
                        val x = (-it.xVelocity / 3).toInt() // 随着手指移动，并设置了2/3的阻尼
//                        d("move x:$x")
                        scrollBy(x, 0)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                this.mTracker?.let {
//                    d("xVelocity: ${it.xVelocity}, scrollX:$scrollX, realWidth:$mRealWidth, menuWidth:$mMenuWidthCache")
                    when {
                        // 没有滑动说明是点击
                        it.xVelocity == 0f && it.yVelocity == 0f && this.isClickFirstChildToCollapsed -> performClick() // 因为事件分发我们自己处理了，所以需要模拟点击
//                        abs(event.x - this.mTouchPointX) >= this.mScaledTouchSlop -> { // 滑动距离超过阈值
                        else -> { // 滑动距离超过阈值
//                            // 这种滑动方式，滑动动画时间不可控（默认太快了）
//                            if(it.xVelocity <= 0) this.mScroller.startScroll(scrollX, 0, this.mRealWidth + scrollX, 0) // 向左滑动
//                            else this.mScroller.startScroll(scrollX, 0, 0, 0) // 向右滑动
//                            invalidate()

                            if(it.xVelocity <= 0f) expand() // 向左滑动展开
                            else collapse() // 向右滑动折叠
                        }
//                        else -> {}
                    }
                }
                this.mTracker?.recycle()
                this.mTracker = null
            }
        }
        return true
    }

    /*记录是否处于展开状态*/
    private var mIsExpanded = false
    val isExpanded : Boolean
        get() = this.mIsExpanded

    /**
     * 展开
     */
    fun expand() {
        if(!this.mIsExpanded) {
            startScrollAnimation(
                scrollX, this.mMenuWidth,
                startAction = { expandStateChangeBeforeAction?.invoke(this, false) },
                endAction = {
                    this.mIsExpanded = true
                    expandStateChangeAction?.invoke(this, true)
                }
            )
        }
    }

    /**
     * 折叠
     */
    fun collapse() {
        if(this.mIsExpanded) {
            startScrollAnimation(
                scrollX, 0, duration = 250,
                startAction = { expandStateChangeBeforeAction?.invoke(this, true) },
                endAction = {
                    this.mIsExpanded = false
                    expandStateChangeAction?.invoke(this,false)
                }
            )
        }
    }

    private var mAnimatorCache : ValueAnimator? = null
    private inline fun startScrollAnimation(from : Int, to : Int,
                                            duration : Long = 300L,
                                            crossinline startAction : (Animator) -> Unit,
                                            crossinline endAction : (Animator) -> Unit) {
        releaseAnimation(true)
        this.mAnimatorCache = ObjectAnimator.ofInt(from, to).apply {
            this.duration = duration
            this.repeatCount = 0
            addUpdateListener { animation ->
                val value = animation.animatedValue
                if (value is Int) scrollTo(value, 0)
            }
            val onEnd : (Animator) -> Unit = {
                releaseAnimation()
                endAction(it)
            }
            addListener(
                onStart = startAction,
                onCancel = onEnd,
                onEnd = onEnd
            )
            mAnimatorCache = this
            start()
        }
    }

    private fun releaseAnimation(needCancel : Boolean = false) {
        this.mAnimatorCache?.let {
            if(needCancel && it.isRunning) {
                try { it.cancel() }
                catch (e : Exception) {}
            }
            it.removeAllUpdateListeners()
            it.removeAllListeners()
        }
        this.mAnimatorCache = null
    }

    override fun scrollTo(x: Int, y: Int) {
        // 限制滑动范围为：0~最大宽度
        val newX = when {
            x < 0 -> 0
            x > this.mMenuWidth -> this.mMenuWidth
            else -> x
        }
        super.scrollTo(newX, y)
    }

    /**
     * 默认可以使用margin
     */
    override fun generateDefaultLayoutParams(): LayoutParams {
        return MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

//    private fun d(log : String) = Log.d(this::class.java.simpleName, log)
}