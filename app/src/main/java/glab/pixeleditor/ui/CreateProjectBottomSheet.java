package glab.pixeleditor.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
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
    private int selectedFps = 30;
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

            tvCompSize.setText("Composition Size\n" + canvasWidth + " × " + canvasHeight);
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
            canvasWidth = 1200;
            canvasHeight = 1200;
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
                    selectedFps,
                    selectedBgColor,
                    1024,
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
}
