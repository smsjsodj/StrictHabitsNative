const { app, BrowserWindow, ipcMain, Notification } = require('electron');
const path = require('path');
const firebase = require('firebase/app');
require('firebase/database');

let mainWindow;

// Firebase configuration - пользователь должен заполнить свои данные
const firebaseConfig = {
  apiKey: "YOUR_API_KEY",
  authDomain: "YOUR_AUTH_DOMAIN",
  databaseURL: "YOUR_DATABASE_URL",
  projectId: "YOUR_PROJECT_ID",
  storageBucket: "YOUR_STORAGE_BUCKET",
  messagingSenderId: "YOUR_MESSAGING_SENDER_ID",
  appId: "YOUR_APP_ID"
};

// Initialize Firebase
if (!firebase.apps.length) {
  firebase.initializeApp(firebaseConfig);
}

const database = firebase.database();

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

// IPC handlers for sync
ipcMain.handle('sync-get-habits', async () => {
  try {
    const snapshot = await database.ref('habits').once('value');
    return snapshot.val() || [];
  } catch (error) {
    console.error('Error fetching habits:', error);
    return [];
  }
});

ipcMain.handle('sync-save-habits', async (event, habits) => {
  try {
    await database.ref('habits').set(habits);
    return { success: true };
  } catch (error) {
    console.error('Error saving habits:', error);
    return { success: false, error: error.message };
  }
});

ipcMain.handle('sync-get-blocks', async () => {
  try {
    const snapshot = await database.ref('blocks').once('value');
    return snapshot.val() || [];
  } catch (error) {
    console.error('Error fetching blocks:', error);
    return [];
  }
});

ipcMain.handle('sync-save-blocks', async (event, blocks) => {
  try {
    await database.ref('blocks').set(blocks);
    return { success: true };
  } catch (error) {
    console.error('Error saving blocks:', error);
    return { success: false, error: error.message };
  }
});

// Show notification
ipcMain.handle('show-notification', async (event, { title, body }) => {
  if (Notification.isSupported()) {
    new Notification({ title, body }).show();
  }
});
