#!/usr/bin/env bash
# Builds a portable Windows x64 folder + zip from Linux/macOS:
#   MangaScroll/MangaScroll.exe  (small launcher, compiled with zig)
#   MangaScroll/app/MangaScroll.jar
#   MangaScroll/runtime/          (slim Java 17 runtime for Windows, made with jlink)
# Needs: JDK 17+, curl, unzip, tar, zip, and zig (pip install ziglang, or https://ziglang.org).
# Downloads Temurin JDK 17 for Windows (its jmods) and for Linux (jlink must match the jmods version).
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=1.1
OUT=build/windows
ZIG=${ZIG:-zig}

./gradlew windowsJar
rm -rf "$OUT" && mkdir -p "$OUT/MangaScroll/app"
cp build/libs/MangaScroll-windows.jar "$OUT/MangaScroll/app/MangaScroll.jar"

JDKS=build/jdks
mkdir -p "$JDKS"
API=https://api.adoptium.net/v3/binary/latest/17/ga
[ -d "$JDKS/win" ]   || { curl -L -o "$JDKS/win.zip" "$API/windows/x64/jdk/hotspot/normal/eclipse"; unzip -q "$JDKS/win.zip" -d "$JDKS/win"; }
[ -d "$JDKS/linux" ] || { curl -L -o "$JDKS/linux.tgz" "$API/linux/x64/jdk/hotspot/normal/eclipse"; mkdir -p "$JDKS/linux"; tar xzf "$JDKS/linux.tgz" -C "$JDKS/linux"; }
JLINK=$(echo "$JDKS"/linux/*/bin/jlink)
JMODS=$(echo "$JDKS"/win/*/jmods)
MODULES=java.base,java.desktop,java.instrument,java.prefs,java.sql,jdk.unsupported,java.logging,java.xml,jdk.accessibility,jdk.charsets,java.naming
"$JLINK" --module-path "$JMODS" --add-modules "$MODULES" \
    --strip-debug --no-header-files --no-man-pages --compress=2 --output "$OUT/MangaScroll/runtime"

(cd packaging && $ZIG cc -target x86_64-windows-gnu -O2 -municode -Wl,--subsystem,windows \
    launcher.c launcher.rc -o "../$OUT/MangaScroll/MangaScroll.exe")
rm -f "$OUT/MangaScroll/MangaScroll.pdb"
cp packaging/README-windows.txt "$OUT/MangaScroll/README.txt"

(cd "$OUT" && zip -qr "MangaScroll-$VERSION-windows-x64.zip" MangaScroll)
echo "Built $OUT/MangaScroll-$VERSION-windows-x64.zip"
