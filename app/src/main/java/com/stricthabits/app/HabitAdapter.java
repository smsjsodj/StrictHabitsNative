package com.stricthabits.app;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.ViewHolder> {
    private final List<Habit> habits;
    private final OnDeleteListener deleteListener;
    private final OnEditListener editListener;
    private final OnSkipTodayListener skipTodayListener;
    private final OnToggleListener toggleListener;
    private final OnCompleteListener completeListener;

    public interface OnDeleteListener { void onDelete(int position); }
    public interface OnEditListener { void onEdit(int position); }
    public interface OnSkipTodayListener { void onSkipToday(int position); }
    public interface OnToggleListener { void onToggle(int position); }
    public interface OnCompleteListener { void onComplete(int position, boolean completed); }

    public HabitAdapter(List<Habit> habits,
                        OnDeleteListener deleteListener,
                        OnEditListener editListener,
                        OnSkipTodayListener skipTodayListener,
                        OnToggleListener toggleListener,
                        OnCompleteListener completeListener) {
        this.habits = habits;
        this.deleteListener = deleteListener;
        this.editListener = editListener;
        this.skipTodayListener = skipTodayListener;
        this.toggleListener = toggleListener;
        this.completeListener = completeListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_habit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Habit h = habits.get(position);
        holder.habitName.setText(h.getName());
        holder.habitDetails.setText(h.getTime()
                + "  "
                + getDaysString(h.getDays())
                + "  |  \u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u043e: "
                + h.getCompletedCount());

        if (!h.isEnabled()) {
            holder.habitStatus.setText("\u041e\u0422\u041a\u041b\u042e\u0427\u0415\u041d\u041e");
            holder.habitStatus.setTextColor(Color.parseColor("#777777"));
        } else if (h.getSkippedDate() != null && !h.getSkippedDate().isEmpty()) {
            holder.habitStatus.setText("\u041f\u0420\u041e\u041f\u0423\u0429\u0415\u041d\u041e \u0421\u0415\u0413\u041e\u0414\u041d\u042f");
            holder.habitStatus.setTextColor(Color.parseColor("#607D8B"));
        } else if (h.isCompletedToday()) {
            holder.habitStatus.setText("\u0412\u042b\u041f\u041e\u041b\u041d\u0415\u041d\u041e");
            holder.habitStatus.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            holder.habitStatus.setText("\u041e\u0416\u0418\u0414\u0410\u0415\u0422\u0421\u042f");
            holder.habitStatus.setTextColor(Color.parseColor("#FF9800"));
        }

        holder.checkCompleted.setOnCheckedChangeListener(null);
        holder.checkCompleted.setChecked(h.isCompletedToday());
        holder.checkCompleted.setEnabled(h.isEnabled());
        holder.checkCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                completeListener.onComplete(adapterPosition, isChecked);
            }
        });

        holder.btnToggle.setText(h.isEnabled() ? "\u0412\u044b\u043a\u043b" : "\u0412\u043a\u043b");
        holder.btnEdit.setOnClickListener(v -> callPosition(holder, editListener::onEdit));
        holder.btnSkipToday.setOnClickListener(v -> callPosition(holder, skipTodayListener::onSkipToday));
        holder.btnToggle.setOnClickListener(v -> callPosition(holder, toggleListener::onToggle));
        holder.btnDelete.setOnClickListener(v -> callPosition(holder, deleteListener::onDelete));
    }

    private void callPosition(ViewHolder holder, PositionCallback callback) {
        int adapterPosition = holder.getAdapterPosition();
        if (adapterPosition != RecyclerView.NO_POSITION) {
            callback.call(adapterPosition);
        }
    }

    private String getDaysString(Map<String, Boolean> days) {
        if (days == null || !days.containsValue(true)) return "\u043e\u0434\u043d\u043e\u043a\u0440\u0430\u0442\u043d\u043e";
        String[] names = {"\u043f\u043d","\u0432\u0442","\u0441\u0440","\u0447\u0442","\u043f\u0442","\u0441\u0431","\u0432\u0441"};
        String[] keys = {"mon","tue","wed","thu","fri","sat","sun"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (days.getOrDefault(keys[i], false)) {
                sb.append(names[i]).append(" ");
            }
        }
        return sb.toString().trim();
    }

    @Override
    public int getItemCount() { return habits.size(); }

    private interface PositionCallback { void call(int position); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkCompleted;
        TextView habitName, habitDetails, habitStatus;
        Button btnEdit, btnSkipToday, btnToggle, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkCompleted = itemView.findViewById(R.id.checkCompleted);
            habitName = itemView.findViewById(R.id.habitName);
            habitDetails = itemView.findViewById(R.id.habitDetails);
            habitStatus = itemView.findViewById(R.id.habitStatus);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnSkipToday = itemView.findViewById(R.id.btnSkipToday);
            btnToggle = itemView.findViewById(R.id.btnToggle);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
