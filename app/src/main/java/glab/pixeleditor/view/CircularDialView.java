package glab.pixeleditor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class CircularDialView extends View {

    public interface OnAngleChangeListener {
        void onAngleChanged(float angleDegrees);
    }

    private OnAngleChangeListener listener;
    private float currentAngle = 0f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcBounds = new RectF();
    private float lastTouchAngle = 0f;

    public CircularDialView(Context context) {
        super(context);
        init();
    }

    public CircularDialView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularDialView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(0xFF24304A);
        trackPaint.setStrokeWidth(6f);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setColor(0x5594A3B8);
        tickPaint.setStrokeWidth(2f);

        majorTickPaint.setStyle(Paint.Style.STROKE);
        majorTickPaint.setColor(0xFF00E5BC);
        majorTickPaint.setStrokeWidth(3.5f);

        activeArcPaint.setStyle(Paint.Style.STROKE);
        activeArcPaint.setColor(0xFF00E5BC);
        activeArcPaint.setStrokeWidth(6f);
        activeArcPaint.setStrokeCap(Paint.Cap.ROUND);

        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(0xFF00E5BC);

        centerTextPaint.setStyle(Paint.Style.FILL);
        centerTextPaint.setColor(0xFFFFFFFF);
        centerTextPaint.setTextSize(36f);
        centerTextPaint.setTextAlign(Paint.Align.CENTER);
        centerTextPaint.setFakeBoldText(true);
    }

    public void setOnAngleChangeListener(OnAngleChangeListener listener) {
        this.listener = listener;
    }

    public void setAngle(float degrees) {
        this.currentAngle = (degrees % 360f + 360f) % 360f;
        invalidate();
    }

    public float getAngle() {
        return currentAngle;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 24f;
        if (radius <= 0) return;

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // 1. Draw Background Track
        canvas.drawCircle(cx, cy, radius, trackPaint);

        // 2. Draw Ticks (every 15 degrees)
        for (int deg = 0; deg < 360; deg += 15) {
            double rad = Math.toRadians(deg - 90);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            boolean isMajor = (deg % 45 == 0);
            float innerR = isMajor ? radius - 16f : radius - 10f;
            float outerR = radius - 2f;

            canvas.drawLine(
                    cx + innerR * cos, cy + innerR * sin,
                    cx + outerR * cos, cy + outerR * sin,
                    isMajor ? majorTickPaint : tickPaint
            );
        }

        // 3. Draw Active Arc from 0° (top) to currentAngle
        canvas.drawArc(arcBounds, -90f, currentAngle, false, activeArcPaint);

        // 4. Draw Thumb Knob at currentAngle
        double thumbRad = Math.toRadians(currentAngle - 90);
        float thumbX = cx + radius * (float) Math.cos(thumbRad);
        float thumbY = cy + radius * (float) Math.sin(thumbRad);
        canvas.drawCircle(thumbX, thumbY, 14f, thumbPaint);

        // 5. Center Angle Text
        Paint.FontMetrics fm = centerTextPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(String.format(java.util.Locale.US, "%.1f°", currentAngle), cx, textY, centerTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float touchX = event.getX() - cx;
        float touchY = event.getY() - cy;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                updateAngleFromTouch(touchX, touchY);
                return true;

            case MotionEvent.ACTION_MOVE:
                updateAngleFromTouch(touchX, touchY);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateAngleFromTouch(float x, float y) {
        double rad = Math.atan2(y, x);
        float deg = (float) Math.toDegrees(rad) + 90f;
        float normalized = (deg % 360f + 360f) % 360f;

        this.currentAngle = Math.round(normalized * 2f) / 2f; // Snap to 0.5 deg
        invalidate();

        if (listener != null) {
            listener.onAngleChanged(this.currentAngle);
        }
    }
}
