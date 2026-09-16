package glab.pixeleditor.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipCompressService extends Service {

    public static volatile boolean isRunning = false;
    public static volatile int currentProgress = 0;
    public static volatile String currentFileName = "";
    public static volatile String currentSizeText = "";
    public static volatile String currentItemText = "";

    public static final String ACTION_EXTRACT_ASSETS = "glab.pixeleditor.ACTION_EXTRACT_ASSETS";
    public static final String EXTRA_ZIP_PATH = "zipPath";
    public static final String EXTRA_DEST_PATH = "destPath";
    public static final String BROADCAST_ACTION = "ZIP_PROGRESS_UPDATE";

    public static final String PREF_EXTRACTION_COMPLETED = "assets_extraction_completed";
    public static final String PREFS_NAME = "PixelEditorPrefs";

    private static final String CHANNEL_ID = "ZipExtractChannel";
    private static final String TAG = "ZipCompressService";

    public static boolean isAssetsExtracted(Context context) {
        if (context == null) return false;
        File fontsDir = new File(context.getFilesDir(), "fonts");
        File listFile = new File(fontsDir, "list");
        boolean pref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_EXTRACTION_COMPLETED, false);
        return pref && fontsDir.exists() && (listFile.exists() || (fontsDir.list() != null && fontsDir.list().length > 0));
    }

    public static void startAssetExtraction(Context context) {
        Intent intent = new Intent(context, ZipCompressService.class);
        intent.setAction(ACTION_EXTRACT_ASSETS);
        try {
            context.startService(intent);
        } catch (Exception e) {
            Log.w(TAG, "startService exception: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        currentProgress = 0;
        currentFileName = "Preparing resources...";
        currentSizeText = "";
        currentItemText = "";

        new Thread(() -> {
            try {
                if (intent != null && ACTION_EXTRACT_ASSETS.equals(intent.getAction())) {
                    extractAssetsZipResources();
                } else if (intent != null && intent.getStringExtra(EXTRA_ZIP_PATH) != null) {
                    String zipPath = intent.getStringExtra(EXTRA_ZIP_PATH);
                    String destPath = intent.getStringExtra(EXTRA_DEST_PATH);
                    if (destPath == null) destPath = getFilesDir().getAbsolutePath();
                    extractZipFile(new File(zipPath), new File(destPath), 0, 100);
                } else {
                    extractAssetsZipResources();
                }

                // Mark extraction completed in preferences
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_EXTRACTION_COMPLETED, true)
                        .apply();

                currentProgress = 100;
                isRunning = false;
                sendCompletionBroadcast();

                try {
                    stopForeground(true);
                    NotificationManagerCompat.from(ZipCompressService.this).cancel(1001);
                } catch (Exception ignored) {}
                stopSelf();
            } catch (Exception e) {
                Log.e(TAG, "Extraction failed", e);
                isRunning = false;
                sendErrorBroadcast(e.getMessage());
                try {
                    stopForeground(true);
                    NotificationManagerCompat.from(ZipCompressService.this).cancel(1001);
                } catch (Exception ignored) {}
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void extractAssetsZipResources() throws Exception {
        File filesDir = getFilesDir();
        File fontsDest = new File(filesDir, "fonts");
        if (!fontsDest.exists()) fontsDest.mkdirs();

        File svgDest = new File(filesDir, "svg");
        if (!svgDest.exists()) svgDest.mkdirs();

        // 1. Extract fonts.zip (0% - 60%)
        currentFileName = "Extracting typography fonts...";
        try (InputStream is = getAssets().open("fonts.zip")) {
            extractZipStream(is, filesDir, 0, 60);
        } catch (Exception e) {
            Log.w(TAG, "fonts.zip extraction warning: " + e.getMessage());
        }

        // 2. Extract svg.zip (60% - 100%) - extracts into filesDir/svg/...
        currentFileName = "Extracting vector shapes...";
        try (InputStream is = getAssets().open("svg.zip")) {
            extractZipStream(is, filesDir, 60, 100);
        } catch (Exception e) {
            Log.w(TAG, "svg.zip extraction warning: " + e.getMessage());
        }

        // Flatten any nested filesDir/svg/svg from earlier versions
        flattenNestedSvgDir(filesDir);
    }

    private void flattenNestedSvgDir(File filesDir) {
        try {
            File doubleNested = new File(new File(filesDir, "svg"), "svg");
            if (doubleNested.exists() && doubleNested.isDirectory()) {
                File svgDir = new File(filesDir, "svg");
                File[] subs = doubleNested.listFiles();
                if (subs != null) {
                    for (File sub : subs) {
                        File target = new File(svgDir, sub.getName());
                        if (!target.exists()) {
                            sub.renameTo(target);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void extractZipFile(File zipFile, File destDir, int startPct, int endPct) throws Exception {
        try (InputStream is = new FileInputStream(zipFile)) {
            extractZipStream(is, destDir, startPct, endPct);
        }
    }

    private void extractZipStream(InputStream inputStream, File destDir, int startPct, int endPct) throws Exception {
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(inputStream))) {
            ZipEntry entry;
            byte[] buffer = new byte[16384];
            int count = 0;
            long lastNotificationTime = 0;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                File targetFile = new File(destDir, entryName);

                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(targetFile))) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
                count++;

                currentFileName = targetFile.getName();
                currentItemText = "Extracted " + count + " assets";

                int range = endPct - startPct;
                int progress = Math.min(endPct, startPct + Math.min(range, count % (range + 1)));
                currentProgress = progress;

                long now = System.currentTimeMillis();
                if (now - lastNotificationTime > 150) {
                    sendProgressBroadcast(progress, targetFile.getName(), 0, 0, count, count);
                    try {
                        NotificationManagerCompat.from(this).notify(1001, getNotification("Extracting " + targetFile.getName() + " (" + progress + "%)"));
                    } catch (Exception ignored) {}
                    lastNotificationTime = now;
                }
            }
        }
    }

    private void sendProgressBroadcast(int progress, String fileName, long processedSize, long totalSize, int count, int total) {
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.setPackage(getPackageName());
        intent.putExtra("progress", progress);
        intent.putExtra("fileName", fileName);
        intent.putExtra("sizeText", formatSize(processedSize) + " / " + formatSize(totalSize));
        intent.putExtra("itemText", count + " items");
        sendBroadcast(intent);
    }

    private void sendCompletionBroadcast() {
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.setPackage(getPackageName());
        intent.putExtra("progress", 100);
        intent.putExtra("fileName", "Completed");
        intent.putExtra("sizeText", "Done");
        intent.putExtra("itemText", "All assets ready");
        sendBroadcast(intent);
    }

    private void sendErrorBroadcast(String errorMsg) {
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.setPackage(getPackageName());
        intent.putExtra("error", errorMsg);
        sendBroadcast(intent);
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "";
        double size = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int i = 0;
        while (size >= 1024 && i < units.length - 1) {
            size /= 1024;
            i++;
        }
        return String.format("%.1f %s", size, units[i]);
    }

    private Notification getNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Pixel Editor Resources")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Asset Extraction",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Extracting initial typography fonts and SVG vector assets");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try {
            stopForeground(true);
            NotificationManagerCompat.from(this).cancel(1001);
        } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}