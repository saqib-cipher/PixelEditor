package glab.pixeleditor.svg;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import glab.pixeleditor.model.CanvasLayer;
import glab.pixeleditor.model.ShapeLayer;
import glab.pixeleditor.shape.ShapeDefinition;

public class SvgIconManager {

    private static final String TAG = "SvgIconManager";
    public static final String CATEGORY_ALL = "All Icons";
    public static final String CATEGORY_FILLED = "Filled";
    public static final String CATEGORY_OUTLINE = "Outline";

    private static List<SvgIconItem> sCachedIcons = null;
    private static List<String> sCachedCategories = null;

    public static synchronized void clearCache() {
        sCachedIcons = null;
        sCachedCategories = null;
    }

    public static synchronized List<String> getCategories(Context context) {
        if (sCachedCategories != null && !sCachedCategories.isEmpty()) {
            return new ArrayList<>(sCachedCategories);
        }
        List<String> categories = new ArrayList<>();
        categories.add(CATEGORY_ALL);
        categories.add(CATEGORY_FILLED);
        categories.add(CATEGORY_OUTLINE);
        sCachedCategories = categories;
        return categories;
    }

    public static synchronized List<SvgIconItem> getAllIcons(Context context) {
        if (sCachedIcons != null && !sCachedIcons.isEmpty()) {
            return sCachedIcons;
        }

        List<SvgIconItem> icons = new ArrayList<>();
        if (context == null) return icons;

        File filesDir = context.getFilesDir();
        File svgDir = new File(filesDir, "svg");

        // 1. Check disk extracted SVG folders (svg/filled, svg/outline, or svg/svg/...)
        boolean foundOnDisk = false;
        if (svgDir.exists() && svgDir.isDirectory()) {
            // Check direct subfolders
            foundOnDisk = scanSvgDirectory(svgDir, icons);

            // Also check svg/svg/ if nested
            File nestedSvg = new File(svgDir, "svg");
            if (nestedSvg.exists() && nestedSvg.isDirectory()) {
                scanSvgDirectory(nestedSvg, icons);
                foundOnDisk = true;
            }
        }

        // 2. If nothing on disk yet, read entries directly from assets/svg.zip
        if (!foundOnDisk || icons.isEmpty()) {
            try (InputStream is = context.getAssets().open("svg.zip");
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (!entry.isDirectory() && name.toLowerCase().endsWith(".svg")) {
                        String cleanName = cleanSvgName(new File(name).getName());
                        boolean isOutline = name.toLowerCase().contains("outline");
                        String cat = isOutline ? CATEGORY_OUTLINE : CATEGORY_FILLED;
                        icons.add(new SvgIconItem(cleanName, cat, null, name, isOutline));
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed reading assets/svg.zip: " + t.getMessage());
            }
        }

        Collections.sort(icons, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        sCachedIcons = icons;
        return icons;
    }

    private static boolean scanSvgDirectory(File dir, List<SvgIconItem> outIcons) {
        File[] subs = dir.listFiles();
        if (subs == null || subs.length == 0) return false;

        boolean found = false;
        for (File sub : subs) {
            if (sub.isDirectory() && !"svg".equalsIgnoreCase(sub.getName())) {
                String folderName = sub.getName();
                boolean isOutline = folderName.equalsIgnoreCase("outline");
                String catName = isOutline ? CATEGORY_OUTLINE : CATEGORY_FILLED;

                File[] svgFiles = sub.listFiles((d, n) -> n.toLowerCase().endsWith(".svg"));
                if (svgFiles != null && svgFiles.length > 0) {
                    found = true;
                    for (File f : svgFiles) {
                        String displayName = cleanSvgName(f.getName());
                        outIcons.add(new SvgIconItem(displayName, catName, f.getAbsolutePath(), null, isOutline));
                    }
                }
            } else if (sub.isFile() && sub.getName().toLowerCase().endsWith(".svg")) {
                found = true;
                boolean isOutline = sub.getName().toLowerCase().contains("outline");
                outIcons.add(new SvgIconItem(cleanSvgName(sub.getName()), isOutline ? CATEGORY_OUTLINE : CATEGORY_FILLED, sub.getAbsolutePath(), null, isOutline));
            }
        }
        return found;
    }

    public static List<SvgIconItem> getIconsByCategory(Context context, String category) {
        List<SvgIconItem> all = getAllIcons(context);
        if (category == null || CATEGORY_ALL.equalsIgnoreCase(category)) {
            return all;
        }

        List<SvgIconItem> filtered = new ArrayList<>();
        for (SvgIconItem icon : all) {
            if (icon.getCategory().equalsIgnoreCase(category)) {
                filtered.add(icon);
            }
        }
        return filtered;
    }

    public static ShapeLayer createShapeLayerFromSvg(Context context, SvgIconItem item, float x, float y) {
        if (item == null) return null;
        String content = item.getSvgContent(context);

        ShapeDefinition def = new ShapeDefinition("svg_" + item.getName().toLowerCase().replace(' ', '_'), item.getName() + ".svg", item.getName());
        def.setSvg(true);
        def.setOutlineSvg(item.isOutline());
        def.setSvgContent(content);

        ShapeLayer layer = new ShapeLayer(item.getName(), x, y, 260, 260);
        layer.setShapeDefinition(def);

        if (item.isOutline()) {
            layer.setFillMode(ShapeLayer.FillMode.NONE);
            layer.setHasStroke(true);
            layer.setStrokeWidth(6f);
            layer.setStrokeColor(0xFF00E5BC);
        } else {
            layer.setFillMode(ShapeLayer.FillMode.SOLID);
            layer.setFillColor(0xFF00E5BC);
            layer.setHasStroke(false);
        }

        return layer;
    }

    private static String cleanSvgName(String fileName) {
        if (fileName == null) return "";
        String clean = fileName;
        if (clean.toLowerCase().endsWith(".svg")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        clean = clean.replace('_', ' ').replace('-', ' ');
        if (!clean.isEmpty()) {
            clean = Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
        }
        return clean.trim();
    }
}
