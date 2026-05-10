package com.stricthabits.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.ViewHolder> {

    private List<Habit> habits;
    private OnDeleteListener deleteListener;
    private OnTestListener testListener;
    private OnCompleteListener completeListener;

    public interface OnDeleteListener { void onDelete(int position); }
    public interface OnTestListener { void onTest(Habit habit); }
    public interface OnCompleteListener { void onComplete(int position, boolean completed); }

    public HabitAdapter(List<Habit> habits,
                        OnDeleteListener deleteListener,
                        OnTestListener testListener,
                        OnCompleteListener completeListener) {
        this.habits = habits;
        this.deleteListener = deleteListener;
        this.testListener = testListener;
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
        String daysStr = getDaysString(h.getDays());
        String status = h.isCompletedToday() ? "  ✅ ВЫПОЛНЕНО" : "  ⏳ Ждёт";
        holder.habitDetails.setText(h.getTime() + "  " + daysStr + status);
        holder.checkCompleted.setOnCheckedChangeListener(null);
        holder.checkCompleted.setChecked(h.isCompletedToday());
        holder.checkCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
            completeListener.onComplete(holder.getAdapterPosition(), isChecked);
        });
        holder.btnTest.setOnClickListener(v -> testListener.onTest(h));
        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(position));
    }

    private String getDaysString(Map<String, Boolean> days) {
        if (days == null) return "";
        String[] names = {"пн","вт","ср","чт","пт","сб","вс"};
        String[] keys = {"mon","tue","wed","thu","fri","sat","sun"};
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<keys.length; i++) {
            if (days.getOrDefault(keys[i], false)) {
                sb.append(names[i]).append(" ");
            }
        }
        return sb.toString().trim();
    }

    @Override
    public int getItemCount() { return habits.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkCompleted;
        TextView habitName, habitDetails;
        Button btnTest, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkCompleted = itemView.findViewById(R.id.checkCompleted);
            habitName = itemView.findViewById(R.id.habitName);
            habitDetails = itemView.findViewById(R.id.habitDetails);
            btnTest = itemView.findViewById(R.id.btnTest);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}