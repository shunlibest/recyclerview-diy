package com.shuhnli.recyclerview_diy.childUtil;

import android.view.View;
import android.view.ViewGroup;

import com.shuhnli.recyclerview_diy.recyclerview.ViewHolder;

public interface ChildHelperCallBack {

    int getChildCount();

    void addView(View child, int index);

    int indexOfChild(View view);

    void removeViewAt(int index);

    View getChildAt(int offset);

    void removeAllViews();

    ViewHolder getChildViewHolder(View view);

    void attachViewToParent(View child, int index, ViewGroup.LayoutParams layoutParams);

    void detachViewFromParent(int offset);

    void onEnteredHiddenState(View child);

    void onLeftHiddenState(View child);
}
