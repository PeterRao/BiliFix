package com.xjw.bilifix.in.feature.sponsor;

/** Category IDs and colours follow BiliSponsorSkip / BilibiliSponsorBlock. */
public enum SponsorCategory {
    SPONSOR("sponsor", "赞助广告", 0xFF00D400),
    SELFPROMO("selfpromo", "无偿／自我推广", 0xFFFFFF00),
    INTERACTION("interaction", "三连互动", 0xFFCC00FF),
    INTRO("intro", "片头", 0xFF00FFFF),
    OUTRO("outro", "片尾", 0xFF4444EE),
    PREVIEW("preview", "预告／回顾", 0xFF008FD6),
    MUSIC_OFFTOPIC("music_offtopic", "非音乐片段", 0xFFFF9900),
    FILLER("filler", "离题内容", 0xFF7300FF),
    PADDING("padding", "填充内容", 0xFF888888);

    public final String id, title;
    public final int color;
    SponsorCategory(String id, String title, int color) {
        this.id = id; this.title = title; this.color = color;
    }
    public String key() { return "bilifix_sponsor_category_" + id; }
    public int bit() { return 1 << ordinal(); }
    public static SponsorCategory fromId(String id) {
        for (SponsorCategory category : values()) if (category.id.equals(id)) return category;
        return null;
    }
}
