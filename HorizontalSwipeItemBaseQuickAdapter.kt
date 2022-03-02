package com.bean.airecordmodule.ui.adapter

import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder

/**
 * Describe:带有侧滑的列表，扩展 展开一个item自动关闭上一个item的功能
 * Created by huyi on 2022/01/19.
 */
abstract class HorizontalSwipeItemBaseQuickAdapter<ItemData, ViewHolder : BaseViewHolder>(
    @LayoutRes layoutResId: Int,
    data: MutableList<ItemData>? = null
) : BaseQuickAdapter<ItemData, ViewHolder>(layoutResId, data) {

    private var mExpandedStateChangeBeforeAction : ExpandStateChangeAction? = null
    private var mLastExpandedSwipeLayoutCache : HorizontalSwipeLayout? = null

    init {
        this.mExpandedStateChangeBeforeAction = { swipeLayout, isExpanded ->
            if(!isExpanded && this.mLastExpandedSwipeLayoutCache != swipeLayout) { // 即将展开
                this.mLastExpandedSwipeLayoutCache?.collapse() // 将上一个item折叠
                this.mLastExpandedSwipeLayoutCache = swipeLayout
            }
        }
    }

    override fun onItemViewHolderCreated(viewHolder: ViewHolder, viewType: Int) {
        val itemView = viewHolder.itemView
        if(itemView is HorizontalSwipeLayout) {
            itemView.expandStateChangeBeforeAction = this.mExpandedStateChangeBeforeAction
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.mExpandedStateChangeBeforeAction = null
        this.mLastExpandedSwipeLayoutCache = null
    }
}
