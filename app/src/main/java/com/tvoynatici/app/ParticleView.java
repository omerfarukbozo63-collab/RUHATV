package com.tvoynatici.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** Acilis ekraninda yukari suzulen parlak turkuaz parcaciklar. */
public class ParticleView extends View {

    private static final int N = 48;
    private final float[] px = new float[N];
    private final float[] py = new float[N];
    private final float[] pr = new float[N];
    private final float[] ps = new float[N];
    private final float[] pp = new float[N];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long t0 = SystemClock.uptimeMillis();

    public ParticleView(Context context) {
        this(context, null);
    }

    public ParticleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Random rnd = new Random(7);
        for (int i = 0; i < N; i++) {
            px[i] = rnd.nextFloat();
            py[i] = rnd.nextFloat();
            pr[i] = 1.2f + rnd.nextFloat() * 3.2f;
            ps[i] = 0.02f + rnd.nextFloat() * 0.05f;
            pp[i] = rnd.nextFloat() * 6.28f;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float t = (SystemClock.uptimeMillis() - t0) / 1000f;
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        for (int i = 0; i < N; i++) {
            float y = py[i] - ps[i] * t;
            y = y - (float) Math.floor(y);
            float x = px[i] + 0.015f * (float) Math.sin(t * 0.8f + pp[i]);
            float a = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 2f + pp[i]));
            int alpha = (int) (a * 200);
            paint.setColor(Color.argb((int) (alpha * 0.25f), 29, 233, 208));
            c.drawCircle(x * w, y * h, pr[i] * d * 3f, paint);
            paint.setColor(Color.argb(alpha, 140, 245, 255));
            c.drawCircle(x * w, y * h, pr[i] * d, paint);
        }
        postInvalidateOnAnimation();
    }
}
