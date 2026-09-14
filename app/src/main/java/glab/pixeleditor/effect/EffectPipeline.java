package glab.pixeleditor.effect;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;

import java.util.List;

import glab.pixeleditor.model.CanvasLayer;
import glab.pixeleditor.model.PhotoLayer;
import glab.pixeleditor.model.ShapeLayer;

public class EffectPipeline {

    /**
     * Applies geometric transforms introduced by effects (e.g. Stretch Axis, Flip, Offset, 3D Camera)
     */
    public static void applyEffectTransforms(Canvas canvas, CanvasLayer layer) {
        for (EffectDefinition eff : layer.getAppliedEffects()) {
            if (!eff.isEnabled()) continue;
            String id = eff.getId().toLowerCase();
            String name = eff.getName().toLowerCase();
            String cat = eff.getCategory() != null ? eff.getCategory().toLowerCase() : "";

            // 1. Stretch / Stretch Axis
            if (id.contains("stretch") || name.contains("stretch")) {
                EffectParam scaleParam = eff.getParam("scale");
                if (scaleParam == null) scaleParam = eff.getParam("strength");
                EffectParam angleParam = eff.getParam("angle");

                float s = scaleParam != null ? scaleParam.getFloatValue() : 1.0f;
                float ang = angleParam != null ? angleParam.getFloatValue() : 0.0f;

                if (s != 1.0f || ang != 0.0f) {
                    canvas.rotate(ang);
                    canvas.scale(Math.max(0.01f, s), 1.0f);
                    canvas.rotate(-ang);
                }
            }

            // 2. Flip (3D Axis Flip as defined in flip.xml, flip2.xml)
            if (id.contains("flip") || name.contains("flip")) {
                EffectParam angleParam = eff.getParam("angle");
                EffectParam axisParam = eff.getParam("axis");
                EffectParam zdistParam = eff.getParam("zdist");

                float angle = angleParam != null ? angleParam.getFloatValue() : 0f;
                float axis = axisParam != null ? axisParam.getFloatValue() : 0f;
                float zdist = zdistParam != null ? zdistParam.getFloatValue() : 0f;

                // 2D Boolean flips
                EffectParam flipH = eff.getParam("horizontal");
                EffectParam flipV = eff.getParam("vertical");
                if (flipH != null && flipH.getBooleanValue()) canvas.scale(-1f, 1f);
                if (flipV != null && flipV.getBooleanValue()) canvas.scale(1f, -1f);

                // 3D Axis Flip: Rotates layer plane around custom axis vector
                if (angle != 0f) {
                    canvas.rotate(-axis);
                    Camera camera = new Camera();
                    camera.save();
                    camera.setLocation(0, 0, -45f - zdist * 8f);
                    camera.rotateY(angle);
                    camera.applyToCanvas(canvas);
                    camera.restore();
                    canvas.rotate(axis);
                }
            }

            // 3. Offset
            if (id.contains("offset") || name.contains("offset")) {
                EffectParam xParam = eff.getParam("x");
                if (xParam == null) xParam = eff.getParam("offset_x");
                EffectParam yParam = eff.getParam("y");
                if (yParam == null) yParam = eff.getParam("offset_y");
                if (xParam != null || yParam != null) {
                    float ox = xParam != null ? xParam.getFloatValue() : 0f;
                    float oy = yParam != null ? yParam.getFloatValue() : 0f;
                    canvas.translate(ox, oy);
                }
            }

            // 4. Cube 3D (cube.xml, cube2.xml) - Isometric Perspective Raymarch Space
            String fn = eff.getFileName() != null ? eff.getFileName().toLowerCase() : "";
            if (id.contains("cube") || fn.contains("cube")) {
                EffectParam rxParam = eff.getParam("orient_x");
                EffectParam ryParam = eff.getParam("orient_y");
                EffectParam rzParam = eff.getParam("orient_z");
                EffectParam depthParam = eff.getParam("depth");
                EffectParam zdistParam = eff.getParam("zdist");

                float rx = rxParam != null ? rxParam.getFloatValue() : 25f;
                float ry = ryParam != null ? ryParam.getFloatValue() : -35f;
                float rz = rzParam != null ? rzParam.getFloatValue() : 0f;
                float zdist = zdistParam != null ? zdistParam.getFloatValue() : 0f;
                float depth = depthParam != null ? depthParam.getFloatValue() : 1.0f;

                Camera camera = new Camera();
                camera.save();
                camera.setLocation(0, 0, -42f - zdist * 10f);
                camera.rotateX(rx);
                camera.rotateY(ry);
                camera.rotateZ(rz);
                camera.applyToCanvas(canvas);
                camera.restore();

                if (depth > 0.01f && depth != 1.0f) {
                    canvas.scale(1.0f, 1.0f * depth);
                }
            }
            // 5. Shape 3D & Extrusions (s3d-box, s3d-extrude, s3d-cylinder, s3d-ring, etc.)
            else if (id.startsWith("s3d") || fn.startsWith("s3d") || cat.contains("3d")) {
                EffectParam rxParam = eff.getParam("rotate_x");
                if (rxParam == null) rxParam = eff.getParam("orient_x");
                EffectParam ryParam = eff.getParam("rotate_y");
                if (ryParam == null) ryParam = eff.getParam("orient_y");
                EffectParam rzParam = eff.getParam("rotate_z");
                if (rzParam == null) rzParam = eff.getParam("orient_z");

                float rx = rxParam != null ? rxParam.getFloatValue() : 20f;
                float ry = ryParam != null ? ryParam.getFloatValue() : -25f;
                float rz = rzParam != null ? rzParam.getFloatValue() : 0f;

                EffectParam scaleParam = eff.getParam("scale");
                float sc = scaleParam != null ? scaleParam.getFloatValue() : 1.0f;

                Camera camera = new Camera();
                camera.save();
                camera.setLocation(0, 0, -50f);
                camera.rotateX(rx);
                camera.rotateY(ry);
                camera.rotateZ(rz);
                camera.applyToCanvas(canvas);
                camera.restore();

                if (sc != 1.0f && sc > 0f) {
                    canvas.scale(sc, sc);
                }
            }

            // 6. Bend (bend.xml)
            if (id.contains("bend") || fn.contains("bend")) {
                EffectParam angleParam = eff.getParam("angle");
                EffectParam axisParam = eff.getParam("axis");
                float ang = angleParam != null ? angleParam.getFloatValue() : 30f;
                float axis = axisParam != null ? axisParam.getFloatValue() : 0f;
                if (ang != 0f) {
                    canvas.rotate(-axis);
                    float skew = (float) Math.tan(Math.toRadians(ang * 0.5f));
                    canvas.skew(0f, skew * 0.2f);
                    canvas.rotate(axis);
                }
            }

            // 7. Spherize / 360 Reorient (spherize.xml, 360-reorient-sphere.xml)
            if (id.contains("spherize") || id.contains("sphere") || fn.contains("sphere")) {
                EffectParam strParam = eff.getParam("strength");
                float str = strParam != null ? strParam.getFloatValue() : 0.5f;
                if (str != 0f) {
                    float s = 1.0f + str * 0.35f;
                    canvas.scale(s, s);
                }
            }
        }
    }

    /**
     * Constructs a combined ColorMatrixColorFilter from all active color effects on this layer.
     */
    public static ColorFilter createCombinedColorFilter(CanvasLayer layer) {
        ColorMatrix matrix = new ColorMatrix();
        boolean hasAdjustment = false;

        float totalBrightness = 0f;
        float totalContrast = 0f;
        float totalSaturation = 0f;
        float totalHue = 0f;
        float totalWarmth = 0f;
        boolean isInverted = false;
        int colorTint = 0;
        float tintAmount = 0f;

        // Factor in PhotoLayer base parameters if applicable
        if (layer instanceof PhotoLayer) {
            PhotoLayer photo = (PhotoLayer) layer;
            totalBrightness += photo.getBrightness();
            totalContrast += photo.getContrast();
            totalSaturation += photo.getSaturation();
            totalWarmth += photo.getWarmth();

            // Preset
            String preset = photo.getFilterPreset();
            if ("BW".equalsIgnoreCase(preset)) {
                totalSaturation -= 100f;
            } else if ("VINTAGE".equalsIgnoreCase(preset)) {
                ColorMatrix sepia = new ColorMatrix(new float[]{
                        0.393f, 0.769f, 0.189f, 0f, 30f,
                        0.349f, 0.686f, 0.168f, 0f, 15f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                });
                matrix.postConcat(sepia);
                hasAdjustment = true;
            } else if ("CYBERPUNK".equalsIgnoreCase(preset)) {
                ColorMatrix cp = new ColorMatrix(new float[]{
                        1.4f, 0f, 0.4f, 0f, 20f,
                        0f, 1.1f, 0.6f, 0f, 0f,
                        0.2f, 0.4f, 1.8f, 0f, 40f,
                        0f, 0f, 0f, 1f, 0f
                });
                matrix.postConcat(cp);
                hasAdjustment = true;
            } else if ("CINEMATIC".equalsIgnoreCase(preset)) {
                ColorMatrix cine = new ColorMatrix(new float[]{
                        1.2f, 0f, 0f, 0f, -10f,
                        0f, 1.05f, 0.2f, 0f, 0f,
                        0.1f, 0.1f, 1.3f, 0f, 25f,
                        0f, 0f, 0f, 1f, 0f
                });
                matrix.postConcat(cine);
                hasAdjustment = true;
            }
        }

        if (totalBrightness != 0f) {
            ColorMatrix bMat = new ColorMatrix();
            bMat.set(new float[]{
                    1, 0, 0, 0, totalBrightness * 2.55f,
                    0, 1, 0, 0, totalBrightness * 2.55f,
                    0, 0, 1, 0, totalBrightness * 2.55f,
                    0, 0, 0, 1, 0
            });
            matrix.postConcat(bMat);
            hasAdjustment = true;
        }

        if (totalContrast != 0f) {
            float scale = (totalContrast + 100f) / 100f;
            float translate = (-0.5f * scale + 0.5f) * 255f;
            ColorMatrix cMat = new ColorMatrix(new float[]{
                    scale, 0, 0, 0, translate,
                    0, scale, 0, 0, translate,
                    0, 0, scale, 0, translate,
                    0, 0, 0, 1, 0
            });
            matrix.postConcat(cMat);
            hasAdjustment = true;
        }

        if (totalSaturation != 0f) {
            ColorMatrix sMat = new ColorMatrix();
            sMat.setSaturation((totalSaturation + 100f) / 100f);
            matrix.postConcat(sMat);
            hasAdjustment = true;
        }

        if (totalWarmth != 0f) {
            float w = totalWarmth / 100f;
            ColorMatrix wMat = new ColorMatrix(new float[]{
                    1f + w * 0.15f, 0, 0, 0, 0,
                    0, 1f, 0, 0, 0,
                    0, 0, 1f - w * 0.15f, 0, 0,
                    0, 0, 0, 1, 0
            });
            matrix.postConcat(wMat);
            hasAdjustment = true;
        }

        return hasAdjustment ? new ColorMatrixColorFilter(matrix) : null;
    }

    /**
     * Applies styling, shadow, blur, and path trims to Paint / Shape geometries.
     */
    public static void applyMaskAndStyling(Canvas canvas, CanvasLayer layer, RectF bounds, Paint fillPaint, Paint strokePaint) {
        for (EffectDefinition eff : layer.getAppliedEffects()) {
            if (!eff.isEnabled()) continue;
            String id = eff.getId().toLowerCase();
            String name = eff.getName().toLowerCase();

            // 1. Drawing Progress / Trim
            if (id.contains("progress") || name.contains("progress") || id.contains("trim")) {
                EffectParam startParam = eff.getParam("start");
                EffectParam endParam = eff.getParam("end");
                float startPct = startParam != null ? Math.max(0f, startParam.getFloatValue() / 100f) : 0f;
                float endPct = endParam != null ? Math.min(1f, endParam.getFloatValue() / 100f) : 1f;

                if (startPct > 0f || endPct < 1f) {
                    float left = bounds.left + bounds.width() * startPct;
                    float right = bounds.left + bounds.width() * endPct;
                    canvas.clipRect(left, bounds.top, right, bounds.bottom);
                }
            }


            // 3. Shadow / Glow / Drop Shadow
            if (id.contains("shadow") || name.contains("shadow") || id.contains("glow") || name.contains("glow")) {
                EffectParam distParam = eff.getParam("distance");
                EffectParam angleParam = eff.getParam("angle");
                EffectParam radParam = eff.getParam("radius");
                EffectParam colorParam = eff.getParam("color");

                float dist = distParam != null ? distParam.getFloatValue() * 20f : 10f;
                float ang = angleParam != null ? (float) Math.toRadians(angleParam.getFloatValue()) : 0.78f;
                float radius = radParam != null ? radParam.getFloatValue() * 15f : 10f;
                int col = colorParam != null ? colorParam.getColorValue() : 0x88000000;

                float dx = (float) (Math.cos(ang) * dist);
                float dy = (float) (Math.sin(ang) * dist);

                if (fillPaint != null) {
                    fillPaint.setShadowLayer(Math.max(1f, radius), dx, dy, col);
                }
            }

            // 4. Gradient Overlay / Four Color Gradient
            if (id.contains("gradient") || name.contains("gradient")) {
                EffectParam c1 = eff.getParam("color1");
                if (c1 == null) c1 = eff.getParam("startColor");
                EffectParam c2 = eff.getParam("color2");
                if (c2 == null) c2 = eff.getParam("endColor");

                int colorStart = c1 != null ? c1.getColorValue() : 0xFF00E5BC;
                int colorEnd = c2 != null ? c2.getColorValue() : 0xFF7A4B58;

                LinearGradient grad = new LinearGradient(
                        bounds.left, bounds.top, bounds.right, bounds.bottom,
                        colorStart, colorEnd, Shader.TileMode.CLAMP
                );
                if (fillPaint != null) {
                    fillPaint.setShader(grad);
                }
            }

            // 5. Outline / Stroke
            if (id.contains("outline") || id.contains("border") || name.contains("outline") || name.contains("border")) {
                EffectParam widthParam = eff.getParam("width");
                if (widthParam == null) widthParam = eff.getParam("strokeWidth");
                EffectParam colorParam = eff.getParam("color");

                float w = widthParam != null ? widthParam.getFloatValue() * 20f : 8f;
                int col = colorParam != null ? colorParam.getColorValue() : 0xFF00E5BC;

                if (strokePaint != null) {
                    strokePaint.setStrokeWidth(w);
                    strokePaint.setColor(col);
                    strokePaint.setAlpha(layer.getOpacity());
                }
            }
        }
    }

    /**
     * Post-draw effects such as Vignette overlays
     */
    public static void applyPostDraw(Canvas canvas, CanvasLayer layer, RectF bounds) {
        float vignetteIntensity = 0f;

        // PhotoLayer base vignette
        if (layer instanceof PhotoLayer) {
            vignetteIntensity = Math.max(vignetteIntensity, ((PhotoLayer) layer).getVignette());
        }

        // Effect vignette
        for (EffectDefinition eff : layer.getAppliedEffects()) {
            if (!eff.isEnabled()) continue;
            String id = eff.getId().toLowerCase();
            String name = eff.getName().toLowerCase();
            if (id.contains("vignette") || name.contains("vignette")) {
                EffectParam param = eff.getParam("intensity");
                if (param == null) param = eff.getParam("strength");
                if (param == null) param = eff.getParam("radius");
                if (param != null) {
                    vignetteIntensity = Math.max(vignetteIntensity, param.getFloatValue() * 100f);
                }
            }
        }

        if (vignetteIntensity > 0) {
            Paint vigPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            float radius = Math.max(bounds.width(), bounds.height()) * 0.7f;
            int vigAlpha = (int) (Math.min(100f, vignetteIntensity) * 2.2f);
            int darkEdge = (vigAlpha << 24) | 0x000000;
            RadialGradient gradient = new RadialGradient(
                    bounds.centerX(), bounds.centerY(), radius,
                    new int[]{0x00000000, darkEdge},
                    new float[]{0.5f, 1.0f},
                    Shader.TileMode.CLAMP
            );
            vigPaint.setShader(gradient);
            canvas.drawRect(bounds, vigPaint);
        }
    }

    // Bitmap processing cache (keyed by layer id + effect hash)
    private static final LruCache<String, Bitmap> sProcessedBitmapCache = new LruCache<>(10);

    /**
     * Checks if the layer has an active Tiles effect (tile.xml).
     */
    public static boolean hasTileEffect(CanvasLayer layer) {
        for (EffectDefinition eff : layer.getAppliedEffects()) {
            if (!eff.isEnabled()) continue;
            String id = eff.getId().toLowerCase();
            String fn = eff.getFileName() != null ? eff.getFileName().toLowerCase() : "";
            if (id.contains("tile") || fn.contains("tile")) return true;
        }
        return false;
    }

    /**
     * Creates a BitmapShader configured with mirroring and tiling from tile.xml.
     */
    public static BitmapShader createTileShader(CanvasLayer layer, Bitmap source, RectF destRect) {
        EffectDefinition tileEff = null;
        for (EffectDefinition eff : layer.getAppliedEffects()) {
            if (!eff.isEnabled()) continue;
            String id = eff.getId().toLowerCase();
            String fn = eff.getFileName() != null ? eff.getFileName().toLowerCase() : "";
            if (id.contains("tile") || fn.contains("tile")) {
                tileEff = eff;
                break;
            }
        }
        if (tileEff == null || source == null) return null;

        EffectParam mirrorParam = tileEff.getParam("mirror");
        EffectParam scaleParam = tileEff.getParam("scale");
        EffectParam phaseParam = tileEff.getParam("phase");

        boolean isMirror = mirrorParam != null && mirrorParam.getBooleanValue();
        float scale = scaleParam != null ? Math.max(0.1f, scaleParam.getFloatValue()) : 1.0f;
        float phase = phaseParam != null ? phaseParam.getFloatValue() : 0f;

        Shader.TileMode mode = isMirror ? Shader.TileMode.MIRROR : Shader.TileMode.REPEAT;
        BitmapShader shader = new BitmapShader(source, mode, mode);

        Matrix m = new Matrix();
        float sx = (destRect.width() / (float) source.getWidth()) / scale;
        float sy = (destRect.height() / (float) source.getHeight()) / scale;
        m.postScale(sx, sy);
        m.postTranslate(destRect.left + phase * destRect.width(), destRect.top);
        shader.setLocalMatrix(m);

        return shader;
    }

    /**
     * Executes the layer's enabled Alight Motion effects sequentially using the GLSL CDATA shader engine.
     */
    public static Bitmap processLayerEffects(CanvasLayer layer, Bitmap source, RectF layerBounds, RectF canvasBounds) {
        if (layer == null || source == null || source.isRecycled()) return source;

        List<EffectDefinition> effects = layer.getAppliedEffects();
        if (effects == null || effects.isEmpty()) {
            return source;
        }

        // Build deterministic cache key representing the layer + enabled effects + params
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(layer.getId()).append("_").append(source.getWidth()).append("x").append(source.getHeight()).append("_");
        boolean hasAnyActive = false;

        for (EffectDefinition eff : effects) {
            if (!eff.isEnabled()) continue;
            hasAnyActive = true;
            keyBuilder.append(eff.getId()).append(":");
            for (EffectParam p : eff.getParams()) {
                keyBuilder.append(p.getId()).append("=").append(p.getFormattedValue()).append(",");
            }
            keyBuilder.append(";");
        }

        if (!hasAnyActive) {
            return source;
        }

        String cacheKey = keyBuilder.toString();
        Bitmap cached = sProcessedBitmapCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        Bitmap current = source;
        for (EffectDefinition eff : effects) {
            if (!eff.isEnabled()) continue;
            String id = eff.getId().toLowerCase();
            String fn = eff.getFileName() != null ? eff.getFileName().toLowerCase() : "";

            // Handle repeat/pattern effects that have no GLSL shader — rendered CPU-side
            if (id.contains("repeat") || fn.startsWith("repeat")) {
                current = applyRepeatEffect(current, eff);
                continue;
            }

            String shader = eff.getShaderSource();
            if (shader != null && !shader.trim().isEmpty()) {
                current = GLEffectEngine.getInstance().applyEffect(current, eff, layerBounds, canvasBounds);
            }
        }

        sProcessedBitmapCache.put(cacheKey, current);
        return current;
    }

    /**
     * CPU-side implementation of repeat.xml effect.
     * Draws N copies of the source bitmap, each offset/rotated/scaled by cumulative step values.
     */
    private static Bitmap applyRepeatEffect(Bitmap source, EffectDefinition eff) {
        if (source == null || source.isRecycled()) return source;

        int count = 10;
        float offsetX = 0f, offsetY = 0f;
        float angle = 0f;
        float scale = 1.0f;
        float alpha = 1.0f;

        EffectParam countParam = eff.getParam("count");
        if (countParam != null) count = Math.max(1, Math.min(50, Math.round(countParam.getFloatValue())));

        EffectParam oxParam = eff.getParam("offset_x");
        EffectParam oyParam = eff.getParam("offset_y");
        if (oxParam != null) offsetX = oxParam.getFloatValue();
        if (oyParam != null) offsetY = oyParam.getFloatValue();

        EffectParam angleParam = eff.getParam("angle");
        if (angleParam != null) angle = angleParam.getFloatValue();

        EffectParam scaleParam = eff.getParam("scale");
        if (scaleParam != null) scale = scaleParam.getFloatValue();

        EffectParam alphaParam = eff.getParam("alpha");
        if (alphaParam != null) alpha = alphaParam.getFloatValue();

        int w = source.getWidth();
        int h = source.getHeight();

        // Compute canvas size to fit all copies (add padding for offsets)
        int padX = (int) (Math.abs(offsetX) * count + Math.abs(w));
        int padY = (int) (Math.abs(offsetY) * count + Math.abs(h));
        int outW = Math.max(w, padX + w);
        int outH = Math.max(h, padY + h);
        // Safety cap
        outW = Math.min(outW, 4096);
        outH = Math.min(outH, 4096);

        Bitmap output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float cx = outW / 2f;
        float cy = outH / 2f;

        for (int i = count; i >= 0; i--) {
            float stepAlpha = alpha - (alpha / count) * i;
            int a = (int) (Math.max(0f, Math.min(1f, stepAlpha)) * 255);
            drawPaint.setAlpha(a);

            canvas.save();
            canvas.translate(cx + offsetX * i, cy + offsetY * i);
            canvas.rotate(angle * i);
            float sc = (float) Math.pow(scale, i);
            canvas.scale(sc, sc);
            canvas.drawBitmap(source, -w / 2f, -h / 2f, drawPaint);
            canvas.restore();
        }

        return output;
    }

    /**
     * Backward-compatible alias for photo layer and legacy callers.
     */
    public static Bitmap getRenderBitmap(CanvasLayer layer, Bitmap source) {
        return processLayerEffects(layer, source, null, null);
    }
}
