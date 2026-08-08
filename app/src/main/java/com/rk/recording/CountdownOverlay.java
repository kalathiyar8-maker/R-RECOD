package com.rk.recording;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Full-screen 3-2-1 countdown with a Skip button, shown before recording begins. */
public class CountdownOverlay {

    private final Context ctx;
    private final WindowManager wm;
    private View root;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean done = false;

    public CountdownOverlay(Context c) {
        ctx = c;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    }

    private int dp(float v) { return Math.round(v * ctx.getResources().getDisplayMetrics().density); }

    public void show(int seconds, Runnable onDone) {
        FrameLayout fl = new FrameLayout(ctx);
        fl.setBackgroundColor(0xB3000000);

        final TextView num = new TextView(ctx);
        num.setTextColor(Color.WHITE);
        num.setTextSize(96);
        num.setText(String.valueOf(seconds));
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        np.gravity = Gravity.CENTER;
        fl.addView(num, np);

        TextView skip = new TextView(ctx);
        skip.setText("Skip countdown");
        skip.setTextColor(Color.WHITE);
        skip.setTextSize(15);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x33FFFFFF);
        g.setCornerRadius(dp(22));
        skip.setBackground(g);
        skip.setPadding(dp(22), dp(10), dp(22), dp(10));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        sp.bottomMargin = dp(140);
        fl.addView(skip, sp);

        root = fl;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        try { wm.addView(root, lp); } catch (Exception e) { onDone.run(); return; }

        skip.setOnClickListener(v -> finish(onDone));

        final int[] n = {seconds};
        Runnable tick = new Runnable() {
            @Override public void run() {
                n[0]--;
                if (n[0] <= 0) finish(onDone);
                else { num.setText(String.valueOf(n[0])); h.postDelayed(this, 1000); }
            }
        };
        h.postDelayed(tick, 1000);
    }

    private void finish(Runnable onDone) {
        if (done) return;
        done = true;
        hide();
        onDone.run();
    }

    public void hide() {
        h.removeCallbacksAndMessages(null);
        try { if (root != null) wm.removeView(root); } catch (Exception ignored) {}
        root = null;
    }
}
