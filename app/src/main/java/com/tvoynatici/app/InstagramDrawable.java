package com.tvoynatici.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Instagram simgesi: renk gecisli yuvarlak kare ve beyaz kamera cizimi (kodla cizilir). */
public class InstagramDrawable extends Drawable {

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public InstagramDrawable() {
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0xFFFFFFFF);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        float w = b.width();
        bg.setShader(new RadialGradient(
                b.left + w * 0.30f, b.top + b.height() * 1.07f, w * 1.15f,
                new int[]{0xFFFDF497, 0xFFFDF497, 0xFFFD5949, 0xFFD6249F, 0xFF285AEB},
                new float[]{0f, 0.05f, 0.45f, 0.60f, 0.90f},
                Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = b.width() / 48f;
        c.save();
        c.translate(b.left, b.top);
        c.scale(s, s);

        rect.set(0, 0, 48, 48);
        c.drawRoundRect(rect, 12f, 12f, bg);

        line.setStrokeWidth(3f);
        rect.set(11f, 11f, 37f, 37f);
        c.drawRoundRect(rect, 8f, 8f, line);
        c.drawCircle(24f, 24f, 6.5f, line);
        c.drawCircle(31.5f, 16.5f, 1.8f, dot);
        c.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        bg.setAlpha(alpha);
        line.setAlpha(alpha);
        dot.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        bg.setColorFilter(cf);
        line.setColorFilter(cf);
        dot.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return 96;
    }

    @Override
    public int getIntrinsicHeight() {
        return 96;
    }
}
