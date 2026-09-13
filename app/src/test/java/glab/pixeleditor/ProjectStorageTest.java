package glab.pixeleditor;

import org.junit.Test;
import static org.junit.Assert.*;

import glab.pixeleditor.model.ProjectStorageManager;

public class ProjectStorageTest {

    @Test
    public void testProjectItemBusinessLogic() {
        ProjectStorageManager.ProjectItem item = new ProjectStorageManager.ProjectItem(
                "proj-123",
                "Cinematic Reel 2026",
                "9:16",
                1080,
                1920,
                60,
                0xFF161D2D,
                4096,
                1789297658000L,
                "thumb_proj-123.png",
                false
        );

        assertEquals("proj-123", item.getId());
        assertEquals("Cinematic Reel 2026", item.getTitle());
        assertEquals("9:16", item.getAspectRatio());
        assertEquals(1080, item.getWidth());
        assertEquals(1920, item.getHeight());
        assertEquals(60, item.getFps());
        assertEquals(0xFF161D2D, item.getBackgroundColor());
        assertEquals(4096, item.getFileSize());
        assertEquals("thumb_proj-123.png", item.getThumbnailName());
        assertFalse(item.isInTrash());

        // Formatting tests
        assertEquals("1080p", item.getFormattedResolution());
        assertEquals("4.0KB", item.getFormattedSize());

        // Test 4K resolution check
        ProjectStorageManager.ProjectItem item4k = new ProjectStorageManager.ProjectItem(
                "proj-4k", "4K Banner", "16:9", 3840, 2160, 30, 0xFF000000, 1024 * 1024 * 2,
                System.currentTimeMillis(), "thumb_4k.png", false
        );
        assertEquals("4K", item4k.getFormattedResolution());
        assertEquals("2.0MB", item4k.getFormattedSize());

        // Test Trash Toggle
        assertFalse(item.isInTrash());
        item.setInTrash(true);
        assertTrue(item.isInTrash());
        item.setInTrash(false);
        assertFalse(item.isInTrash());
    }
}
