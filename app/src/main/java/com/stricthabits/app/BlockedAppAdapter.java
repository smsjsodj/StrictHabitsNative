package com.stricthabits.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class BlockedAppAdapter extends RecyclerView.Adapter<BlockedAppAdapter.ViewHolder> {
    private final List<BlockedApp> blockedApps;
    private final Context context;
    private final OnDeleteListener onDeleteListener;
    private final OnToggleListener onToggleListener;
    private final PackageManager packageManager;

    public interface OnDeleteListener {
        void onDelete(BlockedApp app);
    }

    public interface OnToggleListener {
        void onToggle(BlockedApp app, boolean enabled);
    }

    public BlockedAppAdapter(List<BlockedApp> blockedApps, Context context,
                             OnDeleteListener onDeleteListener,
                             OnToggleListener onToggleListener) {
        this.blockedApps = blockedApps;
        this.context = context;
        this.onDeleteListener = onDeleteListener;
        this.onToggleListener = onToggleListener;
        this.packageManager = context.getPackageManager();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_blocked_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BlockedApp app = blockedApps.get(position);

        try {
            Drawable icon = packageManager.getApplicationIcon(app.getPackageName());
            holder.appIcon.setImageDrawable(icon);
        } catch (Exception e) {
            holder.appIcon.setImageResource(android.R.drawable.ic_menu_info_details);
        }

        holder.appName.setText(app.getAppName());

        String blockInfo = "permanent".equals(app.getBlockType())
                ? "Постоянно"
                : "С " + app.getStartTime() + " по " + app.getEndTime();
        holder.blockInfo.setText(blockInfo);

        holder.switchEnable.setChecked(app.isEnabled());
        holder.switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            app.setEnabled(isChecked);
            onToggleListener.onToggle(app, isChecked);
        });

        holder.btnDelete.setOnClickListener(v -> {
            onDeleteListener.onDelete(app);
            notifyItemRemoved(position);
            blockedApps.remove(position);
        });
    }

    @Override
    public int getItemCount() {
        return blockedApps.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        TextView blockInfo;
        Switch switchEnable;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            appName = itemView.findViewById(R.id.appName);
            blockInfo = itemView.findViewById(R.id.blockInfo);
            switchEnable = itemView.findViewById(R.id.switchEnable);
            btnDelete = itemView.findViewById(R.id.btnDeleteApp);
        }
    }
}
