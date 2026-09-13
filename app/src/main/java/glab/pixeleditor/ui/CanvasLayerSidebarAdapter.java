package glab.pixeleditor.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import glab.pixeleditor.R;
import glab.pixeleditor.model.CanvasLayer;
import glab.pixeleditor.model.PhotoLayer;
import glab.pixeleditor.model.ShapeLayer;
import glab.pixeleditor.model.TextLayer;

public class CanvasLayerSidebarAdapter extends RecyclerView.Adapter<CanvasLayerSidebarAdapter.LayerViewHolder> {

    public interface OnLayerSidebarListener {
        void onLayerSelected(int index);
        void onLayerVisibilityToggle(int index);
    }

    private final Context context;
    private final List<CanvasLayer> layers = new ArrayList<>();
    private int selectedIndex = -1;
    private final OnLayerSidebarListener listener;

    public CanvasLayerSidebarAdapter(Context context, OnLayerSidebarListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setLayers(List<CanvasLayer> newLayers, int selectedIdx) {
        this.layers.clear();
        if (newLayers != null) {
            this.layers.addAll(newLayers);
        }
        this.selectedIndex = selectedIdx;
        notifyDataSetChanged();
    }

    public void setSelectedIndex(int selectedIdx) {
        this.selectedIndex = selectedIdx;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_canvas_layer_sidebar, parent, false);
        return new LayerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LayerViewHolder holder, int position) {
        // Render in top-down stack order (highest z-index on top)
        int actualIndex = layers.size() - 1 - position;
        CanvasLayer layer = layers.get(actualIndex);

        holder.tvName.setText(layer.getName());

        // Set layer icon based on type
        if (layer instanceof PhotoLayer) {
            holder.ivIcon.setImageResource(R.drawable.ic_tool_presets);
            holder.ivIcon.setColorFilter(0xFF00D2FF);
        } else if (layer instanceof TextLayer) {
            holder.ivIcon.setImageResource(R.drawable.ic_pe_add);
            holder.ivIcon.setColorFilter(0xFFFFD166);
        } else if (layer instanceof ShapeLayer) {
            holder.ivIcon.setImageResource(R.drawable.ic_tool_shape);
            holder.ivIcon.setColorFilter(0xFF00E5BC);
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_pe_element);
            holder.ivIcon.setColorFilter(0xFF00E5BC);
        }

        // Active selection styling
        boolean isSelected = (actualIndex == selectedIndex);
        if (isSelected) {
            holder.cardRoot.setCardBackgroundColor(0xFF1E2F3F);
            holder.cardRoot.setStrokeColor(0xFF00E5BC);
            holder.cardRoot.setStrokeWidth(2);
            holder.tvName.setTextColor(0xFF00E5BC);
        } else {
            holder.cardRoot.setCardBackgroundColor(0xFF182030);
            holder.cardRoot.setStrokeColor(0x33FFFFFF);
            holder.cardRoot.setStrokeWidth(1);
            holder.tvName.setTextColor(0xFFE2E8F0);
        }

        // Visibility Icon
        holder.btnVisibility.setImageResource(
                layer.isVisible() ? R.drawable.ic_pe_visible : R.drawable.ic_pe_invisible
        );
        holder.btnVisibility.setColorFilter(
                layer.isVisible() ? (isSelected ? 0xFF00E5BC : 0xFFCBD5E1) : 0xFF64748B
        );

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLayerSelected(actualIndex);
            }
        });

        holder.btnVisibility.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLayerVisibilityToggle(actualIndex);
            }
        });
    }

    @Override
    public int getItemCount() {
        return layers.size();
    }

    static class LayerViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        ImageView ivIcon;
        TextView tvName;
        ImageButton btnVisibility;

        public LayerViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardLayerItem);
            ivIcon = itemView.findViewById(R.id.ivLayerTypeIcon);
            tvName = itemView.findViewById(R.id.tvSidebarLayerName);
            btnVisibility = itemView.findViewById(R.id.btnLayerVisibility);
        }
    }
}
