# MangaScroll for Windows

The same app built with Compose for Desktop, shipped as a portable folder:
`MangaScroll.exe` + the app + a slim bundled Java 17 runtime (nothing to install).

Desktop extras: drag-and-drop importing, mouse-wheel / Space / Page Down reading, Ctrl+wheel and +/- zoom,
F11 full screen, auto-hiding controls, native file dialogs.

## Running and building
From this folder:
- Run it locally (any OS): `./gradlew run`
- Build the portable Windows zip from Linux/macOS: `packaging/build-windows.sh`
  (needs JDK 17+, `zig` for the small launcher exe; it downloads Temurin JDK 17 to make the runtime with jlink)
  → `build/windows/MangaScroll-<version>-windows-x64.zip`

The library is stored in `%APPDATA%\MangaScroll` (override with the `MANGASCROLL_DIR` environment variable).
Keyboard shortcuts are listed in `packaging/README-windows.txt`, which ships inside the zip.
