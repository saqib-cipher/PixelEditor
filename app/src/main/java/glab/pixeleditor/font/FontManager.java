package glab.pixeleditor.font;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.util.LruCache;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FontManager {

    private static final String PREFS_NAME = "PixelEditorFontPrefs";
    private static final String KEY_FAVORITES = "favorite_font_paths";
    public static final String CATEGORY_FAVORITES = "Favorites";
    public static final String CATEGORY_ALL = "All Fonts";

    private static final LruCache<String, Typeface> sTypefaceCache = new LruCache<>(100);
    private static List<FontItem> sCachedFonts = null;
    private static List<String> sCachedCategories = null;

    public static synchronized void clearCache() {
        sCachedFonts = null;
        sCachedCategories = null;
    }

    public static File getFontsDirectory(Context context) {
        if (context == null) return null;
        File fontsDir = new File(context.getFilesDir(), "fonts");
        if (!fontsDir.exists()) {
            fontsDir.mkdirs();
        }
        return fontsDir;
    }

    public static synchronized List<String> getCategories(Context context) {
        if (sCachedCategories != null && !sCachedCategories.isEmpty()) {
            return new ArrayList<>(sCachedCategories);
        }

        List<String> categories = new ArrayList<>();
        categories.add(CATEGORY_FAVORITES);

        try {
            File fontsDir = getFontsDirectory(context);
            if (fontsDir != null && fontsDir.exists()) {
                File listFile = new File(fontsDir, "list");
                if (listFile.exists() && listFile.isFile()) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(listFile), "UTF-8"))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append(";");
                        }
                        String[] parts = sb.toString().split(";");
                        for (String p : parts) {
                            String clean = p.trim();
                            if (!clean.isEmpty() && !categories.contains(clean)) {
                                categories.add(clean);
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                // Also check subfolders if list file was missing or incomplete
                File[] subs = fontsDir.listFiles(File::isDirectory);
                if (subs != null) {
                    for (File sub : subs) {
                        String subName = sub.getName();
                        if (!categories.contains(subName)) {
                            categories.add(subName);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (!categories.contains(CATEGORY_ALL)) {
            categories.add(CATEGORY_ALL);
        }
        sCachedCategories = categories;
        return new ArrayList<>(categories);
    }

    public static synchronized List<FontItem> getAllFonts(Context context) {
        if (sCachedFonts != null && !sCachedFonts.isEmpty()) {
            return sCachedFonts;
        }

        List<FontItem> fonts = new ArrayList<>();
        Set<String> favorites = getFavoritePaths(context);

        try {
            File fontsDir = getFontsDirectory(context);
            if (fontsDir != null && fontsDir.exists()) {
                // First check category subdirectories
                File[] dirs = fontsDir.listFiles(File::isDirectory);
                if (dirs != null) {
                    for (File catDir : dirs) {
                        String catName = catDir.getName();
                        File[] fontFiles = catDir.listFiles((dir, name) -> {
                            String lower = name.toLowerCase();
                            return lower.endsWith(".ttf") || lower.endsWith(".otf");
                        });

                        if (fontFiles != null) {
                            for (File ff : fontFiles) {
                                if (ff.isFile() && ff.length() > 1024) {
                                    String displayName = cleanFontName(ff.getName());
                                    boolean isFav = favorites.contains(ff.getAbsolutePath());
                                    fonts.add(new FontItem(displayName, catName, ff.getAbsolutePath(), isFav));
                                }
                            }
                        }
                    }
                }

                // Also root fonts in fonts directory
                File[] rootFontFiles = fontsDir.listFiles((dir, name) -> {
                    String lower = name.toLowerCase();
                    return lower.endsWith(".ttf") || lower.endsWith(".otf");
                });
                if (rootFontFiles != null) {
                    for (File ff : rootFontFiles) {
                        if (ff.isFile() && ff.length() > 1024) {
                            String displayName = cleanFontName(ff.getName());
                            boolean isFav = favorites.contains(ff.getAbsolutePath());
                            fonts.add(new FontItem(displayName, "General", ff.getAbsolutePath(), isFav));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // If no extracted fonts exist yet, provide standard typography system fonts
        if (fonts.isEmpty()) {
            fonts.add(new FontItem("Sans Serif", "Standard", "", false));
            fonts.add(new FontItem("Serif", "Standard", "", false));
            fonts.add(new FontItem("Monospace", "Standard", "", false));
            fonts.add(new FontItem("Casual", "Standard", "", false));
            fonts.add(new FontItem("Cursive", "Standard", "", false));
        }

        Collections.sort(fonts, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        sCachedFonts = fonts;
        return fonts;
    }

    public static List<FontItem> getFontsByCategory(Context context, String category) {
        List<FontItem> all = getAllFonts(context);
        if (category == null || CATEGORY_ALL.equalsIgnoreCase(category)) {
            return all;
        }

        if (CATEGORY_FAVORITES.equalsIgnoreCase(category)) {
            List<FontItem> favs = new ArrayList<>();
            Set<String> favPaths = getFavoritePaths(context);
            for (FontItem f : all) {
                if (favPaths.contains(f.getFilePath()) || f.isFavorite()) {
                    favs.add(f);
                }
            }
            return favs;
        }

        List<FontItem> filtered = new ArrayList<>();
        for (FontItem f : all) {
            if (f.getCategory().equalsIgnoreCase(category)) {
                filtered.add(f);
            }
        }
        return filtered.isEmpty() ? all : filtered;
    }

    public static Set<String> getFavoritePaths(Context context) {
        if (context == null) return Collections.emptySet();
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getStringSet(KEY_FAVORITES, new HashSet<>());
        } catch (Throwable ignored) {
            return Collections.emptySet();
        }
    }

    public static void setFontFavorite(Context context, FontItem item, boolean isFavorite) {
        if (context == null || item == null) return;
        try {
            item.setFavorite(isFavorite);
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> current = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
            if (isFavorite) {
                current.add(item.getFilePath());
            } else {
                current.remove(item.getFilePath());
            }
            prefs.edit().putStringSet(KEY_FAVORITES, current).apply();
        } catch (Throwable ignored) {}
    }

    public static Typeface getTypeface(String filePath, int style) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return Typeface.create(Typeface.SANS_SERIF, style);
        }

        String cacheKey = filePath + "_" + style;
        Typeface cached = sTypefaceCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            File f = new File(filePath);
            if (f.exists() && f.isFile() && f.length() > 1024) {
                Typeface base = Typeface.createFromFile(f);
                if (base != null) {
                    Typeface styled = (style == Typeface.NORMAL) ? base : Typeface.create(base, style);
                    if (styled != null) {
                        sTypefaceCache.put(cacheKey, styled);
                        return styled;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return Typeface.create(Typeface.SANS_SERIF, style);
    }

    private static String cleanFontName(String fileName) {
        if (fileName == null) return "";
        String clean = fileName;
        if (clean.toLowerCase().endsWith(".ttf") || clean.toLowerCase().endsWith(".otf")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        clean = clean.replace("%28", "(").replace("%29", ")").replace("%20", " ");
        clean = clean.replace('_', ' ').replace('-', ' ');
        return clean.trim();
    }
}
