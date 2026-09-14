package glab.pixeleditor.effect;

import java.util.ArrayList;
import java.util.List;

public class EffectDefinition {

    private String id;
    private String fileName;
    private String name;
    private String description = "";
    private String category = "Other";
    private String tags = "";
    private String thumbPath = "";
    private boolean isDeprecated = false;
    private final List<EffectParam> params = new ArrayList<>();
    private String shaderSource = "";

    private boolean isEnabled = true;
    private boolean isExpanded = false;

    private final List<String> passTargets = new ArrayList<>();
    private final List<String> passEffects = new ArrayList<>();
    private final java.util.Map<String, Integer> textureDownsamples = new java.util.HashMap<>();
    private final List<String> textureIds = new ArrayList<>();
    private final java.util.Map<String, String> uniformTypes = new java.util.LinkedHashMap<>();

    public EffectDefinition(String id, String fileName, String name, String category) {
        this.id = id;
        this.fileName = fileName;
        this.name = name;
        this.category = category;
    }

    public void addParam(EffectParam param) {
        params.add(param);
    }

    public EffectParam getParam(String paramId) {
        for (EffectParam param : params) {
            if (param.getId().equalsIgnoreCase(paramId)) {
                return param;
            }
        }
        return null;
    }

    public void resetAllParams() {
        for (EffectParam param : params) {
            param.resetToDefault();
        }
    }

    public EffectDefinition copy() {
        EffectDefinition copy = new EffectDefinition(id, fileName, name, category);
        copy.setDescription(description);
        copy.setCategory(category);
        copy.setTags(tags);
        copy.setThumbPath(thumbPath);
        copy.setDeprecated(isDeprecated);
        copy.setShaderSource(shaderSource);
        copy.setEnabled(isEnabled);
        copy.setExpanded(isExpanded);
        for (EffectParam p : params) {
            copy.addParam(p.copy());
        }
        copy.passTargets.addAll(this.passTargets);
        copy.passEffects.addAll(this.passEffects);
        copy.textureDownsamples.putAll(this.textureDownsamples);
        copy.textureIds.addAll(this.textureIds);
        copy.uniformTypes.putAll(this.uniformTypes);
        return copy;
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getThumbPath() { return thumbPath; }
    public void setThumbPath(String thumbPath) { this.thumbPath = thumbPath; }
    public boolean isDeprecated() { return isDeprecated; }
    public void setDeprecated(boolean deprecated) { isDeprecated = deprecated; }
    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { this.isEnabled = enabled; }
    public boolean isExpanded() { return isExpanded; }
    public void setExpanded(boolean expanded) { this.isExpanded = expanded; }
    public List<EffectParam> getParams() { return params; }
    public String getShaderSource() { return shaderSource; }
    public void setShaderSource(String shaderSource) { this.shaderSource = shaderSource; }

    public List<String> getPassTargets() { return passTargets; }
    public List<String> getPassEffects() { return passEffects; }
    public java.util.Map<String, Integer> getTextureDownsamples() { return textureDownsamples; }
    public List<String> getTextureIds() { return textureIds; }
    public java.util.Map<String, String> getUniformTypes() { return uniformTypes; }

    public org.json.JSONObject toJson() {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("id", id);
            json.put("fileName", fileName);
            json.put("name", name);
            json.put("description", description);
            json.put("category", category);
            json.put("tags", tags);
            json.put("thumbPath", thumbPath);
            json.put("isDeprecated", isDeprecated);
            json.put("isEnabled", isEnabled);
            json.put("isExpanded", isExpanded);
            json.put("shaderSource", shaderSource);

            org.json.JSONArray pArray = new org.json.JSONArray();
            for (EffectParam p : params) {
                pArray.put(p.toJson());
            }
            json.put("params", pArray);

            if (!passTargets.isEmpty()) {
                org.json.JSONArray passArr = new org.json.JSONArray();
                for (String t : passTargets) passArr.put(t != null ? t : "");
                json.put("passTargets", passArr);
            }
            if (!passEffects.isEmpty()) {
                org.json.JSONArray effArr = new org.json.JSONArray();
                for (String e : passEffects) effArr.put(e != null ? e : "");
                json.put("passEffects", effArr);
            }
            if (!uniformTypes.isEmpty()) {
                org.json.JSONObject uObj = new org.json.JSONObject();
                for (java.util.Map.Entry<String, String> e : uniformTypes.entrySet()) {
                    uObj.put(e.getKey(), e.getValue());
                }
                json.put("uniformTypes", uObj);
            }
            if (!textureIds.isEmpty()) {
                org.json.JSONArray texArr = new org.json.JSONArray();
                for (String tid : textureIds) texArr.put(tid);
                json.put("textureIds", texArr);
            }
            return json;
        } catch (Exception e) {
            return new org.json.JSONObject();
        }
    }

    public static EffectDefinition fromJson(org.json.JSONObject json) {
        if (json == null) return null;
        String id = json.optString("id", "");
        String fileName = json.optString("fileName", "");
        String name = json.optString("name", "Effect");
        String category = json.optString("category", "Other");

        EffectDefinition eff = new EffectDefinition(id, fileName, name, category);
        eff.description = json.optString("description", "");
        eff.tags = json.optString("tags", "");
        eff.thumbPath = json.optString("thumbPath", "");
        eff.isDeprecated = json.optBoolean("isDeprecated", false);
        eff.isEnabled = json.optBoolean("isEnabled", true);
        eff.isExpanded = json.optBoolean("isExpanded", false);
        eff.shaderSource = json.optString("shaderSource", "");

        org.json.JSONArray pArray = json.optJSONArray("params");
        if (pArray != null) {
            for (int i = 0; i < pArray.length(); i++) {
                org.json.JSONObject pJson = pArray.optJSONObject(i);
                if (pJson != null) {
                    EffectParam p = EffectParam.fromJson(pJson);
                    if (p != null) eff.addParam(p);
                }
            }
        }

        org.json.JSONArray passArr = json.optJSONArray("passTargets");
        if (passArr != null) {
            for (int i = 0; i < passArr.length(); i++) {
                String t = passArr.optString(i, "");
                eff.passTargets.add(t.isEmpty() ? null : t);
            }
        }

        org.json.JSONArray effArr = json.optJSONArray("passEffects");
        if (effArr != null) {
            for (int i = 0; i < effArr.length(); i++) {
                String e = effArr.optString(i, "");
                eff.passEffects.add(e.isEmpty() ? null : e);
            }
        }

        org.json.JSONObject uObj = json.optJSONObject("uniformTypes");
        if (uObj != null) {
            java.util.Iterator<String> keys = uObj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                eff.uniformTypes.put(k, uObj.optString(k, "float"));
            }
        }

        org.json.JSONArray texArr = json.optJSONArray("textureIds");
        if (texArr != null) {
            for (int i = 0; i < texArr.length(); i++) {
                eff.textureIds.add(texArr.optString(i));
            }
        }
        return eff;
    }
}
