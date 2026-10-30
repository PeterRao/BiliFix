package com.xjw.bilifix.in.feature.stash;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MiniPlayerSizePolicyTest {
    @Test public void entersTwoLevelCycleFromEveryNativeSize() {
        for (int initial = 0; initial < 4; initial++) {
            int first = MiniPlayerSizePolicy.nextMode(initial);
            int second = MiniPlayerSizePolicy.nextMode(first);
            assertTrue(first == 0 || first == 3);
            assertEquals(3, first + second);
            assertEquals(first, MiniPlayerSizePolicy.nextMode(second));
        }
    }

    @Test public void fillsPortraitScreenForLandscapeVideoAndFitsTallContent() {
        assertEquals(1260, MiniPlayerSizePolicy.maximumWidth(1260, 2608, 16f / 9));
        assertEquals(1260, MiniPlayerSizePolicy.maximumWidth(1260, 2608, 9f / 16));
        for (int[] bounds : new int[][] {{1260, 2608}, {2800, 1120}, {600, 800}}) {
            for (float ratio : new float[] {.4f, 9f / 16, 1f, 16f / 9, 2.4f}) {
                int width = MiniPlayerSizePolicy.maximumWidth(bounds[0], bounds[1], ratio);
                assertTrue(width > 0 && width <= bounds[0]);
                assertTrue(width / (double) ratio <= bounds[1] + .01);
                assertTrue(width == bounds[0] || (width + 1) / (double) ratio > bounds[1]);
            }
        }
    }
}
