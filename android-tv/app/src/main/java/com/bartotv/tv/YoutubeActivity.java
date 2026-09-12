package com.bartotv.tv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

// YouTube 100% dentro de la app, como en Vercel (nunca abre la app externa).
// Si el embed falla (ej. inserción desactivada), carga el watch móvil en el mismo WebView.
// Back vuelve a la lista, igual que el player ExoPlayer.
public class YoutubeActivity extends Activity {

    private WebView web;
    private String videoId = "";
    private boolean watchFallback = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

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
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // UA de Chrome: el WebView de TV con UA por defecto lo rechaza YouTube a veces.
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());

        // Contenedor a pantalla completa real (antes el player quedaba en 0x0).
        String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;padding:0;background:#000;width:100%;height:100%;overflow:hidden}"
                + "#p{position:absolute;inset:0;width:100%;height:100%}</style></head>"
                + "<body><div id='p'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script>"
                + "<script>var pl=null,plErr=0;"
                + "function onYouTubeIframeAPIReady(){pl=new YT.Player('p',"
                + "{height:'100%',width:'100%',videoId:'" + videoId.replace("'", "") + "',"
                + "playerVars:{autoplay:1,mute:1,controls:1,modestbranding:1,rel:0,playsinline:1},"
                + "events:{onReady:function(e){e.target.playVideo();var n=0;"
                + "var f=setInterval(function(){try{pl.unMute();pl.setVolume(100);"
                + "if(!pl.isMuted())clearInterval(f);}catch(err){}if(++n>20)clearInterval(f);},500);},"
                + "onError:function(e){plErr=e.data;}}});}</script>"
                + "</body></html>";
        web.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null);

        // A los 9s: si no hay player o dio error, cargar el watch móvil DENTRO del WebView.
        handler.postDelayed(() => {
            if (watchFallback || web == null) return;
            web.evaluateJavascript(
                    "(function(){try{if(typeof plErr!=='undefined'&&plErr)return 'err:'+plErr;"
                            + "if(!pl||!pl.getPlayerState)return 'noready';"
                            + "return 'st:'+pl.getPlayerState();}catch(e){return 'ex'}})();",
                    (ValueCallback<String>) state -> {
                        if (watchFallback) return;
                        if (state == null) return;
                        String t = state.replace("\"", "");
                        // UNSTARTED(-1) o error => watch móvil in-app (algunos bloquean embed)
                        if (t.startsWith("err:") || t.equals("noready") || t.equals("st:-1")) {
                            openWatchInApp();
                        }
                    });
        }, 9000);
    }

    private void openWatchInApp() {
        watchFallback = true;
        if (web != null) {
            web.loadUrl("https://m.youtube.com/watch?v=" + videoId + "&autoplay=1");
        }
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
