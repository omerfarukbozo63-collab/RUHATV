package com.tvoynatici.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Acilista cikan profil secimi: Ruha TV / Radyo Ruha. TV kumandasi ve dokunma ile calisir. */
public class ProfilePicker extends FrameLayout {

    public interface Listener {
        void onPicked(int profile);   // 0 = Ruha TV, 1 = Radyo Ruha
    }

    private final float d;
    private final ImageView radioLogo;
    private final TextView radioFallback;
    private final LinearLayout cardTv, cardRadio;

    public ProfilePicker(Activity act, boolean compact, Listener l) {
        super(act);
        d = act.getResources().getDisplayMetrics().density;
        setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A2650, 0xFF04112A, 0xFF020818}));
        setClickable(true);
        setVisibility(GONE);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = text(act, "Hoş geldiniz", compact ? 24 : 32, 0xFFFFFFFF, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title);
        TextView sub = text(act, "Bir profil seçin", compact ? 13 : 16, 0xFF1DE9D0, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(compact ? 12 : 22));
        col.addView(sub);

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        col.addView(row);

        int cw = dp(compact ? 190 : 260);
        int ch = dp(compact ? 180 : 250);

        // --- Ruha TV
        ImageView tvLogo = new ImageView(act);
        tvLogo.setImageResource(R.drawable.logo_tr);
        tvLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cardTv = card(act, tvLogo, null, "Ruha TV", "Canlı televizyon yayını", cw, ch, compact);
        cardTv.setOnClickListener(v -> l.onPicked(0));
        row.addView(cardTv, new LinearLayout.LayoutParams(cw, ch));

        // --- Radyo Ruha (logo uzaktan gelir; gelene kadar yazi gorunur)
        radioLogo = new ImageView(act);
        radioLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        radioFallback = text(act, "RADYO\nRUHA", compact ? 20 : 26, 0xFF1DE9D0, true);
        radioFallback.setGravity(Gravity.CENTER);
        cardRadio = card(act, radioLogo, radioFallback, "Radyo Ruha", "Canlı radyo yayını", cw, ch, compact);
        cardRadio.setOnClickListener(v -> l.onPicked(1));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(cw, ch);
        rlp.leftMargin = dp(compact ? 18 : 30);
        row.addView(cardRadio, rlp);

        // Kumanda: sag-sol gecis
        cardTv.setNextFocusRightId(View.NO_ID);
        cardTv.setId(View.generateViewId());
        cardRadio.setId(View.generateViewId());
        cardTv.setNextFocusRightId(cardRadio.getId());
        cardRadio.setNextFocusLeftId(cardTv.getId());
    }

    private LinearLayout card(Activity act, ImageView logo, TextView fallback, String name,
                              String sub, int w, int h, boolean compact) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setBackgroundResource(R.drawable.bg_profile);
        c.setClickable(true);
        c.setFocusable(true);
        c.setPadding(dp(14), dp(compact ? 14 : 22), dp(14), dp(compact ? 12 : 18));

        FrameLayout box = new FrameLayout(act);
        box.addView(logo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (fallback != null) {
            box.addView(fallback, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        }
        c.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView n = text(act, name, compact ? 17 : 22, 0xFFFFFFFF, true);
        n.setGravity(Gravity.CENTER);
        n.setPadding(0, dp(8), 0, 0);
        c.addView(n);
        TextView s = text(act, sub, compact ? 11 : 13, 0xFF9DB4CA, false);
        s.setGravity(Gravity.CENTER);
        c.addView(s);

        // Odak ya da basma: hafif buyur
        c.setOnFocusChangeListener((v, focus) ->
                v.animate().scaleX(focus ? 1.06f : 1f).scaleY(focus ? 1.06f : 1f).setDuration(140).start());
        return c;
    }

    /** Radyo logosu indi ya da degisti. */
    public void setRadioLogo(Bitmap b) {
        if (b == null) return;
        radioLogo.setImageBitmap(b);
        radioFallback.setVisibility(GONE);
    }

    public void show() {
        setAlpha(0f);
        setVisibility(VISIBLE);
        animate().alpha(1f).setDuration(350).start();
        cardTv.requestFocus();
    }

    public void hide() {
        animate().alpha(0f).setDuration(250).withEndAction(() -> setVisibility(GONE)).start();
    }

    public boolean isShown2() {
        return getVisibility() == VISIBLE;
    }

    private TextView text(Activity act, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * d);
    }
}
