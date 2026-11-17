package com.tvoynatici.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Yayin cercevesinin disindaki bosluklara yayinin renklerini isik gibi yansitir. */
public class GlowView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] cur = {0xFF0A2A5A, 0xFF0A2A5A, 0xFF0A2A5A, 0xFF0A2A5A};   // sol, sag, ust, alt
    private final int[] from = new int[4];
    private final int[] to = new int[4];
    private ValueAnimator anim;

    public GlowView(Context context) {
        this(context, null);
    }

    public GlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setTargets(int left, int right, int top, int bottom) {
        System.arraycopy(cur, 0, from, 0, 4);
        to[0] = left;
        to[1] = right;
        to[2] = top;
        to[3] = bottom;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(500);
        anim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            for (int i = 0; i < 4; i++) cur[i] = mix(from[i], to[i], f);
            invalidate();
        });
        anim.start();
    }

    private static int mix(int a, int b, float f) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f);
        return Color.rgb(r, g, bl);
    }

    private static int alpha(int c, int a) {
        return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        c.drawColor(0xFF020B1C);

        paint.setShader(new LinearGradient(0, 0, w * 0.55f, 0,
                alpha(cur[0], 235), alpha(cur[0], 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w * 0.55f, h, paint);

        paint.setShader(new LinearGradient(w, 0, w * 0.45f, 0,
                alpha(cur[1], 235), alpha(cur[1], 0), Shader.TileMode.CLAMP));
        c.drawRect(w * 0.45f, 0, w, h, paint);

        paint.setShader(new LinearGradient(0, 0, 0, h * 0.5f,
                alpha(cur[2], 200), alpha(cur[2], 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.5f, paint);

        paint.setShader(new LinearGradient(0, h, 0, h * 0.5f,
                alpha(cur[3], 200), alpha(cur[3], 0), Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.5f, w, h, paint);
        paint.setShader(null);
    }
}
