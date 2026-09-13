package glab.pixeleditor.effect;

import android.graphics.Color;

public class EffectParam {

    public enum ParamType {
        SLIDER,   // continuous slider / spinner
        SWITCH,   // boolean toggle
        COLOR,    // color picker
        CHOICE    // discrete menu / choice
    }

    private String id;
    private ParamType type;
    private String label;
    private float defaultValue;
    private float minValue;
    private float maxValue;
    private float step = 0.01f;
    private String unitType = ""; // "relative-percent", "percent", "angle", "distance", etc.

    // Current interactive state
    private float floatValue;
    private boolean booleanValue;
    private int colorValue;

    public EffectParam(String id, ParamType type, String label, float defaultValue, float minValue, float maxValue, float step, String unitType) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.step = step > 0 ? step : 0.01f;
        this.unitType = unitType != null ? unitType : "";
        this.floatValue = defaultValue;
    }

    public EffectParam(String id, String label, boolean defaultBool) {
        this.id = id;
        this.type = ParamType.SWITCH;
        this.label = label;
        this.booleanValue = defaultBool;
    }

    public EffectParam(String id, String label, int defaultColor) {
        this.id = id;
        this.type = ParamType.COLOR;
        this.label = label;
        this.colorValue = defaultColor;
    }

    public String getFormattedValue() {
        if (type == ParamType.SWITCH) {
            return booleanValue ? "ON" : "OFF";
        }
        if (type == ParamType.COLOR) {
            return String.format("#%06X", (0xFFFFFF & colorValue));
        }

        if ("relative-percent".equalsIgnoreCase(unitType)) {
            int pct = Math.round(floatValue * 100f);
            return (pct > 0 ? "+" : "") + pct + "%";
        } else if ("percent".equalsIgnoreCase(unitType)) {
            return Math.round(floatValue * 100f) + "%";
        } else if ("angle".equalsIgnoreCase(unitType)) {
            return Math.round(floatValue) + "°";
        } else if (step >= 1.0f) {
            return String.valueOf(Math.round(floatValue));
        } else if (step >= 0.1f) {
            return String.format("%.1f", floatValue);
        } else {
            return String.format("%.2f", floatValue);
        }
    }

    public void resetToDefault() {
        this.floatValue = defaultValue;
    }

    public EffectParam copy() {
        EffectParam copy = new EffectParam(id, type, label, defaultValue, minValue, maxValue, step, unitType);
        copy.setFloatValue(floatValue);
        copy.setBooleanValue(booleanValue);
        copy.setColorValue(colorValue);
        return copy;
    }

    // Getters and Setters
    public String getId() { return id; }
    public ParamType getType() { return type; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public float getDefaultValue() { return defaultValue; }
    public float getMinValue() { return minValue; }
    public float getMaxValue() { return maxValue; }
    public float getStep() { return step; }
    public String getUnitType() { return unitType; }
    public float getFloatValue() { return floatValue; }
    public void setFloatValue(float floatValue) { this.floatValue = Math.max(minValue, Math.min(maxValue, floatValue)); }
    public boolean getBooleanValue() { return booleanValue; }
    public void setBooleanValue(boolean booleanValue) { this.booleanValue = booleanValue; }
    public int getColorValue() { return colorValue; }
    public void setColorValue(int colorValue) { this.colorValue = colorValue; }
}
