package glab.pixeleditor.svg;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.LruCache;

import androidx.core.graphics.PathParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SvgHelper {

    private static final Pattern PATH_DATA_PATTERN = Pattern.compile("d=\"([^\"]+)\"");
    private static final Pattern VIEWBOX_PATTERN = Pattern.compile("viewBox=\"([^\"]+)\"");
    private static final LruCache<String, Bitmap> sThumbnailCache = new LruCache<>(150);

    /**
     * Parses SVG XML content and builds a scaled android.graphics.Path fitted inside targetBounds.
     */
    public static Path createPathFromSvgContent(String svgContent, RectF targetBounds) {
        if (svgContent == null || svgContent.trim().isEmpty()) {
            return null;
        }

        Path combined = new Path();
        Matcher matcher = PATH_DATA_PATTERN.matcher(svgContent);
        boolean foundPath = false;

        while (matcher.find()) {
            String pathData = matcher.group(1);
            if (pathData != null && !pathData.trim().isEmpty()) {
                try {
                    Path p = PathParser.createPathFromPathData(pathData);
                    if (p != null) {
                        combined.addPath(p);
                        foundPath = true;
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (!foundPath) {
            return null;
        }

        // Determine source viewBox (default 0 0 24 24)
        float vbW = 24f;
        float vbH = 24f;
        Matcher vbMatcher = VIEWBOX_PATTERN.matcher(svgContent);
        if (vbMatcher.find()) {
            String vbStr = vbMatcher.group(1);
            if (vbStr != null) {
                String[] parts = vbStr.trim().split("[ ,]+");
                if (parts.length == 4) {
                    try {
                        vbW = Float.parseFloat(parts[2]);
                        vbH = Float.parseFloat(parts[3]);
                    } catch (Throwable ignored) {}
                }
            }
        }

        if (targetBounds != null && targetBounds.width() > 0 && targetBounds.height() > 0) {
            RectF srcRect = new RectF(0, 0, Math.max(1f, vbW), Math.max(1f, vbH));
            Matrix matrix = new Matrix();
            matrix.setRectToRect(srcRect, targetBounds, Matrix.ScaleToFit.CENTER);
            combined.transform(matrix);
        }

        return combined;
    }

    /**
     * Renders a crisp thumbnail Bitmap for UI display with caching.
     */
    public static Bitmap renderSvgThumbnail(String cacheKey, String svgContent, int sizePx, int color, boolean isOutline) {
        if (cacheKey != null) {
            Bitmap cached = sThumbnailCache.get(cacheKey + "_" + sizePx + "_" + color);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
        }

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        float pad = sizePx * 0.16f;
        RectF bounds = new RectF(pad, pad, sizePx - pad, sizePx - pad);

        Path path = createPathFromSvgContent(svgContent, bounds);
        if (path != null) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            if (isOutline) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1.5f, sizePx * 0.07f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
            } else {
                paint.setStyle(Paint.Style.FILL);
            }
            canvas.drawPath(path, paint);
        }

        if (cacheKey != null) {
            sThumbnailCache.put(cacheKey + "_" + sizePx + "_" + color, bmp);
        }
        return bmp;
    }
}
