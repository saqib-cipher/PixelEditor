package glab.pixeleditor.model;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import glab.pixeleditor.effect.EffectDefinition;

public abstract class CanvasLayer {

    public enum MaskType {
        NONE,
        MASK,
        EXCLUDE
    }

    public enum BlendMode {
        NORMAL,
        MULTIPLY,
        DARKEN,
        COLOR_BURN,
        LINEAR_BURN,
        SCREEN,
        LIGHTEN,
        COLOR_DODGE,
        LINEAR_DODGE,
        OVERLAY,
        SOFT_LIGHT,
        HARD_LIGHT,
        DIFFERENCE,
        EXCLUSION,
        HUE,
        SATURATION,
        COLOR,
        LUMINOSITY;

        public android.graphics.PorterDuff.Mode toPorterDuffMode() {
            switch (this) {
                case MULTIPLY: return android.graphics.PorterDuff.Mode.MULTIPLY;
                case SCREEN: return android.graphics.PorterDuff.Mode.SCREEN;
                case OVERLAY: return android.graphics.PorterDuff.Mode.OVERLAY;
                case DARKEN: return android.graphics.PorterDuff.Mode.DARKEN;
                case LIGHTEN: return android.graphics.PorterDuff.Mode.LIGHTEN;
                case LINEAR_DODGE: return android.graphics.PorterDuff.Mode.ADD;
                default: return android.graphics.PorterDuff.Mode.SRC_OVER;
            }
        }

        public void applyToPaint(android.graphics.Paint paint) {
            if (paint == null) return;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.graphics.BlendMode bm = toAndroidBlendMode();
                if (bm != null) {
                    paint.setBlendMode(bm);
                    return;
                }
            }
            android.graphics.PorterDuff.Mode pd = toPorterDuffMode();
            if (pd != android.graphics.PorterDuff.Mode.SRC_OVER) {
                paint.setXfermode(new android.graphics.PorterDuffXfermode(pd));
            } else {
                paint.setXfermode(null);
            }
        }

        public android.graphics.BlendMode toAndroidBlendMode() {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return null;
            switch (this) {
                case MULTIPLY: return android.graphics.BlendMode.MULTIPLY;
                case SCREEN: return android.graphics.BlendMode.SCREEN;
                case OVERLAY: return android.graphics.BlendMode.OVERLAY;
                case DARKEN: return android.graphics.BlendMode.DARKEN;
                case LIGHTEN: return android.graphics.BlendMode.LIGHTEN;
                case COLOR_DODGE: return android.graphics.BlendMode.COLOR_DODGE;
                case COLOR_BURN: return android.graphics.BlendMode.COLOR_BURN;
                case HARD_LIGHT: return android.graphics.BlendMode.HARD_LIGHT;
                case SOFT_LIGHT: return android.graphics.BlendMode.SOFT_LIGHT;
                case DIFFERENCE: return android.graphics.BlendMode.DIFFERENCE;
                case EXCLUSION: return android.graphics.BlendMode.EXCLUSION;
                case HUE: return android.graphics.BlendMode.HUE;
                case SATURATION: return android.graphics.BlendMode.SATURATION;
                case COLOR: return android.graphics.BlendMode.COLOR;
                case LUMINOSITY: return android.graphics.BlendMode.LUMINOSITY;
                default: return android.graphics.BlendMode.SRC_OVER;
            }
        }
    }

    protected String id;
    protected String name;
    protected float x; // Center X on canvas
    protected float y; // Center Y on canvas
    protected float width;
    protected float height;
    protected float rotation = 0f; // in degrees
    protected float scaleX = 1f;
    protected float scaleY = 1f;
    protected float skewX = 0f; // horizontal shear in degrees
    protected float skewY = 0f; // vertical shear in degrees
    protected int opacity = 255; // 0..255
    protected boolean isVisible = true;
    protected boolean isLocked = false;
    protected MaskType maskType = MaskType.NONE;
    protected BlendMode blendMode = BlendMode.NORMAL;
    protected final List<EffectDefinition> appliedEffects = new ArrayList<>();
    protected final List<BorderItem> borders = new ArrayList<>();
    protected final List<ShadowItem> shadows = new ArrayList<>();

    public CanvasLayer(String name, float x, float y, float width, float height) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public abstract void draw(Canvas canvas, Paint basePaint);

    public abstract CanvasLayer copy();

    /**
     * Exact state clone for snapshots, undo, and redo. Preserves original ID, name, and position.
     */
    public abstract CanvasLayer cloneLayer();

    public boolean containsPoint(float px, float py) {
        if (!isVisible) return false;
        // Transform point into layer local coordinates (inverse rotation, skew, and translation)
        float[] pts = new float[]{px, py};
        Matrix matrix = new Matrix();
        matrix.setRotate(-rotation, x, y);
        if (skewX != 0f || skewY != 0f) {
            matrix.preSkew(-(float) Math.tan(Math.toRadians(skewX)), -(float) Math.tan(Math.toRadians(skewY)));
        }
        matrix.mapPoints(pts);

        float halfW = (width * Math.abs(scaleX)) / 2f;
        float halfH = (height * Math.abs(scaleY)) / 2f;
        return pts[0] >= (x - halfW) && pts[0] <= (x + halfW)
                && pts[1] >= (y - halfH) && pts[1] <= (y + halfH);
    }

    public RectF getBounds() {
        float halfW = (width * Math.abs(scaleX)) / 2f;
        float halfH = (height * Math.abs(scaleY)) / 2f;
        return new RectF(x - halfW, y - halfH, x + halfW, y + halfH);
    }

    public PointF[] getTransformedCorners() {
        float halfW = (width * Math.abs(scaleX)) / 2f;
        float halfH = (height * Math.abs(scaleY)) / 2f;
        float[] src = new float[]{
                x - halfW, y - halfH, // top-left
                x + halfW, y - halfH, // top-right
                x + halfW, y + halfH, // bottom-right
                x - halfW, y + halfH  // bottom-left
        };
        Matrix matrix = new Matrix();
        matrix.setRotate(rotation, x, y);
        float[] dst = new float[8];
        matrix.mapPoints(dst, src);

        return new PointF[]{
                new PointF(dst[0], dst[1]),
                new PointF(dst[2], dst[3]),
                new PointF(dst[4], dst[5]),
                new PointF(dst[6], dst[7])
        };
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public float getX() { return x; }
    public void setX(float x) { this.x = x; }
    public float getY() { return y; }
    public void setY(float y) { this.y = y; }
    public float getWidth() { return width; }
    public void setWidth(float width) { this.width = Math.max(20f, width); }
    public float getHeight() { return height; }
    public void setHeight(float height) { this.height = Math.max(20f, height); }
    public float getRotation() { return rotation; }
    public void setRotation(float rotation) { this.rotation = rotation % 360f; }
    public float getScaleX() { return scaleX; }
    public void setScaleX(float scaleX) { this.scaleX = scaleX; }
    public float getScaleY() { return scaleY; }
    public void setScaleY(float scaleY) { this.scaleY = scaleY; }
    public float getSkewX() { return skewX; }
    public void setSkewX(float skewX) { this.skewX = skewX; }
    public float getSkewY() { return skewY; }
    public void setSkewY(float skewY) { this.skewY = skewY; }
    public int getOpacity() { return opacity; }
    public void setOpacity(int opacity) { this.opacity = Math.max(0, Math.min(255, opacity)); }
    public float getOpacityPercent() { return (opacity / 255f) * 100f; }
    public void setOpacityPercent(float percent) { this.opacity = Math.round(Math.max(0f, Math.min(100f, percent)) * 2.55f); }
    public boolean isVisible() { return isVisible; }
    public void setVisible(boolean visible) { isVisible = visible; }
    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { isLocked = locked; }

    public MaskType getMaskType() { return maskType; }
    public void setMaskType(MaskType maskType) { this.maskType = maskType != null ? maskType : MaskType.NONE; }
    public BlendMode getBlendMode() { return blendMode; }
    public void setBlendMode(BlendMode blendMode) { this.blendMode = blendMode != null ? blendMode : BlendMode.NORMAL; }

    public List<EffectDefinition> getAppliedEffects() { return appliedEffects; }
    public void addEffect(EffectDefinition effect) {
        if (effect != null) {
            appliedEffects.add(effect);
        }
    }
    public void removeEffect(int index) {
        if (index >= 0 && index < appliedEffects.size()) {
            appliedEffects.remove(index);
        }
    }
    public void removeEffect(EffectDefinition effect) {
        appliedEffects.remove(effect);
    }
    public void clearAppliedEffects() {
        appliedEffects.clear();
    }

    public List<BorderItem> getBorders() { return borders; }
    public void addBorder(BorderItem border) {
        if (border != null) borders.add(border);
    }
    public void removeBorder(int index) {
        if (index >= 0 && index < borders.size()) borders.remove(index);
    }
    public void clearBorders() { borders.clear(); }

    public List<ShadowItem> getShadows() { return shadows; }
    public void addShadow(ShadowItem shadow) {
        if (shadow != null) shadows.add(shadow);
    }
    public void removeShadow(int index) {
        if (index >= 0 && index < shadows.size()) shadows.remove(index);
    }
    public void clearShadows() { shadows.clear(); }

    public abstract org.json.JSONObject toJson(android.content.Context context);

    protected void writeBaseJson(org.json.JSONObject json) {
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("x", x);
            json.put("y", y);
            json.put("width", width);
            json.put("height", height);
            json.put("rotation", rotation);
            json.put("scaleX", scaleX);
            json.put("scaleY", scaleY);
            json.put("skewX", skewX);
            json.put("skewY", skewY);
            json.put("opacity", opacity);
            json.put("isVisible", isVisible);
            json.put("isLocked", isLocked);
            json.put("maskType", maskType.name());
            json.put("blendMode", blendMode.name());

            org.json.JSONArray effArray = new org.json.JSONArray();
            for (EffectDefinition eff : appliedEffects) {
                effArray.put(eff.toJson());
            }
            json.put("appliedEffects", effArray);

            org.json.JSONArray bArray = new org.json.JSONArray();
            for (BorderItem b : borders) {
                bArray.put(b.toJson());
            }
            json.put("borders", bArray);

            org.json.JSONArray sArray = new org.json.JSONArray();
            for (ShadowItem s : shadows) {
                sArray.put(s.toJson());
            }
            json.put("shadows", sArray);
        } catch (Exception ignored) {}
    }

    protected void readBaseJson(org.json.JSONObject json) {
        this.id = json.optString("id", id);
        this.name = json.optString("name", name);
        this.x = (float) json.optDouble("x", x);
        this.y = (float) json.optDouble("y", y);
        this.width = (float) json.optDouble("width", width);
        this.height = (float) json.optDouble("height", height);
        this.rotation = (float) json.optDouble("rotation", rotation);
        this.scaleX = (float) json.optDouble("scaleX", scaleX);
        this.scaleY = (float) json.optDouble("scaleY", scaleY);
        this.skewX = (float) json.optDouble("skewX", skewX);
        this.skewY = (float) json.optDouble("skewY", skewY);
        this.opacity = json.optInt("opacity", opacity);
        this.isVisible = json.optBoolean("isVisible", isVisible);
        this.isLocked = json.optBoolean("isLocked", isLocked);

        String mType = json.optString("maskType", "NONE");
        try {
            this.maskType = MaskType.valueOf(mType.toUpperCase());
        } catch (Exception ignored) {
            this.maskType = MaskType.NONE;
        }

        String bMode = json.optString("blendMode", "NORMAL");
        try {
            this.blendMode = BlendMode.valueOf(bMode.toUpperCase());
        } catch (Exception ignored) {
            this.blendMode = BlendMode.NORMAL;
        }

        appliedEffects.clear();
        org.json.JSONArray effArray = json.optJSONArray("appliedEffects");
        if (effArray != null) {
            for (int i = 0; i < effArray.length(); i++) {
                org.json.JSONObject effJson = effArray.optJSONObject(i);
                if (effJson != null) {
                    EffectDefinition eff = EffectDefinition.fromJson(effJson);
                    if (eff != null) appliedEffects.add(eff);
                }
            }
        }

        borders.clear();
        org.json.JSONArray bArray = json.optJSONArray("borders");
        if (bArray != null) {
            for (int i = 0; i < bArray.length(); i++) {
                org.json.JSONObject bJson = bArray.optJSONObject(i);
                if (bJson != null) {
                    BorderItem b = BorderItem.fromJson(bJson);
                    if (b != null) borders.add(b);
                }
            }
        }

        shadows.clear();
        org.json.JSONArray sArray = json.optJSONArray("shadows");
        if (sArray != null) {
            for (int i = 0; i < sArray.length(); i++) {
                org.json.JSONObject sJson = sArray.optJSONObject(i);
                if (sJson != null) {
                    ShadowItem s = ShadowItem.fromJson(sJson);
                    if (s != null) shadows.add(s);
                }
            }
        }
    }

    public static CanvasLayer fromJson(android.content.Context context, org.json.JSONObject json) {
        if (json == null) return null;
        String type = json.optString("layerType", "SHAPE");
        if ("GROUP".equalsIgnoreCase(type)) {
            return GroupLayer.fromJson(context, json);
        } else if ("TEXT".equalsIgnoreCase(type)) {
            return TextLayer.fromJson(context, json);
        } else if ("PHOTO".equalsIgnoreCase(type)) {
            return PhotoLayer.fromJson(context, json);
        } else {
            return ShapeLayer.fromJson(context, json);
        }
    }
}
