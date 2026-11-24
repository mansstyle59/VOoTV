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
 * Bande-annonce YouTube lue en fond, sans prendre le focus de la télécommande.
 * La page est chargée avec une adresse de base pour que YouTube accepte la lecture intégrée.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerView(youtubeKey: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val web = remember(youtubeKey) {
        WebView(ctx).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setBackgroundColor(AColor.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            val src = "https://www.youtube-nocookie.com/embed/$youtubeKey?autoplay=1&controls=0&rel=0&modestbranding=1" +
                "&playsinline=1&iv_load_policy=3&disablekb=1&fs=0&cc_load_policy=0&hl=fr&loop=1&playlist=$youtubeKey"
            val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
                iframe{position:absolute;top:50%;left:50%;width:100vw;height:56.25vw;min-height:100vh;min-width:177.78vh;transform:translate(-50%,-50%);border:0;pointer-events:none}</style>
                </head><body><iframe src="$src" allow="autoplay; encrypted-media" referrerpolicy="strict-origin-when-cross-origin"></iframe></body></html>"""
            loadDataWithBaseURL("https://mansstyle59.github.io/", html, "text/html", "utf-8", null)
        }
    }
    DisposableEffect(web) {
        onDispose { runCatching { web.loadUrl("about:blank"); web.stopLoading(); web.destroy() } }
    }
    AndroidView(factory = { web }, modifier = modifier)
}
