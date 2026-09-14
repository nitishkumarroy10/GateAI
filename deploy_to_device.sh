#!/bin/bash
# ==============================================================================
# GateAI ADB Onboarding & Device Provisioning Script
# Purpose: Installs APK, grants permissions, sets Device Owner for Kiosk Mode.
# ==============================================================================

set -e

APK_PATH="app/build/outputs/apk/release/app-release.apk"
PACKAGE_NAME="com.aistudio.gateai.mvpxq"
RECEIVER_COMPONENT="com.example/com.example.util.KioskModeReceiver"
MAIN_ACTIVITY="com.example/com.example.MainActivity"

echo "========================================"
echo "📱 GATE-AI DEVICE PROVISIONING SCRIPT"
echo "========================================"

# 1. Detect Devices
echo "⏳ [1/5] Detecting connected ADB devices..."
DEVICE_COUNT=$(adb devices | grep -v "List" | grep -c -w "device" || true)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "❌ ERROR: No active ADB devices found. Please connect a tablet and enable USB Debugging."
    exit 1
fi
echo "  ✅ Found $DEVICE_COUNT device(s) connected."

# 2. Install Release APK
echo "⏳ [2/5] Installing GateAI Release APK..."
if [ ! -f "$APK_PATH" ]; then
    echo "❌ ERROR: Release APK not found at $APK_PATH. Please run assembleRelease first."
    exit 1
fi
adb install -r -g "$APK_PATH"
echo "  ✅ APK Installed successfully."

# 3. Grant Runtime Permissions
echo "⏳ [3/5] Granting required runtime permissions..."
adb shell pm grant $PACKAGE_NAME android.permission.CAMERA
adb shell pm grant $PACKAGE_NAME android.permission.VIBRATE || true # Vibrate doesn't always need explicit runtime grant, but for completeness.
echo "  ✅ Permissions granted."

# 4. Set Device Owner (Kiosk Lock-down)
echo "⏳ [4/5] Provisioning Device Owner (Required for Lock Task Mode)..."
# Note: This will fail if there are any existing accounts on the device.
adb shell dpm set-device-owner "$RECEIVER_COMPONENT" || {
    echo "  ⚠️ WARNING: Could not set Device Owner. Device must be factory reset and have NO accounts to become a Device Owner."
}
echo "  ✅ Device provisioning attempted."

# 5. Launch Application
echo "⏳ [5/5] Launching GateAI Kiosk App..."
adb shell am start -n "$MAIN_ACTIVITY"
echo "  ✅ MainActivity launched."

echo "========================================"
echo "🎯 PROVISIONING COMPLETE"
echo "========================================"
echo "GateAI should now be running. Verify initial Room DB seed readiness on screen."
exit 0
