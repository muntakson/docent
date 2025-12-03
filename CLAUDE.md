# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Docent is a web-based control interface for a Reeman AMR (Autonomous Mobile Robot) used as an automated tour guide. The system consists of:

- **Web UI** (`index.html`): Single-page application for robot control, map visualization, route management, and SLAM mapping
- **Proxy Server** (`proxy_server.py`): Flask server that forwards API requests to the AMR and provides Google Cloud TTS
- **Android App** (`android/`): Companion DocentBot app (Kotlin) for on-robot display

## Architecture

```
Browser (docent.rongrong.org)
    ↓ HTTPS (Cloudflare)
Nginx (port 443) → static files from /var/www/docent
    ↓ /api/* proxy
Flask Proxy (port 5020)
    ↓ HTTP
AMR Navigation System (192.168.219.42)
```

## Commands

### Start/Restart Proxy Server
```bash
# Using systemd
sudo systemctl restart docent-proxy
sudo systemctl status docent-proxy

# Or manually
python3 /var/www/docent/proxy_server.py
```

### Nginx
```bash
sudo nginx -t                      # Test config
sudo systemctl reload nginx        # Apply changes
```

### Deploy nginx config
```bash
sudo cp nginx-docent.conf /etc/nginx/sites-available/docent.rongrong.org
sudo ln -sf /etc/nginx/sites-available/docent.rongrong.org /etc/nginx/sites-enabled/
sudo systemctl reload nginx
```

### Install proxy service
```bash
sudo cp docent-proxy.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable docent-proxy
sudo systemctl start docent-proxy
```

## Key AMR API Endpoints

All API calls go through the proxy at `/api/`. The AMR IP is configured via `AMR_IP` environment variable (default: 192.168.219.42).

### Movement Control
- `POST /api/cmd/move` - Move distance: `{"distance": 10, "direction": 1, "speed": 0.3}` (direction: 1=forward, 0=backward)
- `POST /api/cmd/turn` - Turn angle: `{"direction": 1, "angle": 90, "speed": 0.6}` (direction: 1=left, 0=right)
- `POST /api/cmd/speed` - Continuous velocity: `{"vx": 0.3, "vth": 0.5}` (emergency stop: both 0)

### Navigation
- `POST /api/cmd/nav_name` - Navigate to waypoint: `{"point": "waypoint_name"}`
- `POST /api/cmd/charge` - Navigate to charging dock
- `GET /api/reeman/nav_status` - Navigation status (res: 1=started, 3=completed; reason: 0=success)
- `POST /api/cmd/cancel_goal` - Cancel navigation

### Status
- `GET /api/reeman/pose` - Current position: `{x, y, theta}`
- `GET /api/reeman/base_encode` - Battery/emergency button: `{battery, chargeFlag, emergencyButton}` (emergencyButton: 0=pressed, 1=released)
- `GET /api/reeman/get_mode` - Mode: 1=mapping, 2=navigation

### Waypoints
- `GET /api/reeman/position` - List all waypoints
- `POST /api/cmd/position` - Save waypoint: `{"name": "...", "type": "delivery|charge", "pose": {x, y, theta}}`
- `DELETE /api/cmd/position` - Delete waypoint: `["waypoint_name"]`

### Map/SLAM
- `GET /api/reeman/map` - Get SLAM map image (base64)
- `POST /api/cmd/set_mode` - Switch mode: `{"mode": 1}` for mapping, `{"mode": 2}` for navigation

### Docking Settings
- `GET /api/reeman/agv_docking_dis` - Get docking distance
- `GET /api/reeman/agv_docking_direction` - Get docking direction (0=forward, 1=backward)
- `POST /api/cmd/agv_docking_dis` - Set docking distance: `{"distance": -0.7}`
- `POST /api/cmd/agv_docking_direction` - Set direction: `{"direction": 1}`

## Web UI Structure (index.html)

The UI is a single HTML file with embedded CSS and JavaScript. Key sections:

- **Main Map Display**: Real-time robot position and waypoint visualization
- **Control Panel**: Navigation controls, route management, quick actions
- **Build Map Mode**: SLAM mapping with jog dial controls, waypoint capture
- **Settings Modal**: TTS configuration, docking parameters
- **Route Modal**: Create/edit navigation routes

Key JavaScript globals:
- `API_BASE = '/api'` - Proxy endpoint
- `mapImage`, `mapInfo` - Loaded map data
- `robotPose` - Current robot position
- `waypoints` - Saved navigation points
- `isBuildMapMode` - Build map mode state

## Environment Variables

For proxy_server.py:
- `AMR_IP` - AMR navigation system IP (default: 192.168.219.42)
- `GOOGLE_TTS_API_KEY` - Google Cloud TTS API key for voice announcements

## File Locations

- Web root: `/var/www/docent/`
- Nginx config: `/etc/nginx/sites-available/docent.rongrong.org`
- SSL certs: `/etc/ssl/certs/docent.rongrong.org.pem`, `/etc/ssl/private/docent.rongrong.org.key`
- Systemd service: `/etc/systemd/system/docent-proxy.service`
- Logs: `/var/log/nginx/docent.access.log`, `/var/log/nginx/docent.error.log`
