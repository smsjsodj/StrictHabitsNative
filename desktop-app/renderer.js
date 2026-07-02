const { ipcRenderer } = require('electron');

let habits = [];
let blocks = [];
let blockCheckInterval = null;

// Load data on startup
window.addEventListener('DOMContentLoaded', () => {
    loadHabits();
    loadBlocks();
    startBlockChecker();
});

function switchTab(tabName) {
    document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));

    event.target.classList.add('active');
    document.getElementById(tabName).classList.add('active');
}

function showStatus(message, type = 'success') {
    const status = document.getElementById('status');
    status.textContent = message;
    status.className = `status ${type}`;
    setTimeout(() => {
        status.className = 'status';
    }, 3000);
}

// Habit functions
function showAddHabitModal() {
    document.getElementById('habitModal').classList.add('active');
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('active');
}

async function saveHabit() {
    const name = document.getElementById('habitName').value.trim();
    const time = document.getElementById('habitTime').value;

    if (!name || !time) {
        showStatus('Заполните все поля', 'error');
        return;
    }

    const days = {
        mon: document.getElementById('dayMon').checked,
        tue: document.getElementById('dayTue').checked,
        wed: document.getElementById('dayWed').checked,
        thu: document.getElementById('dayThu').checked,
        fri: document.getElementById('dayFri').checked,
        sat: document.getElementById('daySat').checked,
        sun: document.getElementById('daySun').checked
    };

    const habit = {
        name,
        time,
        days,
        soundEnabled: true,
        enabled: true,
        completedCount: 0,
        lastCompletedDate: '',
        skippedDate: ''
    };

    habits.push(habit);
    await syncHabits();
    renderHabits();
    closeModal('habitModal');

    // Clear form
    document.getElementById('habitName').value = '';
    document.getElementById('habitTime').value = '';

    showStatus('Привычка добавлена');
}

async function deleteHabit(index) {
    if (confirm('Удалить эту привычку?')) {
        habits.splice(index, 1);
        await syncHabits();
        renderHabits();
        showStatus('Привычка удалена');
    }
}

async function toggleHabit(index) {
    habits[index].enabled = !habits[index].enabled;
    await syncHabits();
    renderHabits();
    showStatus(`Привычка ${habits[index].enabled ? 'включена' : 'выключена'}`);
}

function renderHabits() {
    const habitList = document.getElementById('habitList');

    if (habits.length === 0) {
        habitList.innerHTML = '<p style="color: #666; text-align: center; padding: 40px;">Нет привычек. Добавьте первую!</p>';
        return;
    }

    habitList.innerHTML = habits.map((habit, index) => `
        <div class="habit-item">
            <div class="habit-info">
                <div class="habit-name">${habit.name} ${habit.enabled ? '✓' : '✗'}</div>
                <div class="habit-time">⏰ ${habit.time} | Выполнено: ${habit.completedCount} раз</div>
            </div>
            <div class="habit-actions">
                <button class="button ${habit.enabled ? 'button-primary' : ''}" onclick="toggleHabit(${index})">
                    ${habit.enabled ? 'Выкл' : 'Вкл'}
                </button>
                <button class="button button-danger" onclick="deleteHabit(${index})">Удалить</button>
            </div>
        </div>
    `).join('');
}

async function loadHabits() {
    try {
        habits = await ipcRenderer.invoke('sync-get-habits');
        renderHabits();
    } catch (error) {
        console.error('Error loading habits:', error);
        habits = [];
        renderHabits();
    }
}

async function syncHabits() {
    try {
        const result = await ipcRenderer.invoke('sync-save-habits', habits);
        if (!result.success) {
            showStatus('Ошибка синхронизации', 'error');
        }
    } catch (error) {
        console.error('Error syncing habits:', error);
        showStatus('Ошибка синхронизации', 'error');
    }
}

// Block functions
function showAddBlockModal() {
    document.getElementById('blockModal').classList.add('active');
}

async function saveBlock() {
    const blockName = document.getElementById('blockName').value.trim();
    const startTime = document.getElementById('blockStart').value;
    const endTime = document.getElementById('blockEnd').value;

    if (!startTime || !endTime) {
        showStatus('Заполните все поля', 'error');
        return;
    }

    const days = {
        mon: document.getElementById('blockDayMon').checked,
        tue: document.getElementById('blockDayTue').checked,
        wed: document.getElementById('blockDayWed').checked,
        thu: document.getElementById('blockDayThu').checked,
        fri: document.getElementById('blockDayFri').checked,
        sat: document.getElementById('blockDaySat').checked,
        sun: document.getElementById('blockDaySun').checked
    };

    const block = {
        name: blockName,
        startTime,
        endTime,
        days,
        enabled: true,
        timerMode: document.getElementById('blockTimerMode').checked
    };

    blocks.push(block);
    await syncBlocks();
    renderBlocks();
    closeModal('blockModal');

    // Clear form
    document.getElementById('blockName').value = '';
    document.getElementById('blockStart').value = '';
    document.getElementById('blockEnd').value = '';
    document.getElementById('blockTimerMode').checked = false;

    showStatus('Блокировка добавлена');
}

async function deleteBlock(index) {
    if (confirm('Удалить эту блокировку?')) {
        blocks.splice(index, 1);
        await syncBlocks();
        renderBlocks();
        showStatus('Блокировка удалена');
    }
}

async function toggleBlock(index) {
    blocks[index].enabled = !blocks[index].enabled;
    await syncBlocks();
    renderBlocks();
    showStatus(`Блокировка ${blocks[index].enabled ? 'включена' : 'выключена'}`);
}

function renderBlocks() {
    const blockList = document.getElementById('blockList');

    if (blocks.length === 0) {
        blockList.innerHTML = '<p style="color: #666; text-align: center; padding: 40px;">Нет блокировок. Добавьте первую!</p>';
        return;
    }

    blockList.innerHTML = blocks.map((block, index) => {
        const name = block.name ? `${block.name} | ` : '';
        const timer = block.timerMode ? '⏱️' : '';
        return `
        <div class="block-item">
            <div class="block-info">
                <div class="block-time">${name}${block.startTime} - ${block.endTime} ${block.enabled ? '✓' : '✗'} ${timer}</div>
            </div>
            <div class="block-actions">
                <button class="button ${block.enabled ? 'button-primary' : ''}" onclick="toggleBlock(${index})">
                    ${block.enabled ? 'Выкл' : 'Вкл'}
                </button>
                <button class="button button-danger" onclick="deleteBlock(${index})">Удалить</button>
            </div>
        </div>
    `}).join('');
}

async function loadBlocks() {
    try {
        blocks = await ipcRenderer.invoke('sync-get-blocks');
        renderBlocks();
    } catch (error) {
        console.error('Error loading blocks:', error);
        blocks = [];
        renderBlocks();
    }
}

async function syncBlocks() {
    try {
        const result = await ipcRenderer.invoke('sync-save-blocks', blocks);
        if (!result.success) {
            showStatus('Ошибка синхронизации', 'error');
        }
    } catch (error) {
        console.error('Error syncing blocks:', error);
        showStatus('Ошибка синхронизации', 'error');
    }
}

async function syncData() {
    showStatus('Синхронизация...', 'success');
    await loadHabits();
    await loadBlocks();
    showStatus('Данные обновлены', 'success');
}

// Функция проверки активных блокировок
function startBlockChecker() {
    // Проверяем каждую минуту
    blockCheckInterval = setInterval(() => {
        checkActiveBlocks();
    }, 60000);

    // Сразу проверяем при старте
    checkActiveBlocks();
}

function checkActiveBlocks() {
    const now = new Date();
    const currentTime = now.getHours() * 60 + now.getMinutes();
    const dayOfWeek = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'][now.getDay()];

    for (const block of blocks) {
        if (!block.enabled) continue;

        // Проверяем, активен ли этот день
        if (!block.days[dayOfWeek]) continue;

        const [startH, startM] = block.startTime.split(':').map(Number);
        const [endH, endM] = block.endTime.split(':').map(Number);
        const startTime = startH * 60 + startM;
        let endTime = endH * 60 + endM;

        // Если конец меньше начала, значит блокировка переходит на следующий день
        if (endTime < startTime) {
            endTime += 24 * 60;
        }

        let isActive = false;
        if (endTime < startTime) {
            // Блокировка через полночь
            isActive = currentTime >= startTime || currentTime < (endTime % (24 * 60));
        } else {
            isActive = currentTime >= startTime && currentTime < endTime;
        }

        if (isActive) {
            // Вычисляем время окончания блокировки
            const endDate = new Date(now);
            if (endTime >= 24 * 60) {
                endDate.setDate(endDate.getDate() + 1);
                endDate.setHours(endH, endM, 0, 0);
            } else {
                endDate.setHours(endH, endM, 0, 0);
            }

            activateBlock(block, endDate.getTime());
            return;
        }
    }
}

async function activateBlock(block, endMillis) {
    const blockName = block.name || 'Блокировка';
    const message = block.timerMode
        ? `${blockName} | ${block.startTime} - ${block.endTime}`
        : blockName;

    await ipcRenderer.invoke('activate-block', {
        endTime: endMillis,
        message: message,
        timerMode: block.timerMode
    });
}
