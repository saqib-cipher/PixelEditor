package glab.pixeleditor.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;

import org.json.JSONObject;

import glab.pixeleditor.font.FontManager;

public class TextLayer extends CanvasLayer {

    private String text = "Pixel Editor";
    private int textColor = 0xFFFFFFFF;
    private float textSize = 48f;
    private boolean isBold = true;
    private boolean isItalic = false;
    private boolean isMonospace = false;
    private String alignment = "CENTER"; // LEFT, CENTER, RIGHT
    private String fontPath = null;
    private String fontName = null;
    private float letterSpacing = 0f;
    private float lineSpacing = 1.0f;

    // Fill Mode (Solid, Gradient, Media, None)
    private ShapeLayer.FillMode fillMode = ShapeLayer.FillMode.SOLID;

    // Media Fill properties
    private Bitmap mediaBitmap;
    private String mediaUri;
    private String mediaName = "Media 1";
    private ShapeLayer.MediaScaleMode mediaScaleMode = ShapeLayer.MediaScaleMode.FILL;

    // Gradient properties
    private ShapeLayer.GradientType gradientType = ShapeLayer.GradientType.LINEAR;
    private int gradientStartColor = 0xFF000000;
    private int gradientEndColor = 0xFFFFFFFF;
    private float gradientStartX = -100f;
    private float gradientStartY = -100f;
    private float gradientEndX = 100f;
    private float gradientEndY = 100f;
    private float gradientStartOffset = 0.0f;
    private float gradientEndOffset = 1.0f;

    private int strokeColor = 0xFF000000;
    private float strokeWidth = 0f;
    private boolean hasStroke = false;
    private boolean hasShadow = false;

    public TextLayer(String name, String text, float x, float y) {
        super(name, x, y, 300, 100);
        this.text = text;
        this.gradientStartX = -width / 3f;
        this.gradientStartY = -height / 3f;
        this.gradientEndX = width / 3f;
        this.gradientEndY = height / 3f;
        recalculateBounds();
    }

    public Typeface getTypeface() {
        int style = Typeface.NORMAL;
        if (isBold && isItalic) style = Typeface.BOLD_ITALIC;
        else if (isBold) style = Typeface.BOLD;
        else if (isItalic) style = Typeface.ITALIC;

        if (fontPath != null && !fontPath.isEmpty()) {
            return FontManager.getTypeface(fontPath, style);
        } else if (isMonospace) {
            return Typeface.create(Typeface.MONOSPACE, style);
        } else {
            return Typeface.create(Typeface.SANS_SERIF, style);
        }
    }

    public void recalculateBounds() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        float effectiveLs = getEffectiveLetterSpacing();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            paint.setLetterSpacing(effectiveLs);
        }
        paint.setTypeface(getTypeface());

        String[] lines = (text != null ? text : "").split("\n");
        float maxLineWidth = 0f;
        for (String line : lines) {
            if (line.isEmpty()) continue;
            maxLineWidth = Math.max(maxLineWidth, paint.measureText(line));
        }

        Paint.FontMetrics fm = paint.getFontMetrics();
        float lineHeight = (fm.bottom - fm.top) * Math.max(0.5f, lineSpacing);
        float totalHeight = Math.max(1, lines.length) * lineHeight;

        this.width = Math.max(60f, maxLineWidth + 32f);
        this.height = Math.max(30f, totalHeight + 16f);
    }

    @Override
    public void setWidth(float newW) {
        if (this.width > 0 && Math.abs(newW - this.width) > 1.5f) {
            float ratio = newW / this.width;
            this.textSize = Math.max(8f, Math.min(400f, this.textSize * ratio));
            recalculateBounds();
        } else {
            super.setWidth(newW);
        }
    }

    @Override
    public void setHeight(float newH) {
        if (this.height > 0 && Math.abs(newH - this.height) > 1.5f) {
            float ratio = newH / this.height;
            this.textSize = Math.max(8f, Math.min(400f, this.textSize * ratio));
            recalculateBounds();
        } else {
            super.setHeight(newH);
        }
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
        return letterSpacing;
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
        paint.setAlpha(opacity);

        float effectiveLetterSpacing = getEffectiveLetterSpacing();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            paint.setLetterSpacing(effectiveLetterSpacing);
        }

        paint.setTypeface(getTypeface());

        if ("LEFT".equalsIgnoreCase(alignment)) {
            paint.setTextAlign(Paint.Align.LEFT);
        } else if ("RIGHT".equalsIgnoreCase(alignment)) {
            paint.setTextAlign(Paint.Align.RIGHT);
        } else {
            paint.setTextAlign(Paint.Align.CENTER);
        }

        // Legacy Shadow (if explicitly enabled)
        if (hasShadow) {
            paint.setShadowLayer(8f, 2f, 4f, 0x88000000);
        }

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

            // 3. Text Randomize effect (Fully matching textrand.xml CDATA script specification)
            } else if (id.contains("textrand") || name.contains("randomize") || fn.contains("textrand")) {
                glab.pixeleditor.effect.EffectParam pAmount = eff.getParam("amount");
                glab.pixeleditor.effect.EffectParam pStart = eff.getParam("start");
                glab.pixeleditor.effect.EffectParam pEnd = eff.getParam("end");
                glab.pixeleditor.effect.EffectParam pCharset = eff.getParam("charset");
                glab.pixeleditor.effect.EffectParam pPreserveSpace = eff.getParam("preserveSpace");

                float amt = pAmount != null ? Math.max(0f, Math.min(1f, pAmount.getFloatValue())) : 0f;
                float s = pStart != null ? Math.max(0f, pStart.getFloatValue()) : 0f;
                float e = pEnd != null ? Math.max(s, Math.min(1f, pEnd.getFloatValue())) : 1f;
                int charsetIdx = pCharset != null ? (int) pCharset.getFloatValue() : 0;
                boolean preserveSpace = pPreserveSpace == null || pPreserveSpace.getBooleanValue();

                if (amt > 0f && !displayText.isEmpty()) {
                    int prefixLen = Math.min(displayText.length(), (int) Math.round(displayText.length() * s));
                    int endIdx = Math.min(displayText.length(), (int) Math.round(displayText.length() * e));
                    String prefix = displayText.substring(0, prefixLen);
                    String middle = displayText.substring(prefixLen, Math.max(prefixLen, endIdx));
                    String suffix = displayText.substring(Math.max(prefixLen, endIdx));

                    String chars;
                    switch (charsetIdx) {
                        case 0: chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"; break;
                        case 1: chars = "abcdefghijklmnopqrstuvwxyz"; break;
                        case 2: chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"; break;
                        case 3: chars = "0123456789"; break;
                        case 4: chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"; break;
                        case 5: chars = displayText; break;
                        case 6: chars = "*"; break;
                        case 7: chars = "•"; break;
                        case 8: chars = "_"; break;
                        case 9: chars = " "; break;
                        case 10: chars = "◊"; break;
                        case 11: chars = "-"; break;
                        case 12: chars = "+=-!@#$%^*()[]{}:;/,.<>~`"; break;
                        case 13: chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+=-!@#$%^*()[]{}:;/,.<>~`"; break;
                        case 14:
                        default:
                            chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzΩµ+=-!@#$%^*()[]{}:;/,.<>~`";
                            break;
                    }

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < middle.length(); i++) {
                        char ch = middle.charAt(i);
                        if (preserveSpace && ch == ' ') {
                            sb.append(' ');
                        } else if (Math.random() < amt && !chars.isEmpty()) {
                            int rIdx = (int) (Math.random() * chars.length());
                            sb.append(chars.charAt(rIdx));
                        } else {
                            sb.append(ch);
                        }
                    }
                    displayText = prefix + sb + suffix;
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

        int appliedAlpha = Math.round(Color.alpha(effectiveTextColor) * (opacity / 255f) * textEffAlpha);

        // Fill Paint based on FillMode
        if (fillMode == ShapeLayer.FillMode.GRADIENT) {
            int startA = Math.round(Color.alpha(gradientStartColor) * (opacity / 255f) * textEffAlpha);
            int endA = Math.round(Color.alpha(gradientEndColor) * (opacity / 255f) * textEffAlpha);
            int sCol = Color.argb(startA, Color.red(gradientStartColor), Color.green(gradientStartColor), Color.blue(gradientStartColor));
            int eCol = Color.argb(endA, Color.red(gradientEndColor), Color.green(gradientEndColor), Color.blue(gradientEndColor));

            if (gradientType == ShapeLayer.GradientType.RADIAL) {
                float radius = (float) Math.hypot(gradientEndX - gradientStartX, gradientEndY - gradientStartY);
                RadialGradient rg = new RadialGradient(
                        gradientStartX, gradientStartY, Math.max(1f, radius),
                        sCol, eCol, Shader.TileMode.CLAMP
                );
                fillPaint.setShader(rg);
            } else if (gradientType == ShapeLayer.GradientType.SWEEP) {
                SweepGradient sg = new SweepGradient(
                        gradientStartX, gradientStartY,
                        new int[]{sCol, eCol, sCol},
                        new float[]{0f, 0.5f, 1f}
                );
                float angle = (float) Math.toDegrees(Math.atan2(gradientEndY - gradientStartY, gradientEndX - gradientStartX));
                Matrix sm = new Matrix();
                sm.postRotate(angle, gradientStartX, gradientStartY);
                sg.setLocalMatrix(sm);
                fillPaint.setShader(sg);
            } else {
                // LINEAR
                LinearGradient lg = new LinearGradient(
                        gradientStartX, gradientStartY, gradientEndX, gradientEndY,
                        new int[]{sCol, eCol},
                        new float[]{Math.min(gradientStartOffset, gradientEndOffset), Math.max(gradientStartOffset, gradientEndOffset)},
                        Shader.TileMode.CLAMP
                );
                fillPaint.setShader(lg);
            }
        } else if (fillMode == ShapeLayer.FillMode.MEDIA && mediaBitmap != null && !mediaBitmap.isRecycled()) {
            BitmapShader bs = new BitmapShader(mediaBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            int bw = mediaBitmap.getWidth();
            int bh = mediaBitmap.getHeight();
            if (bw > 0 && bh > 0) {
                RectF rect = new RectF(-width / 2f, -height / 2f, width / 2f, height / 2f);
                Matrix m = new Matrix();
                if (mediaScaleMode == ShapeLayer.MediaScaleMode.STRETCH) {
                    m.setRectToRect(new RectF(0, 0, bw, bh), rect, Matrix.ScaleToFit.FILL);
                } else if (mediaScaleMode == ShapeLayer.MediaScaleMode.FIT) {
                    m.setRectToRect(new RectF(0, 0, bw, bh), rect, Matrix.ScaleToFit.CENTER);
                } else {
                    // FILL (Center-Crop)
                    float s = Math.max(rect.width() / (float) bw, rect.height() / (float) bh);
                    float dw = bw * s;
                    float dh = bh * s;
                    m.setScale(s, s);
                    m.postTranslate(rect.centerX() - dw / 2f, rect.centerY() - dh / 2f);
                }
                bs.setLocalMatrix(m);
            }
            fillPaint.setShader(bs);
            fillPaint.setAlpha(Math.round(opacity * textEffAlpha));
        } else if (fillMode == ShapeLayer.FillMode.NONE) {
            fillPaint.setColor(Color.TRANSPARENT);
            fillPaint.setShader(null);
        } else {
            // SOLID
            fillPaint.setShader(null);
            fillPaint.setColor(Color.argb(appliedAlpha, Color.red(effectiveTextColor), Color.green(effectiveTextColor), Color.blue(effectiveTextColor)));
        }

        // ColorFilter combining base adjustments + all applied effects
        android.graphics.ColorFilter colorFilter = glab.pixeleditor.effect.EffectPipeline.createCombinedColorFilter(this);
        if (colorFilter != null) {
            fillPaint.setColorFilter(colorFilter);
        }

        // Check if there are active shader / procedural effects applied
        boolean hasActiveEffects = false;
        for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
            if (eff.isEnabled()) {
                hasActiveEffects = true;
                break;
            }
        }

        if (hasActiveEffects) {
            float pad = glab.pixeleditor.effect.EffectPipeline.calculateEffectExpansionPadding(appliedEffects, width, height);
            int bw = Math.max(1, (int) Math.ceil(width + pad * 2f));
            int bh = Math.max(1, (int) Math.ceil(height + pad * 2f));
            Bitmap offBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas offCanvas = new Canvas(offBmp);
            offCanvas.translate(width / 2f + pad, height / 2f + pad);

            if (fillMode != ShapeLayer.FillMode.NONE) {
                renderTextLines(offCanvas, displayText, fillPaint, 0f, 0f);
            }

            RectF layerBounds = new RectF(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f);
            Bitmap renderBitmap = glab.pixeleditor.effect.EffectPipeline.processLayerEffects(this, offBmp, layerBounds, null);
            float outPadX = (renderBitmap.getWidth() - width) / 2f;
            float outPadY = (renderBitmap.getHeight() - height) / 2f;
            RectF dstRect = new RectF(-width / 2f - outPadX, -height / 2f - outPadY, width / 2f + outPadX, height / 2f + outPadY);
            canvas.drawBitmap(renderBitmap, null, dstRect, fillPaint);
        } else {
            // Draw Multiple Configured Shadows
            if (!shadows.isEmpty()) {
                for (ShadowItem s : shadows) {
                    if (!s.isEnabled()) continue;
                    int sAlpha = Math.round(Color.alpha(s.getColor()) * s.getAlpha() * (opacity / 255f));
                    if (sAlpha <= 0) continue;
                    Paint shadowPaint = new Paint(fillPaint);
                    shadowPaint.setColor(Color.argb(sAlpha, Color.red(s.getColor()), Color.green(s.getColor()), Color.blue(s.getColor())));
                    if (s.getSize() > 0.5f) {
                        shadowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(s.getSize(), android.graphics.BlurMaskFilter.Blur.NORMAL));
                    }
                    renderTextLines(canvas, displayText, shadowPaint, s.getOffsetX(), s.getOffsetY());
                }
            }

            // Draw Base Text Fill
            if (fillMode != ShapeLayer.FillMode.NONE) {
                renderTextLines(canvas, displayText, fillPaint, 0f, 0f);
            }

            // Draw Stroke (Legacy or direct)
            if (hasStroke && strokeWidth > 0f) {
                Paint strokePaint = new Paint(paint);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(strokeWidth);
                strokePaint.setColor(strokeColor);
                strokePaint.setAlpha(Math.round(Color.alpha(strokeColor) * (opacity / 255f)));
                renderTextLines(canvas, displayText, strokePaint, 0f, 0f);
            }

            // Draw Multiple Configured Borders
            if (!borders.isEmpty()) {
                for (BorderItem b : borders) {
                    if (!b.isEnabled() || b.getWidth() <= 0f) continue;
                    Paint bp = new Paint(paint);
                    bp.setStyle(Paint.Style.STROKE);
                    bp.setStrokeWidth(b.getWidth());
                    bp.setColor(b.getColor());
                    bp.setAlpha(Math.round(Color.alpha(b.getColor()) * (opacity / 255f)));
                    renderTextLines(canvas, displayText, bp, 0f, 0f);
                }
            }
        }

        canvas.restore();
    }

    private void renderTextLines(Canvas targetCanvas, String content, Paint drawPaint, float offsetX, float offsetY) {
        String[] lines = (content != null ? content : "").split("\n");
        Paint.FontMetrics fm = drawPaint.getFontMetrics();
        float lineHeight = (fm.bottom - fm.top) * Math.max(0.5f, lineSpacing);
        float totalHeight = lines.length * lineHeight;
        float startY = offsetY - totalHeight / 2f - fm.ascent;

        float startX = offsetX;
        if ("LEFT".equalsIgnoreCase(alignment)) {
            startX = offsetX - width / 2f + 16f;
        } else if ("RIGHT".equalsIgnoreCase(alignment)) {
            startX = offsetX + width / 2f - 16f;
        }

        for (int i = 0; i < lines.length; i++) {
            float yPos = startY + i * lineHeight;
            targetCanvas.drawText(lines[i], startX, yPos, drawPaint);
        }
    }

    // Getters and Setters
    public String getText() { return text; }
    public void setText(String text) {
        this.text = text;
        recalculateBounds();
    }

    public int getTextColor() { return textColor; }
    public void setTextColor(int textColor) { this.textColor = textColor; }

    public int getFillColor() { return textColor; }
    public void setFillColor(int color) {
        this.textColor = color;
        this.fillMode = ShapeLayer.FillMode.SOLID;
    }

    public ShapeLayer.FillMode getFillMode() { return fillMode != null ? fillMode : ShapeLayer.FillMode.SOLID; }
    public void setFillMode(ShapeLayer.FillMode fillMode) { this.fillMode = fillMode != null ? fillMode : ShapeLayer.FillMode.SOLID; }

    public ShapeLayer.GradientType getGradientType() { return gradientType != null ? gradientType : ShapeLayer.GradientType.LINEAR; }
    public void setGradientType(ShapeLayer.GradientType gradientType) { this.gradientType = gradientType != null ? gradientType : ShapeLayer.GradientType.LINEAR; }

    public int getGradientStartColor() { return gradientStartColor; }
    public void setGradientStartColor(int gradientStartColor) { this.gradientStartColor = gradientStartColor; }

    public int getGradientEndColor() { return gradientEndColor; }
    public void setGradientEndColor(int gradientEndColor) { this.gradientEndColor = gradientEndColor; }

    public float getGradientStartX() { return gradientStartX; }
    public void setGradientStartX(float gradientStartX) { this.gradientStartX = gradientStartX; }

    public float getGradientStartY() { return gradientStartY; }
    public void setGradientStartY(float gradientStartY) { this.gradientStartY = gradientStartY; }

    public float getGradientEndX() { return gradientEndX; }
    public void setGradientEndX(float gradientEndX) { this.gradientEndX = gradientEndX; }

    public float getGradientEndY() { return gradientEndY; }
    public void setGradientEndY(float gradientEndY) { this.gradientEndY = gradientEndY; }

    public float getGradientStartOffset() { return gradientStartOffset; }
    public void setGradientStartOffset(float gradientStartOffset) { this.gradientStartOffset = gradientStartOffset; }

    public float getGradientEndOffset() { return gradientEndOffset; }
    public void setGradientEndOffset(float gradientEndOffset) { this.gradientEndOffset = gradientEndOffset; }

    public Bitmap getMediaBitmap() { return mediaBitmap; }
    public void setMediaBitmap(Bitmap mediaBitmap) { this.mediaBitmap = mediaBitmap; }

    public String getMediaUri() { return mediaUri; }
    public void setMediaUri(String mediaUri) { this.mediaUri = mediaUri; }

    public String getMediaName() { return mediaName; }
    public void setMediaName(String mediaName) { this.mediaName = mediaName; }

    public ShapeLayer.MediaScaleMode getMediaScaleMode() { return mediaScaleMode != null ? mediaScaleMode : ShapeLayer.MediaScaleMode.FILL; }
    public void setMediaScaleMode(ShapeLayer.MediaScaleMode mediaScaleMode) { this.mediaScaleMode = mediaScaleMode != null ? mediaScaleMode : ShapeLayer.MediaScaleMode.FILL; }

    public float getTextSize() { return textSize; }
    public void setTextSize(float textSize) {
        this.textSize = textSize;
        recalculateBounds();
    }

    public boolean isBold() { return isBold; }
    public void setBold(boolean bold) {
        this.isBold = bold;
        recalculateBounds();
    }

    public boolean isItalic() { return isItalic; }
    public void setItalic(boolean italic) {
        this.isItalic = italic;
        recalculateBounds();
    }

    public boolean isMonospace() { return isMonospace; }
    public void setMonospace(boolean monospace) {
        this.isMonospace = monospace;
        recalculateBounds();
    }

    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }

    public String getFontPath() { return fontPath; }
    public void setFontPath(String fontPath) {
        this.fontPath = fontPath;
        recalculateBounds();
    }

    public String getFontName() { return fontName; }
    public void setFontName(String fontName) { this.fontName = fontName; }

    public float getLetterSpacing() { return letterSpacing; }
    public void setLetterSpacing(float letterSpacing) {
        this.letterSpacing = letterSpacing;
        recalculateBounds();
    }

    public float getLineSpacing() { return lineSpacing; }
    public void setLineSpacing(float lineSpacing) {
        this.lineSpacing = lineSpacing;
        recalculateBounds();
    }

    public int getStrokeColor() { return strokeColor; }
    public void setStrokeColor(int strokeColor) { this.strokeColor = strokeColor; }

    public float getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(float strokeWidth) { this.strokeWidth = strokeWidth; }

    public boolean isHasStroke() { return hasStroke; }
    public void setHasStroke(boolean hasStroke) { this.hasStroke = hasStroke; }

    public boolean isHasShadow() { return hasShadow; }
    public void setHasShadow(boolean hasShadow) { this.hasShadow = hasShadow; }

    @Override
    public JSONObject toJson(Context context) {
        JSONObject json = new JSONObject();
        try {
            json.put("layerType", "TEXT");
            writeBaseJson(json);
            json.put("text", text);
            json.put("textColor", textColor);
            json.put("fillMode", fillMode != null ? fillMode.name() : ShapeLayer.FillMode.SOLID.name());

            if (mediaBitmap != null && !mediaBitmap.isRecycled() && context != null) {
                java.io.File projectsDir = ProjectStorageManager.getProjectsDir(context);
                java.io.File assetsDir = new java.io.File(projectsDir, "assets");
                if (!assetsDir.exists()) assetsDir.mkdirs();
                String fileName = "text_media_" + id + ".png";
                java.io.File photoFile = new java.io.File(assetsDir, fileName);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(photoFile)) {
                    mediaBitmap.compress(Bitmap.CompressFormat.PNG, 95, fos);
                    mediaUri = "assets/" + fileName;
                } catch (Exception ignored) {}
            }
            json.put("mediaUri", mediaUri != null ? mediaUri : "");
            json.put("mediaName", mediaName != null ? mediaName : "Media 1");
            json.put("mediaScaleMode", mediaScaleMode != null ? mediaScaleMode.name() : ShapeLayer.MediaScaleMode.FILL.name());
            json.put("gradientType", gradientType != null ? gradientType.name() : ShapeLayer.GradientType.LINEAR.name());
            json.put("gradientStartColor", gradientStartColor);
            json.put("gradientEndColor", gradientEndColor);
            json.put("gradientStartX", (double) gradientStartX);
            json.put("gradientStartY", (double) gradientStartY);
            json.put("gradientEndX", (double) gradientEndX);
            json.put("gradientEndY", (double) gradientEndY);
            json.put("gradientStartOffset", (double) gradientStartOffset);
            json.put("gradientEndOffset", (double) gradientEndOffset);

            json.put("textSize", textSize);
            json.put("isBold", isBold);
            json.put("isItalic", isItalic);
            json.put("isMonospace", isMonospace);
            json.put("alignment", alignment);
            if (fontPath != null) json.put("fontPath", fontPath);
            if (fontName != null) json.put("fontName", fontName);
            json.put("letterSpacing", letterSpacing);
            json.put("lineSpacing", lineSpacing);
            json.put("strokeColor", strokeColor);
            json.put("strokeWidth", strokeWidth);
            json.put("hasStroke", hasStroke);
            json.put("hasShadow", hasShadow);
        } catch (Exception ignored) {}
        return json;
    }

    public static TextLayer fromJson(Context context, JSONObject json) {
        if (json == null) return null;
        String name = json.optString("name", "Text");
        String text = json.optString("text", "Pixel Editor");
        float x = (float) json.optDouble("x", 200);
        float y = (float) json.optDouble("y", 200);

        TextLayer layer = new TextLayer(name, text, x, y);
        layer.readBaseJson(json);

        layer.textColor = json.optInt("textColor", 0xFFFFFFFF);
        String fmStr = json.optString("fillMode", ShapeLayer.FillMode.SOLID.name());
        try {
            layer.fillMode = ShapeLayer.FillMode.valueOf(fmStr);
        } catch (Exception ignored) {}

        layer.mediaUri = json.optString("mediaUri", null);
        layer.mediaName = json.optString("mediaName", "Media 1");
        String mScale = json.optString("mediaScaleMode", ShapeLayer.MediaScaleMode.FILL.name());
        try {
            layer.mediaScaleMode = ShapeLayer.MediaScaleMode.valueOf(mScale);
        } catch (Exception ignored) {
            layer.mediaScaleMode = ShapeLayer.MediaScaleMode.FILL;
        }

        if (layer.mediaUri != null && !layer.mediaUri.isEmpty() && context != null) {
            try {
                if (layer.mediaUri.startsWith("assets/")) {
                    java.io.File projectsDir = ProjectStorageManager.getProjectsDir(context);
                    java.io.File photoFile = new java.io.File(projectsDir, layer.mediaUri);
                    if (photoFile.exists()) {
                        layer.mediaBitmap = android.graphics.BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                    }
                } else if (layer.mediaUri.startsWith("content://") || layer.mediaUri.startsWith("file://")) {
                    layer.mediaBitmap = android.provider.MediaStore.Images.Media.getBitmap(context.getContentResolver(), android.net.Uri.parse(layer.mediaUri));
                }
            } catch (Exception ignored) {}
        }

        String gtStr = json.optString("gradientType", ShapeLayer.GradientType.LINEAR.name());
        try {
            layer.gradientType = ShapeLayer.GradientType.valueOf(gtStr);
        } catch (Exception ignored) {}

        layer.gradientStartColor = json.optInt("gradientStartColor", 0xFF000000);
        layer.gradientEndColor = json.optInt("gradientEndColor", 0xFFFFFFFF);
        layer.gradientStartX = (float) json.optDouble("gradientStartX", -layer.width / 3f);
        layer.gradientStartY = (float) json.optDouble("gradientStartY", -layer.height / 3f);
        layer.gradientEndX = (float) json.optDouble("gradientEndX", layer.width / 3f);
        layer.gradientEndY = (float) json.optDouble("gradientEndY", layer.height / 3f);
        layer.gradientStartOffset = (float) json.optDouble("gradientStartOffset", 0.0);
        layer.gradientEndOffset = (float) json.optDouble("gradientEndOffset", 1.0);

        layer.textSize = (float) json.optDouble("textSize", 48.0);
        layer.isBold = json.optBoolean("isBold", true);
        layer.isItalic = json.optBoolean("isItalic", false);
        layer.isMonospace = json.optBoolean("isMonospace", false);
        layer.alignment = json.optString("alignment", "CENTER");
        layer.fontPath = json.optString("fontPath", null);
        layer.fontName = json.optString("fontName", null);
        layer.letterSpacing = (float) json.optDouble("letterSpacing", 0.0);
        layer.lineSpacing = (float) json.optDouble("lineSpacing", 1.0);
        layer.strokeColor = json.optInt("strokeColor", 0xFF000000);
        layer.strokeWidth = (float) json.optDouble("strokeWidth", 0.0);
        layer.hasStroke = json.optBoolean("hasStroke", false);
        layer.hasShadow = json.optBoolean("hasShadow", false);

        layer.recalculateBounds();
        return layer;
    }

    @Override
    public CanvasLayer copy() {
        TextLayer copy = new TextLayer(this.name + " Copy", this.text, this.x + 25, this.y + 25);
        copy.textColor = this.textColor;
        copy.fillMode = this.fillMode;
        copy.mediaBitmap = this.mediaBitmap;
        copy.mediaUri = this.mediaUri;
        copy.mediaName = this.mediaName;
        copy.mediaScaleMode = this.mediaScaleMode;
        copy.gradientType = this.gradientType;
        copy.gradientStartColor = this.gradientStartColor;
        copy.gradientEndColor = this.gradientEndColor;
        copy.gradientStartX = this.gradientStartX;
        copy.gradientStartY = this.gradientStartY;
        copy.gradientEndX = this.gradientEndX;
        copy.gradientEndY = this.gradientEndY;
        copy.gradientStartOffset = this.gradientStartOffset;
        copy.gradientEndOffset = this.gradientEndOffset;

        copy.textSize = this.textSize;
        copy.isBold = this.isBold;
        copy.isItalic = this.isItalic;
        copy.isMonospace = this.isMonospace;
        copy.alignment = this.alignment;
        copy.fontPath = this.fontPath;
        copy.fontName = this.fontName;
        copy.letterSpacing = this.letterSpacing;
        copy.lineSpacing = this.lineSpacing;
        copy.strokeColor = this.strokeColor;
        copy.strokeWidth = this.strokeWidth;
        copy.hasStroke = this.hasStroke;
        copy.hasShadow = this.hasShadow;
        copy.width = this.width;
        copy.height = this.height;
        copy.rotation = this.rotation;
        copy.scaleX = this.scaleX;
        copy.scaleY = this.scaleY;
        copy.skewX = this.skewX;
        copy.skewY = this.skewY;
        copy.opacity = this.opacity;
        copy.isVisible = this.isVisible;
        copy.isLocked = this.isLocked;

        for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
            copy.getAppliedEffects().add(eff.copy());
        }
        for (BorderItem b : borders) {
            copy.getBorders().add(b.copy());
        }
        for (ShadowItem s : shadows) {
            copy.getShadows().add(s.copy());
        }
        return copy;
    }

    @Override
    public CanvasLayer cloneLayer() {
        TextLayer clone = new TextLayer(this.name, this.text, this.x, this.y);
        clone.id = this.id;
        clone.textColor = this.textColor;
        clone.fillMode = this.fillMode;
        clone.mediaBitmap = this.mediaBitmap;
        clone.mediaUri = this.mediaUri;
        clone.mediaName = this.mediaName;
        clone.mediaScaleMode = this.mediaScaleMode;
        clone.gradientType = this.gradientType;
        clone.gradientStartColor = this.gradientStartColor;
        clone.gradientEndColor = this.gradientEndColor;
        clone.gradientStartX = this.gradientStartX;
        clone.gradientStartY = this.gradientStartY;
        clone.gradientEndX = this.gradientEndX;
        clone.gradientEndY = this.gradientEndY;
        clone.gradientStartOffset = this.gradientStartOffset;
        clone.gradientEndOffset = this.gradientEndOffset;

        clone.textSize = this.textSize;
        clone.isBold = this.isBold;
        clone.isItalic = this.isItalic;
        clone.isMonospace = this.isMonospace;
        clone.alignment = this.alignment;
        clone.fontPath = this.fontPath;
        clone.fontName = this.fontName;
        clone.letterSpacing = this.letterSpacing;
        clone.lineSpacing = this.lineSpacing;
        clone.strokeColor = this.strokeColor;
        clone.strokeWidth = this.strokeWidth;
        clone.hasStroke = this.hasStroke;
        clone.hasShadow = this.hasShadow;
        clone.width = this.width;
        clone.height = this.height;
        clone.rotation = this.rotation;
        clone.scaleX = this.scaleX;
        clone.scaleY = this.scaleY;
        clone.skewX = this.skewX;
        clone.skewY = this.skewY;
        clone.opacity = this.opacity;
        clone.isVisible = this.isVisible;
        clone.isLocked = this.isLocked;

        for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
            clone.getAppliedEffects().add(eff.copy());
        }
        for (BorderItem b : borders) {
            clone.getBorders().add(b.copy());
        }
        for (ShadowItem s : shadows) {
            clone.getShadows().add(s.copy());
        }
        return clone;
    }
}
