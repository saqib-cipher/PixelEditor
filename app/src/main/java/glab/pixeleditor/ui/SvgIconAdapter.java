package glab.pixeleditor.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import glab.pixeleditor.R;
import glab.pixeleditor.svg.SvgHelper;
import glab.pixeleditor.svg.SvgIconItem;

public class SvgIconAdapter extends RecyclerView.Adapter<SvgIconAdapter.SvgViewHolder> {

    public interface OnIconSelectedListener {
        void onIconSelected(SvgIconItem item);
    }

    private final Context context;
    private final List<SvgIconItem> allItems = new ArrayList<>();
    private final List<SvgIconItem> items = new ArrayList<>();
    private OnIconSelectedListener listener;

    public SvgIconAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<SvgIconItem> newItems) {
        this.allItems.clear();
        this.items.clear();
        if (newItems != null) {
            this.allItems.addAll(newItems);
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void filter(String query) {
        items.clear();
        if (query == null || query.trim().isEmpty()) {
            items.addAll(allItems);
        } else {
            String lower = query.trim().toLowerCase();
            for (SvgIconItem item : allItems) {
                if (item.getName().toLowerCase().contains(lower) || item.getCategory().toLowerCase().contains(lower)) {
                    items.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setOnIconSelectedListener(OnIconSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SvgViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_add_shape_cell, parent, false);
        return new SvgViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SvgViewHolder holder, int position) {
        try {
            SvgIconItem item = items.get(position);
            if (item != null) {
                holder.bind(context, item, listener);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SvgViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardCell;
        private final ImageView ivShapeIcon;
        private final TextView tvShapeName;

        public SvgViewHolder(@NonNull View itemView) {
            super(itemView);
            cardCell = itemView.findViewById(R.id.cardShapeCell);
            ivShapeIcon = itemView.findViewById(R.id.ivShapeIcon);
            tvShapeName = itemView.findViewById(R.id.tvShapeName);
        }

        public void bind(Context context, SvgIconItem item, OnIconSelectedListener listener) {
            if (tvShapeName != null) {
                tvShapeName.setText(item.getName());
            }

            if (ivShapeIcon != null) {
                ivShapeIcon.clearColorFilter();
                String svgContent = item.getSvgContent(context);
                if (svgContent != null && !svgContent.isEmpty()) {
                    Bitmap thumb = SvgHelper.renderSvgThumbnail(
                            item.getName() + "_" + item.getCategory(),
                            svgContent,
                            80,
                            0xFF00E5BC,
                            item.isOutline()
                    );
                    ivShapeIcon.setImageBitmap(thumb);
                } else {
                    ivShapeIcon.setImageResource(R.drawable.ic_pe_element);
                    ivShapeIcon.setColorFilter(0xFF00E5BC);
                }
            }

            View.OnClickListener clickListener = v -> {
                if (listener != null) {
                    listener.onIconSelected(item);
                }
            };
            itemView.setOnClickListener(clickListener);
            if (cardCell != null) cardCell.setOnClickListener(clickListener);
            if (ivShapeIcon != null) ivShapeIcon.setOnClickListener(clickListener);
            if (tvShapeName != null) tvShapeName.setOnClickListener(clickListener);
        }
    }
}
