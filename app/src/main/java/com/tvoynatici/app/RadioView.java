package com.tvoynatici.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Radyo modunda goruntu yerine gorunen ekran: logo, dalgalar, parcaciklar. Dokunmayi gecirir. */
public class RadioView extends FrameLayout {

    private final ImageView logo;
    private final TextView fallback;

    public RadioView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0A2C5C, 0xFF04112A, 0xFF020818}));
        setVisibility(GONE);

        addView(new ParticleView(c), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addView(new WaveView(c), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LayoutParams llp = new LayoutParams((int) (260 * d), (int) (130 * d), Gravity.CENTER);
        llp.bottomMargin = (int) (34 * d);
        addView(logo, llp);

        fallback = new TextView(c);
        fallback.setText("RADYO RUHA");
        fallback.setTextColor(0xFF1DE9D0);
        fallback.setTextSize(30);
        fallback.setTypeface(null, Typeface.BOLD);
        fallback.setGravity(Gravity.CENTER);
        LayoutParams flp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        flp.bottomMargin = (int) (34 * d);
        addView(fallback, flp);

        TextView live = new TextView(c);
        live.setText("●  CANLI RADYO YAYINI");
        live.setTextColor(0xFF1DE9D0);
        live.setTextSize(13);
        live.setLetterSpacing(0.18f);
        live.setTypeface(null, Typeface.BOLD);
        LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tlp.topMargin = (int) (120 * d);
        addView(live, tlp);
        android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofFloat(live, ALPHA, 0.45f, 1f);
        pulse.setDuration(900);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.start();
    }

    public void setLogo(Bitmap b) {
        if (b == null) return;
        logo.setImageBitmap(b);
        fallback.setVisibility(GONE);
    }
}
