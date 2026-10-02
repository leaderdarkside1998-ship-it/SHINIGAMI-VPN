#!/usr/bin/env bash
# Downloads the Mihomo (Clash.Meta) Android binaries and installs them as native libraries
# (app/libs/<abi>/libmihomo.so). They must live there, not in assets: Android 10+ only lets an
# app execute binaries from its nativeLibraryDir, never from files copied into its data dir.
set -euo pipefail
VERSION="v1.19.31"
BASE="https://github.com/MetaCubeX/mihomo/releases/download/${VERSION}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/V2rayNG/app/libs"

declare -A URLS=(
  [arm64-v8a]="mihomo-android-arm64-v8-${VERSION}.gz"
  [armeabi-v7a]="mihomo-android-armv7-${VERSION}.gz"
  [x86_64]="mihomo-android-amd64-${VERSION}.gz"
  [x86]="mihomo-android-386-${VERSION}.gz"
)
for abi in "${!URLS[@]}"; do
  mkdir -p "$OUT/$abi"
  archive="${URLS[$abi]}"
  echo "Downloading $archive"
  curl -fL --retry 3 "$BASE/$archive" | gzip -d > "$OUT/$abi/libmihomo.so"
  chmod 755 "$OUT/$abi/libmihomo.so"
done
echo "Mihomo $VERSION installed under $OUT/<abi>/libmihomo.so"
