package glab.pixeleditor.shape;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import glab.pixeleditor.effect.EffectParam;

public class ShapeDefinition {

    private String id;
    private String fileName;
    private String name;
    private final List<EffectParam> params = new ArrayList<>();
    private String scriptSource = "";

    public ShapeDefinition(String id, String fileName, String name) {
        this.id = id;
        this.fileName = fileName;
        this.name = name;
    }

    public void addParam(EffectParam param) {
        params.add(param);
    }

    public EffectParam getParam(String paramId) {
        for (EffectParam p : params) {
            if (p.getId().equalsIgnoreCase(paramId)) {
                return p;
            }
        }
        return null;
    }

    public void resetAllParams() {
        for (EffectParam p : params) {
            p.resetToDefault();
        }
    }

    public ShapeDefinition copy() {
        ShapeDefinition copy = new ShapeDefinition(id, fileName, name);
        copy.setScriptSource(scriptSource);
        for (EffectParam p : params) {
            copy.addParam(p.copy());
        }
        return copy;
    }

    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("fileName", fileName);
            json.put("name", name);
            json.put("scriptSource", scriptSource);

            JSONArray pArray = new JSONArray();
            for (EffectParam p : params) {
                pArray.put(p.toJson());
            }
            json.put("params", pArray);
            return json;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static ShapeDefinition fromJson(JSONObject json) {
        if (json == null) return null;
        String id = json.optString("id", "");
        String fileName = json.optString("fileName", "");
        String name = json.optString("name", "Shape");

        ShapeDefinition def = new ShapeDefinition(id, fileName, name);
        def.scriptSource = json.optString("scriptSource", "");

        JSONArray pArray = json.optJSONArray("params");
        if (pArray != null) {
            for (int i = 0; i < pArray.length(); i++) {
                JSONObject pJson = pArray.optJSONObject(i);
                if (pJson != null) {
                    EffectParam p = EffectParam.fromJson(pJson);
                    if (p != null) def.addParam(p);
                }
            }
        }
        return def;
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<EffectParam> getParams() { return params; }
    public String getScriptSource() { return scriptSource; }
    public void setScriptSource(String scriptSource) { this.scriptSource = scriptSource; }
}
