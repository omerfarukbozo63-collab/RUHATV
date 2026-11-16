package com.tvoynatici.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    // Uzak ayar dosyasi: icinde sadece yayin linki yazar (Gist "yayin.txt")
    private static final String CONFIG_URL =
            "https://gist.githubusercontent.com/omerfarukbozo63-collab/3f0ffd3629d37767b6034f27ae461631/raw/yayin.txt";

    // Genel bilgi ekrani metni: "bilgi.txt" dosyasi (ayri Gist)
    private static final String INFO_URL =
            "https://gist.githubusercontent.com/omerfarukbozo63-collab/6a42452f27938d3837147c39ebe42e9d/raw/bilgi.txt";

    // Ayar dosyasina ulasilamazsa kullanilacak yedek yayin linki
    private static final String DEFAULT_URL =
            "http://163.172.31.106:8080/memfs/f7ba0b87-af81-491c-ba90-cb3fb6f15314.m3u8";

    private static final String DEFAULT_INFO_TITLE = "Ruha TV";
    private static final String DEFAULT_INFO_BODY =
            "Ruha TV'ye hoş geldiniz.\n\nBu ekranın yazısı uzaktan güncellenebilir.";

    private static final long POLL_MS = 60000;     // ayar dosyasi kontrol araligi
    private static final long SPLASH_MS = 6000;    // acilis animasyonu suresi
    private static final int VOL_MAX = 63;
    private static final int VOL_STEP = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView liveBadge, statusMsg, infoTitle, infoBody, splashText;
    private View infoOverlay, infoCard, splashLayer, splashImage, splashProgress, splashParticles;
    private ScrollView infoScroll;
    private VolumeRingView volumeRing;
    private ObjectAnimator textPulse;

    private String currentUrl;
    private boolean splashDone = false;
    private boolean splashRunning = false;
    private boolean infoVisible = false;
    private int volLevel = VOL_MAX;
    private boolean muted = false;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            fetchRemote();
            handler.postDelayed(this, POLL_MS);
        }
    };
    private final Runnable hideInfoTask = this::hideInfo;
    private final Runnable hideVolumeTask = () ->
            volumeRing.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(350).start();
    private final Runnable hideLiveTask = () ->
            liveBadge.animate().setStartDelay(0).alpha(0f).setDuration(800)
                    .withEndAction(() -> liveBadge.setVisibility(View.GONE)).start();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view);
        liveBadge = findViewById(R.id.live_badge);
        statusMsg = findViewById(R.id.status_msg);
        infoOverlay = findViewById(R.id.info_overlay);
        infoCard = findViewById(R.id.info_card);
        infoTitle = findViewById(R.id.info_title);
        infoBody = findViewById(R.id.info_body);
        infoScroll = findViewById(R.id.info_scroll);
        splashLayer = findViewById(R.id.splash_layer);
        splashImage = findViewById(R.id.splash_image);
        splashProgress = findViewById(R.id.splash_progress);
        splashParticles = findViewById(R.id.splash_particles);
        splashText = findViewById(R.id.splash_text);
        volumeRing = findViewById(R.id.volume_ring);

        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        volLevel = prefs.getInt("vol", VOL_MAX);
        applyInfoTexts(
                prefs.getString("info_title", DEFAULT_INFO_TITLE),
                prefs.getString("info_body", DEFAULT_INFO_BODY));
        hideSystemUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startPlayer();
        if (splashDone) {
            splashLayer.setVisibility(View.GONE);
        } else {
            runSplash();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPlayer();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    // ------------------------------------------------------------------ Oynatici

    private void startPlayer() {
        if (player != null) return;

        currentUrl = prefs.getString("url", DEFAULT_URL);

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android TV) TVOynatici/1.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .build();
        playerView.setPlayer(player);
        applyVolume();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) hideStatus();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (splashDone) showStatus("Bağlantı yeniden deneniyor…");
                handler.postDelayed(() -> {
                    if (player != null) {
                        player.setMediaItem(buildItem(currentUrl));
                        player.prepare();
                        player.setPlayWhenReady(splashDone);
                    }
                }, 3000);
            }
        });

        // Acilis animasyonu bitene kadar yayin arka planda hazirlanir ama oynatilmaz
        player.setMediaItem(buildItem(currentUrl));
        player.setPlayWhenReady(splashDone);
        player.prepare();

        handler.post(pollTask);
    }

    private MediaItem buildItem(String url) {
        return new MediaItem.Builder()
                .setUri(url)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build();
    }

    private void stopPlayer() {
        handler.removeCallbacksAndMessages(null);
        splashRunning = false;
        if (textPulse != null) textPulse.cancel();
        splashProgress.animate().cancel();
        splashImage.animate().cancel();
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private void applyVolume() {
        if (player == null) return;
        float v = muted ? 0f : (float) Math.pow(volLevel / (double) VOL_MAX, 1.5);
        player.setVolume(v);
    }

    // ------------------------------------------------------------------ Acilis

    private void runSplash() {
        if (splashRunning) return;
        splashRunning = true;

        splashLayer.setAlpha(1f);
        splashLayer.setVisibility(View.VISIBLE);
        splashParticles.setVisibility(View.VISIBLE);

        // Gorsel yavasca belirir ve 6 sn boyunca yavasca yakinlasir
        splashImage.setAlpha(0f);
        splashImage.setScaleX(1f);
        splashImage.setScaleY(1f);
        ObjectAnimator.ofFloat(splashImage, View.ALPHA, 0f, 1f).setDuration(900).start();
        splashImage.animate().scaleX(1.12f).scaleY(1.12f)
                .setDuration(SPLASH_MS + 800).setInterpolator(new LinearInterpolator()).start();

        // Alttaki turkuaz ilerleme cubugu 6 sn'de dolar
        splashProgress.setPivotX(0f);
        splashProgress.setScaleX(0f);
        splashProgress.animate().scaleX(1f)
                .setDuration(SPLASH_MS).setInterpolator(new LinearInterpolator()).start();

        // "Yayin hazirlaniyor" yazisi nabiz gibi yanip soner
        if (textPulse != null) textPulse.cancel();
        textPulse = ObjectAnimator.ofFloat(splashText, View.ALPHA, 0.35f, 1f);
        textPulse.setDuration(900);
        textPulse.setRepeatCount(ValueAnimator.INFINITE);
        textPulse.setRepeatMode(ValueAnimator.REVERSE);
        textPulse.start();

        handler.postDelayed(this::endSplash, SPLASH_MS);
    }

    private void endSplash() {
        splashRunning = false;
        splashDone = true;
        if (textPulse != null) textPulse.cancel();
        if (player != null) {
            applyVolume();
            player.setPlayWhenReady(true);
        }
        splashLayer.animate().alpha(0f).setDuration(800).withEndAction(() -> {
            splashLayer.setVisibility(View.GONE);
            splashParticles.setVisibility(View.GONE);
        }).start();
        showLiveBadge();
    }

    private void showLiveBadge() {
        liveBadge.animate().cancel();
        liveBadge.setVisibility(View.VISIBLE);
        liveBadge.setAlpha(0f);
        liveBadge.animate().setStartDelay(0).alpha(1f).setDuration(500).start();
        handler.removeCallbacks(hideLiveTask);
        handler.postDelayed(hideLiveTask, 6500);
    }

    // ------------------------------------------------------------------ Durum mesaji

    private void showStatus(String msg) {
        statusMsg.setText(msg);
        statusMsg.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        statusMsg.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ Bilgi ekrani

    private void applyInfoTexts(String title, String body) {
        infoTitle.setText(title);
        infoBody.setText(body);
    }

    private void showInfo() {
        if (infoVisible || !splashDone) return;
        infoVisible = true;
        infoScroll.scrollTo(0, 0);
        infoOverlay.animate().cancel();
        infoOverlay.setAlpha(0f);
        infoOverlay.setVisibility(View.VISIBLE);
        infoOverlay.animate().alpha(1f).setDuration(250).start();
        infoCard.setTranslationY(dp(30));
        infoCard.animate().translationY(0f).setDuration(300).start();
        handler.removeCallbacks(hideInfoTask);
        handler.postDelayed(hideInfoTask, 30000);
    }

    private void hideInfo() {
        if (!infoVisible) return;
        infoVisible = false;
        handler.removeCallbacks(hideInfoTask);
        infoOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            if (!infoVisible) infoOverlay.setVisibility(View.GONE);
        }).start();
    }

    // ------------------------------------------------------------------ Ses

    private void handleVolumeKey(int code) {
        if (code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            muted = !muted;
        } else if (code == KeyEvent.KEYCODE_VOLUME_UP) {
            muted = false;
            volLevel = Math.min(VOL_MAX, volLevel + VOL_STEP);
        } else {
            volLevel = Math.max(0, volLevel - VOL_STEP);
        }
        prefs.edit().putInt("vol", volLevel).apply();
        applyVolume();
        showVolume();
    }

    private void showVolume() {
        volumeRing.setLevel(muted ? 0 : volLevel, muted);
        volumeRing.animate().cancel();
        if (volumeRing.getAlpha() < 0.99f) {
            volumeRing.setScaleX(0.85f);
            volumeRing.setScaleY(0.85f);
        }
        volumeRing.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
        handler.removeCallbacks(hideVolumeTask);
        handler.postDelayed(hideVolumeTask, 1800);
    }

    // ------------------------------------------------------------------ Tuslar

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_VOLUME_UP
                || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (down) handleVolumeKey(code);
            return true;
        }

        if (!splashDone) return super.dispatchKeyEvent(e);

        boolean okKey = code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                || code == KeyEvent.KEYCODE_MENU
                || code == KeyEvent.KEYCODE_INFO;

        if (infoVisible) {
            if (down) {
                if (code == KeyEvent.KEYCODE_BACK || (okKey && e.getRepeatCount() == 0)) {
                    hideInfo();
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    infoScroll.smoothScrollBy(0, (int) dp(120));
                    handler.removeCallbacks(hideInfoTask);
                    handler.postDelayed(hideInfoTask, 30000);
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    infoScroll.smoothScrollBy(0, -(int) dp(120));
                    handler.removeCallbacks(hideInfoTask);
                    handler.postDelayed(hideInfoTask, 30000);
                }
            }
            return true;
        }

        if (okKey) {
            if (down && e.getRepeatCount() == 0) showInfo();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ------------------------------------------------------------------ Uzaktan ayar

    private void fetchRemote() {
        if (CONFIG_URL.isEmpty()) return;
        new Thread(() -> {
            String cfg = httpGet(CONFIG_URL);
            if (cfg != null) {
                String link = null;
                for (String line : cfg.split("\n")) {
                    line = line.replace("\uFEFF", "").trim();
                    if (line.startsWith("http")) {
                        link = line;
                        break;
                    }
                }
                final String newUrl = link;
                if (newUrl != null) handler.post(() -> applyUrl(newUrl));
            }

            String info = httpGet(INFO_URL);
            if (info != null) {
                info = info.replace("\uFEFF", "").replace("\r", "").trim();
                if (!info.isEmpty()) {
                    String title;
                    String body;
                    int nl = info.indexOf('\n');
                    if (nl < 0) {
                        title = "Genel Bilgi";
                        body = info;
                    } else {
                        title = info.substring(0, nl).trim();
                        body = info.substring(nl + 1).trim();
                    }
                    final String t = title;
                    final String b = body;
                    handler.post(() -> {
                        prefs.edit().putString("info_title", t).putString("info_body", b).apply();
                        applyInfoTexts(t, b);
                    });
                }
            }
        }).start();
    }

    private String httpGet(String urlStr) {
        HttpURLConnection c = null;
        try {
            String u = urlStr + (urlStr.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            c.setRequestProperty("Cache-Control", "no-cache");
            if (c.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null && sb.length() < 20000) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception ex) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void applyUrl(String url) {
        if (player == null || url.equals(currentUrl)) return;
        currentUrl = url;
        prefs.edit().putString("url", url).apply();
        player.setMediaItem(buildItem(url));
        player.prepare();
        player.setPlayWhenReady(splashDone);
        if (splashDone) showLiveBadge();
    }

    // ------------------------------------------------------------------ Yardimcilar

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
