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
        float letterSpacing = getEffectiveLetterSpacing();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            paint.setLetterSpacing(letterSpacing);
        }
        int style = Typeface.NORMAL;
        if (isBold && isItalic) style = Typeface.BOLD_ITALIC;
        else if (isBold) style = Typeface.BOLD;
        else if (isItalic) style = Typeface.ITALIC;
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, style));

        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        Paint.FontMetrics fm = paint.getFontMetrics();
        this.width = Math.max(60f, bounds.width() + 30f);
        this.height = Math.max(30f, (fm.bottom - fm.top) + 20f);
    }

    private float getEffectiveLetterSpacing() {
        for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
            if (!eff.isEnabled()) continue;
            String id = eff.getId().toLowerCase();
            String name = eff.getName().toLowerCase();
            if (id.contains("textspacing") || name.contains("spacing")) {
                glab.pixeleditor.effect.EffectParam ls = eff.getParam("letterspacing");
                if (ls != null) {
                    return ls.getFloatValue();
                }
            }
        }
        return 0f;
    }

    @Override
    public void draw(Canvas canvas, Paint basePaint) {
        if (!isVisible || text == null || text.isEmpty()) return;

        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        canvas.scale(scaleX, scaleY);
        if (skewX != 0f || skewY != 0f) {
            canvas.skew((float) Math.tan(Math.toRadians(skewX)), (float) Math.tan(Math.toRadians(skewY)));
        }

        // Apply Effect Geometric Transforms (e.g. Stretch Axis, Flip, 3D Camera)
        glab.pixeleditor.effect.EffectPipeline.applyEffectTransforms(canvas, this);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setAlpha(opacity);

        float effectiveLetterSpacing = getEffectiveLetterSpacing();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            paint.setLetterSpacing(effectiveLetterSpacing);
        }

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
        int effectiveTextColor = textColor;

        // Check Text Effects for transforms, styling, and text content changes
        String displayText = text;
        float textEffOffsetX = 0f;
        float textEffOffsetY = 0f;
        float textEffAngle = 0f;
        float textEffScale = 1.0f;
        float textEffAlpha = 1.0f;

        for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
            if (!eff.isEnabled()) continue;
            String id = eff.getId().toLowerCase();
            String name = eff.getName().toLowerCase();
            String fn = eff.getFileName() != null ? eff.getFileName().toLowerCase() : "";

            // 1. Text Transform effect
            if (id.contains("texttransform") || name.contains("text transform") || fn.contains("text-transform")) {
                glab.pixeleditor.effect.EffectParam useFill = eff.getParam("useFillColor");
                glab.pixeleditor.effect.EffectParam fillCol = eff.getParam("fillColor");
                if (fillCol != null && (useFill == null || useFill.getBooleanValue())) {
                    effectiveTextColor = fillCol.getColorValue();
                }

                glab.pixeleditor.effect.EffectParam pStart = eff.getParam("start");
                glab.pixeleditor.effect.EffectParam pEnd = eff.getParam("end");
                if (pStart != null || pEnd != null) {
                    float s = pStart != null ? Math.max(0f, pStart.getFloatValue()) : 0f;
                    float e = pEnd != null ? Math.min(1f, pEnd.getFloatValue()) : 1f;
                    int startIdx = Math.max(0, Math.min(displayText.length(), (int) (displayText.length() * s)));
                    int endIdx = Math.max(startIdx, Math.min(displayText.length(), (int) Math.ceil(displayText.length() * e)));
                    displayText = displayText.substring(startIdx, endIdx);
                }

                glab.pixeleditor.effect.EffectParam pOffX = eff.getParam("offset_x");
                glab.pixeleditor.effect.EffectParam pOffY = eff.getParam("offset_y");
                if (pOffX != null) textEffOffsetX += pOffX.getFloatValue();
                if (pOffY != null) textEffOffsetY += pOffY.getFloatValue();

                glab.pixeleditor.effect.EffectParam pAngle = eff.getParam("angle");
                if (pAngle != null) textEffAngle += pAngle.getFloatValue();

                glab.pixeleditor.effect.EffectParam pScale = eff.getParam("scale");
                if (pScale != null && pScale.getFloatValue() != 0f) {
                    textEffScale *= (1.0f + pScale.getFloatValue());
                }

                glab.pixeleditor.effect.EffectParam pAlpha = eff.getParam("alpha");
                if (pAlpha != null) {
                    textEffAlpha *= Math.max(0f, Math.min(1f, 1f + pAlpha.getFloatValue()));
                }

            // 2. Text Progress effect
            } else if (id.contains("textprogress") || name.contains("text progress") || fn.contains("textprogress")) {
                glab.pixeleditor.effect.EffectParam pStart = eff.getParam("start");
                glab.pixeleditor.effect.EffectParam pEnd = eff.getParam("end");
                if (pEnd == null) pEnd = eff.getParam("progress");

                float s = pStart != null ? Math.max(0f, Math.min(1f, pStart.getFloatValue())) : 0f;
                float e = pEnd != null ? Math.max(s, Math.min(1f, pEnd.getFloatValue())) : 1f;
                int startIdx = Math.max(0, Math.min(displayText.length(), (int) (displayText.length() * s)));
                int endIdx = Math.max(startIdx, Math.min(displayText.length(), (int) Math.ceil(displayText.length() * e)));
                displayText = displayText.substring(startIdx, endIdx);

            // 3. Text Randomize effect
            } else if (id.contains("textrand") || name.contains("randomize") || fn.contains("textrand")) {
                glab.pixeleditor.effect.EffectParam pAmount = eff.getParam("amount");
                float amt = pAmount != null ? Math.max(0f, Math.min(1f, pAmount.getFloatValue())) : 0f;
                if (amt > 0f && !displayText.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    String charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
                    for (int i = 0; i < displayText.length(); i++) {
                        if (Math.random() < amt && displayText.charAt(i) != ' ') {
                            int rIdx = (int) (Math.random() * charset.length());
                            sb.append(charset.charAt(rIdx));
                        } else {
                            sb.append(displayText.charAt(i));
                        }
                    }
                    displayText = sb.toString();
                }
            }
        }

        if (textEffOffsetX != 0f || textEffOffsetY != 0f) {
            canvas.translate(textEffOffsetX, textEffOffsetY);
        }
        if (textEffAngle != 0f) {
            canvas.rotate(textEffAngle);
        }
        if (textEffScale != 1.0f && textEffScale > 0f) {
            canvas.scale(textEffScale, textEffScale);
        }

        fillPaint.setColor(effectiveTextColor);
        if (textEffAlpha < 1.0f) {
            fillPaint.setAlpha((int) (paint.getAlpha() * textEffAlpha));
            paint.setAlpha((int) (paint.getAlpha() * textEffAlpha));
        }

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
        if (strokePaint != null && !displayText.isEmpty()) {
            canvas.drawText(displayText, 0, baseline, strokePaint);
        }

        // Draw Fill
        if (!displayText.isEmpty()) {
            canvas.drawText(displayText, 0, baseline, fillPaint);
        }

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
        copy.setSkewX(skewX);
        copy.setSkewY(skewY);
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
        if (width <= 0) return;
        float oldW = Math.max(10f, this.width);
        float ratio = width / oldW;
        this.textSize = Math.max(10f, this.textSize * ratio);
        recalculateBounds();
    }

    @Override
    public void setHeight(float height) {
        if (height <= 0) return;
        float oldH = Math.max(10f, this.height);
        float ratio = height / oldH;
        this.textSize = Math.max(10f, this.textSize * ratio);
        recalculateBounds();
    }

    public boolean isHasStroke() { return hasStroke; }
    public void setHasStroke(boolean hasStroke) { this.hasStroke = hasStroke; }
    public boolean isHasShadow() { return hasShadow; }
    public void setHasShadow(boolean hasShadow) { this.hasShadow = hasShadow; }

    @Override
    public org.json.JSONObject toJson(android.content.Context context) {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("layerType", "TEXT");
            writeBaseJson(json);
            json.put("text", text);
            json.put("textColor", textColor);
            json.put("textSize", textSize);
            json.put("isBold", isBold);
            json.put("isItalic", isItalic);
            json.put("strokeColor", strokeColor);
            json.put("strokeWidth", strokeWidth);
            json.put("hasStroke", hasStroke);
            json.put("hasShadow", hasShadow);
        } catch (Exception ignored) {}
        return json;
    }

    public static TextLayer fromJson(android.content.Context context, org.json.JSONObject json) {
        if (json == null) return null;
        String name = json.optString("name", "Text");
        String text = json.optString("text", "Pixel Editor");
        float x = (float) json.optDouble("x", 200);
        float y = (float) json.optDouble("y", 200);

        TextLayer layer = new TextLayer(name, text, x, y);
        layer.readBaseJson(json);

        layer.textColor = json.optInt("textColor", 0xFFFFFFFF);
        layer.textSize = (float) json.optDouble("textSize", 48.0);
        layer.isBold = json.optBoolean("isBold", true);
        layer.isItalic = json.optBoolean("isItalic", false);
        layer.strokeColor = json.optInt("strokeColor", 0xFF000000);
        layer.strokeWidth = (float) json.optDouble("strokeWidth", 0.0);
        layer.hasStroke = json.optBoolean("hasStroke", false);
        layer.hasShadow = json.optBoolean("hasShadow", true);

        layer.recalculateBounds();
        return layer;
    }
}
