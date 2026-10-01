#!/usr/bin/env bash
set -euo pipefail
VERSION="v1.19.31"
BASE="https://github.com/MetaCubeX/mihomo/releases/download/${VERSION}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/V2rayNG/app/src/main/assets/mihomo"
mkdir -p "$OUT"

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
  curl -fL --retry 3 "$BASE/$archive" | gzip -d > "$OUT/$abi/mihomo"
  chmod 755 "$OUT/$abi/mihomo"
done
echo "Mihomo $VERSION installed under $OUT"
