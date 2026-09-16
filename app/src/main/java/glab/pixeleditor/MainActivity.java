package glab.pixeleditor;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import glab.pixeleditor.svg.SvgIconItem;
import glab.pixeleditor.svg.SvgIconManager;
import glab.pixeleditor.ui.CanvasLayerSidebarAdapter;
import glab.pixeleditor.ui.SvgIconAdapter;
import glab.pixeleditor.view.CircularDialView;
import glab.pixeleditor.view.ClampedSliderView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import glab.pixeleditor.shape.ShapeDefinition;
import glab.pixeleditor.shape.ShapeDefinition;
import glab.pixeleditor.shape.ShapeGeometryHelper;
import glab.pixeleditor.shape.ShapeHelper;

import glab.pixeleditor.databinding.ActivityMainBinding;
import glab.pixeleditor.effect.EffectControlHelper;
import glab.pixeleditor.effect.EffectDefinition;
import glab.pixeleditor.effect.EffectHelper;
import glab.pixeleditor.effect.EffectParam;
import glab.pixeleditor.model.BorderItem;
import glab.pixeleditor.model.CanvasLayer;
import glab.pixeleditor.model.EditorProject;
import glab.pixeleditor.model.GroupLayer;
import glab.pixeleditor.model.PhotoLayer;
import glab.pixeleditor.model.ProjectStorageManager;
import glab.pixeleditor.model.ShadowItem;
import glab.pixeleditor.model.ShapeLayer;
import glab.pixeleditor.model.TextLayer;
import glab.pixeleditor.ui.AlightColorPickerDialog;
import glab.pixeleditor.ui.AlightColorPickerView;
import glab.pixeleditor.ui.BackgroundRemoverActivity;
import glab.pixeleditor.ui.FontAdapter;
import glab.pixeleditor.font.FontItem;
import glab.pixeleditor.font.FontManager;
import glab.pixeleditor.view.GradientBarView;
import glab.pixeleditor.view.PixelCanvasView;
import glab.pixeleditor.view.ScrubRulerView;

import android.content.res.ColorStateList;
import android.graphics.Paint;

public class MainActivity extends AppCompatActivity implements PixelCanvasView.OnLayerSelectedListener {

    private ActivityMainBinding binding;
    private EditorProject project;
    private ActivityResultLauncher<String> photoPickerLauncher;
    private ActivityResultLauncher<String> mediaFillPickerLauncher;
    private ActivityResultLauncher<Intent> bgRemoverLauncher;
    private final Set<CanvasLayer> selectedMultiLayers = new HashSet<>();

    // ViewFlipper Index constants
    private static final int PANEL_LAYER_MENU = 0;
    private static final int PANEL_EDIT_SHAPE = 1;
    private static final int PANEL_BORDER_SHADOW = 2;
    private static final int PANEL_COLOR_FILL = 3;
    private static final int PANEL_PHOTO_ADJUST = 4;
    private static final int SHEET_ADD_ELEMENT = 5;
    private static final int PANEL_EFFECT_CONTROLS = 6;
    private static final int PANEL_MOVE_TRANSFORM = 7;
    private static final int PANEL_LAYERS_OVERVIEW = 8;
    private static final int PANEL_BLENDING_OPACITY = 9;
    private static final int PANEL_EDIT_TEXT = 10;

    private FontAdapter fontAdapter;
    private String currentFontCategory = FontManager.CATEGORY_FAVORITES;

    private enum TransformMode {
        POSITION,
        ROTATE,
        SCALE,
        SKEW
    }

    private enum PositionAxis {
        XY,
        Z
    }

    private enum ScaleAxis {
        WIDTH,
        HEIGHT,
        BOTH
    }

    private enum SkewAxis {
        X,
        Y
    }

    private TransformMode currentTransformMode = TransformMode.POSITION;
    private PositionAxis activePositionAxis = PositionAxis.XY;
    private ScaleAxis activeScaleAxis = ScaleAxis.BOTH;
    private SkewAxis activeSkewAxis = SkewAxis.X;

    // Transform clipboard for Copy / Paste
    private boolean hasCopiedTransform = false;
    private float copyX, copyY, copyRot, copyScaleX = 1f, copyScaleY = 1f, copyWidth = 300f, copyHeight = 300f, copySkewX = 0f, copySkewY = 0f;

    private boolean isRightToolsVisible = true;

    private CanvasLayerSidebarAdapter sidebarAdapter;

    // Nested Group Edit Mode stack
    private EditorProject rootProject = null;
    private final Stack<GroupEditSession> groupEditSessionStack = new Stack<>();

    private static class GroupEditSession {
        final GroupLayer groupLayer;
        final EditorProject groupProject;

        GroupEditSession(GroupLayer groupLayer, EditorProject groupProject) {
            this.groupLayer = groupLayer;
            this.groupProject = groupProject;
        }
    }

    // Aspect ratio definitions
    private final String[] aspectLabels = {"4:5", "1:1", "9:16", "16:9"};
    private final int[][] aspectDimensions = {
            {1080, 1350}, // 4:5 Instagram Portrait
            {1080, 1080}, // 1:1 Square
            {1080, 1920}, // 9:16 Story / Reels
            {1920, 1080}  // 16:9 Landscape / Thumbnail
    };
    private int currentAspectIndex = 0;

    // Palette Colors
    private final int[] paletteColors = new int[]{
            0xFF7A4B58, 0xFF00E5BC, 0xFF00D2FF, 0xFF7F5AF0,
            0xFFFF007F, 0xFFFFD166, 0xFF06D6A0, 0xFF118AB2,
            0xFFEF476F, 0xFFFFFFFF, 0xFF24304A, 0xFF000000
    };

    // Effects Registry Cache
    private List<EffectDefinition> allEffects = new ArrayList<>();
    private EffectDefinition activeSelectedEffect = null;
    private int currentCategoryIndex = -1;
    private List<String> availableCategories = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Enable Edge-to-Edge display
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 2. Fix Window Insets for immersive edge-to-edge layout
        setupWindowInsets();
        setupBackNavigation();

        initProject();
        initPhotoPicker();
        setupTopBar();
        setupMidToolbar();
        setupLayerMenu();
        setupSubpanels();
        setupAddElementSheet();
        setupEffectBrowser();
        setupEffectControlsPanel();
        setupMoveTransformPanel();
        setupRightCanvasTools();
        setupBottomPanelExpandCollapse();

        // Hide overlay UI elements while eyedropper is active
        binding.canvasView.setEyedropperStateListener(new PixelCanvasView.OnEyedropperStateListener() {
            @Override
            public void onEyedropperStarted() {
                setEyedropperOverlayVisible(false);
            }
            @Override
            public void onEyedropperStopped() {
                setEyedropperOverlayVisible(true);
            }
        });

        // Load all XML effects asynchronously from assets/effects
        loadEffectsAsync();

        updateUIForActiveLayer(project.getSelectedLayer());
    }

    /** Hide or show the canvas overlay buttons (left rail + bottom panel) during eyedropper mode */
    private void setEyedropperOverlayVisible(boolean visible) {
        int vis = visible ? View.VISIBLE : View.INVISIBLE;
        // Left floating action buttons
        View leftRail = binding.getRoot().findViewById(R.id.layoutLeftCanvasTools);
        if (leftRail != null) leftRail.setVisibility(vis);
        // Bottom panel
        if (binding.layoutBottomContainer != null) binding.layoutBottomContainer.setVisibility(vis);
    }


    /**
     * Fixes Window Insets so top bar respects status bar cutout and bottom controls respect gesture/nav bar.
     */
    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootCoordinator, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
            );
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(insets.bottom, imeInsets.bottom);

            float density = getResources().getDisplayMetrics().density;
            int padHoriz = (int) (8 * density);

            // Pad Top Bar for Status Bar & camera notch
            int targetHeight = (int) (54 * density) + insets.top;
            if (binding.topBar.getLayoutParams().height != targetHeight) {
                binding.topBar.getLayoutParams().height = targetHeight;
                binding.topBar.requestLayout();
            }
            binding.topBar.setPadding(
                    insets.left + padHoriz,
                    insets.top,
                    insets.right + padHoriz,
                    0
            );

            // Pad Bottom Controls Container for Navigation Bar and Keyboard (IME)
            binding.layoutBottomContainer.setPadding(
                    insets.left,
                    0,
                    insets.right,
                    bottomInset
            );

            // Pad Effect Browser header and scroll container
            View browserHeader = binding.getRoot().findViewById(R.id.layoutEffectBrowserHeader);
            if (browserHeader != null) {
                browserHeader.setPadding(
                        insets.left,
                        insets.top,
                        insets.right,
                        0
                );
            }

            View browserScroll = binding.getRoot().findViewById(R.id.scrollEffectBrowser);
            if (browserScroll != null) {
                browserScroll.setPadding(
                        insets.left,
                        0,
                        insets.right,
                        bottomInset
                );
            }

            return windowInsets;
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleSmartBack();
            }
        });
    }

    public void handleSmartBack() {
        // 1. If Effect Browser is open, check if viewing a filtered category
        if (binding.containerEffectBrowser.getVisibility() == View.VISIBLE) {
            View layoutFiltered = binding.getRoot().findViewById(R.id.layoutFilteredEffectsGrid);
            if (layoutFiltered != null && layoutFiltered.getVisibility() == View.VISIBLE) {
                showMainCategoryView();
                return;
            }
            binding.containerEffectBrowser.setVisibility(View.GONE);
            return;
        }

        // 2. If inside a subpanel or add element sheet, return to layer menu or overview
        int currentPanel = binding.flipperBottomPanels.getDisplayedChild();
        if (currentPanel != PANEL_LAYER_MENU && currentPanel != PANEL_LAYERS_OVERVIEW) {
            if (project != null && project.getSelectedLayer() != null) {
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
            } else {
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYERS_OVERVIEW);
                populateLayersOverviewPanel();
            }
            return;
        }

        // 3. If a layer is selected, deselect it and show overview panel
        if (project != null && project.getSelectedLayer() != null) {
            project.setSelectedIndex(-1);
            updateUIForActiveLayer(null);
            binding.canvasView.invalidate();
            return;
        }

        // 4. If inside Group Edit Mode, exit to parent project
        if (!groupEditSessionStack.isEmpty()) {
            exitGroupEditMode();
            return;
        }

        // 5. Save and return to Home screen gracefully
        saveCurrentProjectState();
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentProjectState();
    }

    private void saveCurrentProjectState() {
        if (project == null || binding == null || binding.canvasView == null) return;
        try {
            Intent intent = getIntent();
            String projectId = intent != null ? intent.getStringExtra("EXTRA_PROJECT_ID") : null;
            if (projectId == null || projectId.isEmpty()) {
                projectId = UUID.randomUUID().toString();
                if (intent != null) intent.putExtra("EXTRA_PROJECT_ID", projectId);
            }

            // Save full project layers and effect parameters
            ProjectStorageManager.saveProjectContent(this, projectId, project);

            // Capture and save updated thumbnail
            Bitmap thumb = binding.canvasView.exportArtboardBitmap();
            String thumbName = "thumb_" + projectId + ".png";
            if (thumb != null) {
                Bitmap scaled = Bitmap.createScaledBitmap(thumb, 240, 240, true);
                ProjectStorageManager.saveThumbnail(this, thumbName, scaled);
            }

            String aspect = project.getAspectRatio();
            if (aspect == null || aspect.isEmpty()) aspect = "9:16";
            ProjectStorageManager.ProjectItem item = new ProjectStorageManager.ProjectItem(
                    projectId,
                    project.getTitle(),
                    aspect,
                    project.getCanvasWidth(),
                    project.getCanvasHeight(),
                    30,
                    project.getBackgroundColor(),
                    Math.max(1024, project.getLayers().size() * 512L),
                    System.currentTimeMillis(),
                    thumbName,
                    false
            );
            ProjectStorageManager.addOrUpdateProject(this, item);
        } catch (Exception ignored) {}
    }

    private void initProject() {
        Intent intent = getIntent();
        String projectId = intent != null ? intent.getStringExtra("EXTRA_PROJECT_ID") : null;
        String title = intent != null ? intent.getStringExtra("EXTRA_PROJECT_TITLE") : null;
        String aspect = intent != null ? intent.getStringExtra("EXTRA_PROJECT_ASPECT") : null;
        int width = intent != null ? intent.getIntExtra("EXTRA_PROJECT_WIDTH", 1080) : 1080;
        int height = intent != null ? intent.getIntExtra("EXTRA_PROJECT_HEIGHT", 1920) : 1920;
        int bg = intent != null ? intent.getIntExtra("EXTRA_PROJECT_BG", 0xFFD8DCE3) : 0xFFD8DCE3;

        if (projectId != null && !projectId.isEmpty()) {
            project = ProjectStorageManager.loadProjectContent(this, projectId);
        }

        if (project == null) {
            project = new EditorProject();
            project.setTitle(title != null && !title.isEmpty() ? title : "New Project 1");
            project.setCanvasWidth(width);
            project.setCanvasHeight(height);
            project.setBackgroundColor(bg);
            if (aspect != null && !aspect.isEmpty()) {
                project.setAspectRatio(aspect);
            }
        } else {
            if (title != null && !title.isEmpty()) project.setTitle(title);
            if (width > 0) project.setCanvasWidth(width);
            if (height > 0) project.setCanvasHeight(height);
            if (bg != 0) project.setBackgroundColor(bg);
            if (aspect != null && !aspect.isEmpty()) project.setAspectRatio(aspect);
        }

        binding.canvasView.setProject(project);
        binding.canvasView.setOnLayerSelectedListener(this);
    }

    private void initPhotoPicker() {
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        loadPhotoFromUri(uri);
                    }
                }
        );

        mediaFillPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::loadMediaFillFromUri
        );

        bgRemoverLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && BackgroundRemoverActivity.sResultBitmap != null) {
                        CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                        if (layer instanceof PhotoLayer) {
                            PhotoLayer pl = (PhotoLayer) layer;
                            project.saveSnapshot();
                            pl.setBitmap(BackgroundRemoverActivity.sResultBitmap);
                            binding.canvasView.invalidate();
                            updateCanvasToolsState();
                            Toast.makeText(this, "AI Cutout applied to layer", Toast.LENGTH_SHORT).show();
                        }
                    }
                    BackgroundRemoverActivity.sResultBitmap = null;
                    BackgroundRemoverActivity.sInputBitmap = null;
                }
        );
    }

    private void loadMediaFillFromUri(Uri uri) {
        if (uri == null) return;
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            if (bitmap != null) {
                int maxDim = 1200;
                if (bitmap.getWidth() > maxDim || bitmap.getHeight() > maxDim) {
                    float ratio = Math.min((float) maxDim / bitmap.getWidth(), (float) maxDim / bitmap.getHeight());
                    int w = Math.round(ratio * bitmap.getWidth());
                    int h = Math.round(ratio * bitmap.getHeight());
                    bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
                }
                CanvasLayer selectedLayer = project != null ? project.getSelectedLayer() : null;
                if (selectedLayer instanceof ShapeLayer) {
                    ShapeLayer sl = (ShapeLayer) selectedLayer;
                    sl.setMediaBitmap(bitmap);
                    sl.setMediaUri(uri.toString());
                    String name = "media_" + (System.currentTimeMillis() % 10000);
                    sl.setMediaName(name);
                    sl.setFillMode(ShapeLayer.FillMode.MEDIA);
                    populateColorFillPanel(sl);
                    binding.canvasView.invalidate();
                } else if (selectedLayer instanceof PhotoLayer) {
                    PhotoLayer pl = (PhotoLayer) selectedLayer;
                    pl.setBitmap(bitmap);
                    populateColorFillPanel(pl);
                    binding.canvasView.invalidate();
                } else if (selectedLayer instanceof TextLayer) {
                    TextLayer tl = (TextLayer) selectedLayer;
                    tl.setMediaBitmap(bitmap);
                    tl.setMediaUri(uri.toString());
                    String name = "media_" + (System.currentTimeMillis() % 10000);
                    tl.setMediaName(name);
                    tl.setFillMode(ShapeLayer.FillMode.MEDIA);
                    populateColorFillPanel(tl);
                    binding.canvasView.invalidate();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image fill: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPhotoFromUri(Uri uri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            if (bitmap != null) {
                // Scale if too large
                int maxDim = 1200;
                if (bitmap.getWidth() > maxDim || bitmap.getHeight() > maxDim) {
                    float ratio = Math.min((float) maxDim / bitmap.getWidth(), (float) maxDim / bitmap.getHeight());
                    int w = Math.round(ratio * bitmap.getWidth());
                    int h = Math.round(ratio * bitmap.getHeight());
                    bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
                }

                float cx = project.getCanvasWidth() / 2f;
                float cy = project.getCanvasHeight() / 2f;
                float pw = Math.min(project.getCanvasWidth() * 0.8f, bitmap.getWidth());
                float ph = pw * ((float) bitmap.getHeight() / bitmap.getWidth());

                PhotoLayer photoLayer = new PhotoLayer("Photo Layer " + (project.getLayers().size() + 1), bitmap, cx, cy, pw, ph);
                project.addLayer(photoLayer);
                binding.canvasView.invalidate();
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
                updateUIForActiveLayer(photoLayer);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------------------------------------------------
    // 1. TOP BAR SETUP
    // -------------------------------------------------------------
    private void setupTopBar() {
        binding.btnTopBack.setOnClickListener(v -> handleSmartBack());
        binding.layoutTitleBlock.setOnClickListener(v -> {
            if (!groupEditSessionStack.isEmpty()) {
                exitGroupEditMode();
            } else {
                showProjectRenameDialog();
            }
        });

        binding.tvProjectTitle.setText(project.getTitle());
        binding.tvProjectDimensions.setText(project.getCanvasWidth() + " × " + project.getCanvasHeight() + " • " + project.getAspectRatio());

        // Settings (Canvas background)
        binding.btnTopSettings.setOnClickListener(v -> showCanvasSettingsDialog());

        // Export
        binding.btnTopExport.setOnClickListener(v -> showExportDialog());
    }

    private void updateBreadcrumbsUI() {
        if (binding == null) return;
        if (groupEditSessionStack.isEmpty()) {
            binding.ivBreadcrumbArrow.setVisibility(View.GONE);
            binding.tvBreadcrumbGroup.setVisibility(View.GONE);
            binding.tvProjectTitle.setText(project != null ? project.getTitle() : "Project");
            binding.tvProjectDimensions.setVisibility(View.VISIBLE);
        } else {
            binding.ivBreadcrumbArrow.setVisibility(View.VISIBLE);
            binding.tvBreadcrumbGroup.setVisibility(View.VISIBLE);
            binding.tvProjectTitle.setText("<< " + (rootProject != null ? rootProject.getTitle() : "Project"));
            binding.tvBreadcrumbGroup.setText(project != null ? project.getTitle() : "Group");
            binding.tvProjectDimensions.setVisibility(View.GONE);
        }
    }

    private void showProjectRenameDialog() {
        EditText input = new EditText(this);
        input.setText(project.getTitle());
        input.setTextColor(Color.WHITE);
        input.setPadding(40, 30, 40, 30);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename Project")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (!newTitle.isEmpty()) {
                        project.setTitle(newTitle);
                        binding.tvProjectTitle.setText(newTitle);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCanvasSettingsDialog() {
        AlightColorPickerDialog.show(
                this,
                "Canvas Background",
                project.getBackgroundColor(),
                selectedColor -> {
                    project.setBackgroundColor(selectedColor);
                    binding.canvasView.invalidate();
                },
                (dialog, pickerView) -> {
                    Toast.makeText(this, "Tap canvas to pick color", Toast.LENGTH_SHORT).show();
                    binding.canvasView.startEyedropper(color -> {
                        project.setBackgroundColor(color);
                        pickerView.setColor(color, true);
                        binding.canvasView.invalidate();
                    });
                }
        );
    }

    private enum ExportFormat {
        PNG,
        JPEG,
        PROJECT
    }
    private ExportFormat selectedExportFormat = ExportFormat.PNG;
    private float selectedExportScale = 1.0f;
    private int selectedJpegQuality = 95;

    private void showExportDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_export_share, null);
        dialog.setContentView(view);

        MaterialCardView cardPng = view.findViewById(R.id.cardExportPng);
        MaterialCardView cardJpeg = view.findViewById(R.id.cardExportJpeg);
        MaterialCardView cardProject = view.findViewById(R.id.cardExportProject);
        TextView tvPng = view.findViewById(R.id.tvFormatPngTitle);
        TextView tvJpeg = view.findViewById(R.id.tvFormatJpegTitle);
        TextView tvProject = view.findViewById(R.id.tvFormatProjectTitle);

        View sectionResolution = view.findViewById(R.id.sectionResolution);
        View layoutJpegQuality = view.findViewById(R.id.layoutJpegQuality);
        ClampedSliderView sliderQuality = view.findViewById(R.id.sliderJpegQuality);
        TextView tvQualityVal = view.findViewById(R.id.tvJpegQualityVal);

        MaterialButton btn1x = view.findViewById(R.id.btnScale1x);
        MaterialButton btn2x = view.findViewById(R.id.btnScale2x);
        MaterialButton btn4x = view.findViewById(R.id.btnScale4x);

        View btnSave = view.findViewById(R.id.btnSaveExportToGallery);
        View btnShare = view.findViewById(R.id.btnShareExport);
        View btnClose = view.findViewById(R.id.btnCloseExportDialog);

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        Runnable updateFormatUI = () -> {
            cardPng.setStrokeColor(selectedExportFormat == ExportFormat.PNG ? 0xFF00E5BC : 0x22FFFFFF);
            tvPng.setTextColor(selectedExportFormat == ExportFormat.PNG ? 0xFF00E5BC : 0xFFFFFFFF);

            cardJpeg.setStrokeColor(selectedExportFormat == ExportFormat.JPEG ? 0xFF00E5BC : 0x22FFFFFF);
            tvJpeg.setTextColor(selectedExportFormat == ExportFormat.JPEG ? 0xFF00E5BC : 0xFFFFFFFF);

            cardProject.setStrokeColor(selectedExportFormat == ExportFormat.PROJECT ? 0xFF00E5BC : 0x22FFFFFF);
            tvProject.setTextColor(selectedExportFormat == ExportFormat.PROJECT ? 0xFF00E5BC : 0xFFFFFFFF);

            sectionResolution.setVisibility(selectedExportFormat == ExportFormat.PROJECT ? View.GONE : View.VISIBLE);
            layoutJpegQuality.setVisibility(selectedExportFormat == ExportFormat.JPEG ? View.VISIBLE : View.GONE);
        };

        Runnable updateScaleUI = () -> {
            btn1x.setStrokeColor(ColorStateList.valueOf(selectedExportScale == 1.0f ? 0xFF00E5BC : 0x22FFFFFF));
            btn1x.setTextColor(selectedExportScale == 1.0f ? 0xFF00E5BC : 0xFF94A3B8);

            btn2x.setStrokeColor(ColorStateList.valueOf(selectedExportScale == 2.0f ? 0xFF00E5BC : 0x22FFFFFF));
            btn2x.setTextColor(selectedExportScale == 2.0f ? 0xFF00E5BC : 0xFF94A3B8);

            btn4x.setStrokeColor(ColorStateList.valueOf(selectedExportScale == 4.0f ? 0xFF00E5BC : 0x22FFFFFF));
            btn4x.setTextColor(selectedExportScale == 4.0f ? 0xFF00E5BC : 0xFF94A3B8);
        };

        cardPng.setOnClickListener(v -> {
            selectedExportFormat = ExportFormat.PNG;
            updateFormatUI.run();
        });

        cardJpeg.setOnClickListener(v -> {
            selectedExportFormat = ExportFormat.JPEG;
            updateFormatUI.run();
        });

        cardProject.setOnClickListener(v -> {
            selectedExportFormat = ExportFormat.PROJECT;
            updateFormatUI.run();
        });

        btn1x.setOnClickListener(v -> { selectedExportScale = 1.0f; updateScaleUI.run(); });
        btn2x.setOnClickListener(v -> { selectedExportScale = 2.0f; updateScaleUI.run(); });
        btn4x.setOnClickListener(v -> { selectedExportScale = 4.0f; updateScaleUI.run(); });

        if (sliderQuality != null) {
            sliderQuality.setValueFrom(10);
            sliderQuality.setValueTo(100);
            sliderQuality.setValue(selectedJpegQuality);
            sliderQuality.addOnChangeListener((slider, value, fromUser) -> {
                selectedJpegQuality = Math.round(value);
                if (tvQualityVal != null) tvQualityVal.setText(selectedJpegQuality + "%");
            });
        }

        updateFormatUI.run();
        updateScaleUI.run();

        btnSave.setOnClickListener(v -> {
            dialog.dismiss();
            if (selectedExportFormat == ExportFormat.PROJECT) {
                exportProjectBackupFile(false);
            } else {
                Bitmap bmp = binding.canvasView.exportArtboardBitmap(selectedExportScale, selectedExportFormat != ExportFormat.PNG);
                if (bmp == null) return;
                Bitmap.CompressFormat fmt = (selectedExportFormat == ExportFormat.PNG) ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
                String mime = (selectedExportFormat == ExportFormat.PNG) ? "image/png" : "image/jpeg";
                saveImageToGallery(bmp, fmt, mime, selectedJpegQuality);
            }
        });

        btnShare.setOnClickListener(v -> {
            dialog.dismiss();
            if (selectedExportFormat == ExportFormat.PROJECT) {
                exportProjectBackupFile(true);
            } else {
                Bitmap bmp = binding.canvasView.exportArtboardBitmap(selectedExportScale, selectedExportFormat != ExportFormat.PNG);
                if (bmp == null) return;
                Bitmap.CompressFormat fmt = (selectedExportFormat == ExportFormat.PNG) ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
                shareArtworkBitmap(bmp, fmt, selectedJpegQuality);
            }
        });

        dialog.show();
    }

    private void exportProjectBackupFile(boolean share) {
        if (project == null) return;
        try {
            saveCurrentProjectState();
            Intent intent = getIntent();
            String projectId = intent != null ? intent.getStringExtra("EXTRA_PROJECT_ID") : null;
            if (projectId != null && !projectId.isEmpty()) {
                ProjectStorageManager.ProjectItem item = ProjectStorageManager.getProjectById(this, projectId);
                if (item != null) {
                    ProjectStorageManager.shareProject(this, item);
                    return;
                }
            }
            Toast.makeText(this, "Project backup saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export project failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImageToGallery(Bitmap bitmap, Bitmap.CompressFormat format, String mimeType, int quality) {
        String ext = (format == Bitmap.CompressFormat.PNG) ? ".png" : ".jpg";
        String filename = "PixelEditor_" + System.currentTimeMillis() + ext;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelEditor");
        }

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(format, quality, out);
                Toast.makeText(this, "Saved " + (format == Bitmap.CompressFormat.PNG ? "PNG" : "JPEG") + " to Gallery (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ")", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to export: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void shareArtworkBitmap(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        try {
            File cacheDir = new File(getCacheDir(), "shared");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            String ext = (format == Bitmap.CompressFormat.PNG) ? ".png" : ".jpg";
            File file = new File(cacheDir, "PixelEditor_" + System.currentTimeMillis() + ext);
            try (OutputStream out = new FileOutputStream(file)) {
                bitmap.compress(format, quality, out);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(format == Bitmap.CompressFormat.PNG ? "image/png" : "image/jpeg");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share Artwork"));
        } catch (Exception e) {
            Toast.makeText(this, "Failed to share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------------------------------------------------
    // 2. CANVAS TOOLS & MID TOOLBAR SETUP
    // -------------------------------------------------------------
    private void setupMidToolbar() {
        // Floating Left-Side Actions (Copy, Delete, Undo, Redo)
        binding.btnLeftCopy.setOnClickListener(v -> {
            int selected = project != null ? project.getSelectedIndex() : -1;
            if (project != null && selected >= 0 && selected < project.getLayers().size()) {
                project.duplicateLayer(selected);
                binding.canvasView.invalidate();
                updateUIForActiveLayer(project.getSelectedLayer());
                updateCanvasToolsState();
            }
        });

        binding.btnLeftDelete.setOnClickListener(v -> {
            int selected = project != null ? project.getSelectedIndex() : -1;
            if (project != null && selected >= 0 && selected < project.getLayers().size()) {
                project.removeLayer(selected);
                binding.canvasView.invalidate();
                updateUIForActiveLayer(project.getSelectedLayer());
                updateCanvasToolsState();
            }
        });

        binding.btnLeftUndo.setOnClickListener(v -> {
            if (project != null && project.canUndo()) {
                project.undo();
                binding.canvasView.invalidate();
                updateUIForActiveLayer(project.getSelectedLayer());
                updateCanvasToolsState();
            }
        });

        binding.btnLeftRedo.setOnClickListener(v -> {
            if (project != null && project.canRedo()) {
                project.redo();
                binding.canvasView.invalidate();
                updateUIForActiveLayer(project.getSelectedLayer());
                updateCanvasToolsState();
            }
        });

        updateCanvasToolsState();
    }

    public void updateCanvasToolsState() {
        boolean canUndo = project != null && project.canUndo();
        binding.btnLeftUndo.setEnabled(canUndo);
        binding.btnLeftUndo.setAlpha(canUndo ? 1.0f : 0.35f);

        boolean canRedo = project != null && project.canRedo();
        binding.btnLeftRedo.setEnabled(canRedo);
        binding.btnLeftRedo.setAlpha(canRedo ? 1.0f : 0.35f);

        boolean hasLayer = project != null && project.getSelectedLayer() != null;
        binding.btnLeftCopy.setEnabled(hasLayer);
        binding.btnLeftCopy.setAlpha(hasLayer ? 1.0f : 0.35f);

        binding.btnLeftDelete.setEnabled(hasLayer);
        binding.btnLeftDelete.setAlpha(hasLayer ? 1.0f : 0.35f);
    }

    // -------------------------------------------------------------
    // 3. LAYER MENU
    // -------------------------------------------------------------
    private void setupLayerMenu() {
        View menuView = binding.flipperBottomPanels.getChildAt(PANEL_LAYER_MENU);
        if (menuView == null) return;

        // 6 Primary Grid Actions
        menuView.findViewById(R.id.btnToolColorFill).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null) {
                populateColorFillPanel(layer);
            }
            binding.flipperBottomPanels.setDisplayedChild(PANEL_COLOR_FILL);
        });

        menuView.findViewById(R.id.btnToolBorderShadow).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null) {
                populateBorderShadowPanel(layer);
            }
            binding.flipperBottomPanels.setDisplayedChild(PANEL_BORDER_SHADOW);
        });

        menuView.findViewById(R.id.btnToolBlending).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null) {
                populateBlendingOpacityPanel(layer);
            }
            binding.flipperBottomPanels.setDisplayedChild(PANEL_BLENDING_OPACITY);
        });

        menuView.findViewById(R.id.btnToolTransform).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null) {
                openMoveTransformPanel();
            }
        });

        menuView.findViewById(R.id.btnToolEditShape).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer instanceof GroupLayer) {
                enterGroupEditMode((GroupLayer) layer);
            } else if (layer instanceof ShapeLayer) {
                binding.flipperBottomPanels.setDisplayedChild(PANEL_EDIT_SHAPE);
                populateEditShapePanel((ShapeLayer) layer);
            } else if (layer instanceof PhotoLayer) {
                binding.flipperBottomPanels.setDisplayedChild(PANEL_PHOTO_ADJUST);
            } else if (layer instanceof TextLayer) {
                binding.flipperBottomPanels.setDisplayedChild(PANEL_EDIT_TEXT);
                populateEditTextPanel((TextLayer) layer);
            }
        });

        menuView.findViewById(R.id.btnToolEffects).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null && !layer.getAppliedEffects().isEmpty()) {
                openAppliedEffectsPanel();
            } else {
                binding.containerEffectBrowser.setVisibility(View.VISIBLE);
            }
        });
    }

    private void populateEditTextPanel(TextLayer textLayer) {
        if (textLayer == null) return;

        EditText etText = binding.getRoot().findViewById(R.id.etLayerText);
        View swatchColor = binding.getRoot().findViewById(R.id.swatchTextColor);
        ImageButton btnAlignLeft = binding.getRoot().findViewById(R.id.btnTextAlignLeft);
        ImageButton btnAlignCenter = binding.getRoot().findViewById(R.id.btnTextAlignCenter);
        ImageButton btnAlignRight = binding.getRoot().findViewById(R.id.btnTextAlignRight);
        TextView chipRegular = binding.getRoot().findViewById(R.id.chipStyleRegular);
        TextView chipBold = binding.getRoot().findViewById(R.id.chipStyleBold);
        TextView chipItalic = binding.getRoot().findViewById(R.id.chipStyleItalic);
        TextView chipMono = binding.getRoot().findViewById(R.id.chipStyleMono);
        ScrubRulerView rulerSize = binding.getRoot().findViewById(R.id.rulerTextSize);
        TextView tvSizeDisplay = binding.getRoot().findViewById(R.id.tvTextSizeDisplay);
        LinearLayout layoutCategoryChips = binding.getRoot().findViewById(R.id.layoutFontCategoryChips);
        RecyclerView rvFonts = binding.getRoot().findViewById(R.id.rvFontsList);
        View layoutEmptyFonts = binding.getRoot().findViewById(R.id.layoutEmptyFonts);
        TextView tvEmptyFontsTitle = binding.getRoot().findViewById(R.id.tvEmptyFontsTitle);
        TextView tvEmptyFontsSubtitle = binding.getRoot().findViewById(R.id.tvEmptyFontsSubtitle);

        // 1. Text input
        if (etText != null) {
            // Remove old watcher BEFORE setting text to prevent firing on old layer
            Object oldWatcher = etText.getTag();
            if (oldWatcher instanceof TextWatcher) {
                etText.removeTextChangedListener((TextWatcher) oldWatcher);
            }
            etText.setText(textLayer.getText() != null ? textLayer.getText() : "");
            if (textLayer.getText() != null && !textLayer.getText().isEmpty()) {
                etText.setSelection(textLayer.getText().length());
            }
            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    textLayer.setText(s != null ? s.toString() : "");
                    binding.canvasView.invalidate();
                }
                @Override public void afterTextChanged(Editable s) {}
            };
            etText.setTag(watcher);
            etText.addTextChangedListener(watcher);
        }

        // 2. Color swatch
        if (swatchColor != null) {
            swatchColor.setBackgroundColor(textLayer.getTextColor());
            swatchColor.setOnClickListener(v -> {
                AlightColorPickerDialog.show(this, "Text Color", textLayer.getTextColor(), color -> {
                    textLayer.setTextColor(color);
                    swatchColor.setBackgroundColor(color);
                    binding.canvasView.invalidate();
                }, null);
            });
        }

        // 3. Alignment update & listeners
        Runnable updateAlignmentUI = () -> {
            String align = textLayer.getAlignment();
            if (btnAlignLeft != null) {
                btnAlignLeft.setColorFilter("LEFT".equalsIgnoreCase(align) ? 0xFF00E5BC : 0xFF64748B);
            }
            if (btnAlignCenter != null) {
                btnAlignCenter.setColorFilter("CENTER".equalsIgnoreCase(align) ? 0xFF00E5BC : 0xFF64748B);
            }
            if (btnAlignRight != null) {
                btnAlignRight.setColorFilter("RIGHT".equalsIgnoreCase(align) ? 0xFF00E5BC : 0xFF64748B);
            }
        };
        updateAlignmentUI.run();

        if (btnAlignLeft != null) {
            btnAlignLeft.setOnClickListener(v -> {
                textLayer.setAlignment("LEFT");
                updateAlignmentUI.run();
                binding.canvasView.invalidate();
            });
        }
        if (btnAlignCenter != null) {
            btnAlignCenter.setOnClickListener(v -> {
                textLayer.setAlignment("CENTER");
                updateAlignmentUI.run();
                binding.canvasView.invalidate();
            });
        }
        if (btnAlignRight != null) {
            btnAlignRight.setOnClickListener(v -> {
                textLayer.setAlignment("RIGHT");
                updateAlignmentUI.run();
                binding.canvasView.invalidate();
            });
        }

        // 4. Style update & listeners
        Runnable updateStyleUI = () -> {
            boolean isRegular = !textLayer.isBold() && !textLayer.isItalic() && !textLayer.isMonospace();
            if (chipRegular != null) {
                chipRegular.setTextColor(isRegular ? 0xFF00382B : 0xFF94A3B8);
                chipRegular.setBackgroundResource(isRegular ? R.drawable.bg_pill_accent : R.drawable.bg_input_box);
            }
            if (chipBold != null) {
                chipBold.setTextColor(textLayer.isBold() ? 0xFF00382B : 0xFF94A3B8);
                chipBold.setBackgroundResource(textLayer.isBold() ? R.drawable.bg_pill_accent : R.drawable.bg_input_box);
            }
            if (chipItalic != null) {
                chipItalic.setTextColor(textLayer.isItalic() ? 0xFF00382B : 0xFF94A3B8);
                chipItalic.setBackgroundResource(textLayer.isItalic() ? R.drawable.bg_pill_accent : R.drawable.bg_input_box);
            }
            if (chipMono != null) {
                chipMono.setTextColor(textLayer.isMonospace() ? 0xFF00382B : 0xFF94A3B8);
                chipMono.setBackgroundResource(textLayer.isMonospace() ? R.drawable.bg_pill_accent : R.drawable.bg_input_box);
            }
        };
        updateStyleUI.run();

        if (chipRegular != null) {
            chipRegular.setOnClickListener(v -> {
                textLayer.setBold(false);
                textLayer.setItalic(false);
                textLayer.setMonospace(false);
                updateStyleUI.run();
                textLayer.recalculateBounds();
                binding.canvasView.invalidate();
            });
        }
        if (chipBold != null) {
            chipBold.setOnClickListener(v -> {
                textLayer.setBold(!textLayer.isBold());
                updateStyleUI.run();
                textLayer.recalculateBounds();
                binding.canvasView.invalidate();
            });
        }
        if (chipItalic != null) {
            chipItalic.setOnClickListener(v -> {
                textLayer.setItalic(!textLayer.isItalic());
                updateStyleUI.run();
                textLayer.recalculateBounds();
                binding.canvasView.invalidate();
            });
        }
        if (chipMono != null) {
            chipMono.setOnClickListener(v -> {
                textLayer.setMonospace(!textLayer.isMonospace());
                updateStyleUI.run();
                textLayer.recalculateBounds();
                binding.canvasView.invalidate();
            });
        }

        // 5. Text size ruler
        if (tvSizeDisplay != null) {
            tvSizeDisplay.setText(Math.round(textLayer.getTextSize()) + " pt");
        }
        if (rulerSize != null) {
            float curSize = Math.max(8f, Math.min(200f, textLayer.getTextSize()));
            rulerSize.setBounds(8f, 200f, curSize, 0.5f);
            rulerSize.setOnScrubListener(delta -> {
                float newSize = Math.max(8f, Math.min(200f, textLayer.getTextSize() + delta));
                textLayer.setTextSize(newSize);
                rulerSize.setCurrentValue(newSize);
                if (tvSizeDisplay != null) {
                    tvSizeDisplay.setText(Math.round(newSize) + " pt");
                }
                binding.canvasView.invalidate();
            });
        }

        // 6. Fonts List & Categories
        try {
            Runnable updateFontsState = () -> {
                List<FontItem> items = FontManager.getFontsByCategory(this, currentFontCategory);
                if (fontAdapter != null) {
                    fontAdapter.setItems(items, textLayer.getFontPath());
                }
                boolean isEmpty = (items == null || items.isEmpty());
                if (rvFonts != null) {
                    rvFonts.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                }
                if (layoutEmptyFonts != null) {
                    layoutEmptyFonts.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                    if (isEmpty) {
                        if (FontManager.CATEGORY_FAVORITES.equalsIgnoreCase(currentFontCategory)) {
                            if (tvEmptyFontsTitle != null) tvEmptyFontsTitle.setText("No favorite fonts yet");
                            if (tvEmptyFontsSubtitle != null) tvEmptyFontsSubtitle.setText("Tap the star icon next to any font to add it to your favorites.");
                        } else {
                            if (tvEmptyFontsTitle != null) tvEmptyFontsTitle.setText("No fonts found");
                            if (tvEmptyFontsSubtitle != null) tvEmptyFontsSubtitle.setText("No items available in this category.");
                        }
                    }
                }
            };

            if (rvFonts != null) {
                rvFonts.setLayoutManager(new LinearLayoutManager(this));
                if (fontAdapter == null) {
                    fontAdapter = new FontAdapter(this);
                }
                rvFonts.setAdapter(fontAdapter);

                fontAdapter.setOnFontSelectedListener(font -> {
                    textLayer.setFontPath(font.getFilePath());
                    textLayer.setFontName(font.getName());
                    fontAdapter.setSelectedFontPath(font.getFilePath());
                    textLayer.recalculateBounds();
                    binding.canvasView.invalidate();
                });

                fontAdapter.setOnFavoriteToggledListener((font, isFav) -> {
                    if (FontManager.CATEGORY_FAVORITES.equalsIgnoreCase(currentFontCategory)) {
                        updateFontsState.run();
                    }
                });
            }

            // 7. Category Chips
            if (layoutCategoryChips != null) {
                layoutCategoryChips.removeAllViews();
                List<String> categories = FontManager.getCategories(this);
                if (categories == null || categories.isEmpty()) {
                    categories = Collections.singletonList(FontManager.CATEGORY_ALL);
                }
                float density = getResources().getDisplayMetrics().density;

                for (String cat : categories) {
                    TextView chip = new TextView(this);
                    chip.setText(cat);
                    chip.setTextSize(11);
                    chip.setPadding((int) (12 * density), (int) (6 * density), (int) (12 * density), (int) (6 * density));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    lp.setMargins(0, 0, (int) (6 * density), 0);
                    chip.setLayoutParams(lp);

                    boolean isSelected = cat.equalsIgnoreCase(currentFontCategory);
                    chip.setTextColor(isSelected ? 0xFF00382B : 0xFF94A3B8);
                    chip.setBackgroundResource(isSelected ? R.drawable.bg_pill_accent : R.drawable.bg_chip_pill);

                    chip.setOnClickListener(v -> {
                        currentFontCategory = cat;
                        for (int i = 0; i < layoutCategoryChips.getChildCount(); i++) {
                            View child = layoutCategoryChips.getChildAt(i);
                            if (child instanceof TextView) {
                                TextView tv = (TextView) child;
                                boolean sel = tv.getText().toString().equalsIgnoreCase(currentFontCategory);
                                tv.setTextColor(sel ? 0xFF00382B : 0xFF94A3B8);
                                tv.setBackgroundResource(sel ? R.drawable.bg_pill_accent : R.drawable.bg_chip_pill);
                            }
                        }
                        updateFontsState.run();
                    });

                    layoutCategoryChips.addView(chip);
                }

                // Load initial fonts for current category
                updateFontsState.run();
            }
        } catch (Throwable t) {
            Log.e("MainActivity", "Error in populateEditTextPanel: " + t.getMessage(), t);
        }
    }

    private void populateEditShapePanel(ShapeLayer shapeLayer) {
        if (shapeLayer == null) return;
        ShapeDefinition def = shapeLayer.ensureShapeDefinition(this);

        TextView tvTitle = binding.getRoot().findViewById(R.id.tvSubpanelTitle);
        if (tvTitle != null) {
            String name = def != null ? def.getName() : "Shape";
            tvTitle.setText("Edit Shape • " + name);
        }


        // Header Actions
        View btnReset = binding.getRoot().findViewById(R.id.btnResetShapeParams);
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                if (def != null) {
                    def.resetAllParams();
                    populateEditShapePanel(shapeLayer);
                    binding.canvasView.invalidate();
                    Toast.makeText(this, "Reset shape parameters to defaults", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnCdata = binding.getRoot().findViewById(R.id.btnShapeScriptCdata);
        if (btnCdata != null) {
            btnCdata.setOnClickListener(v -> showShapeCdataDialog(shapeLayer));
        }

        View btnChange = binding.getRoot().findViewById(R.id.btnChangeShapeType);
        if (btnChange != null) {
            btnChange.setOnClickListener(v -> showChooseShapeDialog(shapeLayer));
        }

        // Dynamic Parameters from XML
        LinearLayout container = binding.getRoot().findViewById(R.id.containerShapeCustomParams);
        if (container == null) return;
        container.removeAllViews();

        if (def == null || def.getParams().isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No custom parameters for this shape");
            emptyTv.setTextColor(0xFF94A3B8);
            emptyTv.setTextSize(12);
            emptyTv.setPadding(16, 16, 16, 16);
            container.addView(emptyTv);
            return;
        }

        for (EffectParam param : def.getParams()) {
            if (param.getType() == EffectParam.ParamType.COLOR) {
                LinearLayout colorRow = new LinearLayout(this);
                colorRow.setOrientation(LinearLayout.HORIZONTAL);
                colorRow.setGravity(Gravity.CENTER_VERTICAL);
                colorRow.setPadding(8, 8, 8, 8);
                TextView tvC = new TextView(this);
                tvC.setText(param.getLabel());
                tvC.setTextColor(Color.WHITE);
                tvC.setTextSize(13);
                tvC.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                colorRow.addView(tvC);

                View swatch = new View(this);
                int sz = (int) (28 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(sz, sz);
                swatch.setLayoutParams(clp);
                GradientDrawable cd = new GradientDrawable();
                cd.setShape(GradientDrawable.OVAL);
                cd.setColor(param.getColorValue());
                cd.setStroke(2, 0xFFFFFFFF);
                swatch.setBackground(cd);
                swatch.setOnClickListener(v -> {
                    AlightColorPickerDialog.show(this, param.getLabel(), param.getColorValue(), selectedColor -> {
                        param.setColorValue(selectedColor);
                        cd.setColor(selectedColor);
                        swatch.setBackground(cd);
                        binding.canvasView.invalidate();
                    }, null);
                });
                colorRow.addView(swatch);
                container.addView(colorRow);
                continue;
            }

            if (param.getType() == EffectParam.ParamType.SWITCH) {
                LinearLayout switchRow = new LinearLayout(this);
                switchRow.setOrientation(LinearLayout.HORIZONTAL);
                switchRow.setGravity(Gravity.CENTER_VERTICAL);
                switchRow.setPadding(8, 8, 8, 8);
                TextView tvS = new TextView(this);
                tvS.setText(param.getLabel());
                tvS.setTextColor(Color.WHITE);
                tvS.setTextSize(13);
                tvS.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                switchRow.addView(tvS);

                MaterialSwitch ms = new MaterialSwitch(this);
                ms.setChecked(param.getBooleanValue());
                ms.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    param.setBooleanValue(isChecked);
                    binding.canvasView.invalidate();
                });
                switchRow.addView(ms);
                container.addView(switchRow);
                continue;
            }

            View row = getLayoutInflater().inflate(R.layout.item_shape_custom_param, container, false);
            TextView tvLabel = row.findViewById(R.id.tvParamLabel);
            ClampedSliderView slider = row.findViewById(R.id.sliderParam);
            TextView tvVal = row.findViewById(R.id.tvParamValue);

            tvLabel.setText(param.getLabel());

            float min = param.getMinValue();
            float max = param.getMaxValue();
            float step = param.getStep() > 0 ? param.getStep() : 1f;

            slider.setValueFrom(min);
            slider.setValueTo(max);
            slider.setStepSize(step);
            slider.setValue(Math.min(max, Math.max(min, param.getFloatValue())));
            tvVal.setText(param.getFormattedValue());

            slider.addOnChangeListener((s, val, fromUser) -> {
                if (fromUser) {
                    param.setFloatValue(val);
                    tvVal.setText(param.getFormattedValue());
                    binding.canvasView.invalidate();
                }
            });

            container.addView(row);
        }
    }

    private void showShapeCdataDialog(ShapeLayer shapeLayer) {
        if (shapeLayer == null) return;
        ShapeDefinition def = shapeLayer.ensureShapeDefinition(this);
        if (def == null) return;

        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_shape_cdata_editor);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        TextView tvTitle = dialog.findViewById(R.id.tvShapeCdataTitle);
        TextView tvSubtitle = dialog.findViewById(R.id.tvShapeCdataSubtitle);
        EditText etCode = dialog.findViewById(R.id.etShapeCdataCode);
        ImageButton btnClose = dialog.findViewById(R.id.btnCloseShapeCdata);
        View btnReset = dialog.findViewById(R.id.btnResetShapeCdata);
        View btnApply = dialog.findViewById(R.id.btnApplyShapeCdata);

        if (tvTitle != null) tvTitle.setText(def.getName() + " — Script (CDATA)");
        if (tvSubtitle != null) tvSubtitle.setText("assets/shapes/" + def.getFileName() + " • JavaScript getPath()");

        String source = def.getScriptSource();
        if (source == null || source.trim().isEmpty()) {
            source = "// No CDATA script defined for this shape";
        }
        if (etCode != null) etCode.setText(source);

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                ShapeDefinition orig = ShapeHelper.getShapeById(this, def.getFileName());
                if (orig != null && orig.getScriptSource() != null) {
                    if (etCode != null) etCode.setText(orig.getScriptSource());
                    Toast.makeText(this, "Reloaded script from XML asset", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                if (etCode != null) {
                    def.setScriptSource(etCode.getText().toString());
                    binding.canvasView.invalidate();
                    Toast.makeText(this, "Shape script updated and applied!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });
        }

        dialog.show();
    }

    private void showChooseShapeDialog(ShapeLayer shapeLayer) {
        if (shapeLayer == null) return;
        List<ShapeDefinition> allShapes = ShapeHelper.getAllShapes(this);
        if (allShapes == null || allShapes.isEmpty()) return;

        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_choose_shape);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        ImageButton btnClose = dialog.findViewById(R.id.btnCloseChooseShape);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        RecyclerView rv = dialog.findViewById(R.id.rvChooseShapeGrid);
        if (rv != null) {
            rv.setLayoutManager(new GridLayoutManager(this, 3));
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View view = getLayoutInflater().inflate(R.layout.item_choose_shape_card, parent, false);
                    return new RecyclerView.ViewHolder(view) {};
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    ShapeDefinition sDef = allShapes.get(position);
                    ImageView ivIcon = holder.itemView.findViewById(R.id.ivShapeCardIcon);
                    TextView tvName = holder.itemView.findViewById(R.id.tvShapeCardTitle);

                    tvName.setText(sDef.getName());

                    if (ivIcon != null) {
                        Bitmap thumb = ShapeGeometryHelper.renderShapeThumbnail(sDef, 96, 0xFF00E5BC);
                        ivIcon.setImageBitmap(thumb);
                    }

                    View.OnClickListener shapeClick = v -> {
                        shapeLayer.setShapeDefinition(sDef.copy());
                        populateEditShapePanel(shapeLayer);
                        binding.canvasView.invalidate();
                        dialog.dismiss();
                    };
                    holder.itemView.setOnClickListener(shapeClick);
                    if (ivIcon != null) ivIcon.setOnClickListener(shapeClick);
                    if (tvName != null) tvName.setOnClickListener(shapeClick);
                    View cardChoose = holder.itemView.findViewById(R.id.cardChooseShape);
                    if (cardChoose != null) cardChoose.setOnClickListener(shapeClick);
                }

                @Override
                public int getItemCount() {
                    return allShapes.size();
                }
            });
        }

        dialog.show();
    }

    // -------------------------------------------------------------
    // 5. SUBPANELS SETUP
    // -------------------------------------------------------------
    private void setupSubpanels() {
        // Back buttons for sub-panels
        binding.getRoot().findViewById(R.id.btnBackFromEditShape).setOnClickListener(v ->
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));
        binding.getRoot().findViewById(R.id.btnBackFromBorder).setOnClickListener(v ->
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));
        binding.getRoot().findViewById(R.id.btnBackFromColor).setOnClickListener(v ->
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));
        binding.getRoot().findViewById(R.id.btnBackFromAdjust).setOnClickListener(v ->
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));
        View btnBackBlending = binding.getRoot().findViewById(R.id.btnBackFromBlendingOpacity);
        if (btnBackBlending != null) {
            btnBackBlending.setOnClickListener(v ->
                    binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));
        }

        View btnBackText = binding.getRoot().findViewById(R.id.btnBackTextEdit);
        if (btnBackText != null) {
            btnBackText.setOnClickListener(v ->
                    binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));
        }
        View btnDoneText = binding.getRoot().findViewById(R.id.btnDoneTextEdit);
        if (btnDoneText != null) {
            btnDoneText.setOnClickListener(v ->
                    binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));
        }
        binding.getRoot().findViewById(R.id.btnCloseAddSheet).setOnClickListener(v -> {
            binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYERS_OVERVIEW);
            populateLayersOverviewPanel();
        });


        // Subpanel 2: Border & Shadow
        setupBorderShadowPanel();

        // Subpanel 3: Color & Fill (Alight Motion 4-Tab Design with Gradient & Eyedropper)
        setupColorFillPanel();

        // Subpanel 9: Blending & Opacity Panel (Screenshot 2)
        setupBlendingOpacityPanel();

        // Subpanel 4: Photo Size Controls (Width X & Height Y)
        ClampedSliderView sliderPhotoW = binding.getRoot().findViewById(R.id.sliderPhotoWidth);
        ClampedSliderView sliderPhotoH = binding.getRoot().findViewById(R.id.sliderPhotoHeight);
        TextView tvPhotoW = binding.getRoot().findViewById(R.id.tvPhotoWidthVal);
        TextView tvPhotoH = binding.getRoot().findViewById(R.id.tvPhotoHeightVal);
        MaterialSwitch switchAspect =
                binding.getRoot().findViewById(R.id.switchPhotoAspectLock);

        if (sliderPhotoW != null) {
            sliderPhotoW.setValueFrom(20);
            sliderPhotoW.setValueTo(3000);
            sliderPhotoW.addOnChangeListener((slider, value, fromUser) -> {
                CanvasLayer layer = project.getSelectedLayer();
                if (layer instanceof PhotoLayer && fromUser) {
                    PhotoLayer pl = (PhotoLayer) layer;
                    float oldW = pl.getWidth();
                    pl.setWidth(value);
                    if (tvPhotoW != null) tvPhotoW.setText(Math.round(value) + "px");

                    if (switchAspect != null && switchAspect.isChecked() && oldW > 0) {
                        float ratio = value / oldW;
                        float newH = Math.min(3000, Math.max(20, pl.getHeight() * ratio));
                        pl.setHeight(newH);
                        if (sliderPhotoH != null) sliderPhotoH.setValue(newH);
                        if (tvPhotoH != null) tvPhotoH.setText(Math.round(newH) + "px");
                    }
                    binding.canvasView.invalidate();
                    updateTransformCoordinatesUI();
                }
            });
        }

        if (sliderPhotoH != null) {
            sliderPhotoH.setValueFrom(20);
            sliderPhotoH.setValueTo(3000);
            sliderPhotoH.addOnChangeListener((slider, value, fromUser) -> {
                CanvasLayer layer = project.getSelectedLayer();
                if (layer instanceof PhotoLayer && fromUser) {
                    PhotoLayer pl = (PhotoLayer) layer;
                    float oldH = pl.getHeight();
                    pl.setHeight(value);
                    if (tvPhotoH != null) tvPhotoH.setText(Math.round(value) + "px");

                    if (switchAspect != null && switchAspect.isChecked() && oldH > 0) {
                        float ratio = value / oldH;
                        float newW = Math.min(3000, Math.max(20, pl.getWidth() * ratio));
                        pl.setWidth(newW);
                        if (sliderPhotoW != null) sliderPhotoW.setValue(newW);
                        if (tvPhotoW != null) tvPhotoW.setText(Math.round(newW) + "px");
                    }
                    binding.canvasView.invalidate();
                    updateTransformCoordinatesUI();
                }
            });
        }

        // Reset to original photo dimensions
        binding.getRoot().findViewById(R.id.btnResetAdjustments).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer instanceof PhotoLayer) {
                PhotoLayer pl = (PhotoLayer) layer;
                float origW = pl.getOriginalWidth();
                float origH = pl.getOriginalHeight();
                if (origW > 0 && origH > 0) {
                    pl.setWidth(origW);
                    pl.setHeight(origH);
                    sliderPhotoW.setValue(Math.min(3000, Math.max(20, origW)));
                    sliderPhotoH.setValue(Math.min(3000, Math.max(20, origH)));
                    tvPhotoW.setText(Math.round(origW) + "px");
                    tvPhotoH.setText(Math.round(origH) + "px");
                    binding.canvasView.invalidate();
                    updateTransformCoordinatesUI();
                }
            }
        });

        // Fit Canvas
        View btnFitCanvas = binding.getRoot().findViewById(R.id.btnPhotoMatchCanvas);
        if (btnFitCanvas != null) {
            btnFitCanvas.setOnClickListener(v -> {
                CanvasLayer layer = project.getSelectedLayer();
                if (layer instanceof PhotoLayer) {
                    PhotoLayer pl = (PhotoLayer) layer;
                    float cw = project.getCanvasWidth();
                    float ratio = cw / Math.max(1f, pl.getWidth());
                    float newH = Math.min(3000, Math.max(20, pl.getHeight() * ratio));
                    pl.setWidth(cw);
                    pl.setHeight(newH);
                    sliderPhotoW.setValue(Math.min(3000, Math.max(20, cw)));
                    sliderPhotoH.setValue(newH);
                    tvPhotoW.setText(Math.round(cw) + "px");
                    tvPhotoH.setText(Math.round(newH) + "px");
                    binding.canvasView.invalidate();
                    updateTransformCoordinatesUI();
                }
            });
        }

        // Square 1:1
        View btnSquare = binding.getRoot().findViewById(R.id.btnPhotoSquare);
        if (btnSquare != null) {
            btnSquare.setOnClickListener(v -> {
                CanvasLayer layer = project.getSelectedLayer();
                if (layer instanceof PhotoLayer) {
                    PhotoLayer pl = (PhotoLayer) layer;
                    float size = Math.min(pl.getWidth(), pl.getHeight());
                    pl.setWidth(size);
                    pl.setHeight(size);
                    sliderPhotoW.setValue(Math.min(3000, Math.max(20, size)));
                    sliderPhotoH.setValue(Math.min(3000, Math.max(20, size)));
                    tvPhotoW.setText(Math.round(size) + "px");
                    tvPhotoH.setText(Math.round(size) + "px");
                    binding.canvasView.invalidate();
                    updateTransformCoordinatesUI();
                }
            });
        }
        // AI Subject / Background Remover (Both Adjust and Color & Fill panels)
        View.OnClickListener removeBgListener = v -> {
            CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
            if (layer instanceof PhotoLayer) {
                PhotoLayer pl = (PhotoLayer) layer;
                Bitmap bmp = pl.getBitmap();
                if (bmp != null && !bmp.isRecycled()) {
                    BackgroundRemoverActivity.sInputBitmap = bmp;
                    Intent intent = new Intent(this, BackgroundRemoverActivity.class);
                    bgRemoverLauncher.launch(intent);
                }
            }
        };

        View btnRemoveBg = binding.getRoot().findViewById(R.id.btnRemoveBackground);
        if (btnRemoveBg != null) {
            btnRemoveBg.setOnClickListener(removeBgListener);
        }
        View btnRemoveBgFill = binding.getRoot().findViewById(R.id.btnRemoveBgFromFill);
        if (btnRemoveBgFill != null) {
            btnRemoveBgFill.setOnClickListener(removeBgListener);
        }        }
    

    // -------------------------------------------------------------
    // SUBPANEL 2: BORDER & SHADOW (ALIGHT MOTION 3-TAB SYSTEM)
    // -------------------------------------------------------------
    private void setupBorderShadowPanel() {
        ViewFlipper flipper = binding.getRoot().findViewById(R.id.flipperBorderShadowSubpanels);
        MaterialCardView tabStroke = binding.getRoot().findViewById(R.id.tabRailStroke);
        MaterialCardView tabAddBorder = binding.getRoot().findViewById(R.id.tabRailAddBorder);
        MaterialCardView tabShadow = binding.getRoot().findViewById(R.id.tabRailShadow);

        ImageView ivStroke = binding.getRoot().findViewById(R.id.ivRailStroke);
        ImageView ivAddBorder = binding.getRoot().findViewById(R.id.ivRailAddBorder);
        ImageView ivShadow = binding.getRoot().findViewById(R.id.ivRailShadow);

        Runnable updateRailTabs = () -> {
            if (flipper == null || tabStroke == null) return;
            int child = flipper.getDisplayedChild();

            tabStroke.setStrokeColor(child == 0 ? 0xFF00E5BC : 0x22FFFFFF);
            tabStroke.setCardBackgroundColor(child == 0 ? 0xFF243048 : 0xFF1A2234);
            if (ivStroke != null) ivStroke.setImageTintList(ColorStateList.valueOf(child == 0 ? 0xFF00E5BC : 0xFF94A3B8));

            tabAddBorder.setStrokeColor(child == 1 ? 0xFF00E5BC : 0x22FFFFFF);
            tabAddBorder.setCardBackgroundColor(child == 1 ? 0xFF243048 : 0xFF1A2234);
            if (ivAddBorder != null) ivAddBorder.setImageTintList(ColorStateList.valueOf(child == 1 ? 0xFF00E5BC : 0xFF94A3B8));

            tabShadow.setStrokeColor(child == 2 ? 0xFF00E5BC : 0x22FFFFFF);
            tabShadow.setCardBackgroundColor(child == 2 ? 0xFF243048 : 0xFF1A2234);
            if (ivShadow != null) ivShadow.setImageTintList(ColorStateList.valueOf(child == 2 ? 0xFF00E5BC : 0xFF94A3B8));
        };

        if (tabStroke != null) {
            tabStroke.setOnClickListener(v -> {
                if (flipper != null) flipper.setDisplayedChild(0);
                updateRailTabs.run();
            });
        }
        if (tabAddBorder != null) {
            tabAddBorder.setOnClickListener(v -> {
                if (flipper != null) flipper.setDisplayedChild(1);
                updateRailTabs.run();
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                populateBordersList(layer);
            });
        }
        if (tabShadow != null) {
            tabShadow.setOnClickListener(v -> {
                if (flipper != null) flipper.setDisplayedChild(2);
                updateRailTabs.run();
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                populateShadowsList(layer);
            });
        }

        // 1. Primary Stroke Controls
        MaterialSwitch switchStroke =
                binding.getRoot().findViewById(R.id.switchStrokeToggle);
        ClampedSliderView sliderStroke = binding.getRoot().findViewById(R.id.sliderStrokeWidth);
        TextView tvStrokeVal = binding.getRoot().findViewById(R.id.tvStrokeVal);
        View viewStrokeSwatch = binding.getRoot().findViewById(R.id.viewStrokeColorSwatch);

        MaterialButton btnInside = binding.getRoot().findViewById(R.id.btnBorderAlignInside);
        MaterialButton btnCenter = binding.getRoot().findViewById(R.id.btnBorderAlignCenter);
        MaterialButton btnOutside = binding.getRoot().findViewById(R.id.btnBorderAlignOutside);

        MaterialButton btnJoinRound = binding.getRoot().findViewById(R.id.btnJoinRound);
        MaterialButton btnJoinMiter = binding.getRoot().findViewById(R.id.btnJoinMiter);
        MaterialButton btnJoinBevel = binding.getRoot().findViewById(R.id.btnJoinBevel);

        if (switchStroke != null) {
            switchStroke.setOnCheckedChangeListener((btn, isChecked) -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setHasStroke(isChecked);
                    binding.canvasView.invalidate();
                }
            });
        }

        if (sliderStroke != null) {
            sliderStroke.setValueFrom(0);
            sliderStroke.setValueTo(60);
            sliderStroke.addOnChangeListener((slider, value, fromUser) -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer && fromUser) {
                    ((ShapeLayer) layer).setStrokeWidth(value);
                    if (value > 0 && !((ShapeLayer) layer).isHasStroke()) {
                        ((ShapeLayer) layer).setHasStroke(true);
                        if (switchStroke != null) switchStroke.setChecked(true);
                    }
                    if (tvStrokeVal != null) tvStrokeVal.setText(String.format(Locale.US, "%.1f", value));
                    binding.canvasView.invalidate();
                }
            });
        }

        if (viewStrokeSwatch != null) {
            viewStrokeSwatch.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                int curColor = (layer instanceof ShapeLayer) ? ((ShapeLayer) layer).getStrokeColor() : 0xFF00E5BC;
                AlightColorPickerDialog.show(MainActivity.this, "Stroke Color", curColor, selectedColor -> {
                    if (layer instanceof ShapeLayer) {
                        ((ShapeLayer) layer).setStrokeColor(selectedColor);
                        viewStrokeSwatch.setBackgroundTintList(ColorStateList.valueOf(selectedColor));
                        binding.canvasView.invalidate();
                    }
                }, (dialog, pickerView) -> {
                    Toast.makeText(MainActivity.this, "Tap canvas to pick color", Toast.LENGTH_SHORT).show();
                    binding.canvasView.startEyedropper(color -> {
                        if (layer instanceof ShapeLayer) {
                            ((ShapeLayer) layer).setStrokeColor(color);
                            pickerView.setColor(color, true);
                            viewStrokeSwatch.setBackgroundTintList(ColorStateList.valueOf(color));
                            binding.canvasView.invalidate();
                        }
                    });
                });
            });
        }

        Runnable updateAlignButtons = () -> {
            CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
            BorderItem.Alignment align = (layer instanceof ShapeLayer) ? ((ShapeLayer) layer).getStrokeAlignment() : BorderItem.Alignment.CENTER;
            if (btnInside != null) {
                btnInside.setTextColor(align == BorderItem.Alignment.INSIDE ? 0xFF00E5BC : 0xFF94A3B8);
                btnInside.setTypeface(null, align == BorderItem.Alignment.INSIDE ? Typeface.BOLD : Typeface.NORMAL);
            }
            if (btnCenter != null) {
                btnCenter.setTextColor(align == BorderItem.Alignment.CENTER ? 0xFF00E5BC : 0xFF94A3B8);
                btnCenter.setTypeface(null, align == BorderItem.Alignment.CENTER ? Typeface.BOLD : Typeface.NORMAL);
            }
            if (btnOutside != null) {
                btnOutside.setTextColor(align == BorderItem.Alignment.OUTSIDE ? 0xFF00E5BC : 0xFF94A3B8);
                btnOutside.setTypeface(null, align == BorderItem.Alignment.OUTSIDE ? Typeface.BOLD : Typeface.NORMAL);
            }
        };

        if (btnInside != null) {
            btnInside.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setStrokeAlignment(BorderItem.Alignment.INSIDE);
                    updateAlignButtons.run();
                    binding.canvasView.invalidate();
                }
            });
        }
        if (btnCenter != null) {
            btnCenter.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setStrokeAlignment(BorderItem.Alignment.CENTER);
                    updateAlignButtons.run();
                    binding.canvasView.invalidate();
                }
            });
        }
        if (btnOutside != null) {
            btnOutside.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setStrokeAlignment(BorderItem.Alignment.OUTSIDE);
                    updateAlignButtons.run();
                    binding.canvasView.invalidate();
                }
            });
        }

        Runnable updateJoinButtons = () -> {
            CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
            Paint.Join join = (layer instanceof ShapeLayer) ? ((ShapeLayer) layer).getStrokeJoin() : Paint.Join.ROUND;
            if (btnJoinRound != null) {
                btnJoinRound.setTextColor(join == Paint.Join.ROUND ? 0xFF00E5BC : 0xFF94A3B8);
            }
            if (btnJoinMiter != null) {
                btnJoinMiter.setTextColor(join == Paint.Join.MITER ? 0xFF00E5BC : 0xFF94A3B8);
            }
            if (btnJoinBevel != null) {
                btnJoinBevel.setTextColor(join == Paint.Join.BEVEL ? 0xFF00E5BC : 0xFF94A3B8);
            }
        };

        if (btnJoinRound != null) {
            btnJoinRound.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setStrokeJoin(Paint.Join.ROUND);
                    updateJoinButtons.run();
                    binding.canvasView.invalidate();
                }
            });
        }
        if (btnJoinMiter != null) {
            btnJoinMiter.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setStrokeJoin(Paint.Join.MITER);
                    updateJoinButtons.run();
                    binding.canvasView.invalidate();
                }
            });
        }
        if (btnJoinBevel != null) {
            btnJoinBevel.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setStrokeJoin(Paint.Join.BEVEL);
                    updateJoinButtons.run();
                    binding.canvasView.invalidate();
                }
            });
        }

        // 2. Add Multi-Border Button
        View btnAddBorder = binding.getRoot().findViewById(R.id.btnAddBorderItem);
        if (btnAddBorder != null) {
            btnAddBorder.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer != null) {
                    BorderItem border = new BorderItem(0xFF00E5BC, 4f, BorderItem.Alignment.CENTER);
                    layer.addBorder(border);
                    populateBordersList(layer);
                    binding.canvasView.invalidate();
                }
            });
        }

        // 3. Add Multi-Shadow Button
        View btnAddShadow = binding.getRoot().findViewById(R.id.btnAddShadowItem);
        if (btnAddShadow != null) {
            btnAddShadow.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer != null) {
                    ShadowItem shadow = new ShadowItem(0xFF000000, 16f, 0.6f, 0f, 8f);
                    layer.addShadow(shadow);
                    populateShadowsList(layer);
                    binding.canvasView.invalidate();
                }
            });
        }

        // 4. Quick Reset Defaults
        View btnReset = binding.getRoot().findViewById(R.id.btnResetBorderShadow);
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer != null) {
                    if (layer instanceof ShapeLayer) {
                        ((ShapeLayer) layer).setHasStroke(false);
                        ((ShapeLayer) layer).setStrokeWidth(0f);
                        ((ShapeLayer) layer).setStrokeColor(0xFF00E5BC);
                        ((ShapeLayer) layer).setStrokeAlignment(BorderItem.Alignment.CENTER);
                        ((ShapeLayer) layer).setStrokeJoin(Paint.Join.ROUND);
                    }
                    layer.clearBorders();
                    layer.clearShadows();
                    populateBorderShadowPanel(layer);
                    binding.canvasView.invalidate();
                }
            });
        }
    }

    private void populateBorderShadowPanel(CanvasLayer layer) {
        if (layer == null) return;

        MaterialSwitch switchStroke =
                binding.getRoot().findViewById(R.id.switchStrokeToggle);
        ClampedSliderView sliderStroke = binding.getRoot().findViewById(R.id.sliderStrokeWidth);
        TextView tvStrokeVal = binding.getRoot().findViewById(R.id.tvStrokeVal);
        View viewStrokeSwatch = binding.getRoot().findViewById(R.id.viewStrokeColorSwatch);

        MaterialButton btnInside = binding.getRoot().findViewById(R.id.btnBorderAlignInside);
        MaterialButton btnCenter = binding.getRoot().findViewById(R.id.btnBorderAlignCenter);
        MaterialButton btnOutside = binding.getRoot().findViewById(R.id.btnBorderAlignOutside);

        MaterialButton btnJoinRound = binding.getRoot().findViewById(R.id.btnJoinRound);
        MaterialButton btnJoinMiter = binding.getRoot().findViewById(R.id.btnJoinMiter);
        MaterialButton btnJoinBevel = binding.getRoot().findViewById(R.id.btnJoinBevel);

        if (layer instanceof ShapeLayer) {
            ShapeLayer sl = (ShapeLayer) layer;
            if (switchStroke != null) switchStroke.setChecked(sl.isHasStroke());
            if (sliderStroke != null) sliderStroke.setValue(sl.getStrokeWidth());
            if (tvStrokeVal != null) tvStrokeVal.setText(String.format(Locale.US, "%.1f", sl.getStrokeWidth()));
            if (viewStrokeSwatch != null) {
                viewStrokeSwatch.setBackgroundTintList(ColorStateList.valueOf(sl.getStrokeColor()));
            }

            BorderItem.Alignment align = sl.getStrokeAlignment();
            if (btnInside != null) {
                btnInside.setTextColor(align == BorderItem.Alignment.INSIDE ? 0xFF00E5BC : 0xFF94A3B8);
                btnInside.setTypeface(null, align == BorderItem.Alignment.INSIDE ? Typeface.BOLD : Typeface.NORMAL);
            }
            if (btnCenter != null) {
                btnCenter.setTextColor(align == BorderItem.Alignment.CENTER ? 0xFF00E5BC : 0xFF94A3B8);
                btnCenter.setTypeface(null, align == BorderItem.Alignment.CENTER ? Typeface.BOLD : Typeface.NORMAL);
            }
            if (btnOutside != null) {
                btnOutside.setTextColor(align == BorderItem.Alignment.OUTSIDE ? 0xFF00E5BC : 0xFF94A3B8);
                btnOutside.setTypeface(null, align == BorderItem.Alignment.OUTSIDE ? Typeface.BOLD : Typeface.NORMAL);
            }

            Paint.Join join = sl.getStrokeJoin();
            if (btnJoinRound != null) btnJoinRound.setTextColor(join == Paint.Join.ROUND ? 0xFF00E5BC : 0xFF94A3B8);
            if (btnJoinMiter != null) btnJoinMiter.setTextColor(join == Paint.Join.MITER ? 0xFF00E5BC : 0xFF94A3B8);
            if (btnJoinBevel != null) btnJoinBevel.setTextColor(join == Paint.Join.BEVEL ? 0xFF00E5BC : 0xFF94A3B8);
        }

        populateBordersList(layer);
        populateShadowsList(layer);
    }

    private void populateBordersList(CanvasLayer layer) {
        LinearLayout container = binding.getRoot().findViewById(R.id.containerBordersList);
        TextView tvCount = binding.getRoot().findViewById(R.id.tvBordersCount);
        if (container == null) return;

        int count = (layer != null) ? layer.getBorders().size() : 0;
        if (tvCount != null) {
            tvCount.setText("Borders (" + count + ")");
        }

        container.removeAllViews();
        if (layer == null) return;

        List<BorderItem> borders = layer.getBorders();
        for (int i = 0; i < borders.size(); i++) {
            final int index = i;
            BorderItem item = borders.get(i);
            View card = getLayoutInflater().inflate(R.layout.item_border_card, container, false);

            TextView tvTitle = card.findViewById(R.id.tvBorderItemTitle);
            View viewSwatch = card.findViewById(R.id.viewBorderItemSwatch);
            MaterialSwitch sw = card.findViewById(R.id.switchBorderItem);
            View btnDelete = card.findViewById(R.id.btnDeleteBorderItem);
            ClampedSliderView sliderW = card.findViewById(R.id.sliderBorderItemWidth);
            TextView tvWVal = card.findViewById(R.id.tvBorderItemWidthVal);

            MaterialButton btnIn = card.findViewById(R.id.btnItemAlignInside);
            MaterialButton btnCtr = card.findViewById(R.id.btnItemAlignCenter);
            MaterialButton btnOut = card.findViewById(R.id.btnItemAlignOutside);

            if (tvTitle != null) tvTitle.setText("Border " + (index + 1));
            if (viewSwatch != null) {
                viewSwatch.setBackgroundTintList(ColorStateList.valueOf(item.getColor()));
                viewSwatch.setOnClickListener(v -> {
                    AlightColorPickerDialog.show(MainActivity.this, "Border " + (index + 1) + " Color", item.getColor(), col -> {
                        item.setColor(col);
                        viewSwatch.setBackgroundTintList(ColorStateList.valueOf(col));
                        binding.canvasView.invalidate();
                    }, (dialog, pickerView) -> {
                        Toast.makeText(MainActivity.this, "Tap canvas to pick color", Toast.LENGTH_SHORT).show();
                        binding.canvasView.startEyedropper(col -> {
                            item.setColor(col);
                            pickerView.setColor(col, true);
                            viewSwatch.setBackgroundTintList(ColorStateList.valueOf(col));
                            binding.canvasView.invalidate();
                        });
                    });
                });
            }

            if (sw != null) {
                sw.setChecked(item.isEnabled());
                sw.setOnCheckedChangeListener((btn, isChecked) -> {
                    item.setEnabled(isChecked);
                    binding.canvasView.invalidate();
                });
            }

            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> {
                    layer.removeBorder(index);
                    populateBordersList(layer);
                    binding.canvasView.invalidate();
                });
            }

            if (sliderW != null) {
                sliderW.setValueFrom(0);
                sliderW.setValueTo(60);
                sliderW.setValue(item.getWidth());
                if (tvWVal != null) tvWVal.setText(String.format(Locale.US, "%.1f", item.getWidth()));
                sliderW.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser) {
                        item.setWidth(value);
                        if (tvWVal != null) tvWVal.setText(String.format(Locale.US, "%.1f", value));
                        binding.canvasView.invalidate();
                    }
                });
            }

            Runnable updateAlignUI = () -> {
                BorderItem.Alignment a = item.getAlignment();
                if (btnIn != null) {
                    btnIn.setTextColor(a == BorderItem.Alignment.INSIDE ? 0xFF00E5BC : 0xFF94A3B8);
                    btnIn.setTypeface(null, a == BorderItem.Alignment.INSIDE ? Typeface.BOLD : Typeface.NORMAL);
                }
                if (btnCtr != null) {
                    btnCtr.setTextColor(a == BorderItem.Alignment.CENTER ? 0xFF00E5BC : 0xFF94A3B8);
                    btnCtr.setTypeface(null, a == BorderItem.Alignment.CENTER ? Typeface.BOLD : Typeface.NORMAL);
                }
                if (btnOut != null) {
                    btnOut.setTextColor(a == BorderItem.Alignment.OUTSIDE ? 0xFF00E5BC : 0xFF94A3B8);
                    btnOut.setTypeface(null, a == BorderItem.Alignment.OUTSIDE ? Typeface.BOLD : Typeface.NORMAL);
                }
            };
            updateAlignUI.run();

            if (btnIn != null) {
                btnIn.setOnClickListener(v -> {
                    item.setAlignment(BorderItem.Alignment.INSIDE);
                    updateAlignUI.run();
                    binding.canvasView.invalidate();
                });
            }
            if (btnCtr != null) {
                btnCtr.setOnClickListener(v -> {
                    item.setAlignment(BorderItem.Alignment.CENTER);
                    updateAlignUI.run();
                    binding.canvasView.invalidate();
                });
            }
            if (btnOut != null) {
                btnOut.setOnClickListener(v -> {
                    item.setAlignment(BorderItem.Alignment.OUTSIDE);
                    updateAlignUI.run();
                    binding.canvasView.invalidate();
                });
            }

            container.addView(card);
        }
    }

    private void populateShadowsList(CanvasLayer layer) {
        LinearLayout container = binding.getRoot().findViewById(R.id.containerShadowsList);
        TextView tvCount = binding.getRoot().findViewById(R.id.tvShadowsCount);
        if (container == null) return;

        int count = (layer != null) ? layer.getShadows().size() : 0;
        if (tvCount != null) {
            tvCount.setText("Shadows (" + count + ")");
        }

        container.removeAllViews();
        if (layer == null) return;

        List<ShadowItem> shadows = layer.getShadows();
        for (int i = 0; i < shadows.size(); i++) {
            final int index = i;
            ShadowItem item = shadows.get(i);
            View card = getLayoutInflater().inflate(R.layout.item_shadow_card, container, false);

            TextView tvTitle = card.findViewById(R.id.tvShadowItemTitle);
            View viewSwatch = card.findViewById(R.id.viewShadowItemSwatch);
            MaterialSwitch sw = card.findViewById(R.id.switchShadowItem);
            View btnDelete = card.findViewById(R.id.btnDeleteShadowItem);

            ClampedSliderView sliderSize = card.findViewById(R.id.sliderShadowItemSize);
            TextView tvSizeVal = card.findViewById(R.id.tvShadowItemSizeVal);

            ClampedSliderView sliderAlpha = card.findViewById(R.id.sliderShadowItemAlpha);
            TextView tvAlphaVal = card.findViewById(R.id.tvShadowItemAlphaVal);

            ClampedSliderView sliderPosX = card.findViewById(R.id.sliderShadowItemPosX);
            TextView tvPosXVal = card.findViewById(R.id.tvShadowItemPosXVal);

            ClampedSliderView sliderPosY = card.findViewById(R.id.sliderShadowItemPosY);
            TextView tvPosYVal = card.findViewById(R.id.tvShadowItemPosYVal);

            if (tvTitle != null) tvTitle.setText("Shadow " + (index + 1));
            if (viewSwatch != null) {
                int swatchAlpha = Math.round(item.getAlpha() * 255f);
                int previewCol = Color.argb(swatchAlpha, Color.red(item.getColor()), Color.green(item.getColor()), Color.blue(item.getColor()));
                viewSwatch.setBackgroundTintList(ColorStateList.valueOf(previewCol));

                viewSwatch.setOnClickListener(v -> {
                    AlightColorPickerDialog.show(MainActivity.this, "Shadow " + (index + 1) + " Color", item.getColor(), col -> {
                        item.setColor(col);
                        int updatedCol = Color.argb(Math.round(item.getAlpha() * 255f), Color.red(col), Color.green(col), Color.blue(col));
                        viewSwatch.setBackgroundTintList(ColorStateList.valueOf(updatedCol));
                        binding.canvasView.invalidate();
                    }, null);
                });
            }

            if (sw != null) {
                sw.setChecked(item.isEnabled());
                sw.setOnCheckedChangeListener((btn, isChecked) -> {
                    item.setEnabled(isChecked);
                    binding.canvasView.invalidate();
                });
            }

            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> {
                    layer.removeShadow(index);
                    populateShadowsList(layer);
                    binding.canvasView.invalidate();
                });
            }

            // Size (Blur Radius 0 - 80)
            if (sliderSize != null) {
                sliderSize.setValueFrom(0);
                sliderSize.setValueTo(80);
                sliderSize.setValue(item.getSize());
                if (tvSizeVal != null) tvSizeVal.setText(Math.round(item.getSize()) + "px");
                sliderSize.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser) {
                        item.setSize(value);
                        if (tvSizeVal != null) tvSizeVal.setText(Math.round(value) + "px");
                        binding.canvasView.invalidate();
                    }
                });
            }

            // Alpha (Opacity 0 - 100%)
            if (sliderAlpha != null) {
                sliderAlpha.setValueFrom(0);
                sliderAlpha.setValueTo(100);
                sliderAlpha.setValue(item.getAlpha() * 100f);
                if (tvAlphaVal != null) tvAlphaVal.setText(Math.round(item.getAlpha() * 100f) + "%");
                sliderAlpha.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser) {
                        item.setAlpha(value / 100f);
                        if (tvAlphaVal != null) tvAlphaVal.setText(Math.round(value) + "%");
                        if (viewSwatch != null) {
                            int updatedCol = Color.argb(Math.round(item.getAlpha() * 255f), Color.red(item.getColor()), Color.green(item.getColor()), Color.blue(item.getColor()));
                            viewSwatch.setBackgroundTintList(ColorStateList.valueOf(updatedCol));
                        }
                        binding.canvasView.invalidate();
                    }
                });
            }

            // Pos X (-100 to +100)
            if (sliderPosX != null) {
                sliderPosX.setValueFrom(-100);
                sliderPosX.setValueTo(100);
                sliderPosX.setValue(item.getOffsetX());
                if (tvPosXVal != null) tvPosXVal.setText(Math.round(item.getOffsetX()) + "");
                sliderPosX.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser) {
                        item.setOffsetX(value);
                        if (tvPosXVal != null) tvPosXVal.setText(Math.round(value) + "");
                        binding.canvasView.invalidate();
                    }
                });
            }

            // Pos Y (-100 to +100)
            if (sliderPosY != null) {
                sliderPosY.setValueFrom(-100);
                sliderPosY.setValueTo(100);
                sliderPosY.setValue(item.getOffsetY());
                if (tvPosYVal != null) tvPosYVal.setText(Math.round(item.getOffsetY()) + "");
                sliderPosY.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser) {
                        item.setOffsetY(value);
                        if (tvPosYVal != null) tvPosYVal.setText(Math.round(value) + "");
                        binding.canvasView.invalidate();
                    }
                });
            }

            container.addView(card);
        }
    }

    // -------------------------------------------------------------
    // SUBPANEL 3: COLOR & FILL (Alight Motion 4-Tab System with Gradient & Eyedropper & Media Fill)
    // -------------------------------------------------------------
    private void setupColorFillPanel() {
        AlightColorPickerView colorPicker = binding.getRoot().findViewById(R.id.panelAlightColorPicker);
        ViewFlipper flipperFill = binding.getRoot().findViewById(R.id.flipperFillSubpanels);
        MaterialCardView tabNone = binding.getRoot().findViewById(R.id.tabFillNone);
        MaterialCardView tabSolid = binding.getRoot().findViewById(R.id.tabFillSolid);
        MaterialCardView tabGradient = binding.getRoot().findViewById(R.id.tabFillGradient);
        MaterialCardView tabMedia = binding.getRoot().findViewById(R.id.tabFillMedia);

        ImageView ivNone = binding.getRoot().findViewById(R.id.ivTabNoneIcon);
        ImageView ivSolid = binding.getRoot().findViewById(R.id.ivTabSolidIcon);
        ImageView ivGrad = binding.getRoot().findViewById(R.id.ivTabGradientIcon);
        ImageView ivMedia = binding.getRoot().findViewById(R.id.ivTabMediaIcon);

        View previewFill = binding.getRoot().findViewById(R.id.viewCurrentFillColorPreview);

        MaterialCardView btnGradTypeLinear = binding.getRoot().findViewById(R.id.btnGradTypeLinear);
        MaterialCardView btnGradTypeRadial = binding.getRoot().findViewById(R.id.btnGradTypeRadial);
        MaterialCardView btnGradTypeSweep = binding.getRoot().findViewById(R.id.btnGradTypeSweep);
        ImageView ivGradLinear = binding.getRoot().findViewById(R.id.ivGradTypeLinear);
        ImageView ivGradRadial = binding.getRoot().findViewById(R.id.ivGradTypeRadial);
        ImageView ivGradSweep = binding.getRoot().findViewById(R.id.ivGradTypeSweep);

        GradientBarView gradientBarView = binding.getRoot().findViewById(R.id.gradientBarView);
        View btnGradStart = binding.getRoot().findViewById(R.id.btnGradStartColor);
        View btnGradEnd = binding.getRoot().findViewById(R.id.btnGradEndColor);
        View btnGradStartContainer = binding.getRoot().findViewById(R.id.btnGradStartContainer);
        View btnGradEndContainer = binding.getRoot().findViewById(R.id.btnGradEndContainer);
        View btnReverse = binding.getRoot().findViewById(R.id.btnReverseGradient);
        View btnEyedropper = binding.getRoot().findViewById(R.id.btnGradEyedropper);

        // Media Fill scaling & picking controls (Screenshot 1)
        MaterialButton btnMediaScaleFill = binding.getRoot().findViewById(R.id.btnMediaScaleFill);
        MaterialButton btnMediaScaleFit = binding.getRoot().findViewById(R.id.btnMediaScaleFit);
        MaterialButton btnMediaScaleStretch = binding.getRoot().findViewById(R.id.btnMediaScaleStretch);
        View cardMediaSelector = binding.getRoot().findViewById(R.id.cardMediaFileSelector);
        View btnPickMedia = binding.getRoot().findViewById(R.id.btnPickMediaFill);

        Runnable updateTabsState = () -> {
            if (flipperFill == null || tabNone == null) return;
            int child = flipperFill.getDisplayedChild();
            tabNone.setStrokeColor(child == 0 ? 0xFF00E5BC : 0x22FFFFFF);
            if (ivNone != null) ivNone.setImageTintList(ColorStateList.valueOf(child == 0 ? 0xFF00E5BC : 0xFF94A3B8));
            tabSolid.setStrokeColor(child == 1 ? 0xFF00E5BC : 0x22FFFFFF);
            if (ivSolid != null) ivSolid.setImageTintList(ColorStateList.valueOf(child == 1 ? 0xFF00E5BC : 0xFF94A3B8));
            tabGradient.setStrokeColor(child == 2 ? 0xFF00E5BC : 0x22FFFFFF);
            if (ivGrad != null) ivGrad.setImageTintList(ColorStateList.valueOf(child == 2 ? 0xFF00E5BC : 0xFF94A3B8));
            tabMedia.setStrokeColor(child == 3 ? 0xFF00E5BC : 0x22FFFFFF);
            if (ivMedia != null) ivMedia.setImageTintList(ColorStateList.valueOf(child == 3 ? 0xFF00E5BC : 0xFF94A3B8));
        };

        if (tabNone != null) {
            tabNone.setOnClickListener(v -> {
                if (flipperFill != null) flipperFill.setDisplayedChild(0);
                updateTabsState.run();
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setFillMode(ShapeLayer.FillMode.NONE);
                } else if (layer instanceof PhotoLayer) {
                    ((PhotoLayer) layer).setFillMode(ShapeLayer.FillMode.NONE);
                } else if (layer instanceof TextLayer) {
                    ((TextLayer) layer).setFillMode(ShapeLayer.FillMode.NONE);
                }
                if (previewFill != null) previewFill.setBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
                binding.canvasView.invalidate();
            });
        }

        if (tabSolid != null) {
            tabSolid.setOnClickListener(v -> {
                if (flipperFill != null) flipperFill.setDisplayedChild(1);
                updateTabsState.run();
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ShapeLayer sl = (ShapeLayer) layer;
                    sl.setFillMode(ShapeLayer.FillMode.SOLID);
                    if (colorPicker != null) {
                        sl.setFillColor(colorPicker.getColor());
                    }
                    if (previewFill != null) {
                        previewFill.setBackgroundTintList(ColorStateList.valueOf(sl.getFillColor()));
                    }
                } else if (layer instanceof PhotoLayer) {
                    PhotoLayer pl = (PhotoLayer) layer;
                    pl.setFillMode(ShapeLayer.FillMode.SOLID);
                    if (colorPicker != null) {
                        pl.setFillColor(colorPicker.getColor());
                    }
                    if (previewFill != null) {
                        previewFill.setBackgroundTintList(ColorStateList.valueOf(pl.getFillColor()));
                    }
                } else if (layer instanceof TextLayer) {
                    TextLayer tl = (TextLayer) layer;
                    tl.setFillMode(ShapeLayer.FillMode.SOLID);
                    if (colorPicker != null) {
                        tl.setFillColor(colorPicker.getColor());
                    }
                    if (previewFill != null) {
                        previewFill.setBackgroundTintList(ColorStateList.valueOf(tl.getFillColor()));
                    }
                }
                binding.canvasView.invalidate();
            });
        }

        if (tabGradient != null) {
            tabGradient.setOnClickListener(v -> {
                if (flipperFill != null) flipperFill.setDisplayedChild(2);
                updateTabsState.run();
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ShapeLayer sl = (ShapeLayer) layer;
                    sl.setFillMode(ShapeLayer.FillMode.GRADIENT);
                    if (gradientBarView != null) {
                        gradientBarView.setColors(sl.getGradientStartColor(), sl.getGradientEndColor());
                        gradientBarView.setOffsets(sl.getGradientStartOffset(), sl.getGradientEndOffset());
                    }
                    if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(sl.getGradientStartColor()));
                    if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(sl.getGradientEndColor()));
                    if (previewFill != null) previewFill.setBackgroundTintList(ColorStateList.valueOf(sl.getGradientStartColor()));
                } else if (layer instanceof PhotoLayer) {
                    PhotoLayer pl = (PhotoLayer) layer;
                    pl.setFillMode(ShapeLayer.FillMode.GRADIENT);
                    if (gradientBarView != null) {
                        gradientBarView.setColors(pl.getGradientStartColor(), pl.getGradientEndColor());
                        gradientBarView.setOffsets(pl.getGradientStartOffset(), pl.getGradientEndOffset());
                    }
                    if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(pl.getGradientStartColor()));
                    if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(pl.getGradientEndColor()));
                    if (previewFill != null) previewFill.setBackgroundTintList(ColorStateList.valueOf(pl.getGradientStartColor()));
                } else if (layer instanceof TextLayer) {
                    TextLayer tl = (TextLayer) layer;
                    tl.setFillMode(ShapeLayer.FillMode.GRADIENT);
                    if (gradientBarView != null) {
                        gradientBarView.setColors(tl.getGradientStartColor(), tl.getGradientEndColor());
                        gradientBarView.setOffsets(tl.getGradientStartOffset(), tl.getGradientEndOffset());
                    }
                    if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(tl.getGradientStartColor()));
                    if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(tl.getGradientEndColor()));
                    if (previewFill != null) previewFill.setBackgroundTintList(ColorStateList.valueOf(tl.getGradientStartColor()));
                }
                binding.canvasView.invalidate();
            });
        }

        if (tabMedia != null) {
            tabMedia.setOnClickListener(v -> {
                if (flipperFill != null) flipperFill.setDisplayedChild(3);
                updateTabsState.run();
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setFillMode(ShapeLayer.FillMode.MEDIA);
                } else if (layer instanceof PhotoLayer) {
                    ((PhotoLayer) layer).setFillMode(ShapeLayer.FillMode.MEDIA);
                } else if (layer instanceof TextLayer) {
                    ((TextLayer) layer).setFillMode(ShapeLayer.FillMode.MEDIA);
                }
                binding.canvasView.invalidate();
            });
        }

        // Media Scale Buttons & Picking
        Runnable updateMediaScaleButtons = () -> {
            CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
            ShapeLayer.MediaScaleMode mode = ShapeLayer.MediaScaleMode.FILL;
            if (layer instanceof ShapeLayer) {
                mode = ((ShapeLayer) layer).getMediaScaleMode();
            } else if (layer instanceof TextLayer) {
                mode = ((TextLayer) layer).getMediaScaleMode();
            }
            if (btnMediaScaleFill != null) {
                btnMediaScaleFill.setTextColor(mode == ShapeLayer.MediaScaleMode.FILL ? 0xFF00E5BC : 0xFF94A3B8);
            }
            if (btnMediaScaleFit != null) {
                btnMediaScaleFit.setTextColor(mode == ShapeLayer.MediaScaleMode.FIT ? 0xFF00E5BC : 0xFF94A3B8);
            }
            if (btnMediaScaleStretch != null) {
                btnMediaScaleStretch.setTextColor(mode == ShapeLayer.MediaScaleMode.STRETCH ? 0xFF00E5BC : 0xFF94A3B8);
            }
        };

        if (btnMediaScaleFill != null) {
            btnMediaScaleFill.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setMediaScaleMode(ShapeLayer.MediaScaleMode.FILL);
                    updateMediaScaleButtons.run();
                    binding.canvasView.invalidate();
                } else if (layer instanceof TextLayer) {
                    ((TextLayer) layer).setMediaScaleMode(ShapeLayer.MediaScaleMode.FILL);
                    updateMediaScaleButtons.run();
                    binding.canvasView.invalidate();
                }
            });
        }
        if (btnMediaScaleFit != null) {
            btnMediaScaleFit.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setMediaScaleMode(ShapeLayer.MediaScaleMode.FIT);
                    updateMediaScaleButtons.run();
                    binding.canvasView.invalidate();
                } else if (layer instanceof TextLayer) {
                    ((TextLayer) layer).setMediaScaleMode(ShapeLayer.MediaScaleMode.FIT);
                    updateMediaScaleButtons.run();
                    binding.canvasView.invalidate();
                }
            });
        }
        if (btnMediaScaleStretch != null) {
            btnMediaScaleStretch.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setMediaScaleMode(ShapeLayer.MediaScaleMode.STRETCH);
                    updateMediaScaleButtons.run();
                    binding.canvasView.invalidate();
                } else if (layer instanceof TextLayer) {
                    ((TextLayer) layer).setMediaScaleMode(ShapeLayer.MediaScaleMode.STRETCH);
                    updateMediaScaleButtons.run();
                    binding.canvasView.invalidate();
                }
            });
        }

        View.OnClickListener pickMediaClick = v -> {
            if (mediaFillPickerLauncher != null) {
                mediaFillPickerLauncher.launch("image/*");
            }
        };
        if (cardMediaSelector != null) cardMediaSelector.setOnClickListener(pickMediaClick);
        if (btnPickMedia != null) btnPickMedia.setOnClickListener(pickMediaClick);

        // Gradient Type Switcher Highlights
        Runnable updateGradTypeButtons = () -> {
            CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
            ShapeLayer.GradientType gt = ShapeLayer.GradientType.LINEAR;
            if (layer instanceof ShapeLayer) {
                gt = ((ShapeLayer) layer).getGradientType();
            } else if (layer instanceof PhotoLayer) {
                gt = ((PhotoLayer) layer).getGradientType();
            } else if (layer instanceof TextLayer) {
                gt = ((TextLayer) layer).getGradientType();
            }

            if (btnGradTypeLinear != null) {
                boolean active = (gt == ShapeLayer.GradientType.LINEAR);
                btnGradTypeLinear.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeLinear.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradLinear != null) ivGradLinear.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }
            if (btnGradTypeRadial != null) {
                boolean active = (gt == ShapeLayer.GradientType.RADIAL);
                btnGradTypeRadial.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeRadial.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradRadial != null) ivGradRadial.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }
            if (btnGradTypeSweep != null) {
                boolean active = (gt == ShapeLayer.GradientType.SWEEP);
                btnGradTypeSweep.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeSweep.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradSweep != null) ivGradSweep.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }
        };

        if (btnGradTypeLinear != null) {
            btnGradTypeLinear.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setGradientType(ShapeLayer.GradientType.LINEAR);
                } else if (layer instanceof PhotoLayer) {
                    ((PhotoLayer) layer).setGradientType(ShapeLayer.GradientType.LINEAR);
                } else if (layer instanceof TextLayer) {
                    ((TextLayer) layer).setGradientType(ShapeLayer.GradientType.LINEAR);
                }
                updateGradTypeButtons.run();
                binding.canvasView.invalidate();
            });
        }

        if (btnGradTypeRadial != null) {
            btnGradTypeRadial.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setGradientType(ShapeLayer.GradientType.RADIAL);
                } else if (layer instanceof PhotoLayer) {
                    ((PhotoLayer) layer).setGradientType(ShapeLayer.GradientType.RADIAL);
                } else if (layer instanceof TextLayer) {
                    ((TextLayer) layer).setGradientType(ShapeLayer.GradientType.RADIAL);
                }
                updateGradTypeButtons.run();
                binding.canvasView.invalidate();
            });
        }

        if (btnGradTypeSweep != null) {
            btnGradTypeSweep.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setGradientType(ShapeLayer.GradientType.SWEEP);
                } else if (layer instanceof PhotoLayer) {
                    ((PhotoLayer) layer).setGradientType(ShapeLayer.GradientType.SWEEP);
                } else if (layer instanceof TextLayer) {
                    ((TextLayer) layer).setGradientType(ShapeLayer.GradientType.SWEEP);
                }
                updateGradTypeButtons.run();
                binding.canvasView.invalidate();
            });
        }

        // Gradient Bar Offsets & Selection Listener
        if (gradientBarView != null) {
            gradientBarView.setOnGradientChangeListener(new GradientBarView.OnGradientChangeListener() {
                @Override
                public void onStopSelected(int stopIndex, int color) {
                }

                @Override
                public void onOffsetsChanged(float startOffset, float endOffset, boolean fromUser) {
                    CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                    if (layer instanceof ShapeLayer) {
                        ShapeLayer sl = (ShapeLayer) layer;
                        sl.setGradientStartOffset(startOffset);
                        sl.setGradientEndOffset(endOffset);
                        binding.canvasView.invalidate();
                    } else if (layer instanceof PhotoLayer) {
                        PhotoLayer pl = (PhotoLayer) layer;
                        pl.setGradientStartOffset(startOffset);
                        pl.setGradientEndOffset(endOffset);
                        binding.canvasView.invalidate();
                    } else if (layer instanceof TextLayer) {
                        TextLayer tl = (TextLayer) layer;
                        tl.setGradientStartOffset(startOffset);
                        tl.setGradientEndOffset(endOffset);
                        binding.canvasView.invalidate();
                    }
                }
            });
        }

        // Start / End Color pickers
        View.OnClickListener pickStartColor = v -> {
            CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
            int curCol = 0xFF000000;
            if (layer instanceof ShapeLayer) curCol = ((ShapeLayer) layer).getGradientStartColor();
            else if (layer instanceof PhotoLayer) curCol = ((PhotoLayer) layer).getGradientStartColor();
            else if (layer instanceof TextLayer) curCol = ((TextLayer) layer).getGradientStartColor();

            AlightColorPickerDialog.show(MainActivity.this, "Gradient Start Color", curCol, col -> {
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setGradientStartColor(col);
                } else if (layer instanceof PhotoLayer) {
                    ((PhotoLayer) layer).setGradientStartColor(col);
                } else if (layer instanceof TextLayer) {
                    ((TextLayer) layer).setGradientStartColor(col);
                }
                if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(col));
                if (gradientBarView != null) {
                    int endCol = (layer instanceof ShapeLayer) ? ((ShapeLayer) layer).getGradientEndColor() :
                                 (layer instanceof PhotoLayer) ? ((PhotoLayer) layer).getGradientEndColor() :
                                 (layer instanceof TextLayer) ? ((TextLayer) layer).getGradientEndColor() : 0xFFFFFFFF;
                    gradientBarView.setColors(col, endCol);
                }
                binding.canvasView.invalidate();
            }, (dialog, pickerView) -> {
                Toast.makeText(MainActivity.this, "Drag across canvas to pick color", Toast.LENGTH_SHORT).show();
                binding.canvasView.startEyedropper(col -> {
                    if (layer instanceof ShapeLayer) {
                        ((ShapeLayer) layer).setGradientStartColor(col);
                    } else if (layer instanceof PhotoLayer) {
                        ((PhotoLayer) layer).setGradientStartColor(col);
                    } else if (layer instanceof TextLayer) {
                        ((TextLayer) layer).setGradientStartColor(col);
                    }
                    pickerView.setColor(col, true);
                    if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(col));
                    if (gradientBarView != null) {
                        int endCol = (layer instanceof ShapeLayer) ? ((ShapeLayer) layer).getGradientEndColor() :
                                     (layer instanceof PhotoLayer) ? ((PhotoLayer) layer).getGradientEndColor() :
                                     (layer instanceof TextLayer) ? ((TextLayer) layer).getGradientEndColor() : 0xFFFFFFFF;
                        gradientBarView.setColors(col, endCol);
                    }
                    binding.canvasView.invalidate();
                });
            });
        };

        if (btnGradStart != null) btnGradStart.setOnClickListener(pickStartColor);
        if (btnGradStartContainer != null) btnGradStartContainer.setOnClickListener(pickStartColor);

        View.OnClickListener pickEndColor = v -> {
            CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
            int curCol = 0xFFFFFFFF;
            if (layer instanceof ShapeLayer) curCol = ((ShapeLayer) layer).getGradientEndColor();
            else if (layer instanceof PhotoLayer) curCol = ((PhotoLayer) layer).getGradientEndColor();
            else if (layer instanceof TextLayer) curCol = ((TextLayer) layer).getGradientEndColor();

            AlightColorPickerDialog.show(MainActivity.this, "Gradient End Color", curCol, col -> {
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setGradientEndColor(col);
                } else if (layer instanceof PhotoLayer) {
                    ((PhotoLayer) layer).setGradientEndColor(col);
                } else if (layer instanceof TextLayer) {
                    ((TextLayer) layer).setGradientEndColor(col);
                }
                if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(col));
                if (gradientBarView != null) {
                    int startCol = (layer instanceof ShapeLayer) ? ((ShapeLayer) layer).getGradientStartColor() :
                                   (layer instanceof PhotoLayer) ? ((PhotoLayer) layer).getGradientStartColor() :
                                   (layer instanceof TextLayer) ? ((TextLayer) layer).getGradientStartColor() : 0xFF000000;
                    gradientBarView.setColors(startCol, col);
                }
                binding.canvasView.invalidate();
            }, (dialog, pickerView) -> {
                Toast.makeText(MainActivity.this, "Drag across canvas to pick color", Toast.LENGTH_SHORT).show();
                binding.canvasView.startEyedropper(col -> {
                    if (layer instanceof ShapeLayer) {
                        ((ShapeLayer) layer).setGradientEndColor(col);
                    } else if (layer instanceof PhotoLayer) {
                        ((PhotoLayer) layer).setGradientEndColor(col);
                    } else if (layer instanceof TextLayer) {
                        ((TextLayer) layer).setGradientEndColor(col);
                    }
                    pickerView.setColor(col, true);
                    if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(col));
                    if (gradientBarView != null) {
                        int startCol = (layer instanceof ShapeLayer) ? ((ShapeLayer) layer).getGradientStartColor() :
                                       (layer instanceof PhotoLayer) ? ((PhotoLayer) layer).getGradientStartColor() :
                                       (layer instanceof TextLayer) ? ((TextLayer) layer).getGradientStartColor() : 0xFF000000;
                        gradientBarView.setColors(startCol, col);
                    }
                    binding.canvasView.invalidate();
                });
            });
        };

        if (btnGradEnd != null) btnGradEnd.setOnClickListener(pickEndColor);
        if (btnGradEndContainer != null) btnGradEndContainer.setOnClickListener(pickEndColor);

        // Reverse Gradient Button
        if (btnReverse != null) {
            btnReverse.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer instanceof ShapeLayer) {
                    ShapeLayer sl = (ShapeLayer) layer;
                    int tempCol = sl.getGradientStartColor();
                    sl.setGradientStartColor(sl.getGradientEndColor());
                    sl.setGradientEndColor(tempCol);

                    float tempOff = sl.getGradientStartOffset();
                    sl.setGradientStartOffset(sl.getGradientEndOffset());
                    sl.setGradientEndOffset(tempOff);

                    if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(sl.getGradientStartColor()));
                    if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(sl.getGradientEndColor()));
                    if (gradientBarView != null) {
                        gradientBarView.setColors(sl.getGradientStartColor(), sl.getGradientEndColor());
                        gradientBarView.setOffsets(sl.getGradientStartOffset(), sl.getGradientEndOffset());
                    }
                    binding.canvasView.invalidate();
                } else if (layer instanceof PhotoLayer) {
                    PhotoLayer pl = (PhotoLayer) layer;
                    int tempCol = pl.getGradientStartColor();
                    pl.setGradientStartColor(pl.getGradientEndColor());
                    pl.setGradientEndColor(tempCol);

                    float tempOff = pl.getGradientStartOffset();
                    pl.setGradientStartOffset(pl.getGradientEndOffset());
                    pl.setGradientEndOffset(tempOff);

                    if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(pl.getGradientStartColor()));
                    if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(pl.getGradientEndColor()));
                    if (gradientBarView != null) {
                        gradientBarView.setColors(pl.getGradientStartColor(), pl.getGradientEndColor());
                        gradientBarView.setOffsets(pl.getGradientStartOffset(), pl.getGradientEndOffset());
                    }
                    binding.canvasView.invalidate();
                } else if (layer instanceof TextLayer) {
                    TextLayer tl = (TextLayer) layer;
                    int tempCol = tl.getGradientStartColor();
                    tl.setGradientStartColor(tl.getGradientEndColor());
                    tl.setGradientEndColor(tempCol);

                    float tempOff = tl.getGradientStartOffset();
                    tl.setGradientStartOffset(tl.getGradientEndOffset());
                    tl.setGradientEndOffset(tempOff);

                    if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(tl.getGradientStartColor()));
                    if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(tl.getGradientEndColor()));
                    if (gradientBarView != null) {
                        gradientBarView.setColors(tl.getGradientStartColor(), tl.getGradientEndColor());
                        gradientBarView.setOffsets(tl.getGradientStartOffset(), tl.getGradientEndOffset());
                    }
                    binding.canvasView.invalidate();
                }
            });
        }

        // Gradient Eyedropper Button
        if (btnEyedropper != null) {
            btnEyedropper.setOnClickListener(v -> {
                Toast.makeText(MainActivity.this, "Drag across canvas to pick gradient stop color", Toast.LENGTH_SHORT).show();
                binding.canvasView.startEyedropper(color -> {
                    CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                    int selectedStop = (gradientBarView != null) ? gradientBarView.getSelectedStopIndex() : 0;
                    if (layer instanceof ShapeLayer) {
                        ShapeLayer sl = (ShapeLayer) layer;
                        if (selectedStop == 0) {
                            sl.setGradientStartColor(color);
                            if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(color));
                        } else {
                            sl.setGradientEndColor(color);
                            if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(color));
                        }
                        if (gradientBarView != null) {
                            gradientBarView.setColors(sl.getGradientStartColor(), sl.getGradientEndColor());
                        }
                    } else if (layer instanceof PhotoLayer) {
                        PhotoLayer pl = (PhotoLayer) layer;
                        if (selectedStop == 0) {
                            pl.setGradientStartColor(color);
                            if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(color));
                        } else {
                            pl.setGradientEndColor(color);
                            if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(color));
                        }
                        if (gradientBarView != null) {
                            gradientBarView.setColors(pl.getGradientStartColor(), pl.getGradientEndColor());
                        }
                    } else if (layer instanceof TextLayer) {
                        TextLayer tl = (TextLayer) layer;
                        if (selectedStop == 0) {
                            tl.setGradientStartColor(color);
                            if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(color));
                        } else {
                            tl.setGradientEndColor(color);
                            if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(color));
                        }
                        if (gradientBarView != null) {
                            gradientBarView.setColors(tl.getGradientStartColor(), tl.getGradientEndColor());
                        }
                    }
                    binding.canvasView.invalidate();
                });
            });
        }

        // Solid Color Picker Listener
        if (colorPicker != null) {
            colorPicker.setOnColorChangeListener(new AlightColorPickerView.OnColorChangeListener() {
                @Override
                public void onColorChanged(int color, boolean fromUser) {
                    CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                    if (layer instanceof ShapeLayer) {
                        ((ShapeLayer) layer).setFillColor(color);
                    } else if (layer instanceof PhotoLayer) {
                        ((PhotoLayer) layer).setFillColor(color);
                    } else if (layer instanceof TextLayer) {
                        ((TextLayer) layer).setFillColor(color);
                    }
                    if (previewFill != null) {
                        previewFill.setBackgroundTintList(ColorStateList.valueOf(color));
                    }
                    binding.canvasView.invalidate();
                }

                @Override
                public void onEyedropperRequested() {
                    Toast.makeText(MainActivity.this, "Drag across canvas to pick color", Toast.LENGTH_SHORT).show();
                    binding.canvasView.startEyedropper(color -> {
                        colorPicker.setColor(color, true);
                    });
                }
            });
        }
    }

    private void populateColorFillPanel(CanvasLayer layer) {
        if (layer == null) return;
        AlightColorPickerView colorPicker = binding.getRoot().findViewById(R.id.panelAlightColorPicker);
        ViewFlipper flipperFill = binding.getRoot().findViewById(R.id.flipperFillSubpanels);
        MaterialCardView tabNone = binding.getRoot().findViewById(R.id.tabFillNone);
        MaterialCardView tabSolid = binding.getRoot().findViewById(R.id.tabFillSolid);
        MaterialCardView tabGradient = binding.getRoot().findViewById(R.id.tabFillGradient);
        MaterialCardView tabMedia = binding.getRoot().findViewById(R.id.tabFillMedia);

        ImageView ivNone = binding.getRoot().findViewById(R.id.ivTabNoneIcon);
        ImageView ivSolid = binding.getRoot().findViewById(R.id.ivTabSolidIcon);
        ImageView ivGrad = binding.getRoot().findViewById(R.id.ivTabGradientIcon);
        ImageView ivMedia = binding.getRoot().findViewById(R.id.ivTabMediaIcon);

        View previewFill = binding.getRoot().findViewById(R.id.viewCurrentFillColorPreview);

        MaterialCardView btnGradTypeLinear = binding.getRoot().findViewById(R.id.btnGradTypeLinear);
        MaterialCardView btnGradTypeRadial = binding.getRoot().findViewById(R.id.btnGradTypeRadial);
        MaterialCardView btnGradTypeSweep = binding.getRoot().findViewById(R.id.btnGradTypeSweep);
        ImageView ivGradLinear = binding.getRoot().findViewById(R.id.ivGradTypeLinear);
        ImageView ivGradRadial = binding.getRoot().findViewById(R.id.ivGradTypeRadial);
        ImageView ivGradSweep = binding.getRoot().findViewById(R.id.ivGradTypeSweep);

        GradientBarView gradientBarView = binding.getRoot().findViewById(R.id.gradientBarView);
        View btnGradStart = binding.getRoot().findViewById(R.id.btnGradStartColor);
        View btnGradEnd = binding.getRoot().findViewById(R.id.btnGradEndColor);

        TextView tvMediaName = binding.getRoot().findViewById(R.id.tvMediaFillName);
        MaterialButton btnMediaScaleFill = binding.getRoot().findViewById(R.id.btnMediaScaleFill);
        MaterialButton btnMediaScaleFit = binding.getRoot().findViewById(R.id.btnMediaScaleFit);
        MaterialButton btnMediaScaleStretch = binding.getRoot().findViewById(R.id.btnMediaScaleStretch);

        int childIdx = 1; // Default Solid
        if (layer instanceof ShapeLayer) {
            ShapeLayer sl = (ShapeLayer) layer;
            ShapeLayer.FillMode fm = sl.getFillMode();
            if (fm == ShapeLayer.FillMode.NONE) childIdx = 0;
            else if (fm == ShapeLayer.FillMode.SOLID) childIdx = 1;
            else if (fm == ShapeLayer.FillMode.GRADIENT) childIdx = 2;
            else if (fm == ShapeLayer.FillMode.MEDIA) childIdx = 3;

            if (colorPicker != null && fm == ShapeLayer.FillMode.SOLID) {
                colorPicker.setColor(sl.getFillColor(), false);
            }
            if (previewFill != null) {
                int col = (fm == ShapeLayer.FillMode.GRADIENT) ? sl.getGradientStartColor() : sl.getFillColor();
                previewFill.setBackgroundTintList(ColorStateList.valueOf(col));
            }

            if (gradientBarView != null) {
                gradientBarView.setColors(sl.getGradientStartColor(), sl.getGradientEndColor());
                gradientBarView.setOffsets(sl.getGradientStartOffset(), sl.getGradientEndOffset());
            }
            if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(sl.getGradientStartColor()));
            if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(sl.getGradientEndColor()));

            ShapeLayer.GradientType gt = sl.getGradientType();
            if (btnGradTypeLinear != null) {
                boolean active = (gt == ShapeLayer.GradientType.LINEAR);
                btnGradTypeLinear.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeLinear.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradLinear != null) ivGradLinear.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }
            if (btnGradTypeRadial != null) {
                boolean active = (gt == ShapeLayer.GradientType.RADIAL);
                btnGradTypeRadial.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeRadial.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradRadial != null) ivGradRadial.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }
            if (btnGradTypeSweep != null) {
                boolean active = (gt == ShapeLayer.GradientType.SWEEP);
                btnGradTypeSweep.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeSweep.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradSweep != null) ivGradSweep.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }

            if (tvMediaName != null) {
                if (sl.getMediaName() != null && !sl.getMediaName().isEmpty()) {
                    tvMediaName.setText(sl.getMediaName());
                } else if (sl.getMediaBitmap() != null) {
                    tvMediaName.setText("Media Fill (" + sl.getMediaBitmap().getWidth() + "x" + sl.getMediaBitmap().getHeight() + ")");
                } else {
                    tvMediaName.setText("Select Image / Media...");
                }
            }

            ShapeLayer.MediaScaleMode scaleMode = sl.getMediaScaleMode();
            if (btnMediaScaleFill != null) btnMediaScaleFill.setTextColor(scaleMode == ShapeLayer.MediaScaleMode.FILL ? 0xFF00E5BC : 0xFF94A3B8);
            if (btnMediaScaleFit != null) btnMediaScaleFit.setTextColor(scaleMode == ShapeLayer.MediaScaleMode.FIT ? 0xFF00E5BC : 0xFF94A3B8);
            if (btnMediaScaleStretch != null) btnMediaScaleStretch.setTextColor(scaleMode == ShapeLayer.MediaScaleMode.STRETCH ? 0xFF00E5BC : 0xFF94A3B8);

        } else if (layer instanceof PhotoLayer) {
            PhotoLayer pl = (PhotoLayer) layer;
            ShapeLayer.FillMode fm = pl.getFillMode();
            if (fm == ShapeLayer.FillMode.NONE) childIdx = 0;
            else if (fm == ShapeLayer.FillMode.SOLID) childIdx = 1;
            else if (fm == ShapeLayer.FillMode.GRADIENT) childIdx = 2;
            else if (fm == ShapeLayer.FillMode.MEDIA) childIdx = 3;

            if (colorPicker != null && fm == ShapeLayer.FillMode.SOLID) {
                colorPicker.setColor(pl.getFillColor(), false);
            }
            if (previewFill != null) {
                int col = (fm == ShapeLayer.FillMode.GRADIENT) ? pl.getGradientStartColor() : pl.getFillColor();
                previewFill.setBackgroundTintList(ColorStateList.valueOf(col));
            }

            if (gradientBarView != null) {
                gradientBarView.setColors(pl.getGradientStartColor(), pl.getGradientEndColor());
                gradientBarView.setOffsets(pl.getGradientStartOffset(), pl.getGradientEndOffset());
            }
            if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(pl.getGradientStartColor()));
            if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(pl.getGradientEndColor()));

            ShapeLayer.GradientType gt = pl.getGradientType();
            if (btnGradTypeLinear != null) {
                boolean active = (gt == ShapeLayer.GradientType.LINEAR);
                btnGradTypeLinear.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeLinear.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradLinear != null) ivGradLinear.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }
            if (btnGradTypeRadial != null) {
                boolean active = (gt == ShapeLayer.GradientType.RADIAL);
                btnGradTypeRadial.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeRadial.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradRadial != null) ivGradRadial.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }
            if (btnGradTypeSweep != null) {
                boolean active = (gt == ShapeLayer.GradientType.SWEEP);
                btnGradTypeSweep.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeSweep.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradSweep != null) ivGradSweep.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }

            if (tvMediaName != null) {
                tvMediaName.setText(pl.getName());
            }
        } else if (layer instanceof TextLayer) {
            TextLayer tl = (TextLayer) layer;
            ShapeLayer.FillMode fm = tl.getFillMode();
            if (fm == ShapeLayer.FillMode.NONE) childIdx = 0;
            else if (fm == ShapeLayer.FillMode.SOLID) childIdx = 1;
            else if (fm == ShapeLayer.FillMode.GRADIENT) childIdx = 2;
            else if (fm == ShapeLayer.FillMode.MEDIA) childIdx = 3;

            if (colorPicker != null && fm == ShapeLayer.FillMode.SOLID) {
                colorPicker.setColor(tl.getTextColor(), false);
            }
            if (previewFill != null) {
                int col = (fm == ShapeLayer.FillMode.GRADIENT) ? tl.getGradientStartColor() : tl.getTextColor();
                previewFill.setBackgroundTintList(ColorStateList.valueOf(col));
            }

            if (gradientBarView != null) {
                gradientBarView.setColors(tl.getGradientStartColor(), tl.getGradientEndColor());
                gradientBarView.setOffsets(tl.getGradientStartOffset(), tl.getGradientEndOffset());
            }
            if (btnGradStart != null) btnGradStart.setBackgroundTintList(ColorStateList.valueOf(tl.getGradientStartColor()));
            if (btnGradEnd != null) btnGradEnd.setBackgroundTintList(ColorStateList.valueOf(tl.getGradientEndColor()));

            ShapeLayer.GradientType gt = tl.getGradientType();
            if (btnGradTypeLinear != null) {
                boolean active = (gt == ShapeLayer.GradientType.LINEAR);
                btnGradTypeLinear.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeLinear.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradLinear != null) ivGradLinear.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }
            if (btnGradTypeRadial != null) {
                boolean active = (gt == ShapeLayer.GradientType.RADIAL);
                btnGradTypeRadial.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeRadial.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradRadial != null) ivGradRadial.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }
            if (btnGradTypeSweep != null) {
                boolean active = (gt == ShapeLayer.GradientType.SWEEP);
                btnGradTypeSweep.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
                btnGradTypeSweep.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
                if (ivGradSweep != null) ivGradSweep.setImageTintList(ColorStateList.valueOf(active ? 0xFF00E5BC : 0xFF94A3B8));
            }

            if (tvMediaName != null) {
                if (tl.getMediaName() != null && !tl.getMediaName().isEmpty()) {
                    tvMediaName.setText(tl.getMediaName());
                } else if (tl.getMediaBitmap() != null) {
                    tvMediaName.setText("Media Fill (" + tl.getMediaBitmap().getWidth() + "x" + tl.getMediaBitmap().getHeight() + ")");
                } else {
                    tvMediaName.setText("Select Image / Media...");
                }
            }

            ShapeLayer.MediaScaleMode scaleMode = tl.getMediaScaleMode();
            if (btnMediaScaleFill != null) btnMediaScaleFill.setTextColor(scaleMode == ShapeLayer.MediaScaleMode.FILL ? 0xFF00E5BC : 0xFF94A3B8);
            if (btnMediaScaleFit != null) btnMediaScaleFit.setTextColor(scaleMode == ShapeLayer.MediaScaleMode.FIT ? 0xFF00E5BC : 0xFF94A3B8);
            if (btnMediaScaleStretch != null) btnMediaScaleStretch.setTextColor(scaleMode == ShapeLayer.MediaScaleMode.STRETCH ? 0xFF00E5BC : 0xFF94A3B8);
        }

        if (flipperFill != null) {
            flipperFill.setDisplayedChild(childIdx);
        }

        if (tabNone != null) {
            tabNone.setStrokeColor(childIdx == 0 ? 0xFF00E5BC : 0x22FFFFFF);
            if (ivNone != null) ivNone.setImageTintList(ColorStateList.valueOf(childIdx == 0 ? 0xFF00E5BC : 0xFF94A3B8));
            tabSolid.setStrokeColor(childIdx == 1 ? 0xFF00E5BC : 0x22FFFFFF);
            if (ivSolid != null) ivSolid.setImageTintList(ColorStateList.valueOf(childIdx == 1 ? 0xFF00E5BC : 0xFF94A3B8));
            tabGradient.setStrokeColor(childIdx == 2 ? 0xFF00E5BC : 0x22FFFFFF);
            if (ivGrad != null) ivGrad.setImageTintList(ColorStateList.valueOf(childIdx == 2 ? 0xFF00E5BC : 0xFF94A3B8));
            tabMedia.setStrokeColor(childIdx == 3 ? 0xFF00E5BC : 0x22FFFFFF);
            if (ivMedia != null) ivMedia.setImageTintList(ColorStateList.valueOf(childIdx == 3 ? 0xFF00E5BC : 0xFF94A3B8));
        }
    }

    // -------------------------------------------------------------
    // SUBPANEL 9: BLENDING & OPACITY (Alight Motion Reference)
    // -------------------------------------------------------------
    private void setupBlendingOpacityPanel() {
        View panel = binding.flipperBottomPanels.getChildAt(PANEL_BLENDING_OPACITY);
        if (panel == null) return;

        View btnBack = panel.findViewById(R.id.btnBackFromBlendingOpacity);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));
        }

        ClampedSliderView sliderOpacity = panel.findViewById(R.id.sliderLayerOpacity);
        TextView tvOpacityVal = panel.findViewById(R.id.tvLayerOpacityVal);

        if (sliderOpacity != null) {
            sliderOpacity.setValueFrom(0);
            sliderOpacity.setValueTo(100);
            sliderOpacity.addOnChangeListener((slider, value, fromUser) -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer != null && fromUser) {
                    layer.setOpacityPercent(value);
                    if (tvOpacityVal != null) {
                        tvOpacityVal.setText(Math.round(value) + "%");
                    }
                    binding.canvasView.invalidate();
                }
            });
        }

        // All accordion groups (for mutual-collapse)
        int[][] accordionGroups = {
            {R.id.headerBlendDarken,   R.id.containerBlendDarken,   R.id.ivToggleBlendDarken},
            {R.id.headerBlendLighten,  R.id.containerBlendLighten,  R.id.ivToggleBlendLighten},
            {R.id.headerBlendContrast, R.id.containerBlendContrast, R.id.ivToggleBlendContrast},
            {R.id.headerBlendDiff,     R.id.containerBlendDiff,     R.id.ivToggleBlendDiff},
            {R.id.headerBlendColor,    R.id.containerBlendColor,    R.id.ivToggleBlendColor},
            {R.id.headerBlendMask,     R.id.containerBlendMask,     R.id.ivToggleBlendMask},
        };
        setupMutualAccordion(panel, accordionGroups);

        // Normal Card (top — always resets to Normal blend mode)
        View cardNormal = panel.findViewById(R.id.cardBlendNormal);
        if (cardNormal != null) {
            cardNormal.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer != null) {
                    layer.setBlendMode(CanvasLayer.BlendMode.NORMAL);
                    layer.setMaskType(CanvasLayer.MaskType.NONE);
                    populateBlendingOpacityPanel(layer);
                    binding.canvasView.invalidate();
                }
            });
        }

        // Darken Category
        bindBlendCard(panel, R.id.cardBlendMultiply, CanvasLayer.BlendMode.MULTIPLY);
        bindBlendCard(panel, R.id.cardBlendDarken, CanvasLayer.BlendMode.DARKEN);
        bindBlendCard(panel, R.id.cardBlendColorBurn, CanvasLayer.BlendMode.COLOR_BURN);
        bindBlendCard(panel, R.id.cardBlendLinearBurn, CanvasLayer.BlendMode.LINEAR_BURN);

        // Lighten Category
        bindBlendCard(panel, R.id.cardBlendScreen, CanvasLayer.BlendMode.SCREEN);
        bindBlendCard(panel, R.id.cardBlendLighten, CanvasLayer.BlendMode.LIGHTEN);
        bindBlendCard(panel, R.id.cardBlendColorDodge, CanvasLayer.BlendMode.COLOR_DODGE);
        bindBlendCard(panel, R.id.cardBlendLinearDodge, CanvasLayer.BlendMode.LINEAR_DODGE);

        // Contrast Category
        bindBlendCard(panel, R.id.cardBlendOverlay, CanvasLayer.BlendMode.OVERLAY);
        bindBlendCard(panel, R.id.cardBlendSoftLight, CanvasLayer.BlendMode.SOFT_LIGHT);
        bindBlendCard(panel, R.id.cardBlendHardLight, CanvasLayer.BlendMode.HARD_LIGHT);

        // Difference Category
        bindBlendCard(panel, R.id.cardBlendDifference, CanvasLayer.BlendMode.DIFFERENCE);
        bindBlendCard(panel, R.id.cardBlendExclusion, CanvasLayer.BlendMode.EXCLUSION);

        // Color Category
        bindBlendCard(panel, R.id.cardBlendHue, CanvasLayer.BlendMode.HUE);
        bindBlendCard(panel, R.id.cardBlendSat, CanvasLayer.BlendMode.SATURATION);
        bindBlendCard(panel, R.id.cardBlendColorMode, CanvasLayer.BlendMode.COLOR);
        bindBlendCard(panel, R.id.cardBlendLum, CanvasLayer.BlendMode.LUMINOSITY);

        // Mask Cards
        MaterialCardView cardMask = panel.findViewById(R.id.cardBlendMask);
        MaterialCardView cardExclude = panel.findViewById(R.id.cardBlendExclude);

        if (cardMask != null) {
            cardMask.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer != null) {
                    CanvasLayer.MaskType newType = (layer.getMaskType() == CanvasLayer.MaskType.MASK) ? CanvasLayer.MaskType.NONE : CanvasLayer.MaskType.MASK;
                    layer.setMaskType(newType);
                    populateBlendingOpacityPanel(layer);
                    binding.canvasView.invalidate();
                }
            });
        }

        if (cardExclude != null) {
            cardExclude.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer != null) {
                    CanvasLayer.MaskType newType = (layer.getMaskType() == CanvasLayer.MaskType.EXCLUDE) ? CanvasLayer.MaskType.NONE : CanvasLayer.MaskType.EXCLUDE;
                    layer.setMaskType(newType);
                    populateBlendingOpacityPanel(layer);
                    binding.canvasView.invalidate();
                }
            });
        }
    }

    /**
     * Sets up accordion sections with mutual-collapse behavior.
     * Each group is an int[3]: {headerId, containerId, arrowId}.
     * Clicking any header expands it and collapses all others.
     */
    private void setupMutualAccordion(View root, int[][] groups) {
        View[] containers = new View[groups.length];
        ImageView[] arrows = new ImageView[groups.length];
        for (int i = 0; i < groups.length; i++) {
            containers[i] = root.findViewById(groups[i][1]);
            arrows[i] = root.findViewById(groups[i][2]);
        }
        for (int i = 0; i < groups.length; i++) {
            final int idx = i;
            View header = root.findViewById(groups[i][0]);
            if (header == null) continue;
            header.setOnClickListener(v -> {
                boolean isAlreadyOpen = containers[idx] != null
                        && containers[idx].getVisibility() == View.VISIBLE;
                // Collapse all
                for (int j = 0; j < groups.length; j++) {
                    if (containers[j] != null) containers[j].setVisibility(View.GONE);
                    if (arrows[j] != null) arrows[j].setRotation(0f);
                }
                // Toggle open if not already open
                if (!isAlreadyOpen && containers[idx] != null) {
                    containers[idx].setVisibility(View.VISIBLE);
                    if (arrows[idx] != null) arrows[idx].setRotation(90f);
                }
            });
        }
    }

    @Deprecated
    private void setupAccordion(View root, int headerId, int containerId, int arrowId) {
        View header = root.findViewById(headerId);
        View container = root.findViewById(containerId);
        ImageView arrow = root.findViewById(arrowId);
        if (header != null && container != null) {
            header.setOnClickListener(v -> {
                boolean isExp = container.getVisibility() == View.VISIBLE;
                container.setVisibility(isExp ? View.GONE : View.VISIBLE);
                if (arrow != null) {
                    arrow.setRotation(isExp ? 0f : 90f);
                }
            });
        }
    }

    private void bindBlendCard(View panel, int cardId, CanvasLayer.BlendMode mode) {
        View card = panel.findViewById(cardId);
        if (card != null) {
            card.setOnClickListener(v -> {
                CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
                if (layer != null) {
                    CanvasLayer.BlendMode newMode = (layer.getBlendMode() == mode) ? CanvasLayer.BlendMode.NORMAL : mode;
                    layer.setBlendMode(newMode);
                    populateBlendingOpacityPanel(layer);
                    binding.canvasView.invalidate();
                }
            });
        }
    }

    private void populateBlendingOpacityPanel(CanvasLayer layer) {
        if (layer == null) return;
        View panel = binding.flipperBottomPanels.getChildAt(PANEL_BLENDING_OPACITY);
        if (panel == null) return;

        ClampedSliderView sliderOpacity = panel.findViewById(R.id.sliderLayerOpacity);
        TextView tvOpacityVal = panel.findViewById(R.id.tvLayerOpacityVal);

        if (sliderOpacity != null) {
            sliderOpacity.setValue(Math.round(layer.getOpacityPercent()));
        }
        if (tvOpacityVal != null) {
            tvOpacityVal.setText(Math.round(layer.getOpacityPercent()) + "%");
        }

        // Highlight active blend mode card
        CanvasLayer.BlendMode mode = layer.getBlendMode();
        boolean isNormal = (mode == CanvasLayer.BlendMode.NORMAL);

        // Normal card
        MaterialCardView cardNormal = panel.findViewById(R.id.cardBlendNormal);
        TextView tvLabelNormal = panel.findViewById(R.id.tvLabelNormal);
        if (cardNormal != null) {
            cardNormal.setStrokeColor(isNormal ? 0xFF00E5BC : 0x22FFFFFF);
            cardNormal.setCardBackgroundColor(isNormal ? 0xFF243048 : 0xFF1E273C);
            if (tvLabelNormal != null) tvLabelNormal.setTextColor(isNormal ? 0xFF00E5BC : 0xFF94A3B8);
        }

        highlightBlendCard(panel, R.id.cardBlendMultiply, R.id.tvLabelMultiply, mode == CanvasLayer.BlendMode.MULTIPLY);
        highlightBlendCard(panel, R.id.cardBlendDarken, R.id.tvLabelDarken, mode == CanvasLayer.BlendMode.DARKEN);
        highlightBlendCard(panel, R.id.cardBlendColorBurn, R.id.tvLabelColorBurn, mode == CanvasLayer.BlendMode.COLOR_BURN);
        highlightBlendCard(panel, R.id.cardBlendLinearBurn, R.id.tvLabelLinearBurn, mode == CanvasLayer.BlendMode.LINEAR_BURN);

        highlightBlendCard(panel, R.id.cardBlendScreen, R.id.tvLabelScreen, mode == CanvasLayer.BlendMode.SCREEN);
        highlightBlendCard(panel, R.id.cardBlendLighten, R.id.tvLabelLighten, mode == CanvasLayer.BlendMode.LIGHTEN);
        highlightBlendCard(panel, R.id.cardBlendColorDodge, R.id.tvLabelColorDodge, mode == CanvasLayer.BlendMode.COLOR_DODGE);
        highlightBlendCard(panel, R.id.cardBlendLinearDodge, R.id.tvLabelLinearDodge, mode == CanvasLayer.BlendMode.LINEAR_DODGE);

        highlightBlendCard(panel, R.id.cardBlendOverlay, R.id.tvLabelOverlay, mode == CanvasLayer.BlendMode.OVERLAY);
        highlightBlendCard(panel, R.id.cardBlendSoftLight, R.id.tvLabelSoftLight, mode == CanvasLayer.BlendMode.SOFT_LIGHT);
        highlightBlendCard(panel, R.id.cardBlendHardLight, R.id.tvLabelHardLight, mode == CanvasLayer.BlendMode.HARD_LIGHT);

        highlightBlendCard(panel, R.id.cardBlendDifference, R.id.tvLabelDifference, mode == CanvasLayer.BlendMode.DIFFERENCE);
        highlightBlendCard(panel, R.id.cardBlendExclusion, R.id.tvLabelExclusion, mode == CanvasLayer.BlendMode.EXCLUSION);

        highlightBlendCard(panel, R.id.cardBlendHue, R.id.tvLabelHue, mode == CanvasLayer.BlendMode.HUE);
        highlightBlendCard(panel, R.id.cardBlendSat, R.id.tvLabelSat, mode == CanvasLayer.BlendMode.SATURATION);
        highlightBlendCard(panel, R.id.cardBlendColorMode, R.id.tvLabelColorMode, mode == CanvasLayer.BlendMode.COLOR);
        highlightBlendCard(panel, R.id.cardBlendLum, R.id.tvLabelLum, mode == CanvasLayer.BlendMode.LUMINOSITY);

        // Determine effective mask status:
        // (a) Layer's own MaskType, OR (b) layer is the last child of a group with MASK/EXCLUDE mode
        CanvasLayer.MaskType maskType = layer.getMaskType();
        if (maskType == CanvasLayer.MaskType.NONE && project != null) {
            // Check if this layer is inside a masked group and is the top/last child (mask target)
            for (CanvasLayer topLayer : project.getLayers()) {
                if (topLayer instanceof GroupLayer) {
                    GroupLayer grp = (GroupLayer) topLayer;
                    List<CanvasLayer> children = grp.getChildren();
                    if (!children.isEmpty() && children.get(children.size() - 1) == layer) {
                        GroupLayer.GroupMaskMode gm = grp.getGroupMaskMode();
                        if (gm == GroupLayer.GroupMaskMode.MASK) {
                            maskType = CanvasLayer.MaskType.MASK;
                        } else if (gm == GroupLayer.GroupMaskMode.EXCLUDE) {
                            maskType = CanvasLayer.MaskType.EXCLUDE;
                        }
                        break;
                    }
                }
            }
        }

        // Highlight mask card
        MaterialCardView cardMask = panel.findViewById(R.id.cardBlendMask);
        MaterialCardView cardExclude = panel.findViewById(R.id.cardBlendExclude);
        TextView tvLabelMask = panel.findViewById(R.id.tvLabelMask);
        TextView tvLabelExclude = panel.findViewById(R.id.tvLabelExclude);

        if (cardMask != null) {
            boolean active = maskType == CanvasLayer.MaskType.MASK;
            cardMask.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
            cardMask.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
            if (tvLabelMask != null) tvLabelMask.setTextColor(active ? 0xFF00E5BC : 0xFF94A3B8);
        }
        if (cardExclude != null) {
            boolean active = maskType == CanvasLayer.MaskType.EXCLUDE;
            cardExclude.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
            cardExclude.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
            if (tvLabelExclude != null) tvLabelExclude.setTextColor(active ? 0xFF00E5BC : 0xFF94A3B8);
        }
    }

    private void highlightBlendCard(View panel, int cardId, int labelId, boolean active) {
        MaterialCardView card = panel.findViewById(cardId);
        TextView label = panel.findViewById(labelId);
        if (card != null) {
            card.setStrokeColor(active ? 0xFF00E5BC : 0x22FFFFFF);
            card.setCardBackgroundColor(active ? 0xFF243048 : 0xFF182234);
        }
        if (label != null) {
            label.setTextColor(active ? 0xFF00E5BC : 0xFF94A3B8);
        }
    }

    // -------------------------------------------------------------
    // GROUPING & UNGROUPING SYSTEM (Isolated Sub-Canvas Masking)
    // -------------------------------------------------------------
    private void groupSelectedLayers(GroupLayer.GroupMaskMode maskMode) {
        if (project == null || selectedMultiLayers.size() < 2) {
            Toast.makeText(this, "Select at least 2 layers to group", Toast.LENGTH_SHORT).show();
            return;
        }

        // Collect layers in project order
        List<CanvasLayer> orderedChildren = new ArrayList<>();
        List<CanvasLayer> projectLayers = project.getLayers();
        int insertIndex = 0;
        for (int i = 0; i < projectLayers.size(); i++) {
            CanvasLayer layer = projectLayers.get(i);
            if (selectedMultiLayers.contains(layer)) {
                orderedChildren.add(layer);
                insertIndex = i;
            }
        }

        if (orderedChildren.size() < 2) return;

        // Group name
        String groupName = (maskMode == GroupLayer.GroupMaskMode.MASK) ? "Mask Group " :
                (maskMode == GroupLayer.GroupMaskMode.EXCLUDE) ? "Exclude Group " : "Group ";
        groupName += (projectLayers.size() + 1);

        GroupLayer group = new GroupLayer(groupName, orderedChildren, maskMode);

        // Remove children from project
        for (CanvasLayer child : orderedChildren) {
            projectLayers.remove(child);
        }

        // Insert group at appropriate index
        insertIndex = Math.min(insertIndex, projectLayers.size());
        projectLayers.add(insertIndex, group);

        // Clear multi selection
        selectedMultiLayers.clear();
        project.setSelectedIndex(insertIndex);

        binding.canvasView.invalidate();
        populateLayersOverviewPanel();
        updateUIForActiveLayer(group);
        Toast.makeText(this, "Created " + groupName, Toast.LENGTH_SHORT).show();
    }

    private void enterGroupEditMode(GroupLayer groupLayer) {
        if (groupLayer == null) return;
        if (rootProject == null) {
            rootProject = project;
        }

        float canvasW = project.getCanvasWidth();
        float canvasH = project.getCanvasHeight();
        float cx = canvasW / 2f;
        float cy = canvasH / 2f;

        EditorProject groupProject = new EditorProject();
        groupProject.setTitle(groupLayer.getName());
        groupProject.setCanvasWidth((int) canvasW);
        groupProject.setCanvasHeight((int) canvasH);
        groupProject.setBackgroundColor(0x00000000); // transparent/checkered

        for (CanvasLayer child : groupLayer.getChildren()) {
            child.setX(child.getX() + cx);
            child.setY(child.getY() + cy);
            groupProject.getLayers().add(child);
        }

        groupEditSessionStack.push(new GroupEditSession(groupLayer, groupProject));
        this.project = groupProject;
        binding.canvasView.setProject(groupProject);

        updateBreadcrumbsUI();

        if (!groupProject.getLayers().isEmpty()) {
            groupProject.setSelectedIndex(groupProject.getLayers().size() - 1);
            updateUIForActiveLayer(groupProject.getSelectedLayer());
        } else {
            groupProject.setSelectedIndex(-1);
            updateUIForActiveLayer(null);
        }

        binding.canvasView.invalidate();
        populateLayersOverviewPanel();
        Toast.makeText(this, "Editing " + groupLayer.getName(), Toast.LENGTH_SHORT).show();
    }

    private boolean exitGroupEditMode() {
        if (groupEditSessionStack.isEmpty()) return false;

        GroupEditSession session = groupEditSessionStack.pop();
        GroupLayer groupLayer = session.groupLayer;
        EditorProject groupProject = session.groupProject;

        float canvasW = groupProject.getCanvasWidth();
        float canvasH = groupProject.getCanvasHeight();
        float cx = canvasW / 2f;
        float cy = canvasH / 2f;

        List<CanvasLayer> updatedChildren = groupProject.getLayers();
        groupLayer.getChildren().clear();

        if (!updatedChildren.isEmpty()) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (CanvasLayer child : updatedChildren) {
                float hw = (child.getWidth() * Math.abs(child.getScaleX())) / 2f;
                float hh = (child.getHeight() * Math.abs(child.getScaleY())) / 2f;
                minX = Math.min(minX, child.getX() - hw);
                minY = Math.min(minY, child.getY() - hh);
                maxX = Math.max(maxX, child.getX() + hw);
                maxY = Math.max(maxY, child.getY() + hh);
            }

            float newCenterXInGroupCanvas = (minX + maxX) / 2f;
            float newCenterYInGroupCanvas = (minY + maxY) / 2f;

            float shiftX = newCenterXInGroupCanvas - cx;
            float shiftY = newCenterYInGroupCanvas - cy;

            groupLayer.setX(groupLayer.getX() + shiftX);
            groupLayer.setY(groupLayer.getY() + shiftY);
            groupLayer.setWidth(Math.max(50f, maxX - minX));
            groupLayer.setHeight(Math.max(50f, maxY - minY));

            for (CanvasLayer child : updatedChildren) {
                child.setX(child.getX() - newCenterXInGroupCanvas);
                child.setY(child.getY() - newCenterYInGroupCanvas);
                groupLayer.addChild(child);
            }
        }

        if (!groupEditSessionStack.isEmpty()) {
            this.project = groupEditSessionStack.peek().groupProject;
        } else {
            this.project = rootProject;
            rootProject = null;
        }

        binding.canvasView.setProject(project);
        updateBreadcrumbsUI();

        int groupIdx = project.getLayers().indexOf(groupLayer);
        project.setSelectedIndex(groupIdx);
        updateUIForActiveLayer(groupLayer);

        binding.canvasView.invalidate();
        populateLayersOverviewPanel();
        return true;
    }

    private void ungroupSelectedLayer() {
        if (project == null) return;
        CanvasLayer selected = project.getSelectedLayer();
        if (!(selected instanceof GroupLayer)) {
            // Check if one of the multi-selected layers is a GroupLayer
            GroupLayer groupToUngroup = null;
            for (CanvasLayer l : selectedMultiLayers) {
                if (l instanceof GroupLayer) {
                    groupToUngroup = (GroupLayer) l;
                    break;
                }
            }
            if (groupToUngroup == null) {
                Toast.makeText(this, "Select a Group to ungroup", Toast.LENGTH_SHORT).show();
                return;
            }
            selected = groupToUngroup;
        }

        GroupLayer group = (GroupLayer) selected;
        int groupIndex = project.getLayers().indexOf(group);
        if (groupIndex < 0) return;

        List<CanvasLayer> children = group.getChildren();
        project.getLayers().remove(groupIndex);

        // Add back children
        for (int i = 0; i < children.size(); i++) {
            CanvasLayer child = children.get(i);
            child.setX(child.getX() + group.getX());
            child.setY(child.getY() + group.getY());
            child.setRotation((child.getRotation() + group.getRotation()) % 360f);
            project.getLayers().add(groupIndex + i, child);
        }

        selectedMultiLayers.clear();
        project.setSelectedIndex(groupIndex);
        binding.canvasView.invalidate();
        populateLayersOverviewPanel();
        updateUIForActiveLayer(project.getSelectedLayer());
        Toast.makeText(this, "Ungrouped " + group.getName(), Toast.LENGTH_SHORT).show();
    }

    // -------------------------------------------------------------
    // 6. ADD ELEMENT SHEET (Loads all shapes from assets/shapes/*.xml)
    // -------------------------------------------------------------
    private void setupAddElementSheet() {
        RecyclerView rv = binding.getRoot().findViewById(R.id.rvAddShapesGrid);
        List<ShapeDefinition> allShapes = ShapeHelper.getAllShapes(this);

        View tabShape = binding.getRoot().findViewById(R.id.tabAddShape);
        View tabElements = binding.getRoot().findViewById(R.id.tabAddElements);
        View tabIcons = binding.getRoot().findViewById(R.id.tabAddIcons);
        ImageView ivTabShape = binding.getRoot().findViewById(R.id.ivTabShape);
        TextView tvTabShape = binding.getRoot().findViewById(R.id.tvTabShape);
        ImageView ivTabElements = binding.getRoot().findViewById(R.id.ivTabElements);
        TextView tvTabElements = binding.getRoot().findViewById(R.id.tvTabElements);
        ImageView ivTabIcons = binding.getRoot().findViewById(R.id.ivTabIcons);
        TextView tvTabIcons = binding.getRoot().findViewById(R.id.tvTabIcons);

        View searchBar = binding.getRoot().findViewById(R.id.layoutIconSearchBar);
        EditText etSearch = binding.getRoot().findViewById(R.id.etIconSearch);
        View btnClearSearch = binding.getRoot().findViewById(R.id.btnClearIconSearch);

        Runnable showShapesGrid = () -> {
            if (searchBar != null) searchBar.setVisibility(View.GONE);
            if (ivTabShape != null) ivTabShape.setColorFilter(0xFF00E5BC);
            if (tvTabShape != null) tvTabShape.setTextColor(0xFF00E5BC);
            if (ivTabElements != null) ivTabElements.setColorFilter(0xFF64748B);
            if (tvTabElements != null) tvTabElements.setTextColor(0xFF94A3B8);
            if (ivTabIcons != null) ivTabIcons.setColorFilter(0xFF64748B);
            if (tvTabIcons != null) tvTabIcons.setTextColor(0xFF94A3B8);

            if (rv != null && allShapes != null && !allShapes.isEmpty()) {
                rv.setLayoutManager(new GridLayoutManager(this, 4));
                rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull
                    @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        View view = getLayoutInflater().inflate(R.layout.item_add_shape_cell, parent, false);
                        return new RecyclerView.ViewHolder(view) {};
                    }

                    @Override
                    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                        ShapeDefinition sDef = allShapes.get(position);
                        ImageView ivIcon = holder.itemView.findViewById(R.id.ivShapeIcon);
                        TextView tvName = holder.itemView.findViewById(R.id.tvShapeName);

                        if (tvName != null) {
                            tvName.setText(sDef.getName());
                        }

                        if (ivIcon != null) {
                            Bitmap thumb = ShapeGeometryHelper.renderShapeThumbnail(sDef, 80, 0xFF00E5BC);
                            ivIcon.setImageBitmap(thumb);
                        }

                        View.OnClickListener addShapeClick = v -> {
                            float cx = project.getCanvasWidth() / 2f;
                            float cy = project.getCanvasHeight() / 2f;
                            ShapeDefinition sCopy = sDef.copy();
                            ShapeLayer shape = new ShapeLayer(sDef.getName() + " " + (project.getLayers().size() + 1), cx, cy, 300, 300);
                            shape.setShapeDefinition(sCopy);
                            shape.setFillColor(paletteColors[(project.getLayers().size()) % paletteColors.length]);
                            shape.setCornerRadius(25f);

                            project.addLayer(shape);
                            binding.canvasView.invalidate();
                            binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
                            updateUIForActiveLayer(shape);
                        };
                        holder.itemView.setOnClickListener(addShapeClick);
                        if (ivIcon != null) ivIcon.setOnClickListener(addShapeClick);
                        if (tvName != null) tvName.setOnClickListener(addShapeClick);
                        View cardCell = holder.itemView.findViewById(R.id.cardShapeCell);
                        if (cardCell != null) cardCell.setOnClickListener(addShapeClick);
                    }

                    @Override
                    public int getItemCount() {
                        return allShapes.size();
                    }
                });
            }
        };

        Runnable showElementsGrid = () -> {
            if (searchBar != null) searchBar.setVisibility(View.GONE);
            if (ivTabShape != null) ivTabShape.setColorFilter(0xFF64748B);
            if (tvTabShape != null) tvTabShape.setTextColor(0xFF94A3B8);
            if (ivTabElements != null) ivTabElements.setColorFilter(0xFF00E5BC);
            if (tvTabElements != null) tvTabElements.setTextColor(0xFF00E5BC);
            if (ivTabIcons != null) ivTabIcons.setColorFilter(0xFF64748B);
            if (tvTabIcons != null) tvTabIcons.setTextColor(0xFF94A3B8);

            List<ProjectStorageManager.ProjectItem> allProjects = ProjectStorageManager.loadAllProjects(this, false);
            List<ProjectStorageManager.ProjectItem> savedElements = new ArrayList<>();
            for (ProjectStorageManager.ProjectItem p : allProjects) {
                if (p.isElement() && !p.isInTrash()) {
                    savedElements.add(p);
                }
            }

            if (rv != null) {
                if (savedElements.isEmpty()) {
                    rv.setLayoutManager(new LinearLayoutManager(this));
                    rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        @NonNull
                        @Override
                        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                            TextView tv = new TextView(MainActivity.this);
                            tv.setText("No saved elements found.\nCreate an element from Home with transparent background to reuse it here.");
                            tv.setTextColor(0xFF94A3B8);
                            tv.setTextSize(12);
                            tv.setGravity(Gravity.CENTER);
                            tv.setPadding(32, 64, 32, 64);
                            tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                            return new RecyclerView.ViewHolder(tv) {};
                        }

                        @Override
                        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}

                        @Override
                        public int getItemCount() { return 1; }
                    });
                } else {
                    rv.setLayoutManager(new GridLayoutManager(this, 3));
                    rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        @NonNull
                        @Override
                        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                            View view = getLayoutInflater().inflate(R.layout.item_add_shape_cell, parent, false);
                            return new RecyclerView.ViewHolder(view) {};
                        }

                        @Override
                        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                            ProjectStorageManager.ProjectItem elemItem = savedElements.get(position);
                            ImageView ivIcon = holder.itemView.findViewById(R.id.ivShapeIcon);
                            TextView tvName = holder.itemView.findViewById(R.id.tvShapeName);

                            if (tvName != null) {
                                tvName.setText(elemItem.getTitle());
                            }

                            if (ivIcon != null) {
                                Bitmap thumb = ProjectStorageManager.loadThumbnail(MainActivity.this, elemItem);
                                if (thumb != null) {
                                    ivIcon.setImageBitmap(thumb);
                                } else {
                                    ivIcon.setImageResource(R.drawable.ic_pe_element);
                                    ivIcon.setColorFilter(0xFF00E5BC);
                                }
                            }

                            View.OnClickListener insertElementClick = v -> {
                                EditorProject elemProj = ProjectStorageManager.loadProjectContent(MainActivity.this, elemItem.getId());
                                if (elemProj != null && !elemProj.getLayers().isEmpty()) {
                                    CanvasLayer lastAdded = null;
                                    for (CanvasLayer l : elemProj.getLayers()) {
                                        CanvasLayer copy = l.copy();
                                        copy.setX(project.getCanvasWidth() / 2f);
                                        copy.setY(project.getCanvasHeight() / 2f);
                                        project.addLayer(copy);
                                        lastAdded = copy;
                                    }
                                    binding.canvasView.invalidate();
                                    binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
                                    if (lastAdded != null) {
                                        updateUIForActiveLayer(lastAdded);
                                    }
                                }
                            };
                            holder.itemView.setOnClickListener(insertElementClick);
                            if (ivIcon != null) ivIcon.setOnClickListener(insertElementClick);
                            if (tvName != null) tvName.setOnClickListener(insertElementClick);
                        }

                        @Override
                        public int getItemCount() {
                            return savedElements.size();
                        }
                    });
                }
            }
        };

        Runnable showIconsGrid = () -> {
            if (searchBar != null) searchBar.setVisibility(View.VISIBLE);
            if (ivTabShape != null) ivTabShape.setColorFilter(0xFF64748B);
            if (tvTabShape != null) tvTabShape.setTextColor(0xFF94A3B8);
            if (ivTabElements != null) ivTabElements.setColorFilter(0xFF64748B);
            if (tvTabElements != null) tvTabElements.setTextColor(0xFF94A3B8);
            if (ivTabIcons != null) ivTabIcons.setColorFilter(0xFF00E5BC);
            if (tvTabIcons != null) tvTabIcons.setTextColor(0xFF00E5BC);

            List<SvgIconItem> allIcons = SvgIconManager.getAllIcons(this);
            if (rv != null) {
                rv.setLayoutManager(new GridLayoutManager(this, 4));
                SvgIconAdapter iconAdapter = new SvgIconAdapter(this);
                iconAdapter.setItems(allIcons);
                if (etSearch != null && etSearch.getText() != null) {
                    String currentQ = etSearch.getText().toString();
                    if (!currentQ.isEmpty()) {
                        iconAdapter.filter(currentQ);
                    }
                }
                iconAdapter.setOnIconSelectedListener(iconItem -> {
                    float cx = project.getCanvasWidth() / 2f;
                    float cy = project.getCanvasHeight() / 2f;
                    ShapeLayer shape = SvgIconManager.createShapeLayerFromSvg(this, iconItem, cx, cy);
                    if (shape != null) {
                        project.addLayer(shape);
                        binding.canvasView.invalidate();
                        binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
                        updateUIForActiveLayer(shape);
                    }
                });
                rv.setAdapter(iconAdapter);
            }
        };

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String q = s != null ? s.toString() : "";
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    if (rv != null && rv.getAdapter() instanceof SvgIconAdapter) {
                        ((SvgIconAdapter) rv.getAdapter()).filter(q);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                if (etSearch != null) etSearch.setText("");
                if (rv != null && rv.getAdapter() instanceof SvgIconAdapter) {
                    ((SvgIconAdapter) rv.getAdapter()).filter("");
                }
            });
        }

        if (tabShape != null) tabShape.setOnClickListener(v -> showShapesGrid.run());
        if (tabElements != null) tabElements.setOnClickListener(v -> showElementsGrid.run());
        if (tabIcons != null) tabIcons.setOnClickListener(v -> showIconsGrid.run());

        showShapesGrid.run();

        // Add Media tab
        binding.getRoot().findViewById(R.id.tabAddMedia).setOnClickListener(v -> {
            photoPickerLauncher.launch("image/*");
        });

        // Add Text tab & rail
        View.OnClickListener textClick = v -> {
            float cx = project.getCanvasWidth() / 2f;
            float cy = project.getCanvasHeight() / 2f;
            TextLayer textLayer = new TextLayer("Heading Text " + (project.getLayers().size() + 1), "Pixel Editor", cx, cy);
            project.addLayer(textLayer);
            binding.canvasView.invalidate();
            binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
            updateUIForActiveLayer(textLayer);
        };
        binding.getRoot().findViewById(R.id.tabAddText).setOnClickListener(textClick);
        binding.getRoot().findViewById(R.id.btnRailText).setOnClickListener(textClick);

        // Freehand drawing rail
        binding.getRoot().findViewById(R.id.btnRailFreehand).setOnClickListener(v -> {
        });

        // Vector Pen rail
        binding.getRoot().findViewById(R.id.btnRailVector).setOnClickListener(v -> {
        });

        // Close 'X' button
        View btnCloseAdd = binding.getRoot().findViewById(R.id.btnCloseAddSheet);
        if (btnCloseAdd != null) {
            btnCloseAdd.setOnClickListener(v -> {
                if (project != null && project.getSelectedLayer() != null) {
                    binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
                } else {
                    binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYERS_OVERVIEW);
                    populateLayersOverviewPanel();
                }
            });
        }
    }

    // -------------------------------------------------------------
    // 7. EFFECT BROWSER & EFFECTS HELPER
    // -------------------------------------------------------------
    private void setupEffectBrowser() {
        binding.getRoot().findViewById(R.id.btnCloseEffectBrowser).setOnClickListener(v -> {
            View layoutFiltered = binding.getRoot().findViewById(R.id.layoutFilteredEffectsGrid);
            if (layoutFiltered != null && layoutFiltered.getVisibility() == View.VISIBLE) {
                showMainCategoryView();
            } else {
                binding.containerEffectBrowser.setVisibility(View.GONE);
            }
        });

        // Category bottom navigation (< and >)
        View btnPrev = binding.getRoot().findViewById(R.id.btnCategoryPrev);
        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                if (!availableCategories.isEmpty() && currentCategoryIndex >= 0) {
                    currentCategoryIndex = (currentCategoryIndex - 1 + availableCategories.size()) % availableCategories.size();
                    displayCategoryByIndex(currentCategoryIndex);
                }
            });
        }

        View btnNext = binding.getRoot().findViewById(R.id.btnCategoryNext);
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                if (!availableCategories.isEmpty() && currentCategoryIndex >= 0) {
                    currentCategoryIndex = (currentCategoryIndex + 1) % availableCategories.size();
                    displayCategoryByIndex(currentCategoryIndex);
                }
            });
        }

        // Search Bar Toggle
        View layoutSearchBar = binding.getRoot().findViewById(R.id.layoutSearchBar);
        EditText etSearch = binding.getRoot().findViewById(R.id.etEffectSearch);
        ImageButton btnSearch = binding.getRoot().findViewById(R.id.btnSearchEffects);
        ImageButton btnClearSearch = binding.getRoot().findViewById(R.id.btnClearSearch);

        btnSearch.setOnClickListener(v -> {
            if (layoutSearchBar.getVisibility() == View.VISIBLE) {
                layoutSearchBar.setVisibility(View.GONE);
                etSearch.setText("");
                showMainCategoryView();
            } else {
                layoutSearchBar.setVisibility(View.VISIBLE);
                etSearch.requestFocus();
            }
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            showMainCategoryView();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    showMainCategoryView();
                } else {
                    List<EffectDefinition> results = EffectHelper.searchEffects(MainActivity.this, query);
                    displayFilteredEffects("Search: \"" + query + "\"", results, false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadEffectsAsync() {
        new Thread(() -> {
            List<EffectDefinition> list = EffectHelper.getAllEffects(this);
            runOnUiThread(() -> {
                allEffects = list;
                populateCategoriesGrid();
                populateQuickEffects();
            });
        }).start();
    }

    private void populateQuickEffects() {
        LinearLayout layoutQuick = binding.getRoot().findViewById(R.id.layoutQuickEffects);
        layoutQuick.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        String[] popularKeys = {"brightness-contrast", "gaussianblur", "chromakey", "vignette", "fourcolorgradient", "exposure"};

        for (String key : popularKeys) {
            EffectDefinition eff = null;
            for (EffectDefinition item : allEffects) {
                if (item.getFileName().startsWith(key) || item.getId().toLowerCase().contains(key)) {
                    eff = item;
                    break;
                }
            }
            if (eff == null) continue;

            final EffectDefinition finalEff = eff;
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(14 * density);
            card.setCardBackgroundColor(0xFF1E273C);
            card.setStrokeWidth(1);
            card.setStrokeColor(0x33FFFFFF);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (110 * density),
                    (int) (90 * density)
            );
            lp.setMargins(6, 0, 6, 0);
            card.setLayoutParams(lp);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setPadding(6, 6, 6, 6);

            // Thumbnail
            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new LinearLayout.LayoutParams((int) (42 * density), (int) (42 * density)));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Drawable thumbDrawable = EffectHelper.loadThumbnailDrawable(this, finalEff);
            if (thumbDrawable != null) {
                iv.setImageDrawable(thumbDrawable);
            } else {
                iv.setImageResource(R.drawable.ic_tool_effects);
                iv.setColorFilter(0xFF00E5BC);
            }
            col.addView(iv);

            TextView tv = new TextView(this);
            tv.setText(finalEff.getName());
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(11);
            tv.setGravity(Gravity.CENTER);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setMaxLines(1);
            tv.setPadding(2, 4, 2, 0);
            col.addView(tv);

            card.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            card.setOnClickListener(v -> openEffectControls(finalEff));

            layoutQuick.addView(card);
        }
    }

    private void populateCategoriesGrid() {
        LinearLayout catGrid = binding.getRoot().findViewById(R.id.gridEffectCategories);
        catGrid.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        availableCategories = EffectHelper.getCategories(this);

        int[][] catColors = {
                {0xFF212529, 0xFF2B2D42},
                {0xFF1F2421, 0xFF191924},
                {0xFF2E1F27, 0xFF1D2D44},
                {0xFF283618, 0xFF3F37C9},
                {0xFF49111C, 0xFF240046},
                {0xFF202020, 0xFF1A1A1A}
        };

        for (int i = 0; i < availableCategories.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (70 * density)
            ));
            ((LinearLayout.LayoutParams) row.getLayoutParams()).setMargins(0, 6, 0, 6);

            for (int c = 0; c < 2; c++) {
                int index = i + c;
                if (index >= availableCategories.size()) break;

                final int catIndex = index;
                final String catTitle = availableCategories.get(index);
                MaterialCardView card = new MaterialCardView(this);
                card.setRadius(14 * density);
                int colorIndex = (i / 2) % catColors.length;
                card.setCardBackgroundColor(catColors[colorIndex][c]);
                card.setStrokeWidth(1);
                card.setStrokeColor(0x33FFFFFF);

                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (c == 0) clp.setMarginEnd((int) (6 * density));
                else clp.setMarginStart((int) (6 * density));
                card.setLayoutParams(clp);

                LinearLayout inner = new LinearLayout(this);
                inner.setOrientation(LinearLayout.VERTICAL);
                inner.setGravity(Gravity.CENTER);

                TextView tv = new TextView(this);
                tv.setText(catTitle);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(13);
                tv.setTypeface(null, Typeface.BOLD);
                tv.setGravity(Gravity.CENTER);
                inner.addView(tv);

                List<EffectDefinition> effs = EffectHelper.getEffectsByCategory(this, catTitle);
                TextView tvCount = new TextView(this);
                tvCount.setText(effs.size() + " effects");
                tvCount.setTextColor(0xFF00E5BC);
                tvCount.setTextSize(10);
                inner.addView(tvCount);

                card.addView(inner, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                card.setOnClickListener(v -> displayCategoryByIndex(catIndex));

                row.addView(card);
            }
            catGrid.addView(row);
        }
    }

    private void displayCategoryByIndex(int index) {
        if (index < 0 || index >= availableCategories.size()) return;
        currentCategoryIndex = index;
        String catTitle = availableCategories.get(index);
        List<EffectDefinition> effs = EffectHelper.getEffectsByCategory(this, catTitle);
        displayFilteredEffects(catTitle, effs, true);
    }

    private void displayFilteredEffects(String title, List<EffectDefinition> effects, boolean showCategoryNav) {
        View layoutMain = binding.getRoot().findViewById(R.id.layoutMainCategoryView);
        LinearLayout layoutGrid = binding.getRoot().findViewById(R.id.layoutFilteredEffectsGrid);
        View layoutNav = binding.getRoot().findViewById(R.id.layoutCategoryBottomNav);
        TextView tvTitle = binding.getRoot().findViewById(R.id.tvEffectBrowserTitle);

        layoutMain.setVisibility(View.GONE);
        layoutGrid.setVisibility(View.VISIBLE);
        if (layoutNav != null) {
            layoutNav.setVisibility(showCategoryNav ? View.VISIBLE : View.GONE);
        }
        tvTitle.setText(title);

        layoutGrid.removeAllViews();

        if (effects.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No effects found matching this filter.");
            emptyTv.setTextColor(0xFF94A3B8);
            emptyTv.setPadding(0, 40, 0, 40);
            emptyTv.setGravity(Gravity.CENTER);
            layoutGrid.addView(emptyTv);
            return;
        }

        // Render 3-Column Grid matching Alight Motion layout
        final int columns = 3;
        for (int i = 0; i < effects.size(); i += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            for (int c = 0; c < columns; c++) {
                int itemIdx = i + c;
                if (itemIdx < effects.size()) {
                    EffectDefinition eff = effects.get(itemIdx);
                    View itemCard = getLayoutInflater().inflate(R.layout.item_effect_grid_card, row, false);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    itemCard.setLayoutParams(lp);

                    TextView tvName = itemCard.findViewById(R.id.tvEffectName);
                    ImageView ivThumb = itemCard.findViewById(R.id.ivEffectThumbnail);
                    View thumbCard = itemCard.findViewById(R.id.cardEffectThumb);

                    tvName.setText(eff.getName());

                    Drawable thumbDrawable = EffectHelper.loadThumbnailDrawable(this, eff);
                    if (thumbDrawable != null) {
                        ivThumb.setImageDrawable(thumbDrawable);
                    } else {
                        ivThumb.setImageResource(R.drawable.ic_tool_effects);
                        ivThumb.setColorFilter(0xFF00E5BC);
                    }

                    View.OnClickListener effectClick = v -> openEffectControls(eff);
                    itemCard.setOnClickListener(effectClick);
                    if (thumbCard != null) thumbCard.setOnClickListener(effectClick);
                    if (ivThumb != null) ivThumb.setOnClickListener(effectClick);
                    row.addView(itemCard);
                } else {
                    // Empty spacer view for proper alignment in the last row
                    View spacer = new View(this);
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, 0, 1f);
                    spacer.setLayoutParams(slp);
                    row.addView(spacer);
                }
            }

            layoutGrid.addView(row);
        }
    }

    private void showMainCategoryView() {
        currentCategoryIndex = -1;
        View layoutMain = binding.getRoot().findViewById(R.id.layoutMainCategoryView);
        LinearLayout layoutGrid = binding.getRoot().findViewById(R.id.layoutFilteredEffectsGrid);
        View layoutNav = binding.getRoot().findViewById(R.id.layoutCategoryBottomNav);
        TextView tvTitle = binding.getRoot().findViewById(R.id.tvEffectBrowserTitle);

        tvTitle.setText(R.string.effect_browser_title);
        layoutMain.setVisibility(View.VISIBLE);
        layoutGrid.setVisibility(View.GONE);
        if (layoutNav != null) {
            layoutNav.setVisibility(View.GONE);
        }
    }

    // -------------------------------------------------------------
    // 8. DYNAMIC EFFECT CONTROLS PANEL
    // -------------------------------------------------------------
    private void setupEffectControlsPanel() {
        binding.getRoot().findViewById(R.id.btnBackFromEffectControls).setOnClickListener(v ->
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));

        binding.getRoot().findViewById(R.id.btnEffectBackRail).setOnClickListener(v ->
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));

        binding.getRoot().findViewById(R.id.btnAddEffectCard).setOnClickListener(v ->
                binding.containerEffectBrowser.setVisibility(View.VISIBLE));

        binding.getRoot().findViewById(R.id.btnResetEffectParams).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null) {
                for (EffectDefinition eff : layer.getAppliedEffects()) {
                    eff.resetAllParams();
                }
                if (layer instanceof TextLayer) {
                    ((TextLayer) layer).recalculateBounds();
                }
                refreshAppliedEffectsPanel();
                binding.canvasView.invalidate();
            }
        });
    }

    public void openAppliedEffectsPanel() {
        CanvasLayer layer = project.getSelectedLayer();
        if (layer == null) {
            return;
        }
        binding.containerEffectBrowser.setVisibility(View.GONE);
        binding.flipperBottomPanels.setDisplayedChild(PANEL_EFFECT_CONTROLS);
        refreshAppliedEffectsPanel();
    }

    /**
     * Applies selected effect to the active layer and opens the expandable controls panel
     */
    public void openEffectControls(EffectDefinition effectTemplate) {
        CanvasLayer layer = project.getSelectedLayer();
        if (layer == null) {
            return;
        }

        EffectDefinition cloned = effectTemplate.copy();
        cloned.setExpanded(true);
        layer.addEffect(cloned);

        if (layer instanceof TextLayer) {
            ((TextLayer) layer).recalculateBounds();
        }

        binding.containerEffectBrowser.setVisibility(View.GONE);
        binding.flipperBottomPanels.setDisplayedChild(PANEL_EFFECT_CONTROLS);

        refreshAppliedEffectsPanel();
        binding.canvasView.invalidate();
    }

    private void refreshAppliedEffectsPanel() {
        CanvasLayer layer = project.getSelectedLayer();
        LinearLayout container = binding.getRoot().findViewById(R.id.containerAppliedEffects);
        TextView emptyView = binding.getRoot().findViewById(R.id.tvEmptyAppliedEffects);
        TextView tvTitle = binding.getRoot().findViewById(R.id.tvEffectControlsTitle);

        if (container == null) return;

        if (layer != null && tvTitle != null) {
            tvTitle.setText("Effects • " + layer.getName());
        } else if (tvTitle != null) {
            tvTitle.setText("Effects");
        }

        EffectControlHelper.populateAppliedEffectsList(
                this,
                getLayoutInflater(),
                container,
                emptyView,
                layer,
                new EffectControlHelper.OnEffectInteractionListener() {
                    @Override
                    public void onParamChanged(EffectDefinition effect, EffectParam param) {
                        if (layer instanceof TextLayer) {
                            ((TextLayer) layer).recalculateBounds();
                        }
                        binding.canvasView.invalidate();
                    }

                    @Override
                    public void onEffectToggled(EffectDefinition effect, boolean isEnabled) {
                        if (layer instanceof TextLayer) {
                            ((TextLayer) layer).recalculateBounds();
                        }
                        binding.canvasView.invalidate();
                    }

                    @Override
                    public void onEffectDeleted(EffectDefinition effect) {
                        if (layer != null) {
                            layer.removeEffect(effect);
                            if (layer instanceof TextLayer) {
                                ((TextLayer) layer).recalculateBounds();
                            }
                            refreshAppliedEffectsPanel();
                            binding.canvasView.invalidate();
                        }
                    }

                    @Override
                    public void onEffectReset(EffectDefinition effect) {
                        if (layer instanceof TextLayer) {
                            ((TextLayer) layer).recalculateBounds();
                        }
                        binding.canvasView.invalidate();
                    }
                }
        );
    }

    // -------------------------------------------------------------
    // UI UPDATES ON LAYER SELECTION
    // -------------------------------------------------------------
    @Override
    public void onLayerSelected(@Nullable CanvasLayer layer, int index) {
        updateUIForActiveLayer(layer);
    }

    @Override
    public void onLayerModified(CanvasLayer layer) {
        updateUIForActiveLayer(layer);
    }

    private void updateUIForActiveLayer(@Nullable CanvasLayer layer) {
        updateCanvasToolsState();
        if (layer == null) {
            binding.flipperBottomPanels.setVisibility(View.VISIBLE);
            binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYERS_OVERVIEW);
            populateLayersOverviewPanel();
            return;
        }

        binding.flipperBottomPanels.setVisibility(View.VISIBLE);
        if (binding.flipperBottomPanels.getDisplayedChild() == PANEL_LAYERS_OVERVIEW) {
            binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
        }

        // Dynamically update Edit Shape / Edit Text / Adjust Photo / Edit Group in Layer Menu
        ImageView ivEditShape = binding.getRoot().findViewById(R.id.ivEditShapeIcon);
        TextView tvEditShape = binding.getRoot().findViewById(R.id.tvEditShapeTitle);
        if (ivEditShape != null && tvEditShape != null) {
            if (layer instanceof GroupLayer) {
                ivEditShape.setImageResource(R.drawable.ic_pe_layers);
                tvEditShape.setText("Edit Group");
            } else if (layer instanceof TextLayer) {
                ivEditShape.setImageResource(R.drawable.ic_pe_element);
                tvEditShape.setText("Edit Text");
            } else if (layer instanceof PhotoLayer) {
                ivEditShape.setImageResource(R.drawable.ic_tool_shape);
                tvEditShape.setText("Image Size");
            } else {
                ivEditShape.setImageResource(R.drawable.ic_tool_shape);
                tvEditShape.setText("Edit Shape");
            }
        }

        // Update subpanel values if shape is selected
        if (layer instanceof ShapeLayer) {
            ShapeLayer sl = (ShapeLayer) layer;
            sl.ensureShapeDefinition(this);

            if (binding.flipperBottomPanels.getDisplayedChild() == PANEL_EDIT_SHAPE) {
                populateEditShapePanel(sl);
            }
        } else if (layer instanceof PhotoLayer) {
            PhotoLayer pl = (PhotoLayer) layer;
            ClampedSliderView sW = binding.getRoot().findViewById(R.id.sliderPhotoWidth);
            ClampedSliderView sH = binding.getRoot().findViewById(R.id.sliderPhotoHeight);
            TextView tvW = binding.getRoot().findViewById(R.id.tvPhotoWidthVal);
            TextView tvH = binding.getRoot().findViewById(R.id.tvPhotoHeightVal);
            if (sW != null && tvW != null) {
                sW.setValue(Math.min(3000, Math.max(20, pl.getWidth())));
                tvW.setText(Math.round(pl.getWidth()) + "px");
            }
            if (sH != null && tvH != null) {
                sH.setValue(Math.min(3000, Math.max(20, pl.getHeight())));
                tvH.setText(Math.round(pl.getHeight()) + "px");
            }
        }

        updateTransformCoordinatesUI();

        if (binding.flipperBottomPanels.getDisplayedChild() == PANEL_BORDER_SHADOW) {
            populateBorderShadowPanel(layer);
        }

        if (binding.flipperBottomPanels.getDisplayedChild() == PANEL_COLOR_FILL) {
            populateColorFillPanel(layer);
        }

        if (binding.flipperBottomPanels.getDisplayedChild() == PANEL_BLENDING_OPACITY) {
            populateBlendingOpacityPanel(layer);
        }

        if (binding.flipperBottomPanels.getDisplayedChild() == PANEL_EFFECT_CONTROLS) {
            refreshAppliedEffectsPanel();
        }
    }

    private void populateLayersOverviewPanel() {
        RecyclerView rv = binding.getRoot().findViewById(R.id.rvOverviewLayers);
        TextView tvCount = binding.getRoot().findViewById(R.id.tvOverviewLayersCount);
        View btnAdd = binding.getRoot().findViewById(R.id.btnOverviewAddLayer);

        int layerCount = project != null ? project.getLayers().size() : 0;
        if (tvCount != null) {
            tvCount.setText("Layers (" + layerCount + ")");
        }

        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> {
                binding.flipperBottomPanels.setVisibility(View.VISIBLE);
                binding.flipperBottomPanels.setDisplayedChild(SHEET_ADD_ELEMENT);
            });
        }

        // Multi-Selection Action Bar
        View multiActions = binding.getRoot().findViewById(R.id.layoutOverviewMultiSelectActions);
        View btnGroup = binding.getRoot().findViewById(R.id.btnOverviewGroup);
        View btnMaskGroup = binding.getRoot().findViewById(R.id.btnOverviewMaskGroup);
        View btnExcludeGroup = binding.getRoot().findViewById(R.id.btnOverviewExcludeGroup);
        View btnUngroup = binding.getRoot().findViewById(R.id.btnOverviewUngroup);
        View btnClear = binding.getRoot().findViewById(R.id.btnOverviewClearSelect);

        if (multiActions != null) {
            multiActions.setVisibility(!selectedMultiLayers.isEmpty() ? View.VISIBLE : View.GONE);
        }

        if (btnGroup != null) btnGroup.setOnClickListener(v -> groupSelectedLayers(GroupLayer.GroupMaskMode.NONE));
        if (btnMaskGroup != null) btnMaskGroup.setOnClickListener(v -> groupSelectedLayers(GroupLayer.GroupMaskMode.MASK));
        if (btnExcludeGroup != null) btnExcludeGroup.setOnClickListener(v -> groupSelectedLayers(GroupLayer.GroupMaskMode.EXCLUDE));
        if (btnUngroup != null) btnUngroup.setOnClickListener(v -> ungroupSelectedLayer());
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                selectedMultiLayers.clear();
                populateLayersOverviewPanel();
            });
        }

        if (rv == null || project == null) return;

        rv.setLayoutManager(new LinearLayoutManager(this));

        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                new RecyclerView.Adapter<RecyclerView.ViewHolder>() {

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = getLayoutInflater().inflate(R.layout.item_overview_layer_chip, parent, false);
                return new RecyclerView.ViewHolder(view) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                // Topmost layer is at position 0 in timeline stack
                int actualIndex = project.getLayers().size() - 1 - position;
                if (actualIndex < 0 || actualIndex >= project.getLayers().size()) return;
                CanvasLayer l = project.getLayers().get(actualIndex);

                ImageView ivIcon = holder.itemView.findViewById(R.id.ivOverviewChipIcon);
                TextView tvName = holder.itemView.findViewById(R.id.tvOverviewChipName);
                ImageButton btnVis = holder.itemView.findViewById(R.id.btnOverviewLayerVisibility);
                ImageButton btnLock = holder.itemView.findViewById(R.id.btnOverviewLayerlock);
                View card = holder.itemView.findViewById(R.id.cardOverviewLayerRow);
                MaterialCheckBox cbSelect = holder.itemView.findViewById(R.id.cbOverviewLayerSelect);

                if (tvName != null) {
                    tvName.setText(l.getName());
                }

                if (cbSelect != null) {
                    cbSelect.setOnCheckedChangeListener(null);
                    cbSelect.setChecked(selectedMultiLayers.contains(l));
                    cbSelect.setOnCheckedChangeListener((btn, isChecked) -> {
                        if (isChecked) {
                            selectedMultiLayers.add(l);
                        } else {
                            selectedMultiLayers.remove(l);
                        }
                        if (multiActions != null) {
                            multiActions.setVisibility(!selectedMultiLayers.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    });
                }

                if (ivIcon != null) {
                    if (l instanceof GroupLayer) {
                        GroupLayer gl = (GroupLayer) l;
                        if (gl.getGroupMaskMode() == GroupLayer.GroupMaskMode.MASK) {
                            ivIcon.setImageResource(R.drawable.ic_mask_circle_card);
                            ivIcon.clearColorFilter();
                        } else if (gl.getGroupMaskMode() == GroupLayer.GroupMaskMode.EXCLUDE) {
                            ivIcon.setImageResource(R.drawable.ic_exclude_circle_card);
                            ivIcon.clearColorFilter();
                        } else {
                            ivIcon.setImageResource(R.drawable.ic_pe_layers);
                            ivIcon.setColorFilter(0xFF00E5BC);
                        }
                    } else if (l instanceof TextLayer) {
                        ivIcon.setImageResource(R.drawable.ic_pe_text);
                        ivIcon.setColorFilter(0xFFFFD166);
                    } else if (l instanceof PhotoLayer) {
                        ivIcon.setImageResource(R.drawable.ic_tool_presets);
                        ivIcon.setColorFilter(0xFF00D2FF);
                    } else if (l instanceof ShapeLayer) {
                        ShapeLayer sl = (ShapeLayer) l;
                        if (sl.getShapeDefinition() != null) {
                            Bitmap thumb = ShapeGeometryHelper.renderShapeThumbnail(sl.getShapeDefinition(), 48, 0xFF00E5BC);
                            ivIcon.setImageBitmap(thumb);
                            ivIcon.clearColorFilter();
                        } else {
                            ivIcon.setImageResource(R.drawable.ic_tool_shape);
                            ivIcon.setColorFilter(0xFF00E5BC);
                        }
                    } else {
                        ivIcon.setImageResource(R.drawable.ic_pe_element);
                        ivIcon.setColorFilter(0xFF00E5BC);
                    }
                }

                // Visibility Toggle
                if (btnVis != null) {
                    btnVis.setImageResource(l.isVisible() ? R.drawable.ic_pe_visible : R.drawable.ic_pe_invisible);
                    btnVis.setColorFilter(l.isVisible() ? 0xFF00E5BC : 0xFF64748B);
                    btnVis.setOnClickListener(v -> {
                        l.setVisible(!l.isVisible());
                        btnVis.setImageResource(l.isVisible() ? R.drawable.ic_pe_visible : R.drawable.ic_pe_invisible);
                        btnVis.setColorFilter(l.isVisible() ? 0xFF00E5BC : 0xFF64748B);
                        binding.canvasView.invalidate();
                    });
                }

                // Lock / Unlock Toggle
                if (btnLock != null) {
                    btnLock.setImageResource(l.isLocked() ? R.drawable.baseline_lock_24 : R.drawable.ic_pe_unlock);
                    btnLock.setColorFilter(l.isLocked() ? 0xFFFF5252 : 0xFF64748B);
                    btnLock.setAlpha(l.isLocked() ? 1.0f : 0.6f);
                    btnLock.setOnClickListener(v -> {
                        l.setLocked(!l.isLocked());
                        btnLock.setImageResource(l.isLocked() ? R.drawable.baseline_lock_24 : R.drawable.ic_pe_unlock);
                        btnLock.setColorFilter(l.isLocked() ? 0xFFFF5252 : 0xFF64748B);
                        btnLock.setAlpha(l.isLocked() ? 1.0f : 0.6f);
                        binding.canvasView.invalidate();
                    });
                }

                // Selection on Card Tap
                if (card != null) {
                    card.setOnClickListener(v -> {
                        int currentIndex = project.getLayers().size() - 1 - holder.getBindingAdapterPosition();
                        if (currentIndex >= 0 && currentIndex < project.getLayers().size()) {
                            project.setSelectedIndex(currentIndex);
                            binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
                            updateUIForActiveLayer(project.getSelectedLayer());
                            binding.canvasView.invalidate();
                        }
                    });
                }
            }

            @Override
            public int getItemCount() {
                return project != null ? project.getLayers().size() : 0;
            }
        };

        rv.setAdapter(adapter);

        // Attach ItemTouchHelper for drag reordering on long-press / grab handle
        final ItemTouchHelper[] touchHelperRef = {null};
        ItemTouchHelper itemTouchHelper =
                new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                        ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder source,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = source.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();
                if (fromPos == toPos || fromPos < 0 || toPos < 0) return false;

                int fromIndex = project.getLayers().size() - 1 - fromPos;
                int toIndex = project.getLayers().size() - 1 - toPos;

                if (fromIndex >= 0 && fromIndex < project.getLayers().size() &&
                    toIndex >= 0 && toIndex < project.getLayers().size()) {
                    CanvasLayer moved = project.getLayers().remove(fromIndex);
                    project.getLayers().add(toIndex, moved);

                    if (project.getSelectedIndex() == fromIndex) {
                        project.setSelectedIndex(toIndex);
                    } else if (project.getSelectedIndex() > fromIndex && project.getSelectedIndex() <= toIndex) {
                        project.setSelectedIndex(project.getSelectedIndex() - 1);
                    } else if (project.getSelectedIndex() < fromIndex && project.getSelectedIndex() >= toIndex) {
                        project.setSelectedIndex(project.getSelectedIndex() + 1);
                    }

                    adapter.notifyItemMoved(fromPos, toPos);
                    binding.canvasView.invalidate();
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                project.saveSnapshot();
                adapter.notifyDataSetChanged();
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true; // Long-press on the whole item still works
            }
        });

        touchHelperRef[0] = itemTouchHelper;
        itemTouchHelper.attachToRecyclerView(rv);

        // Wire drag handle to start drag immediately on touch
        rv.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                ImageView dragHandle = view.findViewById(R.id.ivOverviewReorder);
                if (dragHandle != null) {
                    dragHandle.setOnTouchListener((v, ev) -> {
                        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                            RecyclerView.ViewHolder vh = rv.getChildViewHolder(view);
                            if (vh != null && touchHelperRef[0] != null) {
                                touchHelperRef[0].startDrag(vh);
                            }
                            return true;
                        }
                        return false;
                    });
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {}
        });
    }


    // -------------------------------------------------------------
    // 9. RIGHT-SIDE CANVA TOOLS (Zoom, Grid, Layers, Fit)
    // -------------------------------------------------------------
    private void setupRightCanvasTools() {
        View btnShape = findViewById(R.id.btnRightShapeAspect);
        if (btnShape != null) {
            btnShape.setOnClickListener(v -> {
                isRightToolsVisible = !isRightToolsVisible;
                hideShowRightCntr(isRightToolsVisible);
            });
        }

        // 2. Grid Toggle
        View btnGrid = findViewById(R.id.btnRightGridToggle);
        ImageView ivGrid = findViewById(R.id.ivGridIcon);
        if (btnGrid != null) {
            btnGrid.setOnClickListener(v -> {
                binding.canvasView.toggleGridLines();
                boolean on = binding.canvasView.isGridLinesEnabled();
                if (ivGrid != null) {
                    ivGrid.setColorFilter(on ? 0xFF00E5BC : 0xFF94A3B8);
                }
            });
        }

        // 3. Layers Quick Sheet Toggle
        View btnLayers = findViewById(R.id.btnRightLayersToggle);
        if (btnLayers != null) {
            btnLayers.setOnClickListener(v -> showLayersListBottomSheet());
        }

        // 4. Fit Canvas
        View btnFit = findViewById(R.id.btnRightFitCanvas);
        if (btnFit != null) {
            btnFit.setOnClickListener(v -> {
                binding.canvasView.resetViewport();
                TextView tvZoom = findViewById(R.id.tvZoomPercent);
                if (tvZoom != null) tvZoom.setText("100%");
            });
        }

        // 5. Pan / Hand Navigation Tool Toggle
        View btnPan = findViewById(R.id.btnRightPanToggle);
        ImageView ivPan = findViewById(R.id.ivPanIcon);
        if (btnPan != null) {
            btnPan.setOnClickListener(v -> {
                binding.canvasView.togglePanMode();
                boolean active = binding.canvasView.isPanModeActive();
                if (ivPan != null) {
                    ivPan.setColorFilter(active ? 0xFF00E5BC : 0xFF94A3B8);
                }
            });
        }

        // 6. Zoom Widget (+ / 100% / -)
        View btnZoomIn = findViewById(R.id.btnZoomIn);
        View btnZoomOut = findViewById(R.id.btnZoomOut);
        TextView tvZoom = findViewById(R.id.tvZoomPercent);

        if (btnZoomIn != null) {
            btnZoomIn.setOnClickListener(v -> {
                binding.canvasView.zoomIn();
                if (tvZoom != null) tvZoom.setText(binding.canvasView.getZoomPercent() + "%");
            });
        }

        if (btnZoomOut != null) {
            btnZoomOut.setOnClickListener(v -> {
                binding.canvasView.zoomOut();
                if (tvZoom != null) tvZoom.setText(binding.canvasView.getZoomPercent() + "%");
            });
        }

        if (tvZoom != null) {
            tvZoom.setOnClickListener(v -> {
                binding.canvasView.resetViewport();
                tvZoom.setText("100%");
            });
        }
    }

    private void hideShowRightCntr(Boolean show){
        View BtnGrid = findViewById(R.id.btnRightGridToggle);
        View BtnLayer = findViewById(R.id.btnRightLayersToggle);
        View BtnFit = findViewById(R.id.btnRightFitCanvas);
        View BtnPan = findViewById(R.id.btnRightPanToggle);
        View CardZoom = findViewById(R.id.cardZoomWidget);
        ImageView BtnControl = findViewById(R.id.rightLayoutToggle);

        if (show) {
            BtnControl.setImageResource(R.drawable.ic_pe_expand_arrow);
            View[] vieew = {BtnGrid, BtnLayer, BtnFit, BtnPan, CardZoom};
            for (int i = 0; i < vieew.length; i++) {
                final View ViewsGr = vieew[i];
                ViewsGr.setVisibility(View.VISIBLE);
                ViewsGr.animate()
                        .setStartDelay(i * 200)
                        .setInterpolator(new OvershootInterpolator(4f))
                        .alpha(1f)
                        .translationX(0f);

            }
        } else {
            BtnControl.setImageResource(R.drawable.ic_pe_expand_arrow);

            View[] vieew = {CardZoom, BtnPan, BtnFit, BtnLayer, BtnGrid};
            for (int i = 0; i < vieew.length; i++) {
                final View ViewsGr = vieew[i];
                ViewsGr.animate()
                        .setStartDelay(i * 200)
                        .setInterpolator(new OvershootInterpolator(4f))
                        .translationX(20f)
                        .alpha(0f)
                        .withEndAction(new Runnable() {
                                           @Override
                                           public void run() {
                                               ViewsGr.setVisibility(View.GONE);
                                           }
                                       }
                        );

            }
        }
    }

    private void showLayersListBottomSheet() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_layers_stack);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        RecyclerView rv = dialog.findViewById(R.id.rvDialogLayers);
        TextView tvCount = dialog.findViewById(R.id.tvDialogLayersCount);
        ImageButton btnClose = dialog.findViewById(R.id.btnCloseLayersDialog);

        if (tvCount != null) tvCount.setText(project.getLayers().size() + " Layers");
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            CanvasLayerSidebarAdapter adapter = new CanvasLayerSidebarAdapter(this, new CanvasLayerSidebarAdapter.OnLayerSidebarListener() {
                @Override
                public void onLayerSelected(int index) {
                    project.setSelectedIndex(index);
                    binding.canvasView.invalidate();
                    updateUIForActiveLayer(project.getSelectedLayer());
                    dialog.dismiss();
                }

                @Override
                public void onLayerVisibilityToggle(int index) {
                    if (index >= 0 && index < project.getLayers().size()) {
                        CanvasLayer l = project.getLayers().get(index);
                        l.setVisible(!l.isVisible());
                        binding.canvasView.invalidate();
                        if (index == project.getSelectedIndex()) {
                            updateUIForActiveLayer(l);
                        }
                    }
                }
            });
            adapter.setLayers(project.getLayers(), project.getSelectedIndex());
            rv.setAdapter(adapter);
        }

        dialog.show();
    }

    // -------------------------------------------------------------
    // 10. MOVE & TRANSFORM CONTROL PANEL (With Circular Dial, Scrub Ruler, Z-Order)
    // -------------------------------------------------------------
    private boolean isScaleAspectLinked = true;

    private void setupMoveTransformPanel() {
        View panel = binding.flipperBottomPanels.getChildAt(PANEL_MOVE_TRANSFORM);
        if (panel == null) return;

        // Back buttons
        View.OnClickListener backClick = v -> binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
        panel.findViewById(R.id.btnBackFromTransform).setOnClickListener(backClick);

        // Mode cards
        MaterialCardView cardPos = panel.findViewById(R.id.cardModePosition);
        MaterialCardView cardRot = panel.findViewById(R.id.cardModeRotate);
        MaterialCardView cardScale = panel.findViewById(R.id.cardModeScale);
        MaterialCardView cardSkew = panel.findViewById(R.id.cardModeSkew);

        ImageView ivPos = panel.findViewById(R.id.ivModePosition);
        ImageView ivRot = panel.findViewById(R.id.ivModeRotate);
        ImageView ivScale = panel.findViewById(R.id.ivModeScale);
        ImageView ivSkew = panel.findViewById(R.id.ivModeSkew);

        MaterialCardView[] modeCards = {cardPos, cardRot, cardScale, cardSkew};
        ImageView[] modeIcons = {ivPos, ivRot, ivScale, ivSkew};
        TransformMode[] modes = {TransformMode.POSITION, TransformMode.ROTATE, TransformMode.SCALE, TransformMode.SKEW};

        // Contextual mode containers
        View containerPos = panel.findViewById(R.id.containerModePosition);
        View containerRot = panel.findViewById(R.id.containerModeRotation);
        View containerScale = panel.findViewById(R.id.containerModeScale);
        View containerSkew = panel.findViewById(R.id.containerModeSkew);

        CircularDialView circularDial = panel.findViewById(R.id.circularDialRotation);
        ScrubRulerView rulerScale = panel.findViewById(R.id.rulerScale);
        ScrubRulerView rulerSkew = panel.findViewById(R.id.rulerSkew);

        // Center / Reset Position Button
        View btnCenter = panel.findViewById(R.id.btnTransformCenter);
        if (btnCenter != null) {
            btnCenter.setOnClickListener(v -> {
                CanvasLayer layer = project.getSelectedLayer();
                if (layer != null) {
                    layer.setX(project.getCanvasWidth() / 2f);
                    layer.setY(project.getCanvasHeight() / 2f);
                    binding.canvasView.invalidate();
                    updateTransformCoordinatesUI();
                }
            });
        }

        // Link Aspect Ratio Toggle & Scale Pills
        MaterialCardView btnLinkAspect = panel.findViewById(R.id.btnToggleLinkAspect);
        ImageView ivLinkIcon = panel.findViewById(R.id.ivLinkAspectIcon);
        View pillScaleWidth = panel.findViewById(R.id.layoutPillScaleWidth);
        View pillScaleHeight = panel.findViewById(R.id.layoutPillScaleHeight);

        Runnable updateScalePillsUI = () -> {
            if (ivLinkIcon != null) {
                ivLinkIcon.setColorFilter(isScaleAspectLinked ? 0xFF00E5BC : 0xFF64748B);
            }
            TextView tvWVal = panel.findViewById(R.id.tvScaleWidthVal);
            TextView tvHVal = panel.findViewById(R.id.tvScaleHeightVal);
            if (tvWVal != null && tvHVal != null) {
                if (isScaleAspectLinked || activeScaleAxis == ScaleAxis.BOTH) {
                    tvWVal.setTextColor(0xFF00E5BC);
                    tvHVal.setTextColor(0xFF00E5BC);
                } else if (activeScaleAxis == ScaleAxis.WIDTH) {
                    tvWVal.setTextColor(0xFF00E5BC);
                    tvHVal.setTextColor(0xFFFFFFFF);
                } else {
                    tvWVal.setTextColor(0xFFFFFFFF);
                    tvHVal.setTextColor(0xFF00E5BC);
                }
            }
        };

        if (btnLinkAspect != null) {
            btnLinkAspect.setOnClickListener(v -> {
                isScaleAspectLinked = !isScaleAspectLinked;
                if (isScaleAspectLinked) {
                    activeScaleAxis = ScaleAxis.BOTH;
                } else {
                    activeScaleAxis = ScaleAxis.WIDTH;
                }
                updateScalePillsUI.run();
            });
        }

        if (pillScaleWidth != null) {
            pillScaleWidth.setOnClickListener(v -> {
                if (!isScaleAspectLinked) {
                    activeScaleAxis = ScaleAxis.WIDTH;
                    updateScalePillsUI.run();
                }
            });
        }

        if (pillScaleHeight != null) {
            pillScaleHeight.setOnClickListener(v -> {
                if (!isScaleAspectLinked) {
                    activeScaleAxis = ScaleAxis.HEIGHT;
                    updateScalePillsUI.run();
                }
            });
        }

        // Skew Pills (X Skew / Y Skew)
        View pillSkewX = panel.findViewById(R.id.layoutPillSkewX);
        View pillSkewY = panel.findViewById(R.id.layoutPillSkewY);

        Runnable updateSkewPillsUI = () -> {
            TextView tvSXVal = panel.findViewById(R.id.tvSkewXVal);
            TextView tvSYVal = panel.findViewById(R.id.tvSkewYVal);
            if (tvSXVal != null && tvSYVal != null) {
                if (activeSkewAxis == SkewAxis.X) {
                    tvSXVal.setTextColor(0xFF00E5BC);
                    tvSYVal.setTextColor(0xFFFFFFFF);
                } else {
                    tvSXVal.setTextColor(0xFFFFFFFF);
                    tvSYVal.setTextColor(0xFF00E5BC);
                }
            }
        };

        if (pillSkewX != null) {
            pillSkewX.setOnClickListener(v -> {
                activeSkewAxis = SkewAxis.X;
                updateSkewPillsUI.run();
            });
        }

        if (pillSkewY != null) {
            pillSkewY.setOnClickListener(v -> {
                activeSkewAxis = SkewAxis.Y;
                updateSkewPillsUI.run();
            });
        }

        // Position Mode: X, Y, Z Pills
        View pillX = panel.findViewById(R.id.layoutPillX);
        View pillY = panel.findViewById(R.id.layoutPillY);
        View pillZ = panel.findViewById(R.id.layoutPillZ);
        TextView tvPadHint = panel.findViewById(R.id.tvTransformHint);

        Runnable updatePositionPillsUI = () -> {
            TextView tvXVal = panel.findViewById(R.id.tvCoordVal1);
            TextView tvYVal = panel.findViewById(R.id.tvCoordVal2);
            TextView tvZVal = panel.findViewById(R.id.tvCoordVal3);
            if (tvXVal != null && tvYVal != null && tvZVal != null) {
                if (activePositionAxis == PositionAxis.XY) {
                    tvXVal.setTextColor(0xFF00E5BC);
                    tvYVal.setTextColor(0xFF00E5BC);
                    tvZVal.setTextColor(0xFFFFFFFF);
                    if (tvPadHint != null) tvPadHint.setText("Swipe here to move layer");
                } else {
                    tvXVal.setTextColor(0xFFFFFFFF);
                    tvYVal.setTextColor(0xFFFFFFFF);
                    tvZVal.setTextColor(0xFF00E5BC);
                    if (tvPadHint != null) tvPadHint.setText("Swipe up / down for layer depth");
                }
            }
        };

        if (pillX != null) {
            pillX.setOnClickListener(v -> {
                activePositionAxis = PositionAxis.XY;
                updatePositionPillsUI.run();
            });
        }

        if (pillY != null) {
            pillY.setOnClickListener(v -> {
                activePositionAxis = PositionAxis.XY;
                updatePositionPillsUI.run();
            });
        }

        if (pillZ != null) {
            pillZ.setOnClickListener(v -> {
                activePositionAxis = (activePositionAxis == PositionAxis.Z) ? PositionAxis.XY : PositionAxis.Z;
                updatePositionPillsUI.run();
            });
        }

        Runnable updateModeSelectorUI = () -> {
            for (int i = 0; i < modeCards.length; i++) {
                if (modes[i] == currentTransformMode) {
                    modeCards[i].setCardBackgroundColor(0xFF00E5BC);
                    modeCards[i].setStrokeColor(0xFF00E5BC);
                    modeIcons[i].setColorFilter(0xFF00382B);
                } else {
                    modeCards[i].setCardBackgroundColor(0xFF1E273C);
                    modeCards[i].setStrokeColor(0xFF2C3852);
                    modeIcons[i].setColorFilter(0xFF94A3B8);
                }
            }

            if (containerPos != null) containerPos.setVisibility(currentTransformMode == TransformMode.POSITION ? View.VISIBLE : View.GONE);
            if (containerRot != null) containerRot.setVisibility(currentTransformMode == TransformMode.ROTATE ? View.VISIBLE : View.GONE);
            if (containerScale != null) containerScale.setVisibility(currentTransformMode == TransformMode.SCALE ? View.VISIBLE : View.GONE);
            if (containerSkew != null) containerSkew.setVisibility(currentTransformMode == TransformMode.SKEW ? View.VISIBLE : View.GONE);

            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null) {
                if (circularDial != null) {
                    circularDial.setAngle(layer.getRotation());
                }
                if (rulerScale != null) {
                    float curVal = (layer instanceof TextLayer) ? ((TextLayer) layer).getTextSize() : layer.getWidth();
                    rulerScale.setBounds(layer instanceof TextLayer ? 8f : 20f, layer instanceof TextLayer ? 400f : 3000f, curVal, 0.5f);
                }
                if (rulerSkew != null) {
                    float curSkew = (activeSkewAxis == SkewAxis.X) ? layer.getSkewX() : layer.getSkewY();
                    rulerSkew.setBounds(-85f, 85f, curSkew, 0.3f);
                }
            }

            updatePositionPillsUI.run();
            updateScalePillsUI.run();
            updateSkewPillsUI.run();
            updateTransformCoordinatesUI();
        };

        for (int i = 0; i < modeCards.length; i++) {
            final int idx = i;
            modeCards[i].setOnClickListener(v -> {
                currentTransformMode = modes[idx];
                updateModeSelectorUI.run();
            });
        }

        // Mode 2: Rotation Circular Dial listener
        if (circularDial != null) {
            circularDial.setOnAngleChangeListener(angle -> {
                CanvasLayer layer = project.getSelectedLayer();
                if (layer != null) {
                    layer.setRotation(angle);
                    binding.canvasView.invalidate();
                    updateTransformCoordinatesUI();
                }
            });
        }

        // Mode 3: Scale Scrub Ruler listener
        if (rulerScale != null) {
            rulerScale.setOnScrubListener(delta -> {
                CanvasLayer layer = project.getSelectedLayer();
                if (layer != null) {
                    float factor = 1.0f + (delta * 0.005f); // Drag right increases scale, drag left decreases scale
                    if (layer instanceof TextLayer) {
                        TextLayer tl = (TextLayer) layer;
                        float newSize = Math.max(8f, Math.min(400f, tl.getTextSize() * factor));
                        tl.setTextSize(newSize);
                        rulerScale.setCurrentValue(newSize);
                    } else {
                        if (isScaleAspectLinked || activeScaleAxis == ScaleAxis.BOTH) {
                            float newW = Math.max(20f, Math.min(3000f, layer.getWidth() * factor));
                            float newH = Math.max(20f, Math.min(3000f, layer.getHeight() * factor));
                            layer.setWidth(newW);
                            layer.setHeight(newH);
                            rulerScale.setCurrentValue(newW);
                        } else if (activeScaleAxis == ScaleAxis.WIDTH) {
                            float newW = Math.max(20f, Math.min(3000f, layer.getWidth() * factor));
                            layer.setWidth(newW);
                            rulerScale.setCurrentValue(newW);
                        } else if (activeScaleAxis == ScaleAxis.HEIGHT) {
                            float newH = Math.max(20f, Math.min(3000f, layer.getHeight() * factor));
                            layer.setHeight(newH);
                            rulerScale.setCurrentValue(newH);
                        }
                    }
                    binding.canvasView.invalidate();
                    updateTransformCoordinatesUI();
                }
            });
        }

        // Mode 4: Skew Scrub Ruler listener
        if (rulerSkew != null) {
            rulerSkew.setOnScrubListener(delta -> {
                CanvasLayer layer = project.getSelectedLayer();
                if (layer != null) {
                    if (activeSkewAxis == SkewAxis.X) {
                        float newSkew = Math.max(-85f, Math.min(85f, layer.getSkewX() + (delta * 0.3f)));
                        layer.setSkewX(newSkew);
                        rulerSkew.setCurrentValue(newSkew);
                    } else {
                        float newSkew = Math.max(-85f, Math.min(85f, layer.getSkewY() + (delta * 0.3f)));
                        layer.setSkewY(newSkew);
                        rulerSkew.setCurrentValue(newSkew);
                    }
                    binding.canvasView.invalidate();
                    updateTransformCoordinatesUI();
                }
            });
        }

        // Mode 1: Large Interactive Touchpad Surface with Z-Order swipe and magnetic snapping
        View pad = panel.findViewById(R.id.viewTransformPad);
        if (pad != null) {
            pad.setOnTouchListener(new View.OnTouchListener() {
                private float lastTouchX, lastTouchY;
                private float accumulatedZDy = 0f;
                private boolean hasSavedSnapshotForPad = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    CanvasLayer layer = project.getSelectedLayer();
                    if (layer == null) return false;

                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            lastTouchX = event.getX();
                            lastTouchY = event.getY();
                            accumulatedZDy = 0f;
                            hasSavedSnapshotForPad = false;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getX() - lastTouchX;
                            float dy = event.getY() - lastTouchY;
                            lastTouchX = event.getX();
                            lastTouchY = event.getY();

                            if (!hasSavedSnapshotForPad && (Math.abs(dx) > 1f || Math.abs(dy) > 1f)) {
                                project.saveSnapshot();
                                hasSavedSnapshotForPad = true;
                                updateCanvasToolsState();
                            }

                            if (activePositionAxis == PositionAxis.Z) {
                                accumulatedZDy += dy;
                                float threshold = 32f * getResources().getDisplayMetrics().density;
                                if (accumulatedZDy <= -threshold) {
                                    // Swiped Up -> Bring layer forward (up)
                                    project.moveLayerUp(project.getSelectedIndex());
                                    accumulatedZDy = 0f;
                                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                    binding.canvasView.invalidate();
                                    updateTransformCoordinatesUI();
                                    updateCanvasToolsState();
                                } else if (accumulatedZDy >= threshold) {
                                    // Swiped Down -> Send layer backward (down)
                                    project.moveLayerDown(project.getSelectedIndex());
                                    accumulatedZDy = 0f;
                                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                    binding.canvasView.invalidate();
                                    updateTransformCoordinatesUI();
                                    updateCanvasToolsState();
                                }
                            } else {
                                float rawX = layer.getX() + dx;
                                float rawY = layer.getY() + dy;
                                PointF snapped = binding.canvasView.applyMagneticSnapping(layer, rawX, rawY);
                                layer.setX(snapped.x);
                                layer.setY(snapped.y);
                                binding.canvasView.invalidate();
                                updateTransformCoordinatesUI();
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            accumulatedZDy = 0f;
                            hasSavedSnapshotForPad = false;
                            binding.canvasView.clearMagneticSnapLines();
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            return true;
                    }
                    return false;
                }
            });
        }

        updateModeSelectorUI.run();
    }

    private void openMoveTransformPanel() {
        binding.flipperBottomPanels.setDisplayedChild(PANEL_MOVE_TRANSFORM);
        setupMoveTransformPanel();
        updateTransformCoordinatesUI();
    }

    private void updateTransformCoordinatesUI() {
        View panel = binding.flipperBottomPanels.getChildAt(PANEL_MOVE_TRANSFORM);
        if (panel == null) return;

        TextView tvVal1 = panel.findViewById(R.id.tvCoordVal1);
        TextView tvVal2 = panel.findViewById(R.id.tvCoordVal2);
        TextView tvVal3 = panel.findViewById(R.id.tvCoordVal3);
        TextView tvScaleW = panel.findViewById(R.id.tvScaleWidthVal);
        TextView tvScaleH = panel.findViewById(R.id.tvScaleHeightVal);
        TextView tvSkewX = panel.findViewById(R.id.tvSkewXVal);
        TextView tvSkewY = panel.findViewById(R.id.tvSkewYVal);

        CanvasLayer layer = project != null ? project.getSelectedLayer() : null;
        if (layer == null) {
            if (tvVal1 != null) tvVal1.setText("0.00");
            if (tvVal2 != null) tvVal2.setText("0.00");
            if (tvVal3 != null) tvVal3.setText("0.00");
            return;
        }

        // Position coordinates
        if (tvVal1 != null) tvVal1.setText(String.format(Locale.US, "%.2f", layer.getX()));
        if (tvVal2 != null) tvVal2.setText(String.format(Locale.US, "%.2f", layer.getY()));
        if (tvVal3 != null) tvVal3.setText(String.format(Locale.US, "Z:%d", project.getSelectedIndex() + 1));

        // Scale coordinates
        if (tvScaleW != null) tvScaleW.setText(String.format(Locale.US, "%.1f", layer.getWidth()));
        if (tvScaleH != null) tvScaleH.setText(String.format(Locale.US, "%.1f", layer.getHeight()));

        // Skew coordinates
        if (tvSkewX != null) tvSkewX.setText(String.format(Locale.US, "%.1f°", layer.getSkewX()));
        if (tvSkewY != null) tvSkewY.setText(String.format(Locale.US, "%.1f°", layer.getSkewY()));
    }

    // -------------------------------------------------------------
    // 11. BOTTOM PANEL EXPAND & COLLAPSE (SWIPE UP / DOWN)
    // -------------------------------------------------------------
    private boolean isBottomPanelExpanded = false;

    private void setupBottomPanelExpandCollapse() {
        View handle = findViewById(R.id.handleBottomPanel);
        if (handle == null || binding.layoutBottomContainer == null) return;

        android.view.GestureDetector gestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dy = e2.getY() - e1.getY();
                if (dy < -30f && !isBottomPanelExpanded) {
                    setBottomPanelExpanded(true);
                    return true;
                } else if (dy > 30f && isBottomPanelExpanded) {
                    setBottomPanelExpanded(false);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                setBottomPanelExpanded(!isBottomPanelExpanded);
                return true;
            }
        });

        handle.setOnTouchListener((v, event) -> {
            boolean handled = gestureDetector.onTouchEvent(event);
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return handled || true;
        });
    }

    private void setBottomPanelExpanded(boolean expand) {
        isBottomPanelExpanded = expand;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float density = getResources().getDisplayMetrics().density;
        int expandedHeight = (int) (screenHeight * 0.58f);

        ViewGroup.LayoutParams lp = binding.layoutBottomContainer.getLayoutParams();
        if (expand) {
            lp.height = expandedHeight;
        } else {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        binding.layoutBottomContainer.setLayoutParams(lp);

        // Dynamically adjust internal scroll lists so they show more items ONLY when expanded
        View rvLayers = binding.getRoot().findViewById(R.id.rvOverviewLayers);
        if (rvLayers != null) {
            ViewGroup.LayoutParams rlp = rvLayers.getLayoutParams();
            rlp.height = expand ? Math.max((int) (175 * density), expandedHeight - (int) (90 * density)) : (int) (175 * density);
            rvLayers.setLayoutParams(rlp);
        }

        View centerGridContainer = binding.getRoot().findViewById(R.id.layoutAddElementCenterContainer);
        if (centerGridContainer != null) {
            ViewGroup.LayoutParams clp = centerGridContainer.getLayoutParams();
            clp.height = expand ? Math.max((int) (215 * density), expandedHeight - (int) (80 * density)) : (int) (215 * density);
            centerGridContainer.setLayoutParams(clp);
        }

        View rvFonts = binding.getRoot().findViewById(R.id.rvFontsList);
        if (rvFonts != null) {
            ViewGroup.LayoutParams flp = rvFonts.getLayoutParams();
            flp.height = expand ? Math.max((int) (180 * density), expandedHeight - (int) (200 * density)) : (int) (180 * density);
            rvFonts.setLayoutParams(flp);
        }

        View panelEditText = binding.getRoot().findViewById(R.id.panelEditText);
        if (panelEditText != null) {
            ViewGroup.LayoutParams pelp = panelEditText.getLayoutParams();
            pelp.height = expand ? expandedHeight : (int) (380 * density);
            panelEditText.setLayoutParams(pelp);
        }

        binding.flipperBottomPanels.requestLayout();

        // Reposition canvas smoothly below TopBar
        binding.canvasView.post(() -> {
            binding.canvasView.resetViewport();
        });
    }
}
