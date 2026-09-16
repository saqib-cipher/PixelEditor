package glab.pixeleditor.model;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ProjectStorageManager {

    private static final String PREFS_NAME = "pixeleditor_prefs";
    private static final String KEY_PROJECTS_DIR = "projects_directory";
    private static final String KEY_FOLDER_SETUP_DONE = "folder_setup_done";
    private static final String INDEX_FILE_NAME = "projects_index.json";

    public static class ProjectItem {
        private String id;
        private String title;
        private String aspectRatio;
        private int width;
        private int height;
        private int fps;
        private int backgroundColor;
        private long fileSize;
        private long lastModified;
        private String thumbnailName;
        private boolean inTrash;
        private boolean isElement;

        public ProjectItem(String id, String title, String aspectRatio, int width, int height, int fps, int bg, long size, long lastModified, String thumb, boolean inTrash) {
            this(id, title, aspectRatio, width, height, fps, bg, size, lastModified, thumb, inTrash, false);
        }

        public ProjectItem(String id, String title, String aspectRatio, int width, int height, int fps, int bg, long size, long lastModified, String thumb, boolean inTrash, boolean isElement) {
            this.id = id;
            this.title = title;
            this.aspectRatio = aspectRatio;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.backgroundColor = bg;
            this.fileSize = size;
            this.lastModified = lastModified;
            this.thumbnailName = thumb;
            this.inTrash = inTrash;
            this.isElement = isElement;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAspectRatio() { return aspectRatio != null ? aspectRatio : "9:16"; }
        public int getWidth() { return width > 0 ? width : 1080; }
        public int getHeight() { return height > 0 ? height : 1920; }
        public int getFps() { return fps > 0 ? fps : 30; }
        public int getBackgroundColor() { return backgroundColor; }
        public long getFileSize() { return fileSize; }
        public long getLastModified() { return lastModified; }
        public void setLastModified(long lm) { this.lastModified = lm; }
        public String getThumbnailName() { return thumbnailName; }
        public void setThumbnailName(String name) { this.thumbnailName = name; }
        public boolean isInTrash() { return inTrash; }
        public void setInTrash(boolean inTrash) { this.inTrash = inTrash; }
        public boolean isElement() { return isElement; }
        public void setElement(boolean element) { this.isElement = element; }

        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
            return sdf.format(new Date(lastModified));
        }

        public String getFormattedSize() {
            if (fileSize < 1024) {
                return fileSize + "b";
            } else if (fileSize < 1024 * 1024) {
                return String.format(Locale.US, "%.1fKB", fileSize / 1024f);
            } else {
                return String.format(Locale.US, "%.1fMB", fileSize / (1024f * 1024f));
            }
        }

        public String getFormattedResolution() {
            if (height >= 2160 || width >= 2160) return "4K";
            if (height >= 1080 || width >= 1080) return "1080p";
            if (height >= 720 || width >= 720) return "720p";
            return "SD";
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("title", title);
                obj.put("aspectRatio", aspectRatio);
                obj.put("width", width);
                obj.put("height", height);
                obj.put("fps", fps);
                obj.put("backgroundColor", backgroundColor);
                obj.put("fileSize", fileSize);
                obj.put("lastModified", lastModified);
                obj.put("thumbnailName", thumbnailName);
                obj.put("inTrash", inTrash);
                obj.put("isElement", isElement);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static ProjectItem fromJson(JSONObject obj) {
            return new ProjectItem(
                    obj.optString("id", UUID.randomUUID().toString()),
                    obj.optString("title", "Untitled Project"),
                    obj.optString("aspectRatio", "9:16"),
                    obj.optInt("width", 1080),
                    obj.optInt("height", 1920),
                    obj.optInt("fps", 30),
                    obj.optInt("backgroundColor", 0xFFD8DCE3),
                    obj.optLong("fileSize", 1024),
                    obj.optLong("lastModified", System.currentTimeMillis()),
                    obj.optString("thumbnailName", ""),
                    obj.optBoolean("inTrash", false),
                    obj.optBoolean("isElement", false)
            );
        }
    }

    public static boolean isFolderSetupDone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_FOLDER_SETUP_DONE, false);
    }

    public static void setFolderSetupDone(Context context, boolean done) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_FOLDER_SETUP_DONE, done).apply();
    }

    public static String getProjectsFolderPath(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_PROJECTS_DIR, null);
        if (saved != null && !saved.isEmpty()) {
            return saved;
        }

        // Default: internal app documents or external files directory
        File defaultDir = new File(context.getFilesDir(), "PixelEditor/Projects");
        if (!defaultDir.exists()) {
            defaultDir.mkdirs();
        }
        return defaultDir.getAbsolutePath();
    }

    public static void setProjectsFolderPath(Context context, String path) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_PROJECTS_DIR, path)
                .putBoolean(KEY_FOLDER_SETUP_DONE, true)
                .apply();

        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public static File getProjectsDir(Context context) {
        File dir = new File(getProjectsFolderPath(context));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Loads all projects. If directory is completely empty, seeds initial projects from screenshots.
     */
    public static List<ProjectItem> loadAllProjects(Context context, boolean onlyTrash) {
        File dir = getProjectsDir(context);
        File indexFile = new File(dir, INDEX_FILE_NAME);

        List<ProjectItem> all = new ArrayList<>();

        if (!indexFile.exists()) {
            all = new ArrayList<>();
            saveIndex(context, all);
        } else {
            try (InputStream is = new FileInputStream(indexFile)) {
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                String json = new String(buffer, "UTF-8");
                JSONArray array = new JSONArray(json);
                boolean cleaned = false;
                for (int i = 0; i < array.length(); i++) {
                    ProjectItem item = ProjectItem.fromJson(array.getJSONObject(i));
                    if (item != null) {
                        File projectFile = new File(dir, item.getId() + ".json");
                        // Purge legacy sample projects that have no backing .json file
                        if (!projectFile.exists() && (item.getFileSize() == 743 || item.getFileSize() == 4608 || item.getFileSize() == 2764 || item.getFileSize() == 1843)) {
                            cleaned = true;
                            if (item.getThumbnailName() != null) {
                                try { new File(dir, item.getThumbnailName()).delete(); } catch (Exception ignored) {}
                            }
                            continue;
                        }
                        all.add(item);
                    }
                }
                if (cleaned) {
                    saveIndex(context, all);
                }
            } catch (Exception e) {
                System.err.println("Error loading projects index: " + e.getMessage());
            }
        }

        // Filter based on trash status
        List<ProjectItem> filtered = new ArrayList<>();
        for (ProjectItem p : all) {
            if (p.isInTrash() == onlyTrash) {
                filtered.add(p);
            }
        }

        // Sort by last modified descending
        Collections.sort(filtered, (a, b) -> Long.compare(b.getLastModified(), a.getLastModified()));
        return filtered;
    }

    public static synchronized void saveIndex(Context context, List<ProjectItem> projects) {
        File dir = getProjectsDir(context);
        File indexFile = new File(dir, INDEX_FILE_NAME);
        try {
            JSONArray array = new JSONArray();
            for (ProjectItem p : projects) {
                array.put(p.toJson());
            }
            try (FileOutputStream fos = new FileOutputStream(indexFile)) {
                fos.write(array.toString(2).getBytes("UTF-8"));
            }
        } catch (Exception e) {
            System.err.println("Error saving projects index: " + e.getMessage());
        }
    }

    public static ProjectItem getProjectById(Context context, String projectId) {
        if (projectId == null) return null;
        List<ProjectItem> all = loadAllRaw(context);
        for (ProjectItem item : all) {
            if (projectId.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    public static void addOrUpdateProject(Context context, ProjectItem item) {
        List<ProjectItem> all = loadAllRaw(context);
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(item.getId())) {
                all.set(i, item);
                found = true;
                break;
            }
        }
        if (!found) {
            all.add(0, item);
        }
        saveIndex(context, all);
    }

    public static void moveToTrash(Context context, String projectId) {
        List<ProjectItem> all = loadAllRaw(context);
        for (ProjectItem item : all) {
            if (item.getId().equals(projectId)) {
                item.setInTrash(true);
                item.setLastModified(System.currentTimeMillis());
                break;
            }
        }
        saveIndex(context, all);
    }

    public static void restoreFromTrash(Context context, String projectId) {
        List<ProjectItem> all = loadAllRaw(context);
        for (ProjectItem item : all) {
            if (item.getId().equals(projectId)) {
                item.setInTrash(false);
                item.setLastModified(System.currentTimeMillis());
                break;
            }
        }
        saveIndex(context, all);
    }

    public static void deletePermanently(Context context, String projectId) {
        List<ProjectItem> all = loadAllRaw(context);
        ProjectItem toRemove = null;
        for (ProjectItem item : all) {
            if (item.getId().equals(projectId)) {
                toRemove = item;
                break;
            }
        }
        if (toRemove != null) {
            all.remove(toRemove);
            if (toRemove.getThumbnailName() != null && !toRemove.getThumbnailName().isEmpty()) {
                File thumbFile = new File(getProjectsDir(context), toRemove.getThumbnailName());
                if (thumbFile.exists()) thumbFile.delete();
            }
            File pFile = new File(getProjectsDir(context), "project_" + projectId + ".json");
            if (pFile.exists()) pFile.delete();
            saveIndex(context, all);
        }
    }

    public static void duplicateProject(Context context, ProjectItem source) {
        String newId = UUID.randomUUID().toString();
        String newTitle = source.getTitle() + " (Copy)";
        String newThumb = "thumb_" + newId + ".png";

        // Copy thumbnail file if exists
        File dir = getProjectsDir(context);
        if (source.getThumbnailName() != null && !source.getThumbnailName().isEmpty()) {
            File srcThumb = new File(dir, source.getThumbnailName());
            if (srcThumb.exists()) {
                try (FileInputStream fis = new FileInputStream(srcThumb);
                     FileOutputStream fos = new FileOutputStream(new File(dir, newThumb))) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Copy project JSON file if exists
        File srcProjFile = new File(dir, "project_" + source.getId() + ".json");
        if (srcProjFile.exists()) {
            try (FileInputStream fis = new FileInputStream(srcProjFile);
                 FileOutputStream fos = new FileOutputStream(new File(dir, "project_" + newId + ".json"))) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = fis.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            } catch (Exception ignored) {}
        }

        ProjectItem copy = new ProjectItem(
                newId,
                newTitle,
                source.getAspectRatio(),
                source.getWidth(),
                source.getHeight(),
                source.getFps(),
                source.getBackgroundColor(),
                source.getFileSize(),
                System.currentTimeMillis(),
                newThumb,
                false
        );

        List<ProjectItem> all = loadAllRaw(context);
        all.add(0, copy);
        saveIndex(context, all);
    }

    public static synchronized void saveProjectContent(Context context, String projectId, EditorProject project) {
        if (context == null || projectId == null || project == null) return;
        File dir = getProjectsDir(context);
        File projectFile = new File(dir, "project_" + projectId + ".json");
        try {
            JSONObject json = project.toJson(context);
            try (FileOutputStream fos = new FileOutputStream(projectFile)) {
                fos.write(json.toString(2).getBytes("UTF-8"));
            }
        } catch (Exception e) {
            System.err.println("Error saving project content: " + e.getMessage());
        }
    }

    public static synchronized EditorProject loadProjectContent(Context context, String projectId) {
        if (context == null || projectId == null) return null;
        File dir = getProjectsDir(context);
        File projectFile = new File(dir, "project_" + projectId + ".json");
        if (!projectFile.exists()) return null;

        try (FileInputStream fis = new FileInputStream(projectFile)) {
            int size = fis.available();
            byte[] buffer = new byte[size];
            fis.read(buffer);
            String jsonStr = new String(buffer, "UTF-8");
            JSONObject json = new JSONObject(jsonStr);
            return EditorProject.fromJson(context, json);
        } catch (Exception e) {
            System.err.println("Error loading project content: " + e.getMessage());
            return null;
        }
    }

    public static void shareProject(Context context, ProjectItem item) {
        try {
            File dir = getProjectsDir(context);
            File thumbFile = (item.getThumbnailName() != null && !item.getThumbnailName().isEmpty())
                    ? new File(dir, item.getThumbnailName()) : null;

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            if (thumbFile != null && thumbFile.exists()) {
                Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", thumbFile);
                shareIntent.setType("image/png");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                shareIntent.setType("text/plain");
            }

            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "PixelEditor Project: " + item.getTitle());
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out my project \"" + item.getTitle() + "\" created with PixelEditor (" + item.getAspectRatio() + " • " + item.getFormattedResolution() + ")");

            Intent chooser = Intent.createChooser(shareIntent, "Share Project");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            // Fallback plain text share
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "PixelEditor Project: " + item.getTitle() + " (" + item.getAspectRatio() + ")");
            Intent chooser = Intent.createChooser(shareIntent, "Share Project");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        }
    }

    public static Bitmap loadThumbnail(Context context, ProjectItem item) {
        if (item == null || item.getThumbnailName() == null || item.getThumbnailName().isEmpty()) {
            return null;
        }
        File thumbFile = new File(getProjectsDir(context), item.getThumbnailName());
        if (thumbFile.exists()) {
            return BitmapFactory.decodeFile(thumbFile.getAbsolutePath());
        }
        return null;
    }

    public static void saveThumbnail(Context context, String thumbName, Bitmap bitmap) {
        if (bitmap == null || thumbName == null) return;
        try {
            File dir = getProjectsDir(context);
            File file = new File(dir, thumbName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            }
        } catch (Exception ignored) {}
    }

    private static List<ProjectItem> loadAllRaw(Context context) {
        File dir = getProjectsDir(context);
        File indexFile = new File(dir, INDEX_FILE_NAME);
        List<ProjectItem> all = new ArrayList<>();
        if (indexFile.exists()) {
            try (InputStream is = new FileInputStream(indexFile)) {
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                String json = new String(buffer, "UTF-8");
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    all.add(ProjectItem.fromJson(array.getJSONObject(i)));
                }
            } catch (Exception ignored) {}
        }
        return all;
    }
}
