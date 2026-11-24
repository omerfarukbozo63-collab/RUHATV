package com.tvoynatici.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.Manifest;
import android.app.Activity;
import android.graphics.Color;
import android.view.TextureView;
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

    // Uzak ayar dosyalari (ruhatv.com.tr)
    private static final String BASE = "https://ruhatv.com.tr/";
    private static final String CONFIG_URL = BASE + "yayin.txt";          // Ruha TV yayin linki
    private static final String RADIO_CFG_URL = BASE + "radyoruha.txt";       // Radyo Ruha yayin linki
    private static final String RADIO_LOGO_CFG_URL = BASE + "radyoruhalogo.txt";   // radyo logosunun linki
    private static final String SPLASH_CFG_URL = BASE + "acilis.txt";     // acilis gorseli linki
    private static final String SOCIAL_CFG_URL = BASE + "sosyal.txt";     // sosyal medya listesi
    private static final String INFO_URL = BASE + "bilgi.txt";            // genel bilgi metni

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
    private String pendingLink = null;   // bildirimden gelen, acilistan sonra acilacak link

    // Haber / YouTube akislari (sadece telefon)
    private static final String NEWS_RSS_URL = "https://www.urfahaber.com/rss.xml";
    private static final long FEED_POLL_MS = 5 * 60 * 1000;
    private static final long FEED_RETRY_MS = 60 * 1000;
    private long newsNext = 0;
    private volatile int lastFeedCode = 0;
    private android.animation.ObjectAnimator arrowNewsAnim;
    private java.util.List<FeedData.News> tickerNews = new java.util.ArrayList<>();
    private int tickerIdx = 0;
    private final Runnable tickerTask = new Runnable() {
        @Override
        public void run() {
            tickerStep();
            handler.postDelayed(this, TICKER_MS);
        }
    };
    private static final long TICKER_MS = 6000;
    private FeedPanels panels;
    private float gDownX, gDownY;
    private boolean gTracking = false;

    // Isikli cerceve
    private GlowView glowView;
    private TextView glowItem;
    private boolean glowOn = true;
    private Bitmap glowBmp;
    private final Runnable glowTask = new Runnable() {
        @Override
        public void run() {
            sampleGlow();
            handler.postDelayed(this, 450);
        }
    };
    private ImageView btnMute;

    // Sosyal medya paneli
    private View socialOverlay, socialBar;
    private android.widget.GridLayout socialList;
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
    private int profile = -1;                 // -1: secilmedi, 0: Ruha TV, 1: Radyo Ruha
    private boolean pickerShowing = false;
    private boolean notifAsked = false;
    private ProfilePicker picker;
    private RadioView radioView;
    private static java.lang.ref.WeakReference<MainActivity> sInstance;
    private boolean radioBackground = false;     // radyo arka planda caliyor
    private boolean radioStoppedByUser = false;  // bildirimden durduruldu
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
        sInstance = new java.lang.ref.WeakReference<>(this);

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
        setupProfiles();
        setupNotifications();
        readLinkFromIntent(getIntent());
        hideSystemUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        inPip = Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode();
        if (radioBackground) {
            radioBackground = false;
            stopService(new Intent(this, RadioService.class));
        }
        if (radioStoppedByUser) {
            // Bildirimden "Durdur"a basilmisti: profil secimine don
            radioStoppedByUser = false;
            profile = -1;
            splashDone = false;
            pickerShowing = true;
            radioView.setVisibility(View.GONE);
            picker.show();
        }
        startPlayer();
        if (splashDone) {
            splashLayer.setVisibility(View.GONE);
        } else if (!pickerShowing) {
            runSplash();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Mini oynatici acikken yayin kesilmez
        if (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode()) return;
        inPip = false;
        // Radyo calarken ekran kapansa ya da baska uygulamaya gecilse de yayin surer
        if (profile == 1 && splashDone && player != null && !pickerShowing) {
            radioBackground = true;
            try {
                Intent si = new Intent(this, RadioService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(si); else startService(si);
                controlsVisible = false;
                touchControls.setVisibility(View.GONE);
                if (panels != null) panels.hideNow();
                return;
            } catch (Throwable t) {
                radioBackground = false;   // servis baslamazsa normal davran: yayini durdur
            }
        }
        stopPlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (radioBackground) stopService(new Intent(this, RadioService.class));
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        if (sInstance != null && sInstance.get() == this) sInstance = null;
    }

    /** Bildirimdeki "Durdur" dugmesi. */
    static void stopRadioFromNotification() {
        MainActivity a = sInstance == null ? null : sInstance.get();
        if (a == null) return;
        a.radioBackground = false;
        a.radioStoppedByUser = true;
        a.stopPlayer();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    // ------------------------------------------------------------------ Oynatici

    private void startPlayer() {
        if (player != null) return;

        currentUrl = urlFor(Math.max(profile, 0));

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android TV) TVOynatici/1.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
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
                    if (player != null && profile >= 0 && !currentUrl.isEmpty()) {
                        player.setMediaItem(buildItem(currentUrl));
                        player.prepare();
                        player.setPlayWhenReady(splashDone);
                    }
                }, 3000);
            }
        });

        // Acilis animasyonu bitene kadar yayin arka planda hazirlanir ama oynatilmaz
        if (profile >= 0 && !currentUrl.isEmpty()) {
            player.setMediaItem(buildItem(currentUrl));
            player.setPlayWhenReady(splashDone);
            player.prepare();
        }

        handler.post(pollTask);
        updateGlowState();
    }

    private MediaItem buildItem(String url) {
        MediaItem.Builder b = new MediaItem.Builder().setUri(url);
        // TV yayini HLS; radyo mp3/aac/HLS olabilir, bicimi kendisi bulsun
        if (profile != 1 || url.toLowerCase().contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8);
        return b.build();
    }

    private String urlFor(int prof) {
        return prof == 1 ? prefs.getString("url_radio", "") : prefs.getString("url", DEFAULT_URL);
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
        if (panels != null) panels.hideNow();
        stopArrows();
        if (glowView != null) glowView.setVisibility(View.GONE);
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
        if (textPulse != null) textPulse.cancel();
        // Acilis bitti: profil secimi gelir. Yayin, secimden sonra baslar.
        pickerShowing = true;
        picker.show();
        splashLayer.animate().alpha(0f).setDuration(800).withEndAction(() -> {
            splashLayer.setVisibility(View.GONE);
            splashParticles.setVisibility(View.GONE);
        }).start();
    }

    // ------------------------------------------------------------------ Profiller (Ruha TV / Radyo Ruha)

    private void setupProfiles() {
        FrameLayout r = findViewById(R.id.root);
        radioView = new RadioView(this);
        r.addView(radioView, r.indexOfChild(playerView) + 1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        picker = new ProfilePicker(this, !isTv, this::chooseProfile);
        r.addView(picker, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadRadioLogo();

        View sw = findViewById(R.id.btn_profile);
        if (sw != null) sw.setOnClickListener(v -> {
            hideControls();
            openPicker();
        });
    }

    private void chooseProfile(int p) {
        profile = p;
        pickerShowing = false;
        splashDone = true;
        picker.hide();
        applyProfileUi();
        currentUrl = urlFor(p);
        if (player != null && !currentUrl.isEmpty()) {
            player.setMediaItem(buildItem(currentUrl));
            player.prepare();
            applyVolume();
            player.setPlayWhenReady(true);
            scheduleWait(1200);
        }
        if (!notifAsked) {
            notifAsked = true;
            afterSplashNotifications();
        }
    }

    private void openPicker() {
        if (!splashDone || pickerShowing) return;
        if (infoVisible) hideInfo();
        hideSocialNow();
        if (panels != null) panels.hideNow();
        hideWaitNow();
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        splashDone = false;
        pickerShowing = true;
        picker.show();
    }

    private void applyProfileUi() {
        boolean radio = profile == 1;
        radioView.setVisibility(radio ? View.VISIBLE : View.GONE);
        playerView.setVisibility(radio ? View.INVISIBLE : View.VISIBLE);
        View pip = findViewById(R.id.btn_pip);
        if (pip != null) pip.setVisibility(radio || Build.VERSION.SDK_INT < 26 ? View.GONE : View.VISIBLE);
        if (aspectChip != null) aspectChip.setVisibility(radio ? View.GONE : View.VISIBLE);
        updateGlowState();
    }

    private void loadRadioLogo() {
        File f = new File(getFilesDir(), "radyo_logo.img");
        if (!f.exists()) return;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0) return;
            int sample = 1;
            while (o.outWidth / sample > 1200) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(f.getPath(), o2);
            if (bmp != null) {
                picker.setRadioLogo(bmp);
                radioView.setLogo(bmp);
            }
        } catch (Throwable ignored) {
            // Bozuk dosya: yazi gorunur
        }
    }

    private void showLiveBadge() {
        if (true) return;   // CANLI rozeti kaldirildi
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

        if (panels != null && panels.isOpen() && down && code == KeyEvent.KEYCODE_BACK) {
            panels.hideAll();
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

        if (isTv && down && code == KeyEvent.KEYCODE_BACK) {
            openPicker();   // TV'de geri tusu profil secimine doner
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
            if (newUrl != null) handler.post(() -> applyRemoteUrl(0, newUrl));
        }

        // 1b) Radyo yayin linki
        String rcfg = firstLine(httpGet(RADIO_CFG_URL));
        if (rcfg != null && rcfg.startsWith("http")) {
            final String ru = rcfg;
            handler.post(() -> applyRemoteUrl(1, ru));
        }

        // 1c) Radyo logosu
        String rl = firstLine(httpGet(RADIO_LOGO_CFG_URL));
        if (rl != null && rl.startsWith("http")) {
            String img = normalizeImageUrl(rl);
            File lf = new File(getFilesDir(), "radyo_logo.img");
            if (!lf.exists() || !img.equals(prefs.getString("radio_logo_url", ""))) {
                if (downloadImage(img, "radyo_logo")) {
                    prefs.edit().putString("radio_logo_url", img).apply();
                    handler.post(this::loadRadioLogo);
                }
            }
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
        String first = firstLine(httpGet(SPLASH_CFG_URL));
                if (first != null) {
            String saved = prefs.getString("splash_url", "");
            if (first.equalsIgnoreCase("yok") || first.equalsIgnoreCase("varsayilan")) {
                if (!saved.isEmpty()) {
                    new File(getFilesDir(), "acilis.img").delete();
                    prefs.edit().remove("splash_url").apply();
                }
            } else if (first.startsWith("http")) {
                String img = normalizeImageUrl(first);
                if (!img.equals(saved) && !img.equals(failedSplashUrl)) {
                    if (downloadSplash(img)) {
                        prefs.edit().putString("splash_url", img).apply();
                    } else {
                        failedSplashUrl = img;
                    }
                }
            }
        }

        // 5) Haber akisi (telefon): en sona, yavas olabilir
        fetchFeedsBlocking();
    }

    private String firstLine(String text) {
        if (text == null) return null;
        for (String line : text.split("\n")) {
            line = line.replace("\uFEFF", "").trim();
            if (!line.isEmpty()) return line;
        }
        return null;
    }

    // GitHub "blob" sayfa linkini dogrudan resim linkine cevirir
    private String normalizeImageUrl(String u) {
        if (u.startsWith("https://github.com/") && u.contains("/blob/")) {
            u = u.replace("https://github.com/", "https://raw.githubusercontent.com/")
                    .replace("/blob/", "/");
            int q = u.indexOf('?');
            if (q > 0) u = u.substring(0, q);
        }
        return u;
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
        return downloadImage(urlStr, "acilis");
    }

    private boolean downloadImage(String urlStr, String name) {
        HttpURLConnection c = null;
        File tmp = new File(getFilesDir(), name + ".tmp");
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) RuhaTV/1.0");
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
            File dst = new File(getFilesDir(), name + ".img");
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
        return httpGet(urlStr, 20000);
    }

    private String httpGet(String urlStr, int maxChars) {
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
                while ((line = r.readLine()) != null && sb.length() < maxChars) {
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

    private void applyRemoteUrl(int prof, String url) {
        prefs.edit().putString(prof == 1 ? "url_radio" : "url", url).apply();
        if (prof == profile) applyUrl(url);
    }

    private void applyUrl(String url) {
        if (player == null || profile < 0 || url.equals(currentUrl)) return;
        currentUrl = url;
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

        // Telefonda yayin dokulu (TextureView) yuzeyde acilir: renkleri isik olarak yansitabilmek icin
        int pidx = root.indexOfChild(playerView);
        root.removeView(playerView);
        PlayerView pv = (PlayerView) getLayoutInflater().inflate(R.layout.player_phone, root, false);
        root.addView(pv, Math.max(0, pidx));
        playerView = pv;

        glowView = new GlowView(this);
        root.addView(glowView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        glowOn = prefs.getBoolean("glow", true);

        // Haber (alttan) ve YouTube (sagdan) panelleri
        panels = new FeedPanels(this, root, handler, url -> openLink(url));
        volumeRing.bringToFront();
        String cachedNews = prefs.getString("news_raw", "");
        if (!cachedNews.isEmpty()) onNewsLoaded(FeedData.parseNews(cachedNews));
        findViewById(R.id.chip_news).setOnClickListener(v -> {
            hideControls();
            refreshFeedsIfEmpty();
            panels.showNews();
        });

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

        glowItem = new TextView(this);
        glowItem.setTextSize(15);
        glowItem.setGravity(Gravity.CENTER);
        glowItem.setPadding((int) dp(14), (int) dp(9), (int) dp(14), (int) dp(9));
        glowItem.setClickable(true);
        glowItem.setOnClickListener(v -> {
            glowOn = !glowOn;
            prefs.edit().putBoolean("glow", glowOn).apply();
            updateGlowItem();
            updateGlowState();
            scheduleHideControls();
        });
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(0, 2));
        glp.setGravity(Gravity.FILL_HORIZONTAL);
        aspectMenu.addView(glowItem, glp);
        updateGlowItem();
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
        startArrows();
        scheduleHideControls();
    }

    private void hideControls() {
        controlsVisible = false;
        handler.removeCallbacks(hideControlsTask);
        aspectMenu.setVisibility(View.GONE);
        touchControls.animate().cancel();
        touchControls.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            if (!controlsVisible) {
                touchControls.setVisibility(View.GONE);
                stopArrows();
            }
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
        updateGlowState();
    }

    private void refreshAspectMenu() {
        for (int i = 0; i < ASPECT_NAMES.length && i < aspectMenu.getChildCount(); i++) {
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
            if (panels != null) panels.hideNow();
            volumeRing.setAlpha(0f);
            liveBadge.setVisibility(View.GONE);
        }
        applyAspectLayout(aspectIndex);
    }

    // ------------------------------------------------------------------ Ses kapat ikonu

    private void updateMuteIcon() {
        if (btnMute == null) return;
        btnMute.setBackgroundResource(muted ? R.drawable.bg_dock_on : R.drawable.bg_dock_btn);
        btnMute.setColorFilter(muted ? 0xFF03203A : 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ Sosyal medya

    private int iconFor(String platform) {
        String p = platform.toLowerCase();
        if (p.contains("insta")) return R.drawable.ic_instagram;
        if (p.contains("x.com") || p.contains("twitter") || p.trim().equals("x")) return R.drawable.ic_x;
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

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int cardW = Math.min(screenW - (int) dp(40), (int) dp(600));
        View card = findViewById(R.id.social_card);
        ViewGroup.LayoutParams clp0 = card.getLayoutParams();
        clp0.width = cardW;
        card.setLayoutParams(clp0);
        int cols = cardW >= dp(520) ? 3 : 2;
        socialList.setColumnCount(cols);
        int gap = (int) dp(5);
        int tileW = (cardW - (int) dp(40) - cols * 2 * gap) / cols;

        for (int i = 0; i < items.length; i++) {
            final String url = items[i][3];

            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setBackgroundResource(i == 0 ? R.drawable.bg_row_f : R.drawable.bg_row);
            tile.setPadding((int) dp(10), (int) dp(10), (int) dp(8), (int) dp(10));
            tile.setClickable(true);
            tile.setOnClickListener(v -> openLink(url));

            ImageView ic = new ImageView(this);
            String key = items[i][2] + " " + items[i][3];
            if (key.toLowerCase().contains("insta")) {
                ic.setImageDrawable(new InstagramDrawable());
            } else {
                ic.setImageResource(iconFor(key));
            }
            tile.addView(ic, new LinearLayout.LayoutParams((int) dp(38), (int) dp(38)));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.leftMargin = (int) dp(10);

            TextView name = new TextView(this);
            name.setText(items[i][0]);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(14);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(name);

            TextView sub = new TextView(this);
            sub.setText(items[i][1]);
            sub.setTextColor(0xFF9DB4CA);
            sub.setTextSize(11);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(sub);

            TextView plat = new TextView(this);
            plat.setText(items[i][2].toUpperCase());
            plat.setTextColor(0xFF1DE9D0);
            plat.setTextSize(9);
            plat.setLetterSpacing(0.08f);
            plat.setTypeface(null, android.graphics.Typeface.BOLD);
            plat.setSingleLine(true);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.topMargin = (int) dp(3);
            col.addView(plat, plp);
            tile.addView(col, clp);

            android.widget.GridLayout.LayoutParams glp = new android.widget.GridLayout.LayoutParams();
            glp.width = tileW;
            glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            glp.setMargins(gap, gap, gap, gap);
            socialList.addView(tile, glp);
        }
    }

    private void openLink(String url) {
        hideSocial();
        if (panels != null) panels.hideNow();
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
        int act0 = ev.getActionMasked();
        if (act0 == MotionEvent.ACTION_DOWN) {
            if (panels != null && panels.isOpen()) panels.touch();
            gTracking = !isTv && !inPip && splashDone && panels != null && !panels.isOpen()
                    && !infoVisible && !socialVisible && volumeRing.getAlpha() < 0.5f;
            gDownX = ev.getRawX();
            gDownY = ev.getRawY();
        } else if (act0 == MotionEvent.ACTION_CANCEL) {
            gTracking = false;
        } else if (act0 == MotionEvent.ACTION_UP && gTracking) {
            gTracking = false;
            float dx = ev.getRawX() - gDownX;
            float dy = ev.getRawY() - gDownY;
            int rw = root.getWidth();
            int rh = root.getHeight();
            if (gDownY > rh * 0.55f && dy < -dp(70) && Math.abs(dy) > Math.abs(dx) * 1.5f) {
                hideControls();
                refreshFeedsIfEmpty();
                panels.showNews();
            }
        }
        if (act0 == MotionEvent.ACTION_DOWN) {
            if (socialVisible) restartSocialTimer();
            if (infoVisible && !isTv) {
                handler.removeCallbacks(hideInfoTask);
                handler.postDelayed(hideInfoTask, 30000);
            }
            if (controlsVisible) scheduleHideControls();
        }
        return super.dispatchTouchEvent(ev);
    }

    // ------------------------------------------------------------------ Bildirimler (Firebase)

    private void setupNotifications() {
        if (isTv) return;   // TV'de bildirim yok
        try {
            RuhaMessagingService.ensureChannel(this);
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("herkes");
        } catch (Throwable ignored) {
            // Firebase hazir degilse uygulama yine normal calisir
        }
    }

    private void afterSplashNotifications() {
        if (isTv) return;
        // Android 13+: bildirim izni iste (bir kez)
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !prefs.getBoolean("notif_asked", false)) {
            prefs.edit().putBoolean("notif_asked", true).apply();
            try {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            } catch (Throwable ignored) {
                // Izin penceresi acilamazsa devam
            }
        }
        // Bildirime dokunarak gelindiyse ve bildirimde link varsa ac
        if (pendingLink != null) {
            String l = pendingLink;
            pendingLink = null;
            openLink(l);
        }
    }

    private void readLinkFromIntent(Intent i) {
        if (i == null) return;
        String l = i.getStringExtra("link");
        if (l != null && (l.startsWith("http://") || l.startsWith("https://"))) pendingLink = l;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readLinkFromIntent(intent);
        if (splashDone && pendingLink != null) {
            String l = pendingLink;
            pendingLink = null;
            openLink(l);
        }
    }

    // ------------------------------------------------------------------ Haber / YouTube akislari

    private void fetchFeedsBlocking() {
        if (isTv || panels == null) return;
        long now = System.currentTimeMillis();

        if (now >= newsNext) {
            String n = httpGetFeed(NEWS_RSS_URL, 600000);
            final java.util.List<FeedData.News> news = FeedData.parseNews(n);
            if (!news.isEmpty()) {
                newsNext = now + FEED_POLL_MS;
                if (n.length() < 300000) prefs.edit().putString("news_raw", n).apply();
                handler.post(() -> onNewsLoaded(news));
            } else {
                newsNext = now + FEED_RETRY_MS;
                final String msg = "Haberler alınamıyor (kod " + lastFeedCode + ")";
                handler.post(() -> panels.setError(msg));
            }
        }
    }

    // Akis/sayfa indirir. Tarayici gibi gorunur, onbellek engelleyici parametre eklemez.
    private String httpGetFeed(String urlStr, int maxChars) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/124.0.0.0 Mobile Safari/537.36");
            c.setRequestProperty("Accept",
                    "application/atom+xml,application/rss+xml,application/xml,text/xml,text/html;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8");
            c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI");
            int code = c.getResponseCode();
            lastFeedCode = code;
            if (code != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null && sb.length() < maxChars) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception ex) {
            lastFeedCode = -1;
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // Panel acilirken liste bossa hemen yeniden dene
    private void refreshFeedsIfEmpty() {
        if (panels == null) return;
        if (panels.hasNews()) return;
        newsNext = 0;
        fetchRemote();
    }

    // Haberler geldi: panele ver, ust banttaki son haber basligini baslat
    private void onNewsLoaded(java.util.List<FeedData.News> news) {
        if (panels == null) return;
        panels.setNews(news);
        tickerNews = news == null ? new java.util.ArrayList<>() : news;
        tickerIdx = 0;
        handler.removeCallbacks(tickerTask);
        if (!tickerNews.isEmpty()) {
            TextView t = findViewById(R.id.ticker_title);
            if (t != null) t.setText(tickerNews.get(0).title);
            handler.postDelayed(tickerTask, TICKER_MS);
            restartTickerProgress();
        }
    }

    private void restartTickerProgress() {
        View pr = findViewById(R.id.ticker_prog);
        if (pr == null) return;
        pr.animate().cancel();
        pr.setPivotX(0f);
        pr.setScaleX(0f);
        pr.animate().scaleX(1f).setDuration(TICKER_MS).setInterpolator(new LinearInterpolator()).start();
    }

    private void tickerStep() {
        if (tickerNews.size() < 2) return;
        tickerIdx = (tickerIdx + 1) % tickerNews.size();
        final TextView t = findViewById(R.id.ticker_title);
        if (t == null) return;
        final String next = tickerNews.get(tickerIdx).title;
        t.animate().alpha(0f).translationY(-dp(6)).setDuration(180).withEndAction(() -> {
            t.setText(next);
            t.setTranslationY(dp(6));
            t.animate().alpha(1f).translationY(0f).setDuration(220).start();
        }).start();
        restartTickerProgress();
    }

    // ------------------------------------------------------------------ Ok animasyonlari

    private void startArrows() {
        stopArrows();
        View an = findViewById(R.id.chip_news_arrow);
        arrowNewsAnim = android.animation.ObjectAnimator.ofFloat(an, View.TRANSLATION_Y, 0f, -dp(3));
        arrowNewsAnim.setDuration(620);
        arrowNewsAnim.setRepeatCount(ValueAnimator.INFINITE);
        arrowNewsAnim.setRepeatMode(ValueAnimator.REVERSE);
        arrowNewsAnim.start();
    }

    private void stopArrows() {
        if (arrowNewsAnim != null) arrowNewsAnim.cancel();
        View an = findViewById(R.id.chip_news_arrow);
        if (an != null) an.setTranslationY(0f);
    }

    // ------------------------------------------------------------------ Isikli cerceve

    private void updateGlowItem() {
        if (glowItem == null) return;
        glowItem.setText("Işıklı çerçeve: " + (glowOn ? "Açık" : "Kapalı"));
        glowItem.setTextColor(glowOn ? 0xFF1DE9D0 : 0xFFFFFFFF);
    }

    // Isik sadece yayin cercevesinin disinda bosluk varken ve mini oynatici disinda calisir
    private void updateGlowState() {
        if (glowView == null) return;
        handler.removeCallbacks(glowTask);
        boolean active = glowOn && profile != 1 && !isTv && !inPip && aspectIndex != 1 && aspectIndex != 2
                && player != null;
        glowView.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) handler.post(glowTask);
    }

    private void sampleGlow() {
        if (player == null || glowView == null) return;
        if (player.getPlaybackState() != Player.STATE_READY) return;
        View sv = playerView.getVideoSurfaceView();
        if (!(sv instanceof TextureView)) return;
        TextureView tv = (TextureView) sv;
        if (!tv.isAvailable()) return;
        final int w = 24;
        final int h = 14;
        try {
            if (glowBmp == null) glowBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            tv.getBitmap(glowBmp);
            int l = avg(0, 3, 0, h);
            int r = avg(w - 3, w, 0, h);
            int t = avg(0, w, 0, 2);
            int b = avg(0, w, h - 2, h);
            glowView.setTargets(tint(l), tint(r), tint(t), tint(b));
        } catch (Throwable ignored) {
            // Ornekleme basarisiz olursa mevcut renkler kalir
        }
    }

    private int avg(int x0, int x1, int y0, int y1) {
        long rr = 0;
        long gg = 0;
        long bb = 0;
        int n = 0;
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                int p = glowBmp.getPixel(x, y);
                rr += Color.red(p);
                gg += Color.green(p);
                bb += Color.blue(p);
                n++;
            }
        }
        if (n == 0) return 0xFF0A2A5A;
        return Color.rgb((int) (rr / n), (int) (gg / n), (int) (bb / n));
    }

    // Rengi biraz canlandirir, cok karanlik olursa koyu laciverte yaklastirir
    private int tint(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.3f);
        hsv[2] = Math.min(1f, Math.max(hsv[2] * 1.25f, 0.14f));
        return Color.HSVToColor(hsv);
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
