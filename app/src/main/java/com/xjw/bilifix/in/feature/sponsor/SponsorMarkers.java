/* Mini progress recognition adapted from BiliSponsorSkip PlayerUiInjector.kt (GPL-3.0).
 * See THIRD_PARTY_NOTICES.md. */
package com.xjw.bilifix.in.feature.sponsor;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Player-owned, non-interactive overlays. Layout events drive discovery, never a view polling loop. */
final class SponsorMarkers {
    private WeakReference<View> root = new WeakReference<>(null);
    private List<SponsorSegments.Segment> segments = Collections.emptyList();
    private int duration;
    private final Map<View, Marker> attached = new WeakHashMap<>();
    private final ViewTreeObserver.OnGlobalLayoutListener layout = this::refresh;

    void update(View playerRoot, List<SponsorSegments.Segment> value, int durationMs) {
        if (root.get() != playerRoot) {
            clear();
            root = new WeakReference<>(playerRoot);
            if (playerRoot != null) playerRoot.getViewTreeObserver().addOnGlobalLayoutListener(layout);
        }
        segments = value;
        duration = durationMs;
        refresh();
    }
    void clear() {
        View view = root.get();
        if (view != null && view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnGlobalLayoutListener(layout);
        for (Map.Entry<View, Marker> entry : attached.entrySet()) entry.getKey().getOverlay().remove(entry.getValue());
        attached.clear(); root.clear(); segments = Collections.emptyList();
    }
    private void refresh() {
        View view = root.get();
        List<View> visible = new ArrayList<>();
        if (view != null && duration > 0 && !segments.isEmpty()) collect(view, visible);
        for (View old : new ArrayList<>(attached.keySet())) {
            if (!visible.contains(old)) old.getOverlay().remove(attached.remove(old));
        }
        for (View track : visible) {
            Marker marker = attached.get(track);
            if (marker == null) {
                marker = new Marker(track);
                attached.put(track, marker); track.getOverlay().add(marker);
            }
            marker.segments = segments; marker.duration = duration;
            marker.setBounds(0, 0, track.getWidth(), track.getHeight()); marker.invalidateSelf();
        }
    }
    private void collect(View view, List<View> out) {
        if (!view.isShown()) return;
        String name = view.getClass().getName();
        if (view.getWidth() > 0 && view.getHeight() > 0
                && (name.equals("com.bilibili.playerbizcommon.widget.control.HighEnergySeekWidget")
                || name.equals("com.bilibili.playerbizcommonv2.widget.seek.v3.PlayerSeekWidget3")
                || name.equals("com.bilibili.video.story.view.StorySeekBar")
                || isMiniProgress(view))) out.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), out);
        }
    }
    private static final Map<Class<?>, Boolean> MINI_TRACK_CLASSES = new WeakHashMap<>();
    private static boolean isMiniProgress(View view) {
        if (view.getClass().getSuperclass() != View.class || !(view.getParent() instanceof View)) return false;
        View parent = (View) view.getParent();
        boolean functionContainer = parent.getClass().getName().startsWith("tv.danmaku.biliplayerimpl.functionwidget.");
        for (Class<?> type : parent.getClass().getInterfaces()) {
            functionContainer |= type.getName().equals("tv.danmaku.biliplayerv2.widget.IFunctionContainer");
        }
        float density = view.getResources().getDisplayMetrics().density;
        if (!functionContainer || view.getWidth() < 120 * density || view.getHeight() > 8 * density) return false;
        Class<?> type = view.getClass();
        Boolean known = MINI_TRACK_CLASSES.get(type);
        if (known != null) return known;
        boolean paint = false, progress = false, draw = false;
        for (java.lang.reflect.Field field : type.getDeclaredFields()) {
            paint |= field.getType() == Paint.class; progress |= field.getType() == float.class;
        }
        try { type.getDeclaredMethod("onDraw", Canvas.class); draw = true; }
        catch (NoSuchMethodException ignored) { }
        boolean track = paint && progress && draw;
        MINI_TRACK_CLASSES.put(type, track);
        return track;
    }
    private static final class Marker extends Drawable {
        private final WeakReference<View> host;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<SponsorSegments.Segment> segments;
        private int duration;
        Marker(View host) { this.host = new WeakReference<>(host); }
        @Override public void draw(Canvas canvas) {
            View view = host.get();
            if (view == null || !view.isShown() || duration <= 0 || segments == null) return;
            float left = view.getPaddingLeft(), right = getBounds().width() - view.getPaddingRight();
            float density = view.getResources().getDisplayMetrics().density;
            float height = Math.min(3 * density, getBounds().height());
            float y = getBounds().exactCenterY();
            for (SponsorSegments.Segment s : segments) {
                float start = left + (right - left) * s.start / duration;
                float end = Math.min(right, Math.max(start + density, left + (right - left) * s.end / duration));
                paint.setColor(s.category.color); paint.setAlpha(235);
                canvas.drawRoundRect(start, y - height / 2, end, y + height / 2, height / 2, height / 2, paint);
            }
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @SuppressWarnings("deprecation")
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
