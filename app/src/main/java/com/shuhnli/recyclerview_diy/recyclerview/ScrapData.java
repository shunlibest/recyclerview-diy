package com.shuhnli.recyclerview_diy.recyclerview;

import java.util.ArrayList;

/**
 * 跟踪池持有者，以及给定类型的创建/绑定时间元数据。
 * 1) 这使我们能够跟踪跨多个适配器的平均创建和绑定时间。
 * 尽管对于不同的 Adapter 子类，创建（尤其是绑定）的行为可能不同，但共享池是一个强烈的信号，表明它们将按类型执行相似的操作。
 * 2) 如果willBindInTime(int, long, long)对一个视图返回 false，
 * 它将在同一截止日willBindInTime(int, long, long)为其类型的所有其他视图返回 false。
 * 这可以防止由GapWorker预取构造的项目绑定到较低优先级的预取。
 */
public class ScrapData {
    private static final int DEFAULT_MAX_SCRAP = 5;
    //当前View类型, 锁缓存的ViewHolder
    final ArrayList<ViewHolder> mScrapHeap = new ArrayList<>();
    // 某一数据类型对应的最大缓存数量
    int mMaxScrap = DEFAULT_MAX_SCRAP;
    //创建viewHolder所需的时间, 用于预加载
    long mCreateRunningAverageNs = 0;
    long mBindRunningAverageNs = 0;
}