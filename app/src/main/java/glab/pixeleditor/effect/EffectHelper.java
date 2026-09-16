package glab.pixeleditor.effect;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class EffectHelper {

    private static final String EFFECTS_DIR = "effects";
    private static List<EffectDefinition> cachedEffects = null;
    // Video and animation-only effects excluded from image/photo editor
    private static final java.util.Set<String> VIDEO_ANIMATION_EFFECTS = new java.util.HashSet<>(java.util.Arrays.asList(
            "blink.xml", "blink2.xml",
            "flicker.xml", "flicker2.xml",
            "oscillate.xml", "oscillate2.xml", "oscillate3.xml",
            "pulsate.xml", "pulsate2.xml",
            "pulse-opacity.xml", "pulse_opacity2.xml",
            "shake.xml", "shake2.xml", "shake-parts.xml",
            "spin.xml",
            "swing.xml", "swing2.xml",
            "timequant.xml",
            "drawing-progress.xml",
            "echo-keyframe.xml",
            "parenthelper.xml",
            "scaleassist.xml",
            "checkerdissolve.xml", "checkerdissolve2.xml",
            "fade.xml",
            "wipe.xml", "wipe2.xml", "radialwipe.xml",
            "dissolve.xml",
            "motionblur.xml", "motionblur2.xml", "motionblur3.xml", "motionblur4.xml",
            "move-along-path.xml", "move-along-path2.xml", "move-along-path3.xml",
            "grow-parts.xml",
            "facemotion.xml"
    ));

    public static boolean isVideoAnimationOnlyEffect(String fileName) {
        if (fileName == null) return false;
        return VIDEO_ANIMATION_EFFECTS.contains(fileName.toLowerCase(java.util.Locale.US));
    }

    public static EffectDefinition getEffectById(Context context, String effectId) {
        if (effectId == null || context == null) return null;
        List<EffectDefinition> all = getAllEffects(context);
        for (EffectDefinition def : all) {
            if (effectId.equalsIgnoreCase(def.getId()) || effectId.equalsIgnoreCase(def.getFileName())) {
                return def.copy();
            }
        }
        // Direct asset fallback
        try {
            String cleanName = effectId;
            if (cleanName.contains(".")) {
                cleanName = cleanName.substring(cleanName.lastIndexOf('.') + 1);
            }
            if (!cleanName.endsWith(".xml")) {
                cleanName = cleanName + ".xml";
            }
            AssetManager am = context.getAssets();
            try (InputStream is = am.open(EFFECTS_DIR + "/" + cleanName)) {
                return parseEffectFromXml(is, cleanName);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Clear the in-memory effect cache (call after filtering rules change at runtime). */
    public static synchronized void clearCache() {
        cachedEffects = null;
    }

    /**
     * Loads and caches all effects from assets/effects/*.xml (excluding video/animation-only effects).
     */
    public static synchronized List<EffectDefinition> getAllEffects(Context context) {
        if (cachedEffects != null && !cachedEffects.isEmpty()) {
            return cachedEffects;
        }

        List<EffectDefinition> list = new ArrayList<>();
        AssetManager am = context.getAssets();

        try {
            String[] files = am.list(EFFECTS_DIR);
            if (files != null) {
                for (String file : files) {
                    if (file.endsWith(".xml")) {
                        // Skip effects made purely for video / temporal animation
                        if (isVideoAnimationOnlyEffect(file)) {
                            continue;
                        }
                        // Skip blend-*.xml — these power the Blending panel, not the Effects browser
                        if (file.startsWith("blend-") || file.startsWith("blend_")) {
                            continue;
                        }

                        try (InputStream is = am.open(EFFECTS_DIR + "/" + file)) {
                            EffectDefinition effect = parseEffectFromXml(is, file);
                            if (effect != null) {
                                list.add(effect);
                            }
                        } catch (Exception e) {
                            System.err.println("Warning: Failed parsing effect " + file + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error: Error listing effects directory: " + e.getMessage());
        }

        // Sort by name
        Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        cachedEffects = list;
        return cachedEffects;
    }

    /**
     * Parses an effect XML file stream into an EffectDefinition with all parameters.
     * Uses standard Java DOM DocumentBuilder which runs seamlessly on both Android and JVM unit tests.
     */
    public static EffectDefinition parseEffectFromXml(InputStream is, String fileName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            try {
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {}

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            Element root = doc.getDocumentElement();

            if (root == null || !"effect".equalsIgnoreCase(root.getTagName())) {
                return null;
            }

            String id = root.getAttribute("id");
            String rawName = root.getAttribute("name");
            String desc = root.getAttribute("desc");
            String category = root.getAttribute("category");
            String tags = root.getAttribute("tags");
            String thumb = root.getAttribute("thumb");
            String deprecated = root.getAttribute("deprecated");

            String cleanName = humanizeEffectName(rawName, fileName);
            String cleanCategory = humanizeCategory(category);

            EffectDefinition effect = new EffectDefinition(
                    !id.isEmpty() ? id : fileName.replace(".xml", ""),
                    fileName,
                    cleanName,
                    cleanCategory
            );
            if (!desc.isEmpty()) effect.setDescription(humanizeLabel(desc, ""));
            if (!tags.isEmpty()) effect.setTags(tags);
            if (!thumb.isEmpty()) effect.setThumbPath(thumb);
            if ("true".equalsIgnoreCase(deprecated)) effect.setDeprecated(true);

            // Parse Params
            NodeList paramGroups = root.getElementsByTagName("params");
            for (int i = 0; i < paramGroups.getLength(); i++) {
                Node pGroupNode = paramGroups.item(i);
                NodeList childNodes = pGroupNode.getChildNodes();

                for (int j = 0; j < childNodes.getLength(); j++) {
                    Node child = childNodes.item(j);
                    if (child.getNodeType() != Node.ELEMENT_NODE) continue;

                    Element el = (Element) child;
                    String tagName = el.getTagName();

                    if ("texture".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        effect.getTextureIds().add(paramId);
                        String ds = el.getAttribute("downsample");
                        if (!ds.isEmpty()) {
                            try {
                                effect.getTextureDownsamples().put(paramId, Integer.parseInt(ds.trim()));
                            } catch (Exception ignored) {}
                        }
                    } else if ("spinner".equalsIgnoreCase(tagName) || "slider".equalsIgnoreCase(tagName) || "float".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        String label = el.getAttribute("label");
                        String defVal = el.getAttribute("default");
                        String minVal = el.getAttribute("min");
                        String maxVal = el.getAttribute("max");
                        String stepVal = el.getAttribute("step");
                        String type = el.getAttribute("type");

                        float def = parseSafeFloat(defVal, 0.0f);
                        float min = parseSafeFloat(minVal, 0.0f);
                        float max = parseSafeFloat(maxVal, 1.0f);
                        float step = parseSafeFloat(stepVal, 0.01f);

                        String cleanLabel = humanizeLabel(label, paramId);

                        EffectParam param = new EffectParam(
                                paramId,
                                EffectParam.ParamType.SLIDER,
                                cleanLabel,
                                def, min, max, step,
                                type
                        );
                        effect.addParam(param);
                        effect.getUniformTypes().put(paramId, "float");

                    } else if ("hue-disc".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        String label = el.getAttribute("label");
                        String cleanLabel = humanizeLabel(label, paramId);
                        String defVal = el.getAttribute("default");
                        float defHue = 0f;
                        float defSat = 0.5f;
                        if (!defVal.isEmpty()) {
                            String[] parts = defVal.split(",");
                            if (parts.length >= 1) defHue = parseSafeFloat(parts[0].trim(), 0f);
                            if (parts.length >= 2) defSat = parseSafeFloat(parts[1].trim(), 0.5f);
                        }
                        effect.addParam(new EffectParam(paramId + "_hue", EffectParam.ParamType.SLIDER, cleanLabel + " Hue", defHue, 0f, 360f, 1f, "angle"));
                        effect.addParam(new EffectParam(paramId + "_strength", EffectParam.ParamType.SLIDER, cleanLabel + " Strength", defSat, 0f, 1f, 0.01f, "percent"));
                        effect.getUniformTypes().put(paramId, "vec2");

                    } else if ("selector".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        String label = el.getAttribute("label");
                        String cleanLabel = humanizeLabel(label, paramId);
                        String defVal = el.getAttribute("default");
                        float def = parseSafeFloat(defVal, 0f);
                        effect.addParam(new EffectParam(paramId, EffectParam.ParamType.SLIDER, cleanLabel, def, 0f, 10f, 1f, "integer"));
                        effect.getUniformTypes().put(paramId, "int");

                    } else if ("switch".equalsIgnoreCase(tagName) || "toggle".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        String label = el.getAttribute("label");
                        String defVal = el.getAttribute("default");
                        boolean def = "true".equalsIgnoreCase(defVal);

                        String cleanLabel = humanizeLabel(label, paramId);
                        effect.addParam(new EffectParam(paramId, cleanLabel, def));
                        effect.getUniformTypes().put(paramId, "bool");

                    } else if ("color".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        String label = el.getAttribute("label");
                        String defVal = el.getAttribute("default");

                        int col = 0xFFFFFFFF;
                        if (!defVal.isEmpty()) {
                            try {
                                col = Color.parseColor(defVal);
                            } catch (Exception ignored) {}
                        }

                        String cleanLabel = humanizeLabel(label, paramId);
                        effect.addParam(new EffectParam(paramId, cleanLabel, col));
                        effect.getUniformTypes().put(paramId, "vec4");

                    } else if ("xyz".equalsIgnoreCase(tagName) || "orient".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        String label = el.getAttribute("label");
                        String cleanLabel = humanizeLabel(label, paramId);
                        String defVal = el.getAttribute("default");
                        float defX = 0f, defY = 0f, defZ = 0f;
                        if (!defVal.isEmpty()) {
                            String[] parts = defVal.split(",");
                            if (parts.length >= 1) defX = parseSafeFloat(parts[0].trim(), 0f);
                            if (parts.length >= 2) defY = parseSafeFloat(parts[1].trim(), 0f);
                            if (parts.length >= 3) defZ = parseSafeFloat(parts[2].trim(), 0f);
                        }
                        effect.addParam(new EffectParam(paramId + "_x", EffectParam.ParamType.SLIDER, cleanLabel + " X", defX, -360f, 360f, 1f, "angle"));
                        effect.addParam(new EffectParam(paramId + "_y", EffectParam.ParamType.SLIDER, cleanLabel + " Y", defY, -360f, 360f, 1f, "angle"));
                        effect.addParam(new EffectParam(paramId + "_z", EffectParam.ParamType.SLIDER, cleanLabel + " Z", defZ, -360f, 360f, 1f, "angle"));
                        effect.getUniformTypes().put(paramId, "vec3");

                    } else if ("point".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        String label = el.getAttribute("label");
                        String cleanLabel = humanizeLabel(label, paramId);
                        String defVal = el.getAttribute("default");
                        float defX = 0f, defY = 0f;
                        if (!defVal.isEmpty()) {
                            String[] parts = defVal.split(",");
                            if (parts.length >= 1) defX = parseSafeFloat(parts[0].trim(), 0f);
                            if (parts.length >= 2) defY = parseSafeFloat(parts[1].trim(), 0f);
                        }
                        effect.addParam(new EffectParam(paramId + "_x", EffectParam.ParamType.SLIDER, cleanLabel + " X", defX, -1000f, 1000f, 1f, "distance"));
                        effect.addParam(new EffectParam(paramId + "_y", EffectParam.ParamType.SLIDER, cleanLabel + " Y", defY, -1000f, 1000f, 1f, "distance"));
                        effect.getUniformTypes().put(paramId, "vec2");
                    }
                }
            }

            // Parse Passes (multi-pass ping-pong buffers)
            NodeList passNodes = root.getElementsByTagName("pass");
            for (int p = 0; p < passNodes.getLength(); p++) {
                Node passNode = passNodes.item(p);
                if (passNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element passEl = (Element) passNode;
                    String target = passEl.getAttribute("target");
                    effect.getPassTargets().add(target != null && !target.trim().isEmpty() ? target.trim() : null);
                    String passEff = passEl.getAttribute("effect");
                    effect.getPassEffects().add(passEff != null && !passEff.trim().isEmpty() ? passEff.trim() : null);
                }
            }

            // Parse Shader content (prefer primary group="0" or default fragment shader)
            NodeList shaderNodes = root.getElementsByTagName("shader");
            Element selectedShader = null;
            for (int s = 0; s < shaderNodes.getLength(); s++) {
                Node sNode = shaderNodes.item(s);
                if (sNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element sEl = (Element) sNode;
                    String type = sEl.getAttribute("type");
                    if ("fragment".equalsIgnoreCase(type)) {
                        String group = sEl.getAttribute("group");
                        if ("0".equals(group) || group.isEmpty()) {
                            selectedShader = sEl;
                            break;
                        } else if (selectedShader == null) {
                            selectedShader = sEl;
                        }
                    }
                }
            }
            if (selectedShader != null) {
                effect.setShaderSource(selectedShader.getTextContent());
            } else if (shaderNodes.getLength() > 0) {
                effect.setShaderSource(shaderNodes.item(0).getTextContent());
            }

            return effect;

        } catch (Exception e) {
            System.err.println("Error parsing XML for " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    // In-memory LRU Bitmap Cache (up to 4MB or ~60 thumbnails)
    private static final android.util.LruCache<String, Bitmap> sThumbnailCache =
            new android.util.LruCache<String, Bitmap>(60) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return 1;
                }
            };

    // Index of available asset thumbnail filenames in effects/thumb/
    private static Map<String, String> sThumbIndex = null;

    private static synchronized void ensureThumbIndex(Context context) {
        if (sThumbIndex != null) return;
        sThumbIndex = new java.util.HashMap<>();
        try {
            AssetManager am = context.getAssets();
            String[] list = am.list(EFFECTS_DIR + "/thumb");
            if (list != null) {
                for (String filename : list) {
                    if (filename.endsWith(".webp") || filename.endsWith(".png") || filename.endsWith(".jpg")) {
                        String fullPath = EFFECTS_DIR + "/thumb/" + filename;
                        // Store exact filename
                        sThumbIndex.put(filename.toLowerCase(java.util.Locale.US), fullPath);
                        // Store base name without extension
                        String base = filename.substring(0, filename.lastIndexOf('.')).toLowerCase(java.util.Locale.US);
                        sThumbIndex.put(base, fullPath);
                        // Store stripped name without underscores and dashes
                        String stripped = base.replaceAll("[_\\-0-9]", "");
                        if (!stripped.isEmpty() && !sThumbIndex.containsKey(stripped)) {
                            sThumbIndex.put(stripped, fullPath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed building thumbnail index: " + e.getMessage());
        }
    }

    /**
     * Resolves the relative asset path in effects/thumb/ for an effect.
     */
    public static String resolveThumbnailAssetPath(Context context, EffectDefinition effect) {
        if (effect == null || context == null) {
            return null;
        }
        ensureThumbIndex(context);

        List<String> candidates = new ArrayList<>();

        // Add explicit thumbPath
        String thumbPath = effect.getThumbPath();
        if (thumbPath != null && !thumbPath.isEmpty()) {
            String clean = thumbPath.replace("\\", "/");
            if (clean.contains("/")) {
                clean = clean.substring(clean.lastIndexOf('/') + 1);
            }
            candidates.add(clean.toLowerCase(java.util.Locale.US));
            if (clean.contains(".")) {
                candidates.add(clean.substring(0, clean.lastIndexOf('.')).toLowerCase(java.util.Locale.US));
            }
        }

        // Add filename base
        String fileBase = effect.getFileName().replace(".xml", "").toLowerCase(java.util.Locale.US);
        candidates.add(fileBase);
        candidates.add(fileBase.replace("-", "_"));
        candidates.add(fileBase.replaceAll("[_\\-0-9]", ""));

        // Add ID suffix
        String id = effect.getId();
        if (id.contains(".")) {
            String idSuffix = id.substring(id.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.US);
            candidates.add(idSuffix);
            candidates.add(idSuffix.replace("-", "_"));
            candidates.add(idSuffix.replaceAll("[_\\-0-9]", ""));
        }

        // Known aliases mapping
        Map<String, String> aliases = getCommonAliases();
        for (String c : new ArrayList<>(candidates)) {
            if (aliases.containsKey(c)) {
                candidates.add(aliases.get(c));
            }
        }

        // 1. Direct index match
        for (String cand : candidates) {
            if (sThumbIndex.containsKey(cand)) {
                return sThumbIndex.get(cand);
            }
            if (!cand.endsWith(".webp") && sThumbIndex.containsKey(cand + ".webp")) {
                return sThumbIndex.get(cand + ".webp");
            }
        }

        // 2. Substring match
        for (String cand : candidates) {
            if (cand.length() < 3) continue;
            for (Map.Entry<String, String> entry : sThumbIndex.entrySet()) {
                if (entry.getKey().contains(cand) || cand.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        // 3. Fallback direct path
        if (thumbPath != null && !thumbPath.isEmpty()) {
            String directPath = thumbPath;
            if (!directPath.startsWith(EFFECTS_DIR + "/")) {
                directPath = EFFECTS_DIR + "/" + directPath;
            }
            return directPath;
        }

        return null;
    }

    /**
     * Loads thumbnail drawable supporting animated WebP playback on Android 9+ (API 28+).
     */
    public static android.graphics.drawable.Drawable loadThumbnailDrawable(Context context, EffectDefinition effect) {
        if (effect == null || context == null) return null;
        String assetPath = resolveThumbnailAssetPath(context, effect);
        if (assetPath != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.Source src =
                            android.graphics.ImageDecoder.createSource(context.getAssets(), assetPath);
                    android.graphics.drawable.Drawable drawable =
                            android.graphics.ImageDecoder.decodeDrawable(src);
                    if (drawable instanceof android.graphics.drawable.Animatable) {
                        ((android.graphics.drawable.Animatable) drawable).start();
                    }
                    return drawable;
                } else {
                    try (InputStream is = context.getAssets().open(assetPath)) {
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        if (bmp != null) {
                            return new android.graphics.drawable.BitmapDrawable(context.getResources(), bmp);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        Bitmap fallback = loadThumbnail(context, effect);
        if (fallback != null) {
            return new android.graphics.drawable.BitmapDrawable(context.getResources(), fallback);
        }
        return null;
    }

    /**
     * Loads thumbnail image bitmap from assets with fuzzy matching, aliases and procedural fallback.
     */
    public static Bitmap loadThumbnail(Context context, EffectDefinition effect) {
        if (effect == null || context == null) {
            return null;
        }

        String cacheKey = effect.getId();
        Bitmap cached = sThumbnailCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String assetPath = resolveThumbnailAssetPath(context, effect);
        if (assetPath != null) {
            try (InputStream is = context.getAssets().open(assetPath)) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) {
                    sThumbnailCache.put(cacheKey, bmp);
                    return bmp;
                }
            } catch (Exception ignored) {}
        }

        // Generate expressive procedural thumbnail bitmap
        Bitmap generated = generateProceduralThumbnail(context, effect);
        if (generated != null) {
            sThumbnailCache.put(cacheKey, generated);
            return generated;
        }

        return null;
    }

    private static Bitmap generateProceduralThumbnail(Context context, EffectDefinition effect) {
        int size = 96;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        String cat = effect.getCategory() != null ? effect.getCategory().toLowerCase() : "";
        int startCol = 0xFF1E273C;
        int endCol = 0xFF0D1525;
        int accentCol = 0xFF00E5BC;

        if (cat.contains("blur")) {
            startCol = 0xFF2A1B3D;
            endCol = 0xFF140D20;
            accentCol = 0xFFD946EF;
        } else if (cat.contains("color")) {
            startCol = 0xFF1C2D37;
            endCol = 0xFF0A1820;
            accentCol = 0xFF06B6D4;
        } else if (cat.contains("repeat") || cat.contains("procedural")) {
            startCol = 0xFF1A332B;
            endCol = 0xFF0B1E17;
            accentCol = 0xFF10B981;
        } else if (cat.contains("warp") || cat.contains("distort")) {
            startCol = 0xFF35201A;
            endCol = 0xFF1B0F0B;
            accentCol = 0xFFF97316;
        }

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setShader(new android.graphics.LinearGradient(0, 0, size, size, startCol, endCol, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0, 0, size, size, 16f, 16f, bgPaint);

        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);
        ringPaint.setColor(accentCol);
        canvas.drawCircle(size / 2f, size / 2f, 24f, ringPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(20f);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        String name = effect.getName() != null && !effect.getName().isEmpty() ? effect.getName() : "FX";
        String initial = name.substring(0, Math.min(2, name.length())).toUpperCase(java.util.Locale.US);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = size / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(initial, size / 2f, baseline, textPaint);

        return bmp;
    }

    private static Map<String, String> getCommonAliases() {
        Map<String, String> map = new java.util.HashMap<>();
        map.put("glowscan", "glow_scan");
        map.put("maskblur", "mask_blur");
        map.put("mblur", "motion_blur");
        map.put("palmap", "palette_map");
        map.put("pinchbulge", "pinch_bulge");
        map.put("mattechoke", "matte_choker");
        map.put("simplestars", "simple_starfield");
        map.put("repeat", "repeat");
        map.put("repeatlinear", "linear_repeat");
        map.put("repeatscatter", "scatter_repeat");
        map.put("repeatradial", "radial_repeat");
        map.put("directionalblur", "directional_blur");
        map.put("turbdisplace", "turbulent_displace");
        map.put("radialrays", "radial_rays");
        map.put("chromakey", "chroma_key");
        map.put("brightnesscontrast", "brightness_contrast");
        map.put("colortemp", "color_temperature");
        map.put("colortune", "color_tune");
        map.put("fastblur", "box_blur");
        map.put("unsharpmask", "unsharp_mask");
        map.put("tiles", "tiles");
        map.put("rgbsplit", "rgb_split");
        map.put("vignette", "vignette");
        map.put("halftone", "halftone");
        map.put("findedges", "find_edges");
        return map;
    }

    /**
     * Filter effects by category
     */
    public static List<EffectDefinition> getEffectsByCategory(Context context, String category) {
        List<EffectDefinition> all = getAllEffects(context);
        List<EffectDefinition> filtered = new ArrayList<>();
        for (EffectDefinition eff : all) {
            if (eff.getCategory().equalsIgnoreCase(category) || category.equalsIgnoreCase("All")) {
                filtered.add(eff);
            }
        }
        return filtered;
    }

    /**
     * Search effects by keyword
     */
    public static List<EffectDefinition> searchEffects(Context context, String query) {
        List<EffectDefinition> all = getAllEffects(context);
        if (query == null || query.trim().isEmpty()) return all;

        String q = query.toLowerCase().trim();
        List<EffectDefinition> results = new ArrayList<>();
        for (EffectDefinition eff : all) {
            if (eff.getName().toLowerCase().contains(q)
                    || eff.getCategory().toLowerCase().contains(q)
                    || eff.getTags().toLowerCase().contains(q)
                    || eff.getFileName().toLowerCase().contains(q)) {
                results.add(eff);
            }
        }
        return results;
    }

    /**
     * Returns unique categories
     */
    public static List<String> getCategories(Context context) {
        List<EffectDefinition> all = getAllEffects(context);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (EffectDefinition eff : all) {
            map.put(eff.getCategory(), map.getOrDefault(eff.getCategory(), 0) + 1);
        }
        return new ArrayList<>(map.keySet());
    }

    private static float parseSafeFloat(String val, float fallback) {
        if (val == null || val.trim().isEmpty()) return fallback;
        try {
            return Float.parseFloat(val.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String humanizeCategory(String cat) {
        if (cat == null || cat.trim().isEmpty()) return "Other";
        String c = cat.toLowerCase().trim();
        switch (c) {
            case "color": return "Color & Light";
            case "blur": return "Blur";
            case "drawing":
            case "edge": return "Drawing & Edge";
            case "distortion":
            case "warp": return "Distortion / Warp";
            case "procedural": return "Procedural";
            case "3d": return "3D";
            case "matte":
            case "mask": return "Matte / Mask / Key";
            case "repeat": return "Repeat";
            case "text": return "Text";
            default:
                return Character.toUpperCase(c.charAt(0)) + c.substring(1);
        }
    }

    private static final Map<String, String> KNOWN_NAMES = new HashMap<>();
    static {
        KNOWN_NAMES.put("boxblur", "Box Blur");
        KNOWN_NAMES.put("chromaticzoomblur", "Chromatic Zoom Blur");
        KNOWN_NAMES.put("directionalblur", "Directional Blur");
        KNOWN_NAMES.put("gaussianblur", "Gaussian Blur");
        KNOWN_NAMES.put("innerblur", "Inner Blur");
        KNOWN_NAMES.put("lensblur", "Lens Blur");
        KNOWN_NAMES.put("linearstreaks", "Linear Streaks");
        KNOWN_NAMES.put("maskblur", "Mask Blur");
        KNOWN_NAMES.put("mosaic", "Mosaic");
        KNOWN_NAMES.put("motionblur", "Motion Blur");
        KNOWN_NAMES.put("mblur", "Motion Blur");
        KNOWN_NAMES.put("sharpen", "Sharpen");
        KNOWN_NAMES.put("spinblur", "Spin Blur");
        KNOWN_NAMES.put("spinstreaks", "Spin Streaks");
        KNOWN_NAMES.put("streakstrips", "Streak Strips");
        KNOWN_NAMES.put("unsharpmask", "Unsharp Mask");
        KNOWN_NAMES.put("warpblur", "Warp Blur");
        KNOWN_NAMES.put("zoomblur", "Zoom Blur");
        KNOWN_NAMES.put("zoomstreaks", "Zoom Streaks");
        KNOWN_NAMES.put("brightnesscontrast", "Brightness / Contrast");
        KNOWN_NAMES.put("colortemperature", "Color Temperature");
        KNOWN_NAMES.put("colortune", "Color Tune");
        KNOWN_NAMES.put("exposuregamma", "Exposure / Gamma");
        KNOWN_NAMES.put("gradientoverlay", "Gradient Overlay");
        KNOWN_NAMES.put("gradientmap", "Gradient Map");
        KNOWN_NAMES.put("fourcolorgradient", "4-Color Gradient");
        KNOWN_NAMES.put("hotcolors", "Hot Colors");
        KNOWN_NAMES.put("hueshift", "Hue Shift");
        KNOWN_NAMES.put("monochrome", "Monochrome");
        KNOWN_NAMES.put("palettemap", "Palette Map");
        KNOWN_NAMES.put("rgbcolorsplit", "RGB Color Split");
        KNOWN_NAMES.put("saturationvibrance", "Saturation / Vibrance");
        KNOWN_NAMES.put("solidcolor", "Solid Color");
        KNOWN_NAMES.put("threshold", "Threshold");
        KNOWN_NAMES.put("vignette", "Vignette");
        KNOWN_NAMES.put("chromakey", "Chroma Key");
        KNOWN_NAMES.put("advancedchromakey", "Advanced Chroma Key");
        KNOWN_NAMES.put("lumakey", "Luma Key");
        KNOWN_NAMES.put("luma_key", "Luma Key");
        KNOWN_NAMES.put("mattechoker", "Matte Choker");
        KNOWN_NAMES.put("autoshake", "Auto Shake");
        KNOWN_NAMES.put("bend", "Bend");
        KNOWN_NAMES.put("pinchbulge", "Pinch / Bulge");
        KNOWN_NAMES.put("polarcoordinates", "Polar Coordinates");
        KNOWN_NAMES.put("radialrays", "Radial Rays");
        KNOWN_NAMES.put("ripple", "Ripple");
        KNOWN_NAMES.put("simplestarfield", "Simple Starfield");
        KNOWN_NAMES.put("stars", "Stars");
        KNOWN_NAMES.put("swirl", "Swirl");
        KNOWN_NAMES.put("turbulentdisplace", "Turbulent Displace");
        KNOWN_NAMES.put("wavewarp", "Wave Warp");
        KNOWN_NAMES.put("circularripple", "Circular Ripple");
        KNOWN_NAMES.put("cube", "Cube 3D");
        KNOWN_NAMES.put("cylinder", "Cylinder 3D");
        KNOWN_NAMES.put("flip_layer", "Flip Layer");
        KNOWN_NAMES.put("sphere", "Sphere 3D");
        KNOWN_NAMES.put("stretchaxis", "Stretch Axis");
        KNOWN_NAMES.put("textprogress", "Text Progress");
        KNOWN_NAMES.put("textrandomizer", "Text Randomize");
        KNOWN_NAMES.put("texttransform", "Text Transform");
        KNOWN_NAMES.put("textspacing", "Text Spacing");
    }

    private static String humanizeEffectName(String rawName, String fileName) {
        String key = "";
        if (rawName != null && !rawName.isEmpty()) {
            key = rawName;
            if (key.contains("/")) {
                key = key.substring(key.lastIndexOf('/') + 1);
            }
            key = key.replace("effect_", "").replace("_name", "");
        } else {
            key = fileName.replace(".xml", "");
        }

        String cleanKey = key.toLowerCase(java.util.Locale.US).replace("-", "").replace("_", "");
        if (KNOWN_NAMES.containsKey(cleanKey)) {
            return KNOWN_NAMES.get(cleanKey);
        }

        String noDigits = cleanKey.replaceAll("[0-9]", "");
        if (KNOWN_NAMES.containsKey(noDigits)) {
            return KNOWN_NAMES.get(noDigits);
        }

        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2")
                           .replaceAll("([A-Za-z])([0-9])", "$1 $2")
                           .replace('_', ' ')
                           .replace('-', ' ');
        return toTitleCase(spaced);
    }

    private static String humanizeLabel(String rawLabel, String fallback) {
        String label = (rawLabel != null && !rawLabel.isEmpty()) ? rawLabel : fallback;
        if (label == null || label.isEmpty()) return "";

        if (label.contains("/")) {
            label = label.substring(label.lastIndexOf('/') + 1);
        }
        label = label.replace("effect_param_", "").replace("param_", "");
        return toTitleCase(label.replace('_', ' ').replace('-', ' '));
    }

    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char ch : input.toCharArray()) {
            if (Character.isWhitespace(ch) || ch == '/' || ch == '-') {
                sb.append(ch);
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(ch));
                nextUpper = false;
            } else if (Character.isUpperCase(ch)) {
                sb.append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
