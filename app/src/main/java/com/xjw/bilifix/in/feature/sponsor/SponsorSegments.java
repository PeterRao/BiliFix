/* Adapted from BiliSponsorSkip SponsorBlockClient.kt and SegmentSkipGuard.kt (GPL-3.0).
 * Java port adds bounded parsing, category filtering, overlapping-range handling and duration checks.
 * See THIRD_PARTY_NOTICES.md. */
package com.xjw.bilifix.in.feature.sponsor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class SponsorSegments {
    static final class Segment {
        final int start, end;
        final SponsorCategory category;
        Segment(int start, int end, SponsorCategory category) {
            this.start = start; this.end = end; this.category = category;
        }
    }
    static List<Segment> parse(String json, String bvid, long cid) throws Exception {
        JSONArray videos = new JSONArray(json);
        List<Segment> result = new ArrayList<>();
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.optJSONObject(i);
            if (video == null || !bvid.equals(video.optString("videoID"))) continue;
            JSONArray segments = video.optJSONArray("segments");
            if (segments == null) continue;
            for (int j = 0; j < segments.length() && result.size() < 1000; j++) {
                JSONObject item = segments.optJSONObject(j);
                if (item == null || !Long.toString(cid).equals(item.optString("cid"))
                        || !"skip".equals(item.optString("actionType", "skip"))) continue;
                SponsorCategory category = SponsorCategory.fromId(item.optString("category"));
                JSONArray range = item.optJSONArray("segment");
                if (category == null || range == null || range.length() < 2) continue;
                double start = range.optDouble(0, -1) * 1000, end = range.optDouble(1, -1) * 1000;
                if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0
                        || end > Integer.MAX_VALUE || end - start < 500) continue;
                result.add(new Segment((int) start, (int) end, category));
            }
        }
        result.sort(Comparator.comparingInt(s -> s.start));
        return Collections.unmodifiableList(result);
    }
    static List<Segment> select(List<Segment> all, int mask, int duration) {
        List<Segment> selected = new ArrayList<>();
        for (Segment s : all) {
            // Do not act on a different edit of the same video or corrupt server data.
            if ((mask & s.category.bit()) != 0 && s.end <= duration && s.start < duration) selected.add(s);
        }
        return Collections.unmodifiableList(selected);
    }
    /** A refused/clamped seek must not repeat forever while still inside that range. */
    static final class Guard {
        private int attemptedStart = -1, attemptedEnd = -1;
        void reset() { attemptedStart = attemptedEnd = -1; }
        int target(List<Segment> segments, int position) {
            // Decoders can briefly report frames just before the target after seeking.
            if (position < attemptedStart - 250 || (long) position >= (long) attemptedEnd + 1000) reset();
            int start = Integer.MAX_VALUE, end = -1;
            for (Segment s : segments) {
                if (s.start <= position && position < s.end) { start = Math.min(start, s.start); end = Math.max(end, s.end); }
            }
            if (end < 0) return -1;
            // Extend across overlapping or touching enabled segments in start order.
            for (Segment s : segments) if (s.start <= end && s.end > end && s.end > position) end = s.end;
            if (attemptedStart >= 0 && position >= attemptedStart - 250 && (long) position < (long) attemptedEnd + 1000) return -1;
            attemptedStart = start; attemptedEnd = end;
            return end;
        }
    }
    private SponsorSegments() { }
}
