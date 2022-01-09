package com.shuhnli.recyclerview_diy.recyclerview;

import android.util.SparseArray;

import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * 可以在多个 RecyclerView 之间复用View
 * 默认自动创建一个RecycledViewPool, 也可以手动创建RecyclerView.setRecycledViewPool(RecycledViewPool)
 */
public class RecycledViewPool {
    //回收站
    SparseArray<ScrapData> mScrap = new SparseArray<>();
    private int mAttachCount = 0;

    //清楚所有类型, 以及该Type下的所有数据
    public void clear() {
        for (int i = 0; i < mScrap.size(); i++) {
            ScrapData data = mScrap.valueAt(i);
            data.mScrapHeap.clear();
        }
    }

    /**
     * 回收站内回收某一View类型对应的数量
     *
     * @param viewType view类型
     * @param max      最大数量
     */
    public void setMaxRecycledViews(int viewType, int max) {
        ScrapData scrapData = getScrapDataForType(viewType);
        scrapData.mMaxScrap = max;
        final ArrayList<ViewHolder> scrapHeap = scrapData.mScrapHeap;
        while (scrapHeap.size() > max) {
            //已经超出目标大小, 所以要从尾部一个一个地移除
            scrapHeap.remove(scrapHeap.size() - 1);
        }
    }

    /**
     * 返回某一类型View 在回收站里的数量
     */
    public int getRecycledViewCount(int viewType) {
        return getScrapDataForType(viewType).mScrapHeap.size();
    }

    //从回收池中, 获取对应Type对应的holder, 如果不存在, 则为null
    @Nullable
    public ViewHolder getRecycledView(int viewType) {
        final ScrapData scrapData = mScrap.get(viewType);
        if (scrapData != null && !scrapData.mScrapHeap.isEmpty()) {
            final ArrayList<ViewHolder> scrapHeap = scrapData.mScrapHeap;
            for (int i = scrapHeap.size() - 1; i >= 0; i--) {
                if (!scrapHeap.get(i).isAttachedToTransitionOverlay()) {
                    return scrapHeap.remove(i);
                }
            }
        }
        return null;
    }

    //回收池中的viewHolder的总数。所有类型都加在一起
    int size() {
        int count = 0;
        for (int i = 0; i < mScrap.size(); i++) {
            ArrayList<ViewHolder> viewHolders = mScrap.valueAt(i).mScrapHeap;
            if (viewHolders != null) {
                count += viewHolders.size();
            }
        }
        return count;
    }

    //向回收池内添加废弃的viewHolder
    public void putRecycledView(ViewHolder scrap) {
        final int viewType = scrap.getItemViewType();
        final ArrayList<ViewHolder> scrapHeap = getScrapDataForType(viewType).mScrapHeap;
        if (mScrap.get(viewType).mMaxScrap <= scrapHeap.size()) {
            return;
        }
        if (scrapHeap.contains(scrap)) {
            throw new IllegalArgumentException("this scrap item already exists");
        }
        scrap.resetInternal();
        scrapHeap.add(scrap);
    }

    private long runningAverage(long oldAverage, long newValue) {
        if (oldAverage == 0) {
            return newValue;
        }
        return (oldAverage / 4 * 3) + (newValue / 4);
    }

    public void factorInCreateTime(int viewType, long createTimeNs) {
        ScrapData scrapData = getScrapDataForType(viewType);
        scrapData.mCreateRunningAverageNs = runningAverage(
                scrapData.mCreateRunningAverageNs, createTimeNs);
    }

    public void factorInBindTime(int viewType, long bindTimeNs) {
        ScrapData scrapData = getScrapDataForType(viewType);
        scrapData.mBindRunningAverageNs = runningAverage(
                scrapData.mBindRunningAverageNs, bindTimeNs);
    }

    //是否可以在过期时间内创建
    boolean willCreateInTime(int viewType, long approxCurrentNs, long deadlineNs) {
        long expectedDurationNs = getScrapDataForType(viewType).mCreateRunningAverageNs;
        return expectedDurationNs == 0 || (approxCurrentNs + expectedDurationNs < deadlineNs);
    }

    boolean willBindInTime(int viewType, long approxCurrentNs, long deadlineNs) {
        long expectedDurationNs = getScrapDataForType(viewType).mBindRunningAverageNs;
        return expectedDurationNs == 0 || (approxCurrentNs + expectedDurationNs < deadlineNs);
    }

    void attach() {
        mAttachCount++;
    }

    void detach() {
        mAttachCount--;
    }

    private ScrapData getScrapDataForType(int viewType) {
        ScrapData scrapData = mScrap.get(viewType);
        if (scrapData == null) {
            scrapData = new ScrapData();
            mScrap.put(viewType, scrapData);
        }
        return scrapData;
    }
}
