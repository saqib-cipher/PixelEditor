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
}
