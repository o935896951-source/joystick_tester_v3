# joystick_tester_v3_ps

A new Flutter project.

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Learn Flutter](https://docs.flutter.dev/get-started/learn-flutter)
- [Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Flutter learning resources](https://docs.flutter.dev/reference/learning-resources)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.

## x86_64 Linux 一鍵 Build

在 x86_64 Linux（且有 Flutter stable 3.47.2 + Android SDK + JDK 17）環境下，
可用一鍵腳本產生 release APK：

```bash
chmod +x build_apk.sh
./build_apk.sh
```

腳本流程：

1. 檢查 `flutter` 與 `java` 是否已安裝
2. 顯示 Flutter 與 Java 版本
3. `flutter pub get`
4. `flutter analyze --no-fatal-infos`
5. `flutter build apk --release`
6. 建立 `output/` 並將 `build/app/outputs/flutter-apk/app-release.apk`
   複製為 `output/joystick_tester_v3.apk`
7. 顯示 APK 路徑與檔案大小

任一步驟失敗會立即停止（`set -e`）。

### 環境需求

- Linux x86_64
- Flutter stable 3.47.2（含 cmdline-tools/Android SDK）
- JDK 17（AGP 8.11 相容）
- 專案設定：compileSdk 36 / minSdk 24 / targetSdk 36 / Gradle 8.14

產物：`output/joystick_tester_v3.apk`
