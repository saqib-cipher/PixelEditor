package glab.pixeleditor.model;

import android.graphics.Paint;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class BorderItem {

    public enum Alignment {
        CENTER,
        INSIDE,
        OUTSIDE
    }

    private String id;
    private boolean enabled;
    private int color;
    private float width;
    private Alignment alignment;
    private Paint.Cap cap;
    private Paint.Join join;

    public BorderItem() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
        this.color = 0xFF00E5BC;
        this.width = 4.0f;
        this.alignment = Alignment.CENTER;
        this.cap = Paint.Cap.ROUND;
        this.join = Paint.Join.ROUND;
    }

    public BorderItem(int color, float width, Alignment alignment) {
        this();
        this.color = color;
        this.width = width;
        this.alignment = alignment != null ? alignment : Alignment.CENTER;
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

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = Math.max(0f, width);
    }

    public Alignment getAlignment() {
        return alignment != null ? alignment : Alignment.CENTER;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment != null ? alignment : Alignment.CENTER;
    }

    public Paint.Cap getCap() {
        return cap != null ? cap : Paint.Cap.ROUND;
    }

    public void setCap(Paint.Cap cap) {
        this.cap = cap;
    }

    public Paint.Join getJoin() {
        return join != null ? join : Paint.Join.ROUND;
    }

    public void setJoin(Paint.Join join) {
        this.join = join;
    }

    public BorderItem copy() {
        BorderItem item = new BorderItem();
        item.id = UUID.randomUUID().toString();
        item.enabled = this.enabled;
        item.color = this.color;
        item.width = this.width;
        item.alignment = this.alignment;
        item.cap = this.cap;
        item.join = this.join;
        return item;
    }

    public BorderItem cloneItem() {
        BorderItem item = new BorderItem();
        item.id = this.id;
        item.enabled = this.enabled;
        item.color = this.color;
        item.width = this.width;
        item.alignment = this.alignment;
        item.cap = this.cap;
        item.join = this.join;
        return item;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("enabled", enabled);
            obj.put("color", color);
            obj.put("width", (double) width);
            obj.put("alignment", alignment.name());
            obj.put("cap", cap.name());
            obj.put("join", join.name());
        } catch (JSONException ignored) {}
        return obj;
    }

    public static BorderItem fromJson(JSONObject obj) {
        if (obj == null) return null;
        BorderItem item = new BorderItem();
        item.id = obj.optString("id", UUID.randomUUID().toString());
        item.enabled = obj.optBoolean("enabled", true);
        item.color = obj.optInt("color", 0xFF00E5BC);
        item.width = (float) obj.optDouble("width", 4.0);
        try {
            item.alignment = Alignment.valueOf(obj.optString("alignment", "CENTER"));
        } catch (Exception e) {
            item.alignment = Alignment.CENTER;
        }
        try {
            item.cap = Paint.Cap.valueOf(obj.optString("cap", "ROUND"));
        } catch (Exception e) {
            item.cap = Paint.Cap.ROUND;
        }
        try {
            item.join = Paint.Join.valueOf(obj.optString("join", "ROUND"));
        } catch (Exception e) {
            item.join = Paint.Join.ROUND;
        }
        return item;
    }
}
