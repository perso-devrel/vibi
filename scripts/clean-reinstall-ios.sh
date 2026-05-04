#!/usr/bin/env bash
#
# vibi iOS 시뮬레이터 깨끗한 재설치 스크립트.
#
# `simctl uninstall + install` 만으로는 같은 UUID 의 Data Container 가 재사용되어
# Documents 디렉터리 (Room DB · picker_media 등) 가 잔존함. 즉 이전 앱의 프로젝트 ·
# segment 편집사항이 새 install 후에도 보임. 이 스크립트는 Container 를 명시 삭제해
# 진짜 "처음 설치" 상태를 만든다.
#
# 사용법:
#   ./scripts/clean-reinstall-ios.sh            # 현재 빌드 결과로 clean-reinstall
#   ./scripts/clean-reinstall-ios.sh --build    # KMP framework + xcodebuild 후 clean-reinstall
#   ./scripts/clean-reinstall-ios.sh --erase    # device 전체 erase (모든 앱 + 시뮬 설정)
#
# 환경변수로 override 가능:
#   BUNDLE_ID    (default: com.dubcast.ios)
#   APP_PATH     (default: iosApp/build/DerivedData/Build/Products/Debug-iphonesimulator/iosApp.app)
#   DEVICE_ID    (default: booted = 현재 부팅된 시뮬)

set -euo pipefail

BUNDLE_ID="${BUNDLE_ID:-com.dubcast.ios}"
DEVICE_ID="${DEVICE_ID:-booted}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_PATH="${APP_PATH:-$REPO_ROOT/iosApp/build/DerivedData/Build/Products/Debug-iphonesimulator/iosApp.app}"

DO_BUILD=0
DO_ERASE=0
for arg in "$@"; do
    case "$arg" in
        --build|-b) DO_BUILD=1 ;;
        --erase) DO_ERASE=1 ;;
        -h|--help)
            sed -n '3,18p' "$0"
            exit 0
            ;;
        *) echo "Unknown arg: $arg" >&2; exit 1 ;;
    esac
done

# 부팅된 시뮬레이터 확인
if [ "$DEVICE_ID" = "booted" ]; then
    BOOTED=$(xcrun simctl list devices booted 2>/dev/null | grep -E "Booted" | head -1)
    if [ -z "$BOOTED" ]; then
        echo "ERROR: 부팅된 시뮬레이터가 없습니다. Simulator 앱에서 device 를 부팅하세요." >&2
        exit 1
    fi
    echo "▶ Target: $BOOTED"
fi

# --erase: 시뮬레이터 자체 reset (모든 앱 + 설정 초기화). 가장 깨끗하지만 다른 앱도 영향.
if [ "$DO_ERASE" = "1" ]; then
    UUID=$(xcrun simctl list devices booted | grep -E "Booted" | sed -E 's/.*\(([0-9A-F-]+)\).*/\1/' | head -1)
    if [ -z "$UUID" ]; then echo "ERROR: device UUID 추출 실패" >&2; exit 1; fi
    echo "▶ Shutting down + erasing $UUID ..."
    xcrun simctl shutdown "$UUID" || true
    xcrun simctl erase "$UUID"
    xcrun simctl boot "$UUID"
    echo "✓ Device erased + booted"
fi

# --build: KMP framework link + xcodebuild
if [ "$DO_BUILD" = "1" ]; then
    echo "▶ Linking iOS debug framework ..."
    cd "$REPO_ROOT"
    ./gradlew :cmp:linkDebugFrameworkIosSimulatorArm64 --no-configuration-cache

    echo "▶ Building iosApp ..."
    cd "$REPO_ROOT/iosApp"
    # 부팅된 device 의 UUID 추출
    DEST_UUID=$(xcrun simctl list devices booted | grep -E "Booted" | sed -E 's/.*\(([0-9A-F-]+)\).*/\1/' | head -1)
    xcodebuild \
        -project iosApp.xcodeproj \
        -scheme iosApp \
        -configuration Debug \
        -sdk iphonesimulator \
        -destination "platform=iOS Simulator,id=$DEST_UUID" \
        -derivedDataPath build/DerivedData \
        build | tail -5
    cd "$REPO_ROOT"
fi

if [ ! -d "$APP_PATH" ]; then
    echo "ERROR: $APP_PATH 가 존재하지 않습니다. --build 옵션 추가 또는 APP_PATH 환경변수 지정." >&2
    exit 1
fi

# Container path 미리 추출 (uninstall 후엔 못 얻음)
APP_DATA=$(xcrun simctl get_app_container "$DEVICE_ID" "$BUNDLE_ID" data 2>/dev/null || true)

echo "▶ Terminate + uninstall $BUNDLE_ID ..."
xcrun simctl terminate "$DEVICE_ID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl uninstall "$DEVICE_ID" "$BUNDLE_ID" 2>/dev/null || true

# 핵심: Data Container 명시 삭제. simctl uninstall 만으론 Documents 잔존 가능.
if [ -n "$APP_DATA" ] && [ -d "$APP_DATA" ]; then
    rm -rf "$APP_DATA"
    echo "✓ Removed Data Container: $APP_DATA"
fi

echo "▶ Install $APP_PATH ..."
xcrun simctl install "$DEVICE_ID" "$APP_PATH"

NEW_DATA=$(xcrun simctl get_app_container "$DEVICE_ID" "$BUNDLE_ID" data 2>/dev/null || true)
if [ -n "$NEW_DATA" ]; then
    echo "✓ Fresh Data Container: $NEW_DATA"
fi

echo "▶ Launch ..."
xcrun simctl launch "$DEVICE_ID" "$BUNDLE_ID"

echo "✓ Done"
