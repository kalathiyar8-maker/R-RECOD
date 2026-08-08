package com.rk.recording;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Small movable bubble over the screen: red dot + timer + Pause/Resume + Stop. */
public class ControlOverlay {

    public interface Listener {
        boolean onPauseToggle();  // returns true if now paused
        void onStop();
    }

    private final Context ctx;
    private final WindowManager wm;
    private final Listener listener;
    private View root;
    private TextView timeTv, pauseTv;
    private WindowManager.LayoutParams lp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private long baseElapsed = 0, segStart = 0;
    private boolean running = true;

    public ControlOverlay(Context c, Listener l) {
        ctx = c; listener = l;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    }

    private int dp(float v) { return Math.round(v * ctx.getResources().getDisplayMetrics().density); }

    public void show() {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF00E1116);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), 0xFF2A303A);
        bar.setBackground(bg);
        bar.setPadding(dp(10), dp(6), dp(10), dp(6));

        // draggable grip: dot + timer
        LinearLayout grip = new LinearLayout(ctx);
        grip.setOrientation(LinearLayout.HORIZONTAL);
        grip.setGravity(Gravity.CENTER_VERTICAL);
        grip.setPadding(dp(4), dp(4), dp(8), dp(4));

        View dot = new View(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0xFFE5484D);
        dot.setBackground(d);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(11), dp(11));
        dlp.rightMargin = dp(8);
        grip.addView(dot, dlp);

        timeTv = new TextView(ctx);
        timeTv.setText("00:00");
        timeTv.setTextColor(0xFFE6E9EF);
        timeTv.setTextSize(15);
        grip.addView(timeTv);
        bar.addView(grip);

        pauseTv = makeBtn("Pause", 0x33FFFFFF, 0xFFE6E9EF);
        pauseTv.setOnClickListener(v -> {
            boolean paused = listener.onPauseToggle();
            if (paused) { pauseTimer(); pauseTv.setText("Resume"); }
            else { resumeTimer(); pauseTv.setText("Pause"); }
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.leftMargin = dp(10);
        bar.addView(pauseTv, pp);

        TextView stop = makeBtn("Stop", 0xFFE5484D, 0xFFFFFFFF);
        stop.setOnClickListener(v -> listener.onStop());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = dp(8);
        bar.addView(stop, sp);

        root = bar;
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(16); lp.y = dp(60);
        try { wm.addView(root, lp); } catch (Exception ignored) {}

        grip.setOnTouchListener(new View.OnTouchListener() {
            int sx, sy; float rx, ry;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = lp.x; sy = lp.y; rx = e.getRawX(); ry = e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = sx + (int) (e.getRawX() - rx);
                        lp.y = sy + (int) (e.getRawY() - ry);
                        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
                        return true;
                }
                return false;
            }
        });

        segStart = SystemClock.elapsedRealtime();
        running = true;
        tick();
    }

    private TextView makeBtn(String t, int bgColor, int textColor) {
        TextView b = new TextView(ctx);
        b.setText(t);
        b.setTextColor(textColor);
        b.setTextSize(14);
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setColor(bgColor);
        g.setCornerRadius(dp(16));
        b.setBackground(g);
        return b;
    }

    private long elapsed() {
        long e = baseElapsed;
        if (running) e += SystemClock.elapsedRealtime() - segStart;
        return e;
    }
    private void tick() {
        long s = elapsed() / 1000;
        timeTv.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
        h.postDelayed(this::tick, 500);
    }
    private void pauseTimer() { baseElapsed += SystemClock.elapsedRealtime() - segStart; running = false; }
    private void resumeTimer() { segStart = SystemClock.elapsedRealtime(); running = true; }

    public void hide() {
        h.removeCallbacksAndMessages(null);
        try { if (root != null) wm.removeView(root); } catch (Exception ignored) {}
        root = null;
    }
}
