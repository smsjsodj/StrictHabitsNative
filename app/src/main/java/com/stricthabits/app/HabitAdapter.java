package com.stricthabits.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.ViewHolder> {

    private List<Habit> habits;
    private OnDeleteListener deleteListener;
    private OnTestListener testListener;

    public interface OnDeleteListener {
        void onDelete(int position);
    }

    public interface OnTestListener {
        void onTest(Habit habit);
    }

    public HabitAdapter(List<Habit> habits, OnDeleteListener deleteListener, OnTestListener testListener) {
        this.habits = habits;
        this.deleteListener = deleteListener;
        this.testListener = testListener;
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
        holder.habitDetails.setText(h.getTime() + (h.isTelegramOnly() ? " 🔐" : ""));
        holder.btnTest.setOnClickListener(v -> testListener.onTest(h));
        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(position));
    }

    @Override
    public int getItemCount() {
        return habits.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView habitName, habitDetails;
        Button btnTest, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            habitName = itemView.findViewById(R.id.habitName);
            habitDetails = itemView.findViewById(R.id.habitDetails);
            btnTest = itemView.findViewById(R.id.btnTest);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}