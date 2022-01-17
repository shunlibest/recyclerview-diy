package com.shuhnli.recyclerview_diy.childUtil;

import androidx.annotation.NonNull;

/**
 * 底层是Bitset实现,可以根据索引功能,读取或存储值
 */
public class Bucket {

    //使用long类型存储, 一个Bucket可以存储64个值
    private static final int BITS_PER_WORD = Long.SIZE;

    //10000000 00000000 00000000 00000000  00000000 00000000 00000000 00000000
    private static final long LAST_BIT = 1L << (Long.SIZE - 1);

    long mData = 0;

    Bucket mNext;

    //把第index位的值, 设置为TRUE
    public void set(int index) {
        if (index >= BITS_PER_WORD) {
            //如果index超出64,则创建一个新的bucket,存储(index - 64)
            ensureNext();
            mNext.set(index - BITS_PER_WORD);
        } else {
            mData |= 1L << index;
        }
    }

    //把第index位的值, 设置为FALSE
    public void clear(int index) {
        if (index >= BITS_PER_WORD) {
            if (mNext != null) {
                mNext.clear(index - BITS_PER_WORD);
            }
        } else {
            mData &= ~(1L << index);
        }
    }


    public boolean get(int index) {
        if (index >= BITS_PER_WORD) {
            ensureNext();
            return mNext.get(index - BITS_PER_WORD);
        } else {
            return (mData & (1L << index)) != 0;
        }
    }

    //把整个list都清空
    void reset() {
        mData = 0;
        if (mNext != null) {
            mNext.reset();
        }
    }

    //在列表中, 插入某一数据
    public void insert(int index, boolean value) {
        if (index >= BITS_PER_WORD) {
            ensureNext();
            mNext.insert(index - BITS_PER_WORD, value);
        } else {
            final boolean lastBit = (mData & LAST_BIT) != 0;
            long mask = (1L << index) - 1;
            final long before = mData & mask;
            final long after = (mData & ~mask) << 1;
            mData = before | after;
            if (value) {
                set(index);
            } else {
                clear(index);
            }
            if (lastBit || mNext != null) {
                ensureNext();
                mNext.insert(0, lastBit);
            }
        }
    }

    //移除列表第index项, 返回值代表该项的值
    public boolean remove(int index) {
        if (index >= BITS_PER_WORD) {
            ensureNext();
            return mNext.remove(index - BITS_PER_WORD);
        } else {
            long mask = (1L << index);
            final boolean value = (mData & mask) != 0;
            mData &= ~mask;
            mask = mask - 1;
            final long before = mData & mask;
            // cannot use >> because it adds one.
            final long after = Long.rotateRight(mData & ~mask, 1);
            mData = before | after;
            if (mNext != null) {
                if (mNext.get(0)) {
                    set(BITS_PER_WORD - 1);
                }
                mNext.remove(0);
            }
            return value;
        }
    }


    //统计[0--index]区间内, 一共有多少个1
    public int countOnesBefore(int index) {
        if (mNext == null) {
            if (index >= BITS_PER_WORD) {
                return Long.bitCount(mData);
            }
            return Long.bitCount(mData & ((1L << index) - 1));
        }
        if (index < BITS_PER_WORD) {
            return Long.bitCount(mData & ((1L << index) - 1));
        } else {
            return mNext.countOnesBefore(index - BITS_PER_WORD) + Long.bitCount(mData);
        }
    }

    private void ensureNext() {
        if (mNext == null) {
            mNext = new Bucket();
        }
    }


    @NonNull
    @Override
    public String toString() {
        return mNext == null ? Long.toBinaryString(mData)
                : mNext.toString() + "xx" + Long.toBinaryString(mData);
    }
}
