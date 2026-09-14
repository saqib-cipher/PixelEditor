package glab.pixeleditor.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.List;

import glab.pixeleditor.model.CanvasLayer;
import glab.pixeleditor.model.EditorProject;
import glab.pixeleditor.model.PhotoLayer;
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

    // Viewport lock & Magnetic Snapping
    private boolean isViewportLocked = true;
    private final List<Float> activeSnapLinesX = new java.util.ArrayList<>();
    private final List<Float> activeSnapLinesY = new java.util.ArrayList<>();
    private final Paint snapGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint snapDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean wasSnapped = false;

    // Grid lines
    private boolean showGridLines = false;
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Handles & Selection Drawing
    private final Paint artboardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkerboardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rotationHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseLayerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Gradient On-Canvas Controller Painting
    private final Paint gradLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gradLineShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gradHandleOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gradHandleInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gradHandleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Eyedropper Magnifier Loupe Painting
    private float lastEyedropperX = -1f;
    private float lastEyedropperY = -1f;
    private int currentSampledColor = 0xFFFFFFFF;
    private final Paint eyedropperOuterRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyedropperInnerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyedropperCrosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyedropperBadgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyedropperTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float HANDLE_RADIUS = 16f; // in view pixels

    // Touch Handling State
    private enum TouchMode {
        NONE,
        DRAG_LAYER,
        RESIZE_HANDLE,
        ROTATE_HANDLE,
        PAN_VIEWPORT,
        GRADIENT_START_HANDLE,
        GRADIENT_END_HANDLE
    }
    private TouchMode currentTouchMode = TouchMode.NONE;
    private int activeHandleIndex = -1; // 0..7 handles, 8 = rotation handle

    private float lastTouchX, lastTouchY;
    private float initialLayerX, initialLayerY;
    private float initialLayerWidth, initialLayerHeight;
    private float initialLayerRotation;
    private boolean hasSavedSnapshotForInteraction = false;
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
        // Enable software rendering so BlurMaskFilter (Gaussian/Box/Inner Blur) and ShadowLayer render properly
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        artboardBgPaint.setStyle(Paint.Style.FILL);
        artboardBgPaint.setColor(0xFFD8DCE3);

        // Checkerboard transparency pattern
        Bitmap checkBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        Canvas checkC = new Canvas(checkBmp);
        Paint c1 = new Paint(); c1.setColor(0xFF1E293B);
        Paint c2 = new Paint(); c2.setColor(0xFF131D2E);
        checkC.drawRect(0, 0, 16, 16, c1);
        checkC.drawRect(16, 16, 32, 32, c1);
        checkC.drawRect(16, 0, 32, 16, c2);
        checkC.drawRect(0, 16, 16, 32, c2);
        checkerboardPaint.setShader(new BitmapShader(checkBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));

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

        // Gradient on-canvas vector line and handles
        gradLinePaint.setStyle(Paint.Style.STROKE);
        gradLinePaint.setColor(0xFFFFFFFF);
        gradLinePaint.setStrokeWidth(2.5f);

        gradLineShadowPaint.setStyle(Paint.Style.STROKE);
        gradLineShadowPaint.setColor(0x88000000);
        gradLineShadowPaint.setStrokeWidth(4.5f);

        gradHandleOuterPaint.setStyle(Paint.Style.FILL);
        gradHandleOuterPaint.setColor(0xCC000000);

        gradHandleInnerPaint.setStyle(Paint.Style.FILL);
        gradHandleInnerPaint.setColor(0xFFFFFFFF);

        gradHandleStrokePaint.setStyle(Paint.Style.STROKE);
        gradHandleStrokePaint.setColor(0xFFFFFFFF);
        gradHandleStrokePaint.setStrokeWidth(2.5f);

        // Eyedropper Magnifier Loupe
        eyedropperOuterRingPaint.setStyle(Paint.Style.STROKE);
        eyedropperOuterRingPaint.setColor(0xFFFFFFFF);
        eyedropperOuterRingPaint.setStrokeWidth(3.5f);

        eyedropperInnerRingPaint.setStyle(Paint.Style.FILL);
        eyedropperInnerRingPaint.setColor(0xFF00E5BC);

        eyedropperCrosshairPaint.setStyle(Paint.Style.STROKE);
        eyedropperCrosshairPaint.setColor(0xFFFFFFFF);
        eyedropperCrosshairPaint.setStrokeWidth(2f);

        eyedropperBadgeBgPaint.setStyle(Paint.Style.FILL);
        eyedropperBadgeBgPaint.setColor(0xEE1E293B);

        eyedropperTextPaint.setStyle(Paint.Style.FILL);
        eyedropperTextPaint.setColor(0xFFFFFFFF);
        eyedropperTextPaint.setTextSize(26f);
        eyedropperTextPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        eyedropperTextPaint.setTextAlign(Paint.Align.CENTER);

        // Magnetic guideline styling: High contrast dashed Mint/Cyan
        snapGuidePaint.setStyle(Paint.Style.STROKE);
        snapGuidePaint.setColor(0xFF00E5BC);
        snapGuidePaint.setStrokeWidth(2.5f);
        snapGuidePaint.setPathEffect(new DashPathEffect(new float[]{14, 10}, 0));

        snapDotPaint.setStyle(Paint.Style.FILL);
        snapDotPaint.setColor(0xFF00D2FF);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setColor(0x33888888);
        gridPaint.setStrokeWidth(1.5f);

        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (!isViewportLocked && (currentTouchMode == TouchMode.PAN_VIEWPORT || currentTouchMode == TouchMode.NONE)) {
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

    /**
     * Samples the exact rendered ARGB pixel color at the specified canvas view coordinates.
     */
    public int sampleColorAt(float viewX, float viewY) {
        if (viewX < 0 || viewY < 0 || viewX >= getWidth() || viewY >= getHeight()) {
            return 0xFFFFFFFF;
        }
        try {
            Bitmap pixelBmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(pixelBmp);
            c.translate(-viewX, -viewY);
            draw(c);
            int color = pixelBmp.getPixel(0, 0);
            pixelBmp.recycle();
            return color;
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    private boolean hasInitializedViewport = false;
    private boolean isPanModeActive = false;

    public boolean isPanModeActive() {
        return isPanModeActive;
    }

    public void setPanModeActive(boolean active) {
        this.isPanModeActive = active;
        this.isViewportLocked = !active;
        invalidate();
    }

    public void togglePanMode() {
        setPanModeActive(!isPanModeActive);
    }

    public boolean isViewportLocked() {
        return isViewportLocked;
    }

    public void setViewportLocked(boolean locked) {
        this.isViewportLocked = locked;
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
        if (!hasInitializedViewport && w > 0 && h > 0) {
            resetViewport();
            hasInitializedViewport = true;
        }
    }

    public boolean isGridLinesEnabled() {
        return showGridLines;
    }

    public void setGridLinesEnabled(boolean enabled) {
        this.showGridLines = enabled;
        invalidate();
    }

    public void toggleGridLines() {
        setGridLinesEnabled(!showGridLines);
    }

    public void zoomIn() {
        viewportScale = Math.min(6.0f, viewportScale * 1.25f);
        updateViewportMatrix();
        invalidate();
    }

    public void zoomOut() {
        viewportScale = Math.max(0.2f, viewportScale / 1.25f);
        updateViewportMatrix();
        invalidate();
    }

    public float getViewportScale() {
        return viewportScale;
    }

    public int getZoomPercent() {
        return Math.round(viewportScale * 100);
    }

    public void resetViewport() {
        if (project == null || getWidth() <= 0 || getHeight() <= 0) return;

        float artW = project.getCanvasWidth();
        float artH = project.getCanvasHeight();

        // Fit artboard with minimal clean 24px margins to maximize height and eliminate blank space
        float availW = Math.max(10f, getWidth() - 24f);
        float availH = Math.max(10f, getHeight() - 24f);

        viewportScale = Math.min(availW / artW, availH / artH);
        viewportTransX = (getWidth() - artW * viewportScale) / 2f;
        viewportTransY = (getHeight() - artH * viewportScale) / 2f;

        hasInitializedViewport = true;
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
        if (Color.alpha(project.getBackgroundColor()) == 0) {
            canvas.drawRect(0, 0, artW, artH, checkerboardPaint);
        } else {
            artboardBgPaint.setColor(project.getBackgroundColor());
            canvas.drawRect(0, 0, artW, artH, artboardBgPaint);
        }

        // Draw Grid Lines if enabled
        if (showGridLines) {
            float gridSpacing = 80f;
            for (float gx = 0; gx <= artW; gx += gridSpacing) {
                canvas.drawLine(gx, 0, gx, artH, gridPaint);
            }
            for (float gy = 0; gy <= artH; gy += gridSpacing) {
                canvas.drawLine(0, gy, artW, gy, gridPaint);
            }
        }

        // Clip to Artboard
        canvas.clipRect(0, 0, artW, artH);

        // Render Layers with Masking Support
        renderLayersWithMasking(canvas, project.getLayers(), baseLayerPaint);

        // Draw Magnetic Guidelines inside artboard coordinate system
        if (!activeSnapLinesX.isEmpty() || !activeSnapLinesY.isEmpty()) {
            for (float sx : activeSnapLinesX) {
                canvas.drawLine(sx, 0, sx, artH, snapGuidePaint);
                canvas.drawCircle(sx, artH / 2f, 5f, snapDotPaint);
            }
            for (float sy : activeSnapLinesY) {
                canvas.drawLine(0, sy, artW, sy, snapGuidePaint);
                canvas.drawCircle(artW / 2f, sy, 5f, snapDotPaint);
            }
        }

        canvas.restore();

        // 2. Draw Selection Box & Handles in View coordinates (Only if layer is NOT locked)
        // Skip overlays when eyedropper is active so the canvas is uncluttered
        if (!isEyedropperActive) {
            CanvasLayer selectedLayer = project.getSelectedLayer();
            if (selectedLayer != null && selectedLayer.isVisible() && !selectedLayer.isLocked()) {
                drawSelectionOverlay(canvas, selectedLayer);

                // Draw on-canvas Gradient Vector Line & Handles if ShapeLayer or PhotoLayer has Gradient Fill
                if (hasGradientFill(selectedLayer)) {
                    drawGradientControllerOverlay(canvas, selectedLayer);
                }
            }
        }

        // 3. Draw Eyedropper Magnifier Loupe if Eyedropper is active
        if (isEyedropperActive) {
            drawEyedropperLoupe(canvas);
        }
    }

    private boolean hasGradientFill(CanvasLayer layer) {
        if (layer instanceof ShapeLayer) {
            return ((ShapeLayer) layer).getFillMode() == ShapeLayer.FillMode.GRADIENT;
        } else if (layer instanceof PhotoLayer) {
            return ((PhotoLayer) layer).getFillMode() == ShapeLayer.FillMode.GRADIENT;
        }
        return false;
    }

    private void drawGradientControllerOverlay(Canvas canvas, CanvasLayer layer) {
        float startX, startY, endX, endY;
        int startCol, endCol;
        if (layer instanceof ShapeLayer) {
            ShapeLayer sl = (ShapeLayer) layer;
            startX = sl.getGradientStartX(); startY = sl.getGradientStartY();
            endX = sl.getGradientEndX(); endY = sl.getGradientEndY();
            startCol = sl.getGradientStartColor(); endCol = sl.getGradientEndColor();
        } else if (layer instanceof PhotoLayer) {
            PhotoLayer pl = (PhotoLayer) layer;
            startX = pl.getGradientStartX(); startY = pl.getGradientStartY();
            endX = pl.getGradientEndX(); endY = pl.getGradientEndY();
            startCol = pl.getGradientStartColor(); endCol = pl.getGradientEndColor();
        } else {
            return;
        }

        Matrix layerMat = getLayerToViewMatrix(layer);
        float[] pts = new float[]{startX, startY, endX, endY};
        layerMat.mapPoints(pts);
        float sx = pts[0];
        float sy = pts[1];
        float ex = pts[2];
        float ey = pts[3];

        canvas.save();

        // 1. Draw Connecting Vector Line (with dark shadow outline for contrast against any background)
        canvas.drawLine(sx, sy, ex, ey, gradLineShadowPaint);
        canvas.drawLine(sx, sy, ex, ey, gradLinePaint);

        // 2. Draw Start Handle (Circular Ring filled with start color + white rim + inner dot)
        float r = HANDLE_RADIUS * 1.25f;
        gradHandleOuterPaint.setColor(startCol);
        canvas.drawCircle(sx, sy, r, gradHandleOuterPaint);
        canvas.drawCircle(sx, sy, r, gradHandleStrokePaint);
        canvas.drawCircle(sx, sy, r * 0.35f, gradHandleInnerPaint);

        // 3. Draw End Handle (Circular Ring filled with end color + white rim + inner dot)
        gradHandleOuterPaint.setColor(endCol);
        canvas.drawCircle(ex, ey, r, gradHandleOuterPaint);
        canvas.drawCircle(ex, ey, r, gradHandleStrokePaint);
        canvas.drawCircle(ex, ey, r * 0.35f, gradHandleInnerPaint);

        canvas.restore();
    }

    private void drawEyedropperLoupe(Canvas canvas) {
        if (lastEyedropperX < 0 || lastEyedropperY < 0) return;

        float loupeRadius = 56f;
        float margin = loupeRadius + 12f;

        // Position loupe above the finger by default; flip below if near top edge
        float loupeY = lastEyedropperY - loupeRadius - 80f;
        if (loupeY < margin) {
            loupeY = lastEyedropperY + loupeRadius + 80f;
        }

        // Clamp loupe X so it stays within the view width
        float loupeX = Math.max(margin, Math.min(getWidth() - margin, lastEyedropperX));
        // Clamp loupe Y within view height
        loupeY = Math.max(margin, Math.min(getHeight() - margin, loupeY));

        canvas.save();

        // Drop shadow
        shadowPaint.setColor(0x88000000);
        canvas.drawCircle(loupeX, loupeY + 6f, loupeRadius + 4f, shadowPaint);

        // Sampled color disc fill
        eyedropperInnerRingPaint.setColor(currentSampledColor);
        canvas.drawCircle(loupeX, loupeY, loupeRadius, eyedropperInnerRingPaint);

        // White border ring
        canvas.drawCircle(loupeX, loupeY, loupeRadius, eyedropperOuterRingPaint);

        // Crosshair reticle
        canvas.drawLine(loupeX - 18f, loupeY, loupeX + 18f, loupeY, eyedropperCrosshairPaint);
        canvas.drawLine(loupeX, loupeY - 18f, loupeX, loupeY + 18f, eyedropperCrosshairPaint);
        canvas.drawCircle(loupeX, loupeY, 4f, eyedropperCrosshairPaint);

        // Hex Code Pill Badge above / below loupe
        String hex = String.format(java.util.Locale.US, "#%08X", currentSampledColor);
        float badgeW = 160f;
        float badgeH = 36f;
        boolean loupeAbove = loupeY < lastEyedropperY;
        float badgeTop = loupeAbove ? (loupeY - loupeRadius - 44f) : (loupeY + loupeRadius + 12f);
        // Clamp badge within view
        badgeTop = Math.max(4f, Math.min(getHeight() - badgeH - 4f, badgeTop));
        float badgeLeft = Math.max(4f, Math.min(getWidth() - badgeW - 4f, loupeX - badgeW / 2f));
        RectF badgeRect = new RectF(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH);
        canvas.drawRoundRect(badgeRect, 10f, 10f, eyedropperBadgeBgPaint);
        canvas.drawText(hex, badgeRect.centerX(), badgeRect.centerY() + 8f, eyedropperTextPaint);

        canvas.restore();
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
        if (layer.getSkewX() != 0f || layer.getSkewY() != 0f) {
            float kx = (float) Math.tan(Math.toRadians(layer.getSkewX()));
            float ky = (float) Math.tan(Math.toRadians(layer.getSkewY()));
            layerMat.postSkew(kx, ky);
        }
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

    public interface OnColorSampledListener {
        void onColorSampled(int color);
    }
    private OnColorSampledListener colorSampledListener;
    private boolean isEyedropperActive = false;

    /** Listener to notify when eyedropper mode starts or stops */
    public interface OnEyedropperStateListener {
        void onEyedropperStarted();
        void onEyedropperStopped();
    }
    private OnEyedropperStateListener eyedropperStateListener;

    public void setEyedropperStateListener(OnEyedropperStateListener l) {
        this.eyedropperStateListener = l;
    }

    public void startEyedropper(OnColorSampledListener listener) {
        this.colorSampledListener = listener;
        this.isEyedropperActive = true;
        if (eyedropperStateListener != null) eyedropperStateListener.onEyedropperStarted();
        invalidate();
    }

    public void stopEyedropper() {
        this.isEyedropperActive = false;
        this.colorSampledListener = null;
        this.lastEyedropperX = -1f;
        this.lastEyedropperY = -1f;
        if (eyedropperStateListener != null) eyedropperStateListener.onEyedropperStopped();
        invalidate();
    }

    public boolean isEyedropperActive() {
        return isEyedropperActive;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);

        float vx = event.getX();
        float vy = event.getY();

        if (isEyedropperActive) {
            lastEyedropperX = vx;
            lastEyedropperY = vy;
            int sampledColor = sampleColorAt(vx, vy);
            currentSampledColor = sampledColor;
            if (colorSampledListener != null) {
                colorSampledListener.onColorSampled(sampledColor);
            }
            invalidate();
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopEyedropper();
            }
            return true;
        }

        if (event.getPointerCount() > 1) {
            // Multi-touch pan/zoom
            currentTouchMode = TouchMode.PAN_VIEWPORT;
            return true;
        }
        float[] canvasPt = mapToCanvas(vx, vy);
        float cx = canvasPt[0];
        float cy = canvasPt[1];

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = vx;
                lastTouchY = vy;

                // When pan mode is active, directly pan viewport without selecting or dragging layers
                if (isPanModeActive) {
                    currentTouchMode = TouchMode.PAN_VIEWPORT;
                    return true;
                }

                CanvasLayer selectedLayer = project != null ? project.getSelectedLayer() : null;

                // 1. Check if clicking on on-canvas Gradient Handles
                if (selectedLayer != null && !selectedLayer.isLocked() && hasGradientFill(selectedLayer)) {
                    int gradHandle = hitTestGradientHandle(selectedLayer, vx, vy);
                    if (gradHandle == 0) {
                        currentTouchMode = TouchMode.GRADIENT_START_HANDLE;
                        hasSavedSnapshotForInteraction = false;
                        return true;
                    } else if (gradHandle == 1) {
                        currentTouchMode = TouchMode.GRADIENT_END_HANDLE;
                        hasSavedSnapshotForInteraction = false;
                        return true;
                    }
                }

                // 2. Check if clicking on an active resize/rotation handle
                if (selectedLayer != null && !selectedLayer.isLocked()) {
                    int handle = hitTestHandle(selectedLayer, vx, vy);
                    if (handle >= 0) {
                        hasSavedSnapshotForInteraction = false;
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

                // 3. Check if clicking on a layer (topmost first)
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
                    if (!layer.isLocked()) {
                        currentTouchMode = TouchMode.DRAG_LAYER;
                    } else {
                        currentTouchMode = TouchMode.NONE;
                    }
                    hasSavedSnapshotForInteraction = false;

                    if (layerSelectedListener != null) {
                        layerSelectedListener.onLayerSelected(layer, clickedIndex);
                    }
                    invalidate();
                    return true;
                } else {
                    // Clicked empty area on canvas or backdrop
                    if (isViewportLocked) {
                        currentTouchMode = TouchMode.NONE;
                        if (project != null && project.getSelectedLayer() != null) {
                            project.setSelectedIndex(-1);
                            if (layerSelectedListener != null) {
                                layerSelectedListener.onLayerSelected(null, -1);
                            }
                            invalidate();
                        }
                    } else {
                        currentTouchMode = TouchMode.PAN_VIEWPORT;
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = vx - lastTouchX;
                float dy = vy - lastTouchY;
                float canvasDx = dx / viewportScale;
                float canvasDy = dy / viewportScale;

                CanvasLayer activeLayer = project != null ? project.getSelectedLayer() : null;

                if (currentTouchMode == TouchMode.GRADIENT_START_HANDLE && activeLayer != null) {
                    if (!hasSavedSnapshotForInteraction && (Math.abs(dx) > 2f || Math.abs(dy) > 2f)) {
                        project.saveSnapshot();
                        hasSavedSnapshotForInteraction = true;
                    }
                    float[] localPt = mapToLayerLocal(activeLayer, vx, vy);
                    if (activeLayer instanceof ShapeLayer) {
                        ((ShapeLayer) activeLayer).setGradientStartX(localPt[0]);
                        ((ShapeLayer) activeLayer).setGradientStartY(localPt[1]);
                    } else if (activeLayer instanceof PhotoLayer) {
                        ((PhotoLayer) activeLayer).setGradientStartX(localPt[0]);
                        ((PhotoLayer) activeLayer).setGradientStartY(localPt[1]);
                    }
                    if (layerSelectedListener != null) {
                        layerSelectedListener.onLayerModified(activeLayer);
                    }
                    invalidate();
                } else if (currentTouchMode == TouchMode.GRADIENT_END_HANDLE && activeLayer != null) {
                    if (!hasSavedSnapshotForInteraction && (Math.abs(dx) > 2f || Math.abs(dy) > 2f)) {
                        project.saveSnapshot();
                        hasSavedSnapshotForInteraction = true;
                    }
                    float[] localPt = mapToLayerLocal(activeLayer, vx, vy);
                    if (activeLayer instanceof ShapeLayer) {
                        ((ShapeLayer) activeLayer).setGradientEndX(localPt[0]);
                        ((ShapeLayer) activeLayer).setGradientEndY(localPt[1]);
                    } else if (activeLayer instanceof PhotoLayer) {
                        ((PhotoLayer) activeLayer).setGradientEndX(localPt[0]);
                        ((PhotoLayer) activeLayer).setGradientEndY(localPt[1]);
                    }
                    if (layerSelectedListener != null) {
                        layerSelectedListener.onLayerModified(activeLayer);
                    }
                    invalidate();
                } else if (currentTouchMode == TouchMode.DRAG_LAYER && activeLayer != null && !activeLayer.isLocked()) {
                    if (!hasSavedSnapshotForInteraction && (Math.abs(dx) > 2f || Math.abs(dy) > 2f)) {
                        project.saveSnapshot();
                        hasSavedSnapshotForInteraction = true;
                    }
                    float rawX = activeLayer.getX() + canvasDx;
                    float rawY = activeLayer.getY() + canvasDy;
                    PointF snapped = applyMagneticSnapping(activeLayer, rawX, rawY);
                    activeLayer.setX(snapped.x);
                    activeLayer.setY(snapped.y);
                    if (layerSelectedListener != null) {
                        layerSelectedListener.onLayerModified(activeLayer);
                    }
                    invalidate();
                } else if (currentTouchMode == TouchMode.RESIZE_HANDLE && activeLayer != null) {
                    if (!hasSavedSnapshotForInteraction && (Math.abs(dx) > 2f || Math.abs(dy) > 2f)) {
                        project.saveSnapshot();
                        hasSavedSnapshotForInteraction = true;
                    }
                    handleResize(activeLayer, canvasDx, canvasDy);
                    if (layerSelectedListener != null) {
                        layerSelectedListener.onLayerModified(activeLayer);
                    }
                    invalidate();
                } else if (currentTouchMode == TouchMode.ROTATE_HANDLE && activeLayer != null) {
                    if (!hasSavedSnapshotForInteraction && (Math.abs(dx) > 2f || Math.abs(dy) > 2f)) {
                        project.saveSnapshot();
                        hasSavedSnapshotForInteraction = true;
                    }
                    // Calculate angle from layer center to current touch point in canvas coords
                    float angleRad = (float) Math.atan2(cy - activeLayer.getY(), cx - activeLayer.getX());
                    float deg = (float) Math.toDegrees(angleRad);
                    activeLayer.setRotation(deg);
                    if (layerSelectedListener != null) {
                        layerSelectedListener.onLayerModified(activeLayer);
                    }
                    invalidate();
                } else if (currentTouchMode == TouchMode.PAN_VIEWPORT) {
                    if (isPanModeActive || !isViewportLocked) {
                        viewportTransX += dx;
                        viewportTransY += dy;
                        updateViewportMatrix();
                        invalidate();
                    }
                }

                lastTouchX = vx;
                lastTouchY = vy;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                currentTouchMode = TouchMode.NONE;
                activeHandleIndex = -1;
                hasSavedSnapshotForInteraction = false;
                clearMagneticSnapLines();
                break;
        }

        return true;
    }

    public void clearMagneticSnapLines() {
        activeSnapLinesX.clear();
        activeSnapLinesY.clear();
        wasSnapped = false;
        invalidate();
    }

    public PointF applyMagneticSnapping(CanvasLayer activeLayer, float targetX, float targetY) {
        if (project == null || activeLayer == null) return new PointF(targetX, targetY);

        float snapThreshold = 8f / viewportScale; // Reduced to ~8dp for subtle, non-sticky precision snapping
        float artW = project.getCanvasWidth();
        float artH = project.getCanvasHeight();

        float halfW = (activeLayer.getWidth() * Math.abs(activeLayer.getScaleX())) / 2f;
        float halfH = (activeLayer.getHeight() * Math.abs(activeLayer.getScaleY())) / 2f;

        float snappedX = targetX;
        float snappedY = targetY;

        activeSnapLinesX.clear();
        activeSnapLinesY.clear();

        // 1. Magnetic Snap to Parent Artboard (Horizontal X)
        if (Math.abs(targetX - artW / 2f) <= snapThreshold) {
            snappedX = artW / 2f;
            activeSnapLinesX.add(artW / 2f);
        } else if (Math.abs((targetX - halfW) - 0f) <= snapThreshold) {
            snappedX = halfW;
            activeSnapLinesX.add(0f);
        } else if (Math.abs((targetX + halfW) - artW) <= snapThreshold) {
            snappedX = artW - halfW;
            activeSnapLinesX.add(artW);
        }

        // 2. Magnetic Snap to Parent Artboard (Vertical Y)
        if (Math.abs(targetY - artH / 2f) <= snapThreshold) {
            snappedY = artH / 2f;
            activeSnapLinesY.add(artH / 2f);
        } else if (Math.abs((targetY - halfH) - 0f) <= snapThreshold) {
            snappedY = halfH;
            activeSnapLinesY.add(0f);
        } else if (Math.abs((targetY + halfH) - artH) <= snapThreshold) {
            snappedY = artH - halfH;
            activeSnapLinesY.add(artH);
        }

        // 3. Magnetic Snap to Sibling Elements
        for (CanvasLayer sib : project.getLayers()) {
            if (sib == activeLayer || !sib.isVisible()) continue;

            float sibX = sib.getX();
            float sibY = sib.getY();
            float sibHw = (sib.getWidth() * Math.abs(sib.getScaleX())) / 2f;
            float sibHh = (sib.getHeight() * Math.abs(sib.getScaleY())) / 2f;
            float sibLeft = sibX - sibHw;
            float sibRight = sibX + sibHw;
            float sibTop = sibY - sibHh;
            float sibBottom = sibY + sibHh;

            // X-axis alignment
            if (activeSnapLinesX.isEmpty()) {
                if (Math.abs(targetX - sibX) <= snapThreshold) {
                    snappedX = sibX;
                    activeSnapLinesX.add(sibX);
                } else if (Math.abs((targetX - halfW) - sibLeft) <= snapThreshold) {
                    snappedX = sibLeft + halfW;
                    activeSnapLinesX.add(sibLeft);
                } else if (Math.abs((targetX + halfW) - sibRight) <= snapThreshold) {
                    snappedX = sibRight - halfW;
                    activeSnapLinesX.add(sibRight);
                } else if (Math.abs((targetX - halfW) - sibRight) <= snapThreshold) {
                    snappedX = sibRight + halfW;
                    activeSnapLinesX.add(sibRight);
                } else if (Math.abs((targetX + halfW) - sibLeft) <= snapThreshold) {
                    snappedX = sibLeft - halfW;
                    activeSnapLinesX.add(sibLeft);
                }
            }

            // Y-axis alignment
            if (activeSnapLinesY.isEmpty()) {
                if (Math.abs(targetY - sibY) <= snapThreshold) {
                    snappedY = sibY;
                    activeSnapLinesY.add(sibY);
                } else if (Math.abs((targetY - halfH) - sibTop) <= snapThreshold) {
                    snappedY = sibTop + halfH;
                    activeSnapLinesY.add(sibTop);
                } else if (Math.abs((targetY + halfH) - sibBottom) <= snapThreshold) {
                    snappedY = sibBottom - halfH;
                    activeSnapLinesY.add(sibBottom);
                } else if (Math.abs((targetY - halfH) - sibBottom) <= snapThreshold) {
                    snappedY = sibBottom + halfH;
                    activeSnapLinesY.add(sibBottom);
                } else if (Math.abs((targetY + halfH) - sibTop) <= snapThreshold) {
                    snappedY = sibTop - halfH;
                    activeSnapLinesY.add(sibTop);
                }
            }
        }

        boolean nowSnapped = !activeSnapLinesX.isEmpty() || !activeSnapLinesY.isEmpty();
        if (nowSnapped && !wasSnapped) {
            try {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            } catch (Exception ignored) {}
        }
        wasSnapped = nowSnapped;

        return new PointF(snappedX, snappedY);
    }

    private void handleResize(CanvasLayer layer, float cdx, float cdy) {
        // Rotate delta into layer's coordinate frame
        float rad = (float) Math.toRadians(-layer.getRotation());
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float ldx = cdx * cos - cdy * sin;
        float ldy = cdx * sin + cdy * cos;

        float w = layer.getWidth();
        float h = layer.getHeight();
        float aspect = (initialLayerWidth > 0 && initialLayerHeight > 0)
                ? (initialLayerWidth / initialLayerHeight)
                : (w / Math.max(1f, h));

        switch (activeHandleIndex) {
            case 0: { // Top-Left (Uniform Proportional Scale)
                float scaleDelta = (-ldx / Math.max(20f, w) - ldy / Math.max(20f, h)) / 2f;
                float newW = Math.max(20f, w * (1f + scaleDelta * 2f));
                float newH = Math.max(20f, newW / aspect);
                layer.setWidth(newW);
                layer.setHeight(newH);
                break;
            }
            case 1: // Top-Mid (Height only)
                layer.setHeight(Math.max(20f, layer.getHeight() - ldy * 2));
                break;
            case 2: { // Top-Right (Uniform Proportional Scale)
                float scaleDelta = (ldx / Math.max(20f, w) - ldy / Math.max(20f, h)) / 2f;
                float newW = Math.max(20f, w * (1f + scaleDelta * 2f));
                float newH = Math.max(20f, newW / aspect);
                layer.setWidth(newW);
                layer.setHeight(newH);
                break;
            }
            case 3: // Right-Mid (Width only)
                layer.setWidth(Math.max(20f, layer.getWidth() + ldx * 2));
                break;
            case 4: { // Bottom-Right (Uniform Proportional Scale)
                float scaleDelta = (ldx / Math.max(20f, w) + ldy / Math.max(20f, h)) / 2f;
                float newW = Math.max(20f, w * (1f + scaleDelta * 2f));
                float newH = Math.max(20f, newW / aspect);
                layer.setWidth(newW);
                layer.setHeight(newH);
                break;
            }
            case 5: // Bottom-Mid (Height only)
                layer.setHeight(Math.max(20f, layer.getHeight() + ldy * 2));
                break;
            case 6: { // Bottom-Left (Uniform Proportional Scale)
                float scaleDelta = (-ldx / Math.max(20f, w) + ldy / Math.max(20f, h)) / 2f;
                float newW = Math.max(20f, w * (1f + scaleDelta * 2f));
                float newH = Math.max(20f, newW / aspect);
                layer.setWidth(newW);
                layer.setHeight(newH);
                break;
            }
            case 7: // Left-Mid (Width only)
                layer.setWidth(Math.max(20f, layer.getWidth() - ldx * 2));
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
        if (layer.getSkewX() != 0f || layer.getSkewY() != 0f) {
            float kx = (float) Math.tan(Math.toRadians(layer.getSkewX()));
            float ky = (float) Math.tan(Math.toRadians(layer.getSkewY()));
            layerMat.postSkew(kx, ky);
        }
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

    private int hitTestGradientHandle(CanvasLayer layer, float vx, float vy) {
        float startX, startY, endX, endY;
        if (layer instanceof ShapeLayer) {
            ShapeLayer sl = (ShapeLayer) layer;
            startX = sl.getGradientStartX(); startY = sl.getGradientStartY();
            endX = sl.getGradientEndX(); endY = sl.getGradientEndY();
        } else if (layer instanceof PhotoLayer) {
            PhotoLayer pl = (PhotoLayer) layer;
            startX = pl.getGradientStartX(); startY = pl.getGradientStartY();
            endX = pl.getGradientEndX(); endY = pl.getGradientEndY();
        } else {
            return -1;
        }

        Matrix layerMat = getLayerToViewMatrix(layer);
        float[] pts = new float[]{startX, startY, endX, endY};
        layerMat.mapPoints(pts);

        float hitRadiusSq = (HANDLE_RADIUS * 2.5f) * (HANDLE_RADIUS * 2.5f);
        float distStartSq = (pts[0] - vx) * (pts[0] - vx) + (pts[1] - vy) * (pts[1] - vy);
        float distEndSq = (pts[2] - vx) * (pts[2] - vx) + (pts[3] - vy) * (pts[3] - vy);

        if (distStartSq <= hitRadiusSq && distStartSq <= distEndSq) {
            return 0; // Start handle
        }
        if (distEndSq <= hitRadiusSq) {
            return 1; // End handle
        }
        return -1;
    }

    private float[] mapToLayerLocal(CanvasLayer layer, float vx, float vy) {
        Matrix layerMat = getLayerToViewMatrix(layer);
        Matrix inv = new Matrix();
        layerMat.invert(inv);
        float[] pt = new float[]{vx, vy};
        inv.mapPoints(pt);
        return pt;
    }

    private Matrix getLayerToViewMatrix(CanvasLayer layer) {
        Matrix layerMat = new Matrix();
        if (layer.getSkewX() != 0f || layer.getSkewY() != 0f) {
            float kx = (float) Math.tan(Math.toRadians(layer.getSkewX()));
            float ky = (float) Math.tan(Math.toRadians(layer.getSkewY()));
            layerMat.postSkew(kx, ky);
        }
        layerMat.postRotate(layer.getRotation());
        layerMat.postTranslate(layer.getX(), layer.getY());
        layerMat.postConcat(viewportMatrix);
        return layerMat;
    }

    private float[] mapToCanvas(float vx, float vy) {
        float[] pts = new float[]{vx, vy};
        inverseViewportMatrix.mapPoints(pts);
        return pts;
    }

    public static void renderLayersWithMasking(Canvas canvas, List<CanvasLayer> layers, Paint basePaint) {
        if (layers == null || layers.isEmpty()) return;
        int i = 0;
        while (i < layers.size()) {
            CanvasLayer layer = layers.get(i);
            if (!layer.isVisible()) {
                i++;
                continue;
            }

            // Check if NEXT layer is a MASK or EXCLUDE layer acting on the current layer
            boolean hasNextMask = i + 1 < layers.size()
                    && layers.get(i + 1).isVisible()
                    && layers.get(i + 1).getMaskType() != CanvasLayer.MaskType.NONE;

            if (hasNextMask) {
                CanvasLayer maskLayer = layers.get(i + 1);

                // Build compositing paint (opacity + blend mode) for the combined pair
                Paint pairPaint = buildLayerPaint(layer);

                // 1. Isolate the masked combination in a separate layer
                int saveCount = canvas.saveLayer(null, pairPaint);

                // 2. Draw the content layer underneath (no blend paint — already in saveLayer)
                Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                innerPaint.setAlpha(layer.getOpacity());
                layer.draw(canvas, innerPaint);

                // 3. Composite the mask layer with DST_IN (Mask) or DST_OUT (Exclude)
                Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                android.graphics.PorterDuff.Mode pdMode = (maskLayer.getMaskType() == CanvasLayer.MaskType.MASK) ?
                        android.graphics.PorterDuff.Mode.DST_IN : android.graphics.PorterDuff.Mode.DST_OUT;
                maskPaint.setXfermode(new android.graphics.PorterDuffXfermode(pdMode));

                int maskSave = canvas.saveLayer(null, maskPaint);
                maskLayer.draw(canvas, basePaint);
                canvas.restoreToCount(maskSave);

                canvas.restoreToCount(saveCount);
                i += 2; // Both content and mask consumed
            } else if (layer.getBlendMode() != CanvasLayer.BlendMode.NORMAL || layer.getOpacity() < 255) {
                // Apply opacity + blend mode via saveLayer
                Paint blendPaint = buildLayerPaint(layer);
                int saveCount = canvas.saveLayer(null, blendPaint);
                layer.draw(canvas, basePaint);
                canvas.restoreToCount(saveCount);
                i++;
            } else {
                layer.draw(canvas, basePaint);
                i++;
            }
        }
    }

    /**
     * Builds a compositing paint for a layer that encodes its opacity and blend mode.
     * Used with Canvas.saveLayer to ensure proper blending against the layers below.
     */
    private static Paint buildLayerPaint(CanvasLayer layer) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setAlpha(layer.getOpacity());
        CanvasLayer.BlendMode bm = layer.getBlendMode();
        if (bm != null && bm != CanvasLayer.BlendMode.NORMAL) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.graphics.BlendMode abm = bm.toAndroidBlendMode();
                if (abm != null) {
                    p.setBlendMode(abm);
                    return p;
                }
            }
            android.graphics.PorterDuff.Mode pd = bm.toPorterDuffMode();
            if (pd != android.graphics.PorterDuff.Mode.SRC_OVER) {
                p.setXfermode(new android.graphics.PorterDuffXfermode(pd));
            }
        }
        return p;
    }


    public Bitmap exportArtboardBitmap() {
        return exportArtboardBitmap(1.0f, true);
    }

    public Bitmap exportArtboardBitmap(float scaleMultiplier, boolean includeBackground) {
        if (project == null) return null;

        int w = Math.round(project.getCanvasWidth() * scaleMultiplier);
        int h = Math.round(project.getCanvasHeight() * scaleMultiplier);
        w = Math.max(1, w);
        h = Math.max(1, h);

        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        if (scaleMultiplier != 1.0f) {
            canvas.scale(scaleMultiplier, scaleMultiplier);
        }

        // Draw Background
        if (includeBackground && Color.alpha(project.getBackgroundColor()) != 0) {
            artboardBgPaint.setColor(project.getBackgroundColor());
            canvas.drawRect(0, 0, project.getCanvasWidth(), project.getCanvasHeight(), artboardBgPaint);
        }

        // Render Layers with Masking Support
        renderLayersWithMasking(canvas, project.getLayers(), baseLayerPaint);
        return result;
    }
}
