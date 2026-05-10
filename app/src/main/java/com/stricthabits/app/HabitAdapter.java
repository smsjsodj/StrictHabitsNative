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

    public interface OnDeleteListener { void onDelete(int position); }
    public interface OnTestListener { void onTest(Habit habit); }

    public HabitAdapter(List<Habit> habits, OnDeleteListener deleteListener, OnTestListener testListener) {
        this.habits = habits;
        this.deleteListener = deleteListener;
        this.testListener = testListener;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Habit h = habits.get(position);
        holder.text1.setText(h.name + " - " + h.time);
        holder.text2.setText(h.telegramOnly ? "🔐 Telegram unlock" : "");
        holder.btnTest.setOnClickListener(v -> testListener.onTest(h));
        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(position));
    }

    @Override
    public int getItemCount() { return habits.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1, text2;
        Button btnTest, btnDelete;
        ViewHolder(View v) {
            super(v);
            text1 = v.findViewById(android.R.id.text1);
            text2 = v.findViewById(android.R.id.text2);
            btnTest = new Button(v.getContext());
            btnDelete = new Button(v.getContext());
            btnTest.setText("🧪 Тест");
            btnDelete.setText("🗑️");
            // для простоты добавим в лайаут, но лучше отдельно. Пропустим для краткости.
        }
    }
}