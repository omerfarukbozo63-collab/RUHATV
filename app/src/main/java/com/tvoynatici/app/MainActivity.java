package com.tvoynatici.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Rational;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.app.UiModeManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    // Uzak ayar dosyasi: icinde sadece yayin linki yazar (Gist "yayin.txt")
    private static final String CONFIG_URL =
            "https://gist.githubusercontent.com/omerfarukbozo63-collab/3f0ffd3629d37767b6034f27ae461631/raw/yayin.txt";

    // Acilis gorselinin linki: yayin.txt ile ayni Gist'teki "acilis.txt" dosyasi
    private static final String SPLASH_CFG_URL = CONFIG_URL.replace("yayin.txt", "acilis.txt");

    // Sosyal medya listesi: yayin.txt ile ayni Gist'teki "sosyal.txt" dosyasi
    private static final String SOCIAL_CFG_URL = CONFIG_URL.replace("yayin.txt", "sosyal.txt");

    // Genel bilgi ekrani metni: "bilgi.txt" dosyasi (ayri Gist)
    private static final String INFO_URL =
            "https://gist.githubusercontent.com/omerfarukbozo63-collab/6a42452f27938d3837147c39ebe42e9d/raw/bilgi.txt";

    // Ayar dosyasina ulasilamazsa kullanilacak yedek yayin linki
    private static final String DEFAULT_URL =
            "http://163.172.31.106:8080/memfs/f7ba0b87-af81-491c-ba90-cb3fb6f15314.m3u8";

    private static final String DEFAULT_INFO_TITLE = "Ruha TV";
    private static final String DEFAULT_INFO_BODY =
            "Ruha TV'ye hoş geldiniz.\n\nBu ekranın yazısı uzaktan güncellenebilir.";

    private static final long POLL_MS = 60000;     // ayar dosyalari kontrol araligi
    private static final long SPLASH_MS = 6000;    // acilis animasyonu suresi
    private static final long MAX_IMG_BYTES = 8L * 1024 * 1024;
    private static final int VOL_MAX = 63;
    private static final int VOL_STEP = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView liveBadge, infoTitle, infoBody, splashText, waitText;
    private View infoOverlay, infoCard, splashLayer, splashProgress, splashParticles, waitLayer;
    private ImageView splashImage, waitLogo;
    private ScrollView infoScroll;
    private VolumeRingView volumeRing;

    // Telefon (dokunmatik) kontrolleri
    private FrameLayout root;
    private View touchControls;
    private GridLayout aspectMenu;
    private TextView aspectChip;
    private boolean isTv = false;
    private AudioManager audio;
    private boolean controlsVisible = false;
    private int aspectIndex = 0;
    private boolean inPip = false;
    private ImageView btnMute;

    // Sosyal medya paneli
    private View socialOverlay, socialBar;
    private LinearLayout socialList;
    private boolean socialVisible = false;
    // Satir: {isim, kullanici adi, platform, link}. Uzaktan gelen liste bunun yerine gecer.
    private String[][] socialItems = {
            {"Ruha TV", "@ruhatv", "Instagram", "https://www.instagram.com/ruhatv"},
            {"Urfa Haber", "@urfa_haber", "Instagram", "https://www.instagram.com/urfa_haber/"},
            {"Ruha TV", "@ruhatvtr", "X", "https://x.com/ruhatvtr"},
            {"Urfa Haber Net", "@UrfahaberNet", "X", "https://x.com/UrfahaberNet"},
            {"Ruha TV", "Ruhatv1", "YouTube", "https://www.youtube.com/user/Ruhatv1"},
            {"Urfa Haber Net", "urfahabernet", "Facebook", "https://www.facebook.com/urfahabernet"},
    };
    private static final long SOCIAL_MS = 10000;   // panel bu sure sonra kendiliginden kapanir

    // 0: Sigdir, 1: Doldur (kirp), 2: Uzat; digerleri sabit oran
    private static final String[] ASPECT_NAMES =
            {"Sığdır", "Doldur", "Uzat", "16:9", "4:3", "21:9", "18:9", "3:2", "1:1", "5:4"};
    private static final float[] ASPECT_RATIO =
            {0f, 0f, 0f, 16f / 9f, 4f / 3f, 21f / 9f, 2f, 1.5f, 1f, 1.25f};
    private ObjectAnimator textPulse, waitSpin, waitPulse;

    private String currentUrl;
    private boolean splashDone = false;
    private boolean splashRunning = false;
    private boolean infoVisible = false;
    private boolean waitVisible = false;
    private int volLevel = VOL_MAX;
    private boolean muted = false;

    private volatile boolean fetching = false;
    private String failedSplashUrl = null;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            fetchRemote();
            handler.postDelayed(this, POLL_MS);
        }
    };
    private final Runnable showWaitTask = this::showWait;
    private final Runnable hideInfoTask = this::hideInfo;
    private final Runnable hideControlsTask = this::hideControls;
    private final Runnable hideSocialTask = this::hideSocial;
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
        waitLayer = findViewById(R.id.wait_layer);
        waitLogo = findViewById(R.id.wait_logo);
        waitText = findViewById(R.id.wait_text);
        volumeRing = findViewById(R.id.volume_ring);

        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        volLevel = prefs.getInt("vol", VOL_MAX);
        applyInfoTexts(
                prefs.getString("info_title", DEFAULT_INFO_TITLE),
                prefs.getString("info_body", DEFAULT_INFO_BODY));
        loadCustomSplash();
        setupPhoneControls();
        hideSystemUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        inPip = Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode();
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
        // Mini oynatici acikken yayin kesilmez
        if (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode()) return;
        inPip = false;
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
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    handler.removeCallbacks(showWaitTask);
                    hideWait();
                } else if (state == Player.STATE_BUFFERING) {
                    // Kisa takilmalarda bekleme ekrani cikmaz, 4 sn'den uzun surerse cikar
                    scheduleWait(4000);
                } else {
                    scheduleWait(1500);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                scheduleWait(0);
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
        hideWaitNow();
        controlsVisible = false;
        touchControls.setVisibility(View.GONE);
        socialVisible = false;
        socialOverlay.setVisibility(View.GONE);
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private void applyVolume() {
        if (player == null) return;
        float v;
        if (muted) {
            v = 0f;
        } else if (isTv) {
            v = (float) Math.pow(volLevel / (double) VOL_MAX, 1.5);   // TV: uygulama ici ses
        } else {
            v = 1f;   // Telefon: sesi telefonun ana ses seviyesi belirler
        }
        player.setVolume(v);
    }

    // ------------------------------------------------------------------ Bekleme ekrani

    private void scheduleWait(long delayMs) {
        if (waitVisible) return;
        handler.removeCallbacks(showWaitTask);
        handler.postDelayed(showWaitTask, delayMs);
    }

    private void showWait() {
        if (!splashDone || waitVisible || player == null) return;
        if (player.getPlaybackState() == Player.STATE_READY && player.getPlayerError() == null) return;
        waitVisible = true;

        waitLayer.animate().cancel();
        waitLayer.setAlpha(0f);
        waitLayer.setVisibility(View.VISIBLE);
        waitLayer.animate().alpha(1f).setDuration(350).start();

        // Donen logo: 360 derece, on yuzde yavaslayip tekrar hizlanir
        waitLogo.setCameraDistance(8000f * getResources().getDisplayMetrics().density);
        if (waitSpin != null) waitSpin.cancel();
        waitSpin = ObjectAnimator.ofFloat(waitLogo, View.ROTATION_Y, 0f, 360f);
        waitSpin.setDuration(3200);
        waitSpin.setRepeatCount(ValueAnimator.INFINITE);
        waitSpin.setInterpolator(new AccelerateDecelerateInterpolator());
        waitSpin.start();

        if (waitPulse != null) waitPulse.cancel();
        waitPulse = ObjectAnimator.ofFloat(waitText, View.ALPHA, 0.35f, 1f);
        waitPulse.setDuration(900);
        waitPulse.setRepeatCount(ValueAnimator.INFINITE);
        waitPulse.setRepeatMode(ValueAnimator.REVERSE);
        waitPulse.start();
    }

    private void hideWait() {
        if (!waitVisible) return;
        waitVisible = false;
        waitLayer.animate().cancel();
        waitLayer.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            if (!waitVisible) {
                waitLayer.setVisibility(View.GONE);
                if (waitSpin != null) waitSpin.cancel();
                if (waitPulse != null) waitPulse.cancel();
            }
        }).start();
    }

    private void hideWaitNow() {
        waitVisible = false;
        waitLayer.animate().cancel();
        waitLayer.setVisibility(View.GONE);
        if (waitSpin != null) waitSpin.cancel();
        if (waitPulse != null) waitPulse.cancel();
    }

    // ------------------------------------------------------------------ Acilis

    // Uzaktan indirilmis acilis gorseli varsa onu, yoksa uygulamanin kendi gorselini kullanir
    private void loadCustomSplash() {
        File f = new File(getFilesDir(), "acilis.img");
        if (!f.exists()) return;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0) return;
            int sample = 1;
            while (o.outWidth / sample > 2400) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(f.getPath(), o2);
            if (bmp != null) splashImage.setImageBitmap(bmp);
        } catch (Throwable ignored) {
            // Bozuk dosya olursa varsayilan gorsel kalir
        }
    }

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
            // Yayin acilis bitince hala hazir degilse bekleme ekranini goster
            if (player.getPlaybackState() != Player.STATE_READY) scheduleWait(1200);
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
        if (isTv || audio == null) {
            if (code == KeyEvent.KEYCODE_VOLUME_MUTE) {
                muted = !muted;
            } else if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                muted = false;
                volLevel = Math.min(VOL_MAX, volLevel + VOL_STEP);
            } else {
                volLevel = Math.max(0, volLevel - VOL_STEP);
            }
            prefs.edit().putInt("vol", volLevel).apply();
        } else {
            // Telefon: gercek medya sesini degistir
            try {
                if (code == KeyEvent.KEYCODE_VOLUME_MUTE) {
                    muted = !muted;
                } else if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                    muted = false;
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                } else {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                }
            } catch (Exception ignored) {
                // Sistem izin vermezse sessizce gec
            }
        }
        applyVolume();
        updateMuteIcon();
        showVolume();
    }

    // Gosterilecek ses seviyesi (0-63)
    private int currentLevel() {
        if (isTv || audio == null) return volLevel;
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        return Math.round(cur * (float) VOL_MAX / max);
    }

    private void showVolume() {
        volumeRing.setLevel(muted ? 0 : currentLevel(), muted);
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

        if (socialVisible && down && code == KeyEvent.KEYCODE_BACK) {
            hideSocial();
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
        if (CONFIG_URL.isEmpty() || fetching) return;
        fetching = true;
        new Thread(() -> {
            try {
                fetchRemoteBlocking();
            } finally {
                fetching = false;
            }
        }).start();
    }

    private void fetchRemoteBlocking() {
        // 1) Yayin linki
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

        // 2) Genel bilgi metni
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

        // 4) Sosyal medya listesi
        fetchSocialBlocking();

        // 3) Acilis gorseli (bir sonraki acilista gorunur)
        String sp = httpGet(SPLASH_CFG_URL);
        if (sp != null) {
            String first = null;
            for (String line : sp.split("\n")) {
                line = line.replace("\uFEFF", "").trim();
                if (!line.isEmpty()) {
                    first = line;
                    break;
                }
            }
            if (first != null) {
                String saved = prefs.getString("splash_url", "");
                if (first.equalsIgnoreCase("yok") || first.equalsIgnoreCase("varsayilan")) {
                    if (!saved.isEmpty()) {
                        new File(getFilesDir(), "acilis.img").delete();
                        prefs.edit().remove("splash_url").apply();
                    }
                } else if (first.startsWith("http")
                        && !first.equals(saved) && !first.equals(failedSplashUrl)) {
                    if (downloadSplash(first)) {
                        prefs.edit().putString("splash_url", first).apply();
                    } else {
                        failedSplashUrl = first;
                    }
                }
            }
        }
    }

    private void fetchSocialBlocking() {
        String so = httpGet(SOCIAL_CFG_URL);
        if (so == null) return;
        final String raw = so.trim();
        final String[][] parsed = parseSocial(raw);
        if (parsed.length == 0) return;
        handler.post(() -> {
            if (raw.equals(prefs.getString("social", ""))) return;
            prefs.edit().putString("social", raw).apply();
            socialItems = parsed;
            if (!isTv && socialList != null) buildSocialRows();
        });
    }

    private boolean downloadSplash(String urlStr) {
        HttpURLConnection c = null;
        File tmp = new File(getFilesDir(), "acilis.tmp");
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            if (c.getResponseCode() != 200) return false;
            if (c.getContentLength() > MAX_IMG_BYTES) return false;
            long total = 0;
            try (InputStream in = c.getInputStream();
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_IMG_BYTES) return false;
                    out.write(buf, 0, n);
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;   // gecerli resim degil
            File dst = new File(getFilesDir(), "acilis.img");
            if (dst.exists()) dst.delete();
            return tmp.renameTo(dst);
        } catch (Exception ex) {
            return false;
        } finally {
            if (c != null) c.disconnect();
            if (tmp.exists()) tmp.delete();
        }
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

    // ------------------------------------------------------------------ Telefon kontrolleri

    private void setupPhoneControls() {
        UiModeManager um = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        PackageManager pm = getPackageManager();
        isTv = (um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        root = findViewById(R.id.root);
        touchControls = findViewById(R.id.touch_controls);
        aspectMenu = findViewById(R.id.aspect_menu);
        aspectChip = findViewById(R.id.aspect_chip);
        btnMute = findViewById(R.id.btn_mute);
        socialOverlay = findViewById(R.id.social_overlay);
        socialBar = findViewById(R.id.social_bar);
        socialList = findViewById(R.id.social_list);

        volumeRing.setOnLevelChangeListener(new VolumeRingView.Listener() {
            @Override
            public void onLevelChanged(int level) {
                muted = false;
                if (isTv || audio == null) {
                    volLevel = level;
                    prefs.edit().putInt("vol", level).apply();
                } else {
                    try {
                        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                        int idx = Math.round(level * (float) max / VOL_MAX);
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0);
                    } catch (Exception ignored) {
                        // Sistem izin vermezse sessizce gec
                    }
                }
                applyVolume();
                updateMuteIcon();
            }

            @Override
            public void onTouchChanged(boolean down) {
                handler.removeCallbacks(hideVolumeTask);
                if (!down) handler.postDelayed(hideVolumeTask, 1800);
            }
        });

        if (isTv) return;   // Asagidakiler sadece telefon/tablet icin

        aspectIndex = Math.max(0, Math.min(ASPECT_NAMES.length - 1, prefs.getInt("aspect", 0)));
        aspectChip.setText(ASPECT_NAMES[aspectIndex] + " ▾");

        for (int i = 0; i < ASPECT_NAMES.length; i++) {
            final int idx = i;
            TextView item = new TextView(this);
            item.setText(ASPECT_NAMES[i]);
            item.setTextSize(15);
            item.setTextColor(0xFFFFFFFF);
            item.setGravity(Gravity.CENTER);
            item.setMinWidth((int) dp(92));
            item.setPadding((int) dp(14), (int) dp(9), (int) dp(14), (int) dp(9));
            item.setClickable(true);
            item.setOnClickListener(v -> {
                applyAspect(idx);
                aspectMenu.setVisibility(View.GONE);
                scheduleHideControls();
            });
            aspectMenu.addView(item, new GridLayout.LayoutParams());
        }
        refreshAspectMenu();

        aspectChip.setOnClickListener(v -> {
            aspectMenu.setVisibility(
                    aspectMenu.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            scheduleHideControls();
        });
        findViewById(R.id.btn_info).setOnClickListener(v -> {
            hideControls();
            showInfo();
        });
        findViewById(R.id.btn_volume).setOnClickListener(v -> {
            showVolume();
            scheduleHideControls();
        });

        // Sesi tamamen kapat / ac
        btnMute.setOnClickListener(v -> {
            muted = !muted;
            applyVolume();
            updateMuteIcon();
            showVolume();
            scheduleHideControls();
        });
        updateMuteIcon();

        // Mini oynatici (Android 8+)
        View btnPip = findViewById(R.id.btn_pip);
        if (Build.VERSION.SDK_INT < 26) {
            btnPip.setVisibility(View.GONE);
        } else {
            btnPip.setOnClickListener(v -> {
                hideControls();
                enterPip();
            });
        }

        // Sosyal medya paneli
        findViewById(R.id.btn_social).setOnClickListener(v -> {
            hideControls();
            showSocial();
        });
        findViewById(R.id.social_wrap).setOnClickListener(v -> hideSocial());
        String cachedSocial = prefs.getString("social", "");
        if (!cachedSocial.isEmpty()) {
            String[][] parsed = parseSocial(cachedSocial);
            if (parsed.length > 0) socialItems = parsed;
        }
        buildSocialRows();

        // Bilgi ekrani: telefona gore boyut, kapat dugmesi, dokununca kapanma
        FrameLayout.LayoutParams ilp = (FrameLayout.LayoutParams) infoCard.getLayoutParams();
        ilp.setMargins((int) dp(16), (int) dp(36), (int) dp(16), (int) dp(36));
        infoCard.setLayoutParams(ilp);
        infoCard.setPadding((int) dp(20), (int) dp(16), (int) dp(20), (int) dp(14));
        infoTitle.setTextSize(24);
        infoBody.setTextSize(17);
        ((TextView) findViewById(R.id.info_hint)).setText("Kaydırmak için sürükleyin");
        View infoLogo = findViewById(R.id.info_logo);
        float heightDp = getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density;
        if (heightDp < 500) {
            infoLogo.setVisibility(View.GONE);   // yatay telefonda metne yer ac
        } else {
            infoLogo.getLayoutParams().height = (int) dp(44);
        }
        View infoClose = findViewById(R.id.info_close);
        infoClose.setVisibility(View.VISIBLE);
        infoClose.setOnClickListener(v -> hideInfo());
        infoOverlay.setOnClickListener(v -> hideInfo());

        root.setOnClickListener(v -> {
            if (!splashDone) return;
            if (infoVisible) {
                hideInfo();
            } else if (aspectMenu.getVisibility() == View.VISIBLE) {
                aspectMenu.setVisibility(View.GONE);
                scheduleHideControls();
            } else if (controlsVisible) {
                hideControls();
            } else {
                showControls();
            }
        });

        root.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if ((r - l) != (oldR - oldL) || (b - t) != (oldB - oldT)) applyAspectLayout(aspectIndex);
        });
    }

    private void showControls() {
        controlsVisible = true;
        touchControls.animate().cancel();
        touchControls.setAlpha(0f);
        touchControls.setVisibility(View.VISIBLE);
        touchControls.animate().alpha(1f).setDuration(200).start();
        scheduleHideControls();
    }

    private void hideControls() {
        controlsVisible = false;
        handler.removeCallbacks(hideControlsTask);
        aspectMenu.setVisibility(View.GONE);
        touchControls.animate().cancel();
        touchControls.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            if (!controlsVisible) touchControls.setVisibility(View.GONE);
        }).start();
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsTask);
        handler.postDelayed(hideControlsTask, 4000);
    }

    // 0: sigdir, 1: doldur(kirp), 2: uzat, 3+: sabit oran
    private void applyAspect(int idx) {
        aspectIndex = idx;
        prefs.edit().putInt("aspect", idx).apply();
        aspectChip.setText(ASPECT_NAMES[idx] + " ▾");
        applyAspectLayout(idx);
    }

    private void applyAspectLayout(int idx) {
        if (inPip) idx = 0;   // mini oynaticida her zaman sigdir

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) playerView.getLayoutParams();
        lp.gravity = Gravity.CENTER;
        if (idx == 0) {
            playerView.setResizeMode(0);   // FIT
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else if (idx == 1) {
            playerView.setResizeMode(4);   // ZOOM
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else if (idx == 2) {
            playerView.setResizeMode(3);   // FILL (uzat)
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            int rw = root.getWidth();
            int rh = root.getHeight();
            if (rw > 0 && rh > 0) {
                float ratio = ASPECT_RATIO[idx];
                int w;
                int h;
                if ((float) rw / rh > ratio) {
                    h = rh;
                    w = Math.round(h * ratio);
                } else {
                    w = rw;
                    h = Math.round(w / ratio);
                }
                lp.width = w;
                lp.height = h;
            }
            playerView.setResizeMode(3);
        }
        playerView.setLayoutParams(lp);
        refreshAspectMenu();
    }

    private void refreshAspectMenu() {
        for (int i = 0; i < aspectMenu.getChildCount(); i++) {
            TextView t = (TextView) aspectMenu.getChildAt(i);
            t.setTextColor(i == aspectIndex ? 0xFF1DE9D0 : 0xFFFFFFFF);
            t.setTypeface(null, i == aspectIndex ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
        }
    }

    // ------------------------------------------------------------------ Mini oynatici

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        enterPip();   // uygulamadan cikinca (ana ekran tusu vb.) mini oynaticiya gec
    }

    private void enterPip() {
        if (isTv || Build.VERSION.SDK_INT < 26 || !splashDone || player == null || inPip) return;
        try {
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .build();
            enterPictureInPictureMode(params);
        } catch (Throwable ignored) {
            // Cihaz desteklemiyorsa ya da kullanici izin vermediyse sessizce gec
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPip, newConfig);
        inPip = isInPip;
        if (isTv) return;
        if (isInPip) {
            handler.removeCallbacks(hideControlsTask);
            controlsVisible = false;
            touchControls.setVisibility(View.GONE);
            aspectMenu.setVisibility(View.GONE);
            infoVisible = false;
            infoOverlay.setVisibility(View.GONE);
            hideSocialNow();
            volumeRing.setAlpha(0f);
            liveBadge.setVisibility(View.GONE);
        }
        applyAspectLayout(aspectIndex);
    }

    // ------------------------------------------------------------------ Ses kapat ikonu

    private void updateMuteIcon() {
        if (btnMute == null) return;
        btnMute.setColorFilter(muted ? 0xFF1DE9D0 : 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ Sosyal medya

    private int iconFor(String platform) {
        String p = platform.toLowerCase();
        if (p.contains("insta")) return R.drawable.ic_instagram;
        if (p.equals("x") || p.contains("twitter")) return R.drawable.ic_x;
        if (p.contains("youtube")) return R.drawable.ic_youtube;
        if (p.contains("facebook")) return R.drawable.ic_facebook;
        return R.drawable.ic_heart;
    }

    // sosyal.txt satirlari: "Isim | @kullanici | Platform | link"  ya da sadece "link"
    private String[][] parseSocial(String text) {
        java.util.ArrayList<String[]> out = new java.util.ArrayList<>();
        for (String line : text.replace("\uFEFF", "").replace("\r", "").split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\|");
            for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
            String url = parts[parts.length - 1];
            if (!url.startsWith("http")) continue;
            String name;
            String handle;
            String platform;
            if (parts.length >= 4) {
                name = parts[0];
                handle = parts[1];
                platform = parts[2];
            } else {
                String host = url.replaceFirst("^https?://(www\\.)?", "");
                String last = host.contains("/") ? host.substring(host.indexOf('/') + 1) : "";
                last = last.replaceAll("/+$", "");
                if (last.contains("/")) last = last.substring(last.lastIndexOf('/') + 1);
                platform = host.startsWith("instagram") ? "Instagram"
                        : host.startsWith("x.com") || host.startsWith("twitter") ? "X"
                        : host.startsWith("youtube") ? "YouTube"
                        : host.startsWith("facebook") ? "Facebook" : "Web";
                handle = last.isEmpty() ? host : "@" + last;
                name = last.isEmpty() ? host : last;
            }
            out.add(new String[]{name, handle, platform, url});
            if (out.size() >= 12) break;
        }
        return out.toArray(new String[0][]);
    }

    private void buildSocialRows() {
        String[][] items = socialItems;
        socialList.removeAllViews();

        for (int i = 0; i < items.length; i++) {
            final String url = items[i][3];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_row);
            row.setPadding((int) dp(12), (int) dp(10), (int) dp(14), (int) dp(10));
            row.setClickable(true);
            row.setOnClickListener(v -> openLink(url));

            ImageView ic = new ImageView(this);
            ic.setImageResource(iconFor(items[i][2]));
            row.addView(ic, new LinearLayout.LayoutParams((int) dp(40), (int) dp(40)));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.leftMargin = (int) dp(14);

            TextView name = new TextView(this);
            name.setText(items[i][0]);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(17);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            col.addView(name);

            TextView sub = new TextView(this);
            sub.setText(items[i][1] + "  ·  " + items[i][2]);
            sub.setTextColor(0xB3FFFFFF);
            sub.setTextSize(13);
            col.addView(sub);
            row.addView(col, clp);

            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(0xFF1DE9D0);
            arrow.setTextSize(28);
            row.addView(arrow);

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = (int) dp(10);
            socialList.addView(row, rlp);
        }
    }

    private void openLink(String url) {
        hideSocial();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            // Linki acacak uygulama ya da tarayici yoksa sessizce gec
        }
    }

    private void showSocial() {
        if (socialVisible || !splashDone) return;
        socialVisible = true;
        socialOverlay.animate().cancel();
        socialOverlay.setAlpha(0f);
        socialOverlay.setVisibility(View.VISIBLE);
        socialOverlay.animate().alpha(1f).setDuration(250).start();

        // Satirlar sirayla asagidan yukari belirir
        for (int i = 0; i < socialList.getChildCount(); i++) {
            View c = socialList.getChildAt(i);
            c.animate().cancel();
            c.setAlpha(0f);
            c.setTranslationY(dp(22));
            c.animate().alpha(1f).translationY(0f)
                    .setStartDelay(120L + 80L * i).setDuration(280).start();
        }
        restartSocialTimer();
    }

    private void restartSocialTimer() {
        handler.removeCallbacks(hideSocialTask);
        socialBar.animate().cancel();
        socialBar.setPivotX(0f);
        socialBar.setScaleX(1f);
        socialBar.animate().scaleX(0f).setDuration(SOCIAL_MS)
                .setInterpolator(new LinearInterpolator()).start();
        handler.postDelayed(hideSocialTask, SOCIAL_MS);
    }

    private void hideSocial() {
        if (!socialVisible) return;
        socialVisible = false;
        handler.removeCallbacks(hideSocialTask);
        socialBar.animate().cancel();
        socialOverlay.animate().cancel();
        socialOverlay.animate().alpha(0f).setDuration(220).withEndAction(() -> {
            if (!socialVisible) socialOverlay.setVisibility(View.GONE);
        }).start();
    }

    private void hideSocialNow() {
        socialVisible = false;
        handler.removeCallbacks(hideSocialTask);
        socialBar.animate().cancel();
        socialOverlay.animate().cancel();
        socialOverlay.setVisibility(View.GONE);
    }

    // Panel acikken ya da menu acikken dokunus olursa kapanma suresi yeniden baslar
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (socialVisible) restartSocialTimer();
            if (infoVisible && !isTv) {
                handler.removeCallbacks(hideInfoTask);
                handler.postDelayed(hideInfoTask, 30000);
            }
            if (controlsVisible) scheduleHideControls();
        }
        return super.dispatchTouchEvent(ev);
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
