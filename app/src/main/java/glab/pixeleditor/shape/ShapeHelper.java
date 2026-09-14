package glab.pixeleditor.shape;

import android.content.Context;
import android.content.res.AssetManager;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import glab.pixeleditor.effect.EffectParam;

public class ShapeHelper {

    private static final String SHAPES_DIR = "shapes";
    private static List<ShapeDefinition> cachedShapes = null;

    /**
     * Loads and caches all shapes from assets/shapes/*.xml
     */
    public static synchronized List<ShapeDefinition> getAllShapes(Context context) {
        if (cachedShapes != null && !cachedShapes.isEmpty()) {
            return cachedShapes;
        }

        List<ShapeDefinition> list = new ArrayList<>();
        AssetManager am = context.getAssets();

        try {
            String[] files = am.list(SHAPES_DIR);
            if (files != null) {
                for (String file : files) {
                    if (file.endsWith(".xml")) {
                        try (InputStream is = am.open(SHAPES_DIR + "/" + file)) {
                            ShapeDefinition shape = parseShapeFromXml(is, file);
                            if (shape != null) {
                                list.add(shape);
                            }
                        } catch (Exception e) {
                            System.err.println("Warning: Failed parsing shape " + file + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error: Error listing shapes directory: " + e.getMessage());
        }

        Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        cachedShapes = list;
        return cachedShapes;
    }

    public static ShapeDefinition getShapeById(Context context, String idOrFileName) {
        List<ShapeDefinition> all = getAllShapes(context);
        for (ShapeDefinition s : all) {
            if (s.getId().equalsIgnoreCase(idOrFileName)
                    || s.getFileName().equalsIgnoreCase(idOrFileName)
                    || s.getFileName().replace(".xml", "").equalsIgnoreCase(idOrFileName)) {
                return s.copy();
            }
        }
        return null;
    }

    public static ShapeDefinition parseShapeFromXml(InputStream is, String fileName) {
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

            if (root == null || !"shape".equalsIgnoreCase(root.getTagName())) {
                return null;
            }

            String id = root.getAttribute("id");
            String rawName = root.getAttribute("name");
            String cleanName = humanizeShapeName(rawName, fileName);

            ShapeDefinition shape = new ShapeDefinition(
                    !id.isEmpty() ? id : fileName.replace(".xml", ""),
                    fileName,
                    cleanName
            );

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

                    if ("slider".equalsIgnoreCase(tagName) || "spinner".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        String label = el.getAttribute("label");
                        String defVal = el.getAttribute("default");
                        String minVal = el.getAttribute("min");
                        String maxVal = el.getAttribute("max");
                        String stepVal = el.getAttribute("step");
                        String type = el.getAttribute("type");

                        float def = parseSafeFloat(defVal, 0.0f);
                        float min = parseSafeFloat(minVal, 0.0f);
                        float max = parseSafeFloat(maxVal, 100.0f);
                        if (max <= min) max = min + 100f;
                        if (max > 500f && !"angle".equalsIgnoreCase(type)) {
                            max = Math.min(max, 500f);
                        }
                        if ("angle".equalsIgnoreCase(type) || paramId.toLowerCase().contains("angle")) {
                            min = -360f;
                            max = 360f;
                            type = "angle";
                        }
                        float step = parseSafeFloat(stepVal, 1.0f);

                        String cleanLabel = humanizeLabel(label, paramId);
                        EffectParam param = new EffectParam(
                                paramId,
                                EffectParam.ParamType.SLIDER,
                                cleanLabel,
                                def, min, max, step,
                                type
                        );
                        shape.addParam(param);

                    } else if ("switch".equalsIgnoreCase(tagName) || "toggle".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        String label = el.getAttribute("label");
                        String defVal = el.getAttribute("default");
                        boolean def = "true".equalsIgnoreCase(defVal);

                        String cleanLabel = humanizeLabel(label, paramId);
                        shape.addParam(new EffectParam(paramId, cleanLabel, def));

                    } else if ("point".equalsIgnoreCase(tagName)) {
                        String paramId = el.getAttribute("id");
                        String label = el.getAttribute("label");
                        String defVal = el.getAttribute("default");
                        String type = el.getAttribute("type");
                        float defX = 100f, defY = 100f;
                        if (!defVal.isEmpty()) {
                            String[] parts = defVal.split(",");
                            if (parts.length >= 1) defX = parseSafeFloat(parts[0].trim(), 100f);
                            if (parts.length >= 2) defY = parseSafeFloat(parts[1].trim(), 100f);
                        }
                        String cleanLabel = humanizeLabel(label, paramId);
                        boolean isSize = "size".equalsIgnoreCase(type) || "size".equalsIgnoreCase(paramId);
                        float minVal = isSize ? 1f : -500f;
                        float maxVal = isSize ? 1000f : 500f;
                        shape.addParam(new EffectParam(paramId + "_x", EffectParam.ParamType.SLIDER, cleanLabel + " X", defX, minVal, maxVal, 1f, "distance"));
                        shape.addParam(new EffectParam(paramId + "_y", EffectParam.ParamType.SLIDER, cleanLabel + " Y", defY, minVal, maxVal, 1f, "distance"));
                    }
                }
            }

            // Parse Script CDATA
            NodeList scriptNodes = root.getElementsByTagName("script");
            if (scriptNodes.getLength() > 0) {
                shape.setScriptSource(scriptNodes.item(0).getTextContent());
            }

            return shape;
        } catch (Exception e) {
            System.err.println("Error parsing shape XML " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    private static float parseSafeFloat(String val, float fallback) {
        if (val == null || val.trim().isEmpty()) return fallback;
        try {
            return Float.parseFloat(val.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static final Map<String, String> SHAPE_NAME_MAP = new LinkedHashMap<>();
    static {
        SHAPE_NAME_MAP.put("shape_star", "Star");
        SHAPE_NAME_MAP.put("shape_arrow", "Arrow");
        SHAPE_NAME_MAP.put("shape_roundrect", "Rounded Rectangle");
        SHAPE_NAME_MAP.put("shape_circle", "Circle");
        SHAPE_NAME_MAP.put("shape_rect", "Rectangle");
        SHAPE_NAME_MAP.put("shape_pie", "Pie / Wedge");
        SHAPE_NAME_MAP.put("shape_arc", "Arc");
        SHAPE_NAME_MAP.put("shape_poly", "Polygon");
        SHAPE_NAME_MAP.put("shape_plus", "Cross / Plus");
        SHAPE_NAME_MAP.put("shape_moon", "Crescent Moon");
        SHAPE_NAME_MAP.put("shape_callout", "Speech Bubble");
        SHAPE_NAME_MAP.put("shape_calloutrr", "Speech Bubble");
        SHAPE_NAME_MAP.put("shape_multifoil", "Multifoil Petals");
        SHAPE_NAME_MAP.put("shape_penta", "Pentagon");
        SHAPE_NAME_MAP.put("shape_stamp", "Stamp Badge");
        SHAPE_NAME_MAP.put("shape_teardrop", "Teardrop");
        SHAPE_NAME_MAP.put("shape_triangle", "Triangle");
        SHAPE_NAME_MAP.put("shape_wideline", "Wide Line");
        SHAPE_NAME_MAP.put("shape_line", "Line");
        SHAPE_NAME_MAP.put("shape_quad", "Quadrilateral");
        SHAPE_NAME_MAP.put("shape_heart", "Heart");
        SHAPE_NAME_MAP.put("shape_shield", "Shield");
        SHAPE_NAME_MAP.put("shape_drop", "Drop");
        SHAPE_NAME_MAP.put("shape_lightning", "Lightning");
        SHAPE_NAME_MAP.put("shape_cloud", "Cloud");
        SHAPE_NAME_MAP.put("shape_diamond", "Diamond");
    }

    private static final Map<String, String> PARAM_LABEL_MAP = new LinkedHashMap<>();
    static {
        PARAM_LABEL_MAP.put("effect_param_num_points", "Points");
        PARAM_LABEL_MAP.put("effect_param_num_sides", "Sides");
        PARAM_LABEL_MAP.put("effect_param_radius_out", "Outer Radius");
        PARAM_LABEL_MAP.put("effect_param_radius_in", "Inner Radius");
        PARAM_LABEL_MAP.put("effect_param_radius", "Radius");
        PARAM_LABEL_MAP.put("effect_param_angle", "Angle");
        PARAM_LABEL_MAP.put("effect_param_start", "Start Angle");
        PARAM_LABEL_MAP.put("effect_param_end", "End Angle");
        PARAM_LABEL_MAP.put("effect_param_size", "Size");
        PARAM_LABEL_MAP.put("effect_param_tail_width", "Tail Width");
        PARAM_LABEL_MAP.put("effect_param_head_width", "Head Width");
        PARAM_LABEL_MAP.put("effect_param_head_length", "Head Length");
        PARAM_LABEL_MAP.put("effect_param_width", "Width");
        PARAM_LABEL_MAP.put("effect_param_height", "Height");
        PARAM_LABEL_MAP.put("effect_param_thickness", "Thickness");
        PARAM_LABEL_MAP.put("effect_param_depth", "Depth");
        PARAM_LABEL_MAP.put("effect_param_teeth", "Teeth");
        PARAM_LABEL_MAP.put("effect_param_petals", "Petals");
        PARAM_LABEL_MAP.put("effect_param_closed", "Closed");
        PARAM_LABEL_MAP.put("effect_param_tail", "Tail");
        PARAM_LABEL_MAP.put("effect_param_squeeze", "Squeeze");
        PARAM_LABEL_MAP.put("effect_param_perfsize", "Perforation Size");
        PARAM_LABEL_MAP.put("effect_param_spacing", "Spacing");
        PARAM_LABEL_MAP.put("effect_param_stem", "Stem Size");
        PARAM_LABEL_MAP.put("effect_param_point_1", "Point 1");
        PARAM_LABEL_MAP.put("effect_param_point_2", "Point 2");
        PARAM_LABEL_MAP.put("effect_param_point_3", "Point 3");
        PARAM_LABEL_MAP.put("effect_param_point_4", "Point 4");
        PARAM_LABEL_MAP.put("effect_param_point_5", "Point 5");
    }

    public static String humanizeShapeName(String raw, String fileName) {
        if (raw != null && !raw.isEmpty()) {
            String key = raw.replace("@string/", "").replace("@am:string/", "").trim();
            if (SHAPE_NAME_MAP.containsKey(key)) {
                return SHAPE_NAME_MAP.get(key);
            }
        }
        String clean = fileName.replace(".xml", "").replace("-", " ").replace("_", " ");
        return capitalizeWords(clean);
    }

    public static String humanizeLabel(String raw, String fallbackId) {
        if (raw != null && !raw.isEmpty()) {
            String key = raw.replace("@string/", "").replace("@am:string/", "").trim();
            if (PARAM_LABEL_MAP.containsKey(key)) {
                return PARAM_LABEL_MAP.get(key);
            }
            if (!raw.startsWith("@am:") && !raw.startsWith("@string/")) {
                return raw;
            }
        }
        return capitalizeWords(fallbackId.replace("_", " ").replace("-", " "));
    }

    private static String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-' || c == '_') {
                sb.append(' ');
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
