package glab.pixeleditor.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import glab.pixeleditor.view.ClampedSliderView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import glab.pixeleditor.R;

public class AlightColorPickerView extends LinearLayout {

    public interface OnColorChangeListener {
        void onColorChanged(int color, boolean fromUser);
        void onEyedropperRequested();
    }

    private static final String PREFS_NAME = "alight_color_swatches";
    private static final String KEY_CUSTOM_SWATCHES = "custom_colors";

    // 14 Standard Colors from Alight Motion
    public static final int[] ROW1_COLORS = new int[]{
            0xFFEF4444, // Red
            0xFFF59E0B, // Orange
            0xFFFBBF24, // Yellow
            0xFF10B981, // Green
            0xFF06B6D4, // Cyan
            0xFF3B82F6, // Blue
            0xFFEC4899  // Pink / Magenta
    };

    public static final int[] ROW2_COLORS = new int[]{
            0xFFFFFFFF, // White
            0xFFCBD5E1, // Light Gray
            0xFF94A3B8, // Medium Gray (#7F7F7F tone)
            0xFF475569, // Dark Gray
            0xFF000000, // Black
            0xFF38BDF8, // Sky Blue
            0xFF86EFAC  // Mint Green
    };

    private final List<Integer> customColors = new ArrayList<>();

    private int currentColor = 0xFF7F7F7F;
    private boolean isUpdatingInternal = false;

    // View References
    private View viewHeaderColorSwatch;
    private EditText etHeaderHex;
    private TextView tvHeaderOpacityBadge;
    private ImageButton btnSaveCustomSwatch;

    private ViewFlipper flipperColorModes;
    private LinearLayout rowSwatches1;
    private LinearLayout rowSwatches2;
    private LinearLayout layoutCustomSwatches;

    private ClampedSliderView sliderRed;
    private ClampedSliderView sliderGreen;
    private ClampedSliderView sliderBlue;
    private ClampedSliderView sliderAlpha;
    private TextView tvValRed;
    private TextView tvValGreen;
    private TextView tvValBlue;
    private TextView tvValAlpha;

    private FrameLayout containerSpectrumPlane;
    private ColorSpectrumPlaneView spectrumPlaneView;
    private ClampedSliderView sliderSpectrumValue;

    private MaterialCardView btnToolEyedropper;
    private MaterialCardView btnToolSlidersMode;
    private MaterialCardView btnToolPaletteMode;
    private ImageView ivSlidersModeIcon;
    private ImageView ivPaletteModeIcon;

    private OnColorChangeListener listener;

    public AlightColorPickerView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public AlightColorPickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AlightColorPickerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_alight_color_picker, this, true);

        loadCustomColors();

        bindViews();
        setupSwatches();
        setupSliders();
        setupSpectrum();
        setupSidebarActions();

        updateColorUI(currentColor, false);
    }

    private void bindViews() {
        viewHeaderColorSwatch = findViewById(R.id.viewHeaderColorSwatch);
        etHeaderHex = findViewById(R.id.etHeaderHex);
        tvHeaderOpacityBadge = findViewById(R.id.tvHeaderOpacityBadge);
        btnSaveCustomSwatch = findViewById(R.id.btnSaveCustomSwatch);

        if (etHeaderHex != null) {
            etHeaderHex.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (isUpdatingInternal) return;
                    String text = s.toString().trim();
                    if (text.startsWith("#")) text = text.substring(1);
                    try {
                        if (text.length() == 6) {
                            int rgb = (int) Long.parseLong(text, 16);
                            int alpha = Color.alpha(currentColor);
                            int col = Color.argb(alpha, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                            setColor(col, true);
                        } else if (text.length() == 8) {
                            int argb = (int) Long.parseLong(text, 16);
                            setColor(argb, true);
                        }
                    } catch (Exception ignored) {}
                }
            });
        }

        flipperColorModes = findViewById(R.id.flipperColorModes);
        rowSwatches1 = findViewById(R.id.rowSwatches1);
        rowSwatches2 = findViewById(R.id.rowSwatches2);
        layoutCustomSwatches = findViewById(R.id.layoutCustomSwatches);

        sliderRed = findViewById(R.id.sliderRed);
        sliderGreen = findViewById(R.id.sliderGreen);
        sliderBlue = findViewById(R.id.sliderBlue);
        sliderAlpha = findViewById(R.id.sliderAlpha);
        tvValRed = findViewById(R.id.tvValRed);
        tvValGreen = findViewById(R.id.tvValGreen);
        tvValBlue = findViewById(R.id.tvValBlue);
        tvValAlpha = findViewById(R.id.tvValAlpha);

        containerSpectrumPlane = findViewById(R.id.containerSpectrumPlane);
        sliderSpectrumValue = findViewById(R.id.sliderSpectrumValue);

        btnToolEyedropper = findViewById(R.id.btnToolEyedropper);
        btnToolSlidersMode = findViewById(R.id.btnToolSlidersMode);
        btnToolPaletteMode = findViewById(R.id.btnToolPaletteMode);
        ivSlidersModeIcon = findViewById(R.id.ivSlidersModeIcon);
        ivPaletteModeIcon = findViewById(R.id.ivPaletteModeIcon);

        btnSaveCustomSwatch.setOnClickListener(v -> saveCurrentColorToCustom());
    }

    private void setupSwatches() {
        populateSwatchRow(rowSwatches1, ROW1_COLORS);
        populateSwatchRow(rowSwatches2, ROW2_COLORS);
        refreshCustomSwatchesUI();
    }

    private void populateSwatchRow(LinearLayout row, int[] colors) {
        row.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int margin = (int) (3 * density);

        for (int col : colors) {
            View swatch = createSwatchView(col, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (32 * density), 1f);
            lp.setMargins(margin, 0, margin, 0);
            swatch.setLayoutParams(lp);
            row.addView(swatch);
        }
    }

    private View createSwatchView(int color, boolean isCustom) {
        float density = getResources().getDisplayMetrics().density;
        View view = new View(getContext());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(8 * density);
        bg.setColor(color);
        bg.setStroke(1, 0x33FFFFFF);
        view.setBackground(bg);
        view.setClickable(true);
        view.setFocusable(true);

        view.setOnClickListener(v -> {
            // Keep existing alpha if solid palette color clicked
            int alpha = Color.alpha(currentColor);
            int newColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
            setColor(newColor, true);
        });

        if (isCustom) {
            view.setOnLongClickListener(v -> {
                customColors.remove(Integer.valueOf(color));
                saveCustomColors();
                refreshCustomSwatchesUI();
                return true;
            });
        }

        return view;
    }

    private void refreshCustomSwatchesUI() {
        layoutCustomSwatches.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int sz = (int) (32 * density);
        int margin = (int) (3 * density);

        for (int col : customColors) {
            View swatch = createSwatchView(col, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.setMargins(margin, 0, margin, 0);
            swatch.setLayoutParams(lp);
            layoutCustomSwatches.addView(swatch);
        }
    }

    private void setupSliders() {
        sliderRed.setValueFrom(0);
        sliderRed.setValueTo(255);
        sliderGreen.setValueFrom(0);
        sliderGreen.setValueTo(255);
        sliderBlue.setValueFrom(0);
        sliderBlue.setValueTo(255);
        sliderAlpha.setValueFrom(0);
        sliderAlpha.setValueTo(255);

        ClampedSliderView.OnChangeListener sliderListener = (slider, value, fromUser) -> {
            if (!fromUser || isUpdatingInternal) return;
            int r = (int) sliderRed.getValue();
            int g = (int) sliderGreen.getValue();
            int b = (int) sliderBlue.getValue();
            int a = (int) sliderAlpha.getValue();
            int col = Color.argb(a, r, g, b);
            setColor(col, true);
        };

        sliderRed.addOnChangeListener(sliderListener);
        sliderGreen.addOnChangeListener(sliderListener);
        sliderBlue.addOnChangeListener(sliderListener);
        sliderAlpha.addOnChangeListener(sliderListener);
    }

    private void setupSpectrum() {
        sliderSpectrumValue.setValueFrom(0f);
        sliderSpectrumValue.setValueTo(1f);

        spectrumPlaneView = new ColorSpectrumPlaneView(getContext());
        containerSpectrumPlane.addView(spectrumPlaneView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        spectrumPlaneView.setOnSpectrumColorChangedListener((hue, sat) -> {
            if (isUpdatingInternal) return;
            float val = sliderSpectrumValue.getValue();
            int hsvCol = Color.HSVToColor(new float[]{hue, sat, val});
            int a = Color.alpha(currentColor);
            int newCol = Color.argb(a, Color.red(hsvCol), Color.green(hsvCol), Color.blue(hsvCol));
            setColor(newCol, true);
        });

        sliderSpectrumValue.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser || isUpdatingInternal) return;
            spectrumPlaneView.setValue(value);
            float[] hsv = new float[3];
            Color.colorToHSV(currentColor, hsv);
            hsv[2] = value;
            int hsvCol = Color.HSVToColor(hsv);
            int a = Color.alpha(currentColor);
            int newCol = Color.argb(a, Color.red(hsvCol), Color.green(hsvCol), Color.blue(hsvCol));
            setColor(newCol, true);
        });
    }

    private void setupSidebarActions() {
        btnToolEyedropper.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEyedropperRequested();
            }
        });

        btnToolSlidersMode.setOnClickListener(v -> {
            if (flipperColorModes.getDisplayedChild() == 1) {
                // Return to swatches
                flipperColorModes.setDisplayedChild(0);
                updateSidebarButtons(0);
            } else {
                flipperColorModes.setDisplayedChild(1);
                updateSidebarButtons(1);
            }
        });

        btnToolPaletteMode.setOnClickListener(v -> {
            if (flipperColorModes.getDisplayedChild() == 2) {
                // Return to swatches
                flipperColorModes.setDisplayedChild(0);
                updateSidebarButtons(0);
            } else {
                flipperColorModes.setDisplayedChild(2);
                updateSidebarButtons(2);
            }
        });
    }

    private void updateSidebarButtons(int mode) {
        ivSlidersModeIcon.setImageTintList(ColorStateList.valueOf(mode == 1 ? 0xFF00E5BC : 0xFF94A3B8));
        ivPaletteModeIcon.setImageTintList(ColorStateList.valueOf(mode == 2 ? 0xFF00E5BC : 0xFF94A3B8));
    }

    public void setColor(int color, boolean fromUser) {
        this.currentColor = color;
        updateColorUI(color, fromUser);
        if (fromUser && listener != null) {
            listener.onColorChanged(color, true);
        }
    }

    public int getColor() {
        return currentColor;
    }

    public void setOnColorChangeListener(OnColorChangeListener listener) {
        this.listener = listener;
    }

    private void updateColorUI(int color, boolean fromUser) {
        isUpdatingInternal = true;

        int a = Color.alpha(color);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        // 1. Header Swatch and Hex + Opacity Text
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setCornerRadius(12 * getResources().getDisplayMetrics().density);
        previewBg.setColor(color);
        previewBg.setStroke(1, 0xFF475569);
        viewHeaderColorSwatch.setBackground(previewBg);

        int opacityPct = Math.round((a / 255f) * 100);
        if (tvHeaderOpacityBadge != null) {
            tvHeaderOpacityBadge.setText(opacityPct + "%");
        }
        if (etHeaderHex != null && !etHeaderHex.hasFocus()) {
            String hex = String.format(Locale.US, "%02X%02X%02X", r, g, b);
            etHeaderHex.setText(hex);
        }

        // 2. Sliders
        sliderRed.setValue(r);
        sliderGreen.setValue(g);
        sliderBlue.setValue(b);
        sliderAlpha.setValue(a);
        tvValRed.setText(String.valueOf(r));
        tvValGreen.setText(String.valueOf(g));
        tvValBlue.setText(String.valueOf(b));
        tvValAlpha.setText(opacityPct + "%");

        // 3. Spectrum
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (spectrumPlaneView != null) {
            spectrumPlaneView.setColor(hsv[0], hsv[1], hsv[2]);
        }
        sliderSpectrumValue.setValue(hsv[2]);

        isUpdatingInternal = false;
    }

    private void saveCurrentColorToCustom() {
        if (!customColors.contains(currentColor)) {
            customColors.add(0, currentColor);
            saveCustomColors();
            refreshCustomSwatchesUI();
        }
    }

    private void loadCustomColors() {
        customColors.clear();
        SharedPreferences sp = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = sp.getString(KEY_CUSTOM_SWATCHES, "");
        if (!saved.isEmpty()) {
            String[] parts = saved.split(",");
            for (String p : parts) {
                try {
                    customColors.add(Integer.parseInt(p.trim()));
                } catch (Exception ignored) {}
            }
        }
    }

    private void saveCustomColors() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < customColors.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(customColors.get(i));
        }
        sp.edit().putString(KEY_CUSTOM_SWATCHES, sb.toString()).apply();
    }
}
