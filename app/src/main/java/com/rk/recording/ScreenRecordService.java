package com.rk.recording;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenRecordService extends Service {

    public static final String ACTION_START = "com.rk.recording.START";
    public static final String ACTION_STOP  = "com.rk.recording.STOP";
    private static final String CH = "rk_rec";

    private MediaProjection projection;
    private RecorderEngine engine;
    private VirtualDisplay vd;
    private Uri outUri;
    private ParcelFileDescriptor pfd;
    private boolean recording = false;
    private AnnotationOverlay overlay;

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_STOP.equals(intent.getAction())) {
            stopRecording();
            stopSelf();
            return START_NOT_STICKY;
        }

        int code    = intent.getIntExtra("code", -1);
        Intent data = intent.getParcelableExtra("data");
        int sw      = intent.getIntExtra("sw", 1080);
        int sh      = intent.getIntExtra("sh", 1920);
        int dpi     = intent.getIntExtra("dpi", 320);
        int target  = intent.getIntExtra("target", 1080);
        boolean mic      = intent.getBooleanExtra("mic", false);
        boolean internal = intent.getBooleanExtra("internal", false);
        boolean clean    = intent.getBooleanExtra("clean", true);
        int fps          = intent.getIntExtra("fps", 30);
        int userBitrate  = intent.getIntExtra("bitrate", 0);
        boolean draw     = intent.getBooleanExtra("draw", false);

        startForeground(1, buildNotification());

        try {
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            if (projection == null) { toast("Could not start capture"); stopSelf(); return START_NOT_STICKY; }

            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopRecording(); }
            }, new Handler(Looper.getMainLooper()));

            int shortSide = Math.min(sw, sh);
            float scale = Math.min(1f, (float) target / shortSide);
            int vw = even(Math.round(sw * scale));
            int vh = even(Math.round(sh * scale));
            int bitrate = userBitrate > 0 ? userBitrate
                    : (target >= 1080 ? 6000000 : target >= 720 ? 3500000 : 1800000);

            openOutputFile();

            engine = new RecorderEngine(projection, pfd.getFileDescriptor(),
                    vw, vh, bitrate, fps, mic, internal, clean,
                    msg -> toast(msg));
            Surface surface = engine.prepare();

            vd = projection.createVirtualDisplay("RKRec", vw, vh, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface, null, null);

            engine.start();
            recording = true;

            if (draw && android.provider.Settings.canDrawOverlays(this)) {
                try { overlay = new AnnotationOverlay(this); overlay.show(); }
                catch (Exception e) { overlay = null; }
            }
            toast("Recording started");
        } catch (Exception e) {
            toast("Start failed: " + e.getMessage());
            stopRecording();
            stopSelf();
        }
        return START_STICKY;
    }

    private int even(int v) { return (v % 2 == 0) ? v : v - 1; }

    private void openOutputFile() throws Exception {
        String name = "RK_REC_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        cv.put(MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/RK RECORDING");
        cv.put(MediaStore.Video.Media.IS_PENDING, 1);
        outUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
        pfd = getContentResolver().openFileDescriptor(outUri, "rw");
    }

    private void stopRecording() {
        try { if (overlay != null) { overlay.hide(); overlay = null; } } catch (Exception ignored) {}
        try { if (engine != null) { engine.stop(); engine = null; } } catch (Exception ignored) {}
        try { if (vd != null) { vd.release(); vd = null; } } catch (Exception ignored) {}
        try { if (projection != null) { projection.stop(); projection = null; } } catch (Exception ignored) {}
        finalizeFile();
        if (recording) {
            recording = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            toast("Saved to Movies/RK RECORDING");
        }
    }

    private void finalizeFile() {
        try { if (pfd != null) { pfd.close(); pfd = null; } } catch (Exception ignored) {}
        if (outUri != null) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.IS_PENDING, 0);
            try { getContentResolver().update(outUri, cv, null, null); } catch (Exception ignored) {}
            outUri = null;
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "Recording",
                    NotificationManager.IMPORTANCE_LOW));
        }
        Intent stopIntent = new Intent(this, ScreenRecordService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CH)
                .setContentTitle("RK RECORDING")
                .setContentText("Recording your screen")
                .setSmallIcon(R.drawable.ic_stat_rec)
                .setUsesChronometer(true)
                .setOngoing(true)
                .addAction(0, "Stop & save", stopPi)
                .build();
    }

    private void toast(String s) {
        new Handler(Looper.getMainLooper()).post(
                () -> Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show());
    }

    @Override public void onDestroy() { super.onDestroy(); stopRecording(); }
}
