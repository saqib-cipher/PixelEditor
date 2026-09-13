package glab.pixeleditor.ui;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Collections;
import java.util.List;

import glab.pixeleditor.MainActivity;
import glab.pixeleditor.R;
import glab.pixeleditor.model.ProjectStorageManager;

public class HomeActivity extends AppCompatActivity implements ProjectAdapter.ProjectActionListener {

    private enum HomeTab {
        PROJECTS,
        ELEMENTS,
        TRASH
    }

    private HomeTab currentTab = HomeTab.PROJECTS;
    private boolean sortDescendingDate = true;

    private TextView tabProjects;
    private TextView tabElements;
    private LinearLayout tabTrash;
    private TextView tvTrashTitle;
    private TextView tvSectionTitle;
    private TextView tvSortLabel;
    private RecyclerView rvProjects;
    private View layoutEmptyState;
    private TextView tvEmptyTitle;
    private TextView tvEmptyDesc;

    private ProjectAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        setupWindowInsets();
        bindViews();
        setupTabs();
        setupSortSelector();
        setupBottomNav();

        // Check if first-launch project storage directory dialog should be shown
        if (!ProjectStorageManager.isFolderSetupDone(this)) {
            showFolderSetupDialog();
        } else {
            loadProjects();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProjects();
    }

    private void setupWindowInsets() {
        View root = findViewById(R.id.homeCoordinator);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
            );

            // Pad top bar
            View topBar = findViewById(R.id.homeTopBar);
            if (topBar != null) {
                topBar.setPadding(insets.left + 16, insets.top, insets.right + 16, 0);
            }

            // Pad bottom nav
            View nav = findViewById(R.id.homeBottomNavContainer);
            if (nav != null) {
                nav.setPadding(insets.left, 0, insets.right, insets.bottom);
            }

            return windowInsets;
        });
    }

    private void bindViews() {
        tabProjects = findViewById(R.id.tabHomeProjects);
        tabElements = findViewById(R.id.tabHomeElements);
        tabTrash = findViewById(R.id.tabHomeTrash);
        tvTrashTitle = findViewById(R.id.tvTrashTabTitle);
        tvSectionTitle = findViewById(R.id.tvProjectsSectionTitle);
        tvSortLabel = findViewById(R.id.tvCurrentSort);
        rvProjects = findViewById(R.id.rvProjects);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyDesc = findViewById(R.id.tvEmptyDesc);

        rvProjects.setLayoutManager(new LinearLayoutManager(this));

        // Drawer Menu Button
        ImageButton btnMenu = findViewById(R.id.btnHomeMenu);
        btnMenu.setOnClickListener(v -> showWorkspaceInfoDialog());

        // Profile Button
        ImageButton btnProfile = findViewById(R.id.btnHomeProfile);
        btnProfile.setOnClickListener(v -> Toast.makeText(this, "PixelEditor Studio Pro Active", Toast.LENGTH_SHORT).show());
    }

    private void setupTabs() {
        tabProjects.setOnClickListener(v -> switchTab(HomeTab.PROJECTS));
        tabElements.setOnClickListener(v -> switchTab(HomeTab.ELEMENTS));
        tabTrash.setOnClickListener(v -> switchTab(HomeTab.TRASH));
    }

    private void switchTab(HomeTab tab) {
        currentTab = tab;

        // Reset backgrounds
        tabProjects.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
        tabProjects.setTextColor(getColor(R.color.pe_text_secondary));

        tabElements.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
        tabElements.setTextColor(getColor(R.color.pe_text_secondary));

        tabTrash.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
        tvTrashTitle.setTextColor(getColor(R.color.pe_text_secondary));

        if (tab == HomeTab.PROJECTS) {
            tabProjects.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1E273C));
            tabProjects.setTextColor(Color.WHITE);
            tvSectionTitle.setText("Your projects");
        } else if (tab == HomeTab.ELEMENTS) {
            tabElements.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1E273C));
            tabElements.setTextColor(Color.WHITE);
            tvSectionTitle.setText("Reusable Elements");
        } else if (tab == HomeTab.TRASH) {
            tabTrash.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1E273C));
            tvTrashTitle.setTextColor(getColor(R.color.pe_accent_danger));
            tvSectionTitle.setText("Trash / Bin");
        }

        loadProjects();
    }

    private void setupSortSelector() {
        View layoutSort = findViewById(R.id.layoutSortBy);
        layoutSort.setOnClickListener(v -> {
            String[] options = {"Sort by Date (Newest first)", "Sort by Date (Oldest first)", "Sort by Name (A-Z)"};
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Sort Projects")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            sortDescendingDate = true;
                            tvSortLabel.setText("Sort by Date");
                        } else if (which == 1) {
                            sortDescendingDate = false;
                            tvSortLabel.setText("Oldest First");
                        } else {
                            tvSortLabel.setText("Sort by Name");
                        }
                        loadProjects();
                    })
                    .show();
        });
    }

    private void setupBottomNav() {
        FloatingActionButton fab = findViewById(R.id.fabCreateProject);
        fab.setOnClickListener(v -> openCreateProjectBottomSheet());

        findViewById(R.id.navBtnHome).setOnClickListener(v -> switchTab(HomeTab.PROJECTS));
        findViewById(R.id.navBtnTutorials).setOnClickListener(v ->
                Toast.makeText(this, "Tutorials & Creative Guides coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.navBtnProjects).setOnClickListener(v -> switchTab(HomeTab.PROJECTS));
        findViewById(R.id.navBtnTemplates).setOnClickListener(v -> {
            openCreateProjectBottomSheet();
        });
    }

    private void openCreateProjectBottomSheet() {
        CreateProjectBottomSheet sheet = new CreateProjectBottomSheet();
        sheet.setOnProjectCreatedListener(item -> loadProjects());
        sheet.show(getSupportFragmentManager(), "CreateProjectBottomSheet");
    }

    private void loadProjects() {
        boolean onlyTrash = (currentTab == HomeTab.TRASH);
        List<ProjectStorageManager.ProjectItem> list = ProjectStorageManager.loadAllProjects(this, onlyTrash);

        // Sorting
        if (tvSortLabel.getText().toString().contains("Name")) {
            Collections.sort(list, (a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
        } else if (sortDescendingDate) {
            Collections.sort(list, (a, b) -> Long.compare(b.getLastModified(), a.getLastModified()));
        } else {
            Collections.sort(list, (a, b) -> Long.compare(a.getLastModified(), b.getLastModified()));
        }

        adapter = new ProjectAdapter(this, onlyTrash, this);
        adapter.setItems(list);
        rvProjects.setAdapter(adapter);

        if (list.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            if (onlyTrash) {
                tvEmptyTitle.setText("Trash is Empty");
                tvEmptyDesc.setText("Deleted projects will be stored here for recovery.");
            } else {
                tvEmptyTitle.setText("No Projects Found");
                tvEmptyDesc.setText("Tap the + button below to create a project.");
            }
        } else {
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void showFolderSetupDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_folder_setup);
        dialog.setCancelable(false);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView tvPath = dialog.findViewById(R.id.tvFolderSetupPath);
        String defaultPath = ProjectStorageManager.getProjectsFolderPath(this);
        tvPath.setText(defaultPath);

        MaterialButton btnUseDefault = dialog.findViewById(R.id.btnUseDefaultFolder);
        MaterialButton btnCustom = dialog.findViewById(R.id.btnChooseCustomFolder);

        btnUseDefault.setOnClickListener(v -> {
            ProjectStorageManager.setProjectsFolderPath(this, defaultPath);
            dialog.dismiss();
            loadProjects();
            Toast.makeText(this, "Project workspace configured successfully", Toast.LENGTH_SHORT).show();
        });

        btnCustom.setOnClickListener(v -> {
            EditText etCustom = new EditText(this);
            etCustom.setText(defaultPath);
            etCustom.setTextColor(Color.WHITE);
            etCustom.setPadding(40, 30, 40, 30);

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Enter Storage Folder Path")
                    .setView(etCustom)
                    .setPositiveButton("Set Folder", (d, which) -> {
                        String customPath = etCustom.getText().toString().trim();
                        if (!customPath.isEmpty()) {
                            ProjectStorageManager.setProjectsFolderPath(this, customPath);
                            dialog.dismiss();
                            loadProjects();
                            Toast.makeText(this, "Workspace set to: " + customPath, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        dialog.show();
    }

    private void showWorkspaceInfoDialog() {
        String currentPath = ProjectStorageManager.getProjectsFolderPath(this);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Workspace Settings")
                .setMessage("Current Project Directory:\n" + currentPath)
                .setPositiveButton("Change Folder", (dialog, which) -> showFolderSetupDialog())
                .setNeutralButton("Close", null)
                .show();
    }

    // --- ProjectAdapter Actions ---
    @Override
    public void onProjectClick(ProjectStorageManager.ProjectItem item) {
        if (item.isInTrash()) {
            Toast.makeText(this, "Restore project first to open it", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("EXTRA_PROJECT_ID", item.getId());
        intent.putExtra("EXTRA_PROJECT_TITLE", item.getTitle());
        intent.putExtra("EXTRA_PROJECT_WIDTH", item.getWidth());
        intent.putExtra("EXTRA_PROJECT_HEIGHT", item.getHeight());
        intent.putExtra("EXTRA_PROJECT_BG", item.getBackgroundColor());
        intent.putExtra("EXTRA_PROJECT_ASPECT", item.getAspectRatio());
        startActivity(intent);
    }

    @Override
    public void onProjectShare(ProjectStorageManager.ProjectItem item) {
        ProjectStorageManager.shareProject(this, item);
    }

    @Override
    public void onProjectDuplicate(ProjectStorageManager.ProjectItem item) {
        ProjectStorageManager.duplicateProject(this, item);
        loadProjects();
        Toast.makeText(this, "Duplicated " + item.getTitle(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProjectMoveToTrash(ProjectStorageManager.ProjectItem item) {
        ProjectStorageManager.moveToTrash(this, item.getId());
        loadProjects();
        Toast.makeText(this, "Moved to Trash", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProjectRestore(ProjectStorageManager.ProjectItem item) {
        ProjectStorageManager.restoreFromTrash(this, item.getId());
        loadProjects();
        Toast.makeText(this, "Restored " + item.getTitle(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProjectDeletePermanent(ProjectStorageManager.ProjectItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Permanently?")
                .setMessage("Are you sure you want to permanently delete \"" + item.getTitle() + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ProjectStorageManager.deletePermanently(this, item.getId());
                    loadProjects();
                    Toast.makeText(this, "Project deleted permanently", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
