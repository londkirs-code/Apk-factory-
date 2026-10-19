package com.apkfactory.alfon;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        webView.setBackgroundColor(0xfff0f4f8);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleExternalUrl(request.getUrl().toString());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternalUrl(url);
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (url != null && url.startsWith("data:text/vcard")) {
                    int comma = url.indexOf(',');
                    if (comma >= 0) {
                        String payload = Uri.decode(url.substring(comma + 1));
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("text/vcard");
                        share.putExtra(Intent.EXTRA_TEXT, payload);
                        try { startActivity(Intent.createChooser(share, "שמירת איש קשר")); }
                        catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this, "לא נמצאה אפליקציה לשמירת איש קשר", Toast.LENGTH_LONG).show(); }
                    }
                } else if (url != null) {
                    handleExternalUrl(url);
                }
            }
        });

        // Android 15+ edge-to-edge is enforced for apps targeting SDK 35.
        // Keep the bundled HTML clear of system bars and display cutouts.
        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setDecorFitsSystemWindows(false);
            webView.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int bottom = Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
                view.setPadding(bars.left, bars.top, bars.right, bottom);
                return insets;
            });
        }

        setContentView(webView);
        if (state == null) webView.loadUrl("file:///android_asset/alfon.html");
        else {
            webView.restoreState(state);
            if (webView.getUrl() == null) webView.loadUrl("file:///android_asset/alfon.html");
        }
    }

    private boolean handleExternalUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("mailto:")) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "לא נמצאה אפליקציה מתאימה", Toast.LENGTH_SHORT).show(); }
            return true;
        }
        if (url.startsWith("data:text/vcard")) return false;
        return !url.startsWith("file:") && !url.startsWith("about:");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
