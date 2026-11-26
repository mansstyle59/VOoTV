package app.tvo.tv.ui

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Bande-annonce YouTube lue sans prendre le focus de la télécommande.
 * - [cover] = true : remplit toute la zone (fond de la fiche, rognée comme une image).
 * - [cover] = false : vidéo entière, centrée (fenêtre).
 * Lecteur YouTube officiel (API iframe) : sous-titres désactivés, qualité adaptée à la télé,
 * taille recalculée selon l'écran pour ne jamais couper l'image en deux.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerView(
    youtubeKey: String, modifier: Modifier = Modifier, cover: Boolean = true,
    muted: Boolean = false, loop: Boolean = true, onPlaying: () -> Unit = {}, onEnded: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val playingCb = androidx.compose.runtime.rememberUpdatedState(onPlaying)
    val endedCb = androidx.compose.runtime.rememberUpdatedState(onEnded)
    val web = remember(youtubeKey, cover, muted, loop) {
        WebView(ctx).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setBackgroundColor(AColor.BLACK)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            // Le lecteur prévient l'appli quand l'image démarre vraiment (fondu sans écran noir) et quand c'est fini
            val view = this
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface fun playing() { view.post { playingCb.value() } }
                @android.webkit.JavascriptInterface fun ended() { view.post { endedCb.value() } }
            }, "TVO")
            val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;width:100%;height:100%;background:#000;overflow:hidden}#p{position:absolute;border:0;pointer-events:none}</style></head>
<body><div id="p"></div>
<script>
var COVER=${if (cover) "true" else "false"};
function fit(){var e=document.getElementById('p');if(!e)return;var W=innerWidth,H=innerHeight,w,h;
 if(COVER){ if(W/H>16/9){w=W;h=W*9/16;}else{h=H;w=H*16/9;} } else { if(W/H>16/9){h=H;w=H*16/9;}else{w=W;h=W*9/16;} }
 e.style.width=w+'px';e.style.height=h+'px';e.style.left=((W-w)/2)+'px';e.style.top=((H-h)/2)+'px';}
addEventListener('resize',fit);fit();
var player;
function noCc(p){try{p.unloadModule('captions');p.unloadModule('cc');}catch(x){}}
function onYouTubeIframeAPIReady(){
 player=new YT.Player('p',{videoId:'$youtubeKey',host:'https://www.youtube-nocookie.com',
  playerVars:{autoplay:1,controls:0,rel:0,modestbranding:1,playsinline:1,iv_load_policy:3,disablekb:1,fs:0,cc_load_policy:0${if (loop) ",loop:1,playlist:'$youtubeKey'" else ""}${if (muted) ",mute:1" else ""}},
  events:{onReady:function(e){fit();noCc(e.target);${if (muted) "e.target.mute();" else ""}try{e.target.setPlaybackQuality('hd720');}catch(x){}e.target.playVideo();},
   onApiChange:function(e){noCc(e.target);},onStateChange:function(e){if(e.data===1){noCc(e.target);try{TVO.playing();}catch(x){}}if(e.data===0){try{TVO.ended();}catch(x){}}}}});
 setTimeout(fit,300);setInterval(fit,2000);}
</script><script src="https://www.youtube.com/iframe_api"></script></body></html>"""
            loadDataWithBaseURL("https://mansstyle59.github.io/", html, "text/html", "utf-8", null)
        }
    }
    DisposableEffect(web) {
        onDispose { runCatching { web.loadUrl("about:blank"); web.stopLoading(); web.destroy() } }
    }
    AndroidView(factory = { web }, modifier = modifier)
}
