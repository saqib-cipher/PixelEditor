package glab.pixeleditor.model;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

import glab.pixeleditor.effect.EffectDefinition;

import java.util.ArrayList;
import java.util.List;

public class GroupLayer extends CanvasLayer {

    public enum GroupMaskMode {
        NONE,
        MASK,
        EXCLUDE
    }

    private final List<CanvasLayer> children = new ArrayList<>();
    private GroupMaskMode groupMaskMode = GroupMaskMode.NONE;

    public GroupLayer(String name, float x, float y, float width, float height) {
        super(name, x, y, width, height);
    }

    public GroupLayer(String name, List<CanvasLayer> initialChildren, GroupMaskMode mode) {
        super(name, 0, 0, 100, 100);
        this.groupMaskMode = (mode != null) ? mode : GroupMaskMode.NONE;
        if (initialChildren != null && !initialChildren.isEmpty()) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (CanvasLayer child : initialChildren) {
                float hw = (child.getWidth() * Math.abs(child.getScaleX())) / 2f;
                float hh = (child.getHeight() * Math.abs(child.getScaleY())) / 2f;
                minX = Math.min(minX, child.getX() - hw);
                minY = Math.min(minY, child.getY() - hh);
                maxX = Math.max(maxX, child.getX() + hw);
                maxY = Math.max(maxY, child.getY() + hh);
            }
            this.x = (minX + maxX) / 2f;
            this.y = (minY + maxY) / 2f;
            this.width = Math.max(50f, maxX - minX);
            this.height = Math.max(50f, maxY - minY);

            for (CanvasLayer child : initialChildren) {
                child.setX(child.getX() - this.x);
                child.setY(child.getY() - this.y);
                this.children.add(child);
            }
        }
    }

    public void recalculateBounds() {
        if (children.isEmpty()) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (CanvasLayer child : children) {
            float hw = (child.getWidth() * Math.abs(child.getScaleX())) / 2f;
            float hh = (child.getHeight() * Math.abs(child.getScaleY())) / 2f;
            minX = Math.min(minX, child.getX() - hw);
            minY = Math.min(minY, child.getY() - hh);
            maxX = Math.max(maxX, child.getX() + hw);
            maxY = Math.max(maxY, child.getY() + hh);
        }
        this.width = Math.max(50f, maxX - minX);
        this.height = Math.max(50f, maxY - minY);
    }

    public List<CanvasLayer> getChildren() {
        return children;
    }

    public void addChild(CanvasLayer layer) {
        if (layer != null) {
            children.add(layer);
        }
    }

    public void removeChild(CanvasLayer layer) {
        children.remove(layer);
    }

    public void removeChild(int index) {
        if (index >= 0 && index < children.size()) {
            children.remove(index);
        }
    }

    public void clearChildren() {
        children.clear();
    }

    public GroupMaskMode getGroupMaskMode() {
        return groupMaskMode;
    }

    public void setGroupMaskMode(GroupMaskMode mode) {
        this.groupMaskMode = mode != null ? mode : GroupMaskMode.NONE;
    }

    @Override
    public void draw(Canvas canvas, Paint basePaint) {
        if (!isVisible || children.isEmpty()) return;

        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        canvas.scale(scaleX, scaleY);
        if (skewX != 0f || skewY != 0f) {
            canvas.skew((float) Math.tan(Math.toRadians(skewX)), (float) Math.tan(Math.toRadians(skewY)));
        }

        glab.pixeleditor.effect.EffectPipeline.applyEffectTransforms(canvas, this);

        // Group-level Paint with opacity and blend mode
        Paint groupPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        groupPaint.setAlpha(opacity);
        if (blendMode != BlendMode.NORMAL) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.graphics.BlendMode abm = blendMode.toAndroidBlendMode();
                if (abm != null && abm != android.graphics.BlendMode.SRC_OVER) {
                    groupPaint.setBlendMode(abm);
                }
            } else {
                PorterDuff.Mode pdMode = blendMode.toPorterDuffMode();
                if (pdMode != null && pdMode != PorterDuff.Mode.SRC_OVER) {
                    groupPaint.setXfermode(new PorterDuffXfermode(pdMode));
                }
            }
        }

        // Isolate group rendering into an offscreen layer buffer
        int saveCount = canvas.saveLayer(null, groupPaint);

        if (groupMaskMode == GroupMaskMode.NONE || children.size() < 2) {
            for (CanvasLayer child : children) {
                if (child.isVisible()) {
                    child.draw(canvas, basePaint);
                }
            }
        } else if (groupMaskMode == GroupMaskMode.MASK) {
            // 1. Draw all content layers below the top layer
            for (int i = 0; i < children.size() - 1; i++) {
                CanvasLayer child = children.get(i);
                if (child.isVisible()) {
                    child.draw(canvas, basePaint);
                }
            }

            // 2. Composite top layer as alpha mask using DST_IN
            CanvasLayer maskChild = children.get(children.size() - 1);
            if (maskChild.isVisible()) {
                Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                int maskSave = canvas.saveLayer(null, maskPaint);
                maskChild.draw(canvas, basePaint);
                canvas.restoreToCount(maskSave);
            }
        } else if (groupMaskMode == GroupMaskMode.EXCLUDE) {
            // 1. Draw all content layers below the top layer
            for (int i = 0; i < children.size() - 1; i++) {
                CanvasLayer child = children.get(i);
                if (child.isVisible()) {
                    child.draw(canvas, basePaint);
                }
            }

            // 2. Composite top layer as cutout mask using DST_OUT
            CanvasLayer maskChild = children.get(children.size() - 1);
            if (maskChild.isVisible()) {
                Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                int maskSave = canvas.saveLayer(null, maskPaint);
                maskChild.draw(canvas, basePaint);
                canvas.restoreToCount(maskSave);
            }
        }

        // Draw group-level borders if any
        float left = -width / 2f;
        float top = -height / 2f;
        float right = width / 2f;
        float bottom = height / 2f;
        RectF rect = new RectF(left, top, right, bottom);

        for (BorderItem b : borders) {
            if (!b.isEnabled() || b.getWidth() <= 0f) continue;
            int bAlpha = Math.round(Color.alpha(b.getColor()) * (opacity / 255f));
            if (bAlpha <= 0) continue;

            Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
            bp.setStyle(Paint.Style.STROKE);
            bp.setColor(b.getColor());
            bp.setAlpha(bAlpha);
            bp.setStrokeWidth(b.getWidth());
            canvas.drawRect(rect, bp);
        }

        canvas.restoreToCount(saveCount);
        canvas.restore();
    }

    @Override
    public boolean containsPoint(float px, float py) {
        if (!isVisible) return false;
        if (super.containsPoint(px, py)) return true;
        // Check children in group's local coordinate system
        float[] pts = new float[]{px, py};
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setRotate(-rotation, x, y);
        if (skewX != 0f || skewY != 0f) {
            matrix.preSkew(-(float) Math.tan(Math.toRadians(skewX)), -(float) Math.tan(Math.toRadians(skewY)));
        }
        matrix.mapPoints(pts);
        float localX = pts[0] - x;
        float localY = pts[1] - y;
        for (CanvasLayer child : children) {
            if (child.isVisible() && child.containsPoint(localX, localY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CanvasLayer copy() {
        GroupLayer copy = new GroupLayer(name + " Copy", x + 30f, y + 30f, width, height);
        copy.rotation = rotation;
        copy.scaleX = scaleX;
        copy.scaleY = scaleY;
        copy.skewX = skewX;
        copy.skewY = skewY;
        copy.opacity = opacity;
        copy.isVisible = isVisible;
        copy.isLocked = isLocked;
        copy.groupMaskMode = groupMaskMode;
        copy.maskType = maskType;
        copy.blendMode = blendMode;

        for (CanvasLayer child : children) {
            copy.addChild(child.copy());
        }
        for (EffectDefinition eff : appliedEffects) {
            copy.addEffect(eff.copy());
        }
        for (BorderItem b : borders) {
            copy.addBorder(b.copy());
        }
        for (ShadowItem s : shadows) {
            copy.addShadow(s.copy());
        }
        return copy;
    }

    @Override
    public CanvasLayer cloneLayer() {
        GroupLayer clone = new GroupLayer(name, x, y, width, height);
        clone.id = id;
        clone.rotation = rotation;
        clone.scaleX = scaleX;
        clone.scaleY = scaleY;
        clone.skewX = skewX;
        clone.skewY = skewY;
        clone.opacity = opacity;
        clone.isVisible = isVisible;
        clone.isLocked = isLocked;
        clone.groupMaskMode = groupMaskMode;
        clone.maskType = maskType;
        clone.blendMode = blendMode;

        for (CanvasLayer child : children) {
            clone.addChild(child.cloneLayer());
        }
        for (EffectDefinition eff : appliedEffects) {
            clone.addEffect(eff.copy());
        }
        for (BorderItem b : borders) {
            clone.addBorder(b.copy());
        }
        for (ShadowItem s : shadows) {
            clone.addShadow(s.copy());
        }
        return clone;
    }

    @Override
    public JSONObject toJson(Context context) {
        JSONObject json = new JSONObject();
        try {
            json.put("layerType", "GROUP");
            json.put("groupMaskMode", groupMaskMode.name());
            writeBaseJson(json);

            JSONArray childArray = new JSONArray();
            for (CanvasLayer child : children) {
                childArray.put(child.toJson(context));
            }
            json.put("children", childArray);
        } catch (Exception ignored) {}
        return json;
    }

    public static GroupLayer fromJson(Context context, JSONObject json) {
        if (json == null) return null;
        String name = json.optString("name", "Group 1");
        float x = (float) json.optDouble("x", 540);
        float y = (float) json.optDouble("y", 675);
        float w = (float) json.optDouble("width", 500);
        float h = (float) json.optDouble("height", 500);

        GroupLayer layer = new GroupLayer(name, x, y, w, h);
        layer.readBaseJson(json);

        String maskModeStr = json.optString("groupMaskMode", "NONE");
        try {
            layer.groupMaskMode = GroupMaskMode.valueOf(maskModeStr.toUpperCase());
        } catch (Exception ignored) {
            layer.groupMaskMode = GroupMaskMode.NONE;
        }

        JSONArray childArray = json.optJSONArray("children");
        if (childArray != null) {
            for (int i = 0; i < childArray.length(); i++) {
                JSONObject cJson = childArray.optJSONObject(i);
                if (cJson != null) {
                    CanvasLayer child = CanvasLayer.fromJson(context, cJson);
                    if (child != null) {
                        layer.addChild(child);
                    }
                }
            }
        }

        return layer;
    }
}
