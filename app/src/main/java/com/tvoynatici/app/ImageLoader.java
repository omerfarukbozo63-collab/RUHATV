package com.tvoynatici.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Kucuk, bagimliliksiz resim yukleyici (bellek onbellekli). */
public class ImageLoader {

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void load(final ImageView view, final String url, final int targetW) {
        view.setTag(url);
        if (url == null || url.isEmpty()) {
            view.setImageDrawable(null);
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        POOL.execute(() -> {
            final Bitmap bmp = download(url, targetW);
            if (bmp == null) return;
            CACHE.put(url, bmp);
            MAIN.post(() -> {
                if (url.equals(view.getTag())) view.setImageBitmap(bmp);
            });
        });
    }

    private static Bitmap download(String urlStr, int targetW) {
        try {
            String cur = urlStr;
            for (int hop = 0; hop < 4; hop++) {
                HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(12000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) RuhaTV/1.0");
                int code = c.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null) return null;
                    cur = new URL(new URL(cur), loc).toString();
                    continue;
                }
                if (code != 200) {
                    c.disconnect();
                    return null;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > 4L * 1024 * 1024) return null;
                        bos.write(buf, 0, n);
                    }
                } finally {
                    c.disconnect();
                }
                byte[] data = bos.toByteArray();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                if (o.outWidth <= 0) return null;
                int sample = 1;
                while (o.outWidth / (sample * 2) >= targetW) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
            }
        } catch (Throwable ignored) {
            // Resim yuklenemezse bos kalir
        }
        return null;
    }
}
