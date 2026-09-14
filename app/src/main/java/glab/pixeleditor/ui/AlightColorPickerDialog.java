package glab.pixeleditor.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;

import glab.pixeleditor.R;

public class AlightColorPickerDialog {

    public interface OnColorPickedListener {
        void onColorPicked(int color);
    }

    public interface OnEyedropperListener {
        void onEyedropperRequested(Dialog dialog, AlightColorPickerView pickerView);
    }

    public static Dialog show(
            @NonNull Context context,
            String title,
            int initialColor,
            final OnColorPickedListener listener,
            final OnEyedropperListener eyedropperListener) {

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View root = LayoutInflater.from(context).inflate(R.layout.dialog_alight_color_picker, null);
        dialog.setContentView(root);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (360 * context.getResources().getDisplayMetrics().density),
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
        }

        TextView tvTitle = root.findViewById(R.id.tvColorDialogTitle);
        if (title != null && !title.isEmpty()) {
            tvTitle.setText(title);
        }

        AlightColorPickerView pickerView = root.findViewById(R.id.dialogColorPickerView);
        pickerView.setColor(initialColor, false);

        pickerView.setOnColorChangeListener(new AlightColorPickerView.OnColorChangeListener() {
            @Override
            public void onColorChanged(int color, boolean fromUser) {
                // Real-time callback if needed
            }

            @Override
            public void onEyedropperRequested() {
                if (eyedropperListener != null) {
                    eyedropperListener.onEyedropperRequested(dialog, pickerView);
                }
            }
        });

        MaterialButton btnApply = root.findViewById(R.id.btnDialogApplyColor);
        MaterialButton btnCancel = root.findViewById(R.id.btnDialogCancelColor);

        btnApply.setOnClickListener(v -> {
            if (listener != null) {
                listener.onColorPicked(pickerView.getColor());
            }
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        return dialog;
    }
}
