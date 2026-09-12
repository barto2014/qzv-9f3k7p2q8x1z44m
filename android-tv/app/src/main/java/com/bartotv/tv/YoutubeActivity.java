package com.bartotv.tv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

// YouTube tipo embed limpio 100% dentro de la app: sin interfaz de YouTube,
// sin salir a otra app. El navegador exige un gesto para el audio:
// el video arranca solo pero mudo, con OK se activa el sonido.
public class YoutubeActivity extends Activity {

    private WebView web;
    private TextView hint;
    private TextView ytError;
    private String videoId = "";
    private boolean watchFallback = false;
    private int noReadyCount = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable mutePoll = new Runnable() {
        @Override public void run() {
            if (web == null) return;
            web.evaluateJavascript(
                    "(function(){try{if(!pl||!pl.getPlayerState)return 'noready';"
                            + "if(plErr)return 'err:'+plErr;"
                            + "return (pl.isMuted()?'muted':'sonido')+':'+pl.getPlayerState();}"
                            + "catch(e){return 'ex'}})();",
                    (ValueCallback<String>) state -> {
                        if (web == null || state == null) return;
                        String t = state.replace("\"", "");
                        if (t.equals("noready") || t.equals("ex")) {
                            // La API nunca arrancó en este WebView: player embed directo
                            // (con su UI mínima) en vez de pantalla negra.
                            if (++noReadyCount >= 4 && !watchFallback) {
                                watchFallback = true;
                                web.loadUrl("https://www.youtube.com/embed/" + videoId
                                        + "?autoplay=1&rel=0&modestbranding=1&playsinline=1"
                                        + "&origin=https%3A%2F%2Fwww.youtube.com");
                            } else {
                                handler.postDelayed(mutePoll, 2000);
                            }
                            return;
                        }
                        if (t.startsWith("err:")) {
                            String code = t.substring(4);
                            // 101/150 = el dueño desactivó la inserción: ningún embed lo reproduce.
                            // Se carga el watch móvil dentro de la app (sigue sin salir a otra app).
                            if (!watchFallback && (code.equals("101") || code.equals("150"))) {
                                watchFallback = true;
                                web.loadUrl("https://m.youtube.com/watch?v=" + videoId + "&autoplay=1");
                                handler.postDelayed(mutePoll, 4000);
                            } else {
                                showError("Este video no permite inserción (error " + code + ")");
                            }
                            return;
                        }
                        if (t.startsWith("sonido")) {
                            hint.setVisibility(View.GONE);
                        } else {
                            hint.setVisibility(View.VISIBLE);
                            handler.postDelayed(mutePoll, 1500);
                        }
                    });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_youtube);

        videoId = getIntent().getStringExtra("videoId");
        if (videoId == null) videoId = "";

        web = findViewById(R.id.web);
        hint = findViewById(R.id.hint);
        ytError = findViewById(R.id.yt_error);

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
            // Sin esto YouTube responde 153 en WebView (bloquea el player anónimo).
            try {
                android.webkit.CookieManager.getInstance().setAcceptCookie(true);
                android.webkit.CookieManager.getInstance()
                        .setAcceptThirdPartyCookies(web, true);
            } catch (Exception ignored) {}
        }
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.requestFocus();

        // Embed limpio: sin controles, sin logo extra, sin relacionados.
        String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;padding:0;background:#000;width:100%;height:100%;overflow:hidden}"
                + "#p{position:absolute;inset:0;width:100%;height:100%}</style></head>"
                + "<body><div id='p'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script>"
                + "<script>var pl=null,plErr=0;"
                + "function unmute(){try{if(pl){pl.unMute();pl.setVolume(100);pl.playVideo();}}catch(e){}}"
                + "function onYouTubeIframeAPIReady(){pl=new YT.Player('p',"
                + "{height:'100%',width:'100%',videoId:'" + videoId.replace("'", "") + "',"
                + "playerVars:{autoplay:1,mute:1,controls:0,disablekb:0,fs:0,modestbranding:1,rel:0,iv_load_policy:3,playsinline:1,origin:'https://www.youtube.com',widget_referrer:'https://www.youtube.com'},"
                + "events:{onReady:function(e){e.target.playVideo();"
                + "var n=0;var f=setInterval(function(){try{pl.unMute();pl.setVolume(100);"
                + "if(!pl.isMuted()){clearInterval(f);}}catch(err){}if(++n>30)clearInterval(f);},500);},"
                + "onError:function(e){plErr=e.data;}}});}</script>"
                + "</body></html>";
        web.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null);
        handler.postDelayed(mutePoll, 2500);
    }

    // OK del control = gesto de usuario: ahí sí deja desmutear.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (web != null) {
                web.evaluateJavascript("unmute();", null);
                handler.postDelayed(mutePoll, 800);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showError(String msg) {
        ytError.setText(msg);
        ytError.setVisibility(View.VISIBLE);
        hint.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        handler.removeCallbacksAndMessages(null);
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

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
