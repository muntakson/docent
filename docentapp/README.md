# DocentBot - 도슨트봇

Android application for Reeman AMR robot used as an automated docent/tour guide for kids' library.

## Features

### Main Screen
- **Animated Robot Face**: Two animated eyeballs and a mouth that respond to app state
  - Idle mode: Eyes slowly wander, mouth is closed
  - Speaking mode: Eyes track actively, mouth animates
  - Welcome mode: Friendly smile expression
- **Start Button**: Initiates the docent tour
- **Settings Menu**: Hamburger icon at top-left corner

### Tour Flow
1. User clicks **Start** button
2. App checks if robot is at Welcome Zone
3. Plays welcome speech (TTS) and optionally streams welcome video to projector
4. Shows **안내시작 (Continue)** button
5. User clicks Continue to begin navigation tour
6. Robot navigates through zones, speaking at each location
7. Tour completes with goodbye message

### Settings
- **AMR IP Address**: Configure robot navigation server (default: 192.168.219.42)
- **Backend URL**: Configure web backend for TTS and zone data (default: https://docent.rongrong.org)
- **Projector Discovery**: Find EShare/AirPlay projectors on the network
- **Video Upload**: Select video file for welcome zone playback
- **Stream Video**: Test video streaming to selected projector

## Architecture

```
┌─────────────────────────────────────────┐
│          DocentBot Android App          │
│                                         │
│  ┌─────────────┐  ┌─────────────────┐   │
│  │ MainActivity│  │ SettingsActivity│   │
│  │ - Eyeballs  │  │ - AMR Config    │   │
│  │ - Mouth     │  │ - Projector     │   │
│  │ - Buttons   │  │ - Video         │   │
│  └──────┬──────┘  └────────┬────────┘   │
│         │                  │            │
│  ┌──────┴──────────────────┴──────┐     │
│  │         MainViewModel          │     │
│  └──────────────┬─────────────────┘     │
│                 │                       │
│  ┌──────────────┼──────────────────┐    │
│  │              │                  │    │
│  ▼              ▼                  ▼    │
│ AMRApiService  BackendApiService  DLNA │
│                                  Service│
└──┬──────────────┬──────────────────┬────┘
   │              │                  │
   ▼              ▼                  ▼
┌──────┐   ┌───────────┐    ┌────────────┐
│ AMR  │   │ Backend   │    │ EShare     │
│Server│   │ (TTS/API) │    │ Projector  │
└──────┘   └───────────┘    └────────────┘
```

## API Integration

### AMR Navigation Server (http://192.168.219.42)
- `GET /reeman/pose` - Robot position (x, y, theta)
- `GET /reeman/nav_status` - Navigation status
- `GET /reeman/base_encode` - Battery, charging, e-stop
- `GET /reeman/position` - List all waypoints
- `POST /cmd/nav_name` - Navigate to waypoint
- `POST /cmd/cancel_goal` - Cancel navigation

### Backend Server (https://docent.rongrong.org)
- `GET /api/zones` - Zone definitions with speech text
- `GET /api/routes` - Navigation routes
- `POST /api/tts` - Text-to-speech synthesis (Google Cloud TTS)
- `GET /api/tts/voices` - Available TTS voices

### Projector Streaming
- EShare UDP discovery (ports 48689, 8121, 2425)
- mDNS/Bonjour discovery (_eshare._tcp, _airplay._tcp)
- AirPlay-compatible streaming protocol
- Local HTTP server for video file serving

## Building

### Requirements
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Build Steps
```bash
cd docentapp
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## Project Structure

```
docentapp/
├── app/
│   ├── src/main/
│   │   ├── java/com/docent/bot/
│   │   │   ├── DocentApplication.kt
│   │   │   ├── model/
│   │   │   │   ├── RobotState.kt
│   │   │   │   └── ProjectorDevice.kt
│   │   │   ├── service/
│   │   │   │   ├── AMRApiService.kt
│   │   │   │   ├── BackendApiService.kt
│   │   │   │   ├── DLNADiscoveryService.kt
│   │   │   │   └── MediaServerService.kt
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── MainViewModel.kt
│   │   │   │   ├── SettingsActivity.kt
│   │   │   │   ├── EyeballView.kt
│   │   │   │   ├── MouthView.kt
│   │   │   │   └── DeviceAdapter.kt
│   │   │   └── util/
│   │   │       ├── NetworkUtils.kt
│   │   │       └── PreferenceManager.kt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── drawable/
│   │   │   ├── values/
│   │   │   └── xml/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## Dependencies

- AndroidX Core, AppCompat, Material Design 3
- Kotlin Coroutines
- OkHttp for networking
- JUpnP for UPnP/DLNA
- JmDNS for mDNS discovery
- NanoHTTPD for local HTTP server
- ExoPlayer for media playback

## Configuration

Default settings stored in SharedPreferences:
- AMR IP: `192.168.219.42`
- Backend URL: `https://docent.rongrong.org`
- TTS Voice: `ko-KR-Wavenet-A`
- TTS Rate: `1.0`

## Korean UI

The app is designed for Korean users with all UI text in Korean:
- 시작 (Start)
- 안내시작 (Continue/Start Guide)
- 설정 (Settings)
- 프로젝터 검색 (Find Projector)
- 동영상 선택 (Select Video)
- 동영상 재생 (Play Video)
