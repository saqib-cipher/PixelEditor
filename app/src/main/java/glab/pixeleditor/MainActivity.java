package glab.pixeleditor;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import glab.pixeleditor.databinding.ActivityMainBinding;
import glab.pixeleditor.effect.EffectControlHelper;
import glab.pixeleditor.effect.EffectDefinition;
import glab.pixeleditor.effect.EffectHelper;
import glab.pixeleditor.effect.EffectParam;
import glab.pixeleditor.model.CanvasLayer;
import glab.pixeleditor.model.EditorProject;
import glab.pixeleditor.model.PhotoLayer;
import glab.pixeleditor.model.ShapeLayer;
import glab.pixeleditor.model.TextLayer;
import glab.pixeleditor.view.PixelCanvasView;

public class MainActivity extends AppCompatActivity implements PixelCanvasView.OnLayerSelectedListener {

    private ActivityMainBinding binding;
    private EditorProject project;
    private ActivityResultLauncher<String> photoPickerLauncher;

    // ViewFlipper Index constants
    private static final int PANEL_LAYER_MENU = 0;
    private static final int PANEL_EDIT_SHAPE = 1;
    private static final int PANEL_BORDER_SHADOW = 2;
    private static final int PANEL_COLOR_FILL = 3;
    private static final int PANEL_PHOTO_ADJUST = 4;
    private static final int SHEET_ADD_ELEMENT = 5;
    private static final int PANEL_EFFECT_CONTROLS = 6;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Enable Edge-to-Edge display
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 2. Fix Window Insets for immersive edge-to-edge layout
        setupWindowInsets();

        initProject();
        initPhotoPicker();
        setupTopBar();
        setupMidToolbar();
        setupLayerIndicatorBar();
        setupLayerMenu();
        setupSubpanels();
        setupAddElementSheet();
        setupEffectBrowser();
        setupEffectControlsPanel();

        // Load all XML effects asynchronously from assets/effects
        loadEffectsAsync();

        updateUIForActiveLayer(project.getSelectedLayer());
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

            float density = getResources().getDisplayMetrics().density;
            int padHoriz = (int) (8 * density);

            // Pad Top Bar for Status Bar & camera notch
            binding.topBar.setPadding(
                    insets.left + padHoriz,
                    insets.top,
                    insets.right + padHoriz,
                    0
            );

            // Pad Bottom Panels ViewFlipper for Navigation Bar
            binding.flipperBottomPanels.setPadding(
                    insets.left,
                    0,
                    insets.right,
                    insets.bottom
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
                        insets.bottom
                );
            }

            return windowInsets;
        });
    }

    private void initProject() {
        project = new EditorProject();
        project.setTitle("New Project 1");
        project.setCanvasWidth(1080);
        project.setCanvasHeight(1350);
        project.setBackgroundColor(0xFFD8DCE3);

        // Initial default layer inspired by screenshot: Rounded Rectangle
        ShapeLayer defaultShape = new ShapeLayer("Rounded Rectangle 1", 540f, 675f, 320f, 320f);
        defaultShape.setFillColor(0xFF7A4B58);
        defaultShape.setCornerRadius(30f);
        defaultShape.setHasStroke(false);
        project.addLayer(defaultShape);

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
                Toast.makeText(this, "Photo added to canvas", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------------------------------------------------
    // 1. TOP BAR SETUP
    // -------------------------------------------------------------
    private void setupTopBar() {
        binding.btnTopBack.setOnClickListener(v -> showProjectRenameDialog());
        binding.tvProjectTitle.setOnClickListener(v -> showProjectRenameDialog());

        // Aspect Ratio Switcher
        binding.chipAspectRatio.setOnClickListener(v -> {
            currentAspectIndex = (currentAspectIndex + 1) % aspectLabels.length;
            int w = aspectDimensions[currentAspectIndex][0];
            int h = aspectDimensions[currentAspectIndex][1];
            project.setCanvasWidth(w);
            project.setCanvasHeight(h);
            binding.chipAspectRatio.setText(aspectLabels[currentAspectIndex]);
            binding.tvProjectDimensions.setText(w + " × " + h + " • " + aspectLabels[currentAspectIndex]);
            binding.canvasView.resetViewport();
            Toast.makeText(this, "Canvas set to " + aspectLabels[currentAspectIndex] + " (" + w + "x" + h + ")", Toast.LENGTH_SHORT).show();
        });

        // Settings (Canvas background)
        binding.btnTopSettings.setOnClickListener(v -> showCanvasSettingsDialog());

        // Export
        binding.btnTopExport.setOnClickListener(v -> showExportDialog());

        // Canvas Zoom Reset
        binding.cardZoomReset.setOnClickListener(v -> {
            binding.canvasView.resetViewport();
            Toast.makeText(this, "Viewport reset", Toast.LENGTH_SHORT).show();
        });
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
        String[] colors = {"Light Grey (#D8DCE3)", "Pure White (#FFFFFF)", "Studio Dark (#161D2D)", "Pitch Black (#000000)"};
        int[] colorVals = {0xFFD8DCE3, 0xFFFFFFFF, 0xFF161D2D, 0xFF000000};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Canvas Background")
                .setItems(colors, (dialog, which) -> {
                    project.setBackgroundColor(colorVals[which]);
                    binding.canvasView.invalidate();
                })
                .show();
    }

    private void showExportDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.export_dialog_title)
                .setItems(new String[]{"Save PNG to Gallery (Lossless)", "Save JPEG to Gallery", "Share Artwork"}, (dialog, which) -> {
                    Bitmap bmp = binding.canvasView.exportArtboardBitmap();
                    if (bmp == null) return;

                    if (which == 0) {
                        saveImageToGallery(bmp, Bitmap.CompressFormat.PNG, "image/png");
                    } else if (which == 1) {
                        saveImageToGallery(bmp, Bitmap.CompressFormat.JPEG, "image/jpeg");
                    } else {
                        shareArtworkBitmap(bmp);
                    }
                })
                .show();
    }

    private void saveImageToGallery(Bitmap bitmap, Bitmap.CompressFormat format, String mimeType) {
        String filename = "PixelEditor_" + System.currentTimeMillis() + (format == Bitmap.CompressFormat.PNG ? ".png" : ".jpg");
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelEditor");
        }

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(format, 100, out);
                Toast.makeText(this, R.string.export_success, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to export: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void shareArtworkBitmap(Bitmap bitmap) {
        try {
            String path = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "PixelEditor Artwork", null);
            Uri uri = Uri.parse(path);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/*");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(Intent.createChooser(share, "Share Artwork"));
        } catch (Exception e) {
            Toast.makeText(this, "Failed to share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------------------------------------------------
    // 2. MID TOOLBAR SETUP
    // -------------------------------------------------------------
    private void setupMidToolbar() {
        binding.btnMidUndo.setOnClickListener(v -> {
            if (project.canUndo()) {
                project.undo();
                binding.canvasView.invalidate();
                updateUIForActiveLayer(project.getSelectedLayer());
                Toast.makeText(this, "Undo", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnMidRedo.setOnClickListener(v -> {
            if (project.canRedo()) {
                project.redo();
                binding.canvasView.invalidate();
                updateUIForActiveLayer(project.getSelectedLayer());
                Toast.makeText(this, "Redo", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnFabAddElement.setOnClickListener(v -> {
            binding.flipperBottomPanels.setDisplayedChild(SHEET_ADD_ELEMENT);
        });

        binding.btnMidCompare.setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null) {
                layer.setVisible(!layer.isVisible());
                binding.canvasView.invalidate();
                updateUIForActiveLayer(layer);
            }
        });

        binding.btnMidFit.setOnClickListener(v -> {
            binding.canvasView.resetViewport();
            Toast.makeText(this, "Fit to canvas", Toast.LENGTH_SHORT).show();
        });
    }

    // -------------------------------------------------------------
    // 3. LAYER INDICATOR BAR
    // -------------------------------------------------------------
    private void setupLayerIndicatorBar() {
        binding.btnLayerVisibility.setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null) {
                layer.setVisible(!layer.isVisible());
                binding.canvasView.invalidate();
                updateUIForActiveLayer(layer);
            }
        });

        binding.chipActiveLayerContainer.setOnClickListener(v -> {
            binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
        });

        binding.btnLayerBarDuplicate.setOnClickListener(v -> {
            int idx = project.getSelectedIndex();
            if (idx >= 0) {
                project.duplicateLayer(idx);
                binding.canvasView.invalidate();
                updateUIForActiveLayer(project.getSelectedLayer());
                Toast.makeText(this, "Layer duplicated", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnLayerBarDelete.setOnClickListener(v -> {
            int idx = project.getSelectedIndex();
            if (idx >= 0) {
                project.removeLayer(idx);
                binding.canvasView.invalidate();
                updateUIForActiveLayer(project.getSelectedLayer());
                Toast.makeText(this, "Layer deleted", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // -------------------------------------------------------------
    // 4. LAYER MENU (Image 3)
    // -------------------------------------------------------------
    private void setupLayerMenu() {
        View menuView = binding.flipperBottomPanels.getChildAt(PANEL_LAYER_MENU);
        menuView.findViewById(R.id.btnQuickDuplicate).setOnClickListener(v -> binding.btnLayerBarDuplicate.performClick());
        menuView.findViewById(R.id.btnQuickDelete).setOnClickListener(v -> binding.btnLayerBarDelete.performClick());
        menuView.findViewById(R.id.btnQuickForward).setOnClickListener(v -> {
            project.moveLayerUp(project.getSelectedIndex());
            binding.canvasView.invalidate();
            Toast.makeText(this, "Brought forward", Toast.LENGTH_SHORT).show();
        });
        menuView.findViewById(R.id.btnQuickBackward).setOnClickListener(v -> {
            project.moveLayerDown(project.getSelectedIndex());
            binding.canvasView.invalidate();
            Toast.makeText(this, "Sent backward", Toast.LENGTH_SHORT).show();
        });
        menuView.findViewById(R.id.btnQuickLock).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null) {
                layer.setLocked(!layer.isLocked());
                Toast.makeText(this, layer.isLocked() ? "Layer locked" : "Layer unlocked", Toast.LENGTH_SHORT).show();
            }
        });

        // 7 Grid Actions
        menuView.findViewById(R.id.btnToolColorFill).setOnClickListener(v -> {
            binding.flipperBottomPanels.setDisplayedChild(PANEL_COLOR_FILL);
        });

        menuView.findViewById(R.id.btnToolBorderShadow).setOnClickListener(v -> {
            binding.flipperBottomPanels.setDisplayedChild(PANEL_BORDER_SHADOW);
        });

        menuView.findViewById(R.id.btnToolBlending).setOnClickListener(v -> {
            binding.flipperBottomPanels.setDisplayedChild(PANEL_COLOR_FILL);
        });

        menuView.findViewById(R.id.btnToolTransform).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null) {
                layer.setRotation(layer.getRotation() + 45f);
                binding.canvasView.invalidate();
            }
        });

        menuView.findViewById(R.id.btnToolEditShape).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer instanceof ShapeLayer) {
                binding.flipperBottomPanels.setDisplayedChild(PANEL_EDIT_SHAPE);
            } else if (layer instanceof PhotoLayer) {
                binding.flipperBottomPanels.setDisplayedChild(PANEL_PHOTO_ADJUST);
            } else if (layer instanceof TextLayer) {
                showTextEditDialog((TextLayer) layer);
            }
        });

        menuView.findViewById(R.id.btnToolPresets).setOnClickListener(v -> showPresetsDialog());

        menuView.findViewById(R.id.btnToolEffects).setOnClickListener(v -> {
            binding.containerEffectBrowser.setVisibility(View.VISIBLE);
        });
    }

    private void showTextEditDialog(TextLayer textLayer) {
        EditText input = new EditText(this);
        input.setText(textLayer.getText());
        input.setTextColor(Color.WHITE);
        input.setPadding(40, 30, 40, 30);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit Text")
                .setView(input)
                .setPositiveButton("Update", (dialog, which) -> {
                    String txt = input.getText().toString();
                    textLayer.setText(txt);
                    binding.canvasView.invalidate();
                    updateUIForActiveLayer(textLayer);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPresetsDialog() {
        String[] presets = {"Original / Neutral", "Cyberpunk Mint", "Vintage Warm", "Noir Black & White", "Cinematic Teal"};
        String[] keys = {"NORMAL", "CYBERPUNK", "VINTAGE", "BW", "CINEMATIC"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Aesthetic Presets")
                .setItems(presets, (dialog, which) -> {
                    CanvasLayer layer = project.getSelectedLayer();
                    if (layer instanceof PhotoLayer) {
                        ((PhotoLayer) layer).setFilterPreset(keys[which]);
                    } else if (layer instanceof ShapeLayer) {
                        int[] colors = {0xFF7A4B58, 0xFF00E5BC, 0xFFE07A5F, 0xFF333333, 0xFF118AB2};
                        ((ShapeLayer) layer).setFillColor(colors[which]);
                    }
                    binding.canvasView.invalidate();
                    updateUIForActiveLayer(layer);
                })
                .show();
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
        binding.getRoot().findViewById(R.id.btnCloseAddSheet).setOnClickListener(v ->
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));

        // Subpanel 1: Edit Shape Sliders
        Slider sliderSize = binding.getRoot().findViewById(R.id.sliderSize);
        TextView tvSizeVal = binding.getRoot().findViewById(R.id.tvSizeVal);
        sliderSize.addOnChangeListener((slider, value, fromUser) -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null && fromUser) {
                layer.setWidth(value);
                layer.setHeight(value);
                tvSizeVal.setText(String.valueOf(Math.round(value)));
                binding.canvasView.invalidate();
            }
        });

        Slider sliderRadius = binding.getRoot().findViewById(R.id.sliderRadius);
        TextView tvRadiusVal = binding.getRoot().findViewById(R.id.tvRadiusVal);
        sliderRadius.addOnChangeListener((slider, value, fromUser) -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer instanceof ShapeLayer && fromUser) {
                ((ShapeLayer) layer).setCornerRadius(value);
                tvRadiusVal.setText(String.valueOf(Math.round(value)));
                binding.canvasView.invalidate();
            }
        });

        // Subpanel 2: Border & Shadow
        com.google.android.material.materialswitch.MaterialSwitch switchStroke =
                binding.getRoot().findViewById(R.id.switchStrokeToggle);
        Slider sliderStroke = binding.getRoot().findViewById(R.id.sliderStrokeWidth);
        TextView tvStrokeVal = binding.getRoot().findViewById(R.id.tvStrokeVal);

        switchStroke.setOnCheckedChangeListener((btn, isChecked) -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer instanceof ShapeLayer) {
                ((ShapeLayer) layer).setHasStroke(isChecked);
                binding.canvasView.invalidate();
            }
        });

        sliderStroke.addOnChangeListener((slider, value, fromUser) -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer instanceof ShapeLayer && fromUser) {
                ((ShapeLayer) layer).setStrokeWidth(value);
                ((ShapeLayer) layer).setHasStroke(value > 0);
                tvStrokeVal.setText(String.format("%.1f", value));
                binding.canvasView.invalidate();
            }
        });

        // Stroke color swatches
        LinearLayout strokePresets = binding.getRoot().findViewById(R.id.layoutStrokeColorPresets);
        for (int col : paletteColors) {
            View swatch = new View(this);
            int size = (int) (32 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(8, 0, 8, 0);
            swatch.setLayoutParams(lp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(col);
            gd.setStroke(2, 0x44FFFFFF);
            swatch.setBackground(gd);
            swatch.setOnClickListener(v -> {
                CanvasLayer layer = project.getSelectedLayer();
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setStrokeColor(col);
                    binding.getRoot().findViewById(R.id.viewStrokeColorSwatch).setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(col));
                    binding.canvasView.invalidate();
                }
            });
            strokePresets.addView(swatch);
        }

        // Subpanel 3: Color & Fill
        LinearLayout fillChips = binding.getRoot().findViewById(R.id.layoutFillColorChips);
        for (int col : paletteColors) {
            View swatch = new View(this);
            int size = (int) (36 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(10, 0, 10, 0);
            swatch.setLayoutParams(lp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(col);
            gd.setStroke(3, 0x44FFFFFF);
            swatch.setBackground(gd);
            swatch.setOnClickListener(v -> {
                CanvasLayer layer = project.getSelectedLayer();
                if (layer instanceof ShapeLayer) {
                    ((ShapeLayer) layer).setFillColor(col);
                } else if (layer instanceof TextLayer) {
                    ((TextLayer) layer).setTextColor(col);
                }
                binding.getRoot().findViewById(R.id.viewCurrentFillColorPreview).setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(col));
                binding.viewLayerColorChip.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(col));
                binding.canvasView.invalidate();
            });
            fillChips.addView(swatch);
        }

        Slider sliderOpacity = binding.getRoot().findViewById(R.id.sliderLayerOpacity);
        TextView tvOpacityVal = binding.getRoot().findViewById(R.id.tvOpacityVal);
        sliderOpacity.addOnChangeListener((slider, value, fromUser) -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer != null && fromUser) {
                layer.setOpacity((int) value);
                int pct = Math.round((value / 255f) * 100);
                tvOpacityVal.setText(pct + "%");
                binding.canvasView.invalidate();
            }
        });

        // Subpanel 4: Photo Adjust
        Slider sliderBright = binding.getRoot().findViewById(R.id.sliderBrightness);
        TextView tvBrightVal = binding.getRoot().findViewById(R.id.tvBrightnessVal);
        sliderBright.addOnChangeListener((slider, value, fromUser) -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer instanceof PhotoLayer && fromUser) {
                ((PhotoLayer) layer).setBrightness(value);
                tvBrightVal.setText(String.valueOf(Math.round(value)));
                binding.canvasView.invalidate();
            }
        });

        Slider sliderContrast = binding.getRoot().findViewById(R.id.sliderContrast);
        TextView tvContrastVal = binding.getRoot().findViewById(R.id.tvContrastVal);
        sliderContrast.addOnChangeListener((slider, value, fromUser) -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer instanceof PhotoLayer && fromUser) {
                ((PhotoLayer) layer).setContrast(value);
                tvContrastVal.setText(String.valueOf(Math.round(value)));
                binding.canvasView.invalidate();
            }
        });

        Slider sliderSat = binding.getRoot().findViewById(R.id.sliderSaturation);
        TextView tvSatVal = binding.getRoot().findViewById(R.id.tvSaturationVal);
        sliderSat.addOnChangeListener((slider, value, fromUser) -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer instanceof PhotoLayer && fromUser) {
                ((PhotoLayer) layer).setSaturation(value);
                tvSatVal.setText(String.valueOf(Math.round(value)));
                binding.canvasView.invalidate();
            }
        });

        binding.getRoot().findViewById(R.id.btnResetAdjustments).setOnClickListener(v -> {
            CanvasLayer layer = project.getSelectedLayer();
            if (layer instanceof PhotoLayer) {
                ((PhotoLayer) layer).setBrightness(0);
                ((PhotoLayer) layer).setContrast(0);
                ((PhotoLayer) layer).setSaturation(0);
                sliderBright.setValue(0);
                sliderContrast.setValue(0);
                sliderSat.setValue(0);
                binding.canvasView.invalidate();
            }
        });
    }

    // -------------------------------------------------------------
    // 6. ADD ELEMENT SHEET (Image 1)
    // -------------------------------------------------------------
    private void setupAddElementSheet() {
        GridLayout grid = binding.getRoot().findViewById(R.id.gridShapes);

        int[] shapeDrawables = {
                R.drawable.ic_shape_drop, R.drawable.ic_shape_shield, R.drawable.ic_shape_star,
                R.drawable.ic_shape_triangle, R.drawable.ic_shape_heart, R.drawable.ic_shape_lightning,
                R.drawable.ic_shape_cloud, R.drawable.ic_shape_diamond, R.drawable.ic_shape_circle,
                R.drawable.ic_shape_rounded_rect, R.drawable.ic_tool_shape, R.drawable.ic_pe_element
        };
        ShapeLayer.ShapeType[] shapeTypes = {
                ShapeLayer.ShapeType.DROP, ShapeLayer.ShapeType.SHIELD, ShapeLayer.ShapeType.STAR,
                ShapeLayer.ShapeType.TRIANGLE, ShapeLayer.ShapeType.HEART, ShapeLayer.ShapeType.LIGHTNING,
                ShapeLayer.ShapeType.CLOUD, ShapeLayer.ShapeType.DIAMOND, ShapeLayer.ShapeType.CIRCLE,
                ShapeLayer.ShapeType.ROUNDED_RECT, ShapeLayer.ShapeType.ROUNDED_RECT, ShapeLayer.ShapeType.STAR
        };
        String[] shapeNames = {
                "Drop", "Shield", "Star", "Triangle", "Heart", "Lightning",
                "Cloud", "Diamond", "Circle", "Rounded Rect", "Polygon", "Badge"
        };

        for (int i = 0; i < shapeDrawables.length; i++) {
            final int index = i;
            ImageView iv = new ImageView(this);
            iv.setImageResource(shapeDrawables[i]);
            iv.setColorFilter(Color.WHITE);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            iv.setPadding(pad, pad, pad, pad);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = (int) (64 * getResources().getDisplayMetrics().density);
            params.columnSpec = GridLayout.spec(i % 6, 1f);
            params.rowSpec = GridLayout.spec(i / 6, 1f);
            iv.setLayoutParams(params);

            iv.setOnClickListener(v -> {
                float cx = project.getCanvasWidth() / 2f;
                float cy = project.getCanvasHeight() / 2f;
                ShapeLayer shape = new ShapeLayer(shapeNames[index] + " " + (project.getLayers().size() + 1), cx, cy, 300, 300);
                shape.setShapeType(shapeTypes[index]);
                shape.setFillColor(paletteColors[(project.getLayers().size()) % paletteColors.length]);
                shape.setCornerRadius(25f);
                project.addLayer(shape);
                binding.canvasView.invalidate();
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU);
                updateUIForActiveLayer(shape);
                Toast.makeText(this, shapeNames[index] + " added", Toast.LENGTH_SHORT).show();
            });

            grid.addView(iv);
        }

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
            Toast.makeText(this, "Text added", Toast.LENGTH_SHORT).show();
        };
        binding.getRoot().findViewById(R.id.tabAddText).setOnClickListener(textClick);
        binding.getRoot().findViewById(R.id.btnRailText).setOnClickListener(textClick);

        // Freehand drawing rail
        binding.getRoot().findViewById(R.id.btnRailFreehand).setOnClickListener(v -> {
            Toast.makeText(this, "Freehand Brush activated", Toast.LENGTH_SHORT).show();
        });

        // Vector Pen rail
        binding.getRoot().findViewById(R.id.btnRailVector).setOnClickListener(v -> {
            Toast.makeText(this, "Vector Pen mode activated", Toast.LENGTH_SHORT).show();
        });

        // Templates tab
        binding.getRoot().findViewById(R.id.tabAddTemplates).setOnClickListener(v -> {
            binding.chipAspectRatio.performClick();
        });
    }

    // -------------------------------------------------------------
    // 7. EFFECT BROWSER & EFFECTS HELPER
    // -------------------------------------------------------------
    private void setupEffectBrowser() {
        binding.getRoot().findViewById(R.id.btnCloseEffectBrowser).setOnClickListener(v ->
                binding.containerEffectBrowser.setVisibility(View.GONE));

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
                    displayFilteredEffects("Search: \"" + query + "\" (" + results.size() + ")", results);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Clear category filter button
        binding.getRoot().findViewById(R.id.btnClearCategoryFilter).setOnClickListener(v -> showMainCategoryView());
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
            Bitmap thumb = EffectHelper.loadThumbnail(this, finalEff);
            if (thumb != null) {
                iv.setImageBitmap(thumb);
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
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
        List<String> categories = EffectHelper.getCategories(this);

        int[][] catColors = {
                {0xFF212529, 0xFF2B2D42},
                {0xFF1F2421, 0xFF191924},
                {0xFF2E1F27, 0xFF1D2D44},
                {0xFF283618, 0xFF3F37C9},
                {0xFF49111C, 0xFF240046},
                {0xFF202020, 0xFF1A1A1A}
        };

        for (int i = 0; i < categories.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (70 * density)
            ));
            ((LinearLayout.LayoutParams) row.getLayoutParams()).setMargins(0, 6, 0, 6);

            for (int c = 0; c < 2; c++) {
                int index = i + c;
                if (index >= categories.size()) break;

                final String catTitle = categories.get(index);
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

                card.setOnClickListener(v -> {
                    displayFilteredEffects(catTitle + " (" + effs.size() + ")", effs);
                });

                row.addView(card);
            }
            catGrid.addView(row);
        }
    }

    private void displayFilteredEffects(String title, List<EffectDefinition> effects) {
        View layoutMain = binding.getRoot().findViewById(R.id.layoutMainCategoryView);
        View layoutHeader = binding.getRoot().findViewById(R.id.layoutCategoryFilterHeader);
        LinearLayout layoutList = binding.getRoot().findViewById(R.id.layoutFilteredEffectsList);
        TextView tvTitle = binding.getRoot().findViewById(R.id.tvActiveCategoryFilterTitle);

        layoutMain.setVisibility(View.GONE);
        layoutHeader.setVisibility(View.VISIBLE);
        layoutList.setVisibility(View.VISIBLE);
        tvTitle.setText(title);

        layoutList.removeAllViews();

        if (effects.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No effects found matching this filter.");
            emptyTv.setTextColor(0xFF94A3B8);
            emptyTv.setPadding(0, 40, 0, 40);
            layoutList.addView(emptyTv);
            return;
        }

        for (EffectDefinition eff : effects) {
            View row = getLayoutInflater().inflate(R.layout.item_effect_row, layoutList, false);

            TextView tvName = row.findViewById(R.id.tvEffectItemName);
            TextView tvDesc = row.findViewById(R.id.tvEffectItemDesc);
            TextView tvParams = row.findViewById(R.id.tvEffectItemParamsSummary);
            ImageView ivThumb = row.findViewById(R.id.ivEffectThumbnail);

            tvName.setText(eff.getName());
            tvDesc.setText(eff.getDescription().isEmpty() ? eff.getCategory() : eff.getDescription());

            int controlCount = eff.getParams().size();
            StringBuilder paramSummary = new StringBuilder();
            if (controlCount == 0) {
                paramSummary.append("Presets effect");
            } else {
                paramSummary.append(controlCount).append(" Controls: ");
                for (int p = 0; p < Math.min(3, controlCount); p++) {
                    if (p > 0) paramSummary.append(", ");
                    paramSummary.append(eff.getParams().get(p).getLabel());
                }
                if (controlCount > 3) paramSummary.append("...");
            }
            tvParams.setText(paramSummary.toString());

            Bitmap bmp = EffectHelper.loadThumbnail(this, eff);
            if (bmp != null) {
                ivThumb.setImageBitmap(bmp);
            } else {
                ivThumb.setImageResource(R.drawable.ic_tool_effects);
                ivThumb.setColorFilter(0xFF00E5BC);
            }

            row.setOnClickListener(v -> openEffectControls(eff));

            layoutList.addView(row);
        }
    }

    private void showMainCategoryView() {
        View layoutMain = binding.getRoot().findViewById(R.id.layoutMainCategoryView);
        View layoutHeader = binding.getRoot().findViewById(R.id.layoutCategoryFilterHeader);
        LinearLayout layoutList = binding.getRoot().findViewById(R.id.layoutFilteredEffectsList);

        layoutMain.setVisibility(View.VISIBLE);
        layoutHeader.setVisibility(View.GONE);
        layoutList.setVisibility(View.GONE);
    }

    // -------------------------------------------------------------
    // 8. DYNAMIC EFFECT CONTROLS PANEL
    // -------------------------------------------------------------
    private void setupEffectControlsPanel() {
        binding.getRoot().findViewById(R.id.btnBackFromEffectControls).setOnClickListener(v ->
                binding.flipperBottomPanels.setDisplayedChild(PANEL_LAYER_MENU));

        binding.getRoot().findViewById(R.id.btnResetEffectParams).setOnClickListener(v -> {
            if (activeSelectedEffect != null) {
                activeSelectedEffect.resetAllParams();
                LinearLayout container = binding.getRoot().findViewById(R.id.containerDynamicEffectControls);
                EffectControlHelper.populateControls(this, container, activeSelectedEffect, this::applyEffectParamToActiveLayer);
                applyEffectToActiveLayer(activeSelectedEffect);
                Toast.makeText(this, "Parameters reset to defaults", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Opens dynamic parameter adjustment panel for any selected effect
     */
    public void openEffectControls(EffectDefinition effect) {
        activeSelectedEffect = effect;
        binding.containerEffectBrowser.setVisibility(View.GONE);

        TextView tvTitle = binding.getRoot().findViewById(R.id.tvActiveEffectTitle);
        TextView tvCat = binding.getRoot().findViewById(R.id.tvActiveEffectCategory);
        tvTitle.setText(effect.getName());
        tvCat.setText(effect.getCategory() + " • " + effect.getParams().size() + " Controls");

        LinearLayout container = binding.getRoot().findViewById(R.id.containerDynamicEffectControls);
        EffectControlHelper.populateControls(this, container, effect, this::applyEffectParamToActiveLayer);

        binding.flipperBottomPanels.setDisplayedChild(PANEL_EFFECT_CONTROLS);

        applyEffectToActiveLayer(effect);
        Toast.makeText(this, effect.getName() + " loaded", Toast.LENGTH_SHORT).show();
    }

    private void applyEffectToActiveLayer(EffectDefinition effect) {
        for (EffectParam p : effect.getParams()) {
            applyEffectParamToActiveLayer(effect, p);
        }
    }

    private void applyEffectParamToActiveLayer(EffectDefinition effect, EffectParam param) {
        CanvasLayer layer = project.getSelectedLayer();
        if (layer == null) return;

        String id = param.getId().toLowerCase();

        // If active layer is a PhotoLayer, map relevant parameters
        if (layer instanceof PhotoLayer) {
            PhotoLayer photo = (PhotoLayer) layer;
            if (id.contains("bright")) {
                // In XML, brightness is -1.0 to 1.0; map to -100 to 100
                photo.setBrightness(param.getFloatValue() * 100f);
            } else if (id.contains("contrast")) {
                // In XML, contrast is -1.0 to 3.0; map to -100 to 100
                photo.setContrast(param.getFloatValue() * 50f);
            } else if (id.contains("sat")) {
                photo.setSaturation(param.getFloatValue() * 100f);
            } else if (id.contains("warm") || id.contains("temp")) {
                photo.setWarmth(param.getFloatValue() * 100f);
            } else if (id.contains("vignette") || id.contains("roundness") || id.contains("feather")) {
                photo.setVignette(Math.max(0f, param.getFloatValue() * 50f));
            }
        } else if (layer instanceof ShapeLayer) {
            ShapeLayer shape = (ShapeLayer) layer;
            if (id.contains("radius") || id.contains("roundness")) {
                shape.setCornerRadius(param.getFloatValue() * 50f);
            } else if (id.contains("stroke") || id.contains("width") || id.contains("strength")) {
                shape.setStrokeWidth(Math.max(0f, param.getFloatValue() * 10f));
                shape.setHasStroke(shape.getStrokeWidth() > 0);
            } else if (param.getType() == EffectParam.ParamType.COLOR) {
                shape.setFillColor(param.getColorValue());
            }
        }

        binding.canvasView.invalidate();
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
        if (layer == null) {
            binding.activeLayerBar.setVisibility(View.GONE);
            return;
        }

        binding.activeLayerBar.setVisibility(View.VISIBLE);
        binding.tvActiveLayerName.setText(layer.getName());

        // Update Visibility Icon
        binding.btnLayerVisibility.setImageResource(
                layer.isVisible() ? R.drawable.ic_pe_visible : R.drawable.ic_pe_invisible
        );

        // Update Layer Color Chip
        if (layer instanceof ShapeLayer) {
            int col = ((ShapeLayer) layer).getFillColor();
            binding.viewLayerColorChip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(col));
            binding.viewLayerColorChip.setVisibility(View.VISIBLE);

            // Shape subpanel values
            Slider sliderSize = binding.getRoot().findViewById(R.id.sliderSize);
            TextView tvSize = binding.getRoot().findViewById(R.id.tvSizeVal);
            sliderSize.setValue(Math.min(800, Math.max(30, layer.getWidth())));
            tvSize.setText(String.valueOf(Math.round(layer.getWidth())));

            Slider sliderRad = binding.getRoot().findViewById(R.id.sliderRadius);
            TextView tvRad = binding.getRoot().findViewById(R.id.tvRadiusVal);
            sliderRad.setValue(Math.min(150, Math.max(0, ((ShapeLayer) layer).getCornerRadius())));
            tvRad.setText(String.valueOf(Math.round(((ShapeLayer) layer).getCornerRadius())));
        } else if (layer instanceof TextLayer) {
            int col = ((TextLayer) layer).getTextColor();
            binding.viewLayerColorChip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(col));
            binding.viewLayerColorChip.setVisibility(View.VISIBLE);
        } else {
            binding.viewLayerColorChip.setVisibility(View.GONE);
        }
    }
}
