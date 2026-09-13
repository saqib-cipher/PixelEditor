package glab.pixeleditor.model;

import android.graphics.Bitmap;
import android.graphics.Canvas;
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

    public PhotoLayer(String name, Bitmap bitmap, float x, float y, float width, float height) {
        super(name, x, y, width, height);
        this.bitmap = bitmap;
    }

    @Override
    public void draw(Canvas canvas, Paint basePaint) {
        if (!isVisible || bitmap == null || bitmap.isRecycled()) return;

        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        canvas.scale(scaleX, scaleY);

        // Apply Effect Geometric Transforms (e.g. Stretch Axis, Flip)
        glab.pixeleditor.effect.EffectPipeline.applyEffectTransforms(canvas, this);

        float left = -width / 2f;
        float top = -height / 2f;
        float right = width / 2f;
        float bottom = height / 2f;
        RectF destRect = new RectF(left, top, right, bottom);
        Rect srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setAlpha(opacity);

        // ColorFilter combining base adjustments + all applied effects
        ColorFilter filter = glab.pixeleditor.effect.EffectPipeline.createCombinedColorFilter(this);
        if (filter != null) {
            paint.setColorFilter(filter);
        }

        // Apply Mask, Trim, Blur
        glab.pixeleditor.effect.EffectPipeline.applyMaskAndStyling(canvas, this, destRect, paint, null);

        canvas.drawBitmap(bitmap, srcRect, destRect, paint);

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
        copy.setOpacity(opacity);
        copy.setBrightness(brightness);
        copy.setContrast(contrast);
        copy.setSaturation(saturation);
        copy.setWarmth(warmth);
        copy.setVignette(vignette);
        copy.setFilterPreset(filterPreset);
        for (glab.pixeleditor.effect.EffectDefinition eff : appliedEffects) {
            copy.addEffect(eff.copy());
        }
        return copy;
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
}
