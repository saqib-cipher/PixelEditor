package glab.pixeleditor.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import glab.pixeleditor.R;
import glab.pixeleditor.model.ProjectStorageManager;

public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {

    public interface ProjectActionListener {
        void onProjectClick(ProjectStorageManager.ProjectItem item);
        void onProjectShare(ProjectStorageManager.ProjectItem item);
        void onProjectDuplicate(ProjectStorageManager.ProjectItem item);
        void onProjectMoveToTrash(ProjectStorageManager.ProjectItem item);
        void onProjectRestore(ProjectStorageManager.ProjectItem item);
        void onProjectDeletePermanent(ProjectStorageManager.ProjectItem item);
    }

    private final Context context;
    private List<ProjectStorageManager.ProjectItem> items = new ArrayList<>();
    private final boolean isTrashMode;
    private final ProjectActionListener listener;

    public ProjectAdapter(Context context, boolean isTrashMode, ProjectActionListener listener) {
        this.context = context;
        this.isTrashMode = isTrashMode;
        this.listener = listener;
    }

    public void setItems(List<ProjectStorageManager.ProjectItem> newItems) {
        this.items = new ArrayList<>(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_project_card, parent, false);
        return new ProjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        ProjectStorageManager.ProjectItem item = items.get(position);

        holder.tvTitle.setText(item.getTitle());
        holder.tvAspect.setText(item.getAspectRatio());
        holder.tvResolution.setText(item.getFormattedResolution());
        holder.tvSize.setText(item.getFormattedSize());
        holder.tvFps.setText(item.getFps() + "fps");

        // Load thumbnail
        Bitmap thumb = ProjectStorageManager.loadThumbnail(context, item);
        if (thumb != null) {
            holder.ivThumb.setImageBitmap(thumb);
        } else {
            holder.ivThumb.setImageResource(R.drawable.ic_tool_shape);
            holder.ivThumb.setColorFilter(0x4400E5BC);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onProjectClick(item);
        });

        holder.btnMore.setOnClickListener(v -> showPopupMenu(v, item));
    }

    private void showPopupMenu(View anchor, ProjectStorageManager.ProjectItem item) {
        PopupMenu popup = new PopupMenu(context, anchor);

        if (!isTrashMode) {
            popup.getMenu().add(0, 1, 0, "Open / Edit");
            popup.getMenu().add(0, 2, 1, "Share Project");
            popup.getMenu().add(0, 3, 2, "Duplicate");
            popup.getMenu().add(0, 4, 3, "Move to Trash");
        } else {
            popup.getMenu().add(0, 5, 0, "Restore Project");
            popup.getMenu().add(0, 6, 1, "Delete Permanently");
        }

        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == 1 && listener != null) {
                listener.onProjectClick(item);
            } else if (id == 2 && listener != null) {
                listener.onProjectShare(item);
            } else if (id == 3 && listener != null) {
                listener.onProjectDuplicate(item);
            } else if (id == 4 && listener != null) {
                listener.onProjectMoveToTrash(item);
            } else if (id == 5 && listener != null) {
                listener.onProjectRestore(item);
            } else if (id == 6 && listener != null) {
                listener.onProjectDeletePermanent(item);
            }
            return true;
        });

        popup.show();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ProjectViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvTitle;
        TextView tvAspect;
        TextView tvResolution;
        TextView tvSize;
        TextView tvFps;
        TextView tvDuration;
        ImageButton btnMore;

        public ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.ivProjectThumbnail);
            tvTitle = itemView.findViewById(R.id.tvProjectTitle);
            tvAspect = itemView.findViewById(R.id.tvProjectAspect);
            tvResolution = itemView.findViewById(R.id.tvProjectResolution);
            tvSize = itemView.findViewById(R.id.tvProjectSize);
            tvFps = itemView.findViewById(R.id.tvProjectFps);
            tvDuration = itemView.findViewById(R.id.tvProjectDuration);
            btnMore = itemView.findViewById(R.id.btnProjectMore);
        }
    }
}
