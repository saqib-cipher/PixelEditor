package glab.pixeleditor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class ScrubRulerView extends View {

    public interface OnScrubListener {
        void onScrub(float delta);
    }

    private OnScrubListener listener;
    private float offset = 0f;
    private float lastTouchX = 0f;
    private float tickSpacing = 16f; // pixels between ticks

    private boolean hasBounds = false;
    private float minValue = -Float.MAX_VALUE;
    private float maxValue = Float.MAX_VALUE;
    private float currentValue = 0f;
    private float valuePerPixel = 0.05f;

    private boolean isActive = true;
    private float downX = 0f;
    private float downY = 0f;
    private float touchSlop = 8f;

    public void setActive(boolean active) {
        this.isActive = active;
        setAlpha(active ? 1.0f : 0.45f);
        invalidate();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setBounds(float min, float max) {
        this.hasBounds = true;
        this.minValue = Math.min(min, max);
        this.maxValue = Math.max(min, max);
        this.currentValue = Math.max(this.minValue, Math.min(this.maxValue, this.currentValue));
    }

    public void setBounds(float min, float max, float current, float valuePerPixel) {
        this.hasBounds = true;
        this.minValue = Math.min(min, max);
        this.maxValue = Math.max(min, max);
        this.currentValue = Math.max(this.minValue, Math.min(this.maxValue, current));
        this.valuePerPixel = valuePerPixel > 0 ? valuePerPixel : 0.05f;
    }

    public void setCurrentValue(float val) {
        this.currentValue = Math.max(minValue, Math.min(maxValue, val));
    }

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ScrubRulerView(Context context) {
        super(context);
        init();
    }

    public ScrubRulerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScrubRulerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        tickSpacing = 8f * density;
        try {
            touchSlop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        } catch (Exception e) {
            touchSlop = 8f * density;
        }

        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0x00000000);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setColor(0x4094A3B8);
        tickPaint.setStrokeWidth(1.5f * density);

        majorTickPaint.setStyle(Paint.Style.STROKE);
        majorTickPaint.setColor(0x8094A3B8);
        majorTickPaint.setStrokeWidth(2f * density);

        centerLinePaint.setStyle(Paint.Style.STROKE);
        centerLinePaint.setColor(0xFF00E5BC);
        centerLinePaint.setStrokeWidth(3f * density);
    }

    public void setOnScrubListener(OnScrubListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float centerX = w / 2f;
        float topPad = 8f;
        float bottomPad = h - 8f;
        float midY = h / 2f;

        // Draw ticks across the full width
        float startX = (offset % tickSpacing);
        int tickIndex = (int) Math.floor(-offset / tickSpacing) - (int) (centerX / tickSpacing);

        for (float x = startX - tickSpacing * 2; x <= w + tickSpacing * 2; x += tickSpacing) {
            int currentTick = Math.round((x - centerX - offset) / tickSpacing);
            boolean isMajor = (currentTick % 5 == 0);

            float y1 = isMajor ? topPad : (midY - 14f);
            float y2 = isMajor ? bottomPad : (midY + 14f);

            canvas.drawLine(x, y1, x, y2, isMajor ? majorTickPaint : tickPaint);
        }

        // Draw Center Green Indicator Line
        canvas.drawLine(centerX, topPad - 2, centerX, bottomPad + 2, centerLinePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isActive) {
            // Inactive state: pass vertical scroll events to parent view, handle tap to activate
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float mdx = Math.abs(event.getX() - downX);
                    float mdy = Math.abs(event.getY() - downY);
                    if (mdy > touchSlop) {
                        // Vertical scroll initiated by parent
                        return false;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    if (dx < touchSlop && dy < touchSlop) {
                        performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return false;
            }
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                downX = event.getX();
                downY = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                float currentX = event.getX();
                float dx = currentX - lastTouchX;
                lastTouchX = currentX;

                if (hasBounds) {
                    float testVal = currentValue + dx * valuePerPixel;
                    if (testVal > maxValue) {
                        dx = (maxValue - currentValue) / valuePerPixel;
                        currentValue = maxValue;
                    } else if (testVal < minValue) {
                        dx = (minValue - currentValue) / valuePerPixel;
                        currentValue = minValue;
                    } else {
                        currentValue = testVal;
                    }

                    if (Math.abs(dx) < 0.0001f) {
                        // Max or min reached - cannot scroll further
                        return true;
                    }
                }

                offset += dx;
                invalidate();

                if (Math.abs(dx) > 1f) {
                    try {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    } catch (Exception ignored) {}
                }

                if (listener != null) {
                    listener.onScrub(dx);
                }
                return true;

            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                float upDx = Math.abs(event.getX() - downX);
                float upDy = Math.abs(event.getY() - downY);
                if (upDx < touchSlop && upDy < touchSlop) {
                    performClick();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
