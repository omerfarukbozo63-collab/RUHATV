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
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Alttan cikan haber kartlari ve sagdan cikan YouTube video kareleri. */
public class FeedPanels {

    public interface Host {
        void openLink(String url);
    }

    private static final long IDLE_MS = 30000;   // dokunulmazsa panel bu sure sonra kapanir

    private final Activity act;
    private final Handler handler;
    private final Host host;
    private final float d;
    private final int sideW;
    private final int heightDp;

    private final FrameLayout newsLayer;
    private final FrameLayout videoLayer;
    private final LinearLayout newsSheet;
    private final LinearLayout videoSide;
    private final LinearLayout newsRow;
    private final GridLayout videoGrid;
    private final TextView newsStatus;
    private final TextView videoStatus;
    private boolean newsOpen = false;
    private boolean videoOpen = false;
    private final Runnable idleTask = this::hideAll;

    public FeedPanels(Activity act, FrameLayout root, Handler handler, String channelUrl, Host host) {
        this.act = act;
        this.handler = handler;
        this.host = host;
        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        d = dm.density;
        heightDp = Math.round(dm.heightPixels / dm.density);
        sideW = Math.min((int) (dm.widthPixels * 0.6f), dp(460));

        // ------------------------------------------------------------ Haber paneli
        newsLayer = new FrameLayout(act);
        newsLayer.setBackgroundColor(0x99000A1E);
        newsLayer.setClickable(true);
        newsLayer.setVisibility(View.GONE);
        newsLayer.setOnClickListener(v -> hideNews());

        newsSheet = new LinearLayout(act);
        newsSheet.setOrientation(LinearLayout.VERTICAL);
        newsSheet.setBackgroundResource(R.drawable.bg_sheet);
        newsSheet.setClickable(true);
        newsSheet.setPadding(dp(16), dp(2), dp(16), dp(14));
        newsLayer.addView(newsSheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        FrameLayout grip = new FrameLayout(act);
        View bar = new View(act);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0x66FFFFFF);
        gd.setCornerRadius(dp(3));
        bar.setBackground(gd);
        grip.addView(bar, new FrameLayout.LayoutParams(dp(44), dp(5), Gravity.CENTER));
        newsSheet.addView(grip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        makeDismissable(grip, newsSheet, true);

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(act);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Son Haberler", 20, 0xFF1DE9D0, true));
        titles.addView(text("Urfa Haber", 13, 0xB3FFFFFF, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(closeButton(() -> hideNews()), new LinearLayout.LayoutParams(dp(36), dp(36)));
        newsSheet.addView(head);

        newsStatus = text("Haberler yükleniyor…", 15, 0xB3FFFFFF, false);
        newsStatus.setPadding(0, dp(18), 0, dp(18));
        newsSheet.addView(newsStatus);

        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        newsRow = new LinearLayout(act);
        newsRow.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(newsRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(10);
        newsSheet.addView(hs, hlp);

        root.addView(newsLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ------------------------------------------------------------ YouTube paneli
        videoLayer = new FrameLayout(act);
        videoLayer.setBackgroundColor(0x99000A1E);
        videoLayer.setClickable(true);
        videoLayer.setVisibility(View.GONE);
        videoLayer.setOnClickListener(v -> hideVideos());

        videoSide = new LinearLayout(act);
        videoSide.setOrientation(LinearLayout.VERTICAL);
        videoSide.setBackgroundResource(R.drawable.bg_side);
        videoSide.setClickable(true);
        videoSide.setPadding(dp(14), dp(12), dp(14), dp(12));
        videoLayer.addView(videoSide, new FrameLayout.LayoutParams(
                sideW, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));

        LinearLayout vhead = new LinearLayout(act);
        vhead.setOrientation(LinearLayout.HORIZONTAL);
        vhead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout vt = new LinearLayout(act);
        vt.setOrientation(LinearLayout.VERTICAL);
        TextView vtitle = text("▶  Ruha TV", 20, 0xFFFFFFFF, true);
        vt.addView(vtitle);
        vt.addView(text("YouTube'da son videolar", 13, 0xB3FFFFFF, false));
        vhead.addView(vt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        vhead.addView(closeButton(() -> hideVideos()), new LinearLayout.LayoutParams(dp(36), dp(36)));
        videoSide.addView(vhead);
        makeDismissable(vhead, videoSide, false);

        videoStatus = text("Videolar yükleniyor…", 15, 0xB3FFFFFF, false);
        videoStatus.setPadding(0, dp(18), 0, dp(10));
        videoSide.addView(videoStatus);

        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        videoGrid = new GridLayout(act);
        videoGrid.setColumnCount(2);
        sv.addView(videoGrid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp(8);
        videoSide.addView(sv, slp);

        TextView sub = text("YouTube'da abone ol  ›", 15, 0xFFFFFFFF, true);
        sub.setGravity(Gravity.CENTER);
        sub.setBackgroundResource(R.drawable.bg_yt);
        sub.setPadding(dp(16), dp(11), dp(16), dp(11));
        sub.setClickable(true);
        sub.setOnClickListener(v -> host.openLink(channelUrl));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(10);
        videoSide.addView(sub, blp);

        root.addView(videoLayer, new FrameLayout.LayoutParams(
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
        int cardW = dp(230);
        for (FeedData.News n : list) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(10);
            newsRow.addView(newsCard(n, cardW), lp);
        }
    }

    public void setVideos(List<FeedData.Video> list) {
        videoGrid.removeAllViews();
        if (list == null || list.isEmpty()) {
            videoStatus.setText("Videolar şu an alınamıyor");
            videoStatus.setVisibility(View.VISIBLE);
            return;
        }
        videoStatus.setVisibility(View.GONE);
        int inner = sideW - dp(28);
        int tileW = (inner - dp(16)) / 2;
        for (FeedData.Video v : list) {
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = tileW;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.setMargins(dp(4), dp(4), dp(4), dp(8));
            videoGrid.addView(videoTile(v, tileW), lp);
        }
    }

    public void setError(boolean news) {
        if (news) {
            if (newsRow.getChildCount() == 0) {
                newsStatus.setText("Haberler şu an alınamıyor");
                newsStatus.setVisibility(View.VISIBLE);
            }
        } else if (videoGrid.getChildCount() == 0) {
            videoStatus.setText("Videolar şu an alınamıyor");
            videoStatus.setVisibility(View.VISIBLE);
        }
    }

    // ================================================================ Kartlar

    private View newsCard(final FeedData.News n, int cardW) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_row);
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setOnClickListener(v -> host.openLink(n.link));

        int imgH = Math.min(130, Math.max(80, (int) (heightDp * 0.26f)));
        ImageView img = new ImageView(act);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(0xFF0B2244);
        card.addView(img, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(imgH)));
        ImageLoader.load(img, n.image, cardW);

        TextView title = text(n.title, 14, 0xFFFFFFFF, true);
        title.setMaxLines(3);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(dp(10), dp(8), dp(10), dp(4));
        card.addView(title);

        String meta = n.category.isEmpty() ? FeedData.timeAgo(n.time)
                : n.category + "  ·  " + FeedData.timeAgo(n.time);
        TextView m = text(meta, 11, 0xFF1DE9D0, false);
        m.setPadding(dp(10), 0, dp(10), dp(10));
        card.addView(m);
        return card;
    }

    private View videoTile(final FeedData.Video v, int tileW) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_row);
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setOnClickListener(x -> host.openLink(v.link));

        FrameLayout box = new FrameLayout(act);
        ImageView img = new ImageView(act);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(0xFF0B2244);
        box.addView(img, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView play = text("▶", 14, 0xFFFFFFFF, true);
        play.setGravity(Gravity.CENTER);
        play.setBackgroundResource(R.drawable.bg_circle);
        box.addView(play, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));
        card.addView(box, new LinearLayout.LayoutParams(tileW, tileW * 9 / 16));
        ImageLoader.load(img, v.thumb, tileW);

        TextView title = text(v.title, 12, 0xFFFFFFFF, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(dp(8), dp(6), dp(8), dp(2));
        card.addView(title);

        String views = FeedData.viewsText(v.views);
        String ago = FeedData.timeAgo(v.time);
        String meta = views.isEmpty() ? ago : (ago.isEmpty() ? views : views + "  ·  " + ago);
        TextView m = text(meta, 10, 0xB3FFFFFF, false);
        m.setPadding(dp(8), 0, dp(8), dp(8));
        card.addView(m);
        return card;
    }

    // ================================================================ Acma / kapama

    public boolean isOpen() {
        return newsOpen || videoOpen;
    }

    public void showNews() {
        if (videoOpen) hideVideosNow();
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

    public void showVideos() {
        if (newsOpen) hideNewsNow();
        if (videoOpen) return;
        videoOpen = true;
        videoLayer.animate().cancel();
        videoSide.animate().cancel();
        videoLayer.setAlpha(0f);
        videoLayer.setVisibility(View.VISIBLE);
        videoSide.setTranslationX(sideW);
        videoLayer.animate().alpha(1f).setDuration(180).start();
        videoSide.animate().translationX(0f).setDuration(320)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        for (int i = 0; i < videoGrid.getChildCount(); i++) {
            View c = videoGrid.getChildAt(i);
            c.animate().cancel();
            c.setAlpha(0f);
            c.setTranslationY(dp(26));
            c.animate().alpha(1f).translationY(0f)
                    .setStartDelay(150L + 55L * i).setDuration(260).start();
        }
        touch();
    }

    public void hideNews() {
        if (!newsOpen) return;
        newsOpen = false;
        refreshIdle();
        newsLayer.animate().alpha(0f).setDuration(220).withEndAction(() -> {
            if (!newsOpen) {
                newsLayer.setVisibility(View.GONE);
                newsSheet.setTranslationY(0f);
            }
        }).start();
        newsSheet.animate().translationY(dp(320)).setDuration(240).start();
    }

    public void hideVideos() {
        if (!videoOpen) return;
        videoOpen = false;
        refreshIdle();
        videoLayer.animate().alpha(0f).setDuration(220).withEndAction(() -> {
            if (!videoOpen) {
                videoLayer.setVisibility(View.GONE);
                videoSide.setTranslationX(0f);
            }
        }).start();
        videoSide.animate().translationX(sideW).setDuration(240).start();
    }

    public void hideAll() {
        hideNews();
        hideVideos();
    }

    private void hideNewsNow() {
        newsOpen = false;
        newsLayer.animate().cancel();
        newsSheet.animate().cancel();
        newsLayer.setVisibility(View.GONE);
        newsSheet.setTranslationY(0f);
    }

    private void hideVideosNow() {
        videoOpen = false;
        videoLayer.animate().cancel();
        videoSide.animate().cancel();
        videoLayer.setVisibility(View.GONE);
        videoSide.setTranslationX(0f);
    }

    /** Hicbir animasyon beklemeden her seyi kapatir. */
    public void hideNow() {
        hideNewsNow();
        hideVideosNow();
        handler.removeCallbacks(idleTask);
    }

    /** Kullanici panele dokundukca kapanma suresi yeniden baslar. */
    public void touch() {
        handler.removeCallbacks(idleTask);
        if (isOpen()) handler.postDelayed(idleTask, IDLE_MS);
    }

    private void refreshIdle() {
        handler.removeCallbacks(idleTask);
        if (isOpen()) handler.postDelayed(idleTask, IDLE_MS);
    }

    // ================================================================ Yardimcilar

    private void makeDismissable(View grip, final View panel, final boolean vertical) {
        final float[] start = new float[1];
        grip.setOnTouchListener((v, e) -> {
            float cur = vertical ? e.getRawY() : e.getRawX();
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                start[0] = cur;
                return true;
            }
            if (a == MotionEvent.ACTION_MOVE) {
                float dd = Math.max(0f, cur - start[0]);
                if (vertical) panel.setTranslationY(dd);
                else panel.setTranslationX(dd);
                return true;
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                float dd = cur - start[0];
                if (a == MotionEvent.ACTION_UP && dd > dp(60)) {
                    hideAll();
                } else {
                    panel.animate().translationX(0f).translationY(0f).setDuration(150).start();
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
