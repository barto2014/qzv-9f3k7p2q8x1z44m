package com.bartotv.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

// YouTube embebido como en la web (IFrame API, autoplay, intento de audio).
// Back vuelve a la lista, igual que el player ExoPlayer.
public class YoutubeActivity extends Activity {

    private WebView web;

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
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());

        // Igual que youtube.html: mute 1 para autoplay, API intenta desmutear.
        String html = "<!DOCTYPE html><html><body style='margin:0;background:#000'>"
                + "<div id='p'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script>"
                + "<script>var pl;function onYouTubeIframeAPIReady(){pl=new YT.Player('p',"
                + "{height:'100%',width:'100%',videoId:'" + videoId.replace("'", "") + "',"
                + "playerVars:{autoplay:1,mute:1,controls:1,modestbranding:1,rel:0},"
                + "events:{onReady:function(e){e.target.playVideo();var n=0;"
                + "var f=setInterval(function(){try{pl.unMute();pl.setVolume(100);"
                + "if(!pl.isMuted())clearInterval(f);}catch(err){}if(++n>20)clearInterval(f);},500);}}});}</script>"
                + "</body></html>";
        web.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null);
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
