const { app, BrowserWindow, ipcMain, Notification } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');

let mainWindow;
let blockWindow = null;

// Путь к файлу синхронизации
const documentsPath = path.join(os.homedir(), 'Documents', 'StrictHabits');
const syncFilePath = path.join(documentsPath, 'sync_data.json');

// Создаем директорию если её нет
if (!fs.existsSync(documentsPath)) {
  fs.mkdirSync(documentsPath, { recursive: true });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    },
    icon: path.join(__dirname, 'icon.png')
  });

  mainWindow.loadFile('index.html');

  // Открываем DevTools в режиме разработки
  // mainWindow.webContents.openDevTools();

  mainWindow.on('closed', function () {
    mainWindow = null;
  });
}

app.whenReady().then(() => {
  createWindow();

  app.on('activate', function () {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', function () {
  if (process.platform !== 'darwin') app.quit();
});

// Функция для чтения данных из файла синхронизации
function readSyncData() {
  try {
    if (fs.existsSync(syncFilePath)) {
      const data = fs.readFileSync(syncFilePath, 'utf8');
      return JSON.parse(data);
    }
  } catch (error) {
    console.error('Error reading sync file:', error);
  }
  return { habits: [], blocks: [], lastUpdate: new Date().toISOString() };
}

// Функция для записи данных в файл синхронизации
function writeSyncData(data) {
  try {
    data.lastUpdate = new Date().toISOString();
    fs.writeFileSync(syncFilePath, JSON.stringify(data, null, 2), 'utf8');
    return true;
  } catch (error) {
    console.error('Error writing sync file:', error);
    return false;
  }
}

// IPC handlers for sync
ipcMain.handle('sync-get-habits', async () => {
  try {
    const data = readSyncData();
    return data.habits || [];
  } catch (error) {
    console.error('Error fetching habits:', error);
    return [];
  }
});

ipcMain.handle('sync-save-habits', async (event, habits) => {
  try {
    const data = readSyncData();
    data.habits = habits;
    const success = writeSyncData(data);
    return { success };
  } catch (error) {
    console.error('Error saving habits:', error);
    return { success: false, error: error.message };
  }
});

ipcMain.handle('sync-get-blocks', async () => {
  try {
    const data = readSyncData();
    return data.blocks || [];
  } catch (error) {
    console.error('Error fetching blocks:', error);
    return [];
  }
});

ipcMain.handle('sync-save-blocks', async (event, blocks) => {
  try {
    const data = readSyncData();
    data.blocks = blocks;
    const success = writeSyncData(data);
    return { success };
  } catch (error) {
    console.error('Error saving blocks:', error);
    return { success: false, error: error.message };
  }
});

// IPC handler для активации блокировки экрана
ipcMain.handle('activate-block', async (event, blockData) => {
  try {
    if (blockWindow) {
      blockWindow.close();
    }

    blockWindow = new BrowserWindow({
      fullscreen: true,
      frame: false,
      alwaysOnTop: true,
      skipTaskbar: true,
      webPreferences: {
        nodeIntegration: true,
        contextIsolation: false
      }
    });

    // Создаем HTML для блокировки
    const timerDisplay = blockData.timerMode ? '<div class="timer" id="timer"></div>' : '';
    const blockHtml = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <style>
          body {
            margin: 0;
            padding: 0;
            background: #D32F2F;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
            color: white;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            text-align: center;
          }
          .container {
            padding: 40px;
          }
          h1 {
            font-size: 72px;
            margin-bottom: 20px;
          }
          .timer {
            font-size: 120px;
            font-weight: bold;
            margin: 40px 0;
          }
          .message {
            font-size: 32px;
            opacity: 0.9;
          }
        </style>
      </head>
      <body>
        <div class="container">
          <h1>🚫 БЛОКИРОВКА</h1>
          ${timerDisplay}
          <div class="message">${blockData.message || 'Время блокировки'}</div>
        </div>
        <script>
          const endTime = ${blockData.endTime};
          const timerMode = ${blockData.timerMode || false};

          function updateTimer() {
            const now = Date.now();
            const remaining = endTime - now;

            if (remaining <= 0) {
              window.close();
              return;
            }

            if (timerMode) {
              const hours = Math.floor(remaining / (1000 * 60 * 60));
              const minutes = Math.floor((remaining % (1000 * 60 * 60)) / (1000 * 60));
              const seconds = Math.floor((remaining % (1000 * 60)) / 1000);

              const timerElement = document.getElementById('timer');
              if (timerElement) {
                timerElement.textContent =
                  String(hours).padStart(2, '0') + ':' +
                  String(minutes).padStart(2, '0') + ':' +
                  String(seconds).padStart(2, '0');
              }
            }

            setTimeout(updateTimer, 1000);
          }

          updateTimer();
        </script>
      </body>
      </html>
    `;

    blockWindow.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(blockHtml));

    blockWindow.on('closed', () => {
      blockWindow = null;
    });

    return { success: true };
  } catch (error) {
    console.error('Error activating block:', error);
    return { success: false, error: error.message };
  }
});

// Show notification
ipcMain.handle('show-notification', async (event, { title, body }) => {
  if (Notification.isSupported()) {
    new Notification({ title, body }).show();
  }
});
