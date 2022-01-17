/*
 * Copyright 2018 The Android Open Source Project
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

package com.shuhnli.recyclerview_diy.childUtil;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.shuhnli.recyclerview_diy.recyclerview.ViewHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理子对象的Helper类。
 * 它包装了一个RecyclerView，并增加了隐藏一些孩子的能力。
 * 这个类提供了两组方法。常规方法是复制ViewGroup方法的方法，如getChildAt, getChildCount等。
 * 这些方法忽略了隐藏的子对象。 当RecyclerView需要直接访问视图组的子视图时，它可以调用未过滤的方法，
 * 比如get getUnfilteredChildCount或getUnfilteredChildAt。
 */
public class ChildHelper {

    private static final String TAG = "ChildrenHelper";

    final ChildHelperCallBack mCallback;

    final Bucket mBucket = new Bucket();

    final List<View> mHiddenViews = new ArrayList<View>();

    public ChildHelper(ChildHelperCallBack callback) {
        mCallback = callback;
    }

    //把一个子View, 添加到隐藏列表里
    private void hideViewInternal(View child) {
        mHiddenViews.add(child);
        mCallback.onEnteredHiddenState(child);
    }

    //取消标记隐藏
    private boolean unhideViewInternal(View child) {
        if (mHiddenViews.remove(child)) {
            mCallback.onLeftHiddenState(child);
            return true;
        } else {
            return false;
        }
    }

    //添加View到List内
    void addView(View child, boolean hidden) {
        addView(child, -1, hidden);
    }

    public void addView(View child, final int index, boolean hidden) {
        final int offset;
        if (index < 0) {
            offset = mCallback.getChildCount();
        } else {
            offset = getOffset(index);
        }
        mBucket.insert(offset, hidden);
        if (hidden) {
            hideViewInternal(child);
        }
        mCallback.addView(child, offset);
        Log.d(TAG, "addViewAt " + index + ",h:" + hidden + ", " + this);
    }

    private int getOffset(int index) {
        if (index < 0) {
            return -1; //anything below 0 won't work as diff will be undefined.
        }
        final int limit = mCallback.getChildCount();
        int offset = index;
        while (offset < limit) {
            final int removedBefore = mBucket.countOnesBefore(offset);
            final int diff = index - (offset - removedBefore);
            if (diff == 0) {
                while (mBucket.get(offset)) { // ensure this offset is not hidden
                    offset++;
                }
                return offset;
            } else {
                offset += diff;
            }
        }
        return -1;
    }

    //从底层RecyclerView中移除提供的视图。
    public void removeView(View view) {
        int index = mCallback.indexOfChild(view);
        if (index < 0) {
            return;
        }
        if (mBucket.remove(index)) {
            unhideViewInternal(view);
        }
        mCallback.removeViewAt(index);
        Log.d(TAG, "remove View off:" + index + "," + this);
    }

    //根据index, 移除对应的view
    public void removeViewAt(int index) {
        final int offset = getOffset(index);
        final View view = mCallback.getChildAt(offset);
        if (view == null) {
            return;
        }
        if (mBucket.remove(offset)) {
            unhideViewInternal(view);
        }
        mCallback.removeViewAt(offset);
        Log.d(TAG, "removeViewAt " + index + ", off:" + offset + ", " + this);
    }

    //index换View
    public View getChildAt(int index) {
        final int offset = getOffset(index);
        return mCallback.getChildAt(offset);
    }

    //从ViewGroup中移除所有视图，包括隐藏的视图。
    void removeAllViewsUnfiltered() {
        mBucket.reset();
        for (int i = mHiddenViews.size() - 1; i >= 0; i--) {
            mCallback.onLeftHiddenState(mHiddenViews.get(i));
            mHiddenViews.remove(i);
        }
        mCallback.removeAllViews();
    }

    //根据位置找到一个正在消失的视图。
    View findHiddenNonRemovedView(int position) {
        final int count = mHiddenViews.size();
        for (int i = 0; i < count; i++) {
            final View view = mHiddenViews.get(i);
            ViewHolder holder = mCallback.getChildViewHolder(view);
            if (holder.getLayoutPosition() == position
                    && !holder.isInvalid()
                    && !holder.isRemoved()) {
                return view;
            }
        }
        return null;
    }

    /**
     * Attaches the provided view to the underlying ViewGroup.
     *
     * @param child        Child to attach.
     * @param index        Index of the child to attach in regular perspective.
     * @param layoutParams LayoutParams for the child.
     * @param hidden       If set to true, this item will be invisible to the regular methods.
     */
    public void attachViewToParent(View child, int index, ViewGroup.LayoutParams layoutParams,
                                   boolean hidden) {
        final int offset;
        if (index < 0) {
            offset = mCallback.getChildCount();
        } else {
            offset = getOffset(index);
        }
        mBucket.insert(offset, hidden);
        if (hidden) {
            hideViewInternal(child);
        }
        mCallback.attachViewToParent(child, offset, layoutParams);
        Log.d(TAG, "attach view to parent index:" + index + ",off:" + offset + ","
                + "h:" + hidden + ", " + this);
    }

    /**
     * Returns the number of children that are not hidden.
     *
     * @return Number of children that are not hidden.
     * @see #getChildAt(int)
     */
    public int getChildCount() {
        return mCallback.getChildCount() - mHiddenViews.size();
    }

    /**
     * Returns the total number of children.
     *
     * @return The total number of children including the hidden views.
     * @see #getUnfilteredChildAt(int)
     */
    int getUnfilteredChildCount() {
        return mCallback.getChildCount();
    }

    /**
     * Returns a child by ViewGroup offset. ChildHelper won't offset this index.
     *
     * @param index ViewGroup index of the child to return.
     * @return The view in the provided index.
     */
    View getUnfilteredChildAt(int index) {
        return mCallback.getChildAt(index);
    }

    //detach操作
    public void detachViewFromParent(int index) {
        final int offset = getOffset(index);
        mBucket.remove(offset);
        mCallback.detachViewFromParent(offset);
        Log.d(TAG, "detach view from parent " + index + ", off:" + offset);
    }

    /**
     * Returns the index of the child in regular perspective.
     *
     * @param child The child whose index will be returned.
     * @return The regular perspective index of the child or -1 if it does not exists.
     */
    public int indexOfChild(View child) {
        final int index = mCallback.indexOfChild(child);
        if (index == -1) {
            return -1;
        }
        if (mBucket.get(index)) {
            throw new IllegalArgumentException("cannot get index of a hidden child");
        }
        // reverse the index
        return index - mBucket.countOnesBefore(index);
    }

    /**
     * Returns whether a View is visible to LayoutManager or not.
     *
     * @param view The child view to check. Should be a child of the Callback.
     * @return True if the View is not visible to LayoutManager
     */
    public boolean isHidden(View view) {
        return mHiddenViews.contains(view);
    }

    /**
     * Marks a child view as hidden.
     *
     * @param view The view to hide.
     */
    void hide(View view) {
        final int offset = mCallback.indexOfChild(view);
        if (offset < 0) {
            throw new IllegalArgumentException("view is not a child, cannot hide " + view);
        }
        if (mBucket.get(offset)) {
            throw new RuntimeException("trying to hide same view twice, how come ? " + view);
        }
        mBucket.set(offset);
        hideViewInternal(view);
        Log.d(TAG, "hiding child " + view + " at offset " + offset + ", " + this);
    }

    /**
     * Moves a child view from hidden list to regular list.
     * Calling this method should probably be followed by a detach, otherwise, it will suddenly
     * show up in LayoutManager's children list.
     *
     * @param view The hidden View to unhide
     */
    void unhide(View view) {
        final int offset = mCallback.indexOfChild(view);
        if (offset < 0) {
            throw new IllegalArgumentException("view is not a child, cannot hide " + view);
        }
        if (!mBucket.get(offset)) {
            throw new RuntimeException("trying to unhide a view that was not hidden" + view);
        }
        mBucket.clear(offset);
        unhideViewInternal(view);
    }

    @Override
    public String toString() {
        return mBucket.toString() + ", hidden list:" + mHiddenViews.size();
    }

    /**
     * Removes a view from the ViewGroup if it is hidden.
     *
     * @param view The view to remove.
     * @return True if the View is found and it is hidden. False otherwise.
     */
    boolean removeViewIfHidden(View view) {
        final int index = mCallback.indexOfChild(view);
        if (index == -1) {
            if (unhideViewInternal(view)) {
                throw new IllegalStateException("view is in hidden list but not in view group");
            }
            return true;
        }
        if (mBucket.get(index)) {
            mBucket.remove(index);
            if (!unhideViewInternal(view)) {
                throw new IllegalStateException(
                        "removed a hidden view but it is not in hidden views list");
            }
            mCallback.removeViewAt(index);
            return true;
        }
        return false;
    }


}
