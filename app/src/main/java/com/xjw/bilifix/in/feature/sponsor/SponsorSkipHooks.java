package com.xjw.bilifix.in.feature.sponsor;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Toast;
import com.xjw.bilifix.in.core.HookApi;
import com.xjw.bilifix.in.core.HostApplication;
import com.xjw.bilifix.in.feature.settings.SettingsManager;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One bounded read-only request per video, and a timer only for actively playing hosts. */
public final class SponsorSkipHooks {
    private final HookApi api;
    private final SettingsManager settings;
    private final ClassLoader loader;
    private final Handler main = new Handler(Looper.getMainLooper());
    // Main-thread owned. Session values hold only weak references to host objects.
    private final Map<Object, Session> sessions = new WeakHashMap<>();
    private final Map<VideoKey, Entry> cache = new LinkedHashMap<>(16, .75f, true);
    private final Set<VideoKey> pending = new HashSet<>();
    private final SponsorClient client = new SponsorClient();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), r -> { Thread t = new Thread(r, "BiliFix-Sponsor"); t.setDaemon(true); return t; });
    private Player3204 adapter;
    private int epoch;
    private boolean reading;
    private boolean runtimeFailureLogged;
    private static final class Entry {
        final List<SponsorSegments.Segment> segments;
        final long expires;
        Entry(List<SponsorSegments.Segment> segments, long ttl) {
            this.segments = segments; expires = SystemClock.uptimeMillis() + ttl;
        }
    }
    public SponsorSkipHooks(HookApi api, SettingsManager settings, ClassLoader loader) {
        this.api = api; this.settings = settings; this.loader = loader;
        worker.allowCoreThreadTimeOut(true);
    }
    public void install() throws ReflectiveOperationException {
        if (!api.hostVersion().isExact3204()) throw new IllegalStateException("SponsorSkip requires international 3.20.4");
        adapter = new Player3204(api, loader);
        api.addHook("sponsor player state", adapter.stateChanged, chain -> {
            Object result = chain.proceed();
            if (settings.isSponsorEnabled()) {
                Object player = chain.getThisObject();
                if (Looper.myLooper() == Looper.getMainLooper()) signal(player, true);
                else main.post(() -> signal(player, true));
            }
            return result;
        });
        api.addHook("sponsor playback clock", adapter.position, chain -> {
            Object result = chain.proceed();
            if (!reading && settings.isSponsorEnabled() && Looper.myLooper() == Looper.getMainLooper()) signal(chain.getThisObject(), false);
            return result;
        });
        api.info("sponsor adapter installed: 3.20.4, player-owned identity and progress markers");
    }
    public void onSettingsChanged() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::onSettingsChanged); return; }
        if (!settings.isSponsorEnabled()) {
            epoch++; pending.clear(); worker.getQueue().clear(); client.cancel();
            for (Session session : sessions.values()) session.stop(true);
            return;
        }
        for (Session session : new ArrayList<>(sessions.values())) {
            session.guard.reset(); session.filteredFor = null; session.refresh(true);
        }
    }
    private void signal(Object player, boolean force) {
        if (reading || !settings.isSponsorEnabled()) return;
        Session session = sessions.get(player);
        if (session == null) { session = new Session(player); sessions.put(player, session); }
        if (force || SystemClock.uptimeMillis() - session.lastTick >= 250) session.refresh(force);
    }
    private void load(VideoKey key) {
        Entry existing = cache.get(key);
        if (existing != null && existing.expires > SystemClock.uptimeMillis() || !pending.add(key)) return;
        int generation = epoch;
        try {
            worker.execute(() -> {
                Entry entry;
                try { entry = new Entry(client.fetch(key), 10 * 60_000); }
                catch (Exception error) {
                    api.warn("sponsor segment request failed: " + error.getClass().getSimpleName() + " " + error.getMessage());
                    entry = new Entry(Collections.emptyList(), 60_000);
                }
                Entry result = entry;
                main.post(() -> {
                    if (generation != epoch || !settings.isSponsorEnabled()) return;
                    pending.remove(key); cache.put(key, result);
                    while (cache.size() > 16) cache.remove(cache.keySet().iterator().next());
                    api.info("sponsor segments loaded: count=" + result.segments.size());
                    for (Session session : new ArrayList<>(sessions.values())) {
                        if (key.equals(session.key)) session.refresh(true);
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { pending.remove(key); }
    }
    private final class Session {
        final WeakReference<Object> player;
        WeakReference<Object> director = new WeakReference<>(null);
        WeakReference<View> root = new WeakReference<>(null);
        final SponsorMarkers markers = new SponsorMarkers();
        final SponsorSegments.Guard guard = new SponsorSegments.Guard();
        final Runnable tick = () -> refresh(false);
        VideoKey key;
        Entry filteredFor;
        int filteredMask, filteredDuration;
        List<SponsorSegments.Segment> segments = Collections.emptyList();
        long lastTick;
        Session(Object player) { this.player = new WeakReference<>(player); }
        void stop(boolean clearKey) {
            main.removeCallbacks(tick); markers.clear(); root.clear();
            if (clearKey) { key = null; filteredFor = null; segments = Collections.emptyList(); guard.reset(); }
        }
        void refresh(boolean forceMarkers) {
            main.removeCallbacks(tick);
            Object host = player.get();
            if (host == null || !settings.isSponsorEnabled()) { stop(true); return; }
            lastTick = SystemClock.uptimeMillis();
            reading = true;
            try {
                int state = ((Number) adapter.state.invoke(host)).intValue();
                // 4: playing; 5: paused. Preparing/stopped/released cannot retain old markers.
                if (state != 4 && state != 5) { stop(state == 0 || state == 6 || state == 7); return; }
                Object d = director.get();
                if (d == null) { d = adapter.director(host); director = new WeakReference<>(d); }
                VideoKey current = adapter.key(host, d);
                if (current == null) { stop(false); return; }
                if (!current.equals(key)) {
                    stop(true); key = current;
                    api.debug("sponsor active video=" + key.bvid() + " cid=" + key.cid);
                }
                load(key);
                Entry entry = cache.get(key);
                int duration = ((Number) adapter.duration.invoke(host)).intValue();
                int mask = settings.sponsorCategories();
                boolean changed = entry != filteredFor || mask != filteredMask || duration != filteredDuration;
                if (changed) {
                    filteredFor = entry; filteredMask = mask; filteredDuration = duration;
                    segments = entry == null ? Collections.emptyList() : SponsorSegments.select(entry.segments, mask, duration);
                }
                View view = adapter.root(host);
                if (!settings.isSponsorMarkersEnabled()) { markers.clear(); root.clear(); }
                else if (changed || forceMarkers || root.get() != view) {
                    markers.update(view, segments, duration); root = new WeakReference<>(view);
                }
                if (state == 4) {
                    int position = ((Number) adapter.position.invoke(host)).intValue();
                    int target = guard.target(segments, position);
                    // Validate again immediately before seeking; never reuse identity from a request.
                    if (target > position && current.equals(adapter.key(host, d))) {
                        adapter.seek.invoke(host, target);
                        api.info("sponsor auto-skip: " + position + " -> " + target);
                        if (HostApplication.get() != null) Toast.makeText(HostApplication.get(), "已跳过空降片段", Toast.LENGTH_SHORT).show();
                    }
                    main.postDelayed(tick, 250);
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                stop(true);
                if (!runtimeFailureLogged) {
                    runtimeFailureLogged = true;
                    api.error("sponsor playback adapter failed; skipping disabled for this sample", error);
                }
            } finally { reading = false; }
        }
    }
}
