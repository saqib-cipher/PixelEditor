package glab.pixeleditor.model;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.UUID;

public abstract class CanvasLayer {
    protected String id;
    protected String name;
    protected float x; // Center X on canvas
    protected float y; // Center Y on canvas
    protected float width;
    protected float height;
    protected float rotation = 0f; // in degrees
    protected float scaleX = 1f;
    protected float scaleY = 1f;
    protected int opacity = 255; // 0..255
    protected boolean isVisible = true;
    protected boolean isLocked = false;

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

    public boolean containsPoint(float px, float py) {
        if (!isVisible) return false;
        // Transform point into layer local coordinates (inverse rotation and translation)
        float[] pts = new float[]{px, py};
        Matrix matrix = new Matrix();
        matrix.setRotate(-rotation, x, y);
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
    public int getOpacity() { return opacity; }
    public void setOpacity(int opacity) { this.opacity = Math.max(0, Math.min(255, opacity)); }
    public boolean isVisible() { return isVisible; }
    public void setVisible(boolean visible) { isVisible = visible; }
    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { isLocked = locked; }
}
