package com.gamebooster.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

public class GaugeView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final RectF inner = new RectF();
    private final Matrix matrix = new Matrix();
    private SweepGradient shader;
    private float value = 0f;
    private String caption = "RAM TERPAKAI";
    private String sub = "";
    private ValueAnimator animator;
    private ValueAnimator scanAnim;
    private boolean scanning = false;
    private float scanAngle = 0f;
    private final float density;

    public GaugeView(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        float stroke = 16 * density;

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0x22FFFFFF);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);

        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(stroke * 1.9f);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setAlpha(55);

        scanPaint.setStyle(Paint.Style.STROKE);
        scanPaint.setStrokeWidth(3 * density);
        scanPaint.setStrokeCap(Paint.Cap.ROUND);
        scanPaint.setColor(0xAA22D3EE);

        big.setColor(Color.WHITE);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTextSize(58 * density);
        big.setTypeface(Typeface.DEFAULT_BOLD);

        small.setColor(0xFFA5B4FC);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(13 * density);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float pad = 24 * density;
        oval.set(pad, pad, w - pad, h - pad);
        float ip = pad + 30 * density;
        inner.set(ip, ip, w - ip, h - ip);
        shader = new SweepGradient(w / 2f, h / 2f,
                new int[]{0xFF22D3EE, 0xFF8B5CF6, 0xFFEC4899, 0xFFEC4899},
                new float[]{0f, 0.4f, 0.75f, 1f});
        matrix.setRotate(135, w / 2f, h / 2f);
        shader.setLocalMatrix(matrix);
        arc.setShader(shader);
        glow.setShader(shader);
        glow.setAlpha(55);
    }

    public void setCaption(String c) {
        caption = c;
        invalidate();
    }

    public void setSub(String s) {
        sub = s;
        invalidate();
    }

    public void setScanning(boolean on) {
        if (on == scanning) return;
        scanning = on;
        if (on) {
            scanAnim = ValueAnimator.ofFloat(0f, 360f);
            scanAnim.setDuration(1100);
            scanAnim.setRepeatCount(ValueAnimator.INFINITE);
            scanAnim.setInterpolator(new LinearInterpolator());
            scanAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    scanAngle = (float) a.getAnimatedValue();
                    invalidate();
                }
            });
            scanAnim.start();
        } else {
            if (scanAnim != null) scanAnim.cancel();
            invalidate();
        }
    }

    public void animateTo(float target, long ms) {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(value, Math.max(0f, Math.min(100f, target)));
        animator.setDuration(ms);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                value = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
        if (scanAnim != null) scanAnim.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawArc(oval, 135, 270, false, track);
        float sweep = 270f * value / 100f;
        if (sweep > 0.5f) {
            canvas.drawArc(oval, 135, sweep, false, glow);
            canvas.drawArc(oval, 135, sweep, false, arc);
        }
        if (scanning) {
            canvas.drawArc(inner, scanAngle, 70, false, scanPaint);
            canvas.drawArc(inner, scanAngle + 180f, 35, false, scanPaint);
        }
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawText(Math.round(value) + "%", cx, cy + 14 * density, big);
        canvas.drawText(caption, cx, cy - 46 * density, small);
        canvas.drawText(sub, cx, cy + 46 * density, small);
    }
}
