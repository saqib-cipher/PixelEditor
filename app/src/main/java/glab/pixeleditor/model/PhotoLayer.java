package glab.pixeleditor.model;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

public class PhotoLayer extends CanvasLayer {

    private Bitmap bitmap;
    private float brightness = 0f; // -100 to 100
    private float contrast = 0f;   // -100 to 100
    private float saturation = 0f; // -100 to 100
    private float warmth = 0f;     // -100 to 100
    private float vignette = 0f;   // 0 to 100
    private String filterPreset = "NORMAL";

    // Color & Gradient Overlay Fill
    private ShapeLayer.FillMode fillMode = ShapeLayer.FillMode.MEDIA;
    private int fillColor = 0xFF7A4B58;
    private ShapeLayer.GradientType gradientType = ShapeLayer.GradientType.LINEAR;
    private int gradientStartColor = 0xFF000000;
    private int gradientEndColor = 0xFFFFFFFF;
    private float gradientStartX = -100f;
    private float gradientStartY = -100f;
    private float gradientEndX = 100f;
    private float gradientEndY = 100f;
    private float gradientStartOffset = 0.0f;
    private float gradientEndOffset = 1.0f;

    public PhotoLayer(String name, Bitmap bitmap, float x, float y, float width, float height) {
        super(name, x, y, width, height);
        this.bitmap = bitmap;
        this.gradientStartX = -width / 3f;
        this.gradientStartY = -height / 3f;
        this.gradientEndX = width / 3f;
        this.gradientEndY = height / 3f;
    }

    @Override
    public void draw(Canvas canvas, Paint basePaint) {
        if (!isVisible || bitmap == null || bitmap.isRecycled()) return;

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
        RectF destRect = new RectF(left, top, right, bottom);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setAlpha(opacity);

        // ColorFilter combining base adjustments + all applied effects
        ColorFilter filter = glab.pixeleditor.effect.EffectPipeline.createCombinedColorFilter(this);
        if (filter != null) {
            paint.setColorFilter(filter);
        }

        // Draw Shadows
        if (!shadows.isEmpty()) {
            for (ShadowItem s : shadows) {
                if (!s.isEnabled()) continue;
                int sAlpha = Math.round(Color.alpha(s.getColor()) * s.getAlpha() * (opacity / 255f));
                if (sAlpha <= 0) continue;
                Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadowPaint.setStyle(Paint.Style.FILL);
                shadowPaint.setColor(Color.argb(sAlpha, Color.red(s.getColor()), Color.green(s.getColor()), Color.blue(s.getColor())));
                if (s.getSize() > 0.5f) {
                    shadowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(s.getSize(), android.graphics.BlurMaskFilter.Blur.NORMAL));
                }
                RectF shRect = new RectF(destRect.left + s.getOffsetX(), destRect.top + s.getOffsetY(), destRect.right + s.getOffsetX(), destRect.bottom + s.getOffsetY());
                canvas.drawRect(shRect, shadowPaint);
            }
        }

        // Apply Mask, Trim, Blur
        glab.pixeleditor.effect.EffectPipeline.applyMaskAndStyling(canvas, this, destRect, paint, null);

        // Check if Tiles effect is active
        android.graphics.BitmapShader tileShader = glab.pixeleditor.effect.EffectPipeline.createTileShader(this, bitmap, destRect);
        if (tileShader != null) {
            paint.setShader(tileShader);
            canvas.drawRect(destRect, paint);
            paint.setShader(null);
        } else {
            boolean hasActiveEffects = false;
            for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
                if (eff.isEnabled()) {
                    hasActiveEffects = true;
                    break;
                }
            }

            if (hasActiveEffects) {
                float pad = glab.pixeleditor.effect.EffectPipeline.calculateEffectExpansionPadding(appliedEffects, width, height);
                int bw = Math.max(1, (int) Math.ceil(bitmap.getWidth() + pad * 2f));
                int bh = Math.max(1, (int) Math.ceil(bitmap.getHeight() + pad * 2f));
                Bitmap paddedBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                Canvas offCanvas = new Canvas(paddedBmp);
                offCanvas.drawBitmap(bitmap, pad, pad, paint);

                RectF layerBounds = new RectF(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f);
                Bitmap renderBitmap = glab.pixeleditor.effect.EffectPipeline.processLayerEffects(this, paddedBmp, layerBounds, null);
                float outPadX = (renderBitmap.getWidth() - bitmap.getWidth()) / 2f * (width / (float) bitmap.getWidth());
                float outPadY = (renderBitmap.getHeight() - bitmap.getHeight()) / 2f * (height / (float) bitmap.getHeight());
                RectF dstRect = new RectF(-width / 2f - outPadX, -height / 2f - outPadY, width / 2f + outPadX, height / 2f + outPadY);
                canvas.drawBitmap(renderBitmap, null, dstRect, paint);
            } else {
                android.graphics.Rect srcRect = new android.graphics.Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                canvas.drawBitmap(bitmap, srcRect, destRect, paint);
            }
        }

        // Draw Multiple Configured Borders
        if (!borders.isEmpty()) {
            for (BorderItem b : borders) {
                if (!b.isEnabled() || b.getWidth() <= 0f) continue;
                Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
                bp.setStyle(Paint.Style.STROKE);
                bp.setStrokeWidth(b.getWidth());
                bp.setColor(b.getColor());
                bp.setAlpha(Math.round(Color.alpha(b.getColor()) * (opacity / 255f)));
                canvas.drawRect(destRect, bp);
            }
        }

        // Apply Gradient or Solid Color Overlay if active
        if (fillMode == ShapeLayer.FillMode.GRADIENT) {
            Paint gradPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            gradPaint.setAlpha(opacity);
            gradPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP));

            int startA = Math.round(android.graphics.Color.alpha(gradientStartColor) * (opacity / 255f));
            int endA = Math.round(android.graphics.Color.alpha(gradientEndColor) * (opacity / 255f));
            int sCol = android.graphics.Color.argb(startA, android.graphics.Color.red(gradientStartColor), android.graphics.Color.green(gradientStartColor), android.graphics.Color.blue(gradientStartColor));
            int eCol = android.graphics.Color.argb(endA, android.graphics.Color.red(gradientEndColor), android.graphics.Color.green(gradientEndColor), android.graphics.Color.blue(gradientEndColor));

            if (gradientType == ShapeLayer.GradientType.RADIAL) {
                float radius = (float) Math.hypot(gradientEndX - gradientStartX, gradientEndY - gradientStartY);
                android.graphics.RadialGradient rg = new android.graphics.RadialGradient(
                        gradientStartX, gradientStartY, Math.max(1f, radius),
                        sCol, eCol, android.graphics.Shader.TileMode.CLAMP
                );
                gradPaint.setShader(rg);
            } else if (gradientType == ShapeLayer.GradientType.SWEEP) {
                android.graphics.SweepGradient sg = new android.graphics.SweepGradient(
                        gradientStartX, gradientStartY,
                        new int[]{sCol, eCol, sCol},
                        new float[]{0f, 0.5f, 1f}
                );
                float angle = (float) Math.toDegrees(Math.atan2(gradientEndY - gradientStartY, gradientEndX - gradientStartX));
                android.graphics.Matrix sm = new android.graphics.Matrix();
                sm.postRotate(angle, gradientStartX, gradientStartY);
                sg.setLocalMatrix(sm);
                gradPaint.setShader(sg);
            } else {
                // LINEAR
                android.graphics.LinearGradient lg = new android.graphics.LinearGradient(
                        gradientStartX, gradientStartY, gradientEndX, gradientEndY,
                        new int[]{sCol, eCol},
                        new float[]{Math.min(gradientStartOffset, gradientEndOffset), Math.max(gradientStartOffset, gradientEndOffset)},
                        android.graphics.Shader.TileMode.CLAMP
                );
                gradPaint.setShader(lg);
            }
            canvas.drawRect(destRect, gradPaint);
        } else if (fillMode == ShapeLayer.FillMode.SOLID) {
            int fillAlpha = Math.round(android.graphics.Color.alpha(fillColor) * (opacity / 255f));
            if (fillAlpha > 0) {
                Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                solidPaint.setColor(fillColor);
                solidPaint.setAlpha(fillAlpha);
                solidPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP));
                canvas.drawRect(destRect, solidPaint);
            }
        }

        // Post-draw vignette & overlays
        glab.pixeleditor.effect.EffectPipeline.applyPostDraw(canvas, this, destRect);

        canvas.restore();
    }

    private ColorFilter createCombinedFilter() {
        ColorMatrix matrix = new ColorMatrix();

        // 1. Filter Preset
        if ("BW".equalsIgnoreCase(filterPreset)) {
            ColorMatrix bw = new ColorMatrix();
            bw.setSaturation(0f);
            matrix.postConcat(bw);
        } else if ("CYBERPUNK".equalsIgnoreCase(filterPreset)) {
            ColorMatrix cp = new ColorMatrix(new float[]{
                    1.4f, 0f, 0.4f, 0f, 20f,
                    0f, 1.1f, 0.6f, 0f, 0f,
                    0.2f, 0.4f, 1.8f, 0f, 40f,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(cp);
        } else if ("VINTAGE".equalsIgnoreCase(filterPreset)) {
            ColorMatrix sepia = new ColorMatrix(new float[]{
                    0.393f, 0.769f, 0.189f, 0f, 30f,
                    0.349f, 0.686f, 0.168f, 0f, 15f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(sepia);
        } else if ("CINEMATIC".equalsIgnoreCase(filterPreset)) {
            ColorMatrix cine = new ColorMatrix(new float[]{
                    1.2f, 0f, 0f, 0f, -10f,
                    0f, 1.05f, 0.2f, 0f, 0f,
                    0.1f, 0.1f, 1.3f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(cine);
        }

        // 2. Brightness (-100 to 100)
        if (brightness != 0) {
            ColorMatrix bMat = new ColorMatrix();
            float b = brightness * 1.5f;
            bMat.set(new float[]{
                    1f, 0f, 0f, 0f, b,
                    0f, 1f, 0f, 0f, b,
                    0f, 0f, 1f, 0f, b,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(bMat);
        }

        // 3. Contrast (-100 to 100)
        if (contrast != 0) {
            float c = (contrast + 100f) / 100f;
            float t = (1f - c) / 2f * 255f;
            ColorMatrix cMat = new ColorMatrix(new float[]{
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(cMat);
        }

        // 4. Saturation (-100 to 100)
        if (saturation != 0) {
            float s = (saturation + 100f) / 100f;
            ColorMatrix sMat = new ColorMatrix();
            sMat.setSaturation(Math.max(0f, s));
            matrix.postConcat(sMat);
        }

        // 5. Warmth (-100 to 100)
        if (warmth != 0) {
            float w = warmth * 0.8f;
            ColorMatrix wMat = new ColorMatrix(new float[]{
                    1f, 0f, 0f, 0f, w,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, -w,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(wMat);
        }

        return new ColorMatrixColorFilter(matrix);
    }

    @Override
    public CanvasLayer copy() {
        PhotoLayer copy = new PhotoLayer(this.name + " Copy", bitmap, x + 30, y + 30, width, height);
        copy.setRotation(rotation);
        copy.setScaleX(scaleX);
        copy.setScaleY(scaleY);
        copy.setSkewX(skewX);
        copy.setSkewY(skewY);
        copy.setOpacity(opacity);
        copy.setBrightness(brightness);
        copy.setContrast(contrast);
        copy.setSaturation(saturation);
        copy.setWarmth(warmth);
        copy.setVignette(vignette);
        copy.setFilterPreset(filterPreset);
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
        copy.setMaskType(maskType);
        copy.setBlendMode(blendMode);
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
        PhotoLayer clone = new PhotoLayer(this.name, bitmap, x, y, width, height);
        clone.setId(this.id);
        clone.setRotation(rotation);
        clone.setScaleX(scaleX);
        clone.setScaleY(scaleY);
        clone.setSkewX(skewX);
        clone.setSkewY(skewY);
        clone.setOpacity(opacity);
        clone.setBrightness(brightness);
        clone.setContrast(contrast);
        clone.setSaturation(saturation);
        clone.setWarmth(warmth);
        clone.setVignette(vignette);
        clone.setFilterPreset(filterPreset);
        clone.setFillMode(fillMode);
        clone.setFillColor(fillColor);
        clone.setGradientType(gradientType);
        clone.setGradientStartColor(gradientStartColor);
        clone.setGradientEndColor(gradientEndColor);
        clone.setGradientStartX(gradientStartX);
        clone.setGradientStartY(gradientStartY);
        clone.setGradientEndX(gradientEndX);
        clone.setGradientEndY(gradientEndY);
        clone.setGradientStartOffset(gradientStartOffset);
        clone.setGradientEndOffset(gradientEndOffset);
        clone.setVisible(isVisible);
        clone.setLocked(isLocked);
        clone.setMaskType(maskType);
        clone.setBlendMode(blendMode);
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
    public Bitmap getBitmap() { return bitmap; }
    public void setBitmap(Bitmap bitmap) { this.bitmap = bitmap; }
    public float getBrightness() { return brightness; }
    public void setBrightness(float brightness) { this.brightness = brightness; }
    public float getContrast() { return contrast; }
    public void setContrast(float contrast) { this.contrast = contrast; }
    public float getSaturation() { return saturation; }
    public void setSaturation(float saturation) { this.saturation = saturation; }
    public float getWarmth() { return warmth; }
    public void setWarmth(float warmth) { this.warmth = warmth; }
    public float getVignette() { return vignette; }
    public void setVignette(float vignette) { this.vignette = vignette; }
    public String getFilterPreset() { return filterPreset; }
    public void setFilterPreset(String filterPreset) { this.filterPreset = filterPreset; }

    public ShapeLayer.FillMode getFillMode() { return fillMode != null ? fillMode : ShapeLayer.FillMode.MEDIA; }
    public void setFillMode(ShapeLayer.FillMode fillMode) { this.fillMode = fillMode != null ? fillMode : ShapeLayer.FillMode.MEDIA; }
    public int getFillColor() { return fillColor; }
    public void setFillColor(int fillColor) { this.fillColor = fillColor; }

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

    private String photoFileName = "";
    private float originalWidth = 0f;
    private float originalHeight = 0f;

    public float getOriginalWidth() {
        return originalWidth > 0 ? originalWidth : (bitmap != null ? bitmap.getWidth() : width);
    }
    public void setOriginalWidth(float ow) { this.originalWidth = ow; }

    public float getOriginalHeight() {
        return originalHeight > 0 ? originalHeight : (bitmap != null ? bitmap.getHeight() : height);
    }
    public void setOriginalHeight(float oh) { this.originalHeight = oh; }

    @Override
    public org.json.JSONObject toJson(android.content.Context context) {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("layerType", "PHOTO");
            writeBaseJson(json);
            json.put("brightness", brightness);
            json.put("contrast", contrast);
            json.put("saturation", saturation);
            json.put("warmth", warmth);
            json.put("vignette", vignette);
            json.put("filterPreset", filterPreset);
            json.put("fillMode", fillMode != null ? fillMode.name() : ShapeLayer.FillMode.MEDIA.name());
            json.put("fillColor", fillColor);
            json.put("gradientType", gradientType != null ? gradientType.name() : ShapeLayer.GradientType.LINEAR.name());
            json.put("gradientStartColor", gradientStartColor);
            json.put("gradientEndColor", gradientEndColor);
            json.put("gradientStartX", (double) gradientStartX);
            json.put("gradientStartY", (double) gradientStartY);
            json.put("gradientEndX", (double) gradientEndX);
            json.put("gradientEndY", (double) gradientEndY);
            json.put("gradientStartOffset", (double) gradientStartOffset);
            json.put("gradientEndOffset", (double) gradientEndOffset);
            json.put("originalWidth", getOriginalWidth());
            json.put("originalHeight", getOriginalHeight());

            // Persist bitmap to projects dir/assets
            if (bitmap != null && !bitmap.isRecycled()) {
                java.io.File projectsDir = ProjectStorageManager.getProjectsDir(context);
                java.io.File assetsDir = new java.io.File(projectsDir, "assets");
                if (!assetsDir.exists()) assetsDir.mkdirs();
                String fileName = "photo_" + id + ".png";
                java.io.File photoFile = new java.io.File(assetsDir, fileName);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(photoFile)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos);
                    photoFileName = "assets/" + fileName;
                } catch (Exception ignored) {}
            }
            json.put("photoFileName", photoFileName);
        } catch (Exception ignored) {}
        return json;
    }

    public static PhotoLayer fromJson(android.content.Context context, org.json.JSONObject json) {
        if (json == null) return null;
        String name = json.optString("name", "Photo");
        float x = (float) json.optDouble("x", 200);
        float y = (float) json.optDouble("y", 200);
        float w = (float) json.optDouble("width", 300);
        float h = (float) json.optDouble("height", 300);

        String photoPath = json.optString("photoFileName", "");
        Bitmap loadedBmp = null;
        if (!photoPath.isEmpty()) {
            java.io.File projectsDir = ProjectStorageManager.getProjectsDir(context);
            java.io.File photoFile = new java.io.File(projectsDir, photoPath);
            if (photoFile.exists()) {
                try {
                    loadedBmp = android.graphics.BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                } catch (Exception ignored) {}
            }
        }

        if (loadedBmp == null) {
            loadedBmp = Bitmap.createBitmap(Math.max(50, Math.round(w)), Math.max(50, Math.round(h)), Bitmap.Config.ARGB_8888);
            loadedBmp.eraseColor(0xFF334155);
        }

        PhotoLayer layer = new PhotoLayer(name, loadedBmp, x, y, w, h);
        layer.readBaseJson(json);

        layer.brightness = (float) json.optDouble("brightness", 0.0);
        layer.contrast = (float) json.optDouble("contrast", 0.0);
        layer.saturation = (float) json.optDouble("saturation", 0.0);
        layer.warmth = (float) json.optDouble("warmth", 0.0);
        layer.vignette = (float) json.optDouble("vignette", 0.0);
        layer.filterPreset = json.optString("filterPreset", "NORMAL");

        String fmStr = json.optString("fillMode", ShapeLayer.FillMode.MEDIA.name());
        try {
            layer.fillMode = ShapeLayer.FillMode.valueOf(fmStr);
        } catch (Exception ignored) {}

        layer.fillColor = json.optInt("fillColor", 0xFF7A4B58);

        String gtStr = json.optString("gradientType", ShapeLayer.GradientType.LINEAR.name());
        try {
            layer.gradientType = ShapeLayer.GradientType.valueOf(gtStr);
        } catch (Exception ignored) {}

        layer.gradientStartColor = json.optInt("gradientStartColor", 0xFF000000);
        layer.gradientEndColor = json.optInt("gradientEndColor", 0xFFFFFFFF);
        layer.gradientStartX = (float) json.optDouble("gradientStartX", -w / 3f);
        layer.gradientStartY = (float) json.optDouble("gradientStartY", -h / 3f);
        layer.gradientEndX = (float) json.optDouble("gradientEndX", w / 3f);
        layer.gradientEndY = (float) json.optDouble("gradientEndY", h / 3f);
        layer.gradientStartOffset = (float) json.optDouble("gradientStartOffset", 0.0);
        layer.gradientEndOffset = (float) json.optDouble("gradientEndOffset", 1.0);

        layer.photoFileName = photoPath;
        layer.originalWidth = (float) json.optDouble("originalWidth", loadedBmp.getWidth());
        layer.originalHeight = (float) json.optDouble("originalHeight", loadedBmp.getHeight());

        return layer;
    }
}
