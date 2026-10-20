package com.gamebooster.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

/** Grafik garis ringan (Canvas murni): area gradasi, grid, animasi muncul, nilai terakhir. */
public class LineChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();

    private float[] data = new float[0];
    private String title = "";
    private String unit = "";
    private String xLeft = "";
    private String xRight = "";
    private int color = 0xFF22D3EE;
    private float fixMin = Float.NaN;
    private float fixMax = Float.NaN;
    private float reveal = 1f;
    private ValueAnimator animator;
    private final float d;

    public LineChartView(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.5f * d);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * d);
        gridPaint.setColor(0x22FFFFFF);

        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTextSize(13 * d);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);

        valuePaint.setTextAlign(Paint.Align.RIGHT);
        valuePaint.setTextSize(15 * d);
        valuePaint.setTypeface(Typeface.DEFAULT_BOLD);

        scalePaint.setColor(0xFF8F9AE0);
        scalePaint.setTextSize(10 * d);

        emptyPaint.setColor(0xFFA5B4FC);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setTextSize(13 * d);

        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(float[] values, String title, String unit, int color,
                        float fixedMin, float fixedMax, String xLeft, String xRight, boolean animate) {
        this.data = values == null ? new float[0] : values;
        this.title = title;
        this.unit = unit;
        this.color = color;
        this.fixMin = fixedMin;
        this.fixMax = fixedMax;
        this.xLeft = xLeft == null ? "" : xLeft;
        this.xRight = xRight == null ? "" : xRight;
        linePaint.setColor(color);
        dotPaint.setColor(color);
        valuePaint.setColor(color);
        rebuildShader();

        if (animator != null) animator.cancel();
        if (animate && data.length > 1) {
            reveal = 0f;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(700);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    reveal = (float) a.getAnimatedValue();
                    invalidate();
                }
            });
            animator.start();
        } else {
            reveal = 1f;
            invalidate();
        }
    }

    private void rebuildShader() {
        int h = getHeight();
        if (h <= 0) return;
        float top = 34 * d;
        float bottom = h - 24 * d;
        int c1 = (color & 0x00FFFFFF) | 0x66000000;
        int c2 = color & 0x00FFFFFF;
        fillPaint.setShader(new LinearGradient(0, top, 0, bottom, c1, c2, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rebuildShader();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }

    private String fmt(float v) {
        return String.format(Locale.US, "%.1f", v) + unit;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float l = 14 * d;
        float r = w - 14 * d;
        float t = 34 * d;
        float b = h - 24 * d;

        canvas.drawText(title, l, 20 * d, titlePaint);

        int n = data.length;
        if (n < 2) {
            canvas.drawText("Belum ada data…", w / 2f, h / 2f + 6 * d, emptyPaint);
            return;
        }

        float mn = Float.MAX_VALUE;
        float mx = -Float.MAX_VALUE;
        for (float v : data) {
            if (v < mn) mn = v;
            if (v > mx) mx = v;
        }
        if (Float.isNaN(fixMin) || Float.isNaN(fixMax)) {
            mn = mn - 1f;
            mx = mx + 1f;
            if (mx - mn < 2f) {
                float mid = (mx + mn) / 2f;
                mn = mid - 1f;
                mx = mid + 1f;
            }
        } else {
            mn = fixMin;
            mx = fixMax;
        }
        float range = mx - mn;
        if (range <= 0f) range = 1f;

        for (int i = 0; i < 4; i++) {
            float y = t + (b - t) * i / 3f;
            canvas.drawLine(l, y, r, y, gridPaint);
        }
        scalePaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(fmt(mx), r, t - 3 * d, scalePaint);
        canvas.drawText(fmt(mn), r, b - 3 * d, scalePaint);

        int visible = Math.max(2, (int) Math.ceil(n * reveal));
        if (visible > n) visible = n;

        path.reset();
        fillPath.reset();
        float lastX = l;
        float lastY = b;
        for (int i = 0; i < visible; i++) {
            float x = l + (r - l) * i / (n - 1f);
            float y = b - (data[i] - mn) / range * (b - t);
            if (y < t) y = t;
            if (y > b) y = b;
            if (i == 0) {
                path.moveTo(x, y);
                fillPath.moveTo(x, b);
                fillPath.lineTo(x, y);
            } else {
                path.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
            lastX = x;
            lastY = y;
        }
        fillPath.lineTo(lastX, b);
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(path, linePaint);

        canvas.drawCircle(lastX, lastY, 4 * d, dotPaint);
        canvas.drawText(fmt(data[visible - 1]), r, 20 * d, valuePaint);

        scalePaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(xLeft, l, h - 6 * d, scalePaint);
        scalePaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(xRight, r, h - 6 * d, scalePaint);
    }
}
