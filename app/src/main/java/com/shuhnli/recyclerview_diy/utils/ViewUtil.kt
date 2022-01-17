package com.shuhnli.recyclerview_diy.utils

import android.view.View
import com.shuhnli.recyclerview_diy.layoutManager.LayoutParams
import com.shuhnli.recyclerview_diy.recyclerview.RecyclerView
import com.shuhnli.recyclerview_diy.recyclerview.ViewHolder



//fun View.getViewHolder(): ViewHolder {
//    return (this.layoutParams as LayoutParams).mViewHolder
//}


fun getViewHolder(view :View): ViewHolder {
    return (view.layoutParams as LayoutParams).mViewHolder
}