package jp.dongurikobo.donix;

import android.app.Activity;
import android.media.MediaMetadataRetriever;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import java.util.concurrent.ConcurrentHashMap;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.webkit.WebViewClient;
import android.webkit.PermissionRequest;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
  private final ConcurrentHashMap<String,String> covers = new ConcurrentHashMap<>();
  public class CoverBridge {
    @JavascriptInterface public String getCover(String name) {
      String cover = covers.get(name);
      return cover == null ? "" : cover;
    }
  }
  private void registerCover(Uri uri) {
    if (uri == null) return;
    String name = null;
    try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
    } catch (Exception ignored) {}
    if (name == null) return;
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      retriever.setDataSource(this, uri);
      byte[] bytes = retriever.getEmbeddedPicture();
      if (bytes != null && bytes.length > 0 && bytes.length < 5*1024*1024) {
        String type = bytes.length > 3 && (bytes[0] & 255) == 255 ? "image/jpeg" : "image/png";
        covers.put(name, "data:" + type + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP));
      }
    } catch (Exception ignored) {} finally { try { retriever.release(); } catch (Exception ignored) {} }
  }
  private WebView web;
  private ValueCallback<Uri[]> pendingFiles;
  private static final int PICK_FILES = 42;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    web = new WebView(this);
    web.addJavascriptInterface(new CoverBridge(), "DonixNative");
    setContentView(web);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
    web.setWebViewClient(new WebViewClient() {
      @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
        return loader.shouldInterceptRequest(request.getUrl());
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        if (pendingFiles != null) pendingFiles.onReceiveValue(null);
        pendingFiles = callback;
        try {
          Intent intent = params.createIntent();
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(intent, PICK_FILES);
          return true;
        } catch (Exception e) {
          pendingFiles = null;
          callback.onReceiveValue(null);
          return false;
        }
      }
    });
    web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
  }
  @Override protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == PICK_FILES && pendingFiles != null) {
      Uri[] uris = WebChromeClient.FileChooserParams.parseResult(result, data);
      if (uris != null) for (Uri uri : uris) registerCover(uri);
      pendingFiles.onReceiveValue(uris);
      pendingFiles = null;
    }
  }
  @Override public void onBackPressed() {
    if (web.canGoBack()) web.goBack(); else super.onBackPressed();
  }
  @Override protected void onDestroy() { if (pendingFiles != null) pendingFiles.onReceiveValue(null); web.destroy(); super.onDestroy(); }
}
