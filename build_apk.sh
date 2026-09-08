#!/usr/bin/env bash
set -e

echo "======================================"
echo " Joystick Tester v3 - APK Build Script"
echo "======================================"
echo

# 1. Check flutter
if ! command -v flutter >/dev/null 2>&1; then
    echo "ERROR: flutter not found in PATH." >&2
    echo "Install Flutter stable and add it to PATH, then re-run." >&2
    exit 1
fi

# 2. Check java
if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found in PATH." >&2
    echo "Install JDK 17+ and add it to PATH, then re-run." >&2
    exit 1
fi

# 3. Show Flutter version
echo "--- flutter version ---"
flutter --version

# 4. Show Java version
echo
echo "--- java version ---"
java -version

# 5. Resolve dependencies
echo
echo "--- flutter pub get ---"
flutter pub get

# 6. Static analysis (info-level lints are not fatal)
echo
echo "--- flutter analyze --no-fatal-infos ---"
flutter analyze --no-fatal-infos

# 7. Build release APK
echo
echo "--- flutter build apk --release ---"
flutter build apk --release

# 8. Create output/ directory
echo
echo "--- create output/ ---"
mkdir -p output

# 9. Copy the APK to output/
APK_SOURCE="build/app/outputs/flutter-apk/app-release.apk"
APK_DEST="output/joystick_tester_v3.apk"

if [ ! -f "$APK_SOURCE" ]; then
    echo "ERROR: APK not found: $APK_SOURCE" >&2
    exit 1
fi

cp "$APK_SOURCE" "$APK_DEST"

# 10. Show APK path
echo
echo "--- APK output ---"
echo "APK path: $APK_DEST"

# 11. Show APK size
SIZE=$(stat -c %s "$APK_DEST")
echo "APK size: $SIZE bytes ($(du -h "$APK_DEST" | cut -f1))"

echo
echo "Build successful!"