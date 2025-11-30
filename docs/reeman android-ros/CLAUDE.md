# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android library for serial port communication between Android devices and ROS (Robot Operating System) on Reeman robots. The project consists of three modules:

- **reeman-serialport**: Core library for serial port communication and ROS protocol implementation
- **reeman-log**: Logging wrapper combining Timber and XLog libraries
- **app**: Example application demonstrating library usage

The library is published to JitPack as `com.github.Misaka-XXXXII:reeman-lib`.

## Build System

- **Gradle version**: 4.2.2 (older version, note the commented 7.0.4 line in root build.gradle)
- **Compile SDK**: 33
- **Min SDK**: 21
- **Java compatibility**: 1.8

### Build Commands

```bash
# Build the project
./gradlew build

# Build specific module
./gradlew :reeman-serialport:build
./gradlew :reeman-log:build
./gradlew :app:build

# Install app on device
./gradlew installDebug

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Generate sources JAR (for publishing)
./gradlew generateSourcesJar
```

## Architecture

### Serial Port Communication Flow

1. **SerialPortParser** (reeman-serialport/src/main/java/com/reeman/serialport/controller/SerialPortParser.java:1)
   - Low-level serial port I/O handler
   - Uses native library `libandroid_serial_port.so` via JNI
   - Runs dedicated read thread for continuous data reception
   - Provides `OnDataResultListener` callback for raw byte data

2. **RosCallbackParser** (reeman-serialport/src/main/java/com/reeman/serialport/controller/RosCallbackParser.java:1)
   - Protocol parser for ROS serial communication
   - Parses hex-encoded data packets with format: `AA54[size][data][checksum]` or `AA56[size][data][checksum]`
   - Uses concurrent queues for send/receive buffering
   - Scheduled executors handle queued operations (10ms receive, 50ms send intervals)
   - Provides high-level `RosCallback` interface for parsed string results

3. **RobotActionController** (reeman-serialport/src/main/java/com/reeman/serialport/controller/RobotActionController.java:1)
   - Singleton facade providing robot control API
   - Contains 60+ convenience methods for common robot operations
   - Handles automatic log upload to ROS every 60 seconds
   - Platform-specific initialization for different hardware (rk312x power board support)

### Data Flow

```
Hardware Serial Port
  ↓ (raw bytes)
SerialPortParser (JNI wrapper)
  ↓ (byte arrays)
RosCallbackParser (protocol handler)
  ↓ (parsed strings)
RobotActionController (API facade)
  ↓
Application Code
```

### Logging System

The **reeman-log** module wraps two libraries:
- **Timber**: Simple logging API with tag-based routing
- **XLog**: Advanced file logging with rotation, backup strategies
- **FileLoggingTree**: Custom Timber.Tree that bridges both libraries

Log files are organized by tag (e.g., "ros", "power_board_log") with:
- 5MB file size rotation
- Max 100 backup files
- 7-day retention policy

## Device-Specific Serial Ports

Serial port paths vary by hardware platform:

- **YF3568_XXXE**: `/dev/ttyS4`
- **rk3399_all**: `/dev/ttyXRUSB0`
- **rk3128** (default): `/dev/ttyS1`

Reference: app/src/main/java/com/reeman/lib/ExempleActivity.java:83

## Key ROS Protocol Commands

The library supports extensive ROS commands categorized as:

- **Navigation**: `nav_point[]`, `goal:nav[]`, `nav_pause`, `nav_resume`, `nav_cancel`
- **Mapping**: `model:mapping`, `model:remap`, `save_map`
- **Localization**: `nav:reloc[]`, `nav:get_pose`, `initpose`
- **System**: `sys:reboot`, `power_off`, `power_reboot`, `hostname:get`
- **Speed Control**: `max_vel[]`, `write_max_vel[]`, `move[]`
- **Charging**: `dock:start`, `dock:stop`
- **Multi-robot**: `robot_cost[]` for collision avoidance
- **Data Reporting**: `keep_connect` (heartbeat), `get_battery_info`, `get_current_info[]`

All commands are sent through `RobotActionController.sendCommand()` with automatic logging.

## JNI Native Libraries

The project includes precompiled `.so` files in `reeman-serialport/libs/` for architectures:
- armeabi, armeabi-v7a, arm64-v8a
- x86, x86_64
- mips, mips64

These implement the Google serial port library (forked and customized).

## Important Configuration

### BuildConfig Fields

- **app**: `APP_LOG_DIR = "exemple-log"`
- **reeman-serialport**:
  - `LOG_ROS = "ros"`
  - `LOG_POWER_BOARD = "power_board_log"`

These are used as Timber tags for log categorization.

## Initialization Pattern

Standard initialization in activity lifecycle:

```java
// onResume()
XLog.init();
Timber.plant(new FileLoggingTree(
    Log.VERBOSE,
    BuildConfig.DEBUG,
    Environment.getExternalStorageDirectory().getPath(),
    BuildConfig.APP_LOG_DIR,
    Arrays.asList(BuildConfig.APP_LOG_DIR, com.reeman.serialport.BuildConfig.LOG_POWER_BOARD)
));

RobotActionController.getInstance().init(
    115200,                    // baud rate
    "/dev/ttyS1",             // port path
    rosCallback,              // ROS data callback
    BuildConfig.APP_LOG_DIR   // logs to upload
);

// onPause/onDestroy
RobotActionController.getInstance().stopListen();
```

## Thread Safety

- **RosCallbackParser**: Uses `ConcurrentLinkedQueue` for thread-safe send/receive buffering
- **RobotActionController**: Singleton with double-checked locking
- **Serial I/O**: Dedicated thread "serial-port-read-thread1" handles reads
- **Callbacks**: Must not perform blocking operations (runs on scheduled executor threads)

## Publishing Configuration

Both library modules have `maven-publish` plugin configured with `generateSourcesJar` task for JitPack publication.
