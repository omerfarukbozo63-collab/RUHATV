package com.tvoynatici.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

/**
 * Radyo arka planda calarken uygulamayi canli tutan on plan servisi.
 * Oynatici MainActivity'de kalir; bu servis sadece bildirimi gosterir ve islemi korur.
 */
public class RadioService extends Service {

    public static final String ACTION_STOP = "com.tvoynatici.app.RADIO_STOP";
    private static final String CHANNEL = "ruha_radyo";
    private static final int ID = 4711;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            MainActivity.stopRadioFromNotification();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Radyo Ruha",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Radyo arka planda çalarken görünür");
            nm.createNotificationChannel(ch);
        }

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, piFlags);
        Intent stop = new Intent(this, RadioService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, piFlags);

        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(0xFF1DE9D0)
                .setContentTitle("Radyo Ruha")
                .setContentText("Canlı yayın çalıyor")
                .setContentIntent(openPi)
                .addAction(0, "Durdur", stopPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(ID, n);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
