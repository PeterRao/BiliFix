package com.xjw.bilifix.in.feature.stash;

/** Coordinates are relative to the overlay's parent, not the physical display. */
public final class StashGeometry {
    public static final int NONE = 0, LEFT = -1, RIGHT = 1;

    public static int side(int x, int width, int left, int right, int dragX, int threshold) {
        if (width <= 0 || right <= left || Math.abs(dragX) < threshold) return NONE;
        if (dragX < 0 && x < left - threshold) return LEFT;
        if (dragX > 0 && x + width > right + threshold) return RIGHT;
        return NONE;
    }

    /** Move the original window and its shadow fully out of the display, without resizing it. */
    public static int hiddenX(int side, int width, int left, int right, int shadowMargin) {
        int margin = Math.max(0, shadowMargin);
        return side == LEFT ? left - width - margin : right + margin;
    }

    public static int restoredX(int side, int width, int left, int right) {
        return side == LEFT ? left : Math.max(left, right - width);
    }

    public static int clampY(int y, int height, int top, int bottom) {
        return Math.max(top, Math.min(y, Math.max(top, bottom - height)));
    }
}
