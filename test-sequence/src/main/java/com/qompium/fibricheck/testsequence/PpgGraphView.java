package com.qompium.fibricheck.testsequence;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayDeque;
import java.util.Deque;

public class PpgGraphView extends View {

    private static final float STEP_DP = 2.5f;

    private int maxSamples = 48; // fallback before first layout
    private final Deque<Double> samples = new ArrayDeque<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private final RectF bgRect = new RectF();
    private float cornerRadius;

    public PpgGraphView(Context context) {
        super(context);
        init();
    }

    public PpgGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PpgGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        cornerRadius = 12f * density;

        linePaint.setColor(Color.parseColor("#63b3a6"));
        linePaint.setStrokeWidth(3f * density);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setColor(Color.parseColor("#2063b3a6"));
        fillPaint.setStyle(Paint.Style.FILL);

        bgPaint.setColor(Color.parseColor("#1A2634"));
        bgPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) {
            float stepPx = STEP_DP * getResources().getDisplayMetrics().density;
            maxSamples = Math.max(2, (int) (w / stepPx));
            while (samples.size() > maxSamples) samples.removeFirst();
        }
    }

    /** Must be called on the UI thread. */
    public void addSample(double ppg) {
        samples.addLast(ppg);
        while (samples.size() > maxSamples) samples.removeFirst();
        invalidate();
    }

    /** Must be called on the UI thread. */
    public void clear() {
        samples.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        bgRect.set(0, 0, w, h);
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint);

        int count = samples.size();
        if (count < 2) return;

        Double[] pts = samples.toArray(new Double[0]);

        double min = pts[0], max = pts[0];
        for (double v : pts) {
            if (v < min) min = v;
            if (v > max) max = v;
        }

        double range = max - min;

        float padY = h * 0.12f;
        float drawH = h - 2 * padY;
        float xStep = (float) w / (maxSamples - 1);
        float xOffset = (maxSamples - count) * xStep;

        linePath.reset();
        fillPath.reset();

        float x0 = xOffset;
        float y0 = (range == 0) ? padY + drawH / 2f : padY + (float) ((max - pts[0]) / range) * drawH;
        linePath.moveTo(x0, y0);
        fillPath.moveTo(x0, h);
        fillPath.lineTo(x0, y0);

        for (int i = 1; i < count; i++) {
            float x = xOffset + i * xStep;
            float y = (range == 0) ? padY + drawH / 2f : padY + (float) ((max - pts[i]) / range) * drawH;
            linePath.lineTo(x, y);
            fillPath.lineTo(x, y);
        }

        float xLast = xOffset + (count - 1) * xStep;
        fillPath.lineTo(xLast, h);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }
}
