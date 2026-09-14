package glab.pixeleditor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class ColorSpectrumPlaneView extends View {

    private final Paint spectrumPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float currentHue = 0f; // 0..360
    private float currentSat = 1f; // 0..1
    private float currentValue = 1f; // 0..1

    public interface OnSpectrumColorChangedListener {
        void onSpectrumColorChanged(float hue, float sat);
    }

    private OnSpectrumColorChangedListener listener;

    public ColorSpectrumPlaneView(Context context) {
        super(context);
        init();
    }

    public ColorSpectrumPlaneView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorSpectrumPlaneView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        pointerPaint.setStyle(Paint.Style.FILL);
        pointerStrokePaint.setStyle(Paint.Style.STROKE);
        pointerStrokePaint.setColor(Color.WHITE);
        pointerStrokePaint.setStrokeWidth(3f);
    }

    public void setOnSpectrumColorChangedListener(OnSpectrumColorChangedListener listener) {
        this.listener = listener;
    }

    public void setColor(float hue, float sat, float val) {
        this.currentHue = (hue % 360f + 360f) % 360f;
        this.currentSat = Math.max(0f, Math.min(1f, sat));
        this.currentValue = Math.max(0f, Math.min(1f, val));
        updateShaders();
        invalidate();
    }

    public void setValue(float val) {
        this.currentValue = Math.max(0f, Math.min(1f, val));
        updateShaders();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShaders();
    }

    private void updateShaders() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Horizontal gradient across hues: Red -> Yellow -> Green -> Cyan -> Blue -> Magenta -> Red
        int[] hueColors = new int[]{
                0xFFFF0000, 0xFFFFFF00, 0xFF00FF00,
                0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
        };
        Shader hueShader = new LinearGradient(
                0, 0, w, 0, hueColors, null, Shader.TileMode.CLAMP
        );

        // Vertical saturation gradient: White (top) to Transparent (bottom)
        Shader satShader = new LinearGradient(
                0, 0, 0, h, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        );

        // Multiply hue and saturation
        ComposeShader compose = new ComposeShader(hueShader, satShader, PorterDuff.Mode.SRC_OVER);

        // If brightness/value is less than 1.0, darken
        if (currentValue < 1f) {
            int blackOverlay = Color.argb((int) ((1f - currentValue) * 255), 0, 0, 0);
            Shader valShader = new LinearGradient(
                    0, 0, 0, h, blackOverlay, blackOverlay, Shader.TileMode.CLAMP
            );
            compose = new ComposeShader(compose, valShader, PorterDuff.Mode.DARKEN);
        }

        spectrumPaint.setShader(compose);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Draw spectrum background with rounded corners
        canvas.drawRoundRect(0, 0, w, h, 12f, 12f, spectrumPaint);

        // Draw pointer at (Hue, Sat) position
        float px = (currentHue / 360f) * w;
        float py = (1f - currentSat) * h;

        int selectedColor = Color.HSVToColor(new float[]{currentHue, currentSat, currentValue});
        pointerPaint.setColor(selectedColor);

        canvas.drawCircle(px, py, 10f, pointerStrokePaint);
        canvas.drawCircle(px, py, 7f, pointerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float x = Math.max(0f, Math.min(getWidth(), event.getX()));
                float y = Math.max(0f, Math.min(getHeight(), event.getY()));

                currentHue = (x / getWidth()) * 360f;
                currentSat = 1f - (y / getHeight());

                invalidate();
                if (listener != null) {
                    listener.onSpectrumColorChanged(currentHue, currentSat);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}
