package com.stricthabits.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SyncManager {
    private static final String TAG = "SyncManager";
    private static final String PREFS_NAME = "sync_settings";
    private static final String KEY_SYNC_PATH = "sync_file_path";
    private static final String DEFAULT_SYNC_FILENAME = "sync_data.json";

    private Context context;
    private SharedPreferences syncPrefs;
    private SharedPreferences habitsPrefs;

    public SyncManager(Context context) {
        this.context = context;
        this.syncPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.habitsPrefs = context.getSharedPreferences("habits", Context.MODE_PRIVATE);
    }

    public String getSyncFilePath() {
        String customPath = syncPrefs.getString(KEY_SYNC_PATH, "");
        if (!customPath.isEmpty()) {
            return customPath;
        }

        // По умолчанию используем Documents/StrictHabits
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File strictHabitsDir = new File(documentsDir, "StrictHabits");
        if (!strictHabitsDir.exists()) {
            strictHabitsDir.mkdirs();
        }
        return new File(strictHabitsDir, DEFAULT_SYNC_FILENAME).getAbsolutePath();
    }

    public void setSyncFilePath(String path) {
        syncPrefs.edit().putString(KEY_SYNC_PATH, path).apply();
        Log.d(TAG, "Sync file path set to: " + path);
    }

    public boolean exportToSyncFile() {
        try {
            String syncPath = getSyncFilePath();
            File syncFile = new File(syncPath);

            // Создаем родительские директории если нужно
            File parentDir = syncFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            JSONObject data = new JSONObject();

            // Экспортируем привычки
            String habitsList = habitsPrefs.getString("list", "[]");
            JSONArray habitsArray = new JSONArray(habitsList);
            data.put("habits", habitsArray);

            // Экспортируем блокировки
            String blocksList = habitsPrefs.getString("blocks", "[]");
            JSONArray blocksArray = new JSONArray(blocksList);
            data.put("blocks", blocksArray);

            // Добавляем время последнего обновления
            String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    .format(new Date());
            data.put("lastUpdate", timestamp);

            // Записываем в файл
            FileWriter writer = new FileWriter(syncFile);
            writer.write(data.toString(2)); // Pretty print с отступами
            writer.close();

            Log.d(TAG, "Data exported to: " + syncPath);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error exporting data", e);
            return false;
        }
    }

    public boolean importFromSyncFile() {
        try {
            String syncPath = getSyncFilePath();
            File syncFile = new File(syncPath);

            if (!syncFile.exists()) {
                Log.d(TAG, "Sync file does not exist: " + syncPath);
                return false;
            }

            // Читаем файл
            BufferedReader reader = new BufferedReader(new FileReader(syncFile));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            reader.close();

            String jsonString = stringBuilder.toString();
            JSONObject data = new JSONObject(jsonString);

            // Импортируем привычки
            if (data.has("habits")) {
                JSONArray habitsArray = data.getJSONArray("habits");
                habitsPrefs.edit().putString("list", habitsArray.toString()).apply();
            }

            // Импортируем блокировки
            if (data.has("blocks")) {
                JSONArray blocksArray = data.getJSONArray("blocks");
                habitsPrefs.edit().putString("blocks", blocksArray.toString()).apply();
            }

            Log.d(TAG, "Data imported from: " + syncPath);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error importing data", e);
            return false;
        }
    }

    public boolean isSyncFileAvailable() {
        String syncPath = getSyncFilePath();
        File syncFile = new File(syncPath);
        return syncFile.exists();
    }

    public long getSyncFileLastModified() {
        String syncPath = getSyncFilePath();
        File syncFile = new File(syncPath);
        if (syncFile.exists()) {
            return syncFile.lastModified();
        }
        return 0;
    }
}
