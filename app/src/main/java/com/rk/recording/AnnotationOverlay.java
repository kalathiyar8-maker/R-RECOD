package com.rk.recording;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Floating draw canvas + a small movable toolbar (pen, colors, undo, clear). */
public class AnnotationOverlay {

    private final Context ctx;
    private final WindowManager wm;
    private DrawView drawView;
    private View toolbar;
    private TextView penBtn;
    private WindowManager.LayoutParams drawLp, barLp;
    private boolean penOn = true;

    private final int[] COLORS = {0xFFFF3B30, 0xFFFFD60A, 0xFF34FF7A, 0xFF4C8DFF, 0xFFFFFFFF};
    private final float[] WIDTHS = {8f, 14f, 22f};

    public AnnotationOverlay(Context c) {
        ctx = c;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    }

    private int dp(float v) { return Math.round(v * ctx.getResources().getDisplayMetrics().density); }
    private int baseFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
    }

    public void show() {
        drawView = new DrawView(ctx);
        drawLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                baseFlags(), PixelFormat.TRANSLUCENT);
        drawLp.gravity = Gravity.TOP | Gravity.START;
        wm.addView(drawView, drawLp);
        setPen(true);

        toolbar = buildToolbar();
        barLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                baseFlags(), PixelFormat.TRANSLUCENT);
        barLp.gravity = Gravity.TOP | Gravity.START;
        barLp.x = dp(16);
        barLp.y = dp(120);
        wm.addView(toolbar, barLp);
    }

    private void setPen(boolean on) {
        penOn = on;
        drawLp.flags = on ? baseFlags()
                          : (baseFlags() | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        try { wm.updateViewLayout(drawView, drawLp); } catch (Exception ignored) {}
        if (penBtn != null) {
            penBtn.setBackground(pill(on ? 0xFFE5484D : 0x33FFFFFF));
            penBtn.setText(on ? "\u270E Pen" : "\u270E Off");
        }
    }

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF00E1116);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0xFF2A303A);
        bar.setBackground(bg);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));

        // drag handle
        TextView handle = label("\u2630", 0xFF9AA2B1, 18);
        handle.setPadding(dp(6), dp(4), dp(10), dp(4));
        handle.setOnTouchListener(new View.OnTouchListener() {
            int sx, sy; float rx, ry;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = barLp.x; sy = barLp.y; rx = e.getRawX(); ry = e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE:
                        barLp.x = sx + (int) (e.getRawX() - rx);
                        barLp.y = sy + (int) (e.getRawY() - ry);
                        try { wm.updateViewLayout(toolbar, barLp); } catch (Exception ignored) {}
                        return true;
                }
                return false;
            }
        });
        bar.addView(handle);

        // pen toggle
        penBtn = label("\u270E Pen", 0xFFFFFFFF, 14);
        penBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        penBtn.setBackground(pill(0xFFE5484D));
        penBtn.setOnClickListener(v -> setPen(!penOn));
        addSpace(bar, 8);
        bar.addView(penBtn);

        // color dots
        for (int col : COLORS) {
            addSpace(bar, 8);
            View dot = new View(ctx);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(col);
            d.setStroke(dp(2), 0x66FFFFFF);
            dot.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(26), dp(26));
            dot.setLayoutParams(lp);
            dot.setOnClickListener(v -> { drawView.setColor(col); setPen(true); });
            bar.addView(dot);
        }

        // stroke width cycle
        addSpace(bar, 10);
        TextView size = label("\u25CF", 0xFFE6E9EF, 14);
        size.setPadding(dp(8), dp(6), dp(8), dp(6));
        size.setBackground(pill(0x33FFFFFF));
        final int[] wi = {1};
        size.setOnClickListener(v -> {
            wi[0] = (wi[0] + 1) % WIDTHS.length;
            drawView.setStrokeWidth(WIDTHS[wi[0]]);
            size.setTextSize(wi[0] == 0 ? 11 : wi[0] == 1 ? 15 : 19);
        });
        bar.addView(size);

        // undo
        addSpace(bar, 10);
        TextView undo = label("\u21B6", 0xFFE6E9EF, 18);
        undo.setPadding(dp(8), dp(4), dp(8), dp(4));
        undo.setOnClickListener(v -> drawView.undo());
        bar.addView(undo);

        // clear
        addSpace(bar, 6);
        TextView clear = label("\u2715", 0xFFFF8A8C, 16);
        clear.setPadding(dp(8), dp(4), dp(8), dp(4));
        clear.setOnClickListener(v -> drawView.clearAll());
        bar.addView(clear);

        return bar;
    }

    private TextView label(String t, int color, int sizeSp) {
        TextView tv = new TextView(ctx);
        tv.setText(t);
        tv.setTextColor(color);
        tv.setTextSize(sizeSp);
        tv.setIncludeFontPadding(false);
        return tv;
    }
    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        return g;
    }
    private void addSpace(LinearLayout bar, int w) {
        View s = new View(ctx);
        s.setLayoutParams(new LinearLayout.LayoutParams(dp(w), dp(1)));
        bar.addView(s);
    }

    public void hide() {
        try { if (drawView != null) wm.removeView(drawView); } catch (Exception ignored) {}
        try { if (toolbar != null) wm.removeView(toolbar); } catch (Exception ignored) {}
        drawView = null; toolbar = null; penBtn = null;
    }
}
