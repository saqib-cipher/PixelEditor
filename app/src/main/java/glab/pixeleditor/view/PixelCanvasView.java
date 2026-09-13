package glab.pixeleditor.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import glab.pixeleditor.model.CanvasLayer;
import glab.pixeleditor.model.EditorProject;
import glab.pixeleditor.model.ShapeLayer;

public class PixelCanvasView extends View {

    public interface OnLayerSelectedListener {
        void onLayerSelected(@Nullable CanvasLayer layer, int index);
        void onLayerModified(CanvasLayer layer);
    }

    private EditorProject project;
    private OnLayerSelectedListener layerSelectedListener;

    // Viewport matrix (Canvas to View coordinate mapping)
    private final Matrix viewportMatrix = new Matrix();
    private final Matrix inverseViewportMatrix = new Matrix();
    private float viewportScale = 1f;
    private float viewportTransX = 0f;
    private float viewportTransY = 0f;

    // Handles & Selection Drawing
    private final Paint artboardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rotationHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseLayerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float HANDLE_RADIUS = 16f; // in view pixels

    // Touch Handling State
    private enum TouchMode {
        NONE,
        DRAG_LAYER,
        RESIZE_HANDLE,
        ROTATE_HANDLE,
        PAN_VIEWPORT
    }
    private TouchMode currentTouchMode = TouchMode.NONE;
    private int activeHandleIndex = -1; // 0..7 handles, 8 = rotation handle

    private float lastTouchX, lastTouchY;
    private float initialLayerX, initialLayerY;
    private float initialLayerWidth, initialLayerHeight;
    private float initialLayerRotation;
    private ScaleGestureDetector scaleGestureDetector;

    public PixelCanvasView(Context context) {
        super(context);
        init();
    }

    public PixelCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PixelCanvasView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        artboardBgPaint.setStyle(Paint.Style.FILL);
        artboardBgPaint.setColor(0xFFD8DCE3);

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x55000000);

        selectionStrokePaint.setStyle(Paint.Style.STROKE);
        selectionStrokePaint.setColor(0xFF00E5BC);
        selectionStrokePaint.setStrokeWidth(3f);

        handleFillPaint.setStyle(Paint.Style.FILL);
        handleFillPaint.setColor(Color.WHITE);

        handleStrokePaint.setStyle(Paint.Style.STROKE);
        handleStrokePaint.setColor(0xFF00E5BC);
        handleStrokePaint.setStrokeWidth(3f);

        rotationHandlePaint.setStyle(Paint.Style.FILL);
        rotationHandlePaint.setColor(0xFF00E5BC);

        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (currentTouchMode == TouchMode.PAN_VIEWPORT || currentTouchMode == TouchMode.NONE) {
                    float factor = detector.getScaleFactor();
                    viewportScale *= factor;
                    viewportScale = Math.max(0.2f, Math.min(viewportScale, 6f));
                    updateViewportMatrix();
                    invalidate();
                    return true;
                }
                return false;
            }
        });
    }

    public void setProject(EditorProject project) {
        this.project = project;
        resetViewport();
        invalidate();
    }

    public EditorProject getProject() {
        return project;
    }

    public void setOnLayerSelectedListener(OnLayerSelectedListener listener) {
        this.layerSelectedListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetViewport();
    }

    public void resetViewport() {
        if (project == null || getWidth() <= 0 || getHeight() <= 0) return;

        float artW = project.getCanvasWidth();
        float artH = project.getCanvasHeight();

        // Fit artboard with 48dp margins
        float availW = getWidth() - 96f;
        float availH = getHeight() - 96f;

        viewportScale = Math.min(availW / artW, availH / artH);
        viewportTransX = (getWidth() - artW * viewportScale) / 2f;
        viewportTransY = (getHeight() - artH * viewportScale) / 2f;

        updateViewportMatrix();
        invalidate();
    }

    private void updateViewportMatrix() {
        viewportMatrix.reset();
        viewportMatrix.postScale(viewportScale, viewportScale);
        viewportMatrix.postTranslate(viewportTransX, viewportTransY);
        viewportMatrix.invert(inverseViewportMatrix);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (project == null) return;

        // 1. Draw Viewport (artboard + layers)
        canvas.save();
        canvas.concat(viewportMatrix);

        float artW = project.getCanvasWidth();
        float artH = project.getCanvasHeight();

        // Artboard Background
        artboardBgPaint.setColor(project.getBackgroundColor());
        canvas.drawRect(0, 0, artW, artH, artboardBgPaint);

        // Clip to Artboard
        canvas.clipRect(0, 0, artW, artH);

        // Render Layers
        for (CanvasLayer layer : project.getLayers()) {
            if (layer.isVisible()) {
                layer.draw(canvas, baseLayerPaint);
            }
        }

        canvas.restore();

        // 2. Draw Selection Box & Handles in View coordinates
        CanvasLayer selectedLayer = project.getSelectedLayer();
        if (selectedLayer != null && selectedLayer.isVisible()) {
            drawSelectionOverlay(canvas, selectedLayer);
        }
    }

    private void drawSelectionOverlay(Canvas canvas, CanvasLayer layer) {
        // Calculate corner points in canvas coordinates
        float hw = (layer.getWidth() * Math.abs(layer.getScaleX())) / 2f;
        float hh = (layer.getHeight() * Math.abs(layer.getScaleY())) / 2f;
        float lx = layer.getX();
        float ly = layer.getY();
        float rot = layer.getRotation();

        // 8 handle coordinates + 1 rotation handle in layer local
        float[][] localPts = new float[][]{
                {-hw, -hh}, // 0: Top-Left
                {0, -hh},   // 1: Top-Mid
                {hw, -hh},  // 2: Top-Right
                {hw, 0},    // 3: Right-Mid (with green indicator)
                {hw, hh},   // 4: Bottom-Right
                {0, hh},    // 5: Bottom-Mid
                {-hw, hh},  // 6: Bottom-Left
                {-hw, 0},   // 7: Left-Mid
                {hw + 45f / viewportScale, 0} // 8: Rotation handle
        };

        Matrix layerMat = new Matrix();
        layerMat.postRotate(rot);
        layerMat.postTranslate(lx, ly);
        layerMat.postConcat(viewportMatrix); // Direct to view coordinates!

        float[] viewPts = new float[localPts.length * 2];
        for (int i = 0; i < localPts.length; i++) {
            float[] pt = new float[]{localPts[i][0], localPts[i][1]};
            layerMat.mapPoints(pt);
            viewPts[i * 2] = pt[0];
            viewPts[i * 2 + 1] = pt[1];
        }

        // Draw bounding rectangle
        canvas.save();
        android.graphics.Path rectPath = new android.graphics.Path();
        rectPath.moveTo(viewPts[0], viewPts[1]);
        rectPath.lineTo(viewPts[4], viewPts[5]);
        rectPath.lineTo(viewPts[8], viewPts[9]);
        rectPath.lineTo(viewPts[12], viewPts[13]);
        rectPath.close();
        canvas.drawPath(rectPath, selectionStrokePaint);

        // Draw rotation link line
        canvas.drawLine(viewPts[6], viewPts[7], viewPts[16], viewPts[17], selectionStrokePaint);

        // Draw 8 white handles
        for (int i = 0; i < 8; i++) {
            float hx = viewPts[i * 2];
            float hy = viewPts[i * 2 + 1];
            canvas.drawCircle(hx, hy, HANDLE_RADIUS, handleFillPaint);
            canvas.drawCircle(hx, hy, HANDLE_RADIUS, handleStrokePaint);
        }

        // Draw rotation handle (green/cyan circle matching Image 2 & 3)
        float rx = viewPts[16];
        float ry = viewPts[17];
        canvas.drawCircle(rx, ry, HANDLE_RADIUS * 1.15f, rotationHandlePaint);
        canvas.drawCircle(rx, ry, HANDLE_RADIUS * 1.15f, handleStrokePaint);

        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);

        if (event.getPointerCount() > 1) {
            // Multi-touch pan/zoom
            currentTouchMode = TouchMode.PAN_VIEWPORT;
            return true;
        }

        float vx = event.getX();
        float vy = event.getY();
        float[] canvasPt = mapToCanvas(vx, vy);
        float cx = canvasPt[0];
        float cy = canvasPt[1];

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = vx;
                lastTouchY = vy;

                // 1. Check if clicking on an active handle
                CanvasLayer selectedLayer = project != null ? project.getSelectedLayer() : null;
                if (selectedLayer != null && !selectedLayer.isLocked()) {
                    int handle = hitTestHandle(selectedLayer, vx, vy);
                    if (handle >= 0) {
                        project.saveSnapshot();
                        if (handle == 8) {
                            currentTouchMode = TouchMode.ROTATE_HANDLE;
                        } else {
                            currentTouchMode = TouchMode.RESIZE_HANDLE;
                        }
                        activeHandleIndex = handle;
                        initialLayerWidth = selectedLayer.getWidth();
                        initialLayerHeight = selectedLayer.getHeight();
                        initialLayerRotation = selectedLayer.getRotation();
                        return true;
                    }
                }

                // 2. Check if clicking on a layer (topmost first)
                int clickedIndex = -1;
                if (project != null) {
                    for (int i = project.getLayers().size() - 1; i >= 0; i--) {
                        CanvasLayer layer = project.getLayers().get(i);
                        if (layer.containsPoint(cx, cy)) {
                            clickedIndex = i;
                            break;
                        }
                    }
                }

                if (clickedIndex >= 0) {
                    project.setSelectedIndex(clickedIndex);
                    CanvasLayer layer = project.getSelectedLayer();
                    initialLayerX = layer.getX();
                    initialLayerY = layer.getY();
                    currentTouchMode = TouchMode.DRAG_LAYER;
                    project.saveSnapshot();

                    if (layerSelectedListener != null) {
                        layerSelectedListener.onLayerSelected(layer, clickedIndex);
                    }
                    invalidate();
                    return true;
                } else {
                    // Clicked empty area on canvas or backdrop
                    currentTouchMode = TouchMode.PAN_VIEWPORT;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = vx - lastTouchX;
                float dy = vy - lastTouchY;
                float canvasDx = dx / viewportScale;
                float canvasDy = dy / viewportScale;

                CanvasLayer activeLayer = project != null ? project.getSelectedLayer() : null;

                if (currentTouchMode == TouchMode.DRAG_LAYER && activeLayer != null && !activeLayer.isLocked()) {
                    activeLayer.setX(activeLayer.getX() + canvasDx);
                    activeLayer.setY(activeLayer.getY() + canvasDy);
                    if (layerSelectedListener != null) {
                        layerSelectedListener.onLayerModified(activeLayer);
                    }
                    invalidate();
                } else if (currentTouchMode == TouchMode.RESIZE_HANDLE && activeLayer != null) {
                    handleResize(activeLayer, canvasDx, canvasDy);
                    if (layerSelectedListener != null) {
                        layerSelectedListener.onLayerModified(activeLayer);
                    }
                    invalidate();
                } else if (currentTouchMode == TouchMode.ROTATE_HANDLE && activeLayer != null) {
                    // Calculate angle from layer center to current touch point in canvas coords
                    float angleRad = (float) Math.atan2(cy - activeLayer.getY(), cx - activeLayer.getX());
                    float deg = (float) Math.toDegrees(angleRad);
                    activeLayer.setRotation(deg);
                    if (layerSelectedListener != null) {
                        layerSelectedListener.onLayerModified(activeLayer);
                    }
                    invalidate();
                } else if (currentTouchMode == TouchMode.PAN_VIEWPORT) {
                    viewportTransX += dx;
                    viewportTransY += dy;
                    updateViewportMatrix();
                    invalidate();
                }

                lastTouchX = vx;
                lastTouchY = vy;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                currentTouchMode = TouchMode.NONE;
                activeHandleIndex = -1;
                break;
        }

        return true;
    }

    private void handleResize(CanvasLayer layer, float cdx, float cdy) {
        // Rotate delta into layer's coordinate frame
        float rad = (float) Math.toRadians(-layer.getRotation());
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float ldx = cdx * cos - cdy * sin;
        float ldy = cdx * sin + cdy * cos;

        switch (activeHandleIndex) {
            case 0: // Top-Left
                layer.setWidth(layer.getWidth() - ldx * 2);
                layer.setHeight(layer.getHeight() - ldy * 2);
                break;
            case 1: // Top-Mid
                layer.setHeight(layer.getHeight() - ldy * 2);
                break;
            case 2: // Top-Right
                layer.setWidth(layer.getWidth() + ldx * 2);
                layer.setHeight(layer.getHeight() - ldy * 2);
                break;
            case 3: // Right-Mid
                layer.setWidth(layer.getWidth() + ldx * 2);
                break;
            case 4: // Bottom-Right
                layer.setWidth(layer.getWidth() + ldx * 2);
                layer.setHeight(layer.getHeight() + ldy * 2);
                break;
            case 5: // Bottom-Mid
                layer.setHeight(layer.getHeight() + ldy * 2);
                break;
            case 6: // Bottom-Left
                layer.setWidth(layer.getWidth() - ldx * 2);
                layer.setHeight(layer.getHeight() + ldy * 2);
                break;
            case 7: // Left-Mid
                layer.setWidth(layer.getWidth() - ldx * 2);
                break;
        }
    }

    private int hitTestHandle(CanvasLayer layer, float vx, float vy) {
        float hw = (layer.getWidth() * Math.abs(layer.getScaleX())) / 2f;
        float hh = (layer.getHeight() * Math.abs(layer.getScaleY())) / 2f;
        float rot = layer.getRotation();

        float[][] localPts = new float[][]{
                {-hw, -hh}, {0, -hh}, {hw, -hh},
                {hw, 0}, {hw, hh}, {0, hh},
                {-hw, hh}, {-hw, 0},
                {hw + 45f / viewportScale, 0}
        };

        Matrix layerMat = new Matrix();
        layerMat.postRotate(rot);
        layerMat.postTranslate(layer.getX(), layer.getY());
        layerMat.postConcat(viewportMatrix);

        float hitRadiusSq = (HANDLE_RADIUS * 2.2f) * (HANDLE_RADIUS * 2.2f);

        for (int i = 0; i < localPts.length; i++) {
            float[] pt = new float[]{localPts[i][0], localPts[i][1]};
            layerMat.mapPoints(pt);
            float distSq = (pt[0] - vx) * (pt[0] - vx) + (pt[1] - vy) * (pt[1] - vy);
            if (distSq <= hitRadiusSq) {
                return i;
            }
        }
        return -1;
    }

    private float[] mapToCanvas(float vx, float vy) {
        float[] pts = new float[]{vx, vy};
        inverseViewportMatrix.mapPoints(pts);
        return pts;
    }

    public Bitmap exportArtboardBitmap() {
        if (project == null) return null;

        int w = project.getCanvasWidth();
        int h = project.getCanvasHeight();
        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        // Draw Background
        artboardBgPaint.setColor(project.getBackgroundColor());
        canvas.drawRect(0, 0, w, h, artboardBgPaint);

        // Draw Layers
        for (CanvasLayer layer : project.getLayers()) {
            if (layer.isVisible()) {
                layer.draw(canvas, baseLayerPaint);
            }
        }
        return result;
    }
}
