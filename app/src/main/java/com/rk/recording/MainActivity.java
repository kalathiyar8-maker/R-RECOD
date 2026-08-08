package com.rk.recording;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int[] FPS_VALUES = {30, 60, 24, 15};
    private static final int[] BR_VALUES  = {0, 4000000, 8000000, 12000000, 20000000};

    private MediaProjectionManager mpm;
    private RadioGroup qualityGroup;
    private Switch micSwitch, internalSwitch, cleanSwitch, drawSwitch, countdownSwitch, floatingSwitch;
    private Spinner fpsSpinner, bitrateSpinner;
    private TextView status;
    private ActivityResultLauncher<Intent> captureLauncher;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        qualityGroup   = findViewById(R.id.qualityGroup);
        micSwitch      = findViewById(R.id.micSwitch);
        internalSwitch = findViewById(R.id.internalSwitch);
        cleanSwitch    = findViewById(R.id.cleanSwitch);
        drawSwitch     = findViewById(R.id.drawSwitch);
        countdownSwitch = findViewById(R.id.countdownSwitch);
        floatingSwitch  = findViewById(R.id.floatingSwitch);
        fpsSpinner     = findViewById(R.id.fpsSpinner);
        bitrateSpinner = findViewById(R.id.bitrateSpinner);
        status         = findViewById(R.id.status);

        Button start = findViewById(R.id.startBtn);
        Button stop  = findViewById(R.id.stopBtn);
        Button gallery = findViewById(R.id.galleryBtn);

        captureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                res -> {
                    if (res.getResultCode() == Activity.RESULT_OK && res.getData() != null) {
                        startRecording(res.getResultCode(), res.getData());
                    } else {
                        status.setText(R.string.status_cancelled);
                    }
                });

        start.setOnClickListener(v -> requestPermsThenCapture());
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, ScreenRecordService.class);
            i.setAction(ScreenRecordService.ACTION_STOP);
            startService(i);
            status.setText(R.string.status_stopped);
        });
        gallery.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
    }

    private void requestPermsThenCapture() {
        if ((drawSwitch.isChecked() || countdownSwitch.isChecked() || floatingSwitch.isChecked())
                && !android.provider.Settings.canDrawOverlays(this)) {
            status.setText(R.string.need_overlay);
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
            return;
        }
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if ((micSwitch.isChecked() || internalSwitch.isChecked())
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 100);
        } else {
            launchCapture();
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        launchCapture();
    }

    private void launchCapture() {
        captureLauncher.launch(mpm.createScreenCaptureIntent());
    }

    private void startRecording(int code, Intent data) {
        DisplayMetrics m = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);

        int target = 1080;
        int id = qualityGroup.getCheckedRadioButtonId();
        if (id == R.id.q480) target = 480;
        else if (id == R.id.q720) target = 720;

        int fps = FPS_VALUES[clampIdx(fpsSpinner.getSelectedItemPosition(), FPS_VALUES.length)];
        int bitrate = BR_VALUES[clampIdx(bitrateSpinner.getSelectedItemPosition(), BR_VALUES.length)];

        Intent i = new Intent(this, ScreenRecordService.class);
        i.setAction(ScreenRecordService.ACTION_START);
        i.putExtra("code", code);
        i.putExtra("data", data);
        i.putExtra("sw", m.widthPixels);
        i.putExtra("sh", m.heightPixels);
        i.putExtra("dpi", m.densityDpi);
        i.putExtra("target", target);
        i.putExtra("mic", micSwitch.isChecked());
        i.putExtra("internal", internalSwitch.isChecked());
        i.putExtra("clean", cleanSwitch.isChecked());
        i.putExtra("fps", fps);
        i.putExtra("bitrate", bitrate);
        i.putExtra("draw", drawSwitch.isChecked());
        i.putExtra("countdown", countdownSwitch.isChecked());
        i.putExtra("floating", floatingSwitch.isChecked());
        ContextCompat.startForegroundService(this, i);
        status.setText(R.string.status_recording);
    }

    private int clampIdx(int i, int len) { return (i < 0 || i >= len) ? 0 : i; }
}
