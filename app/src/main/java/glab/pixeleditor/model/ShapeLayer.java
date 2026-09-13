package glab.pixeleditor.model;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

public class ShapeLayer extends CanvasLayer {

    public enum ShapeType {
        RECTANGLE,
        ROUNDED_RECT,
        CIRCLE,
        STAR,
        HEART,
        TRIANGLE,
        DIAMOND,
        SHIELD,
        CLOUD,
        DROP,
        LIGHTNING
    }

    private ShapeType shapeType = ShapeType.ROUNDED_RECT;
    private int fillColor = 0xFF7A4B58; // muted mauve/burgundy as in screenshot
    private int strokeColor = 0xFF00E5BC;
    private float strokeWidth = 0f;
    private boolean hasStroke = false;
    private float cornerRadius = 25f;

    public ShapeLayer(String name, float x, float y, float width, float height) {
        super(name, x, y, width, height);
    }

    @Override
    public void draw(Canvas canvas, Paint basePaint) {
        if (!isVisible) return;

        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        canvas.scale(scaleX, scaleY);
        if (skewX != 0f || skewY != 0f) {
            canvas.skew((float) Math.tan(Math.toRadians(skewX)), (float) Math.tan(Math.toRadians(skewY)));
        }

        // Apply Effect Geometric Transforms (e.g. Stretch Axis, Flip, 3D)
        glab.pixeleditor.effect.EffectPipeline.applyEffectTransforms(canvas, this);

        float left = -width / 2f;
        float top = -height / 2f;
        float right = width / 2f;
        float bottom = height / 2f;
        RectF rect = new RectF(left, top, right, bottom);

        // Fill Paint
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
        fillPaint.setAlpha(opacity);

        // Stroke Paint
        Paint strokePaint = null;
        if (hasStroke && strokeWidth > 0) {
            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(strokeColor);
            strokePaint.setStrokeWidth(strokeWidth);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            strokePaint.setAlpha(opacity);
        }

        // Apply ColorMatrix / Filters from active effects
        android.graphics.ColorFilter filter = glab.pixeleditor.effect.EffectPipeline.createCombinedColorFilter(this);
        if (filter != null) {
            fillPaint.setColorFilter(filter);
            if (strokePaint != null) strokePaint.setColorFilter(filter);
        }

        // Apply Masks, Blurs, Trims, Shadow, and Gradient Overlays
        glab.pixeleditor.effect.EffectPipeline.applyMaskAndStyling(canvas, this, rect, fillPaint, strokePaint);

        drawShapeGeometry(canvas, rect, fillPaint, strokePaint);

        // Apply post-draw effects (e.g. Vignette)
        glab.pixeleditor.effect.EffectPipeline.applyPostDraw(canvas, this, rect);

        canvas.restore();
    }

    private void drawShapeGeometry(Canvas canvas, RectF rect, Paint fillPaint, Paint strokePaint) {
        switch (shapeType) {
            case RECTANGLE:
                canvas.drawRect(rect, fillPaint);
                if (strokePaint != null) canvas.drawRect(rect, strokePaint);
                break;
            case ROUNDED_RECT:
                float r = Math.min(cornerRadius, Math.min(rect.width(), rect.height()) / 2f);
                canvas.drawRoundRect(rect, r, r, fillPaint);
                if (strokePaint != null) canvas.drawRoundRect(rect, r, r, strokePaint);
                break;
            case CIRCLE:
                canvas.drawOval(rect, fillPaint);
                if (strokePaint != null) canvas.drawOval(rect, strokePaint);
                break;
            case STAR:
                Path starPath = createStarPath(rect);
                canvas.drawPath(starPath, fillPaint);
                if (strokePaint != null) canvas.drawPath(starPath, strokePaint);
                break;
            case HEART:
                Path heartPath = createHeartPath(rect);
                canvas.drawPath(heartPath, fillPaint);
                if (strokePaint != null) canvas.drawPath(heartPath, strokePaint);
                break;
            case TRIANGLE:
                Path triPath = new Path();
                triPath.moveTo(rect.centerX(), rect.top);
                triPath.lineTo(rect.right, rect.bottom);
                triPath.lineTo(rect.left, rect.bottom);
                triPath.close();
                canvas.drawPath(triPath, fillPaint);
                if (strokePaint != null) canvas.drawPath(triPath, strokePaint);
                break;
            case DIAMOND:
                Path diamondPath = new Path();
                diamondPath.moveTo(rect.centerX(), rect.top);
                diamondPath.lineTo(rect.right, rect.centerY());
                diamondPath.lineTo(rect.centerX(), rect.bottom);
                diamondPath.lineTo(rect.left, rect.centerY());
                diamondPath.close();
                canvas.drawPath(diamondPath, fillPaint);
                if (strokePaint != null) canvas.drawPath(diamondPath, strokePaint);
                break;
            case SHIELD:
                Path shieldPath = createShieldPath(rect);
                canvas.drawPath(shieldPath, fillPaint);
                if (strokePaint != null) canvas.drawPath(shieldPath, strokePaint);
                break;
            case DROP:
                Path dropPath = createDropPath(rect);
                canvas.drawPath(dropPath, fillPaint);
                if (strokePaint != null) canvas.drawPath(dropPath, strokePaint);
                break;
            case CLOUD:
            case LIGHTNING:
            default:
                float rad = Math.min(cornerRadius, Math.min(rect.width(), rect.height()) / 2f);
                canvas.drawRoundRect(rect, rad, rad, fillPaint);
                if (strokePaint != null) canvas.drawRoundRect(rect, rad, rad, strokePaint);
                break;
        }
    }

    private Path createStarPath(RectF rect) {
        Path path = new Path();
        float cx = rect.centerX();
        float cy = rect.centerY();
        float outerR = Math.min(rect.width(), rect.height()) / 2f;
        float innerR = outerR * 0.45f;
        int points = 5;
        double step = Math.PI / points;
        double angle = -Math.PI / 2.0;

        path.moveTo((float) (cx + outerR * Math.cos(angle)), (float) (cy + outerR * Math.sin(angle)));
        for (int i = 0; i < points * 2; i++) {
            angle += step;
            float r = (i % 2 == 0) ? innerR : outerR;
            path.lineTo((float) (cx + r * Math.cos(angle)), (float) (cy + r * Math.sin(angle)));
        }
        path.close();
        return path;
    }

    private Path createHeartPath(RectF rect) {
        Path path = new Path();
        float w = rect.width();
        float h = rect.height();
        float l = rect.left;
        float t = rect.top;

        path.moveTo(l + w / 2f, t + h * 0.8f);
        path.cubicTo(l, t + h * 0.5f, l, t, l + w * 0.25f, t);
        path.cubicTo(l + w * 0.45f, t, l + w / 2f, t + h * 0.2f, l + w / 2f, t + h * 0.35f);
        path.cubicTo(l + w / 2f, t + h * 0.2f, l + w * 0.55f, t, l + w * 0.75f, t);
        path.cubicTo(l + w, t, l + w, t + h * 0.5f, l + w / 2f, t + h * 0.8f);
        path.close();
        return path;
    }

    private Path createShieldPath(RectF rect) {
        Path path = new Path();
        path.moveTo(rect.centerX(), rect.top);
        path.lineTo(rect.right, rect.top + rect.height() * 0.2f);
        path.quadTo(rect.right, rect.bottom * 0.7f, rect.centerX(), rect.bottom);
        path.quadTo(rect.left, rect.bottom * 0.7f, rect.left, rect.top + rect.height() * 0.2f);
        path.close();
        return path;
    }

    private Path createDropPath(RectF rect) {
        Path path = new Path();
        path.moveTo(rect.centerX(), rect.top);
        path.cubicTo(rect.right, rect.top + rect.height() * 0.4f, rect.right, rect.bottom, rect.centerX(), rect.bottom);
        path.cubicTo(rect.left, rect.bottom, rect.left, rect.top + rect.height() * 0.4f, rect.centerX(), rect.top);
        path.close();
        return path;
    }

    @Override
    public CanvasLayer copy() {
        ShapeLayer copy = new ShapeLayer(this.name + " Copy", x + 30, y + 30, width, height);
        copy.setRotation(rotation);
        copy.setScaleX(scaleX);
        copy.setScaleY(scaleY);
        copy.setSkewX(skewX);
        copy.setSkewY(skewY);
        copy.setOpacity(opacity);
        copy.setShapeType(shapeType);
        copy.setFillColor(fillColor);
        copy.setStrokeColor(strokeColor);
        copy.setStrokeWidth(strokeWidth);
        copy.setHasStroke(hasStroke);
        copy.setCornerRadius(cornerRadius);
        for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
            copy.addEffect(eff.copy());
        }
        return copy;
    }

    // Getters and Setters
    public ShapeType getShapeType() { return shapeType; }
    public void setShapeType(ShapeType shapeType) { this.shapeType = shapeType; }
    public int getFillColor() { return fillColor; }
    public void setFillColor(int fillColor) { this.fillColor = fillColor; }
    public int getStrokeColor() { return strokeColor; }
    public void setStrokeColor(int strokeColor) { this.strokeColor = strokeColor; }
    public float getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(float strokeWidth) { this.strokeWidth = Math.max(0f, strokeWidth); }
    public boolean isHasStroke() { return hasStroke; }
    public void setHasStroke(boolean hasStroke) { this.hasStroke = hasStroke; }
    public float getCornerRadius() { return cornerRadius; }
    public void setCornerRadius(float cornerRadius) { this.cornerRadius = Math.max(0f, cornerRadius); }
}
