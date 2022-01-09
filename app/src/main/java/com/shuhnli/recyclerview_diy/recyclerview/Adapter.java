package com.shuhnli.recyclerview_diy.recyclerview;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.TraceCompat;

import java.util.List;

/**
 * 适配器提供了从特定于应用程序的数据集到在RecyclerView中显示的视图的绑定
 */
public abstract class Adapter<VH extends ViewHolder> {
    private final RecyclerView.AdapterDataObservable mObservable = new RecyclerView.AdapterDataObservable();
    //某一position对应的ID是否固定不变
    private boolean mHasStableIds = false;
    private StateRestorationPolicy mStateRestorationPolicy = StateRestorationPolicy.ALLOW;

    /**
     * 创建一个给定类型的新ViewHolder
     * 这个新的ViewHolder应该用一个新的View构造，该View可以表示给定类型的项。
     * 您可以手动创建一个新的视图，也可以从XML布局文件中扩展它。
     */
    @NonNull
    public abstract VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType);

    /**
     * 更新ViewHolder的内容
     */
    public abstract void onBindViewHolder(@NonNull VH holder, int position);


    public void onBindViewHolder(@NonNull VH holder, int position,
                                 @NonNull List<Object> payloads) {
        onBindViewHolder(holder, position);
    }


    /**
     * 创建一个新的ViewHolder，并初始化一些供RecyclerView使用的私有字段。
     */
    @NonNull
    public final VH createViewHolder(@NonNull ViewGroup parent, int viewType) {
        try {
            TraceCompat.beginSection("TRACE_CREATE_VIEW_TAG");
            final VH holder = onCreateViewHolder(parent, viewType);
            if (holder.itemView.getParent() != null) {
                throw new IllegalStateException("ViewHolder views must not be attached when"
                        + " created. Ensure that you are not passing 'true' to the attachToRoot"
                        + " parameter of LayoutInflater.inflate(..., boolean attachToRoot)");
            }
            holder.mItemViewType = viewType;
            return holder;
        } finally {
            TraceCompat.endSection();
        }
    }

    //把viewHolder设置到对应位置上
    public final void bindViewHolder(@NonNull VH holder, int position) {
        //给定的viewHolder现在没有绑定在任何adapter上
        boolean rootBind = holder.mBindingAdapter == null;
        if (rootBind) {
            holder.mPosition = position;
            if (hasStableIds()) {
                holder.mItemId = getItemId(position);
            }
            holder.setFlags(ViewHolder.FLAG_BOUND,
                    ViewHolder.FLAG_BOUND | ViewHolder.FLAG_UPDATE | ViewHolder.FLAG_INVALID
                            | ViewHolder.FLAG_ADAPTER_POSITION_UNKNOWN);
            TraceCompat.beginSection("TRACE_BIND_VIEW_TAG");
        }
        //绑定到当前adapter上
        holder.mBindingAdapter = this;
        onBindViewHolder(holder, position, holder.getUnmodifiedPayloads());
        if (rootBind) {
            holder.clearPayload();
            final ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            if (layoutParams instanceof RecyclerView.LayoutParams) {
                ((RecyclerView.LayoutParams) layoutParams).mInsetsDirty = true;
            }
            TraceCompat.endSection();
        }
    }

    /**
     * 返回位置上项目的视图类型
     */
    public int getItemViewType(int position) {
        return 0;
    }

    public void setHasStableIds(boolean hasStableIds) {
        if (hasObservers()) {
            throw new IllegalStateException("Cannot change whether this adapter has "
                    + "stable IDs while the adapter has registered observers.");
        }
        mHasStableIds = hasStableIds;
    }

    /**
     * 返回项目在位置的稳定ID。只有hasStableIds()==false时生效
     */
    public long getItemId(int position) {
        return -1;
    }

    public abstract int getItemCount();

    public final boolean hasStableIds() {
        return mHasStableIds;
    }


    public void onViewRecycled(@NonNull VH holder) {
    }

    /**
     * Called by the RecyclerView if a ViewHolder created by this Adapter cannot be recycled
     * due to its transient state. Upon receiving this callback, Adapter can clear the
     * animation(s) that effect the View's transient state and return <code>true</code> so that
     * the View can be recycled. Keep in mind that the View in question is already removed from
     * the RecyclerView.
     * <p>
     * In some cases, it is acceptable to recycle a View although it has transient state. Most
     * of the time, this is a case where the transient state will be cleared in
     * {@link #onBindViewHolder(ViewHolder, int)} call when View is rebound to a new position.
     * For this reason, RecyclerView leaves the decision to the Adapter and uses the return
     * value of this method to decide whether the View should be recycled or not.
     * <p>
     * Note that when all animations are created by {@link RecyclerView.ItemAnimator}, you
     * should never receive this callback because RecyclerView keeps those Views as children
     * until their animations are complete. This callback is useful when children of the item
     * views create animations which may not be easy to implement using an {@link RecyclerView.ItemAnimator}.
     * <p>
     * You should <em>never</em> fix this issue by calling
     * <code>holder.itemView.setHasTransientState(false);</code> unless you've previously called
     * <code>holder.itemView.setHasTransientState(true);</code>. Each
     * <code>View.setHasTransientState(true)</code> call must be matched by a
     * <code>View.setHasTransientState(false)</code> call, otherwise, the state of the View
     * may become inconsistent. You should always prefer to end or cancel animations that are
     * triggering the transient state instead of handling it manually.
     *
     * @param holder The ViewHolder containing the View that could not be recycled due to its
     *               transient state.
     * @return True if the View should be recycled, false otherwise. Note that if this method
     * returns <code>true</code>, RecyclerView <em>will ignore</em> the transient state of
     * the View and recycle it regardless. If this method returns <code>false</code>,
     * RecyclerView will check the View's transient state again before giving a final decision.
     * Default implementation returns false.
     */
    public boolean onFailedToRecycleView(@NonNull VH holder) {
        return false;
    }

    /**
     * 当此适配器创建的视图附加到窗口时调用。
     * 这可以作为一个合理的信号，表明视图即将被用户看到
     */
    public void onViewAttachedToWindow(@NonNull VH holder) {
    }

    /**
     * 当此适配器创建的视图从其窗口分离时调用。 与窗户分离并不一定是一种永久的状态;
     * Adapter视图的消费者可以选择在视图不可见时将其缓存到屏幕外，并适当地附加和分离它们。
     */
    public void onViewDetachedFromWindow(@NonNull VH holder) {
    }

    /**
     * Returns true if one or more observers are attached to this adapter.
     *
     * @return true if this adapter has observers
     */
    public final boolean hasObservers() {
        return mObservable.hasObservers();
    }

    /**
     * Register a new observer to listen for data changes.
     *
     * <p>The adapter may publish a variety of events describing specific changes.
     * Not all adapters may support all change types and some may fall back to a generic
     * {@link RecyclerView.AdapterDataObserver#onChanged()
     * "something changed"} event if more specific data is not available.</p>
     *
     * <p>Components registering observers with an adapter are responsible for
     * {@link #unregisterAdapterDataObserver(RecyclerView.AdapterDataObserver)
     * unregistering} those observers when finished.</p>
     *
     * @param observer Observer to register
     * @see #unregisterAdapterDataObserver(RecyclerView.AdapterDataObserver)
     */
    public void registerAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
        mObservable.registerObserver(observer);
    }

    /**
     * Unregister an observer currently listening for data changes.
     *
     * <p>The unregistered observer will no longer receive events about changes
     * to the adapter.</p>
     *
     * @param observer Observer to unregister
     * @see #registerAdapterDataObserver(RecyclerView.AdapterDataObserver)
     */
    public void unregisterAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
        mObservable.unregisterObserver(observer);
    }


    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
    }


    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
    }

    /**
     * Notify any registered observers that the data set has changed.
     *
     * <p>There are two different classes of data change events, item changes and structural
     * changes. Item changes are when a single item has its data updated but no positional
     * changes have occurred. Structural changes are when items are inserted, removed or moved
     * within the data set.</p>
     *
     * <p>This event does not specify what about the data set has changed, forcing
     * any observers to assume that all existing items and structure may no longer be valid.
     * LayoutManagers will be forced to fully rebind and relayout all visible views.</p>
     *
     * <p><code>RecyclerView</code> will attempt to synthesize visible structural change events
     * for adapters that report that they have {@link #hasStableIds() stable IDs} when
     * this method is used. This can help for the purposes of animation and visual
     * object persistence but individual item views will still need to be rebound
     * and relaid out.</p>
     *
     * <p>If you are writing an adapter it will always be more efficient to use the more
     * specific change events if you can. Rely on <code>notifyDataSetChanged()</code>
     * as a last resort.</p>
     *
     * @see #notifyItemChanged(int)
     * @see #notifyItemInserted(int)
     * @see #notifyItemRemoved(int)
     * @see #notifyItemRangeChanged(int, int)
     * @see #notifyItemRangeInserted(int, int)
     * @see #notifyItemRangeRemoved(int, int)
     */
    public final void notifyDataSetChanged() {
        mObservable.notifyChanged();
    }

    /**
     * Notify any registered observers that the item at <code>position</code> has changed.
     * Equivalent to calling <code>notifyItemChanged(position, null);</code>.
     *
     * <p>This is an item change event, not a structural change event. It indicates that any
     * reflection of the data at <code>position</code> is out of date and should be updated.
     * The item at <code>position</code> retains the same identity.</p>
     *
     * @param position Position of the item that has changed
     * @see #notifyItemRangeChanged(int, int)
     */
    public final void notifyItemChanged(int position) {
        mObservable.notifyItemRangeChanged(position, 1);
    }

    /**
     * Notify any registered observers that the item at <code>position</code> has changed with
     * an optional payload object.
     *
     * <p>This is an item change event, not a structural change event. It indicates that any
     * reflection of the data at <code>position</code> is out of date and should be updated.
     * The item at <code>position</code> retains the same identity.
     * </p>
     *
     * <p>
     * Client can optionally pass a payload for partial change. These payloads will be merged
     * and may be passed to adapter's {@link #onBindViewHolder(ViewHolder, int, List)} if the
     * item is already represented by a ViewHolder and it will be rebound to the same
     * ViewHolder. A notifyItemRangeChanged() with null payload will clear all existing
     * payloads on that item and prevent future payload until
     * {@link #onBindViewHolder(ViewHolder, int, List)} is called. Adapter should not assume
     * that the payload will always be passed to onBindViewHolder(), e.g. when the view is not
     * attached, the payload will be simply dropped.
     *
     * @param position Position of the item that has changed
     * @param payload  Optional parameter, use null to identify a "full" update
     * @see #notifyItemRangeChanged(int, int)
     */
    public final void notifyItemChanged(int position, @Nullable Object payload) {
        mObservable.notifyItemRangeChanged(position, 1, payload);
    }

    /**
     * Notify any registered observers that the <code>itemCount</code> items starting at
     * position <code>positionStart</code> have changed.
     * Equivalent to calling <code>notifyItemRangeChanged(position, itemCount, null);</code>.
     *
     * <p>This is an item change event, not a structural change event. It indicates that
     * any reflection of the data in the given position range is out of date and should
     * be updated. The items in the given range retain the same identity.</p>
     *
     * @param positionStart Position of the first item that has changed
     * @param itemCount     Number of items that have changed
     * @see #notifyItemChanged(int)
     */
    public final void notifyItemRangeChanged(int positionStart, int itemCount) {
        mObservable.notifyItemRangeChanged(positionStart, itemCount);
    }

    /**
     * Notify any registered observers that the <code>itemCount</code> items starting at
     * position <code>positionStart</code> have changed. An optional payload can be
     * passed to each changed item.
     *
     * <p>This is an item change event, not a structural change event. It indicates that any
     * reflection of the data in the given position range is out of date and should be updated.
     * The items in the given range retain the same identity.
     * </p>
     *
     * <p>
     * Client can optionally pass a payload for partial change. These payloads will be merged
     * and may be passed to adapter's {@link #onBindViewHolder(ViewHolder, int, List)} if the
     * item is already represented by a ViewHolder and it will be rebound to the same
     * ViewHolder. A notifyItemRangeChanged() with null payload will clear all existing
     * payloads on that item and prevent future payload until
     * {@link #onBindViewHolder(ViewHolder, int, List)} is called. Adapter should not assume
     * that the payload will always be passed to onBindViewHolder(), e.g. when the view is not
     * attached, the payload will be simply dropped.
     *
     * @param positionStart Position of the first item that has changed
     * @param itemCount     Number of items that have changed
     * @param payload       Optional parameter, use null to identify a "full" update
     * @see #notifyItemChanged(int)
     */
    public final void notifyItemRangeChanged(int positionStart, int itemCount,
                                             @Nullable Object payload) {
        mObservable.notifyItemRangeChanged(positionStart, itemCount, payload);
    }

    /**
     * Notify any registered observers that the item reflected at <code>position</code>
     * has been newly inserted. The item previously at <code>position</code> is now at
     * position <code>position + 1</code>.
     *
     * <p>This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their
     * positions may be altered.</p>
     *
     * @param position Position of the newly inserted item in the data set
     * @see #notifyItemRangeInserted(int, int)
     */
    public final void notifyItemInserted(int position) {
        mObservable.notifyItemRangeInserted(position, 1);
    }

    /**
     * Notify any registered observers that the item reflected at <code>fromPosition</code>
     * has been moved to <code>toPosition</code>.
     *
     * <p>This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their
     * positions may be altered.</p>
     *
     * @param fromPosition Previous position of the item.
     * @param toPosition   New position of the item.
     */
    public final void notifyItemMoved(int fromPosition, int toPosition) {
        mObservable.notifyItemMoved(fromPosition, toPosition);
    }

    /**
     * Notify any registered observers that the currently reflected <code>itemCount</code>
     * items starting at <code>positionStart</code> have been newly inserted. The items
     * previously located at <code>positionStart</code> and beyond can now be found starting
     * at position <code>positionStart + itemCount</code>.
     *
     * <p>This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their positions
     * may be altered.</p>
     *
     * @param positionStart Position of the first item that was inserted
     * @param itemCount     Number of items inserted
     * @see #notifyItemInserted(int)
     */
    public final void notifyItemRangeInserted(int positionStart, int itemCount) {
        mObservable.notifyItemRangeInserted(positionStart, itemCount);
    }

    /**
     * Notify any registered observers that the item previously located at <code>position</code>
     * has been removed from the data set. The items previously located at and after
     * <code>position</code> may now be found at <code>oldPosition - 1</code>.
     *
     * <p>This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their positions
     * may be altered.</p>
     *
     * @param position Position of the item that has now been removed
     * @see #notifyItemRangeRemoved(int, int)
     */
    public final void notifyItemRemoved(int position) {
        mObservable.notifyItemRangeRemoved(position, 1);
    }

    /**
     * Notify any registered observers that the <code>itemCount</code> items previously
     * located at <code>positionStart</code> have been removed from the data set. The items
     * previously located at and after <code>positionStart + itemCount</code> may now be found
     * at <code>oldPosition - itemCount</code>.
     *
     * <p>This is a structural change event. Representations of other existing items in the data
     * set are still considered up to date and will not be rebound, though their positions
     * may be altered.</p>
     *
     * @param positionStart Previous position of the first item that was removed
     * @param itemCount     Number of items removed from the data set
     */
    public final void notifyItemRangeRemoved(int positionStart, int itemCount) {
        mObservable.notifyItemRangeRemoved(positionStart, itemCount);
    }

    /**
     * Sets the state restoration strategy for the Adapter.
     * <p>
     * By default, it is set to {@link StateRestorationPolicy#ALLOW} which means RecyclerView
     * expects any set Adapter to be immediately capable of restoring the RecyclerView's saved
     * scroll position.
     * <p>
     * This behaviour might be undesired if the Adapter's data is loaded asynchronously, and
     * thus unavailable during initial layout (e.g. after Activity rotation). To avoid losing
     * scroll position, you can change this to be either
     * {@link StateRestorationPolicy#PREVENT_WHEN_EMPTY} or
     * {@link StateRestorationPolicy#PREVENT}.
     * Note that the former means your RecyclerView will restore state as soon as Adapter has
     * 1 or more items while the latter requires you to call
     * {@link #setStateRestorationPolicy(StateRestorationPolicy)} with either
     * {@link StateRestorationPolicy#ALLOW} or
     * {@link StateRestorationPolicy#PREVENT_WHEN_EMPTY} again when the Adapter is
     * ready to restore its state.
     * <p>
     * RecyclerView will still layout even when State restoration is disabled. The behavior of
     * how State is restored is up to the {@link RecyclerView.LayoutManager}. All default LayoutManagers
     * will override current state with restored state when state restoration happens (unless
     * an explicit call to {@link RecyclerView.LayoutManager#scrollToPosition(int)} is made).
     * <p>
     * Calling this method after state is restored will not have any effect other than changing
     * the return value of {@link #getStateRestorationPolicy()}.
     *
     * @param strategy The saved state restoration strategy for this Adapter.
     * @see #getStateRestorationPolicy()
     */
    public void setStateRestorationPolicy(@NonNull StateRestorationPolicy strategy) {
        mStateRestorationPolicy = strategy;
        mObservable.notifyStateRestorationPolicyChanged();
    }

    /**
     * Returns when this Adapter wants to restore the state.
     *
     * @return The current {@link StateRestorationPolicy} for this Adapter. Defaults to
     * {@link StateRestorationPolicy#ALLOW}.
     * @see #setStateRestorationPolicy(StateRestorationPolicy)
     */
    @NonNull
    public final StateRestorationPolicy getStateRestorationPolicy() {
        return mStateRestorationPolicy;
    }

    /**
     * Called by the RecyclerView to decide whether the SavedState should be given to the
     * LayoutManager or not.
     *
     * @return {@code true} if the Adapter is ready to restore its state, {@code false}
     * otherwise.
     */
    boolean canRestoreState() {
        switch (mStateRestorationPolicy) {
            case PREVENT:
                return false;
            case PREVENT_WHEN_EMPTY:
                return getItemCount() > 0;
            default:
                return true;
        }
    }

    /**
     * Defines how this Adapter wants to restore its state after a view reconstruction (e.g.
     * configuration change).
     */
    public enum StateRestorationPolicy {
        /**
         * Adapter is ready to restore State immediately, RecyclerView will provide the state
         * to the LayoutManager in the next layout pass.
         */
        ALLOW,
        /**
         * Adapter is ready to restore State when it has more than 0 items. RecyclerView will
         * provide the state to the LayoutManager as soon as the Adapter has 1 or more items.
         */
        PREVENT_WHEN_EMPTY,
        /**
         * RecyclerView will not restore the state for the Adapter until a call to
         * {@link #setStateRestorationPolicy(StateRestorationPolicy)} is made with either
         * {@link #ALLOW} or {@link #PREVENT_WHEN_EMPTY}.
         */
        PREVENT
    }
}
