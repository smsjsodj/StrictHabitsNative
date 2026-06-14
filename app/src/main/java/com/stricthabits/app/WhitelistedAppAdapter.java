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

public class WhitelistedAppAdapter extends RecyclerView.Adapter<WhitelistedAppAdapter.ViewHolder> {
    private final List<WhitelistedApp> whitelistedApps;
    private final Context context;
    private final OnDeleteListener onDeleteListener;
    private final OnToggleListener onToggleListener;
    private final PackageManager packageManager;

    public interface OnDeleteListener {
        void onDelete(WhitelistedApp app);
    }

    public interface OnToggleListener {
        void onToggle(WhitelistedApp app, boolean enabled);
    }

    public WhitelistedAppAdapter(List<WhitelistedApp> whitelistedApps, Context context,
                                 OnDeleteListener onDeleteListener,
                                 OnToggleListener onToggleListener) {
        this.whitelistedApps = whitelistedApps;
        this.context = context;
        this.onDeleteListener = onDeleteListener;
        this.onToggleListener = onToggleListener;
        this.packageManager = context.getPackageManager();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_whitelisted_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WhitelistedApp app = whitelistedApps.get(position);

        try {
            Drawable icon = packageManager.getApplicationIcon(app.getPackageName());
            holder.appIcon.setImageDrawable(icon);
        } catch (Exception e) {
            holder.appIcon.setImageResource(android.R.drawable.ic_menu_info_details);
        }

        holder.appName.setText(app.getAppName());
        holder.appStatus.setText("✓ Разрешено всегда");

        holder.switchEnable.setChecked(app.isEnabled());
        holder.switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            app.setEnabled(isChecked);
            onToggleListener.onToggle(app, isChecked);
        });

        holder.btnDelete.setOnClickListener(v -> {
            onDeleteListener.onDelete(app);
            notifyItemRemoved(position);
            whitelistedApps.remove(position);
        });
    }

    @Override
    public int getItemCount() {
        return whitelistedApps.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        TextView appStatus;
        Switch switchEnable;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            appName = itemView.findViewById(R.id.appName);
            appStatus = itemView.findViewById(R.id.appStatus);
            switchEnable = itemView.findViewById(R.id.switchEnable);
            btnDelete = itemView.findViewById(R.id.btnDeleteApp);
        }
    }
}
