package com.xjw.bilifix.in.feature.stash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import java.util.Collections;

/** A small edge tab, independent of video dimensions and without a full-height touch strip. */
final class StashHandleView extends View {
    interface Listener {
        boolean onTouch(MotionEvent event);
        void onClick();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final float density;
    private final int side;
    private final Listener listener;

    StashHandleView(Context context, int side, Listener listener) {
        super(context);
        this.side = side;
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        setClickable(true);
        setContentDescription("恢复视频小窗");
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, w, h)));
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = 22 * density;
        float radius = 14 * density;
        float left = side == StashGeometry.LEFT ? 0 : getWidth() - width;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isPressed() ? 0xE06D737C : 0xC75C626A);
        // Extend the outer corners beyond the display, leaving only the two inner corners round.
        canvas.drawRoundRect(side == StashGeometry.LEFT ? -radius : left, 0,
                side == StashGeometry.LEFT ? width : getWidth() + radius,
                getHeight(), radius, radius, paint);

        float cx = left + width / 2, cy = getHeight() / 2f;
        float direction = -side;
        arrow.rewind();
        arrow.moveTo(cx - direction * 2.5f * density, cy - 9 * density);
        arrow.lineTo(cx + direction * 2.5f * density, cy);
        arrow.lineTo(cx - direction * 2.5f * density, cy + 9 * density);
        paint.setColor(0xF7FFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.6f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(arrow, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) setPressed(true);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            setPressed(false);
        }
        if (action == MotionEvent.ACTION_UP) performClick();
        return listener.onTouch(event);
    }

    @Override public boolean performClick() {
        super.performClick();
        listener.onClick();
        return true;
    }
}
