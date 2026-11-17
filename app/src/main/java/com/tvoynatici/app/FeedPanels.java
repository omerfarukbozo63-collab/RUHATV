package com.tvoynatici.app;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Alttan cikan Urfa Haber paneli: fotografli, kategori etiketli haber kartlari. */
public class FeedPanels {

    public interface Host {
        void openLink(String url);
    }

    private static final long IDLE_MS = 30000;   // dokunulmazsa panel bu sure sonra kapanir
    private static final String SITE_URL = "https://www.urfahaber.com";

    private final Activity act;
    private final Handler handler;
    private final Host host;
    private final float d;
    private final int heightDp;

    private final FrameLayout newsLayer;
    private final LinearLayout newsSheet;
    private final LinearLayout newsRow;
    private final TextView newsStatus;
    private boolean newsOpen = false;
    private final Runnable idleTask = this::hideAll;

    public FeedPanels(Activity act, FrameLayout root, Handler handler, Host host) {
        this.act = act;
        this.handler = handler;
        this.host = host;
        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        d = dm.density;
        heightDp = Math.round(dm.heightPixels / dm.density);

        newsLayer = new FrameLayout(act);
        newsLayer.setBackgroundColor(0x99020818);
        newsLayer.setClickable(true);
        newsLayer.setVisibility(View.GONE);
        newsLayer.setOnClickListener(v -> hideNews());

        newsSheet = new LinearLayout(act);
        newsSheet.setOrientation(LinearLayout.VERTICAL);
        newsSheet.setBackgroundResource(R.drawable.bg_sheet);
        newsSheet.setClickable(true);
        newsSheet.setPadding(dp(18), dp(2), dp(18), dp(14));
        newsLayer.addView(newsSheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        FrameLayout grip = new FrameLayout(act);
        View bar = new View(act);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0x59FFFFFF);
        gd.setCornerRadius(dp(3));
        bar.setBackground(gd);
        grip.addView(bar, new FrameLayout.LayoutParams(dp(44), dp(5), Gravity.CENTER));
        newsSheet.addView(grip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        makeDismissable(grip, newsSheet);

        // Baslik: beyaz logo karosu, "Son Haberler", site adi, "Siteyi ac", kapat
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout plate = new FrameLayout(act);
        plate.setBackgroundResource(R.drawable.bg_logo_plate);
        plate.setPadding(dp(11), dp(6), dp(11), dp(6));
        ImageView urfaLogo = new ImageView(act);
        urfaLogo.setImageResource(R.drawable.logo_urfahaber);
        urfaLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        plate.addView(urfaLogo, new FrameLayout.LayoutParams(dp(62), dp(28)));
        head.addView(plate, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout titles = new LinearLayout(act);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Son Haberler", 20, 0xFFFFFFFF, true));
        titles.addView(text("urfahaber.com", 12, 0xFF1DE9D0, false));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(12);
        head.addView(titles, tlp);

        TextView open = text("Siteyi aç  ↗", 12, 0xFFFFFFFF, true);
        open.setGravity(Gravity.CENTER);
        open.setBackgroundResource(R.drawable.bg_pill);
        open.setPadding(dp(14), 0, dp(14), 0);
        open.setClickable(true);
        open.setOnClickListener(v -> host.openLink(SITE_URL));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        olp.rightMargin = dp(10);
        head.addView(open, olp);

        head.addView(closeButton(() -> hideNews()), new LinearLayout.LayoutParams(dp(34), dp(34)));
        newsSheet.addView(head);

        newsStatus = text("Haberler yükleniyor…", 15, 0xB3FFFFFF, false);
        newsStatus.setPadding(0, dp(18), 0, dp(18));
        newsSheet.addView(newsStatus);

        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        newsRow = new LinearLayout(act);
        newsRow.setOrientation(LinearLayout.HORIZONTAL);
        newsRow.setPadding(dp(4), dp(6), dp(4), dp(6));
        hs.addView(newsRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(8);
        newsSheet.addView(hs, hlp);

        root.addView(newsLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ================================================================ Veri

    public void setNews(List<FeedData.News> list) {
        newsRow.removeAllViews();
        if (list == null || list.isEmpty()) {
            newsStatus.setText("Haberler şu an alınamıyor");
            newsStatus.setVisibility(View.VISIBLE);
            return;
        }
        newsStatus.setVisibility(View.GONE);
        int cardW = dp(232);
        int cardH = dp(Math.min(172, Math.max(120, (int) (heightDp * 0.42f))));
        boolean first = true;
        for (FeedData.News n : list) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            lp.rightMargin = dp(12);
            newsRow.addView(newsCard(n, cardW, first), lp);
            first = false;
        }
    }

    public void setError(String msg) {
        if (newsRow.getChildCount() == 0) {
            newsStatus.setText(msg);
            newsStatus.setVisibility(View.VISIBLE);
        }
    }

    public boolean hasNews() {
        return newsRow.getChildCount() > 0;
    }

    // ================================================================ Kart

    private View newsCard(final FeedData.News n, int cardW, boolean featured) {
        FrameLayout card = new FrameLayout(act);
        GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(dp(18));
        border.setColor(0xFF0B2244);
        border.setStroke(featured ? dp(2) : dp(1), featured ? 0xFF1DE9D0 : 0x1FFFFFFF);
        card.setBackground(border);
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setOnClickListener(v -> host.openLink(n.link));

        ImageView img = new ImageView(act);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(img, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageLoader.load(img, n.image, cardW);

        // Alta dogru koyulasan perde: yazi her fotografta okunur
        View shade = new View(act);
        GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0xF0030A1A});
        shade.setBackground(sg);
        card.addView(shade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (!n.category.isEmpty()) {
            TextView cat = text(n.category.toUpperCase(), 9.5f, 0xFF1DE9D0, true);
            cat.setLetterSpacing(0.08f);
            cat.setBackgroundResource(R.drawable.bg_cat);
            cat.setPadding(dp(9), dp(3), dp(9), dp(3));
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            clp.setMargins(dp(10), dp(10), 0, 0);
            card.addView(cat, clp);
        }

        LinearLayout bottom = new LinearLayout(act);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(12), 0, dp(12), dp(10));
        TextView title = text(n.title, 13.5f, 0xFFFFFFFF, true);
        title.setMaxLines(3);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bottom.addView(title);
        TextView m = text("" + FeedData.timeAgo(n.time), 10.5f, 0xFF9DB4CA, false);
        m.setPadding(0, dp(4), 0, 0);
        bottom.addView(m);
        card.addView(bottom, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));
        return card;
    }

    // ================================================================ Acma / kapama

    public boolean isOpen() {
        return newsOpen;
    }

    public void showNews() {
        if (newsOpen) return;
        newsOpen = true;
        newsLayer.animate().cancel();
        newsSheet.animate().cancel();
        newsLayer.setAlpha(0f);
        newsLayer.setVisibility(View.VISIBLE);
        newsSheet.setTranslationY(dp(320));
        newsLayer.animate().alpha(1f).setDuration(180).start();
        newsSheet.animate().translationY(0f).setDuration(320)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        for (int i = 0; i < newsRow.getChildCount(); i++) {
            View c = newsRow.getChildAt(i);
            c.animate().cancel();
            c.setAlpha(0f);
            c.setTranslationX(dp(40));
            c.animate().alpha(1f).translationX(0f)
                    .setStartDelay(140L + 60L * i).setDuration(280).start();
        }
        touch();
    }

    public void hideNews() {
        if (!newsOpen) return;
        newsOpen = false;
        handler.removeCallbacks(idleTask);
        newsLayer.animate().alpha(0f).setDuration(220).withEndAction(() -> {
            if (!newsOpen) {
                newsLayer.setVisibility(View.GONE);
                newsSheet.setTranslationY(0f);
            }
        }).start();
        newsSheet.animate().translationY(dp(320)).setDuration(240).start();
    }

    public void hideAll() {
        hideNews();
    }

    /** Hicbir animasyon beklemeden her seyi kapatir. */
    public void hideNow() {
        newsOpen = false;
        newsLayer.animate().cancel();
        newsSheet.animate().cancel();
        newsLayer.setVisibility(View.GONE);
        newsSheet.setTranslationY(0f);
        handler.removeCallbacks(idleTask);
    }

    /** Kullanici panele dokundukca kapanma suresi yeniden baslar. */
    public void touch() {
        handler.removeCallbacks(idleTask);
        if (isOpen()) handler.postDelayed(idleTask, IDLE_MS);
    }

    // ================================================================ Yardimcilar

    private void makeDismissable(View grip, final View panel) {
        final float[] start = new float[1];
        grip.setOnTouchListener((v, e) -> {
            float cur = e.getRawY();
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                start[0] = cur;
                return true;
            }
            if (a == MotionEvent.ACTION_MOVE) {
                panel.setTranslationY(Math.max(0f, cur - start[0]));
                return true;
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                float dd = cur - start[0];
                if (a == MotionEvent.ACTION_UP && dd > dp(60)) {
                    hideAll();
                } else {
                    panel.animate().translationY(0f).setDuration(150).start();
                }
                return true;
            }
            return false;
        });
    }

    private TextView closeButton(final Runnable onClick) {
        TextView t = new TextView(act);
        t.setText("✕");
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_circle);
        t.setClickable(true);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * d);
    }
}
