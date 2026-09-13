package glab.pixeleditor.model;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

public class TextLayer extends CanvasLayer {

    private String text = "Pixel Editor";
    private int textColor = 0xFFFFFFFF;
    private float textSize = 48f;
    private boolean isBold = true;
    private boolean isItalic = false;
    private int strokeColor = 0xFF000000;
    private float strokeWidth = 0f;
    private boolean hasStroke = false;
    private boolean hasShadow = true;

    public TextLayer(String name, String text, float x, float y) {
        super(name, x, y, 300, 100);
        this.text = text;
        recalculateBounds();
    }

    public void recalculateBounds() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        int style = Typeface.NORMAL;
        if (isBold && isItalic) style = Typeface.BOLD_ITALIC;
        else if (isBold) style = Typeface.BOLD;
        else if (isItalic) style = Typeface.ITALIC;
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, style));

        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        this.width = Math.max(80f, bounds.width() + 40f);
        this.height = Math.max(40f, bounds.height() + 30f);
    }

    @Override
    public void draw(Canvas canvas, Paint basePaint) {
        if (!isVisible || text == null || text.isEmpty()) return;

        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        canvas.scale(scaleX, scaleY);

        // Apply Effect Geometric Transforms (e.g. Stretch Axis, Flip)
        glab.pixeleditor.effect.EffectPipeline.applyEffectTransforms(canvas, this);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setAlpha(opacity);

        int style = Typeface.NORMAL;
        if (isBold && isItalic) style = Typeface.BOLD_ITALIC;
        else if (isBold) style = Typeface.BOLD;
        else if (isItalic) style = Typeface.ITALIC;
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, style));

        // Center baseline
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = -(fm.ascent + fm.descent) / 2f;

        // Shadow
        if (hasShadow) {
            paint.setShadowLayer(8f, 2f, 4f, 0x88000000);
        }

        RectF bounds = new RectF(-width / 2f, -height / 2f, width / 2f, height / 2f);

        // Fill Paint
        Paint fillPaint = new Paint(paint);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(textColor);

        // Stroke Paint
        Paint strokePaint = null;
        if (hasStroke && strokeWidth > 0) {
            strokePaint = new Paint(paint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(strokeWidth);
            strokePaint.setColor(strokeColor);
        }

        // ColorFilter combining effects
        android.graphics.ColorFilter filter = glab.pixeleditor.effect.EffectPipeline.createCombinedColorFilter(this);
        if (filter != null) {
            fillPaint.setColorFilter(filter);
            if (strokePaint != null) strokePaint.setColorFilter(filter);
        }

        // Apply Mask, Trim, Blur, Shadow, Gradient
        glab.pixeleditor.effect.EffectPipeline.applyMaskAndStyling(canvas, this, bounds, fillPaint, strokePaint);

        // Draw Stroke
        if (strokePaint != null) {
            canvas.drawText(text, 0, baseline, strokePaint);
        }

        // Draw Fill
        canvas.drawText(text, 0, baseline, fillPaint);

        // Post-draw vignette
        glab.pixeleditor.effect.EffectPipeline.applyPostDraw(canvas, this, bounds);

        canvas.restore();
    }

    @Override
    public CanvasLayer copy() {
        TextLayer copy = new TextLayer(this.name + " Copy", text, x + 25, y + 25);
        copy.setRotation(rotation);
        copy.setScaleX(scaleX);
        copy.setScaleY(scaleY);
        copy.setOpacity(opacity);
        copy.setTextColor(textColor);
        copy.setTextSize(textSize);
        copy.setBold(isBold);
        copy.setItalic(isItalic);
        copy.setStrokeColor(strokeColor);
        copy.setStrokeWidth(strokeWidth);
        copy.setHasStroke(hasStroke);
        copy.setHasShadow(hasShadow);
        copy.recalculateBounds();
        for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
            copy.addEffect(eff.copy());
        }
        return copy;
    }

    // Getters and Setters
    public String getText() { return text; }
    public void setText(String text) {
        this.text = text;
        recalculateBounds();
    }
    public int getTextColor() { return textColor; }
    public void setTextColor(int textColor) { this.textColor = textColor; }
    public float getTextSize() { return textSize; }
    public void setTextSize(float textSize) {
        this.textSize = Math.max(12f, textSize);
        recalculateBounds();
    }
    public boolean isBold() { return isBold; }
    public void setBold(boolean bold) {
        isBold = bold;
        recalculateBounds();
    }
    public boolean isItalic() { return isItalic; }
    public void setItalic(boolean italic) {
        isItalic = italic;
        recalculateBounds();
    }
    public int getStrokeColor() { return strokeColor; }
    public void setStrokeColor(int strokeColor) { this.strokeColor = strokeColor; }
    public float getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(float strokeWidth) { this.strokeWidth = strokeWidth; }
    @Override
    public void setWidth(float width) {
        float oldW = Math.max(10f, this.width);
        super.setWidth(width);
        if (oldW > 0 && this.width > 0) {
            float ratio = this.width / oldW;
            this.textSize = Math.max(8f, this.textSize * ratio);
        }
    }

    @Override
    public void setHeight(float height) {
        float oldH = Math.max(10f, this.height);
        super.setHeight(height);
        if (oldH > 0 && this.height > 0) {
            float ratio = this.height / oldH;
            this.textSize = Math.max(8f, this.textSize * ratio);
        }
    }

    public boolean isHasStroke() { return hasStroke; }
    public void setHasStroke(boolean hasStroke) { this.hasStroke = hasStroke; }
    public boolean isHasShadow() { return hasShadow; }
    public void setHasShadow(boolean hasShadow) { this.hasShadow = hasShadow; }
}
