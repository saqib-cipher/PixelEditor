package glab.pixeleditor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class EditorProject {

    private String title = "New Project 1";
    private int canvasWidth = 1080;
    private int canvasHeight = 1350;
    private int backgroundColor = 0xFFD8DCE3; // clean canvas neutral grey from screenshot
    private final List<CanvasLayer> layers = new ArrayList<>();
    private int selectedIndex = -1;

    // Undo / Redo history
    private final Stack<List<CanvasLayer>> undoStack = new Stack<>();
    private final Stack<List<CanvasLayer>> redoStack = new Stack<>();

    public EditorProject() {}

    public void saveSnapshot() {
        List<CanvasLayer> snapshot = new ArrayList<>();
        for (CanvasLayer layer : layers) {
            snapshot.add(layer.copy());
        }
        undoStack.push(snapshot);
        redoStack.clear();
        // Cap undo stack to 25 items
        if (undoStack.size() > 25) {
            undoStack.remove(0);
        }
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (!canUndo()) return;
        // Save current to redo
        List<CanvasLayer> current = new ArrayList<>();
        for (CanvasLayer layer : layers) {
            current.add(layer.copy());
        }
        redoStack.push(current);

        List<CanvasLayer> previous = undoStack.pop();
        layers.clear();
        for (CanvasLayer layer : previous) {
            layers.add(layer.copy());
        }
        if (selectedIndex >= layers.size()) {
            selectedIndex = layers.size() - 1;
        }
    }

    public void redo() {
        if (!canRedo()) return;
        List<CanvasLayer> current = new ArrayList<>();
        for (CanvasLayer layer : layers) {
            current.add(layer.copy());
        }
        undoStack.push(current);

        List<CanvasLayer> next = redoStack.pop();
        layers.clear();
        for (CanvasLayer layer : next) {
            layers.add(layer.copy());
        }
        if (selectedIndex >= layers.size()) {
            selectedIndex = layers.size() - 1;
        }
    }

    public void addLayer(CanvasLayer layer) {
        saveSnapshot();
        layers.add(layer);
        selectedIndex = layers.size() - 1;
    }

    public void removeLayer(int index) {
        if (index >= 0 && index < layers.size()) {
            saveSnapshot();
            layers.remove(index);
            if (selectedIndex >= layers.size()) {
                selectedIndex = layers.size() - 1;
            }
        }
    }

    public void duplicateLayer(int index) {
        if (index >= 0 && index < layers.size()) {
            saveSnapshot();
            CanvasLayer dup = layers.get(index).copy();
            layers.add(index + 1, dup);
            selectedIndex = index + 1;
        }
    }

    public void moveLayerUp(int index) {
        if (index >= 0 && index < layers.size() - 1) {
            saveSnapshot();
            CanvasLayer layer = layers.remove(index);
            layers.add(index + 1, layer);
            selectedIndex = index + 1;
        }
    }

    public void moveLayerDown(int index) {
        if (index > 0 && index < layers.size()) {
            saveSnapshot();
            CanvasLayer layer = layers.remove(index);
            layers.add(index - 1, layer);
            selectedIndex = index - 1;
        }
    }

    public CanvasLayer getSelectedLayer() {
        if (selectedIndex >= 0 && selectedIndex < layers.size()) {
            return layers.get(selectedIndex);
        }
        return null;
    }

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getCanvasWidth() { return canvasWidth; }
    public void setCanvasWidth(int canvasWidth) { this.canvasWidth = canvasWidth; }
    public int getCanvasHeight() { return canvasHeight; }
    public void setCanvasHeight(int canvasHeight) { this.canvasHeight = canvasHeight; }
    public int getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(int backgroundColor) { this.backgroundColor = backgroundColor; }
    public List<CanvasLayer> getLayers() { return layers; }
    public int getSelectedIndex() { return selectedIndex; }
    public void setSelectedIndex(int selectedIndex) { this.selectedIndex = selectedIndex; }
}
