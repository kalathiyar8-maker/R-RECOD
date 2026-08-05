package com.rk.recording;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Transparent full-screen canvas for finger drawing. Captured by MediaProjection. */
public class DrawView extends View {

    private static class Stroke {
        final Path path = new Path();
        int color;
        float width;
    }

    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke current;
    private int color = 0xFFFF3B30;
    private float width = 10f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DrawView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void setColor(int c) { color = c; }
    public void setStrokeWidth(float w) { width = w; }

    public void undo() {
        if (!strokes.isEmpty()) { strokes.remove(strokes.size() - 1); invalidate(); }
    }
    public void clearAll() { strokes.clear(); current = null; invalidate(); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                current = new Stroke();
                current.color = color;
                current.width = width;
                current.path.moveTo(x, y);
                // tiny segment so a tap leaves a dot
                current.path.lineTo(x + 0.1f, y + 0.1f);
                strokes.add(current);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (current != null) { current.path.lineTo(x, y); invalidate(); }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                current = null;
                return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (Stroke s : strokes) {
            paint.setColor(s.color);
            paint.setStrokeWidth(s.width);
            canvas.drawPath(s.path, paint);
        }
    }
}
