package com.xjw.bilifix.in.feature.stash;

import org.junit.Test;

public final class StashGeometryTest {
    private static void equal(int expected, int actual) {
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    @Test
    public void hidesVideoWithShadowAndRestoresWithinBounds() {
        // Insets, almost-full-width video and wrong-direction/vertical drags must be safe.
        equal(-1, StashGeometry.side(-180, 1140, 0, 1260, -207, 78));
        equal(1, StashGeometry.side(300, 1140, 0, 1260, 273, 78));
        equal(0, StashGeometry.side(-180, 1140, 0, 1260, 0, 78));
        equal(0, StashGeometry.side(-30, 1140, 0, 1260, -100, 78));
        equal(0, StashGeometry.side(-180, 1140, 0, 1260, 100, 78));
        equal(0, StashGeometry.side(-180, 0, 0, 1260, -207, 78));
        // The original video and shadow must be fully outside; handle has its own small window.
        for (int width : new int[] {300, 720, 1140}) {
            equal(-26, StashGeometry.hiddenX(-1, width, 0, 1260, 26) + width);
            equal(1286, StashGeometry.hiddenX(1, width, 0, 1260, 26));
            equal(0, StashGeometry.restoredX(-1, width, 0, 1260));
            equal(1260 - width, StashGeometry.restoredX(1, width, 0, 1260));
        }
        // A maximum-size video keeps equal native gaps after restoring from either edge.
        equal(27, StashGeometry.restoredX(-1, 1206, 27, 1233));
        equal(27, StashGeometry.restoredX(1, 1206, 27, 1233));
        equal(20 - 300, StashGeometry.hiddenX(-1, 300, 20, 1240, -10));
        equal(0, StashGeometry.restoredX(1, 1400, 0, 1260));
        equal(0, StashGeometry.clampY(-100, 641, 0, 2608));
        equal(1967, StashGeometry.clampY(2800, 641, 0, 2608));
        equal(20, StashGeometry.clampY(100, 900, 20, 500));
        System.out.println("Stash geometry: PASS");
    }
}
