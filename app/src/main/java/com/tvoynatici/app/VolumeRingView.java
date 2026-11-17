package com.tvoynatici.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Ortada beliren yuvarlak turkuaz ses gostergesi (0 - 63). */
public class VolumeRingView extends View {

    private static final int TURQ = 0xFF1DE9D0;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        bgPaint.setColor(0xE604112A);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.5f));

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(3));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0x33FFFFFF);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(3));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(TURQ);
        arcPaint.setShadowLayer(dp(6), 0, 0, TURQ);

        numPaint.setColor(0xFFFFFFFF);
        numPaint.setTextAlign(Paint.Align.CENTER);
        numPaint.setTextSize(dp(58));
        numPaint.setFakeBoldText(true);

        labelPaint.setColor(TURQ);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(13));
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
        float r = Math.min(w, h) / 2f - dp(22);

        canvas.drawCircle(cx, cy, r + dp(16), bgPaint);
        ringPaint.setColor(0x731DE9D0);
        canvas.drawCircle(cx, cy, r + dp(16), ringPaint);

        // 63 centik: dolu olanlar parlayan turkuaz, bos olanlar soluk beyaz
        float lit = frac * 63f;
        for (int k = 0; k < 63; k++) {
            double ang = Math.toRadians(135 + 270.0 * k / 62.0);
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            boolean major = k % 9 == 0;
            float r1 = r - dp(major ? 14 : 9);
            float r2 = r + dp(9);
            boolean on = k < lit - 0.01f;
            Paint p = on ? arcPaint : trackPaint;
            canvas.drawLine(cx + r1 * cos, cy + r1 * sin, cx + r2 * cos, cy + r2 * sin, p);
        }

        float baseline = cy - (numPaint.ascent() + numPaint.descent()) / 2f - dp(4);
        canvas.drawText(String.valueOf(level), cx, baseline, numPaint);
        canvas.drawText(muted ? "SESSİZ" : "SES", cx, baseline + dp(30), labelPaint);
    }

    /** Halka uzerinde parmakla ses ayarlandiginda haber verir. */
    public interface Listener {
        void onLevelChanged(int level);
        void onTouchChanged(boolean down);
    }

    private Listener listener;
    private boolean touching = false;

    public void setOnLevelChangeListener(Listener l) {
        listener = l;
    }

    /** Animasyonsuz, aninda seviye gunceller. */
    public void setLevelNow(int newLevel) {
        if (anim != null) anim.cancel();
        level = newLevel;
        muted = false;
        frac = newLevel / 63f;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (listener == null || getAlpha() < 0.5f) return false;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float dx = e.getX() - cx;
        float dy = e.getY() - cy;
        int a = e.getActionMasked();

        if (a == MotionEvent.ACTION_DOWN) {
            float rad = Math.min(getWidth(), getHeight()) / 2f;
            if (Math.hypot(dx, dy) > rad) return false;
            touching = true;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            listener.onTouchChanged(true);
        }
        if (!touching) return false;

        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            if (Math.hypot(dx, dy) < dp(30)) return true;
            double ang = Math.toDegrees(Math.atan2(dy, dx));
            if (ang < 0) ang += 360;
            double rel = ang - 135;
            if (rel < 0) rel += 360;
            float f = rel <= 270 ? (float) (rel / 270.0) : (rel < 315 ? 1f : 0f);
            int lvl = Math.round(f * 63);
            setLevelNow(lvl);
            listener.onLevelChanged(lvl);
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            touching = false;
            listener.onTouchChanged(false);
        }
        return true;
    }
}
