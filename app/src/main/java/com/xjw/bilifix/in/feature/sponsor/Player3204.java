package com.xjw.bilifix.in.feature.sponsor;

import android.view.View;
import com.xjw.bilifix.in.core.HookApi;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** All obfuscated symbols are confined to this adapter for international 3.20.4 / 8230800. */
final class Player3204 {
    final Method position, state, stateChanged, seek, duration;
    private final Field container, item;
    private final Method directorV2, directorV3, currentVideo, danmaku, aid, cid, panel, panelView, itemAid, itemId, videoId;

    Player3204(HookApi api, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> player = api.load(loader, "tt3.p0");
        Class<?> host = api.load(loader, "rt3.j");
        Class<?> video = api.load(loader, "tv.danmaku.biliplayerv2.service.Video$f");
        Class<?> dm = api.load(loader, "tv.danmaku.biliplayerv2.service.Video$b");
        position = api.declaredMethod(player, "getCurrentPosition");
        duration = api.declaredMethod(player, "getDuration");
        state = api.declaredMethod(player, "getState");
        stateChanged = api.declaredMethod(player, "ja", int.class);
        seek = api.declaredMethod(player, "seekTo", int.class);
        container = api.declaredField(player, "a");
        item = api.declaredField(player, "I");
        itemAid = api.declaredMethod(api.load(loader, "rw3.d"), "d");
        itemId = api.declaredMethod(api.load(loader, "rw3.d"), "n");
        videoId = api.publicMethod(video, "j0");
        directorV2 = api.declaredMethod(host, "g");
        directorV3 = api.declaredMethod(host, "f");
        currentVideo = api.publicMethod(api.load(loader, "tv.danmaku.biliplayerv2.service.z"), "P");
        danmaku = api.publicMethod(video, "D");
        aid = api.publicMethod(dm, "c"); cid = api.publicMethod(dm, "d");
        panel = api.declaredMethod(host, "A");
        panelView = api.publicMethod(api.load(loader, "ju3.b"), "getView");
    }
    Object director(Object player) throws ReflectiveOperationException {
        Object host = container.get(player);
        if (host == null) return null;
        try { return directorV2.invoke(host); }
        catch (java.lang.reflect.InvocationTargetException ignored) { return directorV3.invoke(host); }
    }
    VideoKey key(Object player, Object director) throws ReflectiveOperationException {
        if (director == null) return null;
        Object video = currentVideo.invoke(director);
        Object params = video == null ? null : danmaku.invoke(video);
        Object media = item.get(player);
        if (params == null || media == null) return null;
        Object playingId = itemId.invoke(media), selectedId = videoId.invoke(video);
        if (!(playingId instanceof String) || ((String) playingId).isEmpty() || !playingId.equals(selectedId)) return null;
        long a = ((Number) aid.invoke(params)).longValue(), c = ((Number) cid.invoke(params)).longValue();
        if (a <= 0 || a >= (1L << 51) || c <= 0 || ((Number) itemAid.invoke(media)).longValue() != a) return null;
        return new VideoKey(a, c);
    }
    View root(Object player) throws ReflectiveOperationException {
        Object host = container.get(player);
        Object p = host == null ? null : panel.invoke(host);
        return p == null ? null : (View) panelView.invoke(p);
    }
}
