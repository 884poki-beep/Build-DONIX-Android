package jp.dongurikobo.donix;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** Keeps the app process foreground while the HTML audio player is active.
 * The WebView remains the audio engine; this is not a replacement for native playback.
 */
public class PlaybackService extends Service {
  public static final String ACTION_START = "jp.dongurikobo.donix.PLAYBACK_START";
  public static final String ACTION_STOP = "jp.dongurikobo.donix.PLAYBACK_STOP";
  private static final String CHANNEL = "donix_playback";
  private static final int ID = 103;
  private PowerManager.WakeLock playbackWakeLock;

  private void acquirePlaybackWakeLock() {
    if (playbackWakeLock == null) {
      PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
      playbackWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DONIX:AudioPlayback");
      playbackWakeLock.setReferenceCounted(false);
    }
    if (!playbackWakeLock.isHeld()) playbackWakeLock.acquire();
  }

  private void releasePlaybackWakeLock() {
    if (playbackWakeLock != null && playbackWakeLock.isHeld()) playbackWakeLock.release();
  }

  @Override public void onCreate() {
    super.onCreate();
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    nm.createNotificationChannel(new NotificationChannel(CHANNEL, "DONIX 音楽再生", NotificationManager.IMPORTANCE_LOW));
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && ACTION_STOP.equals(intent.getAction())) {
      releasePlaybackWakeLock();
      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf();
      return START_NOT_STICKY;
    }
    Intent launch = new Intent(this, MainActivity.class);
    launch.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent open = PendingIntent.getActivity(this, 10, launch,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification note = new Notification.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("DONIX Music Player")
        .setContentText("音楽を再生中")
        .setContentIntent(open)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .build();
    if (Build.VERSION.SDK_INT >= 29) {
      startForeground(ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    } else {
      startForeground(ID, note);
    }
    acquirePlaybackWakeLock();
    return START_NOT_STICKY;
  }

  @Override public void onDestroy() {
    releasePlaybackWakeLock();
    super.onDestroy();
  }

  @Override public IBinder onBind(Intent intent) { return null; }
}
