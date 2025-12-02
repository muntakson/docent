/**
 * Docent AMR Admin Control Panel - JavaScript Controller
 * Handles all API communication, UI updates, and control logic
 */

// Global state
const state = {
    amrIP: '192.168.0.5',
    baseURL: '/api',
    connected: false,
    currentPosition: {x: 0, y: 0, theta: 0},
    booths: [],
    schedule: [],
    tourActive: false,
    tourPaused: false,
    currentTourIndex: 0,
    selectedBooth: null,
    mapScale: 50, // pixels per meter
    mapOffsetX: 400,
    mapOffsetY: 250
};

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    initializeApp();
    setupEventListeners();
    startStatusUpdates();
});

/**
 * Initialize the application
 */
async function initializeApp() {
    log('System', 'Initializing Admin Control Panel...', 'info');

    // Initialize canvas
    initializeCanvas();

    // Load initial data
    await loadBooths();
    await checkConnection();
    await updateSystemStatus();

    log('System', 'Initialization complete', 'success');
}

/**
 * Setup event listeners
 */
function setupEventListeners() {
    // Menu navigation
    document.querySelectorAll('.menu-item').forEach(item => {
        item.addEventListener('click', () => {
            const panel = item.getAttribute('data-panel');
            switchPanel(panel);
        });
    });

    // Download map button
    const downloadMapBtn = document.getElementById('downloadMapBtn');
    if (downloadMapBtn) {
        downloadMapBtn.addEventListener('click', downloadAndDisplayMap);
    }

    // Keyboard shortcuts
    document.addEventListener('keydown', handleKeyboard);
}

/**
 * Switch between panels
 */
function switchPanel(panelId) {
    // Update menu
    document.querySelectorAll('.menu-item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('data-panel') === panelId) {
            item.classList.add('active');
        }
    });

    // Update panels
    document.querySelectorAll('.panel').forEach(panel => {
        panel.classList.remove('active');
    });
    document.getElementById(panelId).classList.add('active');
}

/**
 * Initialize map canvas
 */
function initializeCanvas() {
    const canvas = document.getElementById('mapCanvas');
    const container = canvas.parentElement;

    canvas.width = container.offsetWidth;
    canvas.height = 500;

    state.mapOffsetX = canvas.width / 2;
    state.mapOffsetY = canvas.height / 2;

    drawMap();
}

/**
 * Draw map with grid and markers
 */
function drawMap() {
    const canvas = document.getElementById('mapCanvas');
    const ctx = canvas.getContext('2d');

    // Clear canvas
    ctx.fillStyle = '#1a252f';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // Draw grid
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.lineWidth = 1;

    // Vertical lines
    for (let x = -10; x <= 10; x++) {
        const screenX = state.mapOffsetX + x * state.mapScale;
        ctx.beginPath();
        ctx.moveTo(screenX, 0);
        ctx.lineTo(screenX, canvas.height);
        ctx.stroke();
    }

    // Horizontal lines
    for (let y = -10; y <= 10; y++) {
        const screenY = state.mapOffsetY + y * state.mapScale;
        ctx.beginPath();
        ctx.moveTo(0, screenY);
        ctx.lineTo(canvas.width, screenY);
        ctx.stroke();
    }

    // Draw axes
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.3)';
    ctx.lineWidth = 2;

    // X-axis
    ctx.beginPath();
    ctx.moveTo(0, state.mapOffsetY);
    ctx.lineTo(canvas.width, state.mapOffsetY);
    ctx.stroke();

    // Y-axis
    ctx.beginPath();
    ctx.moveTo(state.mapOffsetX, 0);
    ctx.moveTo(state.mapOffsetX, canvas.height);
    ctx.stroke();

    // Draw origin
    ctx.fillStyle = 'rgba(52, 152, 219, 0.5)';
    ctx.beginPath();
    ctx.arc(state.mapOffsetX, state.mapOffsetY, 5, 0, 2 * Math.PI);
    ctx.fill();

    // Draw booth markers
    updateBoothMarkers();
}

/**
 * Download and display actual SLAM map
 */
async function downloadAndDisplayMap() {
    try {
        log('Map', 'Downloading map from AMR...', 'info');

        // Fetch map data
        const response = await fetch(`${state.baseURL}/reeman/map`);
        const mapData = await response.json();

        state.mapData = mapData;

        // Reload booths to ensure we have the latest preset waypoints
        await loadBooths();

        // Display map image
        displaySLAMMap(mapData);

        log('Map', `Map loaded: ${mapData.width}x${mapData.height}, Booths: ${state.booths.length}`, 'success');

    } catch (error) {
        log('Map', `Failed to download map: ${error.message}`, 'error');
    }
}

/**
 * Display SLAM map image on canvas
 */
function displaySLAMMap(mapData) {
    const canvas = document.getElementById('mapCanvas');
    const ctx = canvas.getContext('2d');

    // Create image from base64 data
    const img = new Image();
    img.onload = function() {
        // Clear canvas
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        // Calculate scaling to fit canvas
        const scale = Math.min(
            canvas.width / img.width,
            canvas.height / img.height
        ) * 0.9; // 90% to leave margin

        const scaledWidth = img.width * scale;
        const scaledHeight = img.height * scale;

        // Center the image
        const offsetX = (canvas.width - scaledWidth) / 2;
        const offsetY = (canvas.height - scaledHeight) / 2;

        // Draw map image
        ctx.drawImage(img, offsetX, offsetY, scaledWidth, scaledHeight);

        // Store transform info for coordinate conversion
        state.mapTransform = {
            offsetX: offsetX,
            offsetY: offsetY,
            scale: scale,
            imgWidth: img.width,
            imgHeight: img.height,
            resolution: mapData.resolution,
            originX: mapData.origin_x,
            originY: mapData.origin_y
        };

        // Redraw robot and booth markers
        updateBoothMarkers();
        if (state.currentPosition) {
            updateRobotPosition(
                state.currentPosition.x,
                state.currentPosition.y,
                state.currentPosition.theta
            );
        }
    };

    img.src = mapData.image_url;
}

/**
 * Convert world coordinates to screen coordinates (SLAM map version)
 */
function worldToScreenSLAM(worldX, worldY) {
    if (!state.mapTransform) {
        // Fallback to grid-based conversion
        return worldToScreen(worldX, worldY);
    }

    const t = state.mapTransform;

    // Convert world coordinates to map pixels
    const mapPixelX = (worldX - t.originX) / t.resolution;
    const mapPixelY = (worldY - t.originY) / t.resolution;

    // Convert map pixels to screen coordinates
    // Note: Image Y is inverted
    const screenX = t.offsetX + mapPixelX * t.scale;
    const screenY = t.offsetY + (t.imgHeight - mapPixelY) * t.scale;

    return { x: screenX, y: screenY };
}

/**
 * Update booth markers on map
 */
function updateBoothMarkers() {
    const container = document.getElementById('boothMarkers');
    container.innerHTML = '';

    state.booths.forEach(booth => {
        // Use SLAM coordinates if map is loaded, otherwise use grid
        const screenPos = state.mapTransform
            ? worldToScreenSLAM(booth.pose.x, booth.pose.y)
            : worldToScreen(booth.pose.x, booth.pose.y);
        const marker = document.createElement('div');
        marker.className = `booth-marker ${booth.type === 'charge' ? 'charging' : ''}`;
        marker.style.left = screenPos.x + 'px';
        marker.style.top = screenPos.y + 'px';
        marker.textContent = booth.name;
        marker.onclick = () => selectBoothOnMap(booth);
        container.appendChild(marker);
    });
}

/**
 * Convert world coordinates to screen coordinates
 */
function worldToScreen(worldX, worldY) {
    return {
        x: state.mapOffsetX + worldX * state.mapScale,
        y: state.mapOffsetY - worldY * state.mapScale
    };
}

/**
 * Update robot position on map
 */
function updateRobotPosition(x, y, theta) {
    state.currentPosition = {x, y, theta};

    // Use SLAM coordinates if map is loaded, otherwise use grid
    const screenPos = state.mapTransform
        ? worldToScreenSLAM(x, y)
        : worldToScreen(x, y);
    const marker = document.getElementById('robotMarker');

    marker.style.display = 'block';
    marker.style.left = screenPos.x + 'px';
    marker.style.top = screenPos.y + 'px';

    const heading = marker.querySelector('.robot-heading');
    heading.style.transform = `rotate(${theta}rad)`;

    // Update position display
    document.getElementById('posX').textContent = x.toFixed(2) + ' m';
    document.getElementById('posY').textContent = y.toFixed(2) + ' m';
    document.getElementById('posTheta').textContent = (theta * 180 / Math.PI).toFixed(1) + '°';
}

/**
 * Load booths from AMR
 */
async function loadBooths() {
    try {
        const response = await fetch(`${state.baseURL}/reeman/position`);
        const data = await response.json();

        state.booths = data.waypoints || [];

        // Update booth lists
        updateAvailableBooths();
        updateBoothMarkers();

        log('Booths', `Loaded ${state.booths.length} booths`, 'success');
    } catch (error) {
        log('Booths', `Failed to load booths: ${error.message}`, 'error');
    }
}

/**
 * Update available booths list
 */
function updateAvailableBooths() {
    const container = document.getElementById('availableBooths');
    container.innerHTML = '';

    state.booths.forEach(booth => {
        const item = document.createElement('div');
        item.className = 'booth-item';
        item.innerHTML = `
            <div style="font-weight: bold;">${booth.name}</div>
            <div style="font-size: 12px; color: #95a5a6;">
                Type: ${booth.type} | Position: (${booth.pose.x.toFixed(2)}, ${booth.pose.y.toFixed(2)})
            </div>
        `;
        item.onclick = () => addToSchedule(booth);
        container.appendChild(item);
    });
}

/**
 * Add booth to schedule
 */
function addToSchedule(booth) {
    if (!state.schedule.find(b => b.name === booth.name)) {
        state.schedule.push(booth);
        updateScheduleDisplay();
        log('Schedule', `Added ${booth.name} to schedule`, 'success');
    }
}

/**
 * Update schedule display
 */
function updateScheduleDisplay() {
    const container = document.getElementById('tourSchedule');

    if (state.schedule.length === 0) {
        container.innerHTML = '<p style="text-align: center; color: #95a5a6; padding: 50px 0;">Click booths to add them to the tour schedule</p>';
        return;
    }

    container.innerHTML = '';

    state.schedule.forEach((booth, index) => {
        const item = document.createElement('div');
        item.className = 'schedule-item';
        item.innerHTML = `
            <div style="display: flex; align-items: center; gap: 10px;">
                <div class="schedule-number">${index + 1}</div>
                <div>
                    <div style="font-weight: bold;">${booth.name}</div>
                    <div style="font-size: 12px; color: #95a5a6;">${booth.type}</div>
                </div>
            </div>
            <div class="schedule-controls">
                <button class="btn btn-small btn-primary" onclick="moveUp(${index})" ${index === 0 ? 'disabled' : ''}>↑</button>
                <button class="btn btn-small btn-primary" onclick="moveDown(${index})" ${index === state.schedule.length - 1 ? 'disabled' : ''}>↓</button>
                <button class="btn btn-small btn-danger" onclick="removeFromSchedule(${index})">✕</button>
            </div>
        `;
        container.appendChild(item);
    });
}

/**
 * Move booth up in schedule
 */
function moveUp(index) {
    if (index > 0) {
        [state.schedule[index], state.schedule[index - 1]] = [state.schedule[index - 1], state.schedule[index]];
        updateScheduleDisplay();
    }
}

/**
 * Move booth down in schedule
 */
function moveDown(index) {
    if (index < state.schedule.length - 1) {
        [state.schedule[index], state.schedule[index + 1]] = [state.schedule[index + 1], state.schedule[index]];
        updateScheduleDisplay();
    }
}

/**
 * Remove booth from schedule
 */
function removeFromSchedule(index) {
    state.schedule.splice(index, 1);
    updateScheduleDisplay();
}

/**
 * Clear schedule
 */
function clearSchedule() {
    if (confirm('Clear entire schedule?')) {
        state.schedule = [];
        updateScheduleDisplay();
        log('Schedule', 'Schedule cleared', 'info');
    }
}

/**
 * Select booth on map
 */
function selectBoothOnMap(booth) {
    state.selectedBooth = booth;

    // Show booth details
    document.getElementById('boothDetails').style.display = 'block';
    document.getElementById('detailName').textContent = booth.name;
    document.getElementById('detailType').textContent = booth.type;
    document.getElementById('detailX').textContent = booth.pose.x.toFixed(3) + ' m';
    document.getElementById('detailY').textContent = booth.pose.y.toFixed(3) + ' m';
    document.getElementById('detailTheta').textContent = (booth.pose.theta * 180 / Math.PI).toFixed(1) + '°';

    log('Selection', `Selected booth: ${booth.name}`, 'info');
}

/**
 * Navigate to selected booth
 */
async function navigateToSelected() {
    if (!state.selectedBooth) return;

    try {
        const response = await fetch(`${state.baseURL}/cmd/nav_name`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({point: state.selectedBooth.name})
        });

        const result = await response.json();

        if (result.status === 'success') {
            log('Navigation', `Navigating to ${state.selectedBooth.name}`, 'success');
        } else {
            log('Navigation', `Failed to navigate to ${state.selectedBooth.name}`, 'error');
        }
    } catch (error) {
        log('Navigation', `Error: ${error.message}`, 'error');
    }
}

/**
 * Save current position as booth position
 */
async function saveCurrentPosition() {
    if (!state.selectedBooth) return;

    try {
        const pose = state.currentPosition;

        const response = await fetch(`${state.baseURL}/cmd/position`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                name: state.selectedBooth.name,
                type: state.selectedBooth.type,
                pose: {
                    x: pose.x,
                    y: pose.y,
                    theta: pose.theta
                }
            })
        });

        const result = await response.json();
        log('Booth', `Updated position for ${state.selectedBooth.name}`, 'success');

        await loadBooths();
    } catch (error) {
        log('Booth', `Error updating position: ${error.message}`, 'error');
    }
}

/**
 * Jog movement controls
 */
async function jogMove(direction) {
    const distance = parseInt(document.getElementById('jogDistance').value);
    const speed = parseFloat(document.getElementById('jogSpeed').value);

    const payload = {
        distance: distance,
        direction: direction === 'forward' ? 1 : 0,
        speed: speed
    };

    try {
        const response = await fetch(`${state.baseURL}/cmd/move`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });

        const result = await response.json();
        log('Jog', `Move ${direction} ${distance}cm`, 'success');
    } catch (error) {
        log('Jog', `Error: ${error.message}`, 'error');
    }
}

/**
 * Jog turn controls
 */
async function jogTurn(direction) {
    const angle = parseInt(document.getElementById('jogAngle').value);
    const speed = parseFloat(document.getElementById('jogTurnSpeed').value);

    const payload = {
        direction: direction === 'left' ? 1 : 0,
        angle: angle,
        speed: speed
    };

    try {
        const response = await fetch(`${state.baseURL}/cmd/turn`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });

        const result = await response.json();
        log('Jog', `Turn ${direction} ${angle}°`, 'success');
    } catch (error) {
        log('Jog', `Error: ${error.message}`, 'error');
    }
}

/**
 * Emergency stop
 */
async function emergencyStop() {
    try {
        const response = await fetch(`${state.baseURL}/cmd/speed`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({vx: 0, vth: 0})
        });

        log('Emergency', 'EMERGENCY STOP activated', 'error');
    } catch (error) {
        log('Emergency', `Error: ${error.message}`, 'error');
    }
}

/**
 * Play sound announcement
 */
function playSound(soundId) {
    log('Sound', `Playing ${soundId}`, 'info');

    // Use Web Speech API for TTS
    const messages = {
        'welcome': 'Welcome to the exhibition. Please follow me for a guided tour.',
        'booth_A': 'Welcome to Booth A. This exhibition features ancient artifacts from 3000 BCE.',
        'booth_B': 'This is Booth B, our Modern Art Gallery with works from the 20th century.',
        'booth_C': 'Welcome to Booth C, showcasing the latest innovations in robotics and AI.',
        'goodbye': 'Thank you for visiting. Have a wonderful day!'
    };

    const text = messages[soundId] || 'Test announcement';
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = 0.9;
    utterance.pitch = 1.0;
    utterance.volume = 1.0;

    window.speechSynthesis.speak(utterance);
}

/**
 * Stop sound
 */
function stopSound() {
    window.speechSynthesis.cancel();
    log('Sound', 'Sound stopped', 'info');
}

/**
 * Start automated tour
 */
async function startTour() {
    if (state.schedule.length === 0) {
        alert('Please add booths to the schedule first');
        return;
    }

    state.tourActive = true;
    state.tourPaused = false;
    state.currentTourIndex = 0;

    document.getElementById('tourStatus').textContent = 'Running';
    document.getElementById('tourStatus').style.color = '#2ecc71';

    log('Tour', 'Starting automated tour', 'success');

    executeTourStep();
}

/**
 * Execute tour step
 */
async function executeTourStep() {
    if (!state.tourActive || state.tourPaused) return;
    if (state.currentTourIndex >= state.schedule.length) {
        stopTour();
        return;
    }

    const booth = state.schedule[state.currentTourIndex];

    // Update UI
    document.getElementById('tourProgress').textContent = `${state.currentTourIndex + 1}/${state.schedule.length}`;
    const progress = ((state.currentTourIndex + 1) / state.schedule.length) * 100;
    document.getElementById('tourProgressBar').style.width = progress + '%';
    document.getElementById('tourProgressBar').textContent = Math.round(progress) + '%';
    document.getElementById('currentBoothDisplay').textContent = `Navigating to: ${booth.name}`;

    log('Tour', `Step ${state.currentTourIndex + 1}: ${booth.name}`, 'info');

    // Navigate to booth
    try {
        await fetch(`${state.baseURL}/cmd/nav_name`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({point: booth.name})
        });

        // Wait for arrival
        await waitForArrival(booth.name);

        // Play announcement
        playSound(booth.name);

        // Wait 10 seconds
        await new Promise(resolve => setTimeout(resolve, 10000));

        // Next step
        state.currentTourIndex++;
        executeTourStep();

    } catch (error) {
        log('Tour', `Error at ${booth.name}: ${error.message}`, 'error');
        stopTour();
    }
}

/**
 * Wait for arrival at destination
 */
async function waitForArrival(targetBooth, timeout = 120000) {
    const startTime = Date.now();

    while (Date.now() - startTime < timeout) {
        try {
            const response = await fetch(`${state.baseURL}/reeman/nav_status`);
            const status = await response.json();

            if (status.res === 3) {
                if (status.reason === 0) {
                    log('Tour', `Arrived at ${targetBooth}`, 'success');
                    return true;
                } else {
                    log('Tour', `Failed to reach ${targetBooth}`, 'error');
                    return false;
                }
            }

            document.getElementById('currentBoothDisplay').textContent =
                `${targetBooth} - Distance: ${status.dist ? status.dist.toFixed(2) : '?'}m`;

            await new Promise(resolve => setTimeout(resolve, 2000));

        } catch (error) {
            console.error('Error checking arrival:', error);
        }
    }

    log('Tour', `Timeout waiting for ${targetBooth}`, 'error');
    return false;
}

/**
 * Pause tour
 */
function pauseTour() {
    state.tourPaused = !state.tourPaused;

    if (state.tourPaused) {
        document.getElementById('tourStatus').textContent = 'Paused';
        document.getElementById('tourStatus').style.color = '#f39c12';
        log('Tour', 'Tour paused', 'info');
    } else {
        document.getElementById('tourStatus').textContent = 'Running';
        document.getElementById('tourStatus').style.color = '#2ecc71';
        log('Tour', 'Tour resumed', 'info');
        executeTourStep();
    }
}

/**
 * Stop tour
 */
async function stopTour() {
    state.tourActive = false;
    state.tourPaused = false;
    state.currentTourIndex = 0;

    document.getElementById('tourStatus').textContent = 'Not Started';
    document.getElementById('tourStatus').style.color = '#95a5a6';
    document.getElementById('tourProgress').textContent = '0/0';
    document.getElementById('tourProgressBar').style.width = '0%';
    document.getElementById('tourProgressBar').textContent = '0%';
    document.getElementById('currentBoothDisplay').textContent = 'No active tour';

    // Cancel current navigation
    await fetch(`${state.baseURL}/cmd/cancel_goal`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({})
    });

    log('Tour', 'Tour stopped', 'info');
}

/**
 * Check connection to AMR
 */
async function checkConnection() {
    try {
        const response = await fetch(`${state.baseURL}/reeman/current_version`);
        const data = await response.json();

        state.connected = true;
        document.getElementById('connectionStatus').classList.add('online');
        document.getElementById('connectionText').textContent = 'Connected';

        log('Connection', 'Connected to AMR', 'success');
    } catch (error) {
        state.connected = false;
        document.getElementById('connectionStatus').classList.remove('online');
        document.getElementById('connectionText').textContent = 'Disconnected';

        log('Connection', 'Connection failed', 'error');
    }
}

/**
 * Update system status
 */
async function updateSystemStatus() {
    try {
        // Get version
        const version = await fetch(`${state.baseURL}/reeman/current_version`).then(r => r.json());
        document.getElementById('navVersion').textContent = version.version || '--';

        // Get hostname
        const hostname = await fetch(`${state.baseURL}/reeman/hostname`).then(r => r.json());
        document.getElementById('hostname').textContent = hostname.hostname || '--';

        // Get mode
        const mode = await fetch(`${state.baseURL}/reeman/get_mode`).then(r => r.json());
        document.getElementById('currentMode').textContent = mode.mode === 2 ? 'Navigation' : 'Mapping';

        // Get battery status
        const battery = await fetch(`${state.baseURL}/reeman/base_encode`).then(r => r.json());
        document.getElementById('batteryLevel').textContent = battery.battery || '--';
        document.getElementById('batteryStatus').textContent = battery.battery + '%';
        document.getElementById('chargeStatus').textContent = battery.chargeFlag === 1 ? 'Not Charging' : 'Charging';
        document.getElementById('emergencyStatus').textContent = battery.emergencyButton === 1 ? 'Released' : 'PRESSED';
        document.getElementById('emergencyStatus').style.color = battery.emergencyButton === 1 ? '#2ecc71' : '#e74c3c';

        // Get current map
        const map = await fetch(`${state.baseURL}/reeman/current_map`).then(r => r.json());
        document.getElementById('currentMap').textContent = map.alias || map.name || '--';

        // Get navigation status
        const navStatus = await fetch(`${state.baseURL}/reeman/nav_status`).then(r => r.json());
        const stateNames = {1: 'Navigating', 3: 'Complete', 4: 'Cancelled', 6: 'Idle'};
        document.getElementById('navState').textContent = stateNames[navStatus.res] || 'Unknown';
        document.getElementById('navGoal').textContent = navStatus.goal || '--';
        document.getElementById('navDist').textContent = navStatus.dist ? navStatus.dist.toFixed(2) + 'm' : '--';
        document.getElementById('navMileage').textContent = navStatus.mileage ? navStatus.mileage.toFixed(2) + 'm' : '--';

    } catch (error) {
        console.error('Status update error:', error);
    }
}

/**
 * Update position
 */
async function updatePosition() {
    try {
        const pose = await fetch(`${state.baseURL}/reeman/pose`).then(r => r.json());
        updateRobotPosition(pose.x, pose.y, pose.theta);

        const speed = await fetch(`${state.baseURL}/reeman/speed`).then(r => r.json());
        document.getElementById('speedValue').textContent = Math.abs(speed.vx).toFixed(2) + ' m/s';

    } catch (error) {
        console.error('Position update error:', error);
    }
}

/**
 * Start status update loop
 */
function startStatusUpdates() {
    // Update position every second
    setInterval(updatePosition, 1000);

    // Update status every 3 seconds
    setInterval(updateSystemStatus, 3000);

    // Check connection every 10 seconds
    setInterval(checkConnection, 10000);
}

/**
 * Logging function
 */
function log(category, message, type = 'info') {
    const timestamp = new Date().toLocaleTimeString();

    // Tour log
    const tourLog = document.getElementById('tourLog');
    if (tourLog) {
        const entry = document.createElement('div');
        entry.className = `log-entry ${type}`;
        entry.innerHTML = `<span class="timestamp">[${timestamp}]</span> [${category}] ${message}`;
        tourLog.insertBefore(entry, tourLog.firstChild);

        while (tourLog.children.length > 50) {
            tourLog.removeChild(tourLog.lastChild);
        }
    }

    // System log
    const systemLog = document.getElementById('systemLog');
    if (systemLog) {
        const entry = document.createElement('div');
        entry.className = `log-entry ${type}`;
        entry.innerHTML = `<span class="timestamp">[${timestamp}]</span> [${category}] ${message}`;
        systemLog.insertBefore(entry, systemLog.firstChild);

        while (systemLog.children.length > 50) {
            systemLog.removeChild(systemLog.lastChild);
        }
    }

    console.log(`[${timestamp}] [${category}] ${message}`);
}

/**
 * Handle keyboard shortcuts
 */
function handleKeyboard(e) {
    // Only in jog panel
    if (document.getElementById('jog').classList.contains('active')) {
        switch(e.key) {
            case 'ArrowUp':
            case 'w':
                e.preventDefault();
                jogMove('forward');
                break;
            case 'ArrowDown':
            case 's':
                e.preventDefault();
                jogMove('backward');
                break;
            case 'ArrowLeft':
            case 'a':
                e.preventDefault();
                jogTurn('left');
                break;
            case 'ArrowRight':
            case 'd':
                e.preventDefault();
                jogTurn('right');
                break;
            case ' ':
                e.preventDefault();
                emergencyStop();
                break;
        }
    }
}

/**
 * Add new booth
 */
async function addNewBooth() {
    const name = document.getElementById('newBoothName').value;
    const type = document.getElementById('newBoothType').value;

    if (!name) {
        alert('Please enter a booth name');
        return;
    }

    try {
        const pose = state.currentPosition;

        const response = await fetch(`${state.baseURL}/cmd/position`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                name: name,
                type: type,
                pose: {
                    x: pose.x,
                    y: pose.y,
                    theta: pose.theta
                }
            })
        });

        log('Booth', `Added new booth: ${name}`, 'success');

        document.getElementById('newBoothName').value = '';

        await loadBooths();

    } catch (error) {
        log('Booth', `Error adding booth: ${error.message}`, 'error');
    }
}

/**
 * Update max speed
 */
async function updateMaxSpeed() {
    const speed = parseFloat(document.getElementById('maxSpeed').value);

    try {
        await fetch(`${state.baseURL}/cmd/max_speed`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({speed: speed})
        });

        log('Settings', `Max speed updated to ${speed} m/s`, 'success');
    } catch (error) {
        log('Settings', `Error updating speed: ${error.message}`, 'error');
    }
}

/**
 * Update connection
 */
function updateConnection() {
    state.amrIP = document.getElementById('amrIP').value;
    log('Settings', `AMR IP updated to ${state.amrIP}`, 'info');
    checkConnection();
}
