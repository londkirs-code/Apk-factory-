package il.co.londkirs.netz;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private GeolocationPermissions.Callback pendingLocationCallback;
    private String pendingLocationOrigin;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xff081323);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                    return;
                }
                pendingLocationOrigin = origin;
                pendingLocationCallback = callback;
                if (Build.VERSION.SDK_INT >= 23) {
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, 7);
                } else {
                    callback.invoke(origin, false, false);
                    pendingLocationCallback = null;
                    pendingLocationOrigin = null;
                }
            }
        });

        // Android 15+ enforces edge-to-edge for apps targeting SDK 35.
        // Apply system-bar and display-cutout insets so the HTML stays readable.
        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setDecorFitsSystemWindows(false);
            web.setOnApplyWindowInsetsListener((view, insets) -> {
                int left = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()).left;
                int top = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()).top;
                int right = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()).right;
                int bottom = Math.max(
                        insets.getInsets(WindowInsets.Type.systemBars()
                                | WindowInsets.Type.displayCutout()).bottom,
                        insets.getInsets(WindowInsets.Type.ime()).bottom);
                view.setPadding(left, top, right, bottom);
                return insets;
            });
        }

        setContentView(web);
        if (state == null) {
            web.loadUrl("file:///android_asset/index.html");
        } else {
            web.restoreState(state);
            if (web.getUrl() == null) web.loadUrl("file:///android_asset/index.html");
        }
    }

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 7) return;
        GeolocationPermissions.Callback callback = pendingLocationCallback;
        String origin = pendingLocationOrigin;
        pendingLocationCallback = null;
        pendingLocationOrigin = null;
        if (callback != null && origin != null) {
            callback.invoke(origin, hasLocationPermission(), false);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (web != null) web.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.setWebChromeClient(null);
            web.setWebViewClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
