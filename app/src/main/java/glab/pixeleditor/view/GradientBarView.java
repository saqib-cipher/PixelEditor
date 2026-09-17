package glab.pixeleditor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import glab.pixeleditor.model.ShapeLayer;

public class GradientBarView extends View {

    public static class GradientStop implements Comparable<GradientStop> {
        public int color;
        public float offset; // 0.0f to 1.0f

        public GradientStop(int color, float offset) {
            this.color = color;
            this.offset = Math.max(0f, Math.min(1f, offset));
        }

        public GradientStop copy() {
            return new GradientStop(this.color, this.offset);
        }

        @Override
        public int compareTo(GradientStop o) {
            return Float.compare(this.offset, o.offset);
        }
    }

    public interface OnGradientChangeListener {
        void onStopSelected(int stopIndex, int color);
        default void onStopClick(int stopIndex, int color) {}
        void onOffsetsChanged(float startOffset, float endOffset, boolean fromUser);
        void onStopsChanged(List<GradientStop> stops, boolean fromUser);
    }

    private final List<GradientStop> stops = new ArrayList<>();
    private int selectedStopIndex = 0;
    private ShapeLayer.GradientType gradientType = ShapeLayer.GradientType.LINEAR;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF trackRect = new RectF();
    private final RectF thumbRect = new RectF();
    private final Path notchPath = new Path();

    private int draggingStopIndex = -1;
    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private long touchDownTime = 0L;
    private boolean isDragMovement = false;
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

        notchPaint.setStyle(Paint.Style.FILL);
        notchPaint.setColor(0xFF00E5BC);

        // Default 2 stops: Start (0.0) -> End (1.0)
        stops.add(new GradientStop(0xFF000000, 0.0f));
        stops.add(new GradientStop(0xFFFFFFFF, 1.0f));
    }

    public void setGradientType(ShapeLayer.GradientType type) {
        if (type != null) {
            this.gradientType = type;
            invalidate();
        }
    }

    public ShapeLayer.GradientType getGradientType() {
        return gradientType;
    }

    public void setStops(List<GradientStop> newStops) {
        stops.clear();
        if (newStops != null && !newStops.isEmpty()) {
            for (GradientStop s : newStops) {
                stops.add(s.copy());
            }
        } else {
            stops.add(new GradientStop(0xFF000000, 0.0f));
            stops.add(new GradientStop(0xFFFFFFFF, 1.0f));
        }
        Collections.sort(stops);
        if (selectedStopIndex >= stops.size()) {
            selectedStopIndex = stops.size() - 1;
        }
        invalidate();
    }

    public List<GradientStop> getStops() {
        List<GradientStop> copy = new ArrayList<>();
        for (GradientStop s : stops) {
            copy.add(s.copy());
        }
        return copy;
    }

    public int[] getColorsArray() {
        if (stops.isEmpty()) return new int[]{0xFF000000, 0xFFFFFFFF};
        int[] cols = new int[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            cols[i] = stops.get(i).color;
        }
        return cols;
    }

    public float[] getPositionsArray() {
        if (stops.isEmpty()) return new float[]{0.0f, 1.0f};
        float[] pos = new float[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            pos[i] = stops.get(i).offset;
        }
        return pos;
    }

    public void setColors(int startColor, int endColor) {
        if (stops.size() >= 2) {
            stops.get(0).color = startColor;
            stops.get(stops.size() - 1).color = endColor;
        } else {
            stops.clear();
            stops.add(new GradientStop(startColor, 0.0f));
            stops.add(new GradientStop(endColor, 1.0f));
        }
        invalidate();
    }

    public void setOffsets(float startOffset, float endOffset) {
        if (stops.size() >= 2) {
            stops.get(0).offset = Math.max(0f, Math.min(1f, startOffset));
            stops.get(stops.size() - 1).offset = Math.max(0f, Math.min(1f, endOffset));
            Collections.sort(stops);
        }
        invalidate();
    }

    public void setSelectedStop(int stopIndex) {
        if (stopIndex >= 0 && stopIndex < stops.size()) {
            this.selectedStopIndex = stopIndex;
            invalidate();
        }
    }

    public int getSelectedStopIndex() {
        return selectedStopIndex;
    }

    public GradientStop getSelectedStop() {
        if (selectedStopIndex >= 0 && selectedStopIndex < stops.size()) {
            return stops.get(selectedStopIndex);
        }
        return !stops.isEmpty() ? stops.get(0) : null;
    }

    public void updateSelectedStopColor(int color) {
        if (selectedStopIndex >= 0 && selectedStopIndex < stops.size()) {
            stops.get(selectedStopIndex).color = color;
            if (listener != null) {
                listener.onStopSelected(selectedStopIndex, color);
                listener.onStopsChanged(getStops(), false);
            }
            invalidate();
        }
    }

    public void addStopAuto() {
        float newOffset = 0.5f;
        if (selectedStopIndex >= 0 && selectedStopIndex < stops.size() - 1) {
            newOffset = (stops.get(selectedStopIndex).offset + stops.get(selectedStopIndex + 1).offset) / 2f;
        } else if (selectedStopIndex == stops.size() - 1 && stops.size() > 1) {
            newOffset = (stops.get(stops.size() - 2).offset + stops.get(stops.size() - 1).offset) / 2f;
        }
        int interpolatedColor = interpolateColorAtOffset(newOffset);
        addStop(interpolatedColor, newOffset);
    }

    public void addStop(int color, float offset) {
        GradientStop newStop = new GradientStop(color, offset);
        stops.add(newStop);
        Collections.sort(stops);
        selectedStopIndex = stops.indexOf(newStop);
        if (listener != null) {
            listener.onStopSelected(selectedStopIndex, color);
            listener.onStopsChanged(getStops(), true);
            if (stops.size() >= 2) {
                listener.onOffsetsChanged(stops.get(0).offset, stops.get(stops.size() - 1).offset, true);
            }
        }
        invalidate();
    }

    public boolean removeSelectedStop() {
        if (stops.size() <= 2) {
            return false; // keep at least 2 stops
        }
        if (selectedStopIndex >= 0 && selectedStopIndex < stops.size()) {
            stops.remove(selectedStopIndex);
            if (selectedStopIndex >= stops.size()) {
                selectedStopIndex = stops.size() - 1;
            }
            if (listener != null) {
                listener.onStopSelected(selectedStopIndex, stops.get(selectedStopIndex).color);
                listener.onStopsChanged(getStops(), true);
                if (stops.size() >= 2) {
                    listener.onOffsetsChanged(stops.get(0).offset, stops.get(stops.size() - 1).offset, true);
                }
            }
            invalidate();
            return true;
        }
        return false;
    }

    public void reverseStops() {
        if (stops.size() < 2) return;
        List<GradientStop> reversed = new ArrayList<>();
        for (int i = stops.size() - 1; i >= 0; i--) {
            GradientStop s = stops.get(i);
            reversed.add(new GradientStop(s.color, 1.0f - s.offset));
        }
        stops.clear();
        stops.addAll(reversed);
        Collections.sort(stops);
        if (listener != null) {
            listener.onStopSelected(selectedStopIndex, stops.get(selectedStopIndex).color);
            listener.onStopsChanged(getStops(), true);
            listener.onOffsetsChanged(stops.get(0).offset, stops.get(stops.size() - 1).offset, true);
        }
        invalidate();
    }

    public int getStartColor() {
        return !stops.isEmpty() ? stops.get(0).color : 0xFF000000;
    }

    public int getEndColor() {
        return !stops.isEmpty() ? stops.get(stops.size() - 1).color : 0xFFFFFFFF;
    }

    public float getStartOffset() {
        return !stops.isEmpty() ? stops.get(0).offset : 0.0f;
    }

    public float getEndOffset() {
        return !stops.isEmpty() ? stops.get(stops.size() - 1).offset : 1.0f;
    }

    public void setOnGradientChangeListener(OnGradientChangeListener listener) {
        this.listener = listener;
    }

    private int interpolateColorAtOffset(float offset) {
        if (stops.isEmpty()) return 0xFF000000;
        if (stops.size() == 1) return stops.get(0).color;

        if (offset <= stops.get(0).offset) return stops.get(0).color;
        if (offset >= stops.get(stops.size() - 1).offset) return stops.get(stops.size() - 1).color;

        for (int i = 0; i < stops.size() - 1; i++) {
            GradientStop s0 = stops.get(i);
            GradientStop s1 = stops.get(i + 1);
            if (offset >= s0.offset && offset <= s1.offset) {
                float range = s1.offset - s0.offset;
                float fraction = (range > 0.0001f) ? (offset - s0.offset) / range : 0f;
                return blendColor(s0.color, s1.color, fraction);
            }
        }
        return stops.get(0).color;
    }

    private int blendColor(int col1, int col2, float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        float inverse = 1f - ratio;
        int a = Math.round(Color.alpha(col1) * inverse + Color.alpha(col2) * ratio);
        int r = Math.round(Color.red(col1) * inverse + Color.red(col2) * ratio);
        int g = Math.round(Color.green(col1) * inverse + Color.green(col2) * ratio);
        int b = Math.round(Color.blue(col1) * inverse + Color.blue(col2) * ratio);
        return Color.argb(a, r, g, b);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) (48 * density);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        int width = resolveSize((int) (200 * density), widthMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float padding = 18 * density;
        float trackHeight = 16 * density;
        float cy = getHeight() / 2f + 2 * density;

        float trackLeft = padding;
        float trackRight = getWidth() - padding;
        float trackTop = cy - trackHeight / 2f;
        float trackBottom = cy + trackHeight / 2f;
        trackRect.set(trackLeft, trackTop, trackRight, trackBottom);

        float trackWidth = trackRight - trackLeft;
        if (trackWidth <= 0 || stops.isEmpty()) return;

        // 1. Prepare Colors and Positions arrays for Shader
        int numStops = stops.size();
        int[] shaderColors;
        float[] shaderPositions;

        if (numStops == 1) {
            shaderColors = new int[]{stops.get(0).color, stops.get(0).color};
            shaderPositions = new float[]{0f, 1f};
        } else {
            shaderColors = new int[numStops];
            shaderPositions = new float[numStops];
            for (int i = 0; i < numStops; i++) {
                shaderColors[i] = stops.get(i).color;
                shaderPositions[i] = stops.get(i).offset;
            }
        }

        LinearGradient gradient = new LinearGradient(
                trackLeft, cy, trackRight, cy,
                shaderColors, shaderPositions,
                Shader.TileMode.CLAMP
        );
        trackPaint.setShader(gradient);
        canvas.drawRoundRect(trackRect, 6 * density, 6 * density, trackPaint);
        canvas.drawRoundRect(trackRect, 6 * density, 6 * density, trackBorderPaint);

        // 2. Draw all Stop Thumbs
        float thumbW = 18 * density;
        float thumbH = 26 * density;
        float thumbRadius = 4 * density;

        for (int i = 0; i < stops.size(); i++) {
            GradientStop s = stops.get(i);
            float thumbX = trackLeft + trackWidth * s.offset;
            thumbRect.set(thumbX - thumbW / 2f, cy - thumbH / 2f, thumbX + thumbW / 2f, cy + thumbH / 2f);

            // Fill with stop color
            thumbFillPaint.setColor(s.color);
            canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbFillPaint);

            boolean isSelected = (i == selectedStopIndex);
            if (isSelected) {
                // Glow border for selected thumb
                canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbSelectedPaint);

                // Draw top pointer notch above thumb
                float notchTop = thumbRect.top - 4 * density;
                notchPath.reset();
                notchPath.moveTo(thumbX, notchTop);
                notchPath.lineTo(thumbX - 4 * density, thumbRect.top);
                notchPath.lineTo(thumbX + 4 * density, thumbRect.top);
                notchPath.close();
                canvas.drawPath(notchPath, notchPaint);
            } else {
                canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbBorderPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float padding = 18 * density;
        float trackWidth = getWidth() - padding * 2;
        if (trackWidth <= 0 || stops.isEmpty()) return super.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();
        float hitRadius = 24 * density;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                touchDownX = x;
                touchDownY = y;
                touchDownTime = System.currentTimeMillis();
                isDragMovement = false;

                // 1. Check if tapped an existing thumb
                int closestIndex = -1;
                float minDistance = Float.MAX_VALUE;

                for (int i = 0; i < stops.size(); i++) {
                    float thumbX = padding + trackWidth * stops.get(i).offset;
                    float dist = Math.abs(x - thumbX);
                    if (dist < minDistance) {
                        minDistance = dist;
                        closestIndex = i;
                    }
                }

                if (minDistance <= hitRadius && closestIndex >= 0) {
                    draggingStopIndex = closestIndex;
                    selectedStopIndex = closestIndex;
                    if (listener != null) {
                        listener.onStopSelected(selectedStopIndex, stops.get(selectedStopIndex).color);
                    }
                    invalidate();
                    return true;
                } else {
                    // Tapped on empty area of track -> Add a new stop at touch position!
                    float touchOffset = Math.max(0f, Math.min(1f, (x - padding) / trackWidth));
                    int interpolatedCol = interpolateColorAtOffset(touchOffset);
                    addStop(interpolatedCol, touchOffset);
                    draggingStopIndex = selectedStopIndex;
                    return true;
                }
            }

            case MotionEvent.ACTION_MOVE: {
                if (draggingStopIndex >= 0 && draggingStopIndex < stops.size()) {
                    if (Math.abs(x - touchDownX) > 4 * density) {
                        isDragMovement = true;
                    }
                    float newOffset = Math.max(0f, Math.min(1f, (x - padding) / trackWidth));
                    stops.get(draggingStopIndex).offset = newOffset;
                    if (listener != null) {
                        listener.onStopsChanged(getStops(), true);
                        if (stops.size() >= 2) {
                            listener.onOffsetsChanged(stops.get(0).offset, stops.get(stops.size() - 1).offset, true);
                        }
                    }
                    invalidate();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (draggingStopIndex >= 0) {
                    GradientStop dragged = (draggingStopIndex < stops.size()) ? stops.get(draggingStopIndex) : null;
                    Collections.sort(stops);
                    if (dragged != null) {
                        selectedStopIndex = stops.indexOf(dragged);
                    }
                    if (listener != null) {
                        listener.onStopsChanged(getStops(), true);
                        if (stops.size() >= 2) {
                            listener.onOffsetsChanged(stops.get(0).offset, stops.get(stops.size() - 1).offset, true);
                        }
                        if (!isDragMovement && (System.currentTimeMillis() - touchDownTime) < 350) {
                            listener.onStopClick(selectedStopIndex, stops.get(selectedStopIndex).color);
                        }
                    }
                    draggingStopIndex = -1;
                    invalidate();
                    return true;
                }
                break;
            }
        }
        return super.onTouchEvent(event);
    }
}
