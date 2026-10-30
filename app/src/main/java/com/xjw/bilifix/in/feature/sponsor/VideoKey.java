/* Adapted from BiliSponsorSkip BvId.kt (GPL-3.0); see THIRD_PARTY_NOTICES.md. */
package com.xjw.bilifix.in.feature.sponsor;

import java.util.Objects;

final class VideoKey {
    final long aid, cid;
    VideoKey(long aid, long cid) { this.aid = aid; this.cid = cid; }
    String bvid() {
        if (aid <= 0 || aid >= (1L << 51)) throw new IllegalArgumentException("Invalid aid");
        String table = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf";
        char[] result = "BV1000000000".toCharArray();
        long value = ((1L << 51) | aid) ^ 23_442_827_791_579L;
        for (int index = 11; value > 0; index--, value /= 58) result[index] = table.charAt((int) (value % 58));
        char temp = result[3]; result[3] = result[9]; result[9] = temp;
        temp = result[4]; result[4] = result[7]; result[7] = temp;
        return new String(result);
    }
    @Override public boolean equals(Object value) {
        return value instanceof VideoKey && aid == ((VideoKey) value).aid && cid == ((VideoKey) value).cid;
    }
    @Override public int hashCode() { return Objects.hash(aid, cid); }
}
