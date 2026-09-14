package glab.pixeleditor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class GradientBarView extends View {

    public interface OnGradientChangeListener {
        void onStopSelected(int stopIndex, int color);
        void onOffsetsChanged(float startOffset, float endOffset, boolean fromUser);
    }

    private int startColor = 0xFF000000;
    private int endColor = 0xFFFFFFFF;
    private float startOffset = 0.0f;
    private float endOffset = 1.0f;
    private int selectedStopIndex = 0; // 0: Start, 1: End

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF trackRect = new RectF();
    private final RectF thumbRect = new RectF();

    private int draggingThumb = -1; // -1: none, 0: start, 1: end
    private OnGradientChangeListener listener;

    private float density;

    public GradientBarView(Context context) {
        super(context);
        init();
    }

    public GradientBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GradientBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        trackBorderPaint.setStyle(Paint.Style.STROKE);
        trackBorderPaint.setStrokeWidth(1f * density);
        trackBorderPaint.setColor(0x44FFFFFF);

        thumbBorderPaint.setStyle(Paint.Style.STROKE);
        thumbBorderPaint.setStrokeWidth(2f * density);
        thumbBorderPaint.setColor(0xFFFFFFFF);

        thumbSelectedPaint.setStyle(Paint.Style.STROKE);
        thumbSelectedPaint.setStrokeWidth(3f * density);
        thumbSelectedPaint.setColor(0xFF00E5BC);
    }

    public void setColors(int startColor, int endColor) {
        this.startColor = startColor;
        this.endColor = endColor;
        invalidate();
    }

    public void setOffsets(float startOffset, float endOffset) {
        this.startOffset = Math.max(0f, Math.min(1f, startOffset));
        this.endOffset = Math.max(0f, Math.min(1f, endOffset));
        invalidate();
    }

    public void setSelectedStop(int stopIndex) {
        this.selectedStopIndex = stopIndex;
        invalidate();
    }

    public int getSelectedStopIndex() {
        return selectedStopIndex;
    }

    public int getStartColor() {
        return startColor;
    }

    public int getEndColor() {
        return endColor;
    }

    public float getStartOffset() {
        return startOffset;
    }

    public float getEndOffset() {
        return endOffset;
    }

    public void setOnGradientChangeListener(OnGradientChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) (44 * density);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        int width = resolveSize((int) (200 * density), widthMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float padding = 16 * density;
        float trackHeight = 16 * density;
        float cy = getHeight() / 2f;

        float trackLeft = padding;
        float trackRight = getWidth() - padding;
        float trackTop = cy - trackHeight / 2f;
        float trackBottom = cy + trackHeight / 2f;
        trackRect.set(trackLeft, trackTop, trackRight, trackBottom);

        float trackWidth = trackRight - trackLeft;
        if (trackWidth <= 0) return;

        // 1. Draw Gradient Track
        int[] colors = new int[]{startColor, startColor, endColor, endColor};
        float[] positions = new float[]{0f, Math.min(startOffset, endOffset), Math.max(startOffset, endOffset), 1f};
        if (startOffset > endOffset) {
            colors = new int[]{endColor, endColor, startColor, startColor};
            positions = new float[]{0f, endOffset, startOffset, 1f};
        }

        LinearGradient gradient = new LinearGradient(
                trackLeft, cy, trackRight, cy,
                new int[]{startColor, endColor},
                new float[]{Math.min(startOffset, endOffset), Math.max(startOffset, endOffset)},
                Shader.TileMode.CLAMP
        );
        trackPaint.setShader(gradient);
        canvas.drawRoundRect(trackRect, 6 * density, 6 * density, trackPaint);
        canvas.drawRoundRect(trackRect, 6 * density, 6 * density, trackBorderPaint);

        // 2. Draw Start Thumb (Thumb 0)
        float thumbSize = 24 * density;
        float thumbRadius = 5 * density;

        float startX = trackLeft + trackWidth * startOffset;
        thumbRect.set(startX - thumbSize / 2f, cy - thumbSize / 2f, startX + thumbSize / 2f, cy + thumbSize / 2f);
        thumbFillPaint.setColor(startColor);
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbFillPaint);
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, (selectedStopIndex == 0) ? thumbSelectedPaint : thumbBorderPaint);

        // 3. Draw End Thumb (Thumb 1)
        float endX = trackLeft + trackWidth * endOffset;
        thumbRect.set(endX - thumbSize / 2f, cy - thumbSize / 2f, endX + thumbSize / 2f, cy + thumbSize / 2f);
        thumbFillPaint.setColor(endColor);
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbFillPaint);
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, (selectedStopIndex == 1) ? thumbSelectedPaint : thumbBorderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float padding = 16 * density;
        float trackWidth = getWidth() - padding * 2;
        if (trackWidth <= 0) return super.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();
        float cy = getHeight() / 2f;
        float startX = padding + trackWidth * startOffset;
        float endX = padding + trackWidth * endOffset;
        float hitRadius = 24 * density;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                float distStart = Math.abs(x - startX);
                float distEnd = Math.abs(x - endX);

                if (distStart <= hitRadius && distStart <= distEnd) {
                    draggingThumb = 0;
                    selectedStopIndex = 0;
                    if (listener != null) listener.onStopSelected(0, startColor);
                    invalidate();
                    return true;
                } else if (distEnd <= hitRadius) {
                    draggingThumb = 1;
                    selectedStopIndex = 1;
                    if (listener != null) listener.onStopSelected(1, endColor);
                    invalidate();
                    return true;
                } else {
                    // Tapped on the track -> select closest thumb and move it
                    if (distStart < distEnd) {
                        draggingThumb = 0;
                        selectedStopIndex = 0;
                        startOffset = Math.max(0f, Math.min(1f, (x - padding) / trackWidth));
                        if (listener != null) {
                            listener.onStopSelected(0, startColor);
                            listener.onOffsetsChanged(startOffset, endOffset, true);
                        }
                    } else {
                        draggingThumb = 1;
                        selectedStopIndex = 1;
                        endOffset = Math.max(0f, Math.min(1f, (x - padding) / trackWidth));
                        if (listener != null) {
                            listener.onStopSelected(1, endColor);
                            listener.onOffsetsChanged(startOffset, endOffset, true);
                        }
                    }
                    invalidate();
                    return true;
                }
            }

            case MotionEvent.ACTION_MOVE: {
                if (draggingThumb == 0) {
                    float newOffset = Math.max(0f, Math.min(1f, (x - padding) / trackWidth));
                    startOffset = newOffset;
                    if (listener != null) {
                        listener.onOffsetsChanged(startOffset, endOffset, true);
                    }
                    invalidate();
                    return true;
                } else if (draggingThumb == 1) {
                    float newOffset = Math.max(0f, Math.min(1f, (x - padding) / trackWidth));
                    endOffset = newOffset;
                    if (listener != null) {
                        listener.onOffsetsChanged(startOffset, endOffset, true);
                    }
                    invalidate();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                draggingThumb = -1;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
