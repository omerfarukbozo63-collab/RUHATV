package com.tvoynatici.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

public class MainActivity extends Activity {

    // Yayin baglantisi (degistirmek icin sadece bu satiri duzenleyin)
    private static final String STREAM_URL =
            "http://163.172.31.106:8080/memfs/f7ba0b87-af81-491c-ba90-cb3fb6f15314.m3u8";

    private ExoPlayer player;
    private PlayerView playerView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        playerView = findViewById(R.id.player_view);
        hideSystemUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startPlayer();
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

    private void startPlayer() {
        if (player != null) return;

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android TV) TVOynatici/1.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                // Hata olursa 3 sn sonra yayini yeniden dene
                handler.postDelayed(() -> {
                    if (player != null) {
                        player.setMediaItem(buildItem());
                        player.prepare();
                        player.play();
                    }
                }, 3000);
            }
        });

        player.setMediaItem(buildItem());
        player.setPlayWhenReady(true);
        player.prepare();
    }

    private MediaItem buildItem() {
        return new MediaItem.Builder()
                .setUri(STREAM_URL)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build();
    }

    private void stopPlayer() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
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
