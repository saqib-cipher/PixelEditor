package glab.pixeleditor.shape;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import glab.pixeleditor.effect.EffectParam;
import glab.pixeleditor.model.ShapeLayer;

public class ShapeGeometryHelper {

    /**
     * Builds a Path for the given ShapeDefinition or ShapeType scaled inside the specified RectF bounds.
     */
    public static Path buildPath(ShapeDefinition def, RectF rect, ShapeLayer.ShapeType fallbackType) {
        if (def != null) {
            String file = def.getFileName() != null ? def.getFileName().toLowerCase() : "";
            String id = def.getId() != null ? def.getId().toLowerCase() : "";

            if (file.contains("star") || id.contains("star")) {
                return buildStarPath(def, rect);
            } else if (file.contains("poly") || id.contains("poly")) {
                return buildPolyPath(def, rect);
            } else if (file.contains("penta") || id.contains("penta")) {
                return buildPentaPath(def, rect);
            } else if (file.contains("arrow") || id.contains("arrow")) {
                return buildArrowPath(def, rect);
            } else if (file.contains("plus") || id.contains("plus")) {
                return buildPlusPath(def, rect);
            } else if (file.contains("moon") || id.contains("moon")) {
                return buildMoonPath(def, rect);
            } else if (file.contains("pie") || id.contains("pie")) {
                return buildPiePath(def, rect);
            } else if (file.contains("arc") || id.contains("arc")) {
                return buildArcPath(def, rect);
            } else if (file.contains("multifoil") || id.contains("multifoil")) {
                return buildMultifoilPath(def, rect);
            } else if (file.contains("stamp") || id.contains("stamp")) {
                return buildStampPath(def, rect);
            } else if (file.contains("callout") || id.contains("callout")) {
                return buildCalloutPath(def, rect);
            } else if (file.contains("teardrop") || id.contains("teardrop") || file.contains("drop")) {
                return buildTeardropPath(def, rect);
            } else if (file.contains("triangle") || id.contains("triangle")) {
                return buildTrianglePath(def, rect);
            } else if (file.contains("wideline") || file.contains("line") || id.contains("line")) {
                return buildWidelinePath(def, rect);
            } else if (file.contains("circle") || id.contains("circle")) {
                return buildCirclePath(def, rect);
            } else if (file.contains("roundrect") || id.contains("roundrect")) {
                return buildRoundRectPath(def, rect);
            } else if (file.contains("rect") || file.contains("quad") || id.contains("rect") || id.contains("quad")) {
                return buildRectPath(def, rect);
            }
        }

        // Fallback using enum ShapeType
        if (fallbackType != null) {
            switch (fallbackType) {
                case STAR:
                    return buildStarPath(def, rect);
                case TRIANGLE:
                    return buildTrianglePath(def, rect);
                case HEART:
                    return buildHeartPath(rect);
                case DIAMOND:
                    return buildDiamondPath(rect);
                case SHIELD:
                    return buildShieldPath(rect);
                case DROP:
                    return buildTeardropPath(def, rect);
                case CIRCLE:
                    return buildCirclePath(def, rect);
                case ROUNDED_RECT:
                default:
                    return buildRoundRectPath(def, rect);
            }
        }

        return buildRoundRectPath(def, rect);
    }

    private static Path buildStarPath(ShapeDefinition def, RectF rect) {
        float pointCount = getParamFloat(def, "pointCount", 5f);
        float outerRadius = getParamFloat(def, "outerRadius", 100f);
        float innerRadius = getParamFloat(def, "innerRadius", 50f);
        float offsetAngle = getParamFloat(def, "offsetAngle", 0f);

        int count = Math.max(3, Math.round(pointCount));
        float radsPerPoint = (float) (2.0 * Math.PI / count);
        float radsPerHalfPoint = radsPerPoint / 2.0f;
        float offsetRad = (float) Math.toRadians(offsetAngle);
        float scale = (Math.min(rect.width(), rect.height()) / 2f) / 100f;
        float cx = rect.centerX();
        float cy = rect.centerY();

        Path path = new Path();
        for (int i = 0; i < count; i++) {
            float angle = radsPerPoint * i + (float) Math.PI / 2.0f + offsetRad;
            float x1 = cx + (float) (outerRadius * Math.cos(angle)) * scale;
            float y1 = cy - (float) (outerRadius * Math.sin(angle)) * scale;
            if (i == 0) path.moveTo(x1, y1);
            else path.lineTo(x1, y1);

            angle += radsPerHalfPoint;
            float x2 = cx + (float) (innerRadius * Math.cos(angle)) * scale;
            float y2 = cy - (float) (innerRadius * Math.sin(angle)) * scale;
            path.lineTo(x2, y2);
        }
        path.close();
        return path;
    }

    private static Path buildPolyPath(ShapeDefinition def, RectF rect) {
        float sideCount = getParamFloat(def, "sideCount", getParamFloat(def, "numSides", 6f));
        float radius = getParamFloat(def, "radius", 100f);
        float offsetAngle = getParamFloat(def, "offsetAngle", 0f);

        int sides = Math.max(3, Math.round(sideCount));
        float radsPerSide = (float) (2.0 * Math.PI / sides);
        float offsetRad = (float) Math.toRadians(offsetAngle);
        float scale = (Math.min(rect.width(), rect.height()) / 2f) / 100f;
        float cx = rect.centerX();
        float cy = rect.centerY();

        Path path = new Path();
        for (int i = 0; i < sides; i++) {
            float angle = radsPerSide * i + (float) Math.PI / 2.0f + offsetRad;
            float px = cx + (float) (radius * Math.cos(angle)) * scale;
            float py = cy - (float) (radius * Math.sin(angle)) * scale;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.close();
        return path;
    }

    private static Path buildPentaPath(ShapeDefinition def, RectF rect) {
        float radius = getParamFloat(def, "radius", 100f);
        float offsetAngle = getParamFloat(def, "offsetAngle", 0f);
        float radsPerSide = (float) (2.0 * Math.PI / 5.0);
        float offsetRad = (float) Math.toRadians(offsetAngle);
        float scale = (Math.min(rect.width(), rect.height()) / 2f) / 100f;
        float cx = rect.centerX();
        float cy = rect.centerY();

        Path path = new Path();
        for (int i = 0; i < 5; i++) {
            float angle = radsPerSide * i + (float) Math.PI / 2.0f + offsetRad;
            float px = cx + (float) (radius * Math.cos(angle)) * scale;
            float py = cy - (float) (radius * Math.sin(angle)) * scale;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.close();
        return path;
    }

    private static Path buildArrowPath(ShapeDefinition def, RectF rect) {
        float lineWidth = getParamFloat(def, "lineWidth", 40f);
        float headWidth = getParamFloat(def, "headWidth", 120f);
        float headLength = getParamFloat(def, "headLength", 130f);

        float w = rect.width();
        float h = rect.height();
        float scale = Math.min(w / 300f, h / 300f);
        float cy = rect.centerY();
        float l = rect.left;
        float r = rect.right;
        float lw = Math.min(headWidth, lineWidth) * scale / 2f;
        float hw = headWidth * scale / 2f;
        float hl = Math.min(w * 0.75f, headLength * scale);

        Path path = new Path();
        path.moveTo(l, cy - lw);
        path.lineTo(r - hl, cy - lw);
        path.lineTo(r - hl, cy - hw);
        path.lineTo(r, cy);
        path.lineTo(r - hl, cy + hw);
        path.lineTo(r - hl, cy + lw);
        path.lineTo(l, cy + lw);
        path.close();
        return path;
    }

    private static Path buildPlusPath(ShapeDefinition def, RectF rect) {
        float sx = getParamFloat(def, "size_x", 100f);
        float sy = getParamFloat(def, "size_y", 100f);
        float stemSize = getParamFloat(def, "stemSize", 50f);

        float scaleX = (rect.width() / 2f) / 100f;
        float scaleY = (rect.height() / 2f) / 100f;
        float w = sx * scaleX;
        float h = sy * scaleY;
        float s = (stemSize / 2f) * Math.min(scaleX, scaleY);
        s = Math.max(2f, Math.min(s, Math.min(w, h) * 0.95f));

        float cx = rect.centerX();
        float cy = rect.centerY();

        Path path = new Path();
        path.moveTo(cx - s, cy - h);
        path.lineTo(cx + s, cy - h);
        path.lineTo(cx + s, cy - s);
        path.lineTo(cx + w, cy - s);
        path.lineTo(cx + w, cy + s);
        path.lineTo(cx + s, cy + s);
        path.lineTo(cx + s, cy + h);
        path.lineTo(cx - s, cy + h);
        path.lineTo(cx - s, cy + s);
        path.lineTo(cx - w, cy + s);
        path.lineTo(cx - w, cy - s);
        path.lineTo(cx - s, cy - s);
        path.close();
        return path;
    }

    private static Path buildMoonPath(ShapeDefinition def, RectF rect) {
        float radius = getParamFloat(def, "radius", 100f);
        float offset = getParamFloat(def, "offset", 250f) / 1000f;
        float scale = (Math.min(rect.width(), rect.height()) / 2f) / 100f;
        float w = radius * scale;
        float h = radius * scale;
        float cx = rect.centerX();
        float cy = rect.centerY();
        float cw = 0.5519f * w;
        float ch = 0.5519f * h;

        Path path = new Path();
        path.moveTo(cx, cy - h);
        path.cubicTo(cx - cw, cy - h, cx - w, cy - ch, cx - w, cy);
        path.cubicTo(cx - w, cy + ch, cx - cw, cy + h, cx, cy + h);

        float innerX = cx - w + (2f * w * offset);
        path.cubicTo(cx - cw + (2f * cw * offset), cy + h, innerX, cy + ch, innerX, cy);
        path.cubicTo(innerX, cy - ch, cx - cw + (2f * cw * offset), cy - h, cx, cy - h);
        path.close();
        return path;
    }

    private static Path buildPiePath(ShapeDefinition def, RectF rect) {
        float radius = getParamFloat(def, "radius", 100f);
        float startAngle = getParamFloat(def, "startAngle", 45f);
        float endAngle = getParamFloat(def, "endAngle", 90f);
        float scale = (Math.min(rect.width(), rect.height()) / 2f) / 100f;
        float r = radius * scale;
        float sweep = endAngle - startAngle;

        RectF pieRect = new RectF(rect.centerX() - r, rect.centerY() - r, rect.centerX() + r, rect.centerY() + r);
        Path path = new Path();
        path.moveTo(rect.centerX(), rect.centerY());
        path.arcTo(pieRect, startAngle, sweep, false);
        path.close();
        return path;
    }

    private static Path buildArcPath(ShapeDefinition def, RectF rect) {
        float radius = getParamFloat(def, "radius", 100f);
        float startAngle = getParamFloat(def, "startAngle", 45f);
        float endAngle = getParamFloat(def, "endAngle", 270f);
        float scale = (Math.min(rect.width(), rect.height()) / 2f) / 100f;
        float r = radius * scale;
        float sweep = endAngle - startAngle;

        RectF arcRect = new RectF(rect.centerX() - r, rect.centerY() - r, rect.centerX() + r, rect.centerY() + r);
        Path path = new Path();
        path.arcTo(arcRect, startAngle, sweep, false);
        return path;
    }

    private static Path buildMultifoilPath(ShapeDefinition def, RectF rect) {
        float pointCount = getParamFloat(def, "pointCount", getParamFloat(def, "petals", 6f));
        float outerRadius = getParamFloat(def, "outerRadius", 100f);
        float innerRadius = getParamFloat(def, "innerRadius", 50f);
        float offsetAngle = getParamFloat(def, "offsetAngle", 0f);

        int petals = Math.max(3, Math.round(pointCount));
        float radsPerPetal = (float) (2.0 * Math.PI / petals);
        float offsetRad = (float) Math.toRadians(offsetAngle);
        float scale = (Math.min(rect.width(), rect.height()) / 2f) / 100f;
        float cx = rect.centerX();
        float cy = rect.centerY();

        Path path = new Path();
        for (int i = 0; i < petals; i++) {
            float a1 = radsPerPetal * i + offsetRad;
            float a2 = a1 + radsPerPetal / 2f;
            float a3 = a1 + radsPerPetal;
            float x1 = cx + (float) (innerRadius * Math.cos(a1)) * scale;
            float y1 = cy - (float) (innerRadius * Math.sin(a1)) * scale;
            float x2 = cx + (float) (outerRadius * Math.cos(a2)) * scale;
            float y2 = cy - (float) (outerRadius * Math.sin(a2)) * scale;
            float x3 = cx + (float) (innerRadius * Math.cos(a3)) * scale;
            float y3 = cy - (float) (innerRadius * Math.sin(a3)) * scale;
            if (i == 0) path.moveTo(x1, y1);
            path.quadTo(x2, y2, x3, y3);
        }
        path.close();
        return path;
    }

    private static Path buildStampPath(ShapeDefinition def, RectF rect) {
        float sx = getParamFloat(def, "size_x", 150f);
        float sy = getParamFloat(def, "size_y", 150f);
        float cornerRad = getParamFloat(def, "cornerRadius", 8f);

        float scaleX = (rect.width() / 2f) / 150f;
        float scaleY = (rect.height() / 2f) / 150f;
        float halfW = sx * scaleX;
        float halfH = sy * scaleY;
        float r = cornerRad * Math.min(scaleX, scaleY);

        RectF stampRect = new RectF(rect.centerX() - halfW, rect.centerY() - halfH, rect.centerX() + halfW, rect.centerY() + halfH);
        Path path = new Path();
        path.addRoundRect(stampRect, r, r, Path.Direction.CW);
        return path;
    }

    private static Path buildCalloutPath(ShapeDefinition def, RectF rect) {
        float sx = getParamFloat(def, "size_x", 150f);
        float sy = getParamFloat(def, "size_y", 100f);
        float cornerRad = getParamFloat(def, "cornerRadius", 25f);

        float scaleX = (rect.width() / 2f) / 150f;
        float scaleY = (rect.height() / 2f) / 100f;
        float halfW = sx * scaleX;
        float halfH = sy * scaleY;
        float r = Math.min(cornerRad * Math.min(scaleX, scaleY), Math.min(halfW, halfH) * 0.4f);

        float left = rect.centerX() - halfW;
        float right = rect.centerX() + halfW;
        float top = rect.centerY() - halfH;
        float bottomBubble = rect.centerY() + halfH * 0.65f;
        float tailBottom = rect.centerY() + halfH;

        Path path = new Path();
        path.moveTo(left + r, top);
        path.lineTo(right - r, top);
        path.quadTo(right, top, right, top + r);
        path.lineTo(right, bottomBubble - r);
        path.quadTo(right, bottomBubble, right - r, bottomBubble);
        path.lineTo(rect.centerX() + 20 * scaleX, bottomBubble);
        path.lineTo(rect.centerX() - 10 * scaleX, tailBottom);
        path.lineTo(rect.centerX(), bottomBubble);
        path.lineTo(left + r, bottomBubble);
        path.quadTo(left, bottomBubble, left, bottomBubble - r);
        path.lineTo(left, top + r);
        path.quadTo(left, top, left + r, top);
        path.close();
        return path;
    }

    private static Path buildTeardropPath(ShapeDefinition def, RectF rect) {
        float radius = getParamFloat(def, "radius", 100f);
        float tail = getParamFloat(def, "tail", 200f);
        float squeeze = Math.max(0.5f, getParamFloat(def, "squeeze", 2.5f));
        float tailWidth = getParamFloat(def, "tailWidth", 0f);

        float scale = (Math.min(rect.width(), rect.height()) / 2f) / 100f;
        float width = radius * scale;
        float height = radius * scale;
        float cw = 0.5519f * width;
        float ch = 0.5519f * height;
        float cx = rect.centerX();
        float cy = rect.centerY();

        float tailY = cy - (height * tail / 100f);
        float tailW = width * tailWidth / 100f;

        Path path = new Path();
        if (tailWidth > 0.001f) {
            path.moveTo(cx - tailW, tailY);
            path.lineTo(cx + tailW, tailY);
            path.cubicTo(cx + width / squeeze, cy - (height * tail / (squeeze * 100f)), cx + width, cy - ch, cx + width, cy);
            path.cubicTo(cx + width, cy + ch, cx + cw, cy + height, cx, cy + height);
            path.cubicTo(cx - cw, cy + height, cx - width, cy + ch, cx - width, cy);
            path.cubicTo(cx - width, cy - ch, cx - width / squeeze, cy - (height * tail / (squeeze * 100f)), cx - tailW, tailY);
        } else {
            path.moveTo(cx, tailY);
            path.cubicTo(cx + width / squeeze, cy - (height * tail / (squeeze * 100f)), cx + width, cy - ch, cx + width, cy);
            path.cubicTo(cx + width, cy + ch, cx + cw, cy + height, cx, cy + height);
            path.cubicTo(cx - cw, cy + height, cx - width, cy + ch, cx - width, cy);
            path.cubicTo(cx - width, cy - ch, cx - width / squeeze, cy - (height * tail / (squeeze * 100f)), cx, tailY);
        }
        path.close();
        return path;
    }

    private static Path buildCirclePath(ShapeDefinition def, RectF rect) {
        float sx = getParamFloat(def, "size_x", getParamFloat(def, "radius", 100f));
        float sy = getParamFloat(def, "size_y", getParamFloat(def, "radius", 100f));

        float scaleX = (rect.width() / 2f) / 100f;
        float scaleY = (rect.height() / 2f) / 100f;
        float halfW = sx * scaleX;
        float halfH = sy * scaleY;

        RectF shapeRect = new RectF(rect.centerX() - halfW, rect.centerY() - halfH, rect.centerX() + halfW, rect.centerY() + halfH);
        Path path = new Path();
        path.addOval(shapeRect, Path.Direction.CW);
        return path;
    }

    private static Path buildRoundRectPath(ShapeDefinition def, RectF rect) {
        float sx = getParamFloat(def, "size_x", getParamFloat(def, "width", 100f));
        float sy = getParamFloat(def, "size_y", getParamFloat(def, "height", 100f));
        float cornerRad = getParamFloat(def, "cornerRadius", getParamFloat(def, "radius", 25f));

        float scaleX = (rect.width() / 2f) / 100f;
        float scaleY = (rect.height() / 2f) / 100f;
        float halfW = sx * scaleX;
        float halfH = sy * scaleY;
        float r = cornerRad * Math.min(scaleX, scaleY);
        r = Math.min(r, Math.min(halfW, halfH));

        RectF shapeRect = new RectF(rect.centerX() - halfW, rect.centerY() - halfH, rect.centerX() + halfW, rect.centerY() + halfH);
        Path path = new Path();
        path.addRoundRect(shapeRect, r, r, Path.Direction.CW);
        return path;
    }

    private static Path buildRectPath(ShapeDefinition def, RectF rect) {
        float sx = getParamFloat(def, "size_x", getParamFloat(def, "width", 100f));
        float sy = getParamFloat(def, "size_y", getParamFloat(def, "height", 100f));

        float scaleX = (rect.width() / 2f) / 100f;
        float scaleY = (rect.height() / 2f) / 100f;
        float halfW = sx * scaleX;
        float halfH = sy * scaleY;

        RectF shapeRect = new RectF(rect.centerX() - halfW, rect.centerY() - halfH, rect.centerX() + halfW, rect.centerY() + halfH);
        Path path = new Path();
        path.addRect(shapeRect, Path.Direction.CW);
        return path;
    }

    private static Path buildWidelinePath(ShapeDefinition def, RectF rect) {
        float lineWidth = getParamFloat(def, "lineWidth", getParamFloat(def, "width", 40f));
        float scale = (Math.min(rect.width(), rect.height()) / 2f) / 100f;
        float lw = lineWidth * scale;
        float cy = rect.centerY();

        Path path = new Path();
        path.addRoundRect(new RectF(rect.left, cy - lw / 2f, rect.right, cy + lw / 2f), lw / 2f, lw / 2f, Path.Direction.CW);
        return path;
    }

    private static Path buildTrianglePath(ShapeDefinition def, RectF rect) {
        float pointCount = getParamFloat(def, "pointCount", 3f);
        float radius = getParamFloat(def, "radius", 100f);
        float offsetAngle = getParamFloat(def, "offsetAngle", 0f);

        int count = Math.max(3, Math.round(pointCount));
        float radsPerPoint = (float) (2.0 * Math.PI / count);
        float offsetRad = (float) Math.toRadians(offsetAngle);
        float scale = (Math.min(rect.width(), rect.height()) / 2f) / 100f;
        float cx = rect.centerX();
        float cy = rect.centerY();

        Path path = new Path();
        for (int i = 0; i < count; i++) {
            float angle = radsPerPoint * i + (float) Math.PI / 2.0f + offsetRad;
            float px = cx + (float) (radius * Math.cos(angle)) * scale;
            float py = cy - (float) (radius * Math.sin(angle)) * scale;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.close();
        return path;
    }

    private static Path buildHeartPath(RectF rect) {
        Path path = new Path();
        float w = rect.width();
        float h = rect.height();
        float l = rect.left;
        float t = rect.top;

        path.moveTo(l + w / 2f, t + h * 0.8f);
        path.cubicTo(l, t + h * 0.5f, l, t, l + w * 0.25f, t);
        path.cubicTo(l + w * 0.45f, t, l + w / 2f, t + h * 0.2f, l + w / 2f, t + h * 0.35f);
        path.cubicTo(l + w / 2f, t + h * 0.2f, l + w * 0.55f, t, l + w * 0.75f, t);
        path.cubicTo(l + w, t, l + w, t + h * 0.5f, l + w / 2f, t + h * 0.8f);
        path.close();
        return path;
    }

    private static Path buildDiamondPath(RectF rect) {
        Path path = new Path();
        path.moveTo(rect.centerX(), rect.top);
        path.lineTo(rect.right, rect.centerY());
        path.lineTo(rect.centerX(), rect.bottom);
        path.lineTo(rect.left, rect.centerY());
        path.close();
        return path;
    }

    private static Path buildShieldPath(RectF rect) {
        Path path = new Path();
        path.moveTo(rect.centerX(), rect.top);
        path.lineTo(rect.right, rect.top + rect.height() * 0.2f);
        path.quadTo(rect.right, rect.bottom * 0.7f, rect.centerX(), rect.bottom);
        path.quadTo(rect.left, rect.bottom * 0.7f, rect.left, rect.top + rect.height() * 0.2f);
        path.close();
        return path;
    }

    /**
     * Generates a crisp vector bitmap preview for any ShapeDefinition.
     */
    public static Bitmap renderShapeThumbnail(ShapeDefinition def, int sizePx, int color) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float pad = sizePx * 0.15f;
        RectF r = new RectF(pad, pad, sizePx - pad, sizePx - pad);

        Path path = buildPath(def, r, ShapeLayer.ShapeType.ROUNDED_RECT);
        if (path != null) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawPath(path, p);
        }
        return bmp;
    }

    private static float getParamFloat(ShapeDefinition def, String id, float fallback) {
        if (def == null) return fallback;
        EffectParam p = def.getParam(id);
        if (p != null) return p.getFloatValue();
        return fallback;
    }
}
