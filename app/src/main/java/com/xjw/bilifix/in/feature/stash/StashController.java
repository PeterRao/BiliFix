package com.xjw.bilifix.in.feature.stash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

/** Keeps the original window and player surface alive, with a separate compact edge handle. */
public final class StashController {
    public interface Host {
        View view();
        WindowManager.LayoutParams params();
        WindowManager windowManager();
        Rect movementBounds();
        Rect restoreBounds();
    }

    private final Host host;
    private final int threshold, handleWidth, handleHeight, offscreenMargin;
    private ValueAnimator animator;
    private StashHandleView handleView;
    private WindowManager handleWindowManager;
    private int side, startX, originalFlags;
    private boolean moving, consuming, restoring;
    private float touchX;

    public StashController(Host host) {
        this.host = host;
        float density = host.view().getResources().getDisplayMetrics().density;
        threshold = Math.round(24 * density);
        // The visible tab is 22 dp wide; 6 dp of transparent inner padding makes it easier to grab.
        handleWidth = Math.round(28 * density);
        handleHeight = Math.round(88 * density);
        offscreenMargin = Math.round(8 * density);
        host.view().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { reset(false); }
        });
    }

    public boolean ownsPosition() { return side != 0 || restoring; }
    public View boundView() { return host.view(); }
    public void cancelDrag() { moving = false; }

    public void dragStarted() {
        if (ownsPosition()) return;
        cancelAnimation();
        startX = host.params().x;
        moving = true;
    }

    public boolean dragEnded() {
        if (!moving || ownsPosition()) return false;
        moving = false;
        View view = host.view();
        WindowManager.LayoutParams p = host.params();
        if (!view.isAttachedToWindow() || p.type != WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
            return false;
        }
        Rect bounds = host.movementBounds();
        int targetSide = StashGeometry.side(p.x, view.getWidth(), bounds.left, bounds.right,
                p.x - startX, threshold);
        if (targetSide == 0) return false;
        side = targetSide;
        originalFlags = p.flags;
        p.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        p.y = StashGeometry.clampY(p.y, view.getHeight(), bounds.top, bounds.bottom);
        // Never move the video out of reach unless the recovery handle was added successfully.
        if (!showHandle(bounds)) {
            reset(true);
            return false;
        }
        animateTo(StashGeometry.hiddenX(side, view.getWidth(), bounds.left, bounds.right,
                offscreenMargin), false);
        return true;
    }

    /** Also consumes a gesture begun on the old video view during a transition. */
    public boolean touch(MotionEvent event) {
        if (!ownsPosition() && !consuming) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                consuming = true;
                touchX = event.getRawX();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!restoring && side != 0 && (event.getRawX() - touchX) * -side > threshold) restore();
                break;
            case MotionEvent.ACTION_UP:
                restore();
                consuming = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                consuming = false;
                break;
            default:
                break;
        }
        return true;
    }

    private void restore() {
        if (side == 0 || restoring) return;
        Rect bounds = host.restoreBounds();
        restoring = true;
        animateTo(StashGeometry.restoredX(side, host.view().getWidth(), bounds.left, bounds.right), true);
    }

    private boolean showHandle(Rect bounds) {
        View view = host.view();
        WindowManager.LayoutParams source = host.params();
        StashHandleView handle = new StashHandleView(view.getContext(), side,
                new StashHandleView.Listener() {
                    @Override public boolean onTouch(MotionEvent event) { return touch(event); }
                    @Override public void onClick() { restore(); }
                });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(handleWidth, handleHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = source.gravity;
        params.x = side == StashGeometry.LEFT ? bounds.left : bounds.right - handleWidth;
        params.y = StashGeometry.clampY(source.y + (view.getHeight() - handleHeight) / 2,
                handleHeight, bounds.top, bounds.bottom);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = source.layoutInDisplayCutoutMode;
        }
        params.setTitle("BiliFix 小窗收起把手");
        handle.setLayoutDirection(view.getLayoutDirection());
        handle.setAlpha(0f);
        try {
            WindowManager manager = host.windowManager();
            manager.addView(handle, params);
            handleView = handle;
            handleWindowManager = manager;
            return true;
        } catch (RuntimeException error) {
            Log.w("BiliStash", "Unable to show recovery handle", error);
            return false;
        }
    }

    private void removeHandle() {
        StashHandleView handle = handleView;
        WindowManager manager = handleWindowManager;
        handleView = null;
        handleWindowManager = null;
        if (handle == null || manager == null) return;
        try {
            manager.removeViewImmediate(handle);
        } catch (IllegalArgumentException ignored) {
            // The host may already have removed all windows during teardown.
        }
    }

    /** Rotation, resize, window replacement, disable and close leave no handle behind. */
    public void reset(boolean updateWindow) {
        cancelAnimation();
        removeHandle();
        if (ownsPosition()) {
            Rect bounds = host.restoreBounds();
            WindowManager.LayoutParams p = host.params();
            p.x = StashGeometry.restoredX(side, host.view().getWidth(), bounds.left, bounds.right);
            p.y = StashGeometry.clampY(p.y, host.view().getHeight(), bounds.top, bounds.bottom);
            restoreFlags();
        }
        side = 0;
        restoring = false;
        moving = false;
        consuming = false;
        if (updateWindow) update();
    }

    private void restoreFlags() {
        WindowManager.LayoutParams p = host.params();
        p.flags = (p.flags & ~WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                | (originalFlags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    private void animateTo(int targetX, boolean restore) {
        cancelAnimation();
        float initialHandleAlpha = handleView != null ? handleView.getAlpha() : 0f;
        ValueAnimator next = ValueAnimator.ofInt(host.params().x, targetX);
        animator = next;
        next.setDuration(180);
        next.setInterpolator(new DecelerateInterpolator());
        next.addUpdateListener(value -> {
            host.params().x = (Integer) value.getAnimatedValue();
            if (handleView != null) {
                float fraction = value.getAnimatedFraction();
                handleView.setAlpha(initialHandleAlpha
                        + ((restore ? 0f : 1f) - initialHandleAlpha) * fraction);
            }
            update();
        });
        next.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != next) return;
                animator = null;
                if (restore) {
                    restoreFlags();
                    side = 0;
                    restoring = false;
                    // Removing the handle may end a pull before ACTION_UP reaches it.
                    // Never carry that stream into the next gesture on the video.
                    consuming = false;
                    removeHandle();
                    update();
                }
                Log.i("BiliStash", "settled side=" + side + " x=" + host.params().x
                        + " width=" + host.view().getWidth() + " compactHandle=" + (handleView != null));
            }
        });
        next.start();
    }

    private void cancelAnimation() {
        ValueAnimator previous = animator;
        animator = null;
        if (previous != null) {
            previous.removeAllUpdateListeners();
            previous.removeAllListeners();
            previous.cancel();
        }
    }

    private void update() {
        View view = host.view();
        if (!view.isAttachedToWindow()) { reset(false); return; }
        try {
            host.windowManager().updateViewLayout(view, host.params());
        } catch (RuntimeException error) {
            Log.w("BiliStash", "Window no longer available", error);
            reset(false);
        }
    }
}
