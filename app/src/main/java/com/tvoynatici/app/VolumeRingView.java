package com.tvoynatici.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Ortada beliren yuvarlak turkuaz ses gostergesi (0 - 63). */
public class VolumeRingView extends View {

    private static final int TURQ = 0xFF1DE9D0;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float frac = 1f;
    private int level = 63;
    private boolean muted = false;
    private ValueAnimator anim;

    public VolumeRingView(Context context) {
        this(context, null);
    }

    public VolumeRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xDD03122B);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(12));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0x33FFFFFF);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(12));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(TURQ);
        arcPaint.setShadowLayer(dp(14), 0, 0, TURQ);

        numPaint.setColor(0xFFFFFFFF);
        numPaint.setTextAlign(Paint.Align.CENTER);
        numPaint.setTextSize(dp(64));
        numPaint.setFakeBoldText(true);

        labelPaint.setColor(TURQ);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(16));
        labelPaint.setFakeBoldText(true);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setLevel(int newLevel, boolean isMuted) {
        level = newLevel;
        muted = isMuted;
        float target = newLevel / 63f;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(frac, target);
        anim.setDuration(160);
        anim.addUpdateListener(a -> {
            frac = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f - dp(20);

        canvas.drawCircle(cx, cy, r + dp(14), bgPaint);

        rect.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawArc(rect, 135, 270, false, trackPaint);
        if (frac > 0.001f) {
            canvas.drawArc(rect, 135, 270f * frac, false, arcPaint);
        }

        float baseline = cy - (numPaint.ascent() + numPaint.descent()) / 2f - dp(6);
        canvas.drawText(String.valueOf(level), cx, baseline, numPaint);
        canvas.drawText(muted ? "SESSİZ" : "SES", cx, baseline + dp(34), labelPaint);
    }
}
