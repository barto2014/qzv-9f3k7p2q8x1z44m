package com.bartotv.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Cargador simple de logos con caché en memoria. Sin librerías externas.
public final class ImageLoader {
    private static final LruCache<String, Bitmap> MEM = new LruCache<>(80);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public static void load(String url, ImageView target) {
        target.setImageBitmap(null);
        if (url == null || url.isEmpty()) return;
        Bitmap hit = MEM.get(url);
        if (hit != null) {
            target.setImageBitmap(hit);
            return;
        }
        target.setTag(url);
        POOL.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "BartoTV-Android/1.0");
                InputStream in = c.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(in);
                in.close();
                if (bmp != null) {
                    MEM.put(url, bmp);
                    UI.post(() -> {
                        if (url.equals(target.getTag())) target.setImageBitmap(bmp);
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private ImageLoader() {}
}
