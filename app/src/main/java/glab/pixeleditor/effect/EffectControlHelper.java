package glab.pixeleditor.effect;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

public class EffectControlHelper {

    public interface OnParamChangeListener {
        void onParamChanged(EffectDefinition effect, EffectParam param);
    }

    /**
     * Builds interactive controls for all parameters of the given effect inside target container.
     */
    public static void populateControls(Context context, LinearLayout container, EffectDefinition effect, OnParamChangeListener listener) {
        container.removeAllViews();
        if (effect == null) return;

        float density = context.getResources().getDisplayMetrics().density;

        // If no parameters
        if (effect.getParams().isEmpty()) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText("This effect does not require manual adjustments.");
            emptyTv.setTextColor(0xFF94A3B8);
            emptyTv.setTextSize(13);
            emptyTv.setPadding(0, (int) (16 * density), 0, (int) (16 * density));
            container.addView(emptyTv);
            return;
        }

        // Generate controls for each param
        for (EffectParam param : effect.getParams()) {
            if (param.getType() == EffectParam.ParamType.SLIDER) {
                View sliderRow = createSliderControl(context, effect, param, density, listener);
                container.addView(sliderRow);
            } else if (param.getType() == EffectParam.ParamType.SWITCH) {
                View switchRow = createSwitchControl(context, effect, param, density, listener);
                container.addView(switchRow);
            } else if (param.getType() == EffectParam.ParamType.COLOR) {
                View colorRow = createColorControl(context, effect, param, density, listener);
                container.addView(colorRow);
            }
        }
    }

    private static View createSliderControl(Context context, EffectDefinition effect, EffectParam param, float density, OnParamChangeListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        ((LinearLayout.LayoutParams) row.getLayoutParams()).setMargins(0, (int) (4 * density), 0, (int) (4 * density));

        // Param Label
        TextView tvLabel = new TextView(context);
        tvLabel.setText(param.getLabel());
        tvLabel.setTextColor(0xFFFFFFFF);
        tvLabel.setTextSize(13);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                (int) (90 * density),
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        row.addView(tvLabel);

        // Value Indicator Box
        TextView tvValue = new TextView(context);
        tvValue.setText(param.getFormattedValue());
        tvValue.setTextColor(0xFF00E5BC);
        tvValue.setTextSize(12);
        tvValue.setTypeface(null, Typeface.BOLD);
        tvValue.setGravity(Gravity.CENTER);
        int padH = (int) (8 * density);
        int padV = (int) (4 * density);
        tvValue.setPadding(padH, padV, padH, padV);

        GradientDrawable valBg = new GradientDrawable();
        valBg.setColor(0xFF1E273C);
        valBg.setCornerRadius(6 * density);
        valBg.setStroke(1, 0xFF2C3852);
        tvValue.setBackground(valBg);

        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                (int) (52 * density),
                (int) (28 * density)
        );
        valLp.setMarginEnd((int) (6 * density));
        tvValue.setLayoutParams(valLp);

        // Material 3 Slider
        Slider slider = new Slider(context);
        float min = param.getMinValue();
        float max = param.getMaxValue();
        if (min >= max) max = min + 1.0f;
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(param.getStep() > 0 && (max - min) / param.getStep() <= 500 ? param.getStep() : 0.0f);
        slider.setValue(Math.max(min, Math.min(max, param.getFloatValue())));

        // Style slider
        slider.setThumbTintList(ColorStateList.valueOf(0xFF00E5BC));
        slider.setTrackActiveTintList(ColorStateList.valueOf(0xFF00E5BC));
        slider.setTrackInactiveTintList(ColorStateList.valueOf(0xFF24304A));

        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        slider.setLayoutParams(sliderLp);

        slider.addOnChangeListener((s, val, fromUser) -> {
            if (fromUser) {
                param.setFloatValue(val);
                tvValue.setText(param.getFormattedValue());
                if (listener != null) {
                    listener.onParamChanged(effect, param);
                }
            }
        });

        row.addView(slider);
        row.addView(tvValue);

        return row;
    }

    private static View createSwitchControl(Context context, EffectDefinition effect, EffectParam param, float density, OnParamChangeListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (40 * density)
        ));

        TextView tvLabel = new TextView(context);
        tvLabel.setText(param.getLabel());
        tvLabel.setTextColor(0xFFFFFFFF);
        tvLabel.setTextSize(13);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        MaterialSwitch sw = new MaterialSwitch(context);
        sw.setChecked(param.getBooleanValue());
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            param.setBooleanValue(isChecked);
            if (listener != null) {
                listener.onParamChanged(effect, param);
            }
        });
        row.addView(sw);

        return row;
    }

    private static View createColorControl(Context context, EffectDefinition effect, EffectParam param, float density, OnParamChangeListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (42 * density)
        ));

        TextView tvLabel = new TextView(context);
        tvLabel.setText(param.getLabel());
        tvLabel.setTextColor(0xFFFFFFFF);
        tvLabel.setTextSize(13);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        View colorSwatch = new View(context);
        int sz = (int) (28 * density);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(sz, sz);
        colorSwatch.setLayoutParams(clp);

        GradientDrawable cd = new GradientDrawable();
        cd.setShape(GradientDrawable.OVAL);
        cd.setColor(param.getColorValue());
        cd.setStroke(2, 0xFFFFFFFF);
        colorSwatch.setBackground(cd);

        int[] colors = {0xFF00FF00, 0xFF0000FF, 0xFFFF0000, 0xFFFFFFFF, 0xFF000000, 0xFF00E5BC};
        colorSwatch.setOnClickListener(v -> {
            // Cycle color on tap
            int nextColor = colors[0];
            for (int i = 0; i < colors.length; i++) {
                if (colors[i] == param.getColorValue()) {
                    nextColor = colors[(i + 1) % colors.length];
                    break;
                }
            }
            param.setColorValue(nextColor);
            cd.setColor(nextColor);
            colorSwatch.setBackground(cd);
            if (listener != null) {
                listener.onParamChanged(effect, param);
            }
        });
        row.addView(colorSwatch);

        return row;
    }
}
