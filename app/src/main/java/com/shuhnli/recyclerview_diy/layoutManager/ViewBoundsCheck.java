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

package com.shuhnli.recyclerview_diy.layoutManager;

import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 工具类, 判断有没有超出父View的边界
 */
public class ViewBoundsCheck {

    static final int GT = 1 << 0;   //GREATER THAN 大于
    static final int EQ = 1 << 1;   //EQUAL 等于
    static final int LT = 1 << 2;   //LESS THAN 小于

    //[A] Child view Start && Parent view Start
    static final int CVS_PVS_POS = 0;

    //子View的start > 父View的start。
    static final int FLAG_CVS_GT_PVS = GT << CVS_PVS_POS;

    //子View的star == 父view的start
    static final int FLAG_CVS_EQ_PVS = EQ << CVS_PVS_POS;

    //子View的start < 父View的start。
    static final int FLAG_CVS_LT_PVS = LT << CVS_PVS_POS;

    //[B] Child view Start && Parent view End
    static final int CVS_PVE_POS = 4;
    static final int FLAG_CVS_GT_PVE = GT << CVS_PVE_POS;
    static final int FLAG_CVS_EQ_PVE = EQ << CVS_PVE_POS;
    static final int FLAG_CVS_LT_PVE = LT << CVS_PVE_POS;

    //[C] Child view End && Parent view Start
    static final int CVE_PVS_POS = 8;
    static final int FLAG_CVE_GT_PVS = GT << CVE_PVS_POS;
    static final int FLAG_CVE_EQ_PVS = EQ << CVE_PVS_POS;
    static final int FLAG_CVE_LT_PVS = LT << CVE_PVS_POS;

    //[D] Child view End && Parent view End
    static final int CVE_PVE_POS = 12;
    static final int FLAG_CVE_GT_PVE = GT << CVE_PVE_POS;
    static final int FLAG_CVE_EQ_PVE = EQ << CVE_PVE_POS;
    static final int FLAG_CVE_LT_PVE = LT << CVE_PVE_POS;

    static final int MASK = GT | EQ | LT;

    final Callback mCallback;
    BoundFlags mBoundFlags;

    /**
     * The set of flags that can be passed for checking the view boundary conditions.
     * CVS in the flag name indicates the child view, and PV indicates the parent view.\
     * The following S, E indicate a view's start and end points, respectively.
     * GT and LT indicate a strictly greater and less than relationship.
     * Greater than or equal (or less than or equal) can be specified by setting both GT and EQ (or
     * LT and EQ) flags.
     * For instance, setting both {@link #FLAG_CVS_GT_PVS} and {@link #FLAG_CVS_EQ_PVS} indicate the
     * child view's start should be greater than or equal to its parent start.
     */
    @IntDef(flag = true, value = {
            FLAG_CVS_GT_PVS, FLAG_CVS_EQ_PVS, FLAG_CVS_LT_PVS,
            FLAG_CVS_GT_PVE, FLAG_CVS_EQ_PVE, FLAG_CVS_LT_PVE,
            FLAG_CVE_GT_PVS, FLAG_CVE_EQ_PVS, FLAG_CVE_LT_PVS,
            FLAG_CVE_GT_PVE, FLAG_CVE_EQ_PVE, FLAG_CVE_LT_PVE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewBounds {
    }

    public ViewBoundsCheck(Callback callback) {
        mCallback = callback;
        mBoundFlags = new BoundFlags();
    }

    static class BoundFlags {
        int mBoundFlags = 0;
        int mRvStart, mRvEnd, mChildStart, mChildEnd;

        void setBounds(int rvStart, int rvEnd, int childStart, int childEnd) {
            mRvStart = rvStart;
            mRvEnd = rvEnd;
            mChildStart = childStart;
            mChildEnd = childEnd;
        }

        void addFlags(@ViewBounds int flags) {
            mBoundFlags |= flags;
        }

        void resetFlags() {
            mBoundFlags = 0;
        }

        int compare(int x, int y) {
            if (x > y) {
                return GT;
            }
            if (x == y) {
                return EQ;
            }
            return LT;
        }

        boolean boundsMatch() {
            if ((mBoundFlags & (MASK << CVS_PVS_POS)) != 0) {
                if ((mBoundFlags & (compare(mChildStart, mRvStart) << CVS_PVS_POS)) == 0) {
                    return false;
                }
            }

            if ((mBoundFlags & (MASK << CVS_PVE_POS)) != 0) {
                if ((mBoundFlags & (compare(mChildStart, mRvEnd) << CVS_PVE_POS)) == 0) {
                    return false;
                }
            }

            if ((mBoundFlags & (MASK << CVE_PVS_POS)) != 0) {
                if ((mBoundFlags & (compare(mChildEnd, mRvStart) << CVE_PVS_POS)) == 0) {
                    return false;
                }
            }

            if ((mBoundFlags & (MASK << CVE_PVE_POS)) != 0) {
                if ((mBoundFlags & (compare(mChildEnd, mRvEnd) << CVE_PVE_POS)) == 0) {
                    return false;
                }
            }
            return true;
        }
    }

    ;

    /**
     * 根据提供的preferred bound flags，返回父view内的[fromIndex,toIndex]第一个子view
     * 如果上述View找不到, 则根据acceptableBoundFlags返回其父View内的最后一个View
     * 上面两个条件还达不到, 则则返回null。
     * <p>
     * preferred bound flags -指示首选匹配的标志。一旦基于该标志找到匹配，该视图立即返回。
     * acceptableBoundFlags
     */
    @Nullable
    public View findOneViewWithinBoundFlags(int fromIndex, int toIndex,
                                            @ViewBounds int preferredBoundFlags,
                                            @ViewBounds int acceptableBoundFlags) {
        final int start = mCallback.getParentStart();
        final int end = mCallback.getParentEnd();
        final int next = toIndex > fromIndex ? 1 : -1;
        View acceptableMatch = null;
        for (int i = fromIndex; i != toIndex; i += next) {
            final View child = mCallback.getChildAt(i);
            final int childStart = mCallback.getChildStart(child);
            final int childEnd = mCallback.getChildEnd(child);
            mBoundFlags.setBounds(start, end, childStart, childEnd);
            if (preferredBoundFlags != 0) {
                mBoundFlags.resetFlags();
                mBoundFlags.addFlags(preferredBoundFlags);
                if (mBoundFlags.boundsMatch()) {
                    // found a perfect match
                    return child;
                }
            }
            if (acceptableBoundFlags != 0) {
                mBoundFlags.resetFlags();
                mBoundFlags.addFlags(acceptableBoundFlags);
                if (mBoundFlags.boundsMatch()) {
                    acceptableMatch = child;
                }
            }
        }
        return acceptableMatch;
    }

    //返回View和父view, 是否满足 boundsFlags 的条件
    boolean isViewWithinBoundFlags(View child, @ViewBounds int boundsFlags) {
        mBoundFlags.setBounds(mCallback.getParentStart(), mCallback.getParentEnd(),
                mCallback.getChildStart(child), mCallback.getChildEnd(child));
        if (boundsFlags != 0) {
            mBoundFlags.resetFlags();
            mBoundFlags.addFlags(boundsFlags);
            return mBoundFlags.boundsMatch();
        }
        return false;
    }

    //需要用户实现, 用以判断子View和父View的边界信息
    public interface Callback {
        View getChildAt(int index);

        int getParentStart();

        int getParentEnd();

        int getChildStart(View view);

        int getChildEnd(View view);
    }
}
