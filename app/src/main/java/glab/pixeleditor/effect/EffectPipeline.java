package glab.pixeleditor.effect;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.List;

import glab.pixeleditor.model.CanvasLayer;
import glab.pixeleditor.model.PhotoLayer;
import glab.pixeleditor.model.ShapeLayer;

public class EffectPipeline {

    /**
     * Applies geometric transforms introduced by effects (e.g. Stretch Axis, Flip, Offset)
     */
    public static void applyEffectTransforms(Canvas canvas, CanvasLayer layer) {
        for (EffectDefinition eff : layer.getAppliedEffects()) {
            if (!eff.isEnabled()) continue;
            String id = eff.getId().toLowerCase();
            String name = eff.getName().toLowerCase();

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

            // 2. Flip
            if (id.contains("flip") || name.contains("flip")) {
                EffectParam axisParam = eff.getParam("axis");
                EffectParam flipH = eff.getParam("horizontal");
                EffectParam flipV = eff.getParam("vertical");
                float sx = (flipH != null && flipH.getBooleanValue()) ? -1f : 1f;
                float sy = (flipV != null && flipV.getBooleanValue()) ? -1f : 1f;
                if (axisParam != null && axisParam.getFloatValue() > 0) sx = -1f;
                canvas.scale(sx, sy);
            }

            // 3. Offset
            if (id.contains("offset") || name.contains("offset")) {
                EffectParam xParam = eff.getParam("x");
                EffectParam yParam = eff.getParam("y");
                if (xParam != null || yParam != null) {
                    float ox = xParam != null ? xParam.getFloatValue() : 0f;
                    float oy = yParam != null ? yParam.getFloatValue() : 0f;
                    canvas.translate(ox, oy);
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

        // Loop applied effects
        for (EffectDefinition eff : layer.getAppliedEffects()) {
            if (!eff.isEnabled()) continue;
            String id = eff.getId().toLowerCase();
            String name = eff.getName().toLowerCase();

            // 1. Brightness & Contrast
            if (id.contains("bright") || name.contains("brightness") || id.contains("contrast") || name.contains("contrast")) {
                EffectParam bParam = eff.getParam("brightness");
                if (bParam == null) bParam = eff.getParam("bright");
                if (bParam == null) bParam = eff.getParam("bias");
                if (bParam != null) {
                    totalBrightness += bParam.getFloatValue() * 100f;
                }

                EffectParam cParam = eff.getParam("contrast");
                if (cParam != null) {
                    totalContrast += (cParam.getFloatValue() - 1.0f) * 100f;
                }
            }

            // 2. Saturation & Vibrance
            if (id.contains("sat") || name.contains("saturation") || id.contains("vibrance") || name.contains("vibrance")) {
                EffectParam sParam = eff.getParam("saturation");
                if (sParam == null) sParam = eff.getParam("sat");
                if (sParam == null) sParam = eff.getParam("vibrance");
                if (sParam != null) {
                    totalSaturation += (sParam.getFloatValue() - 1.0f) * 100f;
                }
            }

            // 3. Hue Shift / Temperature / Tint
            if (id.contains("hue") || name.contains("hue") || id.contains("temperature") || id.contains("tint") || id.contains("warmth")) {
                EffectParam hParam = eff.getParam("shift");
                if (hParam == null) hParam = eff.getParam("hue");
                if (hParam == null) hParam = eff.getParam("angle");
                if (hParam != null) {
                    totalHue += hParam.getFloatValue();
                }

                EffectParam tParam = eff.getParam("temp");
                if (tParam == null) tParam = eff.getParam("temperature");
                if (tParam == null) tParam = eff.getParam("warmth");
                if (tParam != null) {
                    totalWarmth += (tParam.getFloatValue() - 1.0f) * 100f;
                }
            }

            // 4. Invert
            if (id.contains("invert") || name.contains("invert")) {
                isInverted = !isInverted;
            }

            // 5. Exposure / Gamma
            if (id.contains("exposure") || name.contains("exposure")) {
                EffectParam exp = eff.getParam("exposure");
                if (exp == null) exp = eff.getParam("gain");
                if (exp != null) {
                    totalBrightness += (exp.getFloatValue() - 1.0f) * 120f;
                }
            }

            // 6. Colorize / Solid Color
            if (id.contains("colorize") || id.contains("colortint") || name.contains("colorize")) {
                EffectParam colorParam = eff.getParam("color");
                EffectParam strengthParam = eff.getParam("strength");
                if (colorParam != null) {
                    colorTint = colorParam.getColorValue();
                    tintAmount = strengthParam != null ? strengthParam.getFloatValue() : 0.5f;
                }
            }
        }

        // Apply Brightness
        if (totalBrightness != 0) {
            ColorMatrix bMat = new ColorMatrix();
            float b = totalBrightness * 1.5f;
            bMat.set(new float[]{
                    1f, 0f, 0f, 0f, b,
                    0f, 1f, 0f, 0f, b,
                    0f, 0f, 1f, 0f, b,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(bMat);
            hasAdjustment = true;
        }

        // Apply Contrast
        if (totalContrast != 0) {
            float c = (totalContrast + 100f) / 100f;
            float t = (1f - c) / 2f * 255f;
            ColorMatrix cMat = new ColorMatrix(new float[]{
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(cMat);
            hasAdjustment = true;
        }

        // Apply Saturation
        if (totalSaturation != 0) {
            float s = Math.max(0f, (totalSaturation + 100f) / 100f);
            ColorMatrix sMat = new ColorMatrix();
            sMat.setSaturation(s);
            matrix.postConcat(sMat);
            hasAdjustment = true;
        }

        // Apply Hue Shift
        if (totalHue != 0) {
            ColorMatrix hMat = new ColorMatrix();
            float rad = (float) Math.toRadians(totalHue);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            hMat.set(new float[]{
                    0.213f + cos * 0.787f - sin * 0.213f, 0.715f - cos * 0.715f - sin * 0.715f, 0.072f - cos * 0.072f + sin * 0.928f, 0f, 0f,
                    0.213f - cos * 0.213f + sin * 0.143f, 0.715f + cos * 0.285f + sin * 0.140f, 0.072f - cos * 0.072f - sin * 0.283f, 0f, 0f,
                    0.213f - cos * 0.213f - sin * 0.787f, 0.715f - cos * 0.715f + sin * 0.715f, 0.072f + cos * 0.928f + sin * 0.072f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(hMat);
            hasAdjustment = true;
        }

        // Apply Warmth
        if (totalWarmth != 0) {
            float w = totalWarmth * 0.8f;
            ColorMatrix wMat = new ColorMatrix(new float[]{
                    1f, 0f, 0f, 0f, w,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, -w,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(wMat);
            hasAdjustment = true;
        }

        // Apply Invert
        if (isInverted) {
            ColorMatrix invMat = new ColorMatrix(new float[]{
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(invMat);
            hasAdjustment = true;
        }

        // Apply Color Tint / Colorize
        if (colorTint != 0 && tintAmount > 0) {
            float r = Color.red(colorTint) / 255f * tintAmount;
            float g = Color.green(colorTint) / 255f * tintAmount;
            float b = Color.blue(colorTint) / 255f * tintAmount;
            float base = 1f - tintAmount;
            ColorMatrix tintMat = new ColorMatrix(new float[]{
                    base + r, 0f, 0f, 0f, 0f,
                    0f, base + g, 0f, 0f, 0f,
                    0f, 0f, base + b, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
            });
            matrix.postConcat(tintMat);
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

            // 2. Blur / Gaussian Blur / Box Blur
            if (id.contains("blur") || name.contains("blur")) {
                EffectParam strParam = eff.getParam("strength");
                if (strParam == null) strParam = eff.getParam("radius");
                if (strParam == null) strParam = eff.getParam("size");
                float radius = strParam != null ? strParam.getFloatValue() * 25f : 12f;
                if (radius > 0.5f) {
                    BlurMaskFilter filter = new BlurMaskFilter(Math.min(50f, radius), BlurMaskFilter.Blur.NORMAL);
                    if (fillPaint != null) fillPaint.setMaskFilter(filter);
                    if (strokePaint != null) strokePaint.setMaskFilter(filter);
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
}
