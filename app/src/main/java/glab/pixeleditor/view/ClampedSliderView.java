package glab.pixeleditor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern Material 3 Expressive clamped slider that strictly prevents dragging
 * beyond minimum and maximum boundaries, with haptic feedback ticks on bounds hit.
 */
public class ClampedSliderView extends View {

    public interface OnChangeListener {
        void onValueChange(ClampedSliderView slider, float value, boolean fromUser);
    }

    private float valueFrom = 0f;
    private float valueTo = 100f;
    private float value = 0f;
    private float stepSize = 0f;

    private final List<OnChangeListener> listeners = new ArrayList<>();

    // Paints
    private final Paint trackBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF trackRect = new RectF();
    private final RectF activeTrackRect = new RectF();

    private float thumbRadius = 10f;
    private float trackHeight = 6f;
    private float haloRadius = 18f;
    private boolean isDragging = false;
    private boolean hitBoundaryThisDrag = false;

    public ClampedSliderView(Context context) {
        super(context);
        init(null);
    }

    public ClampedSliderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public ClampedSliderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        float density = getResources().getDisplayMetrics().density;
        thumbRadius = 9f * density;
        trackHeight = 6f * density;
        haloRadius = 18f * density;

        trackBgPaint.setStyle(Paint.Style.FILL);
        trackBgPaint.setColor(0xFF28334E);

        trackActivePaint.setStyle(Paint.Style.FILL);
        trackActivePaint.setColor(0xFF00E5BC);

        thumbFillPaint.setStyle(Paint.Style.FILL);
        thumbFillPaint.setColor(0xFFFFFFFF);

        thumbStrokePaint.setStyle(Paint.Style.STROKE);
        thumbStrokePaint.setColor(0xFF00E5BC);
        thumbStrokePaint.setStrokeWidth(2.5f * density);

        thumbHaloPaint.setStyle(Paint.Style.FILL);
        thumbHaloPaint.setColor(0x3300E5BC);

        if (attrs != null) {
            // Read standard android attributes or default
            int[] attrsArray = new int[]{
                    android.R.attr.value,
                    android.R.attr.max
            };
            try {
                android.content.res.TypedArray ta = getContext().obtainStyledAttributes(attrs, attrsArray);
                if (ta.hasValue(0)) {
                    value = ta.getFloat(0, 0f);
                }
                if (ta.hasValue(1)) {
                    valueTo = ta.getFloat(1, 100f);
                }
                ta.recycle();
            } catch (Exception ignored) {}
        }
    }

    public void addOnChangeListener(OnChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeOnChangeListener(OnChangeListener listener) {
        listeners.remove(listener);
    }

    public void clearOnChangeListeners() {
        listeners.clear();
    }

    public float getValue() {
        return value;
    }

    public void setValue(float val) {
        setValueInternal(val, false);
    }

    public float getValueFrom() {
        return valueFrom;
    }

    public void setValueFrom(float from) {
        this.valueFrom = from;
        if (value < valueFrom) {
            setValueInternal(valueFrom, false);
        } else {
            invalidate();
        }
    }

    public float getValueTo() {
        return valueTo;
    }

    public void setValueTo(float to) {
        this.valueTo = to;
        if (value > valueTo) {
            setValueInternal(valueTo, false);
        } else {
            invalidate();
        }
    }

    public float getStepSize() {
        return stepSize;
    }

    public void setStepSize(float step) {
        this.stepSize = Math.max(0f, step);
        invalidate();
    }

    private void setValueInternal(float newVal, boolean fromUser) {
        float min = Math.min(valueFrom, valueTo);
        float max = Math.max(valueFrom, valueTo);
        float clamped = Math.max(min, Math.min(max, newVal));

        if (stepSize > 0f) {
            float steps = Math.round((clamped - min) / stepSize);
            clamped = min + steps * stepSize;
            clamped = Math.max(min, Math.min(max, clamped));
        }

        if (Float.compare(this.value, clamped) != 0) {
            this.value = clamped;
            for (OnChangeListener l : listeners) {
                l.onValueChange(this, this.value, fromUser);
            }
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) (36 * getResources().getDisplayMetrics().density);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int height = desiredHeight;
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredHeight, heightSize);
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float paddingStart = getPaddingStart() + thumbRadius + 4f;
        float paddingEnd = getPaddingEnd() + thumbRadius + 4f;
        float trackWidth = Math.max(1f, w - paddingStart - paddingEnd);
        float centerY = h / 2f;

        // Background track
        trackRect.set(paddingStart, centerY - trackHeight / 2f, w - paddingEnd, centerY + trackHeight / 2f);
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackBgPaint);

        // Active track & thumb position
        float range = valueTo - valueFrom;
        float progress = (range != 0f) ? (value - valueFrom) / range : 0f;
        progress = Math.max(0f, Math.min(1f, progress));

        float thumbX = paddingStart + trackWidth * progress;

        activeTrackRect.set(paddingStart, centerY - trackHeight / 2f, thumbX, centerY + trackHeight / 2f);
        canvas.drawRoundRect(activeTrackRect, trackHeight / 2f, trackHeight / 2f, trackActivePaint);

        // Thumb Halo if touched
        if (isDragging) {
            canvas.drawCircle(thumbX, centerY, haloRadius, thumbHaloPaint);
        }

        // Thumb Body
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbFillPaint);
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbStrokePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int w = getWidth();
        float paddingStart = getPaddingStart() + thumbRadius + 4f;
        float paddingEnd = getPaddingEnd() + thumbRadius + 4f;
        float trackWidth = Math.max(1f, w - paddingStart - paddingEnd);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                hitBoundaryThisDrag = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                updateValueFromTouch(event.getX(), paddingStart, trackWidth);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                updateValueFromTouch(event.getX(), paddingStart, trackWidth);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                hitBoundaryThisDrag = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
        }

        return super.onTouchEvent(event);
    }

    private void updateValueFromTouch(float touchX, float paddingStart, float trackWidth) {
        float fraction;
        float min = Math.min(valueFrom, valueTo);
        float max = Math.max(valueFrom, valueTo);

        if (touchX >= paddingStart + trackWidth) {
            // Strictly clamped at max - cannot drag further
            fraction = 1.0f;
            if (!hitBoundaryThisDrag) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                } catch (Exception ignored) {}
                hitBoundaryThisDrag = true;
            }
        } else if (touchX <= paddingStart) {
            // Strictly clamped at min - cannot drag further
            fraction = 0.0f;
            if (!hitBoundaryThisDrag) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                } catch (Exception ignored) {}
                hitBoundaryThisDrag = true;
            }
        } else {
            fraction = (touchX - paddingStart) / trackWidth;
            hitBoundaryThisDrag = false;
        }

        float calculatedVal = valueFrom + fraction * (valueTo - valueFrom);
        setValueInternal(calculatedVal, true);
    }
}
