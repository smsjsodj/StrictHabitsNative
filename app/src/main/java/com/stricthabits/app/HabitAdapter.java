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
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Habit h = habits.get(position);
        holder.text1.setText(h.getName() + " - " + h.getTime());
        holder.text2.setText(h.isTelegramOnly() ? "🔐 Telegram unlock" : "");
        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(position));
        holder.btnTest.setOnClickListener(v -> testListener.onTest(h));
    }

    @Override
    public int getItemCount() { return habits.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1, text2;
        Button btnDelete, btnTest;
        ViewHolder(View itemView) {
            super(itemView);
            text1 = itemView.findViewById(android.R.id.text1);
            text2 = itemView.findViewById(android.R.id.text2);
            btnDelete = new Button(itemView.getContext());
            btnTest = new Button(itemView.getContext());
            // упростим: переделаем layout, но для быстрой работы проще создать кастомный айтем.
            // Давай сделаем простой linear layout в коде, чтобы не мучиться.
        }
    }
}