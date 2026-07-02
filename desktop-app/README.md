# Strict Habits Desktop

Desktop приложение для Strict Habits с синхронизацией с мобильным приложением.

## Установка

1. Установите Node.js (https://nodejs.org/)
2. Перейдите в папку desktop-app
3. Установите зависимости:
```bash
npm install
```

## Настройка Firebase для синхронизации

1. Создайте проект в Firebase Console (https://console.firebase.google.com/)
2. Включите Realtime Database
3. Скопируйте конфигурацию Firebase
4. Вставьте её в файл `main.js` вместо плейсхолдеров

## Запуск приложения

```bash
npm start
```

## Сборка приложения

### Windows
```bash
npm run build:win
```

### macOS
```bash
npm run build:mac
```

### Linux
```bash
npm run build:linux
```

Готовое приложение будет в папке `dist/`.

## Функции

- ✅ Управление привычками
- ✅ Управление блокировками
- ✅ Синхронизация с мобильным приложением через Firebase
- ✅ Уведомления на рабочем столе
- ✅ Кроссплатформенность (Windows, macOS, Linux)

## Синхронизация с Android приложением

Для синхронизации Android приложения с desktop версией:

1. Добавьте Firebase в Android проект (google-services.json)
2. Используйте ту же Firebase Realtime Database
3. Данные будут автоматически синхронизироваться между устройствами
