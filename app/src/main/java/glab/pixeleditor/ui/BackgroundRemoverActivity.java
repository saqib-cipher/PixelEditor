package glab.pixeleditor.ui;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.Locale;

import glab.pixeleditor.R;
import glab.pixeleditor.segmentation.BackgroundRemover;
import glab.pixeleditor.view.ScrubRulerView;

public class BackgroundRemoverActivity extends AppCompatActivity {

    public static Bitmap sInputBitmap = null;
    public static Bitmap sResultBitmap = null;

    private enum CutoutMode {
        U2NET_LOCAL,
        CLOUD_AI,
        TAP_SUBJECT,
        PORTRAIT_SELFIE,
        TOUCH_BRUSH
    }

    private CutoutMode currentCutoutMode = CutoutMode.U2NET_LOCAL;

    private Bitmap originalBitmap;
    private Bitmap currentMaskBitmap;
    private Bitmap currentResultBitmap;

    // UI elements
    private CutoutPreviewView ivCutoutPreview;
    private TextView tvImageDimensions;
    private TextView tvModeTip;
    private LinearLayout layoutProgress;
    private TextView tvProgressMsg;

    // Mode ChipGroup
    private ChipGroup chipGroupCutoutModes;
    private Chip chipModeU2Net;
    private Chip chipModeCloud;
    private Chip chipModeTap;
    private Chip chipModeSelfie;
    private Chip chipModeBrush;

    // Sub-panels
    private LinearLayout layoutCloudControls;
    private TextView tvCurrentProvider;
    private MaterialButton btnRunCloudAi;

    private LinearLayout layoutToleranceControls;
    private ScrubRulerView rulerBgThreshold;
    private TextView tvThresholdVal;
    private MaterialSwitch switchKeepSubject;

    private LinearLayout layoutBrushControls;
    private ChipGroup chipGroupBrushMode;
    private Chip chipBrushErase;
    private Chip chipBrushRestore;
    private Slider sliderBrushSize;

    private float currentTolerance = 0.50f;
    private float currentFeather = 0.08f;
    private boolean isKeepSubject = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background_remover);

        if (sInputBitmap == null || sInputBitmap.isRecycled()) {
            Toast.makeText(this, "No image provided for background removal", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Clone input bitmap
        originalBitmap = sInputBitmap.copy(Bitmap.Config.ARGB_8888, true);
        currentResultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);

        initViews();
        setupTopBar();
        setupModes();
        setupToleranceRuler();
        setupBrushControls();
        updateProviderLabel();

        // Default: Run local AI (U2-Net) automatically on launch
        runU2NetCutout();
    }

    private void initViews() {
        ivCutoutPreview = findViewById(R.id.ivCutoutPreview);
        tvImageDimensions = findViewById(R.id.tvImageDimensions);
        tvModeTip = findViewById(R.id.tvModeTip);
        layoutProgress = findViewById(R.id.layoutProgress);
        tvProgressMsg = findViewById(R.id.tvProgressMsg);

        chipGroupCutoutModes = findViewById(R.id.chipGroupCutoutModes);
        chipModeU2Net = findViewById(R.id.chipModeU2Net);
        chipModeCloud = findViewById(R.id.chipModeCloud);
        chipModeTap = findViewById(R.id.chipModeTap);
        chipModeSelfie = findViewById(R.id.chipModeSelfie);
        chipModeBrush = findViewById(R.id.chipModeBrush);

        layoutCloudControls = findViewById(R.id.layoutCloudControls);
        tvCurrentProvider = findViewById(R.id.tvCurrentProvider);
        btnRunCloudAi = findViewById(R.id.btnRunCloudAi);

        layoutToleranceControls = findViewById(R.id.layoutToleranceControls);
        rulerBgThreshold = findViewById(R.id.rulerBgThreshold);
        tvThresholdVal = findViewById(R.id.tvThresholdVal);
        switchKeepSubject = findViewById(R.id.switchKeepSubject);

        layoutBrushControls = findViewById(R.id.layoutBrushControls);
        chipGroupBrushMode = findViewById(R.id.chipGroupBrushMode);
        chipBrushErase = findViewById(R.id.chipBrushErase);
        chipBrushRestore = findViewById(R.id.chipBrushRestore);
        sliderBrushSize = findViewById(R.id.sliderBrushSize);

        tvImageDimensions.setText(String.format(Locale.US, "%d × %d px • Neural Cutout", originalBitmap.getWidth(), originalBitmap.getHeight()));
        ivCutoutPreview.setDisplayBitmap(currentResultBitmap);

        ivCutoutPreview.setOnPreviewTouchListener(new CutoutPreviewView.OnPreviewTouchListener() {
            @Override
            public void onImageTap(float normX, float normY) {
                if (currentCutoutMode == CutoutMode.TAP_SUBJECT) {
                    runTapCutout(normX, normY);
                }
            }

            @Override
            public void onBrushStroke(float normX, float normY, float normRadius, boolean isErase) {
                if (currentMaskBitmap != null && !currentMaskBitmap.isRecycled()) {
                    BackgroundRemover.applyBrushToMask(currentMaskBitmap, normX, normY, normRadius, isErase);
                    updateResultFromMask();
                }
            }

            @Override
            public void onBrushStrokeEnd() {
                // Stroke finished
            }
        });
    }

    private void setupTopBar() {
        ImageButton btnBack = findViewById(R.id.btnBackBgRemover);
        ImageButton btnReset = findViewById(R.id.btnResetBgRemover);
        ImageButton btnSettings = findViewById(R.id.btnSettingsBgRemover);
        MaterialButton btnApply = findViewById(R.id.btnApplyBgRemover);

        btnBack.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnReset.setOnClickListener(v -> {
            if (originalBitmap != null) {
                currentResultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                currentMaskBitmap = null;
                ivCutoutPreview.setDisplayBitmap(currentResultBitmap);
                Toast.makeText(this, "Reset to original image", Toast.LENGTH_SHORT).show();
            }
        });

        btnSettings.setOnClickListener(v -> showSettingsBottomSheet());

        btnApply.setOnClickListener(v -> {
            if (currentResultBitmap != null && !currentResultBitmap.isRecycled()) {
                sResultBitmap = currentResultBitmap;
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "No cutout to apply", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupModes() {
        chipGroupCutoutModes.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);

            if (id == R.id.chipModeU2Net) {
                currentCutoutMode = CutoutMode.U2NET_LOCAL;
                ivCutoutPreview.setMode(CutoutPreviewView.Mode.PREVIEW);
                tvModeTip.setVisibility(View.GONE);
                layoutCloudControls.setVisibility(View.GONE);
                layoutToleranceControls.setVisibility(View.VISIBLE);
                layoutBrushControls.setVisibility(View.GONE);
                runU2NetCutout();

            } else if (id == R.id.chipModeCloud) {
                currentCutoutMode = CutoutMode.CLOUD_AI;
                ivCutoutPreview.setMode(CutoutPreviewView.Mode.PREVIEW);
                tvModeTip.setVisibility(View.GONE);
                layoutCloudControls.setVisibility(View.VISIBLE);
                layoutToleranceControls.setVisibility(View.GONE);
                layoutBrushControls.setVisibility(View.GONE);
                updateProviderLabel();

            } else if (id == R.id.chipModeTap) {
                currentCutoutMode = CutoutMode.TAP_SUBJECT;
                ivCutoutPreview.setMode(CutoutPreviewView.Mode.TAP_SELECT);
                tvModeTip.setText("🎯 Tap anywhere on your subject to isolate it");
                tvModeTip.setVisibility(View.VISIBLE);
                layoutCloudControls.setVisibility(View.GONE);
                layoutToleranceControls.setVisibility(View.VISIBLE);
                layoutBrushControls.setVisibility(View.GONE);

            } else if (id == R.id.chipModeSelfie) {
                currentCutoutMode = CutoutMode.PORTRAIT_SELFIE;
                ivCutoutPreview.setMode(CutoutPreviewView.Mode.PREVIEW);
                tvModeTip.setVisibility(View.GONE);
                layoutCloudControls.setVisibility(View.GONE);
                layoutToleranceControls.setVisibility(View.VISIBLE);
                layoutBrushControls.setVisibility(View.GONE);
                runSelfieCutout();

            } else if (id == R.id.chipModeBrush) {
                currentCutoutMode = CutoutMode.TOUCH_BRUSH;
                ensureMaskBitmap();
                boolean isErase = chipBrushErase.isChecked();
                ivCutoutPreview.setMode(isErase ? CutoutPreviewView.Mode.BRUSH_ERASE : CutoutPreviewView.Mode.BRUSH_RESTORE);
                tvModeTip.setText("🖌️ Drag over image to erase or restore background");
                tvModeTip.setVisibility(View.VISIBLE);
                layoutCloudControls.setVisibility(View.GONE);
                layoutToleranceControls.setVisibility(View.GONE);
                layoutBrushControls.setVisibility(View.VISIBLE);
            }
        });

        btnRunCloudAi.setOnClickListener(v -> runCloudCutout());
    }

    private void setupToleranceRuler() {
        rulerBgThreshold.setBounds(0.05f, 0.95f, currentTolerance, 0.005f);
        tvThresholdVal.setText(String.format(Locale.US, "%d%%", Math.round(currentTolerance * 100f)));

        rulerBgThreshold.setOnScrubListener(delta -> {
            currentTolerance = Math.max(0.05f, Math.min(0.95f, currentTolerance + (delta * 0.005f)));
            rulerBgThreshold.setCurrentValue(currentTolerance);
            tvThresholdVal.setText(String.format(Locale.US, "%d%%", Math.round(currentTolerance * 100f)));

            if (currentCutoutMode == CutoutMode.U2NET_LOCAL) {
                runU2NetCutout();
            } else if (currentCutoutMode == CutoutMode.PORTRAIT_SELFIE) {
                runSelfieCutout();
            }
        });

        switchKeepSubject.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isKeepSubject = isChecked;
            switchKeepSubject.setText(isChecked ? "Keep" : "Invert");
            if (currentCutoutMode == CutoutMode.U2NET_LOCAL) {
                runU2NetCutout();
            } else if (currentCutoutMode == CutoutMode.PORTRAIT_SELFIE) {
                runSelfieCutout();
            }
        });
    }

    private void setupBrushControls() {
        chipGroupBrushMode.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipBrushErase) {
                ivCutoutPreview.setMode(CutoutPreviewView.Mode.BRUSH_ERASE);
            } else {
                ivCutoutPreview.setMode(CutoutPreviewView.Mode.BRUSH_RESTORE);
            }
        });

        sliderBrushSize.addOnChangeListener((slider, value, fromUser) -> {
            float sizePx = value * getResources().getDisplayMetrics().density * 400f;
            ivCutoutPreview.setBrushRadius(sizePx);
        });
        ivCutoutPreview.setBrushRadius(0.04f * getResources().getDisplayMetrics().density * 400f);
    }

    private void ensureMaskBitmap() {
        if (currentMaskBitmap == null || currentMaskBitmap.isRecycled()) {
            if (currentResultBitmap != null) {
                currentMaskBitmap = BackgroundRemover.extractMaskFromAlpha(currentResultBitmap);
            } else {
                currentMaskBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(currentMaskBitmap);
                c.drawColor(Color.WHITE);
            }
        }
    }

    private void updateResultFromMask() {
        if (originalBitmap == null || currentMaskBitmap == null) return;
        currentResultBitmap = BackgroundRemover.applyMaskToBitmap(originalBitmap, currentMaskBitmap);
        ivCutoutPreview.setDisplayBitmap(currentResultBitmap);
    }

    // -------------------------------------------------------------
    // Cutout Execution Pipelines
    // -------------------------------------------------------------

    private void showProgress(String message) {
        tvProgressMsg.setText(message);
        layoutProgress.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        layoutProgress.setVisibility(View.GONE);
    }

    private void runU2NetCutout() {
        showProgress("Local Neural Cutout (U²-Net)...");
        BackgroundRemover.removeBackgroundU2Net(
                this,
                originalBitmap,
                !isKeepSubject,
                currentTolerance,
                currentFeather,
                new BackgroundRemover.OnSegmentationListener() {
                    @Override
                    public void onSuccess(Bitmap resultBitmap, Bitmap maskBitmap) {
                        hideProgress();
                        currentResultBitmap = resultBitmap;
                        currentMaskBitmap = maskBitmap;
                        ivCutoutPreview.setDisplayBitmap(currentResultBitmap);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        hideProgress();
                        Toast.makeText(BackgroundRemoverActivity.this, "Local AI failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void runSelfieCutout() {
        showProgress("Portrait AI Segmenting...");
        BackgroundRemover.removeBackgroundMLKit(
                originalBitmap,
                !isKeepSubject,
                currentTolerance,
                currentFeather,
                new BackgroundRemover.OnSegmentationListener() {
                    @Override
                    public void onSuccess(Bitmap resultBitmap, Bitmap maskBitmap) {
                        hideProgress();
                        currentResultBitmap = resultBitmap;
                        currentMaskBitmap = maskBitmap;
                        ivCutoutPreview.setDisplayBitmap(currentResultBitmap);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        hideProgress();
                        Toast.makeText(BackgroundRemoverActivity.this, "Portrait AI failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void runTapCutout(float normX, float normY) {
        showProgress("Isolating Subject...");
        BackgroundRemover.removeBackgroundTapPoint(
                originalBitmap,
                normX,
                normY,
                currentTolerance,
                currentFeather,
                !isKeepSubject,
                new BackgroundRemover.OnSegmentationListener() {
                    @Override
                    public void onSuccess(Bitmap resultBitmap, Bitmap maskBitmap) {
                        hideProgress();
                        currentResultBitmap = resultBitmap;
                        currentMaskBitmap = maskBitmap;
                        ivCutoutPreview.setDisplayBitmap(currentResultBitmap);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        hideProgress();
                        Toast.makeText(BackgroundRemoverActivity.this, "Tap cutout failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void runCloudCutout() {
        SharedPreferences prefs = getSharedPreferences(BackgroundRemover.PREFS_NAME, MODE_PRIVATE);
        String provider = prefs.getString(BackgroundRemover.PREF_PROVIDER, BackgroundRemover.PROVIDER_FREE_AI);
        String apiKey = prefs.getString(BackgroundRemover.PREF_API_KEY, "");
        String customUrl = prefs.getString(BackgroundRemover.PREF_CUSTOM_URL, "http://192.168.1.100:7000/api/remove");

        showProgress("Connecting to " + provider + "...");
        BackgroundRemover.removeBackgroundCloud(
                this,
                originalBitmap,
                provider,
                apiKey,
                customUrl,
                new BackgroundRemover.OnSegmentationListener() {
                    @Override
                    public void onSuccess(Bitmap resultBitmap, Bitmap maskBitmap) {
                        hideProgress();
                        currentResultBitmap = resultBitmap;
                        currentMaskBitmap = maskBitmap;
                        ivCutoutPreview.setDisplayBitmap(currentResultBitmap);
                        Toast.makeText(BackgroundRemoverActivity.this, "Cloud Cutout Complete!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        hideProgress();
                        Toast.makeText(BackgroundRemoverActivity.this, "Cloud AI Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void updateProviderLabel() {
        SharedPreferences prefs = getSharedPreferences(BackgroundRemover.PREFS_NAME, MODE_PRIVATE);
        String provider = prefs.getString(BackgroundRemover.PREF_PROVIDER, BackgroundRemover.PROVIDER_FREE_AI);
        tvCurrentProvider.setText("Using: " + provider);
    }

    // -------------------------------------------------------------
    // Cloud AI Settings Bottom Sheet
    // -------------------------------------------------------------

    private void showSettingsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_bg_remover_settings, null);
        dialog.setContentView(view);

        RadioGroup rgProviders = view.findViewById(R.id.rgBgProviders);
        RadioButton rbFree = view.findViewById(R.id.rbProviderFreeAi);
        RadioButton rbClipdrop = view.findViewById(R.id.rbProviderClipdrop);
        RadioButton rbRemoveBg = view.findViewById(R.id.rbProviderRemoveBg);
        RadioButton rbCustom = view.findViewById(R.id.rbProviderCustom);

        View layoutApiKey = view.findViewById(R.id.layoutApiKeyInput);
        View layoutCustomUrl = view.findViewById(R.id.layoutCustomUrlInput);
        EditText etApiKey = view.findViewById(R.id.etBgApiKey);
        EditText etCustomUrl = view.findViewById(R.id.etBgCustomUrl);

        MaterialButton btnCancel = view.findViewById(R.id.btnCancelSettings);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveSettings);

        SharedPreferences prefs = getSharedPreferences(BackgroundRemover.PREFS_NAME, MODE_PRIVATE);
        String savedProvider = prefs.getString(BackgroundRemover.PREF_PROVIDER, BackgroundRemover.PROVIDER_FREE_AI);
        String savedApiKey = prefs.getString(BackgroundRemover.PREF_API_KEY, "");
        String savedCustomUrl = prefs.getString(BackgroundRemover.PREF_CUSTOM_URL, "http://192.168.1.100:7000/api/remove");

        etApiKey.setText(savedApiKey);
        etCustomUrl.setText(savedCustomUrl);

        if (BackgroundRemover.PROVIDER_CLIPDROP.equalsIgnoreCase(savedProvider)) {
            rbClipdrop.setChecked(true);
            layoutApiKey.setVisibility(View.VISIBLE);
            layoutCustomUrl.setVisibility(View.GONE);
        } else if (BackgroundRemover.PROVIDER_REMOVE_BG.equalsIgnoreCase(savedProvider)) {
            rbRemoveBg.setChecked(true);
            layoutApiKey.setVisibility(View.VISIBLE);
            layoutCustomUrl.setVisibility(View.GONE);
        } else if (BackgroundRemover.PROVIDER_CUSTOM.equalsIgnoreCase(savedProvider)) {
            rbCustom.setChecked(true);
            layoutApiKey.setVisibility(View.GONE);
            layoutCustomUrl.setVisibility(View.VISIBLE);
        } else {
            rbFree.setChecked(true);
            layoutApiKey.setVisibility(View.GONE);
            layoutCustomUrl.setVisibility(View.GONE);
        }

        rgProviders.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbProviderClipdrop || checkedId == R.id.rbProviderRemoveBg) {
                layoutApiKey.setVisibility(View.VISIBLE);
                layoutCustomUrl.setVisibility(View.GONE);
            } else if (checkedId == R.id.rbProviderCustom) {
                layoutApiKey.setVisibility(View.GONE);
                layoutCustomUrl.setVisibility(View.VISIBLE);
            } else {
                layoutApiKey.setVisibility(View.GONE);
                layoutCustomUrl.setVisibility(View.GONE);
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newProvider = BackgroundRemover.PROVIDER_FREE_AI;
            if (rbClipdrop.isChecked()) newProvider = BackgroundRemover.PROVIDER_CLIPDROP;
            else if (rbRemoveBg.isChecked()) newProvider = BackgroundRemover.PROVIDER_REMOVE_BG;
            else if (rbCustom.isChecked()) newProvider = BackgroundRemover.PROVIDER_CUSTOM;

            prefs.edit()
                    .putString(BackgroundRemover.PREF_PROVIDER, newProvider)
                    .putString(BackgroundRemover.PREF_API_KEY, etApiKey.getText().toString().trim())
                    .putString(BackgroundRemover.PREF_CUSTOM_URL, etCustomUrl.getText().toString().trim())
                    .apply();

            updateProviderLabel();
            dialog.dismiss();
            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }
}
