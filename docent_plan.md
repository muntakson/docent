# DOCENT AMR - OPERATIONAL PLAN
**Museum/Exhibition Guide Robot Operation Manual**

**Document Version:** 1.0
**Date:** November 30, 2025
**AMR Model:** Reeman SLAM Navigation System
**Navigation Version:** RSNF1-v5.1.11_06
**Server IP:** 192.168.0.5
**Web Interface:** https://docentbot.aiedus.org

---

## TABLE OF CONTENTS

1. [System Overview](#1-system-overview)
2. [Pre-Operation Setup](#2-pre-operation-setup)
3. [Navigation Operations](#3-navigation-operations)
4. [Position Tracking](#4-position-tracking)
5. [Map Management](#5-map-management)
6. [Voice Announcement System](#6-voice-announcement-system)
7. [Web UI Integration](#7-web-ui-integration)
8. [Complete Workflow Example](#8-complete-workflow-example)
9. [Troubleshooting](#9-troubleshooting)
10. [API Reference](#10-api-reference)

---

## 1. SYSTEM OVERVIEW

### 1.1 Docent AMR Purpose
The Docent AMR serves as an autonomous museum/exhibition guide robot that:
- Navigates between exhibition booths automatically
- Provides voice explanations at each booth
- Displays real-time position on a web-based map
- Allows remote monitoring and control

### 1.2 System Architecture
```
┌─────────────────────────────────────────────┐
│  Exhibition Visitor                         │
│  (Web Browser: docentbot.aiedus.org)        │
└─────────────────┬───────────────────────────┘
                  │ HTTPS
┌─────────────────▼───────────────────────────┐
│  Control Server (192.168.0.34)              │
│  - Web UI (Map Display + Controls)          │
│  - Proxy Server (Port 5020)                 │
│  - Voice Synthesis System                   │
└─────────────────┬───────────────────────────┘
                  │ HTTP API
┌─────────────────▼───────────────────────────┐
│  AMR Navigation System (192.168.0.5)        │
│  - SLAM Navigation                          │
│  - Position Tracking                        │
│  - Map Generation                           │
│  - Movement Control                         │
└─────────────────────────────────────────────┘
```

---

## 2. PRE-OPERATION SETUP

### 2.1 Calibrate Exhibition Booths

Before operating the Docent AMR, you must calibrate waypoints for each exhibition booth.

#### Step 1: Navigate Robot to Booth Location
Manually drive the robot to each booth location using the jogging controls at https://docentbot.aiedus.org

#### Step 2: Get Current Position
```bash
curl -s http://192.168.0.5/reeman/pose
```
**Response:**
```json
{"x": -2.50, "y": 3.45, "theta": 1.57}
```

#### Step 3: Save Booth Position
```bash
curl -X POST http://192.168.0.5/cmd/position \
  -H "Content-Type: application/json" \
  -d '{
    "name": "booth_A",
    "type": "delivery",
    "pose": {
      "x": -2.50,
      "y": 3.45,
      "theta": 1.57
    }
  }'
```

**Booth Types:**
- `delivery` - Exhibition booth / delivery point
- `normal` - Route waypoint
- `production` - Production/preparation area
- `charge` - Charging station

#### Step 4: Verify Saved Booths
```bash
curl -s http://192.168.0.5/reeman/position
```
**Response:**
```json
{
  "waypoints": [
    {"name": "booth_A", "type": "delivery", "pose": {"x": -2.50, "y": 3.45, "theta": 1.57}},
    {"name": "booth_B", "type": "delivery", "pose": {"x": 1.20, "y": -0.80, "theta": 3.14}},
    {"name": "booth_C", "type": "delivery", "pose": {"x": 4.00, "y": 2.10, "theta": 0.00}},
    {"name": "charging_station", "type": "charge", "pose": {"x": -0.87, "y": 1.16, "theta": -0.08}}
  ]
}
```

### 2.2 Create Booth Information Database

Create a JSON file with booth descriptions and audio content:

**File:** `/var/www/docent/booth_info.json`
```json
{
  "booths": [
    {
      "id": "booth_A",
      "name": "Ancient Artifacts",
      "description": "Welcome to the Ancient Artifacts exhibition. Here you can see pottery and tools from 3000 BCE.",
      "audio_file": "/var/www/docent/audio/booth_A.mp3",
      "language": "en"
    },
    {
      "id": "booth_B",
      "name": "Modern Art Gallery",
      "description": "This is our Modern Art Gallery featuring works from the 20th century.",
      "audio_file": "/var/www/docent/audio/booth_B.mp3",
      "language": "en"
    },
    {
      "id": "booth_C",
      "name": "Technology Innovation",
      "description": "Discover the latest innovations in robotics and artificial intelligence.",
      "audio_file": "/var/www/docent/audio/booth_C.mp3",
      "language": "en"
    }
  ]
}
```

---

## 3. NAVIGATION OPERATIONS

### 3.1 Move from Booth A to Booth B

#### Method 1: Using Named Waypoint (Recommended)

**Step 1: Send Navigation Command**
```bash
curl -X POST http://192.168.0.5/cmd/nav_name \
  -H "Content-Type: application/json" \
  -d '{"point": "booth_B"}'
```

**Response:**
```json
{"status": "success"}
```

#### Method 2: Using Coordinates
```bash
curl -X POST http://192.168.0.5/cmd/nav \
  -H "Content-Type: application/json" \
  -d '{
    "x": 1.20,
    "y": -0.80,
    "theta": 3.14
  }'
```

### 3.2 Check Navigation Status

**Poll navigation status every 1-2 seconds:**
```bash
curl -s http://192.168.0.5/reeman/nav_status
```

**Response Examples:**

**1. Navigation Started:**
```json
{
  "res": 1,
  "reason": 0,
  "goal": "booth_B",
  "dist": 3.5,
  "mileage": 0
}
```
- `res: 1` = Navigation started
- `dist: 3.5` = 3.5 meters remaining to target

**2. Navigation in Progress:**
```json
{
  "res": 1,
  "reason": 0,
  "goal": "booth_B",
  "dist": 1.2,
  "mileage": 2.3
}
```
- `dist: 1.2` = 1.2 meters remaining
- `mileage: 2.3` = Traveled 2.3 meters so far

**3. Navigation Completed Successfully:**
```json
{
  "res": 3,
  "reason": 0,
  "goal": "booth_B",
  "dist": 0,
  "mileage": 3.5
}
```
- `res: 3` = Navigation result
- `reason: 0` = Success
- `dist: 0` = Arrived at target

**4. Navigation Failed:**
```json
{
  "res": 3,
  "reason": 1,
  "goal": "booth_B",
  "dist": 2.1,
  "mileage": 1.4
}
```
- `reason: 1` = Navigation failed

### 3.3 Navigation Status Codes

**State (res) Values:**
- `1` = Navigation started
- `3` = Navigation completed (check reason for success/failure)
- `4` = Navigation manually cancelled
- `6` = Normal/idle state

**Reason Codes (when res=6):**
- `0` = Success
- `-2` = Emergency stop switch pressed
- `1` = Connecting to charging pile
- `2` = Emergency stop pressed
- `3` = Adapter charging
- `4` = Target point not found
- `6` = Positioning abnormality

**Reason Codes (when res=3):**
- `0` = Navigation successful
- `1` = Navigation failed

### 3.4 Check If AMR Arrived at Booth B

**Method 1: Monitor Navigation Status (Recommended)**
```python
import requests
import time

def wait_for_arrival(target_booth, timeout=60):
    """
    Wait for AMR to arrive at target booth
    Returns: True if arrived, False if failed/timeout
    """
    start_time = time.time()

    while time.time() - start_time < timeout:
        response = requests.get('http://192.168.0.5/reeman/nav_status')
        status = response.json()

        if status['res'] == 3:  # Navigation completed
            if status['reason'] == 0:  # Success
                print(f"✅ Arrived at {status['goal']}")
                return True
            else:
                print(f"❌ Navigation failed: reason={status['reason']}")
                return False

        elif status['res'] == 1:  # In progress
            print(f"📍 Distance remaining: {status['dist']:.2f}m")

        time.sleep(1)

    print(f"⏱️ Timeout waiting for arrival")
    return False

# Usage
if wait_for_arrival("booth_B", timeout=60):
    print("Ready to provide exhibition explanation!")
```

**Method 2: Distance Threshold**
```python
def check_arrival_by_distance(target_booth, threshold=0.3):
    """
    Check if AMR is within threshold distance of target
    """
    status = requests.get('http://192.168.0.5/reeman/nav_status').json()

    if status['goal'] == target_booth and status['dist'] < threshold:
        return True
    return False
```

**Method 3: Position-Based Verification**
```python
import math

def check_arrival_by_position(target_x, target_y, threshold=0.5):
    """
    Check if current position matches target position
    """
    pose = requests.get('http://192.168.0.5/reeman/pose').json()

    distance = math.sqrt(
        (pose['x'] - target_x)**2 +
        (pose['y'] - target_y)**2
    )

    return distance < threshold
```

---

## 4. POSITION TRACKING

### 4.1 Fetch Current Position of AMR

**API Call:**
```bash
curl -s http://192.168.0.5/reeman/pose
```

**Response:**
```json
{
  "x": 1.2034567,
  "y": -0.8123456,
  "theta": 3.1415926
}
```

**Parameters:**
- `x` = X coordinate in meters
- `y` = Y coordinate in meters
- `theta` = Orientation in radians (0 to 2π)
  - 0 rad = Facing East
  - π/2 rad = Facing North
  - π rad = Facing West
  - 3π/2 rad = Facing South

### 4.2 Convert Theta to Degrees
```python
import math

def radians_to_degrees(theta):
    """Convert radians to degrees"""
    return (theta * 180 / math.pi) % 360

# Example
theta = 3.1415926
degrees = radians_to_degrees(theta)
print(f"Orientation: {degrees}°")  # Output: 180°
```

### 4.3 Real-Time Position Tracking

**JavaScript Example (for Web UI):**
```javascript
// Update position every second
setInterval(async () => {
    const response = await fetch('/api/reeman/pose');
    const position = await response.json();

    console.log(`Position: (${position.x.toFixed(2)}, ${position.y.toFixed(2)})`);
    console.log(`Heading: ${(position.theta * 180 / Math.PI).toFixed(1)}°`);

    // Update map marker
    updateRobotMarker(position.x, position.y, position.theta);
}, 1000);
```

---

## 5. MAP MANAGEMENT

### 5.1 Fetch Map Generated by AMR's SLAM

#### Get Current Map Information
```bash
curl -s http://192.168.0.5/reeman/current_map
```

**Response:**
```json
{
  "name": "7ec45159fd8c09335d244da333e4ad78",
  "alias": "museum_floor_1"
}
```

#### Get Map Data
```bash
curl -s http://192.168.0.5/reeman/map
```

**Response:** (Map data structure)
```json
{
  "width": 384,
  "height": 384,
  "resolution": 0.05,
  "origin": {
    "x": -9.6,
    "y": -9.6,
    "theta": 0
  },
  "data": [0, 0, 0, 100, 100, ...],
  "info": {
    "map_load_time": "2025-11-30 10:30:00"
  }
}
```

**Map Data Values:**
- `0` = Free space (white)
- `100` = Obstacle (black)
- `-1` = Unknown (gray)

#### Download Map File
```bash
curl -X POST http://192.168.0.5/download/export_map \
  -H "Content-Type: application/json" \
  -d '{"name": "7ec45159fd8c09335d244da333e4ad78"}' \
  --output museum_map.tar.gz
```

### 5.2 Get All Available Maps
```bash
curl -s http://192.168.0.5/reeman/history_map
```

**Response:**
```json
{
  "maps": [
    {
      "name": "7ec45159fd8c09335d244da333e4ad78",
      "alias": "museum_floor_1",
      "create_time": "2025-11-30 09:00:00"
    },
    {
      "name": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
      "alias": "museum_floor_2",
      "create_time": "2025-11-29 14:30:00"
    }
  ]
}
```

---

## 6. VOICE ANNOUNCEMENT SYSTEM

### 6.1 Make Voice Announcement When Arrived at Booth B

#### Option 1: Using Pre-recorded Audio Files

**Setup:**
1. Create audio files for each booth (MP3 format)
2. Store in `/var/www/docent/audio/`
3. Use Python script to play audio via robot's speakers

**Python Script:**
```python
import requests
import time
import pygame
import json

def load_booth_info():
    """Load booth information from JSON"""
    with open('/var/www/docent/booth_info.json', 'r') as f:
        return json.load(f)

def navigate_and_announce(booth_id):
    """Navigate to booth and play announcement"""

    # 1. Load booth information
    booth_data = load_booth_info()
    booth = next((b for b in booth_data['booths'] if b['id'] == booth_id), None)

    if not booth:
        print(f"❌ Booth {booth_id} not found")
        return False

    # 2. Send navigation command
    print(f"🚀 Navigating to {booth['name']}...")
    response = requests.post(
        'http://192.168.0.5/cmd/nav_name',
        json={'point': booth_id}
    )

    if response.json().get('status') != 'success':
        print("❌ Navigation command failed")
        return False

    # 3. Wait for arrival
    while True:
        status = requests.get('http://192.168.0.5/reeman/nav_status').json()

        if status['res'] == 3:  # Navigation completed
            if status['reason'] == 0:  # Success
                print(f"✅ Arrived at {booth['name']}")
                break
            else:
                print(f"❌ Navigation failed")
                return False

        print(f"📍 Distance: {status['dist']:.2f}m")
        time.sleep(1)

    # 4. Play audio announcement
    print(f"🔊 Playing announcement...")
    pygame.mixer.init()
    pygame.mixer.music.load(booth['audio_file'])
    pygame.mixer.music.play()

    while pygame.mixer.music.get_busy():
        time.sleep(0.1)

    print(f"✅ Announcement complete")
    return True

# Usage
navigate_and_announce('booth_B')
```

#### Option 2: Using Text-to-Speech (TTS)

**Install TTS Engine:**
```bash
pip3 install gtts pydub
```

**Python TTS Script:**
```python
from gtts import gTTS
import os
import requests
import time

def text_to_speech_announcement(booth_id, text, language='en'):
    """
    Generate and play TTS announcement
    """
    # Generate audio file
    tts = gTTS(text=text, lang=language, slow=False)
    audio_file = f"/tmp/{booth_id}_announcement.mp3"
    tts.save(audio_file)

    # Play audio (using system command)
    os.system(f"mpg123 {audio_file}")

    # Clean up
    os.remove(audio_file)

def navigate_with_tts(booth_id, announcement_text):
    """Navigate and use TTS announcement"""

    # 1. Navigate to booth
    requests.post(
        'http://192.168.0.5/cmd/nav_name',
        json={'point': booth_id}
    )

    # 2. Wait for arrival
    while True:
        status = requests.get('http://192.168.0.5/reeman/nav_status').json()

        if status['res'] == 3 and status['reason'] == 0:
            break
        time.sleep(1)

    # 3. Make announcement
    print(f"🔊 Announcement: {announcement_text}")
    text_to_speech_announcement(booth_id, announcement_text)

# Usage
announcement = "Welcome to the Modern Art Gallery. Here you can see works from famous 20th century artists including Picasso and Dali."
navigate_with_tts('booth_B', announcement)
```

#### Option 3: Using Web Speech API (Browser-based)

**JavaScript Example:**
```javascript
async function navigateAndAnnounce(boothId, announcementText) {
    // 1. Navigate to booth
    await fetch('/api/cmd/nav_name', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({point: boothId})
    });

    // 2. Wait for arrival
    const arrived = await waitForArrival(boothId);

    if (!arrived) {
        console.error('Navigation failed');
        return;
    }

    // 3. Make announcement using Web Speech API
    const utterance = new SpeechSynthesisUtterance(announcementText);
    utterance.lang = 'en-US';
    utterance.rate = 0.9;
    utterance.pitch = 1.0;
    utterance.volume = 1.0;

    window.speechSynthesis.speak(utterance);
}

async function waitForArrival(targetBooth) {
    while (true) {
        const response = await fetch('/api/reeman/nav_status');
        const status = await response.json();

        if (status.res === 3) {
            return status.reason === 0;
        }

        await new Promise(resolve => setTimeout(resolve, 1000));
    }
}

// Usage
navigateAndAnnounce(
    'booth_B',
    'Welcome to the Modern Art Gallery featuring works from the 20th century.'
);
```

---

## 7. WEB UI INTEGRATION

### 7.1 Display Map on Web UI

#### Step 1: Create Map Canvas (HTML)
```html
<!DOCTYPE html>
<html>
<head>
    <title>Docent AMR - Live Map</title>
    <style>
        #mapContainer {
            width: 800px;
            height: 600px;
            border: 2px solid #333;
            position: relative;
            background: #f0f0f0;
        }

        #mapCanvas {
            width: 100%;
            height: 100%;
        }

        .robot-marker {
            position: absolute;
            width: 30px;
            height: 30px;
            background: #667eea;
            border-radius: 50%;
            transform: translate(-50%, -50%);
            transition: all 0.3s ease;
        }

        .robot-heading {
            width: 20px;
            height: 2px;
            background: #fff;
            position: absolute;
            top: 50%;
            left: 50%;
            transform-origin: left center;
        }

        .booth-marker {
            position: absolute;
            width: 20px;
            height: 20px;
            background: #ff6b6b;
            border-radius: 50%;
            transform: translate(-50%, -50%);
        }

        .booth-label {
            position: absolute;
            background: white;
            padding: 2px 6px;
            border-radius: 3px;
            font-size: 12px;
            white-space: nowrap;
        }
    </style>
</head>
<body>
    <div id="mapContainer">
        <canvas id="mapCanvas"></canvas>
        <div id="robotMarker" class="robot-marker">
            <div class="robot-heading"></div>
        </div>
    </div>

    <script src="map-display.js"></script>
</body>
</html>
```

#### Step 2: Map Display JavaScript
```javascript
// File: map-display.js

class AMRMapDisplay {
    constructor(canvasId, containerId) {
        this.canvas = document.getElementById(canvasId);
        this.container = document.getElementById(containerId);
        this.ctx = this.canvas.getContext('2d');

        this.mapData = null;
        this.robotPosition = {x: 0, y: 0, theta: 0};
        this.booths = [];

        this.scale = 50; // pixels per meter
        this.offsetX = 400; // center of canvas
        this.offsetY = 300;

        this.initCanvas();
    }

    initCanvas() {
        const rect = this.container.getBoundingClientRect();
        this.canvas.width = rect.width;
        this.canvas.height = rect.height;
    }

    async loadMap() {
        try {
            const response = await fetch('/api/reeman/map');
            this.mapData = await response.json();
            this.drawMap();
        } catch (error) {
            console.error('Failed to load map:', error);
        }
    }

    async loadBooths() {
        try {
            const response = await fetch('/api/reeman/position');
            const data = await response.json();
            this.booths = data.waypoints || [];
            this.drawBooths();
        } catch (error) {
            console.error('Failed to load booths:', error);
        }
    }

    drawMap() {
        if (!this.mapData) return;

        // Clear canvas
        this.ctx.fillStyle = '#f0f0f0';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        // Draw grid
        this.drawGrid();

        // Draw map data
        const {width, height, resolution, origin, data} = this.mapData;

        for (let y = 0; y < height; y++) {
            for (let x = 0; x < width; x++) {
                const index = y * width + x;
                const value = data[index];

                if (value === 100) {
                    // Obstacle - draw black
                    const worldX = origin.x + x * resolution;
                    const worldY = origin.y + y * resolution;
                    const screenPos = this.worldToScreen(worldX, worldY);

                    this.ctx.fillStyle = '#333';
                    this.ctx.fillRect(
                        screenPos.x,
                        screenPos.y,
                        resolution * this.scale,
                        resolution * this.scale
                    );
                }
            }
        }
    }

    drawGrid() {
        this.ctx.strokeStyle = '#ddd';
        this.ctx.lineWidth = 1;

        // Vertical lines (every meter)
        for (let x = -10; x <= 10; x++) {
            const screenX = this.offsetX + x * this.scale;
            this.ctx.beginPath();
            this.ctx.moveTo(screenX, 0);
            this.ctx.lineTo(screenX, this.canvas.height);
            this.ctx.stroke();
        }

        // Horizontal lines (every meter)
        for (let y = -10; y <= 10; y++) {
            const screenY = this.offsetY + y * this.scale;
            this.ctx.beginPath();
            this.ctx.moveTo(0, screenY);
            this.ctx.lineTo(this.canvas.width, screenY);
            this.ctx.stroke();
        }

        // Draw axes
        this.ctx.strokeStyle = '#999';
        this.ctx.lineWidth = 2;

        // X-axis
        this.ctx.beginPath();
        this.ctx.moveTo(0, this.offsetY);
        this.ctx.lineTo(this.canvas.width, this.offsetY);
        this.ctx.stroke();

        // Y-axis
        this.ctx.beginPath();
        this.ctx.moveTo(this.offsetX, 0);
        this.ctx.lineTo(this.offsetX, this.canvas.height);
        this.ctx.stroke();
    }

    drawBooths() {
        this.booths.forEach(booth => {
            const screenPos = this.worldToScreen(booth.pose.x, booth.pose.y);

            // Draw booth marker
            this.ctx.fillStyle = booth.type === 'charge' ? '#4CAF50' : '#ff6b6b';
            this.ctx.beginPath();
            this.ctx.arc(screenPos.x, screenPos.y, 8, 0, 2 * Math.PI);
            this.ctx.fill();

            // Draw booth label
            this.ctx.fillStyle = '#333';
            this.ctx.font = '12px Arial';
            this.ctx.fillText(
                booth.name,
                screenPos.x + 12,
                screenPos.y + 4
            );
        });
    }

    updateRobotPosition(x, y, theta) {
        this.robotPosition = {x, y, theta};
        this.drawRobot();
    }

    drawRobot() {
        const {x, y, theta} = this.robotPosition;
        const screenPos = this.worldToScreen(x, y);

        // Update DOM marker position
        const marker = document.getElementById('robotMarker');
        marker.style.left = screenPos.x + 'px';
        marker.style.top = screenPos.y + 'px';

        // Update heading indicator
        const heading = marker.querySelector('.robot-heading');
        heading.style.transform = `rotate(${theta}rad)`;
    }

    worldToScreen(worldX, worldY) {
        return {
            x: this.offsetX + worldX * this.scale,
            y: this.offsetY - worldY * this.scale // Invert Y for screen coords
        };
    }

    screenToWorld(screenX, screenY) {
        return {
            x: (screenX - this.offsetX) / this.scale,
            y: -(screenY - this.offsetY) / this.scale
        };
    }

    startPositionTracking() {
        setInterval(async () => {
            try {
                const response = await fetch('/api/reeman/pose');
                const pose = await response.json();
                this.updateRobotPosition(pose.x, pose.y, pose.theta);
            } catch (error) {
                console.error('Position update failed:', error);
            }
        }, 1000);
    }
}

// Initialize map display
const mapDisplay = new AMRMapDisplay('mapCanvas', 'mapContainer');

// Load and display map
mapDisplay.loadMap();
mapDisplay.loadBooths();

// Start real-time position tracking
mapDisplay.startPositionTracking();
```

### 7.2 Display Current Position on Map

The robot position is automatically displayed using the DOM marker that moves with real-time updates.

**Features:**
- Blue circular marker represents the robot
- White line shows heading direction
- Position updates every second
- Smooth transitions between positions

---

## 8. COMPLETE WORKFLOW EXAMPLE

### 8.1 Automated Tour Script

**File:** `/var/www/docent/tour_script.py`

```python
#!/usr/bin/env python3
"""
Docent AMR - Automated Exhibition Tour
"""

import requests
import time
import json
import pygame
from typing import Dict, List

class DocentAMR:
    def __init__(self, amr_ip='192.168.0.5'):
        self.base_url = f'http://{amr_ip}'
        pygame.mixer.init()

    def get_position(self) -> Dict:
        """Get current robot position"""
        response = requests.get(f'{self.base_url}/reeman/pose')
        return response.json()

    def navigate_to_booth(self, booth_id: str) -> bool:
        """Navigate to specified booth"""
        print(f"🚀 Navigating to {booth_id}...")

        response = requests.post(
            f'{self.base_url}/cmd/nav_name',
            json={'point': booth_id}
        )

        return response.json().get('status') == 'success'

    def wait_for_arrival(self, timeout=120) -> bool:
        """Wait for navigation to complete"""
        start_time = time.time()

        while time.time() - start_time < timeout:
            status = requests.get(f'{self.base_url}/reeman/nav_status').json()

            if status['res'] == 3:  # Navigation completed
                if status['reason'] == 0:
                    print(f"✅ Arrived successfully")
                    return True
                else:
                    print(f"❌ Navigation failed: reason={status['reason']}")
                    return False

            elif status['res'] == 1:  # In progress
                print(f"📍 Distance: {status['dist']:.2f}m | Traveled: {status['mileage']:.2f}m")

            time.sleep(2)

        print(f"⏱️ Navigation timeout")
        return False

    def play_announcement(self, audio_file: str):
        """Play audio announcement"""
        print(f"🔊 Playing: {audio_file}")
        pygame.mixer.music.load(audio_file)
        pygame.mixer.music.play()

        while pygame.mixer.music.get_busy():
            time.sleep(0.1)

        print(f"✅ Announcement complete")

    def check_emergency_stop(self) -> bool:
        """Check if emergency stop is pressed"""
        response = requests.get(f'{self.base_url}/reeman/base_encode')
        status = response.json()
        return status['emergencyButton'] == 0  # 0 = pressed

    def get_battery_level(self) -> int:
        """Get current battery level"""
        response = requests.get(f'{self.base_url}/reeman/base_encode')
        return response.json()['battery']

def run_exhibition_tour(booth_sequence: List[str], booth_info_file: str):
    """
    Execute complete exhibition tour

    Args:
        booth_sequence: List of booth IDs in tour order
        booth_info_file: Path to booth information JSON
    """

    # Initialize robot
    robot = DocentAMR()

    # Load booth information
    with open(booth_info_file, 'r') as f:
        booth_data = json.load(f)
    booth_dict = {b['id']: b for b in booth_data['booths']}

    print("="*60)
    print("DOCENT AMR - EXHIBITION TOUR")
    print("="*60)

    # Check battery
    battery = robot.get_battery_level()
    print(f"🔋 Battery: {battery}%")

    if battery < 30:
        print(f"⚠️ Low battery! Returning to charging station...")
        robot.navigate_to_booth('charging_station')
        robot.wait_for_arrival()
        return

    # Check emergency stop
    if robot.check_emergency_stop():
        print(f"🛑 Emergency stop is pressed! Please release it.")
        return

    # Start tour
    for i, booth_id in enumerate(booth_sequence):
        booth = booth_dict.get(booth_id)

        if not booth:
            print(f"❌ Booth {booth_id} not found in database")
            continue

        print(f"\n{'='*60}")
        print(f"STOP {i+1}/{len(booth_sequence)}: {booth['name']}")
        print(f"{'='*60}")

        # Get current position
        position = robot.get_position()
        print(f"📍 Current position: ({position['x']:.2f}, {position['y']:.2f})")

        # Navigate to booth
        if not robot.navigate_to_booth(booth_id):
            print(f"❌ Failed to start navigation to {booth_id}")
            continue

        # Wait for arrival
        if not robot.wait_for_arrival(timeout=120):
            print(f"❌ Failed to reach {booth_id}")
            continue

        # Pause before announcement
        print(f"⏸️ Pausing for 2 seconds...")
        time.sleep(2)

        # Play announcement
        robot.play_announcement(booth['audio_file'])

        # Pause at booth
        print(f"⏸️ Waiting 10 seconds at {booth['name']}...")
        time.sleep(10)

    # Return to home position
    print(f"\n{'='*60}")
    print(f"TOUR COMPLETE - Returning to charging station")
    print(f"{'='*60}")

    robot.navigate_to_booth('charging_station')
    robot.wait_for_arrival()

    print(f"\n✅ Tour complete!")

# Main execution
if __name__ == '__main__':
    # Define tour sequence
    tour_sequence = [
        'booth_A',
        'booth_B',
        'booth_C'
    ]

    # Run tour
    run_exhibition_tour(
        booth_sequence=tour_sequence,
        booth_info_file='/var/www/docent/booth_info.json'
    )
```

### 8.2 Run the Tour

```bash
# Make script executable
chmod +x /var/www/docent/tour_script.py

# Run tour
python3 /var/www/docent/tour_script.py
```

**Output Example:**
```
============================================================
DOCENT AMR - EXHIBITION TOUR
============================================================
🔋 Battery: 94%

============================================================
STOP 1/3: Ancient Artifacts
============================================================
📍 Current position: (0.07, 0.78)
🚀 Navigating to booth_A...
📍 Distance: 3.2m | Traveled: 0.0m
📍 Distance: 2.1m | Traveled: 1.1m
📍 Distance: 0.8m | Traveled: 2.4m
✅ Arrived successfully
⏸️ Pausing for 2 seconds...
🔊 Playing: /var/www/docent/audio/booth_A.mp3
✅ Announcement complete
⏸️ Waiting 10 seconds at Ancient Artifacts...

============================================================
STOP 2/3: Modern Art Gallery
============================================================
...
```

---

## 9. TROUBLESHOOTING

### Problem 1: Robot Not Moving to Booth

**Symptoms:**
- Navigation command returns success but robot doesn't move
- Status shows `res: 6, reason: -2`

**Diagnosis:**
```bash
curl -s http://192.168.0.5/reeman/base_encode | grep emergencyButton
```

**Solution:**
- Release emergency stop button on robot
- Verify: `emergencyButton: 1` (released)

### Problem 2: Booth Not Found

**Symptoms:**
- Navigation returns: `{"status": "Exception"}`

**Diagnosis:**
```bash
curl -s http://192.168.0.5/reeman/position
```

**Solution:**
- Verify booth name exists in waypoints list
- Re-calibrate booth if necessary

### Problem 3: Map Not Displaying

**Symptoms:**
- Map canvas is blank or shows error

**Diagnosis:**
```bash
curl -s http://192.168.0.5/reeman/map
```

**Solution:**
- Check if map is loaded: `curl -s http://192.168.0.5/reeman/current_map`
- Switch to correct map if needed
- Verify CORS headers in nginx config

### Problem 4: Position Not Updating

**Symptoms:**
- Robot marker doesn't move on map

**Diagnosis:**
- Check browser console for JavaScript errors
- Verify proxy server is running: `ps aux | grep proxy_server.py`
- Test position endpoint: `curl -s http://192.168.0.5/reeman/pose`

**Solution:**
- Restart proxy server
- Check nginx configuration
- Verify network connectivity to 192.168.0.5

---

## 10. API REFERENCE

### Navigation APIs

| Endpoint | Method | Purpose | Payload Example |
|----------|--------|---------|-----------------|
| `/cmd/nav_name` | POST | Navigate to named waypoint | `{"point": "booth_A"}` |
| `/cmd/nav` | POST | Navigate to coordinates | `{"x": 1.2, "y": -0.8, "theta": 3.14}` |
| `/cmd/cancel_goal` | POST | Cancel current navigation | `{}` |
| `/reeman/nav_status` | GET | Get navigation status | N/A |

### Position APIs

| Endpoint | Method | Purpose | Response Example |
|----------|--------|---------|------------------|
| `/reeman/pose` | GET | Get current position | `{"x": 1.2, "y": -0.8, "theta": 3.14}` |
| `/reeman/speed` | GET | Get current speed | `{"vx": 0.3, "vth": 0.0}` |

### Map APIs

| Endpoint | Method | Purpose | Response |
|----------|--------|---------|----------|
| `/reeman/map` | GET | Get map data | Map grid data |
| `/reeman/current_map` | GET | Get active map info | `{"name": "...", "alias": "..."}` |
| `/download/export_map` | POST | Download map file | Binary TAR.GZ |

### Waypoint APIs

| Endpoint | Method | Purpose | Payload Example |
|----------|--------|---------|-----------------|
| `/cmd/position` | POST | Add/update waypoint | `{"name": "booth_A", "type": "delivery", "pose": {...}}` |
| `/reeman/position` | GET | Get all waypoints | N/A |
| `/cmd/position` | DELETE | Delete waypoint | `{"name": "booth_A"}` |

---

## CONCLUSION

This operational plan provides complete guidance for operating the Docent AMR system as an exhibition guide robot. The system integrates navigation, position tracking, map visualization, and voice announcements to create an autonomous museum tour experience.

**Key Features Implemented:**
- ✅ Autonomous navigation between exhibition booths
- ✅ Real-time position tracking and display
- ✅ Web-based map visualization
- ✅ Voice announcement system
- ✅ Complete tour automation

**Next Steps:**
1. Record audio announcements for each booth
2. Calibrate all booth positions
3. Test complete tour sequence
4. Deploy web UI with map display
5. Configure backup/charging schedules

For support and updates, refer to:
- AMR API Documentation: `/var/www/docent/AMR_API_ANALYSIS_REPORT.md`
- Web UI: https://docentbot.aiedus.org

---

**Document End**
