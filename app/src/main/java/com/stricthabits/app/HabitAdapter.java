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

    public interface OnDeleteListener {
        void onDelete(int position);
    }
    public interface OnTestListener {
        void onTest(Habit habit);
    }
    public interface OnCompleteListener {
        void onComplete(int position, boolean completed);
    }

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
        holder.habitTime.setText(h.getTime());
        // Показываем дни
        StringBuilder daysStr = new StringBuilder();
        Map<String, Boolean> days = h.getDays();
        if (days != null) {
            if (days.get("Mon")) daysStr.append("Пн ");
            if (days.get("Tue")) daysStr.append("Вт ");
            if (days.get("Wed")) daysStr.append("Ср ");
            if (days.get("Thu")) daysStr.append("Чт ");
            if (days.get("Fri")) daysStr.append("Пт ");
            if (days.get("Sat")) daysStr.append("Сб ");
            if (days.get("Sun")) daysStr.append("Вс ");
        }
        holder.habitDays.setText(daysStr.toString().trim());
        holder.checkCompleted.setChecked(h.isCompletedToday());
        holder.btnTest.setOnClickListener(v -> testListener.onTest(h));
        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(position));
        holder.checkCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
            completeListener.onComplete(position, isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return habits.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView habitName, habitTime, habitDays;
        CheckBox checkCompleted;
        Button btnTest, btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            habitName = itemView.findViewById(R.id.habitName);
            habitTime = itemView.findViewById(R.id.habitTime);
            habitDays = itemView.findViewById(R.id.habitDays);
            checkCompleted = itemView.findViewById(R.id.checkCompleted);
            btnTest = itemView.findViewById(R.id.btnTest);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}