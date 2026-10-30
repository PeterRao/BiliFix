package com.xjw.bilifix.in.feature.stash;

/** Two size levels; never stretch the video or make tall content exceed the available height. */
public final class MiniPlayerSizePolicy {
    public static final int MINIMUM = 0, MAXIMUM = 3;

    public static int nextMode(int current) {
        return current == MAXIMUM ? MINIMUM : MAXIMUM;
    }

    public static int maximumWidth(int availableWidth, int availableHeight, float aspectRatio) {
        if (availableWidth <= 0) return 1;
        if (availableHeight <= 0 || !Float.isFinite(aspectRatio) || aspectRatio <= 0) {
            return availableWidth;
        }
        return Math.max(1, (int) Math.min(availableWidth, Math.floor(availableHeight * (double) aspectRatio)));
    }
}
