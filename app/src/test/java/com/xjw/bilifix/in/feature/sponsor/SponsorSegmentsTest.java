package com.xjw.bilifix.in.feature.sponsor;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class SponsorSegmentsTest {
    private static SponsorSegments.Segment s(int start, int end, SponsorCategory category) {
        return new SponsorSegments.Segment(start, end, category);
    }
    @Test public void onlyAcceptsMatchingVideoPartAndSkipAction() throws Exception {
        String json = """
            [{"videoID":"BVother","segments":[{"cid":"1","category":"sponsor","segment":[1,3]}]},
             {"videoID":"BVtest","segments":[
              {"cid":"2","category":"sponsor","segment":[1,3]},
              {"cid":"1","category":"sponsor","segment":[10,12],"actionType":"skip"},
              {"cid":1,"category":"intro","segment":[0,2]},
              {"cid":"1","category":"sponsor","segment":[3,4],"actionType":"mute"},
              {"cid":"1","category":"unknown","segment":[5,7]}]}]
            """;
        List<SponsorSegments.Segment> segments = SponsorSegments.parse(json,"BVtest",1);
        assertEquals(2,segments.size()); assertEquals(0,segments.get(0).start); assertEquals(12000,segments.get(1).end);
    }
    @Test public void rejectsNegativeReversedTinyAndOverflowingRanges() throws Exception {
        String json = """
            [{"videoID":"BVtest","segments":[
              {"cid":"1","category":"sponsor","segment":[-1,3]},
              {"cid":"1","category":"sponsor","segment":[5,2]},
              {"cid":"1","category":"sponsor","segment":[2,2.1]},
              {"cid":"1","category":"sponsor","segment":[2,1e30]}]}]
            """;
        assertTrue(SponsorSegments.parse(json,"BVtest",1).isEmpty());
    }
    @Test public void categoriesAndVideoDurationBothLimitSkipping() {
        List<SponsorSegments.Segment> all = List.of(s(0,1000,SponsorCategory.INTRO),s(2000,3000,SponsorCategory.SPONSOR),s(4000,6000,SponsorCategory.SPONSOR));
        List<SponsorSegments.Segment> chosen = SponsorSegments.select(all,SponsorCategory.SPONSOR.bit(),5000);
        assertEquals(1,chosen.size());assertEquals(2000,chosen.get(0).start);
        assertTrue(SponsorSegments.select(all,0,10000).isEmpty());
    }
    @Test public void seeksOncePerTraversalEvenWhenPlayerRefusesSeek() {
        SponsorSegments.Guard guard = new SponsorSegments.Guard();
        List<SponsorSegments.Segment> list = List.of(s(1000,3000,SponsorCategory.SPONSOR));
        assertEquals(-1,guard.target(list,999)); assertEquals(3000,guard.target(list,1000));
        assertEquals(-1,guard.target(list,1200)); assertEquals(-1,guard.target(list,2500));
        assertEquals(-1,guard.target(list,500)); assertEquals(3000,guard.target(list,1500));
    }
    @Test public void combinesOverlapsButDoesNotJumpAcrossUnmarkedContent() {
        SponsorSegments.Guard guard = new SponsorSegments.Guard();
        List<SponsorSegments.Segment> list = List.of(s(1000,3000,SponsorCategory.SPONSOR),s(2500,4000,SponsorCategory.INTRO),s(4000,5000,SponsorCategory.SPONSOR),s(6000,7000,SponsorCategory.SPONSOR));
        assertEquals(5000,guard.target(list,1500)); assertEquals(-1,guard.target(list,5500));
        assertEquals(7000,guard.target(list,6000));
    }
    @Test public void decoderJitterAtTargetDoesNotRepeatSeek() {
        SponsorSegments.Guard guard = new SponsorSegments.Guard();
        List<SponsorSegments.Segment> list = List.of(s(25825,72397,SponsorCategory.SPONSOR));
        assertEquals(72397,guard.target(list,25933));
        assertEquals(-1,guard.target(list,72398));
        assertEquals(-1,guard.target(list,72332));
        assertEquals(-1,guard.target(list,72500));
        assertEquals(-1,guard.target(list,72295));
        assertEquals(-1,guard.target(list,74000));
        assertEquals(72397,guard.target(list,30000));
    }
    @Test public void changingVideoClearsPreviousAttempt() {
        SponsorSegments.Guard guard = new SponsorSegments.Guard();
        List<SponsorSegments.Segment> list = List.of(s(1000,3000,SponsorCategory.SPONSOR));
        assertEquals(3000,guard.target(list,1500));guard.reset();assertEquals(3000,guard.target(list,1500));
    }
    @Test public void convertsKnownVideoIdsAndDistinguishesParts() {
        assertEquals("BV17x411w7KC",new VideoKey(170001,1).bvid());
        assertEquals("BV1Q541167Qg",new VideoKey(455017605,2).bvid());
        assertNotEquals(new VideoKey(170001,1),new VideoKey(170001,2));
        assertEquals(new VideoKey(170001,1),new VideoKey(170001,1));
    }
}
