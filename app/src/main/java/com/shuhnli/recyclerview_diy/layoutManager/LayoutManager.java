package com.shuhnli.recyclerview_diy.layoutManager;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.FocusFinder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;


import com.shuhnli.recyclerview_diy.childUtil.ChildHelper;
import com.shuhnli.recyclerview_diy.childUtil.ChildHelperCallBack;
import com.shuhnli.recyclerview_diy.recyclerview.Adapter;
import com.shuhnli.recyclerview_diy.recyclerview.Recycler;
import com.shuhnli.recyclerview_diy.recyclerview.RecyclerView;
import com.shuhnli.recyclerview_diy.recyclerview.ViewHolder;
import com.shuhnli.recyclerview_diy.utils.SizeUtil;
import com.shuhnli.recyclerview_diy.utils.ViewUtilKt;

import java.util.ArrayList;

/**
 * LayoutManager负责在RecyclerView中度量和定位项目视图，以及决定何时回收用户不再可见的项目视图的策略。
 * 通过更改LayoutManager，可以使用RecyclerView实现标准的垂直滚动列表、统一网格、交错网格、水平滚动集合等。
 */
public abstract class LayoutManager {
    private static final String TAG = "LayoutManager";
    ChildHelper mChildHelper;
    RecyclerView mRecyclerView;

    /**
     * The callback used for retrieving information about a RecyclerView and its children in the
     * horizontal direction.
     */
    private final ViewBoundsCheck.Callback mHorizontalBoundCheckCallback = new ViewBoundsCheck.Callback() {
        @Override
        public View getChildAt(int index) {
            return LayoutManager.this.getChildAt(index);
        }

        @Override
        public int getParentStart() {
            return LayoutManager.this.getPaddingLeft();
        }

        @Override
        public int getParentEnd() {
            return LayoutManager.this.getWidth() - LayoutManager.this.getPaddingRight();
        }

        @Override
        public int getChildStart(View view) {
            final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                    view.getLayoutParams();
            return LayoutManager.this.getDecoratedLeft(view) - params.leftMargin;
        }

        @Override
        public int getChildEnd(View view) {
            final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                    view.getLayoutParams();
            return LayoutManager.this.getDecoratedRight(view) + params.rightMargin;
        }
    };

    /**
     * The callback used for retrieving information about a RecyclerView and its children in the
     * vertical direction.
     */
    private final ViewBoundsCheck.Callback mVerticalBoundCheckCallback = new ViewBoundsCheck.Callback() {
        @Override
        public View getChildAt(int index) {
            return LayoutManager.this.getChildAt(index);
        }

        @Override
        public int getParentStart() {
            return LayoutManager.this.getPaddingTop();
        }

        @Override
        public int getParentEnd() {
            return LayoutManager.this.getHeight()
                    - LayoutManager.this.getPaddingBottom();
        }

        @Override
        public int getChildStart(View view) {
            final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                    view.getLayoutParams();
            return LayoutManager.this.getDecoratedTop(view) - params.topMargin;
        }

        @Override
        public int getChildEnd(View view) {
            final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                    view.getLayoutParams();
            return LayoutManager.this.getDecoratedBottom(view) + params.bottomMargin;
        }
    };

    //用于判断水平和竖直case下, 父子View边界的关系
    ViewBoundsCheck mHorizontalBoundCheck = new ViewBoundsCheck(mHorizontalBoundCheckCallback);
    ViewBoundsCheck mVerticalBoundCheck = new ViewBoundsCheck(mVerticalBoundCheckCallback);

    @Nullable
    RecyclerView.SmoothScroller mSmoothScroller;

    boolean mRequestedSimpleAnimations = false;


    /**
     * This field is only set via the deprecated {@link #setAutoMeasureEnabled(boolean)} and is
     * only accessed via {@link #isAutoMeasureEnabled()} for backwards compatability reasons.
     */
    boolean mAutoMeasure = false;

    /**
     * LayoutManager has its own more strict measurement cache to avoid re-measuring a child
     * if the space that will be given to it is already larger than what it has measured before.
     */
    private boolean mMeasurementCacheEnabled = true;

    private boolean mItemPrefetchEnabled = true;

    /**
     * Written by {@link GapWorker} when prefetches occur to track largest number of view ever
     * requested by a {@link #collectInitialPrefetchPositions(int, LayoutPrefetchRegistry)} or
     * {@link #collectAdjacentPrefetchPositions(int, int, RecyclerView.State, LayoutPrefetchRegistry)} call.
     * <p>
     * If expanded by a {@link #collectInitialPrefetchPositions(int, LayoutPrefetchRegistry)},
     * will be reset upon layout to prevent initial prefetches (often large, since they're
     * proportional to expected child count) from expanding cache permanently.
     */
    int mPrefetchMaxCountObserved;

    /**
     * If true, mPrefetchMaxCountObserved is only valid until next layout, and should be reset.
     */
    boolean mPrefetchMaxObservedInInitialPrefetch;


    private int mWidthMode, mHeightMode;
    private int mWidth, mHeight;


    /**
     * Interface for LayoutManagers to request items to be prefetched, based on position, with
     * specified distance from viewport, which indicates priority.
     *
     * @see LayoutManager#collectAdjacentPrefetchPositions(int, int, RecyclerView.State, LayoutPrefetchRegistry)
     * @see LayoutManager#collectInitialPrefetchPositions(int, LayoutPrefetchRegistry)
     */
    public interface LayoutPrefetchRegistry {
        /**
         * Requests an an item to be prefetched, based on position, with a specified distance,
         * indicating priority.
         *
         * @param layoutPosition Position of the item to prefetch.
         * @param pixelDistance  Distance from the current viewport to the bounds of the item,
         *                       must be non-negative.
         */
        void addPosition(int layoutPosition, int pixelDistance);
    }

    //绑定到对应的RecyclerView
    public void setRecyclerView(@NonNull RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
        mChildHelper = recyclerView.mChildHelper;
        mWidth = recyclerView.getWidth();
        mHeight = recyclerView.getHeight();
        mWidthMode = View.MeasureSpec.EXACTLY;
        mHeightMode = View.MeasureSpec.EXACTLY;
    }

    //设置测量模式
    void setMeasureSpecs(int wSpec, int hSpec) {
        mWidth = View.MeasureSpec.getSize(wSpec);
        mWidthMode = View.MeasureSpec.getMode(wSpec);

        mHeight = View.MeasureSpec.getSize(hSpec);
        mHeightMode = View.MeasureSpec.getMode(hSpec);
    }

    /**
     * 当使用自动度量时，在度量通过期间计算布局后调用。
     * 它只是遍历所有的子元素来计算边界框，然后调用setMeasuredDimension(Rect, int, int)。
     * 如果需要以不同的方式处理边界框，LayoutManagers可以重写该方法。
     * 例如，GridLayoutManager重写该方法以确保即使列为空，GridLayoutManager仍然度量足够宽的范围来包含它。
     */
    void setMeasuredDimensionFromChildren(int widthSpec, int heightSpec) {
        final int count = getChildCount();
        if (count == 0) {
            mRecyclerView.defaultOnMeasure(widthSpec, heightSpec);
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            final Rect bounds = mRecyclerView.mTempRect;
            getDecoratedBoundsWithMargins(child, bounds);
            if (bounds.left < minX) {
                minX = bounds.left;
            }
            if (bounds.right > maxX) {
                maxX = bounds.right;
            }
            if (bounds.top < minY) {
                minY = bounds.top;
            }
            if (bounds.bottom > maxY) {
                maxY = bounds.bottom;
            }
        }
        mRecyclerView.mTempRect.set(minX, minY, maxX, maxY);
        setMeasuredDimension(mRecyclerView.mTempRect, widthSpec, heightSpec);
    }

    /**
     * Sets the measured dimensions from the given bounding box of the children and the
     * measurement specs that were passed into {@link RecyclerView#onMeasure(int, int)}. It is
     * only called if a LayoutManager returns <code>true</code> from
     * {@link #isAutoMeasureEnabled()} and it is called after the RecyclerView calls
     * {@link LayoutManager#onLayoutChildren(Recycler, RecyclerView.State)} in the execution of
     * {@link RecyclerView#onMeasure(int, int)}.
     * <p>
     * This method must call {@link #setMeasuredDimension(int, int)}.
     * <p>
     * The default implementation adds the RecyclerView's padding to the given bounding box
     * then caps the value to be within the given measurement specs.
     *
     * @param childrenBounds The bounding box of all children
     * @param wSpec          The widthMeasureSpec that was passed into the RecyclerView.
     * @param hSpec          The heightMeasureSpec that was passed into the RecyclerView.
     * @see #isAutoMeasureEnabled()
     * @see #setMeasuredDimension(int, int)
     */
    public void setMeasuredDimension(Rect childrenBounds, int wSpec, int hSpec) {
        int usedWidth = childrenBounds.width() + getPaddingLeft() + getPaddingRight();
        int usedHeight = childrenBounds.height() + getPaddingTop() + getPaddingBottom();
        int width = SizeUtil.chooseSize(wSpec, usedWidth, getMinimumWidth());
        int height = SizeUtil.chooseSize(hSpec, usedHeight, getMinimumHeight());
        setMeasuredDimension(width, height);
    }


    /**
     * Defines whether the measuring pass of layout should use the AutoMeasure mechanism of
     * {@link RecyclerView} or if it should be done by the LayoutManager's implementation of
     * {@link LayoutManager#onMeasure(Recycler, RecyclerView.State, int, int)}.
     *
     * @param enabled <code>True</code> if layout measurement should be done by the
     *                RecyclerView, <code>false</code> if it should be done by this
     *                LayoutManager.
     * @see #isAutoMeasureEnabled()
     * @deprecated Implementors of LayoutManager should define whether or not it uses
     * AutoMeasure by overriding {@link #isAutoMeasureEnabled()}.
     */
    @Deprecated
    public void setAutoMeasureEnabled(boolean enabled) {
        mAutoMeasure = enabled;
    }

    /**
     * Returns whether the measuring pass of layout should use the AutoMeasure mechanism of
     * {@link RecyclerView} or if it should be done by the LayoutManager's implementation of
     * {@link LayoutManager#onMeasure(Recycler, RecyclerView.State, int, int)}.
     * <p>
     * This method returns false by default (it actually returns the value passed to the
     * deprecated {@link #setAutoMeasureEnabled(boolean)}) and should be overridden to return
     * true if a LayoutManager wants to be auto measured by the RecyclerView.
     * <p>
     * If this method is overridden to return true,
     * {@link LayoutManager#onMeasure(Recycler, RecyclerView.State, int, int)} should not be overridden.
     * <p>
     * AutoMeasure is a RecyclerView mechanism that handles the measuring pass of layout in a
     * simple and contract satisfying way, including the wrapping of children laid out by
     * LayoutManager. Simply put, it handles wrapping children by calling
     * {@link LayoutManager#onLayoutChildren(Recycler, RecyclerView.State)} during a call to
     * {@link RecyclerView#onMeasure(int, int)}, and then calculating desired dimensions based
     * on children's dimensions and positions. It does this while supporting all existing
     * animation capabilities of the RecyclerView.
     * <p>
     * More specifically:
     * <ol>
     * <li>When {@link RecyclerView#onMeasure(int, int)} is called, if the provided measure
     * specs both have a mode of {@link View.MeasureSpec#EXACTLY}, RecyclerView will set its
     * measured dimensions accordingly and return, allowing layout to continue as normal
     * (Actually, RecyclerView will call
     * {@link LayoutManager#onMeasure(Recycler, RecyclerView.State, int, int)} for backwards compatibility
     * reasons but it should not be overridden if AutoMeasure is being used).</li>
     * <li>If one of the layout specs is not {@code EXACT}, the RecyclerView will start the
     * layout process. It will first process all pending Adapter updates and
     * then decide whether to run a predictive layout. If it decides to do so, it will first
     * call {@link #onLayoutChildren(Recycler, RecyclerView.State)} with {@link RecyclerView.State#isPreLayout()} set to
     * {@code true}. At this stage, {@link #getWidth()} and {@link #getHeight()} will still
     * return the width and height of the RecyclerView as of the last layout calculation.
     * <p>
     * After handling the predictive case, RecyclerView will call
     * {@link #onLayoutChildren(Recycler, RecyclerView.State)} with {@link RecyclerView.State#isMeasuring()} set to
     * {@code true} and {@link RecyclerView.State#isPreLayout()} set to {@code false}. The LayoutManager can
     * access the measurement specs via {@link #getHeight()}, {@link #getHeightMode()},
     * {@link #getWidth()} and {@link #getWidthMode()}.</li>
     * <li>After the layout calculation, RecyclerView sets the measured width & height by
     * calculating the bounding box for the children (+ RecyclerView's padding). The
     * LayoutManagers can override {@link #setMeasuredDimension(Rect, int, int)} to choose
     * different values. For instance, GridLayoutManager overrides this value to handle the case
     * where if it is vertical and has 3 columns but only 2 items, it should still measure its
     * width to fit 3 items, not 2.</li>
     * <li>Any following calls to {@link RecyclerView#onMeasure(int, int)} will run
     * {@link #onLayoutChildren(Recycler, RecyclerView.State)} with {@link RecyclerView.State#isMeasuring()} set to
     * {@code true} and {@link RecyclerView.State#isPreLayout()} set to {@code false}. RecyclerView will
     * take care of which views are actually added / removed / moved / changed for animations so
     * that the LayoutManager should not worry about them and handle each
     * {@link #onLayoutChildren(Recycler, RecyclerView.State)} call as if it is the last one.</li>
     * <li>When measure is complete and RecyclerView's
     * {@link #onLayout(boolean, int, int, int, int)} method is called, RecyclerView checks
     * whether it already did layout calculations during the measure pass and if so, it re-uses
     * that information. It may still decide to call {@link #onLayoutChildren(Recycler, RecyclerView.State)}
     * if the last measure spec was different from the final dimensions or adapter contents
     * have changed between the measure call and the layout call.</li>
     * <li>Finally, animations are calculated and run as usual.</li>
     * </ol>
     *
     * @return <code>True</code> if the measuring pass of layout should use the AutoMeasure
     * mechanism of {@link RecyclerView} or <code>False</code> if it should be done by the
     * LayoutManager's implementation of
     * {@link LayoutManager#onMeasure(Recycler, RecyclerView.State, int, int)}.
     * @see #setMeasuredDimension(Rect, int, int)
     * @see #onMeasure(Recycler, RecyclerView.State, int, int)
     */
    public boolean isAutoMeasureEnabled() {
        return mAutoMeasure;
    }


    /**
     * Sets whether the LayoutManager should be queried for views outside of
     * its viewport while the UI thread is idle between frames.
     *
     * <p>If enabled, the LayoutManager will be queried for items to inflate/bind in between
     * view system traversals on devices running API 21 or greater. Default value is true.</p>
     *
     * <p>On platforms API level 21 and higher, the UI thread is idle between passing a frame
     * to RenderThread and the starting up its next frame at the next VSync pulse. By
     * prefetching out of window views in this time period, delays from inflation and view
     * binding are much less likely to cause jank and stuttering during scrolls and flings.</p>
     *
     * <p>While prefetch is enabled, it will have the side effect of expanding the effective
     * size of the View cache to hold prefetched views.</p>
     *
     * @param enabled <code>True</code> if items should be prefetched in between traversals.
     * @see #isItemPrefetchEnabled()
     */
    public final void setItemPrefetchEnabled(boolean enabled) {
        if (enabled != mItemPrefetchEnabled) {
            mItemPrefetchEnabled = enabled;
            mPrefetchMaxCountObserved = 0;
            if (mRecyclerView != null) {
                mRecyclerView.mRecycler.updateViewCacheSize();
            }
        }
    }

    /**
     * Sets whether the LayoutManager should be queried for views outside of
     * its viewport while the UI thread is idle between frames.
     *
     * @return true if item prefetch is enabled, false otherwise
     * @see #setItemPrefetchEnabled(boolean)
     */
    public final boolean isItemPrefetchEnabled() {
        return mItemPrefetchEnabled;
    }

    /**
     * Gather all positions from the LayoutManager to be prefetched, given specified momentum.
     *
     * <p>If item prefetch is enabled, this method is called in between traversals to gather
     * which positions the LayoutManager will soon need, given upcoming movement in subsequent
     * traversals.</p>
     *
     * <p>The LayoutManager should call {@link LayoutPrefetchRegistry#addPosition(int, int)} for
     * each item to be prepared, and these positions will have their ViewHolders created and
     * bound, if there is sufficient time available, in advance of being needed by a
     * scroll or layout.</p>
     *
     * @param dx                     X movement component.
     * @param dy                     Y movement component.
     * @param state                  State of RecyclerView
     * @param layoutPrefetchRegistry PrefetchRegistry to add prefetch entries into.
     * @see #isItemPrefetchEnabled()
     * @see #collectInitialPrefetchPositions(int, LayoutPrefetchRegistry)
     */
    public void collectAdjacentPrefetchPositions(int dx, int dy, RecyclerView.State state,
                                                 LayoutPrefetchRegistry layoutPrefetchRegistry) {
    }

    /**
     * Gather all positions from the LayoutManager to be prefetched in preperation for its
     * RecyclerView to come on screen, due to the movement of another, containing RecyclerView.
     *
     * <p>This method is only called when a RecyclerView is nested in another RecyclerView.</p>
     *
     * <p>If item prefetch is enabled for this LayoutManager, as well in another containing
     * LayoutManager, this method is called in between draw traversals to gather
     * which positions this LayoutManager will first need, once it appears on the screen.</p>
     *
     * <p>For example, if this LayoutManager represents a horizontally scrolling list within a
     * vertically scrolling LayoutManager, this method would be called when the horizontal list
     * is about to come onscreen.</p>
     *
     * <p>The LayoutManager should call {@link LayoutPrefetchRegistry#addPosition(int, int)} for
     * each item to be prepared, and these positions will have their ViewHolders created and
     * bound, if there is sufficient time available, in advance of being needed by a
     * scroll or layout.</p>
     *
     * @param adapterItemCount       number of items in the associated adapter.
     * @param layoutPrefetchRegistry PrefetchRegistry to add prefetch entries into.
     * @see #isItemPrefetchEnabled()
     * @see #collectAdjacentPrefetchPositions(int, int, RecyclerView.State, LayoutPrefetchRegistry)
     */
    public void collectInitialPrefetchPositions(int adapterItemCount,
                                                LayoutPrefetchRegistry layoutPrefetchRegistry) {
    }

    /**
     * Removes the specified Runnable from the message queue.
     * <p>
     * Calling this method when LayoutManager is not attached to a RecyclerView has no effect.
     *
     * @param action The Runnable to remove from the message handling queue
     * @return true if RecyclerView could ask the Handler to remove the Runnable,
     * false otherwise. When the returned value is true, the Runnable
     * may or may not have been actually removed from the message queue
     * (for instance, if the Runnable was not in the queue already.)
     * @see #postOnAnimation
     */
    public boolean removeCallbacks(Runnable action) {
        if (mRecyclerView != null) {
            return mRecyclerView.removeCallbacks(action);
        }
        return false;
    }


    /**
     * Check if the RecyclerView is configured to clip child views to its padding.
     *
     * @return true if this RecyclerView clips children to its padding, false otherwise
     */
    public boolean getClipToPadding() {
        return mRecyclerView != null && mRecyclerView.mClipToPadding;
    }

    /**
     * Lay out all relevant child views from the given adapter.
     * <p>
     * The LayoutManager is in charge of the behavior of item animations. By default,
     * RecyclerView has a non-null {@link #getItemAnimator() ItemAnimator}, and simple
     * item animations are enabled. This means that add/remove operations on the
     * adapter will result in animations to add new or appearing items, removed or
     * disappearing items, and moved items. If a LayoutManager returns false from
     * {@link #supportsPredictiveItemAnimations()}, which is the default, and runs a
     * normal layout operation during {@link #onLayoutChildren(Recycler, RecyclerView.State)}, the
     * RecyclerView will have enough information to run those animations in a simple
     * way. For example, the default ItemAnimator, {@link DefaultItemAnimator}, will
     * simply fade views in and out, whether they are actually added/removed or whether
     * they are moved on or off the screen due to other add/remove operations.
     *
     * <p>A LayoutManager wanting a better item animation experience, where items can be
     * animated onto and off of the screen according to where the items exist when they
     * are not on screen, then the LayoutManager should return true from
     * {@link #supportsPredictiveItemAnimations()} and add additional logic to
     * {@link #onLayoutChildren(Recycler, RecyclerView.State)}. Supporting predictive animations
     * means that {@link #onLayoutChildren(Recycler, RecyclerView.State)} will be called twice;
     * once as a "pre" layout step to determine where items would have been prior to
     * a real layout, and again to do the "real" layout. In the pre-layout phase,
     * items will remember their pre-layout positions to allow them to be laid out
     * appropriately. Also, {@link RecyclerView.LayoutParams#isItemRemoved() removed} items will
     * be returned from the scrap to help determine correct placement of other items.
     * These removed items should not be added to the child list, but should be used
     * to help calculate correct positioning of other views, including views that
     * were not previously onscreen (referred to as APPEARING views), but whose
     * pre-layout offscreen position can be determined given the extra
     * information about the pre-layout removed views.</p>
     *
     * <p>The second layout pass is the real layout in which only non-removed views
     * will be used. The only additional requirement during this pass is, if
     * {@link #supportsPredictiveItemAnimations()} returns true, to note which
     * views exist in the child list prior to layout and which are not there after
     * layout (referred to as DISAPPEARING views), and to position/layout those views
     * appropriately, without regard to the actual bounds of the RecyclerView. This allows
     * the animation system to know the location to which to animate these disappearing
     * views.</p>
     *
     * <p>The default LayoutManager implementations for RecyclerView handle all of these
     * requirements for animations already. Clients of RecyclerView can either use one
     * of these layout managers directly or look at their implementations of
     * onLayoutChildren() to see how they account for the APPEARING and
     * DISAPPEARING views.</p>
     *
     * @param recycler Recycler to use for fetching potentially cached views for a
     *                 position
     * @param state    Transient state of RecyclerView
     */
    public void onLayoutChildren(Recycler recycler, RecyclerView.State state) {
        Log.e(TAG, "You must override onLayoutChildren(Recycler recycler, State state) ");
    }

    /**
     * Called after a full layout calculation is finished. The layout calculation may include
     * multiple {@link #onLayoutChildren(Recycler, RecyclerView.State)} calls due to animations or
     * layout measurement but it will include only one {@link #onLayoutCompleted(RecyclerView.State)} call.
     * This method will be called at the end of {@link View#layout(int, int, int, int)} call.
     * <p>
     * This is a good place for the LayoutManager to do some cleanup like pending scroll
     * position, saved state etc.
     *
     * @param state Transient state of RecyclerView
     */
    public void onLayoutCompleted(RecyclerView.State state) {
    }

    /**
     * Create a default <code>LayoutParams</code> object for a child of the RecyclerView.
     *
     * <p>LayoutManagers will often want to use a custom <code>LayoutParams</code> type
     * to store extra information specific to the layout. Client code should subclass
     * {@link RecyclerView.LayoutParams} for this purpose.</p>
     *
     * <p><em>Important:</em> if you use your own custom <code>LayoutParams</code> type
     * you must also override
     * {@link #checkLayoutParams(RecyclerView.LayoutParams)},
     * {@link #generateLayoutParams(ViewGroup.LayoutParams)} and
     * {@link #generateLayoutParams(Context, AttributeSet)}.</p>
     *
     * @return A new LayoutParams for a child view
     */
    public abstract RecyclerView.LayoutParams generateDefaultLayoutParams();

    /**
     * Determines the validity of the supplied LayoutParams object.
     *
     * <p>This should check to make sure that the object is of the correct type
     * and all values are within acceptable ranges. The default implementation
     * returns <code>true</code> for non-null params.</p>
     *
     * @param lp LayoutParams object to check
     * @return true if this LayoutParams object is valid, false otherwise
     */
    public boolean checkLayoutParams(RecyclerView.LayoutParams lp) {
        return lp != null;
    }

    /**
     * Create a LayoutParams object suitable for this LayoutManager, copying relevant
     * values from the supplied LayoutParams object if possible.
     *
     * <p><em>Important:</em> if you use your own custom <code>LayoutParams</code> type
     * you must also override
     * {@link #checkLayoutParams(RecyclerView.LayoutParams)},
     * {@link #generateLayoutParams(ViewGroup.LayoutParams)} and
     * {@link #generateLayoutParams(Context, AttributeSet)}.</p>
     *
     * @param lp Source LayoutParams object to copy values from
     * @return a new LayoutParams object
     */
    public RecyclerView.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof RecyclerView.LayoutParams) {
            return new RecyclerView.LayoutParams((RecyclerView.LayoutParams) lp);
        } else if (lp instanceof ViewGroup.MarginLayoutParams) {
            return new RecyclerView.LayoutParams((ViewGroup.MarginLayoutParams) lp);
        } else {
            return new RecyclerView.LayoutParams(lp);
        }
    }

    /**
     * Create a LayoutParams object suitable for this LayoutManager from
     * an inflated layout resource.
     *
     * <p><em>Important:</em> if you use your own custom <code>LayoutParams</code> type
     * you must also override
     * {@link #checkLayoutParams(RecyclerView.LayoutParams)},
     * {@link #generateLayoutParams(ViewGroup.LayoutParams)} and
     * {@link #generateLayoutParams(Context, AttributeSet)}.</p>
     *
     * @param c     Context for obtaining styled attributes
     * @param attrs AttributeSet describing the supplied arguments
     * @return a new LayoutParams object
     */
    public RecyclerView.LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return new RecyclerView.LayoutParams(c, attrs);
    }

    //水平滚动dx像素
    public int scrollHorizontallyBy(int dx, Recycler recycler, RecyclerView.State state) {
        return 0;
    }

    //垂直滚动dx像素
    public int scrollVerticallyBy(int dy, Recycler recycler, RecyclerView.State state) {
        return 0;
    }

    //是否支持水平滚动
    public boolean canScrollHorizontally() {
        return false;
    }

    //是否支持垂直滚动
    public boolean canScrollVertically() {
        return false;
    }

    //滚动到对应的index
    public void scrollToPosition(int position) {

    }

    //滚动到对应的index, 需要有滚动动画,而不是瞬移过去
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state,
                                       int position) {
    }

    /**
     * Starts a smooth scroll using the provided {@link RecyclerView.SmoothScroller}.
     *
     * <p>Each instance of SmoothScroller is intended to only be used once. Provide a new
     * SmoothScroller instance each time this method is called.
     *
     * <p>Calling this method will cancel any previous smooth scroll request.
     *
     * @param smoothScroller Instance which defines how smooth scroll should be animated
     */
    public void startSmoothScroll(RecyclerView.SmoothScroller smoothScroller) {
        if (mSmoothScroller != null && smoothScroller != mSmoothScroller
                && mSmoothScroller.isRunning()) {
            mSmoothScroller.stop();
        }
        mSmoothScroller = smoothScroller;
        mSmoothScroller.start(mRecyclerView, this);
    }

    /**
     * @return true if RecyclerView is currently in the state of smooth scrolling.
     */
    public boolean isSmoothScrolling() {
        return mSmoothScroller != null && mSmoothScroller.isRunning();
    }


    /**
     * To be called only during {@link #onLayoutChildren(Recycler, RecyclerView.State)} to add a view
     * to the layout that is known to be going away, either because it has been
     * {@link Adapter#notifyItemRemoved(int) removed} or because it is actually not in the
     * visible portion of the container but is being laid out in order to inform RecyclerView
     * in how to animate the item out of view.
     * <p>
     * Views added via this method are going to be invisible to LayoutManager after the
     * dispatchLayout pass is complete. They cannot be retrieved via {@link #getChildAt(int)}
     * or won't be included in {@link #getChildCount()} method.
     *
     * @param child View to add and then remove with animation.
     */
    public void addDisappearingView(View child) {
        addDisappearingView(child, -1);
    }

    /**
     * To be called only during {@link #onLayoutChildren(Recycler, RecyclerView.State)} to add a view
     * to the layout that is known to be going away, either because it has been
     * {@link Adapter#notifyItemRemoved(int) removed} or because it is actually not in the
     * visible portion of the container but is being laid out in order to inform RecyclerView
     * in how to animate the item out of view.
     * <p>
     * Views added via this method are going to be invisible to LayoutManager after the
     * dispatchLayout pass is complete. They cannot be retrieved via {@link #getChildAt(int)}
     * or won't be included in {@link #getChildCount()} method.
     *
     * @param child View to add and then remove with animation.
     * @param index Index of the view.
     */
    public void addDisappearingView(View child, int index) {
        addViewInt(child, index, true);
    }

    ////////////////////添加子View////////////////////////
    public void addView(View child) {
        addView(child, -1);
    }

    public void addView(View child, int index) {
        addViewInt(child, index, false);
    }

    private void addViewInt(View child, int index, boolean disappearing) {
        final ViewHolder holder = getChildViewHolderInt(child);
        if (disappearing || holder.isRemoved()) {
            // these views will be hidden at the end of the layout pass.
            mRecyclerView.mViewInfoStore.addToDisappearedInLayout(holder);
        } else {
            // This may look like unnecessary but may happen if layout manager supports
            // predictive layouts and adapter removed then re-added the same item.
            // In this case, added version will be visible in the post layout (because add is
            // deferred) but RV will still bind it to the same View.
            // So if a View re-appears in post layout pass, remove it from disappearing list.
            mRecyclerView.mViewInfoStore.removeFromDisappearedInLayout(holder);
        }

        final RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
        //case1: 该View属于被回收的view
        if (holder.wasReturnedFromScrap() || holder.isScrap()) {
            if (holder.isScrap()) {
                holder.unScrap();
            } else {
                holder.clearReturnedFromScrapFlag();
            }
            mChildHelper.attachViewToParent(child, index, child.getLayoutParams(), false);

        } else if (child.getParent() == mRecyclerView) {
            //case2:该View属于一个正在使用的view (按照正常的case, 一般不会出现这种case)
            // ensure in correct position
            int currentIndex = mChildHelper.indexOfChild(child);
            if (index == -1) {
                index = mChildHelper.getChildCount();
            }
            if (currentIndex == -1) {
                throw new IllegalStateException("Added View has RecyclerView as parent but"
                        + " view is not a real child. Unfiltered index:"
                        + mRecyclerView.indexOfChild(child) + mRecyclerView.exceptionLabel());
            }
            if (currentIndex != index) {
                mRecyclerView.mLayout.moveView(currentIndex, index);
            }
        } else {
            mChildHelper.addView(child, index, false);
            lp.mInsetsDirty = true;
            if (mSmoothScroller != null && mSmoothScroller.isRunning()) {
                mSmoothScroller.onChildAttachedToWindow(child);
            }
        }
        if (lp.mPendingInvalidate) {
            if (DEBUG) {
                Log.d(TAG, "consuming pending invalidate on child " + lp.mViewHolder);
            }
            holder.itemView.invalidate();
            lp.mPendingInvalidate = false;
        }
    }

    //从当前附加的RecyclerView中移除一个View
    public void removeView(View child) {
        mChildHelper.removeView(child);
    }

    public void removeViewAt(int index) {
        final View child = getChildAt(index);
        if (child != null) {
            mChildHelper.removeViewAt(index);
        }
    }

    public void removeAllViews() {
        // Only remove non-animating views
        final int childCount = getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            mChildHelper.removeViewAt(i);
        }
    }

    /**
     * Returns the View type defined by the adapter.
     *
     * @param view The view to query
     * @return The type of the view assigned by the adapter.
     */
    public int getItemViewType(@NonNull View view) {
        return ViewUtilKt.getViewHolder(view).getItemViewType();
    }


    //查找表示给定index的View
    //需要遍历所有的子view,在获取子view的position值 == 所需的position
    @Nullable
    public View findViewByPosition(int position) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child == null) {
                continue;
            }
            ViewHolder vh = ViewUtilKt.getViewHolder(child);
            if (vh.getLayoutPosition() == position && !vh.shouldIgnore()
                    && (mRecyclerView.mState.isPreLayout() || !vh.isRemoved())) {
                return child;
            }
        }
        return null;
    }

    //移除view的可见性
    public void detachView(@NonNull View child) {
        final int index = mChildHelper.indexOfChild(child);
        if (index >= 0) {
            mChildHelper.detachViewFromParent(index);
        }
    }

    public void detachViewAt(int index) {
        mChildHelper.detachViewFromParent(index);
    }


    //attachView
    public void attachView(@NonNull View child, int index, LayoutParams lp) {
        ViewHolder vh = ViewUtilKt.getViewHolder(child);
        if (vh.isRemoved()) {
            mRecyclerView.mViewInfoStore.addToDisappearedInLayout(vh);
        } else {
            mRecyclerView.mViewInfoStore.removeFromDisappearedInLayout(vh);
        }
        mChildHelper.attachViewToParent(child, index, lp, vh.isRemoved());
    }

    public void attachView(@NonNull View child, int index) {
        attachView(child, index, (LayoutParams) child.getLayoutParams());
    }


    public void attachView(@NonNull View child) {
        attachView(child, -1);
    }

    /**
     * Finish removing a view that was previously temporarily
     * {@link #detachView(View) detached}.
     *
     * @param child Detached child to remove
     */
    public void removeDetachedView(@NonNull View child) {
        mRecyclerView.removeDetachedView(child, false);
    }

    //把view从fromIndex移动至toIndex
    public void moveView(int fromIndex, int toIndex) {
        View view = getChildAt(fromIndex);
        if (view == null) {
            throw new IllegalArgumentException("Cannot move a child from non-existing index:"
                    + fromIndex + mRecyclerView.toString());
        }
        detachViewAt(fromIndex);
        attachView(view, toIndex);
    }

    public void detachAndScrapView(@NonNull View child, @NonNull Recycler recycler) {
        int index = mChildHelper.indexOfChild(child);
        scrapOrRecycleView(recycler, index, child);
    }

    public void detachAndScrapViewAt(int index, @NonNull Recycler recycler) {
        final View child = getChildAt(index);
        scrapOrRecycleView(recycler, index, child);
    }

    public void removeAndRecycleView(@NonNull View child, @NonNull Recycler recycler) {
        removeView(child);
        recycler.recycleView(child);
    }

    /**
     * Remove a child view and recycle it using the given Recycler.
     *
     * @param index    Index of child to remove and recycle
     * @param recycler Recycler to use to recycle child
     */
    public void removeAndRecycleViewAt(int index, @NonNull Recycler recycler) {
        final View view = getChildAt(index);
        removeViewAt(index);
        recycler.recycleView(view);
    }

    //统计一共有多少个子View
    public int getChildCount() {
        return mChildHelper != null ? mChildHelper.getChildCount() : 0;
    }

    //index 换view
    @Nullable
    public View getChildAt(int index) {
        return mChildHelper != null ? mChildHelper.getChildAt(index) : null;
    }


    public int getWidthMode() {
        return mWidthMode;
    }

    public int getHeightMode() {
        return mHeightMode;
    }

    @Px
    public int getWidth() {
        return mWidth;
    }

    @Px
    public int getHeight() {
        return mHeight;
    }

    @Px
    public int getPaddingLeft() {
        return mRecyclerView != null ? mRecyclerView.getPaddingLeft() : 0;
    }


    @Px
    public int getPaddingTop() {
        return mRecyclerView != null ? mRecyclerView.getPaddingTop() : 0;
    }


    @Px
    public int getPaddingRight() {
        return mRecyclerView != null ? mRecyclerView.getPaddingRight() : 0;
    }


    @Px
    public int getPaddingBottom() {
        return mRecyclerView != null ? mRecyclerView.getPaddingBottom() : 0;
    }

    /**
     * Returns the number of items in the adapter bound to the parent RecyclerView.
     * <p>
     * Note that this number is not necessarily equal to
     * {@link RecyclerView.State#getItemCount() State#getItemCount()}. In methods where {@link RecyclerView.State} is
     * available, you should use {@link RecyclerView.State#getItemCount() State#getItemCount()} instead.
     * For more details, check the documentation for
     * {@link RecyclerView.State#getItemCount() State#getItemCount()}.
     *
     * @return The number of items in the bound adapter
     * @see RecyclerView.State#getItemCount()
     */
    public int getItemCount() {
        final Adapter a = mRecyclerView != null ? mRecyclerView.getAdapter() : null;
        return a != null ? a.getItemCount() : 0;
    }

    /**
     * Offset all child views attached to the parent RecyclerView by dx pixels along
     * the horizontal axis.
     *
     * @param dx Pixels to offset by
     */
    public void offsetChildrenHorizontal(@Px int dx) {
        if (mRecyclerView != null) {
            mRecyclerView.offsetChildrenHorizontal(dx);
        }
    }

    /**
     * Offset all child views attached to the parent RecyclerView by dy pixels along
     * the vertical axis.
     *
     * @param dy Pixels to offset by
     */
    public void offsetChildrenVertical(@Px int dy) {
        if (mRecyclerView != null) {
            mRecyclerView.offsetChildrenVertical(dy);
        }
    }

    /**
     * Flags a view so that it will not be scrapped or recycled.
     * <p>
     * Scope of ignoring a child is strictly restricted to position tracking, scrapping and
     * recyling. Methods like {@link #removeAndRecycleAllViews(Recycler)} will ignore the child
     * whereas {@link #removeAllViews()} or {@link #offsetChildrenHorizontal(int)} will not
     * ignore the child.
     * <p>
     * Before this child can be recycled again, you have to call
     * {@link #stopIgnoringView(View)}.
     * <p>
     * You can call this method only if your LayoutManger is in onLayout or onScroll callback.
     *
     * @param view View to ignore.
     * @see #stopIgnoringView(View)
     */
    public void ignoreView(@NonNull View view) {
        if (view.getParent() != mRecyclerView || mRecyclerView.indexOfChild(view) == -1) {
            // checking this because calling this method on a recycled or detached view may
            // cause loss of state.
            throw new IllegalArgumentException("View should be fully attached to be ignored"
                    + mRecyclerView.exceptionLabel());
        }
        final ViewHolder vh = getChildViewHolderInt(view);
        vh.addFlags(ViewHolder.FLAG_IGNORE);
        mRecyclerView.mViewInfoStore.removeViewHolder(vh);
    }

    /**
     * View can be scrapped and recycled again.
     * <p>
     * Note that calling this method removes all information in the view holder.
     * <p>
     * You can call this method only if your LayoutManger is in onLayout or onScroll callback.
     *
     * @param view View to ignore.
     */
    public void stopIgnoringView(@NonNull View view) {
        final ViewHolder vh = getChildViewHolderInt(view);
        vh.stopIgnoring();
        vh.resetInternal();
        vh.addFlags(ViewHolder.FLAG_INVALID);
    }

    /**
     * Temporarily detach and scrap all currently attached child views. Views will be scrapped
     * into the given Recycler. The Recycler may prefer to reuse scrap views before
     * other views that were previously recycled.
     *
     * @param recycler Recycler to scrap views into
     */
    public void detachAndScrapAttachedViews(@NonNull Recycler recycler) {
        final int childCount = getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            final View v = getChildAt(i);
            scrapOrRecycleView(recycler, i, v);
        }
    }

    private void scrapOrRecycleView(Recycler recycler, int index, View view) {
        final ViewHolder viewHolder = getChildViewHolderInt(view);
        if (viewHolder.shouldIgnore()) {
            if (DEBUG) {
                Log.d(TAG, "ignoring view " + viewHolder);
            }
            return;
        }
        if (viewHolder.isInvalid() && !viewHolder.isRemoved()
                && !mRecyclerView.mAdapter.hasStableIds()) {
            removeViewAt(index);
            recycler.recycleViewHolderInternal(viewHolder);
        } else {
            detachViewAt(index);
            recycler.scrapView(view);
            mRecyclerView.mViewInfoStore.onViewDetached(viewHolder);
        }
    }

    /**
     * Recycles the scrapped views.
     * <p>
     * When a view is detached and removed, it does not trigger a ViewGroup invalidate. This is
     * the expected behavior if scrapped views are used for animations. Otherwise, we need to
     * call remove and invalidate RecyclerView to ensure UI update.
     *
     * @param recycler Recycler
     */
    void removeAndRecycleScrapInt(Recycler recycler) {
        final int scrapCount = recycler.getScrapCount();
        // Loop backward, recycler might be changed by removeDetachedView()
        for (int i = scrapCount - 1; i >= 0; i--) {
            final View scrap = recycler.getScrapViewAt(i);
            final ViewHolder vh = getChildViewHolderInt(scrap);
            if (vh.shouldIgnore()) {
                continue;
            }
            // If the scrap view is animating, we need to cancel them first. If we cancel it
            // here, ItemAnimator callback may recycle it which will cause double recycling.
            // To avoid this, we mark it as not recycleable before calling the item animator.
            // Since removeDetachedView calls a user API, a common mistake (ending animations on
            // the view) may recycle it too, so we guard it before we call user APIs.
            vh.setIsRecyclable(false);
            if (vh.isTmpDetached()) {
                mRecyclerView.removeDetachedView(scrap, false);
            }
            if (mRecyclerView.mItemAnimator != null) {
                mRecyclerView.mItemAnimator.endAnimation(vh);
            }
            vh.setIsRecyclable(true);
            recycler.quickRecycleScrapView(scrap);
        }
        recycler.clearScrap();
        if (scrapCount > 0) {
            mRecyclerView.invalidate();
        }
    }


    //测量子view
    public void measureChild(@NonNull View child, int widthUsed, int heightUsed) {
        final RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();

        final Rect insets = mRecyclerView.getItemDecorInsetsForChild(child);
        widthUsed += insets.left + insets.right;
        heightUsed += insets.top + insets.bottom;
        final int widthSpec = getChildMeasureSpec(getWidth(), getWidthMode(),
                getPaddingLeft() + getPaddingRight() + widthUsed, lp.width,
                canScrollHorizontally());
        final int heightSpec = getChildMeasureSpec(getHeight(), getHeightMode(),
                getPaddingTop() + getPaddingBottom() + heightUsed, lp.height,
                canScrollVertically());
        if (shouldMeasureChild(child, widthSpec, heightSpec, lp)) {
            child.measure(widthSpec, heightSpec);
        }
    }


    // we may consider making this public

    /**
     * RecyclerView internally does its own View measurement caching which should help with
     * WRAP_CONTENT.
     * <p>
     * Use this method if the View is not yet measured and you need to decide whether to
     * measure this View or not.
     */
    boolean shouldMeasureChild(View child, int widthSpec, int heightSpec, RecyclerView.LayoutParams lp) {
        return child.isLayoutRequested()
                || !mMeasurementCacheEnabled
                || !isMeasurementUpToDate(child.getWidth(), widthSpec, lp.width)
                || !isMeasurementUpToDate(child.getHeight(), heightSpec, lp.height);
    }

    /**
     * In addition to the View Framework's measurement cache, RecyclerView uses its own
     * additional measurement cache for its children to avoid re-measuring them when not
     * necessary. It is on by default but it can be turned off via
     * {@link #setMeasurementCacheEnabled(boolean)}.
     *
     * @return True if measurement cache is enabled, false otherwise.
     * @see #setMeasurementCacheEnabled(boolean)
     */
    public boolean isMeasurementCacheEnabled() {
        return mMeasurementCacheEnabled;
    }

    /**
     * Sets whether RecyclerView should use its own measurement cache for the children. This is
     * a more aggressive cache than the framework uses.
     *
     * @param measurementCacheEnabled True to enable the measurement cache, false otherwise.
     * @see #isMeasurementCacheEnabled()
     */
    public void setMeasurementCacheEnabled(boolean measurementCacheEnabled) {
        mMeasurementCacheEnabled = measurementCacheEnabled;
    }

    private static boolean isMeasurementUpToDate(int childSize, int spec, int dimension) {
        final int specMode = View.MeasureSpec.getMode(spec);
        final int specSize = View.MeasureSpec.getSize(spec);
        if (dimension > 0 && childSize != dimension) {
            return false;
        }
        switch (specMode) {
            case View.MeasureSpec.UNSPECIFIED:
                return true;
            case View.MeasureSpec.AT_MOST:
                return specSize >= childSize;
            case View.MeasureSpec.EXACTLY:
                return specSize == childSize;
        }
        return false;
    }

    /**
     * Measure a child view using standard measurement policy, taking the padding
     * of the parent RecyclerView, any added item decorations and the child margins
     * into account.
     *
     * <p>If the RecyclerView can be scrolled in either dimension the caller may
     * pass 0 as the widthUsed or heightUsed parameters as they will be irrelevant.</p>
     *
     * @param child      Child view to measure
     * @param widthUsed  Width in pixels currently consumed by other views, if relevant
     * @param heightUsed Height in pixels currently consumed by other views, if relevant
     */
    public void measureChildWithMargins(@NonNull View child, int widthUsed, int heightUsed) {
        final RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();

        final Rect insets = mRecyclerView.getItemDecorInsetsForChild(child);
        widthUsed += insets.left + insets.right;
        heightUsed += insets.top + insets.bottom;

        final int widthSpec = getChildMeasureSpec(getWidth(), getWidthMode(),
                getPaddingLeft() + getPaddingRight()
                        + lp.leftMargin + lp.rightMargin + widthUsed, lp.width,
                canScrollHorizontally());
        final int heightSpec = getChildMeasureSpec(getHeight(), getHeightMode(),
                getPaddingTop() + getPaddingBottom()
                        + lp.topMargin + lp.bottomMargin + heightUsed, lp.height,
                canScrollVertically());
        if (shouldMeasureChild(child, widthSpec, heightSpec, lp)) {
            child.measure(widthSpec, heightSpec);
        }
    }

    /**
     * Calculate a MeasureSpec value for measuring a child view in one dimension.
     *
     * @param parentSize     Size of the parent view where the child will be placed
     * @param padding        Total space currently consumed by other elements of the parent
     * @param childDimension Desired size of the child view, or MATCH_PARENT/WRAP_CONTENT.
     *                       Generally obtained from the child view's LayoutParams
     * @param canScroll      true if the parent RecyclerView can scroll in this dimension
     * @return a MeasureSpec value for the child view
     * @deprecated use {@link #getChildMeasureSpec(int, int, int, int, boolean)}
     */
    @Deprecated
    public static int getChildMeasureSpec(int parentSize, int padding, int childDimension,
                                          boolean canScroll) {
        int size = Math.max(0, parentSize - padding);
        int resultSize = 0;
        int resultMode = 0;
        if (canScroll) {
            if (childDimension >= 0) {
                resultSize = childDimension;
                resultMode = View.MeasureSpec.EXACTLY;
            } else {
                // MATCH_PARENT can't be applied since we can scroll in this dimension, wrap
                // instead using UNSPECIFIED.
                resultSize = 0;
                resultMode = View.MeasureSpec.UNSPECIFIED;
            }
        } else {
            if (childDimension >= 0) {
                resultSize = childDimension;
                resultMode = View.MeasureSpec.EXACTLY;
            } else if (childDimension == RecyclerView.LayoutParams.MATCH_PARENT) {
                resultSize = size;
                // TODO this should be my spec.
                resultMode = View.MeasureSpec.EXACTLY;
            } else if (childDimension == RecyclerView.LayoutParams.WRAP_CONTENT) {
                resultSize = size;
                resultMode = View.MeasureSpec.AT_MOST;
            }
        }
        return View.MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }

    /**
     * Calculate a MeasureSpec value for measuring a child view in one dimension.
     *
     * @param parentSize     Size of the parent view where the child will be placed
     * @param parentMode     The measurement spec mode of the parent
     * @param padding        Total space currently consumed by other elements of parent
     * @param childDimension Desired size of the child view, or MATCH_PARENT/WRAP_CONTENT.
     *                       Generally obtained from the child view's LayoutParams
     * @param canScroll      true if the parent RecyclerView can scroll in this dimension
     * @return a MeasureSpec value for the child view
     */
    public static int getChildMeasureSpec(int parentSize, int parentMode, int padding,
                                          int childDimension, boolean canScroll) {
        int size = Math.max(0, parentSize - padding);
        int resultSize = 0;
        int resultMode = 0;
        if (canScroll) {
            if (childDimension >= 0) {
                resultSize = childDimension;
                resultMode = View.MeasureSpec.EXACTLY;
            } else if (childDimension == RecyclerView.LayoutParams.MATCH_PARENT) {
                switch (parentMode) {
                    case View.MeasureSpec.AT_MOST:
                    case View.MeasureSpec.EXACTLY:
                        resultSize = size;
                        resultMode = parentMode;
                        break;
                    case View.MeasureSpec.UNSPECIFIED:
                        resultSize = 0;
                        resultMode = View.MeasureSpec.UNSPECIFIED;
                        break;
                }
            } else if (childDimension == RecyclerView.LayoutParams.WRAP_CONTENT) {
                resultSize = 0;
                resultMode = View.MeasureSpec.UNSPECIFIED;
            }
        } else {
            if (childDimension >= 0) {
                resultSize = childDimension;
                resultMode = View.MeasureSpec.EXACTLY;
            } else if (childDimension == RecyclerView.LayoutParams.MATCH_PARENT) {
                resultSize = size;
                resultMode = parentMode;
            } else if (childDimension == RecyclerView.LayoutParams.WRAP_CONTENT) {
                resultSize = size;
                if (parentMode == View.MeasureSpec.AT_MOST || parentMode == View.MeasureSpec.EXACTLY) {
                    resultMode = View.MeasureSpec.AT_MOST;
                } else {
                    resultMode = View.MeasureSpec.UNSPECIFIED;
                }

            }
        }
        //noinspection WrongConstant
        return View.MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }


    /**
     * Calculates the bounding box of the View while taking into account its matrix changes
     * (translation, scale etc) with respect to the RecyclerView.
     * <p>
     * If {@code includeDecorInsets} is {@code true}, they are applied first before applying
     * the View's matrix so that the decor offsets also go through the same transformation.
     *
     * @param child              The ItemView whose bounding box should be calculated.
     * @param includeDecorInsets True if the decor insets should be included in the bounding box
     * @param out                The rectangle into which the output will be written.
     */
    public void getTransformedBoundingBox(@NonNull View child, boolean includeDecorInsets,
                                          @NonNull Rect out) {
        if (includeDecorInsets) {
            Rect insets = ((RecyclerView.LayoutParams) child.getLayoutParams()).mDecorInsets;
            out.set(-insets.left, -insets.top,
                    child.getWidth() + insets.right, child.getHeight() + insets.bottom);
        } else {
            out.set(0, 0, child.getWidth(), child.getHeight());
        }

        if (mRecyclerView != null) {
            final Matrix childMatrix = child.getMatrix();
            if (childMatrix != null && !childMatrix.isIdentity()) {
                final RectF tempRectF = mRecyclerView.mTempRectF;
                tempRectF.set(out);
                childMatrix.mapRect(tempRectF);
                out.set(
                        (int) Math.floor(tempRectF.left),
                        (int) Math.floor(tempRectF.top),
                        (int) Math.ceil(tempRectF.right),
                        (int) Math.ceil(tempRectF.bottom)
                );
            }
        }
        out.offset(child.getLeft(), child.getTop());
    }

    /**
     * Returns the bounds of the view including its decoration and margins.
     *
     * @param view      The view element to check
     * @param outBounds A rect that will receive the bounds of the element including its
     *                  decoration and margins.
     */
    public void getDecoratedBoundsWithMargins(@NonNull View view, @NonNull Rect outBounds) {
        RecyclerView.getDecoratedBoundsWithMarginsInt(view, outBounds);
    }

    /**
     * Returns the left edge of the given child view within its parent, offset by any applied
     * {@link RecyclerView.ItemDecoration ItemDecorations}.
     *
     * @param child Child to query
     * @return Child left edge with offsets applied
     * @see #getLeftDecorationWidth(View)
     */
    public int getDecoratedLeft(@NonNull View child) {
        return child.getLeft() - getLeftDecorationWidth(child);
    }

    /**
     * Returns the top edge of the given child view within its parent, offset by any applied
     * {@link RecyclerView.ItemDecoration ItemDecorations}.
     *
     * @param child Child to query
     * @return Child top edge with offsets applied
     * @see #getTopDecorationHeight(View)
     */
    public int getDecoratedTop(@NonNull View child) {
        return child.getTop() - getTopDecorationHeight(child);
    }

    /**
     * Returns the right edge of the given child view within its parent, offset by any applied
     * {@link RecyclerView.ItemDecoration ItemDecorations}.
     *
     * @param child Child to query
     * @return Child right edge with offsets applied
     * @see #getRightDecorationWidth(View)
     */
    public int getDecoratedRight(@NonNull View child) {
        return child.getRight() + getRightDecorationWidth(child);
    }

    /**
     * Returns the bottom edge of the given child view within its parent, offset by any applied
     * {@link RecyclerView.ItemDecoration ItemDecorations}.
     *
     * @param child Child to query
     * @return Child bottom edge with offsets applied
     * @see #getBottomDecorationHeight(View)
     */
    public int getDecoratedBottom(@NonNull View child) {
        return child.getBottom() + getBottomDecorationHeight(child);
    }


    /**
     * Returns the total height of item decorations applied to child's top.
     * <p>
     * Note that this value is not updated until the View is measured or
     * {@link #calculateItemDecorationsForChild(View, Rect)} is called.
     *
     * @param child Child to query
     * @return The total height of item decorations applied to the child's top.
     * @see #getDecoratedTop(View)
     * @see #calculateItemDecorationsForChild(View, Rect)
     */
    public int getTopDecorationHeight(@NonNull View child) {
        return ((RecyclerView.LayoutParams) child.getLayoutParams()).mDecorInsets.top;
    }

    /**
     * Returns the total height of item decorations applied to child's bottom.
     * <p>
     * Note that this value is not updated until the View is measured or
     * {@link #calculateItemDecorationsForChild(View, Rect)} is called.
     *
     * @param child Child to query
     * @return The total height of item decorations applied to the child's bottom.
     * @see #getDecoratedBottom(View)
     * @see #calculateItemDecorationsForChild(View, Rect)
     */
    public int getBottomDecorationHeight(@NonNull View child) {
        return ((RecyclerView.LayoutParams) child.getLayoutParams()).mDecorInsets.bottom;
    }

    /**
     * Returns the total width of item decorations applied to child's left.
     * <p>
     * Note that this value is not updated until the View is measured or
     * {@link #calculateItemDecorationsForChild(View, Rect)} is called.
     *
     * @param child Child to query
     * @return The total width of item decorations applied to the child's left.
     * @see #getDecoratedLeft(View)
     * @see #calculateItemDecorationsForChild(View, Rect)
     */
    public int getLeftDecorationWidth(@NonNull View child) {
        return ((RecyclerView.LayoutParams) child.getLayoutParams()).mDecorInsets.left;
    }

    /**
     * Returns the total width of item decorations applied to child's right.
     * <p>
     * Note that this value is not updated until the View is measured or
     * {@link #calculateItemDecorationsForChild(View, Rect)} is called.
     *
     * @param child Child to query
     * @return The total width of item decorations applied to the child's right.
     * @see #getDecoratedRight(View)
     * @see #calculateItemDecorationsForChild(View, Rect)
     */
    public int getRightDecorationWidth(@NonNull View child) {
        return ((RecyclerView.LayoutParams) child.getLayoutParams()).mDecorInsets.right;
    }

    /**
     * Called when searching for a focusable view in the given direction has failed
     * for the current content of the RecyclerView.
     *
     * <p>This is the LayoutManager's opportunity to populate views in the given direction
     * to fulfill the request if it can. The LayoutManager should attach and return
     * the view to be focused, if a focusable view in the given direction is found.
     * Otherwise, if all the existing (or the newly populated views) are unfocusable, it returns
     * the next unfocusable view to become visible on the screen. This unfocusable view is
     * typically the first view that's either partially or fully out of RV's padded bounded
     * area in the given direction. The default implementation returns null.</p>
     *
     * @param focused   The currently focused view
     * @param direction One of {@link View#FOCUS_UP}, {@link View#FOCUS_DOWN},
     *                  {@link View#FOCUS_LEFT}, {@link View#FOCUS_RIGHT},
     *                  {@link View#FOCUS_BACKWARD}, {@link View#FOCUS_FORWARD}
     *                  or 0 for not applicable
     * @param recycler  The recycler to use for obtaining views for currently offscreen items
     * @param state     Transient state of RecyclerView
     * @return The chosen view to be focused if a focusable view is found, otherwise an
     * unfocusable view to become visible onto the screen, else null.
     */
    @Nullable
    public View onFocusSearchFailed(@NonNull View focused, int direction,
                                    @NonNull Recycler recycler, @NonNull RecyclerView.State state) {
        return null;
    }

    /**
     * This method gives a LayoutManager an opportunity to intercept the initial focus search
     * before the default behavior of {@link FocusFinder} is used. If this method returns
     * null FocusFinder will attempt to find a focusable child view. If it fails
     * then {@link #onFocusSearchFailed(View, int, Recycler, RecyclerView.State)}
     * will be called to give the LayoutManager an opportunity to add new views for items
     * that did not have attached views representing them. The LayoutManager should not add
     * or remove views from this method.
     *
     * @param focused   The currently focused view
     * @param direction One of {@link View#FOCUS_UP}, {@link View#FOCUS_DOWN},
     *                  {@link View#FOCUS_LEFT}, {@link View#FOCUS_RIGHT},
     *                  {@link View#FOCUS_BACKWARD}, {@link View#FOCUS_FORWARD}
     * @return A descendant view to focus or null to fall back to default behavior.
     * The default implementation returns null.
     */
    @Nullable
    public View onInterceptFocusSearch(@NonNull View focused, int direction) {
        return null;
    }

    /**
     * Returns the scroll amount that brings the given rect in child's coordinate system within
     * the padded area of RecyclerView.
     *
     * @param child The direct child making the request.
     * @param rect  The rectangle in the child's coordinates the child
     *              wishes to be on the screen.
     * @return The array containing the scroll amount in x and y directions that brings the
     * given rect into RV's padded area.
     */
    private int[] getChildRectangleOnScreenScrollAmount(View child, Rect rect) {
        int[] out = new int[2];
        final int parentLeft = getPaddingLeft();
        final int parentTop = getPaddingTop();
        final int parentRight = getWidth() - getPaddingRight();
        final int parentBottom = getHeight() - getPaddingBottom();
        final int childLeft = child.getLeft() + rect.left - child.getScrollX();
        final int childTop = child.getTop() + rect.top - child.getScrollY();
        final int childRight = childLeft + rect.width();
        final int childBottom = childTop + rect.height();

        final int offScreenLeft = Math.min(0, childLeft - parentLeft);
        final int offScreenTop = Math.min(0, childTop - parentTop);
        final int offScreenRight = Math.max(0, childRight - parentRight);
        final int offScreenBottom = Math.max(0, childBottom - parentBottom);

        // Favor the "start" layout direction over the end when bringing one side or the other
        // of a large rect into view. If we decide to bring in end because start is already
        // visible, limit the scroll such that start won't go out of bounds.
        final int dx;
        if (getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL) {
            dx = offScreenRight != 0 ? offScreenRight
                    : Math.max(offScreenLeft, childRight - parentRight);
        } else {
            dx = offScreenLeft != 0 ? offScreenLeft
                    : Math.min(childLeft - parentLeft, offScreenRight);
        }

        // Favor bringing the top into view over the bottom. If top is already visible and
        // we should scroll to make bottom visible, make sure top does not go out of bounds.
        final int dy = offScreenTop != 0 ? offScreenTop
                : Math.min(childTop - parentTop, offScreenBottom);
        out[0] = dx;
        out[1] = dy;
        return out;
    }

    /**
     * Called when a child of the RecyclerView wants a particular rectangle to be positioned
     * onto the screen. See {@link ViewParent#requestChildRectangleOnScreen(View,
     * Rect, boolean)} for more details.
     *
     * <p>The base implementation will attempt to perform a standard programmatic scroll
     * to bring the given rect into view, within the padded area of the RecyclerView.</p>
     *
     * @param child     The direct child making the request.
     * @param rect      The rectangle in the child's coordinates the child
     *                  wishes to be on the screen.
     * @param immediate True to forbid animated or delayed scrolling,
     *                  false otherwise
     * @return Whether the group scrolled to handle the operation
     */
    public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent,
                                                 @NonNull View child, @NonNull Rect rect, boolean immediate) {
        return requestChildRectangleOnScreen(parent, child, rect, immediate, false);
    }

    /**
     * Requests that the given child of the RecyclerView be positioned onto the screen. This
     * method can be called for both unfocusable and focusable child views. For unfocusable
     * child views, focusedChildVisible is typically true in which case, layout manager
     * makes the child view visible only if the currently focused child stays in-bounds of RV.
     *
     * @param parent              The parent RecyclerView.
     * @param child               The direct child making the request.
     * @param rect                The rectangle in the child's coordinates the child
     *                            wishes to be on the screen.
     * @param immediate           True to forbid animated or delayed scrolling,
     *                            false otherwise
     * @param focusedChildVisible Whether the currently focused view must stay visible.
     * @return Whether the group scrolled to handle the operation
     */
    public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent,
                                                 @NonNull View child, @NonNull Rect rect, boolean immediate,
                                                 boolean focusedChildVisible) {
        int[] scrollAmount = getChildRectangleOnScreenScrollAmount(child, rect
        );
        int dx = scrollAmount[0];
        int dy = scrollAmount[1];
        if (!focusedChildVisible || isFocusedChildVisibleAfterScrolling(parent, dx, dy)) {
            if (dx != 0 || dy != 0) {
                if (immediate) {
                    parent.scrollBy(dx, dy);
                } else {
                    parent.smoothScrollBy(dx, dy);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the given child view is partially or fully visible within the padded
     * bounded area of RecyclerView, depending on the input parameters.
     * A view is partially visible if it has non-zero overlap with RV's padded bounded area.
     * If acceptEndPointInclusion flag is set to true, it's also considered partially
     * visible if it's located outside RV's bounds and it's hitting either RV's start or end
     * bounds.
     *
     * @param child                   The child view to be examined.
     * @param completelyVisible       If true, the method returns true if and only if the
     *                                child is
     *                                completely visible. If false, the method returns true
     *                                if and
     *                                only if the child is only partially visible (that is it
     *                                will
     *                                return false if the child is either completely visible
     *                                or out
     *                                of RV's bounds).
     * @param acceptEndPointInclusion If the view's endpoint intersection with RV's start of end
     *                                bounds is enough to consider it partially visible,
     *                                false otherwise.
     * @return True if the given child is partially or fully visible, false otherwise.
     */
    public boolean isViewPartiallyVisible(@NonNull View child, boolean completelyVisible,
                                          boolean acceptEndPointInclusion) {
        int boundsFlag = (ViewBoundsCheck.FLAG_CVS_GT_PVS | ViewBoundsCheck.FLAG_CVS_EQ_PVS
                | ViewBoundsCheck.FLAG_CVE_LT_PVE | ViewBoundsCheck.FLAG_CVE_EQ_PVE);
        boolean isViewFullyVisible = mHorizontalBoundCheck.isViewWithinBoundFlags(child,
                boundsFlag)
                && mVerticalBoundCheck.isViewWithinBoundFlags(child, boundsFlag);
        if (completelyVisible) {
            return isViewFullyVisible;
        } else {
            return !isViewFullyVisible;
        }
    }

    /**
     * Returns whether the currently focused child stays within RV's bounds with the given
     * amount of scrolling.
     *
     * @param parent The parent RecyclerView.
     * @param dx     The scrolling in x-axis direction to be performed.
     * @param dy     The scrolling in y-axis direction to be performed.
     * @return {@code false} if the focused child is not at least partially visible after
     * scrolling or no focused child exists, {@code true} otherwise.
     */
    private boolean isFocusedChildVisibleAfterScrolling(RecyclerView parent, int dx, int dy) {
        final View focusedChild = parent.getFocusedChild();
        if (focusedChild == null) {
            return false;
        }
        final int parentLeft = getPaddingLeft();
        final int parentTop = getPaddingTop();
        final int parentRight = getWidth() - getPaddingRight();
        final int parentBottom = getHeight() - getPaddingBottom();
        final Rect bounds = mRecyclerView.mTempRect;
        getDecoratedBoundsWithMargins(focusedChild, bounds);

        if (bounds.left - dx >= parentRight || bounds.right - dx <= parentLeft
                || bounds.top - dy >= parentBottom || bounds.bottom - dy <= parentTop) {
            return false;
        }
        return true;
    }

    /**
     * @deprecated Use {@link #onRequestChildFocus(RecyclerView, RecyclerView.State, View, View)}
     */
    @Deprecated
    public boolean onRequestChildFocus(@NonNull RecyclerView parent, @NonNull View child,
                                       @Nullable View focused) {
        // eat the request if we are in the middle of a scroll or layout
        return isSmoothScrolling() || parent.isComputingLayout();
    }

    /**
     * Called when a descendant view of the RecyclerView requests focus.
     *
     * <p>A LayoutManager wishing to keep focused views aligned in a specific
     * portion of the view may implement that behavior in an override of this method.</p>
     *
     * <p>If the LayoutManager executes different behavior that should override the default
     * behavior of scrolling the focused child on screen instead of running alongside it,
     * this method should return true.</p>
     *
     * @param parent  The RecyclerView hosting this LayoutManager
     * @param state   Current state of RecyclerView
     * @param child   Direct child of the RecyclerView containing the newly focused view
     * @param focused The newly focused view. This may be the same view as child or it may be
     *                null
     * @return true if the default scroll behavior should be suppressed
     */
    public boolean onRequestChildFocus(@NonNull RecyclerView parent, @NonNull RecyclerView.State state,
                                       @NonNull View child, @Nullable View focused) {
        return onRequestChildFocus(parent, child, focused);
    }

    /**
     * Called if the RecyclerView this LayoutManager is bound to has a different adapter set via
     * {@link RecyclerView#setAdapter(Adapter)} or
     * {@link RecyclerView#swapAdapter(Adapter, boolean)}. The LayoutManager may use this
     * opportunity to clear caches and configure state such that it can relayout appropriately
     * with the new data and potentially new view types.
     *
     * <p>The default implementation removes all currently attached views.</p>
     *
     * @param oldAdapter The previous adapter instance. Will be null if there was previously no
     *                   adapter.
     * @param newAdapter The new adapter instance. Might be null if
     *                   {@link RecyclerView#setAdapter(Adapter)} is called with
     *                   {@code null}.
     */
    public void onAdapterChanged(@Nullable Adapter oldAdapter, @Nullable Adapter newAdapter) {
    }

    /**
     * Called to populate focusable views within the RecyclerView.
     *
     * <p>The LayoutManager implementation should return <code>true</code> if the default
     * behavior of {@link ViewGroup#addFocusables(ArrayList, int)} should be
     * suppressed.</p>
     *
     * <p>The default implementation returns <code>false</code> to trigger RecyclerView
     * to fall back to the default ViewGroup behavior.</p>
     *
     * @param recyclerView  The RecyclerView hosting this LayoutManager
     * @param views         List of output views. This method should add valid focusable views
     *                      to this list.
     * @param direction     One of {@link View#FOCUS_UP}, {@link View#FOCUS_DOWN},
     *                      {@link View#FOCUS_LEFT}, {@link View#FOCUS_RIGHT},
     *                      {@link View#FOCUS_BACKWARD}, {@link View#FOCUS_FORWARD}
     * @param focusableMode The type of focusables to be added.
     * @return true to suppress the default behavior, false to add default focusables after
     * this method returns.
     * @see #FOCUSABLES_ALL
     * @see #FOCUSABLES_TOUCH_MODE
     */
    public boolean onAddFocusables(@NonNull RecyclerView recyclerView,
                                   @NonNull ArrayList<View> views, int direction, int focusableMode) {
        return false;
    }

    /**
     * Called in response to a call to {@link Adapter#notifyDataSetChanged()} or
     * {@link RecyclerView#swapAdapter(Adapter, boolean)} ()} and signals that the the entire
     * data set has changed.
     */
    public void onItemsChanged(@NonNull RecyclerView recyclerView) {
    }

    /**
     * Called when items have been added to the adapter. The LayoutManager may choose to
     * requestLayout if the inserted items would require refreshing the currently visible set
     * of child views. (e.g. currently empty space would be filled by appended items, etc.)
     */
    public void onItemsAdded(@NonNull RecyclerView recyclerView, int positionStart,
                             int itemCount) {
    }

    /**
     * Called when items have been removed from the adapter.
     */
    public void onItemsRemoved(@NonNull RecyclerView recyclerView, int positionStart,
                               int itemCount) {
    }

    /**
     * Called when items have been changed in the adapter.
     * To receive payload,  override {@link #onItemsUpdated(RecyclerView, int, int, Object)}
     * instead, then this callback will not be invoked.
     */
    public void onItemsUpdated(@NonNull RecyclerView recyclerView, int positionStart,
                               int itemCount) {
    }

    /**
     * Called when items have been changed in the adapter and with optional payload.
     * Default implementation calls {@link #onItemsUpdated(RecyclerView, int, int)}.
     */
    public void onItemsUpdated(@NonNull RecyclerView recyclerView, int positionStart,
                               int itemCount, @Nullable Object payload) {
        onItemsUpdated(recyclerView, positionStart, itemCount);
    }

    /**
     * Called when an item is moved withing the adapter.
     * <p>
     * Note that, an item may also change position in response to another ADD/REMOVE/MOVE
     * operation. This callback is only called if and only if {@link Adapter#notifyItemMoved}
     * is called.
     */
    public void onItemsMoved(@NonNull RecyclerView recyclerView, int from, int to,
                             int itemCount) {

    }


    /**
     * <p>Override this method if you want to support scroll bars.</p>
     *
     * <p>Read {@link RecyclerView#computeHorizontalScrollExtent()} for details.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * @param state Current state of RecyclerView
     * @return The horizontal extent of the scrollbar's thumb
     * @see RecyclerView#computeHorizontalScrollExtent()
     */
    public int computeHorizontalScrollExtent(@NonNull RecyclerView.State state) {
        return 0;
    }

    /**
     * <p>Override this method if you want to support scroll bars.</p>
     *
     * <p>Read {@link RecyclerView#computeHorizontalScrollOffset()} for details.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * @param state Current State of RecyclerView where you can find total item count
     * @return The horizontal offset of the scrollbar's thumb
     * @see RecyclerView#computeHorizontalScrollOffset()
     */
    public int computeHorizontalScrollOffset(@NonNull RecyclerView.State state) {
        return 0;
    }

    /**
     * <p>Override this method if you want to support scroll bars.</p>
     *
     * <p>Read {@link RecyclerView#computeHorizontalScrollRange()} for details.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * @param state Current State of RecyclerView where you can find total item count
     * @return The total horizontal range represented by the vertical scrollbar
     * @see RecyclerView#computeHorizontalScrollRange()
     */
    public int computeHorizontalScrollRange(@NonNull RecyclerView.State state) {
        return 0;
    }

    /**
     * <p>Override this method if you want to support scroll bars.</p>
     *
     * <p>Read {@link RecyclerView#computeVerticalScrollExtent()} for details.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * @param state Current state of RecyclerView
     * @return The vertical extent of the scrollbar's thumb
     * @see RecyclerView#computeVerticalScrollExtent()
     */
    public int computeVerticalScrollExtent(@NonNull RecyclerView.State state) {
        return 0;
    }

    /**
     * <p>Override this method if you want to support scroll bars.</p>
     *
     * <p>Read {@link RecyclerView#computeVerticalScrollOffset()} for details.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * @param state Current State of RecyclerView where you can find total item count
     * @return The vertical offset of the scrollbar's thumb
     * @see RecyclerView#computeVerticalScrollOffset()
     */
    public int computeVerticalScrollOffset(@NonNull RecyclerView.State state) {
        return 0;
    }

    /**
     * <p>Override this method if you want to support scroll bars.</p>
     *
     * <p>Read {@link RecyclerView#computeVerticalScrollRange()} for details.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * @param state Current State of RecyclerView where you can find total item count
     * @return The total vertical range represented by the vertical scrollbar
     * @see RecyclerView#computeVerticalScrollRange()
     */
    public int computeVerticalScrollRange(@NonNull RecyclerView.State state) {
        return 0;
    }

    /**
     * Measure the attached RecyclerView. Implementations must call
     * {@link #setMeasuredDimension(int, int)} before returning.
     * <p>
     * It is strongly advised to use the AutoMeasure mechanism by overriding
     * {@link #isAutoMeasureEnabled()} to return true as AutoMeasure handles all the standard
     * measure cases including when the RecyclerView's layout_width or layout_height have been
     * set to wrap_content.  If {@link #isAutoMeasureEnabled()} is overridden to return true,
     * this method should not be overridden.
     * <p>
     * The default implementation will handle EXACTLY measurements and respect
     * the minimum width and height properties of the host RecyclerView if measured
     * as UNSPECIFIED. AT_MOST measurements will be treated as EXACTLY and the RecyclerView
     * will consume all available space.
     *
     * @param recycler   Recycler
     * @param state      Transient state of RecyclerView
     * @param widthSpec  Width {@link View.MeasureSpec}
     * @param heightSpec Height {@link View.MeasureSpec}
     * @see #isAutoMeasureEnabled()
     * @see #setMeasuredDimension(int, int)
     */
    public void onMeasure(@NonNull Recycler recycler, @NonNull RecyclerView.State state, int widthSpec,
                          int heightSpec) {
        mRecyclerView.defaultOnMeasure(widthSpec, heightSpec);
    }

    /**
     * {@link View#setMeasuredDimension(int, int) Set the measured dimensions} of the
     * host RecyclerView.
     *
     * @param widthSize  Measured width
     * @param heightSize Measured height
     */
    public void setMeasuredDimension(int widthSize, int heightSize) {
        mRecyclerView.setMeasuredDimension(widthSize, heightSize);
    }

    /**
     * @return The host RecyclerView's {@link View#getMinimumWidth()}
     */
    @Px
    public int getMinimumWidth() {
        return ViewCompat.getMinimumWidth(mRecyclerView);
    }

    /**
     * @return The host RecyclerView's {@link View#getMinimumHeight()}
     */
    @Px
    public int getMinimumHeight() {
        return ViewCompat.getMinimumHeight(mRecyclerView);
    }

    /**
     * <p>Called when the LayoutManager should save its state. This is a good time to save your
     * scroll position, configuration and anything else that may be required to restore the same
     * layout state if the LayoutManager is recreated.</p>
     * <p>RecyclerView does NOT verify if the LayoutManager has changed between state save and
     * restore. This will let you share information between your LayoutManagers but it is also
     * your responsibility to make sure they use the same parcelable class.</p>
     *
     * @return Necessary information for LayoutManager to be able to restore its state
     */
    @Nullable
    public Parcelable onSaveInstanceState() {
        return null;
    }

    /**
     * Called when the RecyclerView is ready to restore the state based on a previous
     * RecyclerView.
     * <p>
     * Notice that this might happen after an actual layout, based on how Adapter prefers to
     * restore State. See {@link Adapter#getStateRestorationPolicy()} for more information.
     *
     * @param state The parcelable that was returned by the previous LayoutManager's
     *              {@link #onSaveInstanceState()} method.
     */
    public void onRestoreInstanceState(Parcelable state) {

    }

    void stopSmoothScroller() {
        if (mSmoothScroller != null) {
            mSmoothScroller.stop();
        }
    }

    void onSmoothScrollerStopped(RecyclerView.SmoothScroller smoothScroller) {
        if (mSmoothScroller == smoothScroller) {
            mSmoothScroller = null;
        }
    }

    /**
     * RecyclerView calls this method to notify LayoutManager that scroll state has changed.
     *
     * @param state The new scroll state for RecyclerView
     */
    public void onScrollStateChanged(int state) {
    }

    /**
     * Removes all views and recycles them using the given recycler.
     * <p>
     * If you want to clean cached views as well, you should call {@link Recycler#clear()} too.
     * <p>
     * If a View is marked as "ignored", it is not removed nor recycled.
     *
     * @param recycler Recycler to use to recycle children
     * @see #removeAndRecycleView(View, Recycler)
     * @see #removeAndRecycleViewAt(int, Recycler)
     * @see #ignoreView(View)
     */
    public void removeAndRecycleAllViews(@NonNull Recycler recycler) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            final View view = getChildAt(i);
            if (!getChildViewHolderInt(view).shouldIgnore()) {
                removeAndRecycleViewAt(i, recycler);
            }
        }
    }

    // called by accessibility delegate
    void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfoCompat info) {
        onInitializeAccessibilityNodeInfo(mRecyclerView.mRecycler, mRecyclerView.mState, info);
    }

    /**
     * Called by the AccessibilityDelegate when the information about the current layout should
     * be populated.
     * <p>
     * Default implementation adds a {@link
     * AccessibilityNodeInfoCompat.CollectionInfoCompat}.
     * <p>
     * You should override
     * {@link #getRowCountForAccessibility(Recycler, RecyclerView.State)},
     * {@link #getColumnCountForAccessibility(Recycler, RecyclerView.State)},
     * {@link #isLayoutHierarchical(Recycler, RecyclerView.State)} and
     * {@link #getSelectionModeForAccessibility(Recycler, RecyclerView.State)} for
     * more accurate accessibility information.
     *
     * @param recycler The Recycler that can be used to convert view positions into adapter
     *                 positions
     * @param state    The current state of RecyclerView
     * @param info     The info that should be filled by the LayoutManager
     * @see View#onInitializeAccessibilityNodeInfo(
     *android.view.accessibility.AccessibilityNodeInfo)
     * @see #getRowCountForAccessibility(Recycler, RecyclerView.State)
     * @see #getColumnCountForAccessibility(Recycler, RecyclerView.State)
     * @see #isLayoutHierarchical(Recycler, RecyclerView.State)
     * @see #getSelectionModeForAccessibility(Recycler, RecyclerView.State)
     */
    public void onInitializeAccessibilityNodeInfo(@NonNull Recycler recycler,
                                                  @NonNull RecyclerView.State state, @NonNull AccessibilityNodeInfoCompat info) {
        if (mRecyclerView.canScrollVertically(-1) || mRecyclerView.canScrollHorizontally(-1)) {
            info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
            info.setScrollable(true);
        }
        if (mRecyclerView.canScrollVertically(1) || mRecyclerView.canScrollHorizontally(1)) {
            info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
            info.setScrollable(true);
        }
        final AccessibilityNodeInfoCompat.CollectionInfoCompat collectionInfo =
                AccessibilityNodeInfoCompat.CollectionInfoCompat
                        .obtain(getRowCountForAccessibility(recycler, state),
                                getColumnCountForAccessibility(recycler, state),
                                isLayoutHierarchical(recycler, state),
                                getSelectionModeForAccessibility(recycler, state));
        info.setCollectionInfo(collectionInfo);
    }

    // called by accessibility delegate
    public void onInitializeAccessibilityEvent(@NonNull AccessibilityEvent event) {
        onInitializeAccessibilityEvent(mRecyclerView.mRecycler, mRecyclerView.mState, event);
    }

    /**
     * Called by the accessibility delegate to initialize an accessibility event.
     * <p>
     * Default implementation adds item count and scroll information to the event.
     *
     * @param recycler The Recycler that can be used to convert view positions into adapter
     *                 positions
     * @param state    The current state of RecyclerView
     * @param event    The event instance to initialize
     * @see View#onInitializeAccessibilityEvent(AccessibilityEvent)
     */
    public void onInitializeAccessibilityEvent(@NonNull Recycler recycler, @NonNull RecyclerView.State state,
                                               @NonNull AccessibilityEvent event) {
        if (mRecyclerView == null || event == null) {
            return;
        }
        event.setScrollable(mRecyclerView.canScrollVertically(1)
                || mRecyclerView.canScrollVertically(-1)
                || mRecyclerView.canScrollHorizontally(-1)
                || mRecyclerView.canScrollHorizontally(1));

        if (mRecyclerView.mAdapter != null) {
            event.setItemCount(mRecyclerView.mAdapter.getItemCount());
        }
    }

    // called by accessibility delegate
    void onInitializeAccessibilityNodeInfoForItem(View host, AccessibilityNodeInfoCompat info) {
        final ViewHolder vh = getChildViewHolderInt(host);
        // avoid trying to create accessibility node info for removed children
        if (vh != null && !vh.isRemoved() && !mChildHelper.isHidden(vh.itemView)) {
            onInitializeAccessibilityNodeInfoForItem(mRecyclerView.mRecycler,
                    mRecyclerView.mState, host, info);
        }
    }

    /**
     * Called by the AccessibilityDelegate when the accessibility information for a specific
     * item should be populated.
     * <p>
     * Default implementation adds basic positioning information about the item.
     *
     * @param recycler The Recycler that can be used to convert view positions into adapter
     *                 positions
     * @param state    The current state of RecyclerView
     * @param host     The child for which accessibility node info should be populated
     * @param info     The info to fill out about the item
     * @see android.widget.AbsListView#onInitializeAccessibilityNodeInfoForItem(View, int,
     * android.view.accessibility.AccessibilityNodeInfo)
     */
    public void onInitializeAccessibilityNodeInfoForItem(@NonNull Recycler recycler,
                                                         @NonNull RecyclerView.State state, @NonNull View host,
                                                         @NonNull AccessibilityNodeInfoCompat info) {
    }

    /**
     * A LayoutManager can call this method to force RecyclerView to run simple animations in
     * the next layout pass, even if there is not any trigger to do so. (e.g. adapter data
     * change).
     * <p>
     * Note that, calling this method will not guarantee that RecyclerView will run animations
     * at all. For example, if there is not any {@link ItemAnimator} set, RecyclerView will
     * not run any animations but will still clear this flag after the layout is complete.
     */
    public void requestSimpleAnimationsInNextLayout() {
        mRequestedSimpleAnimations = true;
    }

    /**
     * Returns the selection mode for accessibility. Should be
     * {@link AccessibilityNodeInfoCompat.CollectionInfoCompat#SELECTION_MODE_NONE},
     * {@link AccessibilityNodeInfoCompat.CollectionInfoCompat#SELECTION_MODE_SINGLE} or
     * {@link AccessibilityNodeInfoCompat.CollectionInfoCompat#SELECTION_MODE_MULTIPLE}.
     * <p>
     * Default implementation returns
     * {@link AccessibilityNodeInfoCompat.CollectionInfoCompat#SELECTION_MODE_NONE}.
     *
     * @param recycler The Recycler that can be used to convert view positions into adapter
     *                 positions
     * @param state    The current state of RecyclerView
     * @return Selection mode for accessibility. Default implementation returns
     * {@link AccessibilityNodeInfoCompat.CollectionInfoCompat#SELECTION_MODE_NONE}.
     */
    public int getSelectionModeForAccessibility(@NonNull Recycler recycler,
                                                @NonNull RecyclerView.State state) {
        return AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_NONE;
    }

    /**
     * Returns the number of rows for accessibility.
     * <p>
     * Default implementation returns the number of items in the adapter if LayoutManager
     * supports vertical scrolling or 1 if LayoutManager does not support vertical
     * scrolling.
     *
     * @param recycler The Recycler that can be used to convert view positions into adapter
     *                 positions
     * @param state    The current state of RecyclerView
     * @return The number of rows in LayoutManager for accessibility.
     */
    public int getRowCountForAccessibility(@NonNull Recycler recycler, @NonNull RecyclerView.State state) {
        return -1;
    }

    /**
     * Returns the number of columns for accessibility.
     * <p>
     * Default implementation returns the number of items in the adapter if LayoutManager
     * supports horizontal scrolling or 1 if LayoutManager does not support horizontal
     * scrolling.
     *
     * @param recycler The Recycler that can be used to convert view positions into adapter
     *                 positions
     * @param state    The current state of RecyclerView
     * @return The number of rows in LayoutManager for accessibility.
     */
    public int getColumnCountForAccessibility(@NonNull Recycler recycler,
                                              @NonNull RecyclerView.State state) {
        return -1;
    }

    /**
     * Returns whether layout is hierarchical or not to be used for accessibility.
     * <p>
     * Default implementation returns false.
     *
     * @param recycler The Recycler that can be used to convert view positions into adapter
     *                 positions
     * @param state    The current state of RecyclerView
     * @return True if layout is hierarchical.
     */
    public boolean isLayoutHierarchical(@NonNull Recycler recycler, @NonNull RecyclerView.State state) {
        return false;
    }

    // called by accessibility delegate
    boolean performAccessibilityAction(int action, @Nullable Bundle args) {
        return performAccessibilityAction(mRecyclerView.mRecycler, mRecyclerView.mState,
                action, args);
    }

    /**
     * Called by AccessibilityDelegate when an action is requested from the RecyclerView.
     *
     * @param recycler The Recycler that can be used to convert view positions into adapter
     *                 positions
     * @param state    The current state of RecyclerView
     * @param action   The action to perform
     * @param args     Optional action arguments
     * @see View#performAccessibilityAction(int, Bundle)
     */
    public boolean performAccessibilityAction(@NonNull Recycler recycler, @NonNull RecyclerView.State state,
                                              int action, @Nullable Bundle args) {
        if (mRecyclerView == null) {
            return false;
        }
        int vScroll = 0, hScroll = 0;
        switch (action) {
            case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
                if (mRecyclerView.canScrollVertically(-1)) {
                    vScroll = -(getHeight() - getPaddingTop() - getPaddingBottom());
                }
                if (mRecyclerView.canScrollHorizontally(-1)) {
                    hScroll = -(getWidth() - getPaddingLeft() - getPaddingRight());
                }
                break;
            case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
                if (mRecyclerView.canScrollVertically(1)) {
                    vScroll = getHeight() - getPaddingTop() - getPaddingBottom();
                }
                if (mRecyclerView.canScrollHorizontally(1)) {
                    hScroll = getWidth() - getPaddingLeft() - getPaddingRight();
                }
                break;
        }
        if (vScroll == 0 && hScroll == 0) {
            return false;
        }
        mRecyclerView.smoothScrollBy(hScroll, vScroll, null, UNDEFINED_DURATION, true);
        return true;
    }

    // called by accessibility delegate
    boolean performAccessibilityActionForItem(@NonNull View view, int action,
                                              @Nullable Bundle args) {
        return performAccessibilityActionForItem(mRecyclerView.mRecycler, mRecyclerView.mState,
                view, action, args);
    }

    /**
     * Called by AccessibilityDelegate when an accessibility action is requested on one of the
     * children of LayoutManager.
     * <p>
     * Default implementation does not do anything.
     *
     * @param recycler The Recycler that can be used to convert view positions into adapter
     *                 positions
     * @param state    The current state of RecyclerView
     * @param view     The child view on which the action is performed
     * @param action   The action to perform
     * @param args     Optional action arguments
     * @return true if action is handled
     * @see View#performAccessibilityAction(int, Bundle)
     */
    public boolean performAccessibilityActionForItem(@NonNull Recycler recycler,
                                                     @NonNull RecyclerView.State state, @NonNull View view, int action, @Nullable Bundle args) {
        return false;
    }

    /**
     * Parse the xml attributes to get the most common properties used by layout managers.
     * <p>
     * {@link android.R.attr#orientation}
     * {@link androidx.recyclerview.R.attr#spanCount}
     * {@link androidx.recyclerview.R.attr#reverseLayout}
     * {@link androidx.recyclerview.R.attr#stackFromEnd}
     *
     * @return an object containing the properties as specified in the attrs.
     */
    public static Properties getProperties(@NonNull Context context,
                                           @Nullable AttributeSet attrs,
                                           int defStyleAttr, int defStyleRes) {
        Properties properties = new Properties();
        TypedArray a = context.obtainStyledAttributes(attrs, androidx.recyclerview.R.styleable.RecyclerView,
                defStyleAttr, defStyleRes);
        properties.orientation = a.getInt(androidx.recyclerview.R.styleable.RecyclerView_android_orientation,
                DEFAULT_ORIENTATION);
        properties.spanCount = a.getInt(androidx.recyclerview.R.styleable.RecyclerView_spanCount, 1);
        properties.reverseLayout = a.getBoolean(androidx.recyclerview.R.styleable.RecyclerView_reverseLayout, false);
        properties.stackFromEnd = a.getBoolean(androidx.recyclerview.R.styleable.RecyclerView_stackFromEnd, false);
        a.recycle();
        return properties;
    }

    void setExactMeasureSpecsFrom(RecyclerView recyclerView) {
        setMeasureSpecs(
                View.MeasureSpec.makeMeasureSpec(recyclerView.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(recyclerView.getHeight(), View.MeasureSpec.EXACTLY)
        );
    }

    /**
     * Internal API to allow LayoutManagers to be measured twice.
     * <p>
     * This is not public because LayoutManagers should be able to handle their layouts in one
     * pass but it is very convenient to make existing LayoutManagers support wrapping content
     * when both orientations are undefined.
     * <p>
     * This API will be removed after default LayoutManagers properly implement wrap content in
     * non-scroll orientation.
     */
    boolean shouldMeasureTwice() {
        return false;
    }

    boolean hasFlexibleChildInBothOrientations() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp.width < 0 && lp.height < 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Some general properties that a LayoutManager may want to use.
     */
    public static class Properties {
        /**
         * {@link android.R.attr#orientation}
         */
        public int orientation;
        /**
         * {@link androidx.recyclerview.R.attr#spanCount}
         */
        public int spanCount;
        /**
         * {@link androidx.recyclerview.R.attr#reverseLayout}
         */
        public boolean reverseLayout;
        /**
         * {@link androidx.recyclerview.R.attr#stackFromEnd}
         */
        public boolean stackFromEnd;
    }
}
