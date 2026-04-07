# Gyro Tracker Android Project

This is a native Android application built with Kotlin and Jetpack Compose for high-performance gyroscope head tracking.

## Features
- **Native Sensor Integration**: Uses Android's `SensorManager` for low-latency gyroscope data.
- **OpenTrack Compatible**: Sends data via UDP in the standard 6-float little-endian format.
- **Customizable**: Sensitivity control, axis inversion, and one-tap center reset.
- **Efficient Networking**: Reuses UDP sockets to minimize overhead and battery drain.

## How to Build
1. **Download/Clone** this directory (`android-source`).
2. **Open Android Studio**.
3. Select **"Open"** and navigate to the `android-source` directory.
4. Wait for Gradle to sync.
5. Connect your Android device or use an emulator.
6. Click the **"Run"** button (green play icon) to build and install the APK.

### Command Line Build
Alternatively, you can build the APK from the command line:
```bash
./gradlew assembleDebug
```
The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Requirements
- **Android Studio Hedgehog** (2023.1.1) or newer.
- **Android 7.0 (API 24)** or newer.
- Device with a **Gyroscope sensor**.

## Configuration
- **PC IP**: Enter the local IP address of your PC running OpenTrack.
- **UDP Port**: Default is `4242`.
- **OpenTrack Setup**: Set Input to "UDP over network" in OpenTrack settings.
