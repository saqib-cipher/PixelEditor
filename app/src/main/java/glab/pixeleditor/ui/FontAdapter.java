package glab.pixeleditor.ui;

import android.content.Context;
import android.graphics.Typeface;
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
import glab.pixeleditor.font.FontItem;
import glab.pixeleditor.font.FontManager;

public class FontAdapter extends RecyclerView.Adapter<FontAdapter.FontViewHolder> {

    public interface OnFontSelectedListener {
        void onFontSelected(FontItem font);
    }

    public interface OnFavoriteToggledListener {
        void onFavoriteToggled(FontItem font, boolean isFavorite);
    }

    private final Context context;
    private final List<FontItem> items = new ArrayList<>();
    private String selectedFontPath = null;
    private OnFontSelectedListener selectedListener;
    private OnFavoriteToggledListener favoriteListener;

    public FontAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<FontItem> newItems, String selectedPath) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        this.selectedFontPath = selectedPath;
        notifyDataSetChanged();
    }

    public void setSelectedFontPath(String path) {
        this.selectedFontPath = path;
        notifyDataSetChanged();
    }

    public void setOnFontSelectedListener(OnFontSelectedListener listener) {
        this.selectedListener = listener;
    }

    public void setOnFavoriteToggledListener(OnFavoriteToggledListener listener) {
        this.favoriteListener = listener;
    }

    @NonNull
    @Override
    public FontViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_font_card, parent, false);
        return new FontViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FontViewHolder holder, int position) {
        try {
            FontItem item = items.get(position);
            if (item != null) {
                holder.bind(item, selectedFontPath, selectedListener, favoriteListener);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class FontViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView tvFontName;
        private final TextView tvFontCategory;
        private final TextView tvFontSample;
        private final ImageButton btnFavorite;
        private final ImageView ivSelected;

        public FontViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardFontItem);
            tvFontName = itemView.findViewById(R.id.tvFontName);
            tvFontCategory = itemView.findViewById(R.id.tvFontCategory);
            tvFontSample = itemView.findViewById(R.id.tvFontSample);
            btnFavorite = itemView.findViewById(R.id.btnFontFavorite);
            ivSelected = itemView.findViewById(R.id.ivFontSelected);
        }

        public void bind(
                FontItem item,
                String selectedPath,
                OnFontSelectedListener selectListener,
                OnFavoriteToggledListener favListener) {

            tvFontName.setText(item.getName());
            tvFontCategory.setText(item.getCategory());

            // Apply typeface for live preview
            try {
                Typeface tf = FontManager.getTypeface(item.getFilePath(), Typeface.NORMAL);
                tvFontSample.setTypeface(tf);
            } catch (Throwable ignored) {}

            boolean isSelected = selectedPath != null && selectedPath.equals(item.getFilePath());
            if (isSelected) {
                cardView.setStrokeColor(0xFF00E5BC);
                cardView.setCardBackgroundColor(0x1F00E5BC);
                ivSelected.setVisibility(View.VISIBLE);
            } else {
                cardView.setStrokeColor(0xFF24304A);
                cardView.setCardBackgroundColor(0xFF161D2D);
                ivSelected.setVisibility(View.GONE);
            }

            if (item.isFavorite()) {
                btnFavorite.setImageResource(R.drawable.ic_pe_star_filled);
                btnFavorite.setColorFilter(0xFFFFD700);
            } else {
                btnFavorite.setImageResource(R.drawable.ic_pe_star_outline);
                btnFavorite.setColorFilter(0xFF64748B);
            }

            btnFavorite.setOnClickListener(v -> {
                boolean newFav = !item.isFavorite();
                item.setFavorite(newFav);
                FontManager.setFontFavorite(v.getContext(), item, newFav);
                if (newFav) {
                    btnFavorite.setImageResource(R.drawable.ic_pe_star_filled);
                    btnFavorite.setColorFilter(0xFFFFD700);
                } else {
                    btnFavorite.setImageResource(R.drawable.ic_pe_star_outline);
                    btnFavorite.setColorFilter(0xFF64748B);
                }
                if (favListener != null) {
                    favListener.onFavoriteToggled(item, newFav);
                }
            });

            cardView.setOnClickListener(v -> {
                if (selectListener != null) {
                    selectListener.onFontSelected(item);
                }
            });
        }
    }
}
