package com.xjw.bilifix.in.feature.stash;

import android.animation.Animator;
import android.content.pm.PackageInfo;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.xjw.bilifix.in.core.HookApi;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Version-pinned hooks for the host's own overlay; never hooks system PiP or playback. */
public final class MiniPlayerStashHooks {
    private static final String PACKAGE = "com.bilibili.app.in";
    private static final String MANAGER =
            "com.bilibili.mini.player.common.view.MiniPlayerFloatViewManager";
    private static final String DRAG_VIEW = "com.bilibili.mini.player.common.view.d";

    private final HookApi module;
    private final ClassLoader classLoader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Values must also be weak: controllers hold their views and manager via Host.
    private final Map<Object, WeakReference<StashController>> managers = new WeakHashMap<>();
    private final Map<View, WeakReference<StashController>> views = new WeakHashMap<>();
    // Runtime-only overrides: disabling the feature restores the user's original Bili size.
    private final Map<Object, Integer> sizeModes = new WeakHashMap<>();
    private Method getView, getParams, getWindowManager, getWidth, getHeight, getMargins;
    private Method getSize, resize;
    private Field displayRatio;
    private Boolean supportedHost;

    public MiniPlayerStashHooks(HookApi module, ClassLoader classLoader) {
        this.module = module;
        this.classLoader = classLoader;
    }

    public void install() throws ReflectiveOperationException {
        Class<?> manager = module.load(classLoader, MANAGER);
        Class<?> dragView = module.load(classLoader, DRAG_VIEW);
        // Resolve the whole adapter before installing hooks; fail closed on other layouts.
        getView = module.declaredMethod(manager, "x");
        getParams = module.declaredMethod(manager, "y");
        getWindowManager = module.declaredMethod(manager, "E");
        getWidth = module.declaredMethod(manager, "C");
        getHeight = module.declaredMethod(manager, "B");
        getMargins = module.declaredMethod(manager, "D");
        Method start = module.declaredMethod(manager, "c");
        Method end = module.declaredMethod(manager, "a");
        Method calibrate = module.declaredMethod(manager, "N", int.class);
        resize = module.declaredMethod(manager, "S", int.class);
        getSize = module.declaredMethod(manager, "w");
        displayRatio = module.declaredField(manager, "o");
        Method cycleSize = module.declaredMethod(manager, "v");
        Method buildParams = module.declaredMethod(manager, "s", int.class, int.class);
        Method add = module.declaredMethod(manager, "p", boolean.class);
        Method remove = module.declaredMethod(manager, "J");
        Method setView = module.declaredMethod(manager, "K", dragView);
        Field animation = module.declaredField(manager, "s");
        Method intercept = module.declaredMethod(dragView, "onInterceptTouchEvent", MotionEvent.class);
        Method touch = module.declaredMethod(dragView, "onTouchEvent", MotionEvent.class);

        module.addHook("mini player two size levels", cycleSize, chain -> {
            Object host = chain.getThisObject();
            View view = (View) getView.invoke(host);
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) getParams.invoke(host);
            if (!module.isMiniPlayerStashEnabled() || view == null || !view.isAttachedToWindow()
                    || params.type != WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    || !supported(view)) return chain.proceed();
            try {
                int current = (Integer) getSize.invoke(host);
                Object running = animation.get(host);
                if (running instanceof Animator) ((Animator) running).cancel();
                StashController controller = controller(host, false);
                if (controller != null) controller.reset(false);
                sizeModes.put(host, MiniPlayerSizePolicy.nextMode(current));
                resize.invoke(host, view.getWidth());
                WindowManager.LayoutParams changed = (WindowManager.LayoutParams) getParams.invoke(host);
                module.info("mini player size mode=" + sizeModes.get(host) + " width=" + changed.width);
                return null;
            } catch (ReflectiveOperationException | RuntimeException error) {
                sizeModes.remove(host);
                module.error("mini player size toggle failed", error);
                return chain.proceed();
            }
        });
        module.addHook("mini player selected size", getSize, chain -> {
            Integer mode = sizeModes.get(chain.getThisObject());
            return module.isMiniPlayerStashEnabled() && mode != null ? mode : chain.proceed();
        });
        module.addHook("mini player fit maximum size", buildParams, chain -> {
            Object result = chain.proceed();
            Object host = chain.getThisObject();
            if (!usesMaximumSize(host)) return result;
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) getParams.invoke(host);
            if (params.type != WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) return result;
            View view = (View) getView.invoke(host);
            Rect margins = (Rect) getMargins.invoke(host);
            float ratio = displayRatio.getFloat(host);
            // Include any currently visible controls below the video when fitting tall videos.
            int extraHeight = ratio > 0 && view != null
                    ? Math.max(0, view.getHeight() - Math.round(view.getWidth() / ratio)) : 0;
            int availableHeight = (Integer) getHeight.invoke(host) - margins.top - margins.bottom - extraHeight;
            // Keep the host's native narrow edge gaps instead of overriding them to zero.
            int right = (Integer) getWidth.invoke(host) - margins.right;
            int availableWidth = right - margins.left;
            params.width = MiniPlayerSizePolicy.maximumWidth(availableWidth, availableHeight, ratio);
            params.x = Math.max(margins.left, Math.min(params.x, right - params.width));
            return result;
        });
        // buildParams directly calls the short hooked size getter; invalidate inlined copies.
        module.deoptimizeFeatureMethod(buildParams);

        module.addHook("mini player drag start", start, chain -> {
            if (module.isMiniPlayerStashEnabled()) {
                StashController controller = controller(chain.getThisObject(), true);
                if (controller != null) {
                    Object running = animation.get(chain.getThisObject());
                    if (running instanceof Animator) ((Animator) running).cancel();
                    controller.dragStarted();
                }
            }
            return chain.proceed();
        });
        module.addHook("mini player drag end", end, chain -> {
            StashController controller = controller(chain.getThisObject(), false);
            if (module.isMiniPlayerStashEnabled() && controller != null && controller.dragEnded()) {
                return null;
            }
            return chain.proceed();
        });
        module.addHook("mini player edge calibration", calibrate, chain -> {
            StashController controller = controller(chain.getThisObject(), false);
            if (controller != null && controller.ownsPosition()) return null;
            return chain.proceed();
        });
        for (Method method : new Method[]{resize, add, remove}) {
            module.addHook("mini player reset " + method.getName(), method, chain -> {
                StashController controller = controller(chain.getThisObject(), false);
                if (controller != null) controller.reset(false);
                return chain.proceed();
            });
        }
        module.addHook("mini player replace view", setView, chain -> {
            Object host = chain.getThisObject();
            StashController controller = controller(host, false);
            if (controller != null && controller.boundView() != chain.getArg(0)) {
                controller.reset(false);
                views.remove(controller.boundView());
                managers.remove(host);
            }
            return chain.proceed();
        });
        module.addHook("mini player intercept handle", intercept, chain -> {
            StashController controller = lookup(views, chain.getThisObject());
            if (controller != null) {
                MotionEvent event = (MotionEvent) chain.getArg(0);
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) controller.cancelDrag();
                if (controller.ownsPosition()) return true;
            }
            return chain.proceed();
        });
        module.addHook("mini player handle touch", touch, chain -> {
            StashController controller = lookup(views, chain.getThisObject());
            if (controller != null) {
                MotionEvent event = (MotionEvent) chain.getArg(0);
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) controller.cancelDrag();
                if (controller.touch(event)) return true;
            }
            return chain.proceed();
        });
    }

    /** Settings changes may originate off the UI thread; window state belongs to the UI thread. */
    public void onSettingsChanged() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::onSettingsChanged);
            return;
        }
        if (module.isMiniPlayerStashEnabled()) return;
        ArrayList<Object> resizedHosts = new ArrayList<>(sizeModes.keySet());
        sizeModes.clear();
        for (WeakReference<StashController> ref : new ArrayList<>(managers.values())) {
            StashController controller = ref.get();
            if (controller == null) continue;
            try {
                controller.reset(controller.ownsPosition());
            } catch (RuntimeException error) {
                module.error("mini player restore on disable failed", error);
            }
        }
        for (Object host : resizedHosts) {
            try {
                View view = (View) getView.invoke(host);
                if (view != null && view.isAttachedToWindow()) resize.invoke(host, view.getWidth());
            } catch (ReflectiveOperationException | RuntimeException error) {
                module.error("mini player restore original size failed", error);
            }
        }
    }

    private boolean usesMaximumSize(Object host) {
        return module.isMiniPlayerStashEnabled()
                && Integer.valueOf(MiniPlayerSizePolicy.MAXIMUM).equals(sizeModes.get(host));
    }

    private StashController controller(Object manager, boolean create) throws Exception {
        StashController existing = lookup(managers, manager);
        if (existing != null || !create) return existing;
        View view = (View) getView.invoke(manager);
        if (view == null || !supported(view)) return null;
        StashController controller = new StashController(new StashController.Host() {
            @Override public View view() { return view; }
            @Override public WindowManager.LayoutParams params() {
                return (WindowManager.LayoutParams) read(getParams, manager);
            }
            @Override public WindowManager windowManager() {
                return (WindowManager) read(getWindowManager, manager);
            }
            @Override public Rect restoreBounds() {
                Rect bounds = movementBounds();
                Rect margins = (Rect) read(getMargins, manager);
                bounds.left += margins.left;
                bounds.right -= margins.right;
                return bounds;
            }
            @Override public Rect movementBounds() {
                int width = (Integer) read(getWidth, manager);
                int height = (Integer) read(getHeight, manager);
                Rect margins = (Rect) read(getMargins, manager);
                return new Rect(0, margins.top, width, height - margins.bottom);
            }
        });
        managers.put(manager, new WeakReference<>(controller));
        views.put(view, new WeakReference<>(controller));
        return controller;
    }

    private boolean supported(View view) throws Exception {
        if (supportedHost == null) {
            PackageInfo info = view.getContext().getPackageManager().getPackageInfo(PACKAGE, 0);
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
            supportedHost = code == 8_230_800L && "3.20.4".equals(info.versionName);
            module.info("mini player stash host supported=" + supportedHost
                    + " version=" + info.versionName + " code=" + code);
        }
        return supportedHost;
    }

    private static StashController lookup(Map<?, WeakReference<StashController>> map, Object key) {
        WeakReference<StashController> ref = map.get(key);
        return ref == null ? null : ref.get();
    }

    private static Object read(Method method, Object receiver) {
        try {
            return method.invoke(receiver);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Mini player adapter changed: " + method.getName(), error);
        }
    }
}
