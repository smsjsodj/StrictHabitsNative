import sys
import json
import os
from datetime import datetime
from pathlib import Path
from PyQt6.QtWidgets import (QApplication, QMainWindow, QWidget, QVBoxLayout,
                             QHBoxLayout, QPushButton, QLabel, QListWidget,
                             QDialog, QLineEdit, QTimeEdit, QCheckBox, QMessageBox,
                             QTabWidget, QListWidgetItem, QFileDialog, QGroupBox)
from PyQt6.QtCore import Qt, QTime, QTimer, QThread, pyqtSignal
from PyQt6.QtGui import QFont

class SyncWorker(QThread):
    sync_completed = pyqtSignal(dict)

    def __init__(self, sync_file_path):
        super().__init__()
        self.sync_file_path = sync_file_path
        self.running = True

    def run(self):
        while self.running:
            try:
                if os.path.exists(self.sync_file_path):
                    with open(self.sync_file_path, 'r', encoding='utf-8') as f:
                        data = json.load(f)
                        self.sync_completed.emit(data)
            except Exception as e:
                print(f"Sync error: {e}")

            self.msleep(5000)  # Проверка каждые 5 секунд

    def stop(self):
        self.running = False

class AddHabitDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Новая привычка")
        self.setModal(True)
        self.resize(400, 350)

        layout = QVBoxLayout()

        # Название
        layout.addWidget(QLabel("Название привычки:"))
        self.name_input = QLineEdit()
        self.name_input.setPlaceholderText("Например: Зарядка")
        layout.addWidget(self.name_input)

        # Время
        layout.addWidget(QLabel("Время:"))
        self.time_input = QTimeEdit()
        self.time_input.setDisplayFormat("HH:mm")
        self.time_input.setTime(QTime(8, 0))
        layout.addWidget(self.time_input)

        # Дни недели
        days_group = QGroupBox("Дни недели")
        days_layout = QVBoxLayout()

        self.days_checkboxes = {}
        days = [("mon", "Понедельник"), ("tue", "Вторник"), ("wed", "Среда"),
                ("thu", "Четверг"), ("fri", "Пятница"), ("sat", "Суббота"), ("sun", "Воскресенье")]

        for day_key, day_name in days:
            cb = QCheckBox(day_name)
            cb.setChecked(day_key not in ['sat', 'sun'])
            self.days_checkboxes[day_key] = cb
            days_layout.addWidget(cb)

        days_group.setLayout(days_layout)
        layout.addWidget(days_group)

        # Кнопки
        buttons_layout = QHBoxLayout()
        save_btn = QPushButton("Сохранить")
        save_btn.clicked.connect(self.accept)
        cancel_btn = QPushButton("Отмена")
        cancel_btn.clicked.connect(self.reject)

        buttons_layout.addWidget(save_btn)
        buttons_layout.addWidget(cancel_btn)
        layout.addLayout(buttons_layout)

        self.setLayout(layout)

    def get_habit_data(self):
        days = {key: cb.isChecked() for key, cb in self.days_checkboxes.items()}
        return {
            "name": self.name_input.text(),
            "time": self.time_input.time().toString("HH:mm"),
            "days": days,
            "soundEnabled": True,
            "enabled": True,
            "completedCount": 0,
            "lastCompletedDate": "",
            "skippedDate": ""
        }

class AddBlockDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Новая блокировка")
        self.setModal(True)
        self.resize(400, 300)

        layout = QVBoxLayout()

        # Время начала
        layout.addWidget(QLabel("Время начала:"))
        self.start_time_input = QTimeEdit()
        self.start_time_input.setDisplayFormat("HH:mm")
        self.start_time_input.setTime(QTime(22, 0))
        layout.addWidget(self.start_time_input)

        # Время окончания
        layout.addWidget(QLabel("Время окончания:"))
        self.end_time_input = QTimeEdit()
        self.end_time_input.setDisplayFormat("HH:mm")
        self.end_time_input.setTime(QTime(7, 0))
        layout.addWidget(self.end_time_input)

        # Дни недели
        days_group = QGroupBox("Дни недели")
        days_layout = QVBoxLayout()

        self.days_checkboxes = {}
        days = [("mon", "Понедельник"), ("tue", "Вторник"), ("wed", "Среда"),
                ("thu", "Четверг"), ("fri", "Пятница"), ("sat", "Суббота"), ("sun", "Воскресенье")]

        for day_key, day_name in days:
            cb = QCheckBox(day_name)
            cb.setChecked(True)
            self.days_checkboxes[day_key] = cb
            days_layout.addWidget(cb)

        days_group.setLayout(days_layout)
        layout.addWidget(days_group)

        # Кнопки
        buttons_layout = QHBoxLayout()
        save_btn = QPushButton("Сохранить")
        save_btn.clicked.connect(self.accept)
        cancel_btn = QPushButton("Отмена")
        cancel_btn.clicked.connect(self.reject)

        buttons_layout.addWidget(save_btn)
        buttons_layout.addWidget(cancel_btn)
        layout.addLayout(buttons_layout)

        self.setLayout(layout)

    def get_block_data(self):
        days = {key: cb.isChecked() for key, cb in self.days_checkboxes.items()}
        return {
            "startTime": self.start_time_input.time().toString("HH:mm"),
            "endTime": self.end_time_input.time().toString("HH:mm"),
            "days": days,
            "enabled": True
        }

class StrictHabitsApp(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Strict Habits - Desktop")
        self.setGeometry(100, 100, 900, 700)

        # Путь к файлу синхронизации (по умолчанию в Documents)
        docs_path = Path.home() / "Documents" / "StrictHabits"
        docs_path.mkdir(exist_ok=True)
        self.sync_file = str(docs_path / "sync_data.json")

        self.habits = []
        self.blocks = []

        self.init_ui()
        self.load_data()

        # Запускаем синхронизацию
        self.sync_worker = SyncWorker(self.sync_file)
        self.sync_worker.sync_completed.connect(self.on_sync_data)
        self.sync_worker.start()

    def init_ui(self):
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        main_layout = QVBoxLayout(central_widget)

        # Заголовок
        title = QLabel("Strict Habits")
        title_font = QFont()
        title_font.setPointSize(24)
        title_font.setBold(True)
        title.setFont(title_font)
        title.setStyleSheet("color: #D32F2F; margin: 10px;")
        main_layout.addWidget(title)

        # Информация о синхронизации
        sync_label = QLabel(f"🔄 Синхронизация: {self.sync_file}")
        sync_label.setStyleSheet("background: #E3F2FD; padding: 10px; border-radius: 5px;")
        main_layout.addWidget(sync_label)

        # Кнопка выбора папки синхронизации
        change_sync_btn = QPushButton("Изменить папку синхронизации")
        change_sync_btn.clicked.connect(self.change_sync_folder)
        main_layout.addWidget(change_sync_btn)

        # Табы
        tabs = QTabWidget()

        # Вкладка привычек
        habits_tab = QWidget()
        habits_layout = QVBoxLayout(habits_tab)

        habits_buttons = QHBoxLayout()
        add_habit_btn = QPushButton("+ Добавить привычку")
        add_habit_btn.clicked.connect(self.add_habit)
        sync_btn = QPushButton("🔄 Синхронизировать сейчас")
        sync_btn.clicked.connect(self.manual_sync)
        habits_buttons.addWidget(add_habit_btn)
        habits_buttons.addWidget(sync_btn)
        habits_layout.addLayout(habits_buttons)

        self.habits_list = QListWidget()
        habits_layout.addWidget(self.habits_list)

        tabs.addTab(habits_tab, "Привычки")

        # Вкладка блокировок
        blocks_tab = QWidget()
        blocks_layout = QVBoxLayout(blocks_tab)

        blocks_buttons = QHBoxLayout()
        add_block_btn = QPushButton("+ Добавить блокировку")
        add_block_btn.clicked.connect(self.add_block)
        blocks_buttons.addWidget(add_block_btn)
        blocks_layout.addLayout(blocks_buttons)

        self.blocks_list = QListWidget()
        blocks_layout.addWidget(self.blocks_list)

        tabs.addTab(blocks_tab, "Блокировки")

        main_layout.addWidget(tabs)

    def change_sync_folder(self):
        folder = QFileDialog.getExistingDirectory(self, "Выберите папку для синхронизации")
        if folder:
            self.sync_file = str(Path(folder) / "sync_data.json")
            self.save_data()
            QMessageBox.information(self, "Успех", f"Папка синхронизации изменена на:\n{self.sync_file}\n\nПоместите этот же файл на телефон для синхронизации!")

    def add_habit(self):
        dialog = AddHabitDialog(self)
        if dialog.exec() == QDialog.DialogCode.Accepted:
            habit_data = dialog.get_habit_data()
            if habit_data["name"]:
                self.habits.append(habit_data)
                self.save_data()
                self.update_habits_list()

    def add_block(self):
        dialog = AddBlockDialog(self)
        if dialog.exec() == QDialog.DialogCode.Accepted:
            block_data = dialog.get_block_data()
            self.blocks.append(block_data)
            self.save_data()
            self.update_blocks_list()

    def update_habits_list(self):
        self.habits_list.clear()
        for i, habit in enumerate(self.habits):
            status = "✓" if habit.get("enabled", True) else "✗"
            item_text = f"{habit['name']} - {habit['time']} [{status}] (Выполнено: {habit.get('completedCount', 0)} раз)"
            item = QListWidgetItem(item_text)
            self.habits_list.addItem(item)

    def update_blocks_list(self):
        self.blocks_list.clear()
        for i, block in enumerate(self.blocks):
            status = "✓" if block.get("enabled", True) else "✗"
            item_text = f"{block['startTime']} - {block['endTime']} [{status}]"
            item = QListWidgetItem(item_text)
            self.blocks_list.addItem(item)

    def save_data(self):
        try:
            data = {
                "habits": self.habits,
                "blocks": self.blocks,
                "lastUpdate": datetime.now().isoformat()
            }
            with open(self.sync_file, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            print(f"Данные сохранены в {self.sync_file}")
        except Exception as e:
            QMessageBox.warning(self, "Ошибка", f"Не удалось сохранить данные: {e}")

    def load_data(self):
        try:
            if os.path.exists(self.sync_file):
                with open(self.sync_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    self.habits = data.get("habits", [])
                    self.blocks = data.get("blocks", [])
                    self.update_habits_list()
                    self.update_blocks_list()
        except Exception as e:
            print(f"Ошибка загрузки данных: {e}")

    def on_sync_data(self, data):
        # Обновляем данные из файла синхронизации
        self.habits = data.get("habits", self.habits)
        self.blocks = data.get("blocks", self.blocks)
        self.update_habits_list()
        self.update_blocks_list()

    def manual_sync(self):
        self.load_data()
        QMessageBox.information(self, "Синхронизация", "Данные обновлены!")

    def closeEvent(self, event):
        self.sync_worker.stop()
        self.sync_worker.wait()
        event.accept()

if __name__ == "__main__":
    app = QApplication(sys.argv)
    window = StrictHabitsApp()
    window.show()
    sys.exit(app.exec())
