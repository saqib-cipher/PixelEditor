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

    /**
     * Loads thumbnail image bitmap from assets
     */
    public static Bitmap loadThumbnail(Context context, EffectDefinition effect) {
        if (effect == null || effect.getThumbPath() == null || effect.getThumbPath().isEmpty()) {
            return null;
        }

        String path = effect.getThumbPath();
        if (!path.startsWith(EFFECTS_DIR + "/")) {
            path = EFFECTS_DIR + "/" + path;
        }

        String[] possiblePaths = new String[]{
                path,
                path.replace(".jpg", ".webp").replace(".png", ".webp"),
                EFFECTS_DIR + "/thumb/" + effect.getFileName().replace(".xml", ".webp").replace("-", "_")
        };

        AssetManager am = context.getAssets();
        for (String p : possiblePaths) {
            try (InputStream is = am.open(p)) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) return bmp;
            } catch (Exception ignored) {}
        }
        return null;
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
