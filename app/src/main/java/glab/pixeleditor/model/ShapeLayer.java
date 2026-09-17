package glab.pixeleditor.model;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

import glab.pixeleditor.view.GradientBarView;
import java.util.ArrayList;
import java.util.List;

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

    public enum FillMode {
        NONE,
        SOLID,
        GRADIENT,
        MEDIA
    }

    public enum MediaScaleMode {
        FILL,
        FIT,
        STRETCH
    }

    public enum GradientType {
        LINEAR,
        RADIAL,
        SWEEP
    }

    private ShapeType shapeType = ShapeType.ROUNDED_RECT;
    private FillMode fillMode = FillMode.SOLID;
    private int fillColor = 0xFF7A4B58; // muted mauve/burgundy as in screenshot

    // Media Fill properties
    private Bitmap mediaBitmap;
    private String mediaUri;
    private String mediaName = "Media 1";
    private MediaScaleMode mediaScaleMode = MediaScaleMode.FILL;

    // Gradient properties
    private GradientType gradientType = GradientType.LINEAR;
    private int gradientStartColor = 0xFF000000;
    private int gradientEndColor = 0xFFFFFFFF;
    private float gradientStartX = -100f;
    private float gradientStartY = -100f;
    private float gradientEndX = 100f;
    private float gradientEndY = 100f;
    private float gradientStartOffset = 0.0f;
    private float gradientEndOffset = 1.0f;

    private int strokeColor = 0xFF00E5BC;
    private float strokeWidth = 0f;
    private boolean hasStroke = false;
    private float cornerRadius = 25f;
    private BorderItem.Alignment strokeAlignment = BorderItem.Alignment.CENTER;
    private Paint.Cap strokeCap = Paint.Cap.ROUND;
    private Paint.Join strokeJoin = Paint.Join.ROUND;

    // XML-driven Shape Definition with parameters & CDATA script
    private glab.pixeleditor.shape.ShapeDefinition shapeDefinition;

    public ShapeLayer(String name, float x, float y, float width, float height) {
        super(name, x, y, width, height);
        this.gradientStartX = -width / 3f;
        this.gradientStartY = -height / 3f;
        this.gradientEndX = width / 3f;
        this.gradientEndY = height / 3f;
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

        // Calculate fill paint based on FillMode
        Paint fillPaint = null;
        if (fillMode == FillMode.NONE) {
            fillPaint = null;
        } else if (fillMode == FillMode.GRADIENT) {
            fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setAlpha(opacity);

            int[] rawCols = getGradientColorsArray();
            float[] offsets = getGradientOffsetsArray();
            int[] cols = new int[rawCols.length];
            for (int i = 0; i < rawCols.length; i++) {
                int a = Math.round(Color.alpha(rawCols[i]) * (opacity / 255f));
                cols[i] = Color.argb(a, Color.red(rawCols[i]), Color.green(rawCols[i]), Color.blue(rawCols[i]));
            }

            if (gradientType == GradientType.RADIAL) {
                float radius = (float) Math.hypot(gradientEndX - gradientStartX, gradientEndY - gradientStartY);
                RadialGradient rg = new RadialGradient(
                        gradientStartX, gradientStartY, Math.max(1f, radius),
                        cols, offsets, Shader.TileMode.CLAMP
                );
                fillPaint.setShader(rg);
            } else if (gradientType == GradientType.SWEEP) {
                SweepGradient sg = new SweepGradient(
                        gradientStartX, gradientStartY,
                        cols, offsets
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
                        cols, offsets,
                        Shader.TileMode.CLAMP
                );
                fillPaint.setShader(lg);
            }
        } else {
            // SOLID
            int fillAlpha = Math.round(Color.alpha(fillColor) * (opacity / 255f));
            if (fillAlpha > 0) {
                fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(fillColor);
                fillPaint.setAlpha(fillAlpha);
            }
        }

        // Primary Stroke Paint
        int strokeAlpha = Math.round(Color.alpha(strokeColor) * (opacity / 255f));
        Paint strokePaint = null;
        if (hasStroke && strokeWidth > 0 && strokeAlpha > 0) {
            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(strokeColor);
            strokePaint.setStrokeWidth(strokeWidth);
            strokePaint.setStrokeCap(strokeCap != null ? strokeCap : Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(strokeJoin != null ? strokeJoin : Paint.Join.ROUND);
            strokePaint.setAlpha(strokeAlpha);
        }

        // Apply ColorMatrix / Filters from active effects
        android.graphics.ColorFilter filter = glab.pixeleditor.effect.EffectPipeline.createCombinedColorFilter(this);
        if (filter != null) {
            if (fillPaint != null) fillPaint.setColorFilter(filter);
            if (strokePaint != null) strokePaint.setColorFilter(filter);
        }

        // Apply Masks, Blurs, Trims, Shadow, and Gradient Overlays
        glab.pixeleditor.effect.EffectPipeline.applyMaskAndStyling(canvas, this, rect, fillPaint, strokePaint);

        boolean hasActiveEffects = false;
        for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
            if (eff.isEnabled()) {
                hasActiveEffects = true;
                break;
            }
        }

        if (hasActiveEffects && width > 0 && height > 0) {
            float pad = glab.pixeleditor.effect.EffectPipeline.calculateEffectExpansionPadding(appliedEffects, width, height);
            int bw = Math.max(1, (int) Math.ceil(width + pad * 2f));
            int bh = Math.max(1, (int) Math.ceil(height + pad * 2f));
            Bitmap shapeBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas offCanvas = new Canvas(shapeBmp);
            RectF offRect = new RectF(pad - width / 2f, pad - height / 2f, pad + width / 2f, pad + height / 2f);
            offCanvas.translate(bw / 2f, bh / 2f);
            RectF localRect = new RectF(-width / 2f, -height / 2f, width / 2f, height / 2f);
            drawShapeGeometry(offCanvas, localRect, fillPaint, strokePaint);

            RectF layerBounds = new RectF(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f);
            Bitmap processed = glab.pixeleditor.effect.EffectPipeline.processLayerEffects(this, shapeBmp, layerBounds, null);
            float outPadX = (processed.getWidth() - width) / 2f;
            float outPadY = (processed.getHeight() - height) / 2f;
            RectF dstRect = new RectF(-width / 2f - outPadX, -height / 2f - outPadY, width / 2f + outPadX, height / 2f + outPadY);
            canvas.drawBitmap(processed, null, dstRect, null);
        } else {
            drawShapeGeometry(canvas, rect, fillPaint, strokePaint);
        }

        // Apply post-draw effects (e.g. Vignette)
        glab.pixeleditor.effect.EffectPipeline.applyPostDraw(canvas, this, rect);

        canvas.restore();
    }

    private void drawShapeGeometry(Canvas canvas, RectF rect, Paint fillPaint, Paint strokePaint) {
        Path path = glab.pixeleditor.shape.ShapeGeometryHelper.buildPath(shapeDefinition, rect, shapeType);
        if (path == null) return;

        // 1. Draw Shadows
        for (ShadowItem s : shadows) {
            if (!s.isEnabled()) continue;
            int sAlpha = Math.round(Color.alpha(s.getColor()) * s.getAlpha() * (opacity / 255f));
            if (sAlpha <= 0) continue;

            int shadowCol = Color.argb(sAlpha, Color.red(s.getColor()), Color.green(s.getColor()), Color.blue(s.getColor()));
            Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setStyle(Paint.Style.FILL);
            shadowPaint.setColor(shadowCol);
            if (s.getSize() > 0.5f) {
                shadowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(s.getSize(), android.graphics.BlurMaskFilter.Blur.NORMAL));
            }

            canvas.save();
            canvas.translate(s.getOffsetX(), s.getOffsetY());
            canvas.drawPath(path, shadowPaint);
            canvas.restore();
        }

        // 2. Draw Fill (Solid / Gradient / Media)
        if (fillMode == FillMode.MEDIA && mediaBitmap != null && !mediaBitmap.isRecycled()) {
            canvas.save();
            canvas.clipPath(path);
            int bw = mediaBitmap.getWidth();
            int bh = mediaBitmap.getHeight();
            if (bw > 0 && bh > 0) {
                Paint mediaPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                mediaPaint.setAlpha(opacity);
                if (fillPaint != null && fillPaint.getColorFilter() != null) {
                    mediaPaint.setColorFilter(fillPaint.getColorFilter());
                }

                if (mediaScaleMode == MediaScaleMode.STRETCH) {
                    canvas.drawBitmap(mediaBitmap, new android.graphics.Rect(0, 0, bw, bh), rect, mediaPaint);
                } else if (mediaScaleMode == MediaScaleMode.FIT) {
                    float s = Math.min(rect.width() / (float) bw, rect.height() / (float) bh);
                    float dw = bw * s;
                    float dh = bh * s;
                    RectF dst = new RectF(rect.centerX() - dw / 2f, rect.centerY() - dh / 2f, rect.centerX() + dw / 2f, rect.centerY() + dh / 2f);
                    canvas.drawBitmap(mediaBitmap, new android.graphics.Rect(0, 0, bw, bh), dst, mediaPaint);
                } else {
                    // FILL (Center-Crop)
                    float s = Math.max(rect.width() / (float) bw, rect.height() / (float) bh);
                    float dw = bw * s;
                    float dh = bh * s;
                    RectF dst = new RectF(rect.centerX() - dw / 2f, rect.centerY() - dh / 2f, rect.centerX() + dw / 2f, rect.centerY() + dh / 2f);
                    canvas.drawBitmap(mediaBitmap, new android.graphics.Rect(0, 0, bw, bh), dst, mediaPaint);
                }
            }
            canvas.restore();
        } else if (fillPaint != null) {
            canvas.drawPath(path, fillPaint);
        }

        // 3. Draw Primary Stroke (if enabled)
        if (strokePaint != null && hasStroke && strokeWidth > 0) {
            BorderItem.Alignment align = strokeAlignment != null ? strokeAlignment : BorderItem.Alignment.CENTER;
            if (align == BorderItem.Alignment.INSIDE) {
                canvas.save();
                canvas.clipPath(path);
                strokePaint.setStrokeWidth(strokeWidth * 2f);
                canvas.drawPath(path, strokePaint);
                strokePaint.setStrokeWidth(strokeWidth);
                canvas.restore();
            } else if (align == BorderItem.Alignment.OUTSIDE) {
                canvas.save();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    canvas.clipOutPath(path);
                } else {
                    canvas.clipPath(path, android.graphics.Region.Op.DIFFERENCE);
                }
                strokePaint.setStrokeWidth(strokeWidth * 2f);
                canvas.drawPath(path, strokePaint);
                strokePaint.setStrokeWidth(strokeWidth);
                canvas.restore();
            } else {
                // CENTER
                canvas.drawPath(path, strokePaint);
            }
        }

        // 4. Draw Multiple Configured Borders
        if (!borders.isEmpty()) {
            for (BorderItem b : borders) {
                if (!b.isEnabled() || b.getWidth() <= 0f) continue;
                int bAlpha = Math.round(Color.alpha(b.getColor()) * (opacity / 255f));
                if (bAlpha <= 0) continue;

                Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
                bp.setStyle(Paint.Style.STROKE);
                bp.setColor(b.getColor());
                bp.setAlpha(bAlpha);
                bp.setStrokeCap(b.getCap());
                bp.setStrokeJoin(b.getJoin());

                BorderItem.Alignment align = b.getAlignment();
                if (align == BorderItem.Alignment.INSIDE) {
                    canvas.save();
                    canvas.clipPath(path);
                    bp.setStrokeWidth(b.getWidth() * 2f);
                    canvas.drawPath(path, bp);
                    canvas.restore();
                } else if (align == BorderItem.Alignment.OUTSIDE) {
                    canvas.save();
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        canvas.clipOutPath(path);
                    } else {
                        canvas.clipPath(path, android.graphics.Region.Op.DIFFERENCE);
                    }
                    bp.setStrokeWidth(b.getWidth() * 2f);
                    canvas.drawPath(path, bp);
                    canvas.restore();
                } else {
                    // CENTER
                    bp.setStrokeWidth(b.getWidth());
                    canvas.drawPath(path, bp);
                }
            }
        }
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
        copy.setFillMode(fillMode);
        copy.setFillColor(fillColor);
        copy.setGradientType(gradientType);
        copy.setGradientStartColor(gradientStartColor);
        copy.setGradientEndColor(gradientEndColor);
        copy.setGradientStartX(gradientStartX);
        copy.setGradientStartY(gradientStartY);
        copy.setGradientEndX(gradientEndX);
        copy.setGradientEndY(gradientEndY);
        copy.setGradientStartOffset(gradientStartOffset);
        copy.setGradientEndOffset(gradientEndOffset);
        copy.setStrokeColor(strokeColor);
        copy.setStrokeWidth(strokeWidth);
        copy.setHasStroke(hasStroke);
        copy.setStrokeAlignment(strokeAlignment);
        copy.setStrokeCap(strokeCap);
        copy.setStrokeJoin(strokeJoin);
        copy.setCornerRadius(cornerRadius);
        copy.setMediaBitmap(mediaBitmap);
        copy.setMediaUri(mediaUri);
        copy.setMediaName(mediaName);
        copy.setMediaScaleMode(mediaScaleMode);
        if (this.shapeDefinition != null) {
            copy.shapeDefinition = this.shapeDefinition.copy();
        }
        copy.setGradientStops(this.gradientStops);
        for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
            copy.addEffect(eff.copy());
        }
        for (BorderItem b : borders) {
            copy.addBorder(b.copy());
        }
        for (ShadowItem s : shadows) {
            copy.addShadow(s.copy());
        }
        return copy;
    }

    @Override
    public CanvasLayer cloneLayer() {
        ShapeLayer clone = new ShapeLayer(this.name, x, y, width, height);
        clone.setId(this.id);
        clone.setRotation(rotation);
        clone.setScaleX(scaleX);
        clone.setScaleY(scaleY);
        clone.setSkewX(skewX);
        clone.setSkewY(skewY);
        clone.setOpacity(opacity);
        clone.setShapeType(shapeType);
        clone.setFillMode(fillMode);
        clone.setFillColor(fillColor);
        clone.setMediaBitmap(mediaBitmap);
        clone.setMediaUri(mediaUri);
        clone.setMediaName(mediaName);
        clone.setMediaScaleMode(mediaScaleMode);
        clone.setGradientType(gradientType);
        clone.setGradientStartColor(gradientStartColor);
        clone.setGradientEndColor(gradientEndColor);
        clone.setGradientStartX(gradientStartX);
        clone.setGradientStartY(gradientStartY);
        clone.setGradientEndX(gradientEndX);
        clone.setGradientEndY(gradientEndY);
        clone.setGradientStartOffset(gradientStartOffset);
        clone.setGradientEndOffset(gradientEndOffset);
        clone.setGradientStops(this.gradientStops);
        clone.setStrokeColor(strokeColor);
        clone.setStrokeWidth(strokeWidth);
        clone.setHasStroke(hasStroke);
        clone.setStrokeAlignment(strokeAlignment);
        clone.setStrokeCap(strokeCap);
        clone.setStrokeJoin(strokeJoin);
        clone.setCornerRadius(cornerRadius);
        clone.setVisible(isVisible);
        clone.setLocked(isLocked);
        clone.setMaskType(maskType);
        clone.setBlendMode(blendMode);
        if (this.shapeDefinition != null) {
            clone.shapeDefinition = this.shapeDefinition.copy();
        }
        for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
            clone.addEffect(eff.copy());
        }
        for (BorderItem b : borders) {
            clone.addBorder(b.cloneItem());
        }
        for (ShadowItem s : shadows) {
            clone.addShadow(s.cloneItem());
        }
        return clone;
    }

    // Getters and Setters
    public ShapeType getShapeType() { return shapeType; }
    public void setShapeType(ShapeType shapeType) { this.shapeType = shapeType; }
    public FillMode getFillMode() { return fillMode != null ? fillMode : FillMode.SOLID; }
    public void setFillMode(FillMode fillMode) { this.fillMode = fillMode != null ? fillMode : FillMode.SOLID; }
    public int getFillColor() { return fillColor; }
    public void setFillColor(int fillColor) { this.fillColor = fillColor; }

    public Bitmap getMediaBitmap() { return mediaBitmap; }
    public void setMediaBitmap(Bitmap mediaBitmap) { this.mediaBitmap = mediaBitmap; }
    public String getMediaUri() { return mediaUri; }
    public void setMediaUri(String mediaUri) { this.mediaUri = mediaUri; }
    public String getMediaName() { return mediaName != null ? mediaName : "Media 1"; }
    public void setMediaName(String mediaName) { this.mediaName = mediaName; }
    public MediaScaleMode getMediaScaleMode() { return mediaScaleMode != null ? mediaScaleMode : MediaScaleMode.FILL; }
    public void setMediaScaleMode(MediaScaleMode mediaScaleMode) { this.mediaScaleMode = mediaScaleMode != null ? mediaScaleMode : MediaScaleMode.FILL; }

    public GradientType getGradientType() { return gradientType != null ? gradientType : GradientType.LINEAR; }
    public void setGradientType(GradientType gradientType) { this.gradientType = gradientType != null ? gradientType : GradientType.LINEAR; }
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

    private final List<GradientBarView.GradientStop> gradientStops = new ArrayList<>();

    public List<GradientBarView.GradientStop> getGradientStops() {
        return gradientStops;
    }

    public void setGradientStops(List<GradientBarView.GradientStop> stops) {
        this.gradientStops.clear();
        if (stops != null) {
            for (GradientBarView.GradientStop s : stops) {
                this.gradientStops.add(s.copy());
            }
        }
        if (!this.gradientStops.isEmpty()) {
            this.gradientStartColor = this.gradientStops.get(0).color;
            this.gradientStartOffset = this.gradientStops.get(0).offset;
            this.gradientEndColor = this.gradientStops.get(this.gradientStops.size() - 1).color;
            this.gradientEndOffset = this.gradientStops.get(this.gradientStops.size() - 1).offset;
        }
    }

    public int[] getGradientColorsArray() {
        if (gradientStops != null && gradientStops.size() >= 2) {
            int[] arr = new int[gradientStops.size()];
            for (int i = 0; i < gradientStops.size(); i++) {
                arr[i] = gradientStops.get(i).color;
            }
            return arr;
        }
        return new int[]{gradientStartColor, gradientEndColor};
    }

    public float[] getGradientOffsetsArray() {
        if (gradientStops != null && gradientStops.size() >= 2) {
            float[] arr = new float[gradientStops.size()];
            for (int i = 0; i < gradientStops.size(); i++) {
                arr[i] = gradientStops.get(i).offset;
            }
            return arr;
        }
        return new float[]{Math.min(gradientStartOffset, gradientEndOffset), Math.max(gradientStartOffset, gradientEndOffset)};
    }

    public int getStrokeColor() { return strokeColor; }
    public void setStrokeColor(int strokeColor) { this.strokeColor = strokeColor; }
    public float getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(float strokeWidth) { this.strokeWidth = Math.max(0f, strokeWidth); }
    public boolean isHasStroke() { return hasStroke; }
    public void setHasStroke(boolean hasStroke) { this.hasStroke = hasStroke; }
    public BorderItem.Alignment getStrokeAlignment() { return strokeAlignment != null ? strokeAlignment : BorderItem.Alignment.CENTER; }
    public void setStrokeAlignment(BorderItem.Alignment strokeAlignment) { this.strokeAlignment = strokeAlignment != null ? strokeAlignment : BorderItem.Alignment.CENTER; }
    public Paint.Cap getStrokeCap() { return strokeCap != null ? strokeCap : Paint.Cap.ROUND; }
    public void setStrokeCap(Paint.Cap strokeCap) { this.strokeCap = strokeCap; }
    public Paint.Join getStrokeJoin() { return strokeJoin != null ? strokeJoin : Paint.Join.ROUND; }
    public void setStrokeJoin(Paint.Join strokeJoin) { this.strokeJoin = strokeJoin; }
    public float getCornerRadius() { return cornerRadius; }
    public void setCornerRadius(float cornerRadius) { this.cornerRadius = Math.max(0f, cornerRadius); }

    public glab.pixeleditor.shape.ShapeDefinition getShapeDefinition() {
        return shapeDefinition;
    }

    public void setShapeDefinition(glab.pixeleditor.shape.ShapeDefinition shapeDefinition) {
        this.shapeDefinition = shapeDefinition;
    }

    public glab.pixeleditor.shape.ShapeDefinition ensureShapeDefinition(android.content.Context context) {
        if (shapeDefinition == null && context != null) {
            String lookup = "roundrect";
            if (shapeType != null) {
                switch (shapeType) {
                    case STAR: lookup = "star"; break;
                    case CIRCLE: lookup = "circle"; break;
                    case TRIANGLE: lookup = "triangle"; break;
                    case DROP: lookup = "teardrop"; break;
                    case ROUNDED_RECT:
                    default: lookup = "roundrect"; break;
                }
            }
            shapeDefinition = glab.pixeleditor.shape.ShapeHelper.getShapeById(context, lookup);
        }
        return shapeDefinition;
    }

    @Override
    public org.json.JSONObject toJson(android.content.Context context) {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("layerType", "SHAPE");
            writeBaseJson(json);
            json.put("shapeType", shapeType != null ? shapeType.name() : ShapeType.ROUNDED_RECT.name());
            json.put("fillMode", fillMode != null ? fillMode.name() : FillMode.SOLID.name());
            json.put("fillColor", fillColor);
            if (mediaBitmap != null && !mediaBitmap.isRecycled()) {
                java.io.File projectsDir = ProjectStorageManager.getProjectsDir(context);
                java.io.File assetsDir = new java.io.File(projectsDir, "assets");
                if (!assetsDir.exists()) assetsDir.mkdirs();
                String fileName = "shape_media_" + id + ".png";
                java.io.File photoFile = new java.io.File(assetsDir, fileName);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(photoFile)) {
                    mediaBitmap.compress(Bitmap.CompressFormat.PNG, 95, fos);
                    mediaUri = "assets/" + fileName;
                } catch (Exception ignored) {}
            }
            json.put("mediaUri", mediaUri != null ? mediaUri : "");
            json.put("mediaName", mediaName != null ? mediaName : "Media 1");
            json.put("mediaScaleMode", mediaScaleMode != null ? mediaScaleMode.name() : MediaScaleMode.FILL.name());
            json.put("gradientType", gradientType != null ? gradientType.name() : GradientType.LINEAR.name());
            json.put("gradientStartColor", gradientStartColor);
            json.put("gradientEndColor", gradientEndColor);
            json.put("gradientStartX", (double) gradientStartX);
            json.put("gradientStartY", (double) gradientStartY);
            json.put("gradientEndX", (double) gradientEndX);
            json.put("gradientEndY", (double) gradientEndY);
            json.put("gradientStartOffset", (double) gradientStartOffset);
            json.put("gradientEndOffset", (double) gradientEndOffset);

            if (!gradientStops.isEmpty()) {
                org.json.JSONArray stopsArr = new org.json.JSONArray();
                for (GradientBarView.GradientStop s : gradientStops) {
                    org.json.JSONObject sObj = new org.json.JSONObject();
                    sObj.put("color", s.color);
                    sObj.put("offset", (double) s.offset);
                    stopsArr.put(sObj);
                }
                json.put("gradientStops", stopsArr);
            }

            json.put("strokeColor", strokeColor);
            json.put("strokeWidth", strokeWidth);
            json.put("hasStroke", hasStroke);
            json.put("strokeAlignment", strokeAlignment != null ? strokeAlignment.name() : BorderItem.Alignment.CENTER.name());
            json.put("strokeCap", strokeCap != null ? strokeCap.name() : Paint.Cap.ROUND.name());
            json.put("strokeJoin", strokeJoin != null ? strokeJoin.name() : Paint.Join.ROUND.name());
            json.put("cornerRadius", cornerRadius);

            if (shapeDefinition != null) {
                json.put("shapeDefinition", shapeDefinition.toJson());
            }
        } catch (Exception ignored) {}
        return json;
    }

    public static ShapeLayer fromJson(android.content.Context context, org.json.JSONObject json) {
        if (json == null) return null;
        String name = json.optString("name", "Shape");
        float x = (float) json.optDouble("x", 100);
        float y = (float) json.optDouble("y", 100);
        float w = (float) json.optDouble("width", 200);
        float h = (float) json.optDouble("height", 200);

        ShapeLayer layer = new ShapeLayer(name, x, y, w, h);
        layer.readBaseJson(json);

        String stStr = json.optString("shapeType", ShapeType.ROUNDED_RECT.name());
        try {
            layer.shapeType = ShapeType.valueOf(stStr);
        } catch (Exception ignored) {}

        String fmStr = json.optString("fillMode", FillMode.SOLID.name());
        try {
            layer.fillMode = FillMode.valueOf(fmStr);
        } catch (Exception ignored) {}

        layer.fillColor = json.optInt("fillColor", 0xFF7A4B58);
        layer.mediaUri = json.optString("mediaUri", null);
        layer.mediaName = json.optString("mediaName", "Media 1");
        String mScale = json.optString("mediaScaleMode", MediaScaleMode.FILL.name());
        try {
            layer.mediaScaleMode = MediaScaleMode.valueOf(mScale);
        } catch (Exception ignored) {
            layer.mediaScaleMode = MediaScaleMode.FILL;
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

        String gtStr = json.optString("gradientType", GradientType.LINEAR.name());
        try {
            layer.gradientType = GradientType.valueOf(gtStr);
        } catch (Exception ignored) {}

        layer.gradientStartColor = json.optInt("gradientStartColor", 0xFF000000);
        layer.gradientEndColor = json.optInt("gradientEndColor", 0xFFFFFFFF);
        layer.gradientStartX = (float) json.optDouble("gradientStartX", -w / 3f);
        layer.gradientStartY = (float) json.optDouble("gradientStartY", -h / 3f);
        layer.gradientEndX = (float) json.optDouble("gradientEndX", w / 3f);
        layer.gradientEndY = (float) json.optDouble("gradientEndY", h / 3f);
        layer.gradientStartOffset = (float) json.optDouble("gradientStartOffset", 0.0);
        layer.gradientEndOffset = (float) json.optDouble("gradientEndOffset", 1.0);

        org.json.JSONArray stopsArr = json.optJSONArray("gradientStops");
        if (stopsArr != null && stopsArr.length() > 0) {
            List<GradientBarView.GradientStop> loadedStops = new ArrayList<>();
            for (int i = 0; i < stopsArr.length(); i++) {
                org.json.JSONObject sObj = stopsArr.optJSONObject(i);
                if (sObj != null) {
                    loadedStops.add(new GradientBarView.GradientStop(
                            sObj.optInt("color", 0xFF000000),
                            (float) sObj.optDouble("offset", 0.0)
                    ));
                }
            }
            layer.setGradientStops(loadedStops);
        }

        layer.strokeColor = json.optInt("strokeColor", 0xFF00E5BC);
        layer.strokeWidth = (float) json.optDouble("strokeWidth", 0.0);
        layer.hasStroke = json.optBoolean("hasStroke", false);
        try {
            layer.strokeAlignment = BorderItem.Alignment.valueOf(json.optString("strokeAlignment", "CENTER"));
        } catch (Exception e) {
            layer.strokeAlignment = BorderItem.Alignment.CENTER;
        }
        try {
            layer.strokeCap = Paint.Cap.valueOf(json.optString("strokeCap", "ROUND"));
        } catch (Exception e) {
            layer.strokeCap = Paint.Cap.ROUND;
        }
        try {
            layer.strokeJoin = Paint.Join.valueOf(json.optString("strokeJoin", "ROUND"));
        } catch (Exception e) {
            layer.strokeJoin = Paint.Join.ROUND;
        }
        layer.cornerRadius = (float) json.optDouble("cornerRadius", 25.0);

        org.json.JSONObject sDef = json.optJSONObject("shapeDefinition");
        if (sDef != null) {
            layer.shapeDefinition = glab.pixeleditor.shape.ShapeDefinition.fromJson(sDef);
        }

        return layer;
    }
}

