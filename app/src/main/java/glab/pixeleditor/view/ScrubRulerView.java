package glab.pixeleditor.view;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * A horizontal scrub ruler (like exposure/rotation controls in photo editors).
 *
 * Direction convention (fixed): dragging RIGHT increases the value, dragging
 * LEFT decreases it. This matches how the tick marks always animate — the
 * ruler track scrolls in the direction that makes intuitive sense with the
 * finger. If you need the opposite convention for a specific control, call
 * {@link #setInvertDirection(boolean)} instead of touching the math.
 */
public class ScrubRulerView extends View {

    public interface OnScrubListener {
        void onScrub(float delta);
    }

    /** Optional: get notified when the user drags into/out of a hard limit. */
    public interface OnBoundsListener {
        void onBoundsReached(boolean atMax);
        void onBoundsReleased();
    }

    private OnScrubListener scrubListener;
    private OnBoundsListener boundsListener;

    // --- Value / bounds state -------------------------------------------------
    // `currentValue` is the numeric value (semantic meaning is entirely up to
    // the caller — see effectiveDx / invertDirection). `offset` is the pixel
    // position used for rendering; during a drag it tracks raw touch input
    // directly (see onTouchEvent) so it can never visually disagree with the
    // thumb, no matter what sign convention currentValue ends up using.
    private float currentValue = 0f;
    private float rawAccumulator = 0f; // unclamped running total for the active gesture only
    private float valuePerPixel = 0.05f;
    private boolean hasBounds = false;
    private float minValue = -Float.MAX_VALUE;
    private float maxValue = Float.MAX_VALUE;
    private boolean invertDirection = false;

    private float offset = 0f;         // px offset actually used for tick rendering
    private float tickSpacing = 16f;   // px between ticks

    // --- Touch state ------------------------------------------------------
    private boolean isActive = true;
    private boolean isDragging = false;
    private float lastTouchX = 0f;
    private float downX = 0f;
    private float downY = 0f;
    private float touchSlop = 8f;
    private static final float OVERSCROLL_VISUAL_DAMPING = 0.35f;

    // --- Overscroll / bounds feedback --------------------------------------
    private boolean isOverscrolled = false;
    private int lastHapticTickIndex = 0;
    private float boundsFlash = 0f; // 0 = normal color, 1 = full warning color
    private ValueAnimator settleAnimator;
    private ValueAnimator flashAnimator;
    private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();

    private static final int COLOR_NORMAL = 0xFF00E5BC; // teal accent
    private static final int COLOR_WARNING = 0xFFFF5A5A; // limit-reached red

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setColor(0x4094A3B8);
        tickPaint.setStrokeWidth(1.5f * density);

        majorTickPaint.setStyle(Paint.Style.STROKE);
        majorTickPaint.setColor(0x8094A3B8);
        majorTickPaint.setStrokeWidth(2f * density);

        centerLinePaint.setStyle(Paint.Style.STROKE);
        centerLinePaint.setColor(COLOR_NORMAL);
        centerLinePaint.setStrokeWidth(3f * density);
    }

    // --- Public API -------------------------------------------------------

    public void setOnScrubListener(OnScrubListener listener) {
        this.scrubListener = listener;
    }

    public void setOnBoundsListener(OnBoundsListener listener) {
        this.boundsListener = listener;
    }

    /** Flip which way the drag maps to value change, without touching internals. */
    public void setInvertDirection(boolean invert) {
        this.invertDirection = invert;
        recomputeOffset();
        invalidate();
    }

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
        this.currentValue = clampHard(this.currentValue);
        recomputeOffset();
    }

    public void setBounds(float min, float max, float current, float valuePerPixel) {
        this.hasBounds = true;
        this.minValue = Math.min(min, max);
        this.maxValue = Math.max(min, max);
        this.valuePerPixel = valuePerPixel > 0 ? valuePerPixel : 0.05f;
        this.currentValue = clampHard(current);
        recomputeOffset();
        invalidate();
    }

    public void setCurrentValue(float val) {
        this.currentValue = clampHard(val);
        recomputeOffset();
        invalidate();
    }

    public float getCurrentValue() {
        return currentValue;
    }

    public boolean isAtMax() {
        return hasBounds && currentValue >= maxValue;
    }

    public boolean isAtMin() {
        return hasBounds && currentValue <= minValue;
    }

    private float clampHard(float val) {
        if (!hasBounds) return val;
        return Math.max(minValue, Math.min(maxValue, val));
    }

    // --- Rendering ----------------------------------------------------------

    /**
     * Maps currentValue -> a resting pixel offset. Used ONLY when there is no
     * active finger on the screen (initial state, or an external setCurrentValue
     * call e.g. a reset button). While dragging, `offset` is driven directly and
     * exclusively by raw touch pixels in onTouchEvent — see isDragging guard —
     * so the ticks can never visually disagree with the thumb mid-gesture,
     * regardless of what sign convention the value itself uses.
     */
    private void recomputeOffset() {
        if (isDragging) return;
        offset = (invertDirection ? -1f : 1f) * (currentValue / valuePerPixel);
    }

    /** Java's % can return a negative remainder for negative offsets; this can't. */
    private static float positiveMod(float value, float mod) {
        float r = value % mod;
        return r < 0 ? r + mod : r;
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

        int flashColor = boundsFlash > 0f
                ? (int) argbEvaluator.evaluate(boundsFlash, COLOR_NORMAL, COLOR_WARNING)
                : COLOR_NORMAL;

        float startX = positiveMod(offset, tickSpacing);

        for (float x = startX - tickSpacing * 2; x <= w + tickSpacing * 2; x += tickSpacing) {
            int currentTick = Math.round((x - centerX - offset) / tickSpacing);
            boolean isMajor = (currentTick % 5 == 0);

            float y1 = isMajor ? topPad : (midY - 14f);
            float y2 = isMajor ? bottomPad : (midY + 14f);

            Paint p = isMajor ? majorTickPaint : tickPaint;
            if (isMajor && boundsFlash > 0f) {
                p.setColor(blendAlpha(flashColor, 0x80));
            } else if (isMajor) {
                p.setColor(0x8094A3B8);
            }
            canvas.drawLine(x, y1, x, y2, p);
        }

        centerLinePaint.setColor(flashColor);
        canvas.drawLine(centerX, topPad - 2, centerX, bottomPad + 2, centerLinePaint);
    }

    private static int blendAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    // --- Touch handling -----------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isActive) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
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
                cancelAnimators();
                isDragging = true;
                lastTouchX = event.getX();
                downX = event.getX();
                downY = event.getY();
                rawAccumulator = currentValue; // gesture always starts from a settled, in-bounds value
                lastHapticTickIndex = Math.round(offset / tickSpacing);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float currentX = event.getX();
                float dx = currentX - lastTouchX;
                lastTouchX = currentX;

                // Numeric value: this sign convention is independent of rendering,
                // and can also be overridden entirely by the caller (e.g. Mode 4
                // calls setCurrentValue() with its own formula from onScrub).
                float effectiveDx = invertDirection ? -dx : dx;
                rawAccumulator += effectiveDx * valuePerPixel;

                boolean wasOverscrolled = isOverscrolled;
                boolean atMaxNow = false;
                boolean atMinNow = false;

                if (hasBounds && rawAccumulator > maxValue) {
                    currentValue = maxValue + rubberBand(rawAccumulator - maxValue);
                    atMaxNow = true;
                } else if (hasBounds && rawAccumulator < minValue) {
                    currentValue = minValue - rubberBand(minValue - rawAccumulator);
                    atMinNow = true;
                } else {
                    currentValue = rawAccumulator;
                }

                isOverscrolled = atMaxNow || atMinNow;
                if (isOverscrolled && !wasOverscrolled) {
                    startFlash(true);
                    try {
                        performHapticFeedback(HapticFeedbackConstants.REJECT);
                    } catch (Exception ignored) {
                        try {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        } catch (Exception ignored2) { /* no-op */ }
                    }
                    if (boundsListener != null) boundsListener.onBoundsReached(atMaxNow);
                } else if (!isOverscrolled && wasOverscrolled) {
                    startFlash(false);
                    if (boundsListener != null) boundsListener.onBoundsReleased();
                }

                // Rendering: ALWAYS the raw finger delta, 1:1. This can never
                // disagree with the thumb because it IS the thumb's movement —
                // only damped (not reversed) once past a bound, for the rubber-band feel.
                float visualDx = isOverscrolled ? dx * OVERSCROLL_VISUAL_DAMPING : dx;
                offset += visualDx;
                invalidate();

                // Haptic tick only when crossing an actual tick boundary — a real
                // ratchet feel instead of firing on every pixel of movement.
                int tickIndex = Math.round(offset / tickSpacing);
                if (tickIndex != lastHapticTickIndex && !isOverscrolled) {
                    lastHapticTickIndex = tickIndex;
                    try {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    } catch (Exception ignored) { /* no-op */ }
                }

                if (scrubListener != null) {
                    scrubListener.onScrub(effectiveDx);
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                float upDx = Math.abs(event.getX() - downX);
                float upDy = Math.abs(event.getY() - downY);
                settleIfNeeded();
                isDragging = false;
                if (upDx < touchSlop && upDy < touchSlop) {
                    performClick();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                settleIfNeeded();
                isDragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    /** Diminishing-returns resistance curve so overscroll feels springy, not stuck. */
    private float rubberBand(float excess) {
        float range = hasBounds ? Math.max(1f, maxValue - minValue) : 100f;
        float maxStretch = range * 0.08f + 4f;
        float resistance = 0.55f;
        return maxStretch * (1f - (float) Math.exp(-excess / (maxStretch * resistance + 0.0001f)));
    }

    private void startFlash(boolean toWarning) {
        if (flashAnimator != null) flashAnimator.cancel();
        flashAnimator = ValueAnimator.ofFloat(boundsFlash, toWarning ? 1f : 0f);
        flashAnimator.setDuration(toWarning ? 120 : 220);
        flashAnimator.addUpdateListener(a -> {
            boundsFlash = (float) a.getAnimatedValue();
            invalidate();
        });
        flashAnimator.start();
    }

    /**
     * Spring the value AND the visual offset back to the bound together, on
     * release. Both are lerped from the same fraction so they can't drift
     * apart mid-animation.
     */
    private void settleIfNeeded() {
        if (!isOverscrolled) return;
        final float fromValue = currentValue;
        final float toValue = rawAccumulator > maxValue ? maxValue : minValue;
        final float fromOffset = offset;
        final float toOffset = (invertDirection ? -1f : 1f) * (toValue / valuePerPixel);

        if (settleAnimator != null) settleAnimator.cancel();
        settleAnimator = ValueAnimator.ofFloat(0f, 1f);
        settleAnimator.setDuration(220);
        settleAnimator.setInterpolator(new DecelerateInterpolator());
        settleAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            currentValue = fromValue + (toValue - fromValue) * t;
            offset = fromOffset + (toOffset - fromOffset) * t;
            invalidate();
        });
        settleAnimator.start();

        rawAccumulator = toValue;
        isOverscrolled = false;
        startFlash(false);
        if (boundsListener != null) boundsListener.onBoundsReleased();
    }

    private void cancelAnimators() {
        if (settleAnimator != null) { settleAnimator.cancel(); settleAnimator = null; }
        if (flashAnimator != null) { flashAnimator.cancel(); flashAnimator = null; }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}