package glab.pixeleditor.svg;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SvgIconItem {

    private final String name;
    private final String category; // "Filled", "Outline", "General"
    private final String filePath; // Path on disk if extracted
    private final String zipEntryName; // Entry name in assets/svg.zip if reading on demand
    private final boolean isOutline;
    private String cachedSvgContent;

    public SvgIconItem(String name, String category, String filePath, String zipEntryName, boolean isOutline) {
        this.name = name;
        this.category = category;
        this.filePath = filePath;
        this.zipEntryName = zipEntryName;
        this.isOutline = isOutline;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getZipEntryName() {
        return zipEntryName;
    }

    public boolean isOutline() {
        return isOutline;
    }

    public synchronized String getSvgContent(Context context) {
        if (cachedSvgContent != null && !cachedSvgContent.isEmpty()) {
            return cachedSvgContent;
        }

        // 1. Try reading from disk file
        if (filePath != null) {
            File f = new File(filePath);
            if (f.exists() && f.isFile() && f.length() > 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    cachedSvgContent = sb.toString();
                    return cachedSvgContent;
                } catch (Throwable ignored) {}
            }
        }

        // 2. Try reading from assets/svg.zip on demand
        if (context != null && zipEntryName != null) {
            try (InputStream is = context.getAssets().open("svg.zip");
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (zipEntryName.equalsIgnoreCase(entry.getName())) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        cachedSvgContent = sb.toString();
                        return cachedSvgContent;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return "";
    }
}
