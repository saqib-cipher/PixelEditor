package glab.pixeleditor.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class CutoutPreviewView extends View {

    public interface OnPreviewTouchListener {
        void onImageTap(float normX, float normY);
        void onBrushStroke(float normX, float normY, float normRadius, boolean isErase);
        void onBrushStrokeEnd();
    }

    public enum Mode {
        PREVIEW,
        TAP_SELECT,
        BRUSH_ERASE,
        BRUSH_RESTORE
    }

    private Mode currentMode = Mode.PREVIEW;
    private OnPreviewTouchListener touchListener;

    private Bitmap displayBitmap;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint checkerPaint = new Paint();
    private final Paint brushIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tapIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF imageDstRect = new RectF();
    private final Matrix imageMatrix = new Matrix();
    private final Matrix invertMatrix = new Matrix();

    private float brushRadiusPx = 30f;
    private float lastTouchX = -1f;
    private float lastTouchY = -1f;
    private boolean showBrushIndicator = false;

    private float lastTapX = -1f;
    private float lastTapY = -1f;

    public CutoutPreviewView(Context context) {
        super(context);
        init();
    }

    public CutoutPreviewView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CutoutPreviewView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Create checkerboard pattern
        Bitmap checkerBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(checkerBmp);
        Paint p1 = new Paint();
        p1.setColor(0xFF1E2638);
        Paint p2 = new Paint();
        p2.setColor(0xFF161D2D);
        c.drawRect(0, 0, 16, 16, p1);
        c.drawRect(16, 0, 32, 16, p2);
        c.drawRect(0, 16, 16, 32, p2);
        c.drawRect(16, 16, 32, 32, p1);

        BitmapShader shader = new BitmapShader(checkerBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        checkerPaint.setShader(shader);

        brushIndicatorPaint.setStyle(Paint.Style.STROKE);
        brushIndicatorPaint.setStrokeWidth(3f);
        brushIndicatorPaint.setColor(0xFF00FFCC);

        tapIndicatorPaint.setStyle(Paint.Style.STROKE);
        tapIndicatorPaint.setStrokeWidth(4f);
        tapIndicatorPaint.setColor(0xFF00FFCC);
    }

    public void setDisplayBitmap(Bitmap bitmap) {
        this.displayBitmap = bitmap;
        calculateImageBounds();
        invalidate();
    }

    public void setMode(Mode mode) {
        this.currentMode = mode;
        this.showBrushIndicator = false;
        invalidate();
    }

    public void setBrushRadius(float radiusPx) {
        this.brushRadiusPx = radiusPx;
        invalidate();
    }

    public void setOnPreviewTouchListener(OnPreviewTouchListener listener) {
        this.touchListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateImageBounds();
    }

    private void calculateImageBounds() {
        if (displayBitmap == null || getWidth() <= 0 || getHeight() <= 0) return;

        int vw = getWidth() - getPaddingLeft() - getPaddingRight();
        int vh = getHeight() - getPaddingTop() - getPaddingBottom();
        int bw = displayBitmap.getWidth();
        int bh = displayBitmap.getHeight();

        float scale = Math.min((float) vw / bw, (float) vh / bh);
        float dw = bw * scale;
        float dh = bh * scale;

        float left = getPaddingLeft() + (vw - dw) / 2f;
        float top = getPaddingTop() + (vh - dh) / 2f;

        imageDstRect.set(left, top, left + dw, top + dh);

        imageMatrix.reset();
        imageMatrix.postScale(scale, scale);
        imageMatrix.postTranslate(left, top);
        imageMatrix.invert(invertMatrix);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (displayBitmap == null || displayBitmap.isRecycled()) return;

        // Draw checkerboard behind image
        canvas.drawRect(imageDstRect, checkerPaint);

        // Draw current bitmap
        canvas.drawBitmap(displayBitmap, null, imageDstRect, bitmapPaint);

        // Draw tap crosshair / ripple indicator
        if (currentMode == Mode.TAP_SELECT && lastTapX >= 0 && lastTapY >= 0) {
            canvas.drawCircle(lastTapX, lastTapY, 18f, tapIndicatorPaint);
            canvas.drawLine(lastTapX - 26f, lastTapY, lastTapX + 26f, lastTapY, tapIndicatorPaint);
            canvas.drawLine(lastTapX, lastTapY - 26f, lastTapX, lastTapY + 26f, tapIndicatorPaint);
        }

        // Draw brush cursor indicator
        if (showBrushIndicator && (currentMode == Mode.BRUSH_ERASE || currentMode == Mode.BRUSH_RESTORE)) {
            brushIndicatorPaint.setColor(currentMode == Mode.BRUSH_ERASE ? 0xFFFF4466 : 0xFF00FFCC);
            canvas.drawCircle(lastTouchX, lastTouchY, brushRadiusPx, brushIndicatorPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (displayBitmap == null) return super.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();

        if (currentMode == Mode.TAP_SELECT) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (imageDstRect.contains(x, y)) {
                    lastTapX = x;
                    lastTapY = y;
                    float normX = (x - imageDstRect.left) / imageDstRect.width();
                    float normY = (y - imageDstRect.top) / imageDstRect.height();
                    normX = Math.max(0f, Math.min(1f, normX));
                    normY = Math.max(0f, Math.min(1f, normY));

                    if (touchListener != null) {
                        touchListener.onImageTap(normX, normY);
                    }
                    invalidate();
                }
            }
            return true;
        }

        if (currentMode == Mode.BRUSH_ERASE || currentMode == Mode.BRUSH_RESTORE) {
            lastTouchX = x;
            lastTouchY = y;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    showBrushIndicator = true;
                    if (imageDstRect.contains(x, y)) {
                        float normX = (x - imageDstRect.left) / imageDstRect.width();
                        float normY = (y - imageDstRect.top) / imageDstRect.height();
                        normX = Math.max(0f, Math.min(1f, normX));
                        normY = Math.max(0f, Math.min(1f, normY));
                        float normRadius = brushRadiusPx / Math.max(imageDstRect.width(), imageDstRect.height());

                        if (touchListener != null) {
                            touchListener.onBrushStroke(normX, normY, normRadius, currentMode == Mode.BRUSH_ERASE);
                        }
                    }
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    showBrushIndicator = false;
                    if (touchListener != null) {
                        touchListener.onBrushStrokeEnd();
                    }
                    invalidate();
                    return true;
            }
        }

        return super.onTouchEvent(event);
    }
}
