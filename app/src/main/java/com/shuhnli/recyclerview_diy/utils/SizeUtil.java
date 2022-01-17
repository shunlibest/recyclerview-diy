package com.shuhnli.recyclerview_diy.utils;

import android.view.View;

public class SizeUtil {

    /**
     * 从给定的规格和参数中选择最接近所需尺寸且符合规格的尺寸
     *
     * @param spec    测量值
     * @param desired 首选的测量
     * @param min     测量值
     * @return 符合给定规格的尺寸
     */
    public static int chooseSize(int spec, int desired, int min) {
        final int mode = View.MeasureSpec.getMode(spec);
        final int size = View.MeasureSpec.getSize(spec);
        switch (mode) {
            case View.MeasureSpec.EXACTLY:
                return size;
            case View.MeasureSpec.AT_MOST:
                return Math.min(size, Math.max(desired, min));
            case View.MeasureSpec.UNSPECIFIED:
            default:
                return Math.max(desired, min);
        }
    }

}
