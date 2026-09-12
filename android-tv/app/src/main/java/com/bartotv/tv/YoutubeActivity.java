package com.bartotv.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

// YouTube desde tu web Vercel (el sistema que anda): mismo youtube.html probado.
// Solo cambia el motor de MPD/HLS que sí es nativo ExoPlayer.
public class YoutubeActivity extends Activity {

    // Base de tu deploy Vercel actual (sin / final).
    private static final String WEB_BASE = "https://nein-eight.vercel.app";

    private WebView web;
    private boolean firstPage = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_youtube);

        String videoId = getIntent().getStringExtra("videoId");
        if (videoId == null) videoId = "";

        web = findViewById(R.id.web);
        findViewById(R.id.hint).setVisibility(android.view.View.GONE);
        findViewById(R.id.yt_error).setVisibility(android.view.View.GONE);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            try {
                android.webkit.CookieManager.getInstance().setAcceptCookie(true);
                android.webkit.CookieManager.getInstance()
                        .setAcceptThirdPartyCookies(web, true);
            } catch (Exception ignored) {}
        }
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (firstPage) { firstPage = false; return; }
                // El ⬅ de la página vuelve al index: eso es "volver a la lista".
                if (url != null && (url.equals(WEB_BASE + "/") || url.equals(WEB_BASE)
                        || url.endsWith("/index.html"))) {
                    finish();
                }
            }
        });
        web.requestFocus();
        web.loadUrl(WEB_BASE + "/youtube.html?source=" + videoId);
    }

    @Override
    public void onBackPressed() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }
}
