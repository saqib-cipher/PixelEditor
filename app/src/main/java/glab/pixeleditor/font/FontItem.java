package glab.pixeleditor.font;

public class FontItem {
    private final String name;
    private final String category;
    private final String filePath;
    private boolean isFavorite;

    public FontItem(String name, String category, String filePath, boolean isFavorite) {
        this.name = name;
        this.category = category;
        this.filePath = filePath;
        this.isFavorite = isFavorite;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getFilePath() { return filePath; }
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
}
