package glab.pixeleditor.model;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class ShadowItem {

    private String id;
    private boolean enabled;
    private int color;
    private float size;     // Blur radius (0 - 100)
    private float alpha;    // 0.0 - 1.0
    private float offsetX;  // -100 to +100
    private float offsetY;  // -100 to +100

    public ShadowItem() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
        this.color = 0xFF000000;
        this.size = 12.0f;
        this.alpha = 0.6f;
        this.offsetX = 0.0f;
        this.offsetY = 8.0f;
    }

    public ShadowItem(int color, float size, float alpha, float offsetX, float offsetY) {
        this();
        this.color = color;
        this.size = size;
        this.alpha = alpha;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public float getSize() {
        return size;
    }

    public void setSize(float size) {
        this.size = Math.max(0f, size);
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0f, Math.min(1f, alpha));
    }

    public float getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(float offsetX) {
        this.offsetX = offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(float offsetY) {
        this.offsetY = offsetY;
    }

    public ShadowItem copy() {
        ShadowItem item = new ShadowItem();
        item.id = UUID.randomUUID().toString();
        item.enabled = this.enabled;
        item.color = this.color;
        item.size = this.size;
        item.alpha = this.alpha;
        item.offsetX = this.offsetX;
        item.offsetY = this.offsetY;
        return item;
    }

    public ShadowItem cloneItem() {
        ShadowItem item = new ShadowItem();
        item.id = this.id;
        item.enabled = this.enabled;
        item.color = this.color;
        item.size = this.size;
        item.alpha = this.alpha;
        item.offsetX = this.offsetX;
        item.offsetY = this.offsetY;
        return item;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("enabled", enabled);
            obj.put("color", color);
            obj.put("size", (double) size);
            obj.put("alpha", (double) alpha);
            obj.put("offsetX", (double) offsetX);
            obj.put("offsetY", (double) offsetY);
        } catch (JSONException ignored) {}
        return obj;
    }

    public static ShadowItem fromJson(JSONObject obj) {
        if (obj == null) return null;
        ShadowItem item = new ShadowItem();
        item.id = obj.optString("id", UUID.randomUUID().toString());
        item.enabled = obj.optBoolean("enabled", true);
        item.color = obj.optInt("color", 0xFF000000);
        item.size = (float) obj.optDouble("size", 12.0);
        item.alpha = (float) obj.optDouble("alpha", 0.6);
        item.offsetX = (float) obj.optDouble("offsetX", 0.0);
        item.offsetY = (float) obj.optDouble("offsetY", 8.0);
        return item;
    }
}
