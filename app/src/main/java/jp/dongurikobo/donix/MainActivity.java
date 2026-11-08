package jp.dongurikobo.donix;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
  private static final String CHANNEL = "donix_playback";
  private static final int NOTIFICATION_ID = 103;
  private MediaSession mediaSession;
  private NotificationManager notificationManager;
  private boolean playing = false;
  private String currentTitle = "DONIX Music Player";
  private Bitmap currentArtwork;
  private boolean hasTrack = false;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  private void runPlayerCommand(String command) {
    if (web == null) return;
    web.evaluateJavascript("window.DonixMediaCommand && window.DonixMediaCommand('" + command + "')", null);
  }

  private void updateNativeMedia(String title, String artwork, boolean isPlaying) {
    if (mediaSession == null) return;
    hasTrack = true;
    currentTitle = title == null || title.isEmpty() ? "DONIX Music Player" : title;
    playing = isPlaying;
    currentArtwork = null;
    if (artwork != null && artwork.startsWith("data:image/") && artwork.contains(",")) {
      try {
        String raw = artwork.substring(artwork.indexOf(',') + 1);
        if (raw.length() < 3_000_000) {
          byte[] bytes = Base64.decode(raw, Base64.DEFAULT);
          BitmapFactory.Options opts = new BitmapFactory.Options();
          opts.inSampleSize = 2;
          currentArtwork = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        }
      } catch (Exception ignored) {}
    }
    MediaMetadata.Builder meta = new MediaMetadata.Builder()
      .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
      .putString(MediaMetadata.METADATA_KEY_ARTIST, "DONIX Music Player")
      .putString(MediaMetadata.METADATA_KEY_ALBUM, "MINIDISC");
    if (currentArtwork != null) meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentArtwork);
    mediaSession.setMetadata(meta.build());
    long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
      | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
      | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
    mediaSession.setPlaybackState(new PlaybackState.Builder()
      .setActions(actions)
      .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
        PlaybackState.PLAYBACK_POSITION_UNKNOWN, playing ? 1f : 0f).build());
    mediaSession.setActive(true);
    showMediaNotification();
    Intent playbackIntent = new Intent(this, PlaybackService.class);
    if (playing) {
      playbackIntent.setAction(PlaybackService.ACTION_START);
      try { startForegroundService(playbackIntent); } catch (Exception e) { android.util.Log.w("DONIX", "Cannot start playback service", e); }
    } else {
      playbackIntent.setAction(PlaybackService.ACTION_STOP);
      try { startService(playbackIntent); } catch (Exception ignored) {}
    }
  }

  private PendingIntent actionIntent(String action, int requestCode) {
    Intent intent = new Intent(this, MediaButtonReceiver.class);
    intent.setAction("jp.dongurikobo.donix." + action);
    return PendingIntent.getBroadcast(this, requestCode, intent,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  public static class MediaButtonReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
      if (intent == null || intent.getAction() == null) return;
      MainActivity activity = currentActivity;
      if (activity == null) return;
      String action = intent.getAction();
      activity.mainHandler.post(() -> {
        if (action.endsWith(".PLAY_PAUSE")) activity.runPlayerCommand("toggle");
        else if (action.endsWith(".NEXT")) activity.runPlayerCommand("next");
        else if (action.endsWith(".PREV")) activity.runPlayerCommand("prev");
      });
    }
  }
  private static volatile MainActivity currentActivity;

  private void showMediaNotification() {
    if (!hasTrack) return;
    Intent launch = new Intent(this, MainActivity.class);
    launch.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent content = PendingIntent.getActivity(this, 10, launch,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification.Action prev = new Notification.Action.Builder(
      android.R.drawable.ic_media_previous, "前の曲", actionIntent("PREV", 11)).build();
    Notification.Action toggle = new Notification.Action.Builder(
      playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
      playing ? "一時停止" : "再生", actionIntent("PLAY_PAUSE", 12)).build();
    Notification.Action next = new Notification.Action.Builder(
      android.R.drawable.ic_media_next, "次の曲", actionIntent("NEXT", 13)).build();
    Notification.Builder builder = Build.VERSION.SDK_INT >= 26
      ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
    builder.setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle(currentTitle).setContentText("DONIX Music Player")
      .setContentIntent(content).setVisibility(Notification.VISIBILITY_PUBLIC)
      .setOnlyAlertOnce(true).setShowWhen(false)
      .addAction(prev).addAction(toggle).addAction(next)
      .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken())
        .setShowActionsInCompactView(0, 1, 2));
    if (currentArtwork != null) builder.setLargeIcon(currentArtwork);
    if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
      notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
  }

  private void setupMediaSession() {
    notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel = new NotificationChannel(CHANNEL, "DONIX 音楽再生",
        NotificationManager.IMPORTANCE_LOW);
      channel.setDescription("再生中の曲と操作ボタン");
      notificationManager.createNotificationChannel(channel);
    }
    mediaSession = new MediaSession(this, "DONIX");
    mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    mediaSession.setCallback(new MediaSession.Callback() {
      @Override public void onPlay() { mainHandler.post(() -> runPlayerCommand("play")); }
      @Override public void onPause() { mainHandler.post(() -> runPlayerCommand("pause")); }
      @Override public void onSkipToNext() { mainHandler.post(() -> runPlayerCommand("next")); }
      @Override public void onSkipToPrevious() { mainHandler.post(() -> runPlayerCommand("prev")); }
    });
  }

  private final ConcurrentHashMap<String,String> covers = new ConcurrentHashMap<>();
  public class CoverBridge {
    @JavascriptInterface public void updateMedia(String title, String artwork, boolean isPlaying) {
      mainHandler.post(() -> updateNativeMedia(title, artwork, isPlaying));
    }
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
    currentActivity = this;
    setupMediaSession();
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1003);
    }
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

    @Override
    public android.webkit.WebResourceResponse shouldInterceptRequest(
            WebView view,
            android.webkit.WebResourceRequest request) {
        return loader.shouldInterceptRequest(request.getUrl());
    }

    @Override
    public boolean shouldOverrideUrlLoading(
            WebView view,
            android.webkit.WebResourceRequest request) {

        if (!request.isForMainFrame()) return false;

        Uri uri = request.getUrl();
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (("https".equalsIgnoreCase(scheme)
                || "http".equalsIgnoreCase(scheme))
                && host != null
                && (host.equalsIgnoreCase("music.apple.com")
                    || host.equalsIgnoreCase("open.spotify.com"))) {

            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                android.widget.Toast.makeText(
                    MainActivity.this,
                    "リンクを開けませんでした",
                    android.widget.Toast.LENGTH_SHORT
                ).show();
            }

            return true;
        }

        return false;
    }
});
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        if (pendingFiles != null) pendingFiles.onReceiveValue(null);
        pendingFiles = callback;
        try {
          // ACTION_OPEN_DOCUMENT supports selecting multiple tracks on Android.
          // Keep single selection for the manual cover-art picker.
          boolean multi = params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
          Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          intent.setType(multi ? "audio/*" : "*/*");
          if (!multi) {
            String[] types = params.getAcceptTypes();
            if (types != null && types.length > 0 && types[0] != null && !types[0].isEmpty()) {
              intent.setType(types[0]);
            }
          }
          intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multi);
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
  @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 1003 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) showMediaNotification();
  }
  @Override protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == PICK_FILES && pendingFiles != null) {
      Uri[] uris = null;
      if (result == RESULT_OK && data != null) {
        android.content.ClipData clip = data.getClipData();
        if (clip != null && clip.getItemCount() > 0) {
          java.util.LinkedHashSet<Uri> selected = new java.util.LinkedHashSet<>();
          for (int i = 0; i < clip.getItemCount(); i++) {
            Uri uri = clip.getItemAt(i).getUri();
            if (uri != null) selected.add(uri);
          }
          uris = selected.toArray(new Uri[0]);
        } else if (data.getData() != null) {
          uris = new Uri[]{data.getData()};
        }
      }
      if (uris != null) for (Uri uri : uris) registerCover(uri);
      pendingFiles.onReceiveValue(uris);
      pendingFiles = null;
    }
  }
  @Override public void onBackPressed() {
    if (web.canGoBack()) web.goBack(); else super.onBackPressed();
  }
  @Override protected void onDestroy() { currentActivity = null; if (notificationManager != null) notificationManager.cancel(NOTIFICATION_ID); if (mediaSession != null) mediaSession.release(); if (pendingFiles != null) pendingFiles.onReceiveValue(null); web.destroy(); super.onDestroy(); }
}
