package com.tvoynatici.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Uygulama acikken gelen bildirimleri gosterir (kapaliyken sistem kendisi gosterir). */
public class RuhaMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "ruha_haber";

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                "Haber ve yayın bildirimleri", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Canlı yayın ve son dakika bildirimleri");
        nm.createNotificationChannel(ch);
    }

    @Override
    public void onMessageReceived(RemoteMessage m) {
        String title = null;
        String body = null;
        RemoteMessage.Notification n = m.getNotification();
        if (n != null) {
            title = n.getTitle();
            body = n.getBody();
        }
        Map<String, String> data = m.getData();
        if (title == null) title = data.get("title");
        if (body == null) body = data.get("body");
        String link = data.get("link");
        if (title == null && body == null) return;
        if (title == null) title = "Ruha TV";
        if (body == null) body = "";

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ensureChannel(this);

        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (link != null && link.startsWith("http")) i.putExtra("link", link);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), i, flags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(0xFF1DE9D0)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int) (System.currentTimeMillis() & 0x7FFFFFFF), b.build());
    }
}
