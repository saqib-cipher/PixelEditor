package glab.pixeleditor.effect;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class EffectHelper {

    private static final String EFFECTS_DIR = "effects";
    private static List<EffectDefinition> cachedEffects = null;

    /**
     * Loads and caches all effects from assets/effects/*.xml
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

                    if ("spinner".equalsIgnoreCase(tagName) || "slider".equalsIgnoreCase(tagName)) {
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

                    } else if ("switch".equalsIgnoreCase(tagName) || "toggle".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        String label = el.getAttribute("label");
                        String defVal = el.getAttribute("default");
                        boolean def = "true".equalsIgnoreCase(defVal);

                        String cleanLabel = humanizeLabel(label, paramId);
                        effect.addParam(new EffectParam(paramId, cleanLabel, def));

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
                    }
                }
            }

            // Parse Shader content
            NodeList shaderNodes = root.getElementsByTagName("shader");
            if (shaderNodes.getLength() > 0) {
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
     * Loads thumbnail image bitmap from assets with fuzzy matching and LRU caching.
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

        ensureThumbIndex(context);
        AssetManager am = context.getAssets();

        // 1. Candidate lookup strings
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

        // 2. Try resolving path from candidates in the index
        String resolvedAssetPath = null;
        for (String cand : candidates) {
            if (sThumbIndex.containsKey(cand)) {
                resolvedAssetPath = sThumbIndex.get(cand);
                break;
            }
            // Try with .webp extension
            if (!cand.endsWith(".webp") && sThumbIndex.containsKey(cand + ".webp")) {
                resolvedAssetPath = sThumbIndex.get(cand + ".webp");
                break;
            }
        }

        // 3. If still not found, search substring match in index
        if (resolvedAssetPath == null) {
            for (String cand : candidates) {
                if (cand.length() < 3) continue;
                for (Map.Entry<String, String> entry : sThumbIndex.entrySet()) {
                    if (entry.getKey().contains(cand) || cand.contains(entry.getKey())) {
                        resolvedAssetPath = entry.getValue();
                        break;
                    }
                }
                if (resolvedAssetPath != null) break;
            }
        }

        // 4. Try opening the direct asset path if available
        if (resolvedAssetPath != null) {
            try (InputStream is = am.open(resolvedAssetPath)) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) {
                    sThumbnailCache.put(cacheKey, bmp);
                    return bmp;
                }
            } catch (Exception ignored) {}
        }

        // Direct path attempt
        if (thumbPath != null && !thumbPath.isEmpty()) {
            String directPath = thumbPath;
            if (!directPath.startsWith(EFFECTS_DIR + "/")) {
                directPath = EFFECTS_DIR + "/" + directPath;
            }
            try (InputStream is = am.open(directPath)) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) {
                    sThumbnailCache.put(cacheKey, bmp);
                    return bmp;
                }
            } catch (Exception ignored) {}
        }

        return null;
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

    private static String humanizeEffectName(String rawName, String fileName) {
        if (rawName != null && !rawName.isEmpty()) {
            String name = rawName;
            if (name.contains("/")) {
                name = name.substring(name.lastIndexOf('/') + 1);
            }
            name = name.replace("effect_", "").replace("_name", "");
            return toTitleCase(name.replace('_', ' ').replace('-', ' '));
        }

        String base = fileName.replace(".xml", "").replace('_', ' ').replace('-', ' ');
        return toTitleCase(base);
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
