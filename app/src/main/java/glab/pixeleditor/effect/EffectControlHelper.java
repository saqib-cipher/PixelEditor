package glab.pixeleditor.effect;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

import glab.pixeleditor.R;
import glab.pixeleditor.model.CanvasLayer;
import glab.pixeleditor.view.ScrubRulerView;

public class EffectControlHelper {

    public interface OnEffectInteractionListener {
        void onParamChanged(EffectDefinition effect, EffectParam param);
        void onEffectToggled(EffectDefinition effect, boolean isEnabled);
        void onEffectDeleted(EffectDefinition effect);
        void onEffectReset(EffectDefinition effect);
    }

    /**
     * Populates all applied effect cards for the currently selected layer.
     */
    public static void populateAppliedEffectsList(
            Context context,
            LayoutInflater inflater,
            LinearLayout container,
            TextView emptyView,
            CanvasLayer layer,
            OnEffectInteractionListener listener) {

        container.removeAllViews();
        if (layer == null) {
            if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
            return;
        }

        List<EffectDefinition> effects = layer.getAppliedEffects();
        if (effects.isEmpty()) {
            if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
            return;
        }

        if (emptyView != null) emptyView.setVisibility(View.GONE);

        for (EffectDefinition effect : effects) {
            View card = createAppliedEffectCard(context, inflater, container, effect, listener);
            container.addView(card);
        }
    }

    private static View createAppliedEffectCard(
            Context context,
            LayoutInflater inflater,
            ViewGroup parent,
            EffectDefinition effect,
            OnEffectInteractionListener listener) {

        View card = inflater.inflate(R.layout.item_applied_effect_card, parent, false);
        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout layoutHeader = card.findViewById(R.id.layoutCardHeader);
        ImageView ivExpandArrow = card.findViewById(R.id.ivExpandArrow);
        TextView tvName = card.findViewById(R.id.tvEffectCardName);
        LinearLayout layoutCollapsedActions = card.findViewById(R.id.layoutCollapsedActions);
        LinearLayout layoutExpandedActions = card.findViewById(R.id.layoutExpandedActions);
        ImageButton btnToggleVisibility = card.findViewById(R.id.btnToggleVisibility);
        ImageButton btnEffectOptions = card.findViewById(R.id.btnEffectOptions);
        ImageButton btnDeleteEffect = card.findViewById(R.id.btnDeleteEffect);
        LinearLayout containerParams = card.findViewById(R.id.containerCardParams);

        tvName.setText(effect.getName());

        // Initial expand state
        updateCardExpandState(effect.isExpanded(), ivExpandArrow, layoutCollapsedActions, layoutExpandedActions, containerParams);

        // Visibility Eye Icon state
        updateVisibilityIcon(btnToggleVisibility, effect.isEnabled());

        // Header click -> Toggle expand/collapse
        layoutHeader.setOnClickListener(v -> {
            boolean nextState = !effect.isExpanded();
            effect.setExpanded(nextState);
            updateCardExpandState(nextState, ivExpandArrow, layoutCollapsedActions, layoutExpandedActions, containerParams);
        });

        // Visibility Toggle button
        btnToggleVisibility.setOnClickListener(v -> {
            boolean nextEnabled = !effect.isEnabled();
            effect.setEnabled(nextEnabled);
            updateVisibilityIcon(btnToggleVisibility, nextEnabled);
            if (listener != null) {
                listener.onEffectToggled(effect, nextEnabled);
            }
        });

        // Delete button
        btnDeleteEffect.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEffectDeleted(effect);
            }
        });

        // Effect Options (Reset & View/Modify Shader CDATA)
        btnEffectOptions.setOnClickListener(v -> {
            String[] options = new String[]{
                    "Reset to Default Values",
                    "View / Modify Shader Code (CDATA)"
            };
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle(effect.getName() + " Options")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            effect.resetAllParams();
                            containerParams.removeAllViews();
                            populateParamRows(context, containerParams, effect, density, listener);
                            if (listener != null) {
                                listener.onEffectReset(effect);
                            }
                        } else if (which == 1) {
                            showShaderEditorDialog(context, effect, listener);
                        }
                    })
                    .show();
        });

        // Populate parameter controls inside card
        populateParamRows(context, containerParams, effect, density, listener);

        return card;
    }

    private static void updateCardExpandState(
            boolean isExpanded,
            ImageView ivArrow,
            View collapsedActions,
            View expandedActions,
            View paramsContainer) {

        ivArrow.setRotation(isExpanded ? 90f : 0f);
        collapsedActions.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
        expandedActions.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        paramsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
    }

    private static void updateVisibilityIcon(ImageButton btn, boolean isEnabled) {
        if (isEnabled) {
            btn.setImageResource(R.drawable.ic_pe_visible);
            btn.setColorFilter(0xFF00E5BC);
            btn.setAlpha(1.0f);
        } else {
            btn.setImageResource(R.drawable.ic_pe_invisible);
            btn.setColorFilter(0xFF64748B);
            btn.setAlpha(0.6f);
        }
    }

    private static void populateParamRows(
            Context context,
            LinearLayout container,
            EffectDefinition effect,
            float density,
            OnEffectInteractionListener listener) {

        container.removeAllViews();

        if (effect.getParams().isEmpty()) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText("No configurable parameters");
            emptyTv.setTextColor(0xFF64748B);
            emptyTv.setTextSize(12);
            emptyTv.setPadding(0, (int) (8 * density), 0, (int) (8 * density));
            container.addView(emptyTv);
            return;
        }

        final String[] activeParamId = new String[]{null};
        for (EffectParam p : effect.getParams()) {
            if (p.getType() == EffectParam.ParamType.SLIDER) {
                activeParamId[0] = p.getId();
                break;
            }
        }

        final List<Runnable> uiUpdaters = new ArrayList<>();

        for (EffectParam param : effect.getParams()) {
            if (param.getType() == EffectParam.ParamType.SLIDER) {
                View sliderRow = createSliderRulerRow(context, effect, param, density, activeParamId, uiUpdaters, listener);
                container.addView(sliderRow);
            } else if (param.getType() == EffectParam.ParamType.SWITCH) {
                View switchRow = createSwitchRow(context, effect, param, density, listener);
                container.addView(switchRow);
            } else if (param.getType() == EffectParam.ParamType.COLOR) {
                View colorRow = createColorRow(context, effect, param, density, listener);
                container.addView(colorRow);
            }
        }

        for (Runnable u : uiUpdaters) {
            u.run();
        }
    }

    private static View createSliderRulerRow(
            Context context,
            EffectDefinition effect,
            EffectParam param,
            float density,
            String[] activeParamId,
            List<Runnable> uiUpdaters,
            OnEffectInteractionListener listener) {

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (40 * density)
        );
        rowLp.setMargins(0, (int) (3 * density), 0, (int) (3 * density));
        row.setLayoutParams(rowLp);

        // 1. Parameter Label Tab / Button [ Scale ]
        TextView tvLabel = new TextView(context);
        tvLabel.setText(param.getLabel());
        tvLabel.setTextSize(12);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setGravity(Gravity.CENTER);
        int padH = (int) (8 * density);
        int padV = (int) (4 * density);
        tvLabel.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                (int) (80 * density),
                (int) (32 * density)
        );
        tvLabel.setLayoutParams(labelLp);
        row.addView(tvLabel);

        // 2. Precision Scrub Ruler View (Middle)
        ScrubRulerView ruler = new ScrubRulerView(context);
        GradientDrawable rulerBg = new GradientDrawable();
        rulerBg.setColor(0xFF121927);
        rulerBg.setCornerRadius(6 * density);
        ruler.setBackground(rulerBg);

        float min = param.getMinValue();
        float max = param.getMaxValue();
        float cur = param.getFloatValue();
        float step = param.getStep() > 0 ? param.getStep() : 0.01f;
        float range = Math.max(0.001f, max - min);
        float sensitivity = Math.max(step, range / 300f);
        ruler.setBounds(min, max, cur, sensitivity / 4f);

        LinearLayout.LayoutParams rulerLp = new LinearLayout.LayoutParams(
                0, (int) (32 * density), 1f
        );
        rulerLp.setMargins((int) (6 * density), 0, (int) (6 * density), 0);
        ruler.setLayoutParams(rulerLp);
        row.addView(ruler);

        // 3. Value Indicator Box with custom value input (Right)
        TextView tvValue = new TextView(context);
        tvValue.setText(param.getFormattedValue());
        tvValue.setTextColor(0xFF00E5BC);
        tvValue.setTextSize(12);
        tvValue.setTypeface(null, Typeface.BOLD);
        tvValue.setGravity(Gravity.CENTER);
        tvValue.setPadding(padH, padV, padH, padV);

        GradientDrawable valBg = new GradientDrawable();
        valBg.setColor(0xFF141C2B);
        valBg.setCornerRadius(6 * density);
        valBg.setStroke((int) (1 * density), 0xFF28354D);
        tvValue.setBackground(valBg);

        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                (int) (64 * density),
                (int) (32 * density)
        );
        tvValue.setLayoutParams(valLp);
        row.addView(tvValue);

        // Active State UI updater
        Runnable updateActiveUI = () -> {
            boolean isActive = param.getId().equals(activeParamId[0]);
            GradientDrawable lBg = new GradientDrawable();
            lBg.setCornerRadius(6 * density);
            if (isActive) {
                lBg.setColor(0xFF243048);
                lBg.setStroke((int) (1.5f * density), 0xFF00E5BC);
                tvLabel.setTextColor(0xFF00E5BC);
            } else {
                lBg.setColor(0xFF141C2B);
                lBg.setStroke((int) (1 * density), 0xFF28354D);
                tvLabel.setTextColor(0xFF94A3B8);
            }
            tvLabel.setBackground(lBg);
            ruler.setActive(isActive);
            ruler.setCurrentValue(param.getFloatValue());
        };
        uiUpdaters.add(updateActiveUI);

        // Clicking parameter label card or ruler activates it
        View.OnClickListener selectParamClick = v -> {
            activeParamId[0] = param.getId();
            for (Runnable u : uiUpdaters) {
                u.run();
            }
        };
        tvLabel.setOnClickListener(selectParamClick);
        ruler.setOnClickListener(selectParamClick);

        // Clicking value card opens EditText dialog for entering custom value directly
        tvValue.setOnClickListener(v -> {
            activeParamId[0] = param.getId();
            for (Runnable u : uiUpdaters) {
                u.run();
            }
            showCustomValueDialog(context, effect, param, tvValue, listener);
        });

        // Scrub ruler listener (sliding right increases value, sliding left decreases)
        ruler.setOnScrubListener(delta -> {
            // Activate this parameter if not active
            if (!param.getId().equals(activeParamId[0])) {
                activeParamId[0] = param.getId();
                for (Runnable u : uiUpdaters) {
                    u.run();
                }
            }

            float change = (delta / 4f) * sensitivity; // Right drag increases, left drag decreases
            float newVal = Math.max(min, Math.min(max, param.getFloatValue() + change));
            param.setFloatValue(newVal);
            ruler.setCurrentValue(newVal);
            tvValue.setText(param.getFormattedValue());

            if (listener != null) {
                listener.onParamChanged(effect, param);
            }
        });

        return row;
    }

    private static void showCustomValueDialog(
            Context context,
            EffectDefinition effect,
            EffectParam param,
            TextView tvValue,
            OnEffectInteractionListener listener) {

        android.widget.EditText input = new android.widget.EditText(context);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.valueOf(param.getFloatValue()));
        input.setTextColor(0xFF00E5BC);
        input.setTextSize(16);
        input.setPadding(48, 32, 48, 32);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Set " + param.getLabel())
                .setMessage("Min: " + param.getMinValue() + "  |  Max: " + param.getMaxValue())
                .setView(input)
                .setPositiveButton("Set", (dialog, which) -> {
                    try {
                        float val = Float.parseFloat(input.getText().toString().trim());
                        param.setFloatValue(val);
                        tvValue.setText(param.getFormattedValue());
                        if (listener != null) {
                            listener.onParamChanged(effect, param);
                        }
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static View createSwitchRow(
            Context context,
            EffectDefinition effect,
            EffectParam param,
            float density,
            OnEffectInteractionListener listener) {

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (38 * density)
        );
        rowLp.setMargins(0, (int) (3 * density), 0, (int) (3 * density));
        row.setLayoutParams(rowLp);

        TextView tvLabel = new TextView(context);
        tvLabel.setText(param.getLabel());
        tvLabel.setTextColor(0xFFFFFFFF);
        tvLabel.setTextSize(13);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        MaterialSwitch sw = new MaterialSwitch(context);
        sw.setChecked(param.getBooleanValue());
        sw.setThumbTintList(ColorStateList.valueOf(0xFF00E5BC));
        sw.setTrackTintList(ColorStateList.valueOf(0x6600E5BC));

        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            param.setBooleanValue(isChecked);
            if (listener != null) {
                listener.onParamChanged(effect, param);
            }
        });
        row.addView(sw);

        return row;
    }

    private static View createColorRow(
            Context context,
            EffectDefinition effect,
            EffectParam param,
            float density,
            OnEffectInteractionListener listener) {

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (38 * density)
        );
        rowLp.setMargins(0, (int) (3 * density), 0, (int) (3 * density));
        row.setLayoutParams(rowLp);

        TextView tvLabel = new TextView(context);
        tvLabel.setText(param.getLabel());
        tvLabel.setTextColor(0xFFFFFFFF);
        tvLabel.setTextSize(13);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        View colorSwatch = new View(context);
        int sz = (int) (26 * density);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(sz, sz);
        colorSwatch.setLayoutParams(clp);

        GradientDrawable cd = new GradientDrawable();
        cd.setShape(GradientDrawable.OVAL);
        cd.setColor(param.getColorValue());
        cd.setStroke(2, 0xFFFFFFFF);
        colorSwatch.setBackground(cd);

        colorSwatch.setOnClickListener(v -> {
            glab.pixeleditor.ui.AlightColorPickerDialog.show(
                    context,
                    param.getLabel(),
                    param.getColorValue(),
                    selectedColor -> {
                        param.setColorValue(selectedColor);
                        cd.setColor(selectedColor);
                        colorSwatch.setBackground(cd);
                        if (listener != null) {
                            listener.onParamChanged(effect, param);
                        }
                    },
                    null
            );
        });
        row.addView(colorSwatch);

        return row;
    }

    private static void showShaderEditorDialog(
            Context context,
            EffectDefinition effect,
            OnEffectInteractionListener listener) {

        android.widget.EditText editText = new android.widget.EditText(context);
        editText.setText(effect.getShaderSource() != null ? effect.getShaderSource() : "");
        editText.setTypeface(Typeface.MONOSPACE);
        editText.setTextSize(11);
        editText.setTextColor(0xFF00E5BC);
        editText.setBackgroundColor(0xFF0F172A);
        editText.setPadding(32, 24, 32, 24);
        editText.setHorizontallyScrolling(true);
        editText.setVerticalScrollBarEnabled(true);
        editText.setHorizontalScrollBarEnabled(true);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
        scrollView.addView(editText, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (350 * context.getResources().getDisplayMetrics().density)
        ));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(effect.getName() + " Shader (CDATA)")
                .setMessage("Fragment shader source extracted from XML. Edit and tap Apply to test changes live.")
                .setView(scrollView)
                .setPositiveButton("Apply & Save", (dialog, which) -> {
                    String newSource = editText.getText().toString();
                    effect.setShaderSource(newSource);
                    if (listener != null) {
                        listener.onParamChanged(effect, null);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

