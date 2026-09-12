package com.bartotv.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// ExoPlayer directo, sin proxy:
// - HLS (.m3u8) y DASH (.mpd) nativos, http y https (usesCleartextTraffic)
// - Widevine por drm_server + ClearKey por drm_keys
// - Audio preferido español siempre
public class PlayerActivity extends Activity {

    private ExoPlayer player;
    private PlayerView view;
    private TextView error;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);

        view = findViewById(R.id.player_view);
        error = findViewById(R.id.error);

        String url = getIntent().getStringExtra("url");
        String type = getIntent().getStringExtra("type");
        String ua = opt("ua");
        String referer = opt("referer");
        Map<String, String> headers = jsonMap(getIntent().getStringExtra("headers"));
        Map<String, String> drmKeys = jsonMap(getIntent().getStringExtra("drmKeys"));
        String drmServer = opt("drmServer");

        if (url == null || url.isEmpty()) {
            showError("Sin URL");
            return;
        }
        start(url, type, ua, referer, headers, drmKeys, drmServer);
    }

    private String opt(String k) {
        String v = getIntent().getStringExtra(k);
        return v == null ? "" : v;
    }

    private static Map<String, String> jsonMap(String json) {
        Map<String, String> m = new HashMap<>();
        if (json == null || json.isEmpty()) return m;
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                m.put(k, o.optString(k));
            }
        } catch (Exception ignored) {}
        return m;
    }

    private void start(String url, String type, String ua,
                       String referer, Map<String, String> headers,
                       Map<String, String> drmKeys, String drmServer) {
        try {
            String userAgent = ua.isEmpty()
                    ? "Mozilla/5.0 (Linux; Android 10; TV) BartoTV/1.0"
                    : ua;

            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setConnectTimeoutMs(12000)
                    .setReadTimeoutMs(12000)
                    .setAllowCrossProtocolRedirects(true);

            Map<String, String> req = new HashMap<>(headers);
            if (!referer.isEmpty()) req.put("Referer", referer);
            if (!req.isEmpty()) http.setDefaultRequestProperties(req);

            DefaultDataSource.Factory data = new DefaultDataSource.Factory(this, http);

            DefaultTrackSelector tracks = new DefaultTrackSelector(this);
            tracks.setParameters(tracks.buildUponParameters()
                    .setPreferredAudioLanguage("es")
                    .build());

            DefaultMediaSourceFactory sources = new DefaultMediaSourceFactory(data);
            sources.setDrmSessionManagerProvider(drmProvider(http, drmServer, drmKeys));

            player = new ExoPlayer.Builder(this)
                    .setTrackSelector(tracks)
                    .setMediaSourceFactory(sources)
                    .build();
            view.setPlayer(player);

            MediaItem item = buildItem(url, type, drmServer, drmKeys);
            player.setMediaItem(item);
            player.prepare();
            player.play();
        } catch (Exception e) {
            showError("Error: " + e.getMessage());
        }
    }

    private static MediaItem buildItem(String url, String type,
                                       String drmServer, Map<String, String> drmKeys) {
        String mime = null;
        String t = type == null ? "" : type.toLowerCase();
        String u = url.toLowerCase();
        if ("mpd".equals(t) || u.contains(".mpd")) mime = MimeTypes.APPLICATION_MPD;
        else if ("m3u8".equals(t) || "externo".equals(t) || u.contains(".m3u8")) mime = MimeTypes.APPLICATION_M3U8;

        MediaItem.Builder b = new MediaItem.Builder().setUri(url);
        if (mime != null) b.setMimeType(mime);

        // Widevine por servidor de licencias
        if (drmServer != null && !drmServer.isEmpty()) {
            b.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(drmServer)
                    .build());
        } else if (drmKeys != null && !drmKeys.isEmpty()) {
            // ClearKey local (KID:KEY en hex, como en admin.html)
            b.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).build());
        }
        return b.build();
    }

    private static DrmSessionManagerProvider drmProvider(
            DefaultHttpDataSource.Factory http, String drmServer, Map<String, String> drmKeys) {
        return item -> {
            try {
                if (item.localConfiguration == null || item.localConfiguration.drmConfiguration == null) {
                    return DrmSessionManager.DRM_UNSUPPORTED;
                }
                java.util.UUID scheme = item.localConfiguration.drmConfiguration.scheme;
                if (C.WIDEVINE_UUID.equals(scheme) && drmServer != null && !drmServer.isEmpty()) {
                    HttpMediaDrmCallback cb = new HttpMediaDrmCallback(drmServer, http);
                    return new DefaultDrmSessionManager.Builder()
                            .setUuidAndExoMediaDrmProvider(scheme,
                                    androidx.media3.exoplayer.drm.FrameworkMediaDrm.DEFAULT_PROVIDER)
                            .build(cb);
                }
                if (C.CLEARKEY_UUID.equals(scheme) && drmKeys != null && !drmKeys.isEmpty()) {
                    byte[] response = clearKeyResponse(drmKeys);
                    LocalMediaDrmCallback cb = new LocalMediaDrmCallback(response);
                    return new DefaultDrmSessionManager.Builder()
                            .setUuidAndExoMediaDrmProvider(scheme,
                                    androidx.media3.exoplayer.drm.FrameworkMediaDrm.DEFAULT_PROVIDER)
                            .build(cb);
                }
            } catch (Exception ignored) {}
            return DrmSessionManager.DRM_UNSUPPORTED;
        };
    }

    // Convierte {kidHex: keyHex} a respuesta JSON ClearKey con base64url.
    private static byte[] clearKeyResponse(Map<String, String> keys) throws Exception {
        StringBuilder sb = new StringBuilder("{\"keys\":[");
        boolean first = true;
        for (Map.Entry<String, String> e : keys.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"kty\":\"oct\",\"kid\":\"").append(b64url(hex(e.getKey())))
                    .append("\",\"k\":\"").append(b64url(hex(e.getValue()))).append("\"}");
        }
        sb.append("]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hex(String s) throws Exception {
        String t = s.replaceAll("[^0-9a-fA-F]", "");
        if (t.length() % 2 == 1) t = "0" + t;
        byte[] out = new byte[t.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(t.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String b64url(byte[] b) {
        return android.util.Base64.encodeToString(b,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
    }

    private void showError(String msg) {
        error.setVisibility(android.view.View.VISIBLE);
        error.setText(msg);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }
}
