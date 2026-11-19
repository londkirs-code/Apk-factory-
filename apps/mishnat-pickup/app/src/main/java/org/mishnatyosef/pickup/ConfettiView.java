package org.mishnatyosef.pickup;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/** Purple-only confetti rendered on Canvas without external libraries. */
final class ConfettiView extends View {
    private static final int COUNT = 90;
    private static final long DURATION_MS = 3200L;
    private static final int[] PALETTE = {
            Ui.P300, Ui.P400, Ui.P500, Ui.P600, Ui.P700, Ui.P800
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final float[] x = new float[COUNT], y = new float[COUNT];
    private final float[] vx = new float[COUNT], vy = new float[COUNT];
    private final float[] size = new float[COUNT], rot = new float[COUNT], vrot = new float[COUNT];
    private final int[] color = new int[COUNT];
    private final boolean[] round = new boolean[COUNT];

    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private final float density;
    private boolean seeded, cancelled;
    private long lastNanos;
    private Runnable onDone;

    ConfettiView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        animator.setDuration(DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            step();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled && onDone != null) onDone.run();
            }
        });
    }

    void setOnDone(Runnable runnable) {
        onDone = runnable;
    }

    /** Stops without invoking onDone. */
    void stop() {
        cancelled = true;
        animator.cancel();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (!seeded && width > 0 && height > 0) {
            seeded = true;
            seed(width, height);
            lastNanos = System.nanoTime();
            animator.start();
        }
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void seed(int width, int height) {
        for (int i = 0; i < COUNT; i++) {
            x[i] = rnd.nextFloat() * width;
            y[i] = -rnd.nextFloat() * height * 0.35f;
            vx[i] = (rnd.nextFloat() - 0.5f) * 220f * density;
            vy[i] = (120f + rnd.nextFloat() * 260f) * density;
            size[i] = (7f + rnd.nextFloat() * 8f) * density;
            rot[i] = rnd.nextFloat() * 360f;
            vrot[i] = (rnd.nextFloat() - 0.5f) * 540f;
            color[i] = PALETTE[rnd.nextInt(PALETTE.length)];
            round[i] = rnd.nextInt(4) == 0;
        }
    }

    private void step() {
        long now = System.nanoTime();
        float dt = Math.min((now - lastNanos) / 1_000_000_000f, 0.05f);
        lastNanos = now;
        float gravity = 420f * density;
        for (int i = 0; i < COUNT; i++) {
            vy[i] += gravity * dt;
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            rot[i] += vrot[i] * dt;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        float fraction = animator.getAnimatedFraction();
        int alpha = fraction > 0.8f ? (int) (255 * (1f - fraction) / 0.2f) : 255;
        if (alpha < 0) alpha = 0;
        for (int i = 0; i < COUNT; i++) {
            if (y[i] < -size[i] || y[i] > getHeight() + size[i]) continue;
            paint.setColor(color[i]);
            paint.setAlpha(alpha);
            canvas.save();
            canvas.translate(x[i], y[i]);
            canvas.rotate(rot[i]);
            float s = size[i];
            if (round[i]) canvas.drawCircle(0f, 0f, s / 2f, paint);
            else canvas.drawRect(-s / 2f, -s / 4f, s / 2f, s / 4f, paint);
            canvas.restore();
        }
    }
}
