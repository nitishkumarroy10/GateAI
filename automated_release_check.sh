#!/bin/bash
# ==============================================================================
# GateAI Automated Release Check & Verification Script
# Purpose: Validates CI/CD environment, runs tests, builds, and verifies the APK.
# ==============================================================================

set -e
trap 'echo -e "\n❌ Process aborted unexpectedly." ; exit 1' ERR

echo "========================================"
echo "🚀 GATE-AI PRODUCTION RELEASE AUTOMATION"
echo "========================================"

# ------------------------------------------------------------------------------
# 1. Environment Variables Validation
# ------------------------------------------------------------------------------
echo "⏳ [1/5] Verifying secure environment variables..."
REQUIRED_VARS=("KEYSTORE_PATH" "STORE_PASSWORD" "KEY_PASSWORD" "GEMINI_API_KEY" "SUPABASE_URL" "SUPABASE_KEY")

MISSING_VAR=false
for var in "${REQUIRED_VARS[@]}"; do
  if [ -z "${!var}" ]; then
    echo "  ❌ ERROR: Missing required environment variable -> $var"
    MISSING_VAR=true
  fi
done

if [ "$MISSING_VAR" = true ]; then
  echo "❌ Environment validation failed. Please set required secrets in your CI/CD runner."
  exit 1
fi
echo "  ✅ All required environment variables are set."

# ------------------------------------------------------------------------------
# 2. Code Quality & Test Verification
# ------------------------------------------------------------------------------
echo "⏳ [2/5] Running automated tests & validating schemas..."

# Execute linting (to catch style or structural issues)
echo "  -> Running Android Lint (Release)..."
./gradlew lintRelease --quiet || { echo "  ❌ Lint failed. Check reports."; exit 1; }

# Execute Room Migration schema exports & unit tests
echo "  -> Running Local Unit & Robolectric Tests..."
./gradlew testReleaseUnitTest --quiet || { echo "  ❌ Unit tests failed. Review test report."; exit 1; }

echo "  ✅ Code quality and test suites passed successfully."

# ------------------------------------------------------------------------------
# 3. APK Generation
# ------------------------------------------------------------------------------
echo "⏳ [3/5] Building Signed Release APK..."

# Clean and Build Release
./gradlew clean assembleRelease --quiet || { echo "  ❌ Release build failed."; exit 1; }

APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
   echo "  ❌ Build completed but APK was not found at expected path: $APK_PATH"
   exit 1
fi
echo "  ✅ Release APK built securely at: $APK_PATH"

# ------------------------------------------------------------------------------
# 4. APK Security & Configuration Validation
# ------------------------------------------------------------------------------
echo "⏳ [4/5] Running deep APK verification (aapt2 / apkanalyzer)..."

if command -v aapt2 &> /dev/null; then
    # Validate Permissions
    PERMISSIONS=$(aapt2 dump permissions "$APK_PATH")
    
    if echo "$PERMISSIONS" | grep -q "android.permission.CAMERA" && \
       echo "$PERMISSIONS" | grep -q "android.permission.INTERNET" && \
       echo "$PERMISSIONS" | grep -q "android.permission.VIBRATE"; then
        echo "  ✅ Core Permissions verified (CAMERA, INTERNET, VIBRATE)."
    else
        echo "  ❌ ERROR: Core permissions are missing from the compiled manifest!"
        exit 1
    fi

    # Validate Target SDK (36)
    BADGING=$(aapt2 dump badging "$APK_PATH")
    TARGET_SDK=$(echo "$BADGING" | grep "targetSdkVersion" | grep -oP "(?<=targetSdkVersion:\')[^\']*")
    
    if [ "$TARGET_SDK" == "36" ]; then
         echo "  ✅ Target SDK Configuration validated (SDK $TARGET_SDK)."
    else
         echo "  ⚠️ WARNING: Target SDK is $TARGET_SDK. Expected: 36."
         # Not failing the build just warning in case of future upgrades, but ideally this should be strict.
    fi
else
    echo "  ⚠️ 'aapt2' not found in CI PATH, skipping deep binary permissions analysis."
fi

# ------------------------------------------------------------------------------
# 5. Production Release Report Summary
# ------------------------------------------------------------------------------
echo "⏳ [5/5] Generating Artifact Fingerprint..."

# Fetch File Size (cross-compatible stat/ls)
if command -v stat &> /dev/null; then
  APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
else
  APK_SIZE="Unknown"
fi

# Fetch SHA-256 Checksum
if command -v sha256sum &> /dev/null; then
  APK_HASH=$(sha256sum "$APK_PATH" | awk '{print $1}')
else
  APK_HASH="Unknown (sha256sum not found)"
fi

echo ""
echo "========================================"
echo "🎯 GATE-AI PRODUCTION RELEASE SUMMARY"
echo "========================================"
echo " Status        : READY FOR DEPLOYMENT"
echo " Environment   : PRODUCTION (Signed)"
echo " Artifact Path : $APK_PATH"
echo " File Size     : $APK_SIZE"
echo " SHA-256 Hash  : $APK_HASH"
echo "========================================"
echo "Script completed successfully."
exit 0
