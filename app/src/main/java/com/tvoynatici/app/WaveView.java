package com.tvoynatici.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Ekranin alt kisminda yatay akan, ust uste binmis turkuaz/mavi dalgalar. */
public class WaveView extends View {

    private static final int LAYERS = 3;
    private final Paint[] paints = new Paint[LAYERS];
    private final Path path = new Path();
    private final long t0 = SystemClock.uptimeMillis();

    private static final int[] COLORS = {0x401DE9D0, 0x400288D1, 0x331565C0};
    private static final float[] SPEED = {0.9f, -0.6f, 0.4f};
    private static final float[] FREQ = {1.6f, 2.2f, 1.2f};
    private static final float[] PHASE = {0f, 1.7f, 3.4f};

    public WaveView(Context context) {
        this(context, null);
    }

    public WaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        for (int i = 0; i < LAYERS; i++) {
            paints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            paints[i].setStyle(Paint.Style.FILL);
            paints[i].setColor(COLORS[i]);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float t = (SystemClock.uptimeMillis() - t0) / 1000f;
        float w = getWidth();
        float h = getHeight();
        float step = Math.max(8f, w / 120f);
        for (int i = 0; i < LAYERS; i++) {
            float baseY = h * (0.74f + 0.06f * i);
            float amp = h * (0.035f + 0.012f * i);
            path.reset();
            path.moveTo(0, h);
            for (float x = 0; x <= w + step; x += step) {
                float y = baseY + amp * (float) Math.sin(
                        (x / w) * FREQ[i] * 6.2832f + t * SPEED[i] * 2f + PHASE[i]);
                path.lineTo(x, y);
            }
            path.lineTo(w + step, h);
            path.close();
            c.drawPath(path, paints[i]);
        }
        postInvalidateOnAnimation();
    }
}
