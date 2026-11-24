package app.tvo.mobile;

import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.webkit.JavascriptInterface;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private boolean armed = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getBridge().getWebView().addJavascriptInterface(new Object() {
            @JavascriptInterface public void enter() { runOnUiThread(() -> enterPip()); }
            @JavascriptInterface public void arm(boolean on) { armed = on; }
        }, "TvoPip");
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build());
        } catch (Exception ignored) { }
    }

    // Quand la vidéo joue et qu'on quitte l'appli (bouton Accueil) : passage automatique en image dans l'image
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (armed) enterPip();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        getBridge().getWebView().evaluateJavascript(
            "document.documentElement.classList.toggle('in-pip'," + inPip + ");", null);
    }
}
