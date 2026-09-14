package glab.pixeleditor.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.UUID;

import glab.pixeleditor.MainActivity;
import glab.pixeleditor.R;
import glab.pixeleditor.model.ProjectStorageManager;

public class CreateProjectBottomSheet extends BottomSheetDialogFragment {

    public interface OnProjectCreatedListener {
        void onProjectCreated(ProjectStorageManager.ProjectItem item);
    }

    private OnProjectCreatedListener listener;

    private String selectedAspect = "9:16";
    private int canvasWidth = 1080;
    private int canvasHeight = 1920;
    private int customWidth = 1080;
    private int customHeight = 1920;
    private int selectedBgColor = 0xFFD8DCE3; // Light Grey
    private String selectedResolutionLabel = "1080p (FHD)";
    private String selectedBgLabel = "Light Grey";

    public void setOnProjectCreatedListener(OnProjectCreatedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_create_project, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etName = view.findViewById(R.id.etProjectName);
        ImageButton btnClear = view.findViewById(R.id.btnClearProjectName);
        TextView tvCompSize = view.findViewById(R.id.tvCompSizeDesc);
        TextView tvRes = view.findViewById(R.id.tvSelectedResolution);
        TextView tvBgName = view.findViewById(R.id.tvSelectedBgName);
        View viewBgSwatch = view.findViewById(R.id.viewSelectedBgSwatch);

        // Aspect Ratio cards
        MaterialCardView card16_9 = view.findViewById(R.id.cardAspect16_9);
        MaterialCardView card9_16 = view.findViewById(R.id.cardAspect9_16);
        MaterialCardView card4_5 = view.findViewById(R.id.cardAspect4_5);
        MaterialCardView card1_1 = view.findViewById(R.id.cardAspect1_1);
        MaterialCardView card4_3 = view.findViewById(R.id.cardAspect4_3);
        MaterialCardView cardCustom = view.findViewById(R.id.cardAspectCustom);

        TextView tv16_9 = view.findViewById(R.id.tvAspect16_9);
        TextView tv9_16 = view.findViewById(R.id.tvAspect9_16);
        TextView tv4_5 = view.findViewById(R.id.tvAspect4_5);
        TextView tv1_1 = view.findViewById(R.id.tvAspect1_1);
        TextView tv4_3 = view.findViewById(R.id.tvAspect4_3);

        MaterialCardView[] allCards = {card16_9, card9_16, card4_5, card1_1, card4_3, cardCustom};
        TextView[] allTexts = {tv16_9, tv9_16, tv4_5, tv1_1, tv4_3, null};

        Runnable updateAspectUI = () -> {
            for (int i = 0; i < allCards.length; i++) {
                allCards[i].setCardBackgroundColor(0xFF1E273C);
                allCards[i].setStrokeColor(0xFF2C3852);
                allCards[i].setStrokeWidth(1);
                if (allTexts[i] != null) {
                    allTexts[i].setTextColor(Color.WHITE);
                }
            }

            int activeIndex = 1;
            if ("16:9".equals(selectedAspect)) activeIndex = 0;
            else if ("9:16".equals(selectedAspect)) activeIndex = 1;
            else if ("4:5".equals(selectedAspect)) activeIndex = 2;
            else if ("1:1".equals(selectedAspect)) activeIndex = 3;
            else if ("4:3".equals(selectedAspect)) activeIndex = 4;
            else activeIndex = 5;

            allCards[activeIndex].setCardBackgroundColor(0xFF00E5BC);
            allCards[activeIndex].setStrokeColor(0xFF00E5BC);
            allCards[activeIndex].setStrokeWidth(2);
            if (allTexts[activeIndex] != null) {
                allTexts[activeIndex].setTextColor(0xFF00382B);
            }

            tvCompSize.setText("Composition Size\n" + canvasWidth + " × " + canvasHeight + " • " + selectedAspect);
        };

        // Click listeners for aspect ratio cards
        card16_9.setOnClickListener(v -> {
            selectedAspect = "16:9";
            canvasWidth = 1920;
            canvasHeight = 1080;
            updateAspectUI.run();
        });

        card9_16.setOnClickListener(v -> {
            selectedAspect = "9:16";
            canvasWidth = 1080;
            canvasHeight = 1920;
            updateAspectUI.run();
        });

        card4_5.setOnClickListener(v -> {
            selectedAspect = "4:5";
            canvasWidth = 1080;
            canvasHeight = 1350;
            updateAspectUI.run();
        });

        card1_1.setOnClickListener(v -> {
            selectedAspect = "1:1";
            canvasWidth = 1080;
            canvasHeight = 1080;
            updateAspectUI.run();
        });

        card4_3.setOnClickListener(v -> {
            selectedAspect = "4:3";
            canvasWidth = 1440;
            canvasHeight = 1080;
            updateAspectUI.run();
        });

        cardCustom.setOnClickListener(v -> {
            selectedAspect = "Custom";
            showDimensionsDialog(tvCompSize);
            updateAspectUI.run();
        });

        btnClear.setOnClickListener(v -> etName.setText(""));

        // Resolution Picker
        view.findViewById(R.id.cardPickerResolution).setOnClickListener(v -> {
            String[] options = {"1080p (FHD)", "720p (HD)", "4K (UHD)", "540p (SD)"};
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Select Resolution")
                    .setItems(options, (dialog, which) -> {
                        selectedResolutionLabel = options[which];
                        tvRes.setText(selectedResolutionLabel);
                    })
                    .show();
        });


        // Background Color Picker
        view.findViewById(R.id.cardPickerBg).setOnClickListener(v -> {
            String[] bgNames = {"Light Grey", "Black", "White", "Transparent", "Mint Slate"};
            int[] bgColors = {0xFFD8DCE3, 0xFF0F1320, 0xFFFFFFFF, 0x00000000, 0xFF161D2D};
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Select Background")
                    .setItems(bgNames, (dialog, which) -> {
                        selectedBgLabel = bgNames[which];
                        selectedBgColor = bgColors[which];
                        tvBgName.setText(selectedBgLabel);
                        viewBgSwatch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                selectedBgColor != 0 ? selectedBgColor : 0x44FFFFFF));
                    })
                    .show();
        });

        // Close button
        view.findViewById(R.id.btnCloseCreateSheet).setOnClickListener(v -> dismiss());

        // Submit button: CREATE PROJECT
        view.findViewById(R.id.btnSubmitCreateProject).setOnClickListener(v -> {
            String title = etName.getText().toString().trim();
            if (title.isEmpty()) {
                title = "New Project";
            }

            Context ctx = requireContext();
            String id = UUID.randomUUID().toString();
            String thumb = "thumb_" + id + ".png";

            ProjectStorageManager.ProjectItem item = new ProjectStorageManager.ProjectItem(
                    id,
                    title,
                    selectedAspect,
                    canvasWidth,
                    canvasHeight,
                    30,
                    selectedBgColor,
                    1024L,
                    System.currentTimeMillis(),
                    thumb,
                    false
            );

            ProjectStorageManager.addOrUpdateProject(ctx, item);

            if (listener != null) {
                listener.onProjectCreated(item);
            }

            // Launch MainActivity (Editor)
            Intent intent = new Intent(ctx, MainActivity.class);
            intent.putExtra("EXTRA_PROJECT_ID", item.getId());
            intent.putExtra("EXTRA_PROJECT_TITLE", item.getTitle());
            intent.putExtra("EXTRA_PROJECT_WIDTH", item.getWidth());
            intent.putExtra("EXTRA_PROJECT_HEIGHT", item.getHeight());
            intent.putExtra("EXTRA_PROJECT_BG", item.getBackgroundColor());
            intent.putExtra("EXTRA_PROJECT_ASPECT", item.getAspectRatio());
            startActivity(intent);

            dismiss();
        });
    }

        private void showDimensionsDialog (TextView tvCompSize) {
            if (getContext() == null) return;

            LinearLayout root = new LinearLayout(getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(40, 30, 40, 20);

            HorizontalScrollView chipScroll = new HorizontalScrollView(getContext());
            chipScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lpScroll.bottomMargin = 24;
            chipScroll.setLayoutParams(lpScroll);

            com.google.android.material.chip.ChipGroup chipGroup = new com.google.android.material.chip.ChipGroup(getContext());
            chipGroup.setSingleSelection(true);
            chipGroup.setSelectionRequired(true);
            chipGroup.setSingleLine(true);

            final String[] presetLabels = {"Yt Thumb FHD", "Yt Thumb SD", "Yt Banner", "LinkedIn Post", "X Header", "PLayStore FG", "Email Banner", "Custom"};
            final int[][] presetValues = {{1920, 1080}, {1280, 720}, {2560, 1440}, {1200, 627}, {1500, 500}, {1024, 500}, {1200, 400}};

            final com.google.android.material.textfield.TextInputLayout tilW = new com.google.android.material.textfield.TextInputLayout(getContext());
            tilW.setHint("Width (px)");
            tilW.setBoxBackgroundMode(2);
            LinearLayout.LayoutParams lpW = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lpW.setMarginEnd(16);
            tilW.setLayoutParams(lpW);

            final com.google.android.material.textfield.TextInputEditText etW = new com.google.android.material.textfield.TextInputEditText(tilW.getContext());
            etW.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            etW.setText(String.valueOf(customWidth));
            tilW.addView(etW);

            final com.google.android.material.textfield.TextInputLayout tilH = new com.google.android.material.textfield.TextInputLayout(getContext());
            tilH.setHint("Height (px)");
            tilH.setBoxBackgroundMode(2);
            LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tilH.setLayoutParams(lpH);

            final com.google.android.material.textfield.TextInputEditText etH = new com.google.android.material.textfield.TextInputEditText(tilH.getContext());
            etH.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            etH.setText(String.valueOf(customHeight));
            tilH.addView(etH);

            final boolean[] isProgrammatic = {false};
            int matchedChipId = -1;
            final int customChipId = View.generateViewId();

            for (int i = 0; i < presetLabels.length; i++) {
                com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(getContext());
                int chipId = (i < presetValues.length) ? View.generateViewId() : customChipId;
                chip.setId(chipId);
                chip.setText(presetLabels[i]);
                chip.setCheckable(true);

                if (i < presetValues.length) {
                    int wVal = presetValues[i][0];
                    int hVal = presetValues[i][1];
                    if (customWidth == wVal && customHeight == hVal) {
                        matchedChipId = chipId;
                    }
                    chip.setOnClickListener(v -> {
                        isProgrammatic[0] = true;
                        etW.setText(String.valueOf(wVal));
                        etH.setText(String.valueOf(hVal));
                        isProgrammatic[0] = false;
                    });
                } else {
                    if (matchedChipId == -1) {
                        matchedChipId = customChipId;
                    }
                }
                chipGroup.addView(chip);
            }

            if (matchedChipId != -1) {
                chipGroup.check(matchedChipId);
            }

            TextWatcher editWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!isProgrammatic[0]) {
                        chipGroup.check(customChipId);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };

            etW.addTextChangedListener(editWatcher);
            etH.addTextChangedListener(editWatcher);

            chipScroll.addView(chipGroup);
            root.addView(chipScroll);

            LinearLayout inputsRow = new LinearLayout(getContext());
            inputsRow.setOrientation(LinearLayout.HORIZONTAL);
            inputsRow.addView(tilW);
            inputsRow.addView(tilH);
            root.addView(inputsRow);

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Set Custom Dimensions")
                    .setView(root)
                    .setPositiveButton("Apply", (dialog, which) -> {
                        try {
                            int w = Integer.parseInt(etW.getText().toString().trim());
                            int h = Integer.parseInt(etH.getText().toString().trim());
                            if (w > 0 && h > 0) {
                                customWidth = w;
                                customHeight = h;
                                canvasWidth = w;
                                canvasHeight = h;
                                selectedAspect = calculateAspectRatio(w, h);
                                if (tvCompSize != null) {
                                    tvCompSize.setText("Composition Size\n" + customWidth + " × " + customHeight + " • " + selectedAspect);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

    public static String calculateAspectRatio(int width, int height) {
        if (width <= 0 || height <= 0) return "1:1";
        int gcdVal = gcd(width, height);
        int num = width / gcdVal;
        int den = height / gcdVal;

        if (num == 9 && den == 16) return "9:16";
        if (num == 16 && den == 9) return "16:9";
        if (num == 4 && den == 5) return "4:5";
        if (num == 5 && den == 4) return "5:4";
        if (num == 1 && den == 1) return "1:1";
        if (num == 4 && den == 3) return "4:3";
        if (num == 3 && den == 4) return "3:4";
        if (num == 3 && den == 2) return "3:2";
        if (num == 2 && den == 3) return "2:3";
        if (num == 21 && den == 9) return "21:9";
        if (num == 16 && den == 10) return "16:10";

        float ratio = (float) width / (float) height;
        if (Math.abs(ratio - (9f / 16f)) < 0.02f) return "9:16";
        if (Math.abs(ratio - (16f / 9f)) < 0.02f) return "16:9";
        if (Math.abs(ratio - (4f / 5f)) < 0.02f) return "4:5";
        if (Math.abs(ratio - (1f)) < 0.02f) return "1:1";
        if (Math.abs(ratio - (4f / 3f)) < 0.02f) return "4:3";

        return num + ":" + den;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }
}
