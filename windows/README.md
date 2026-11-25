# MangaScroll for Windows

The same app built with Compose for Desktop, shipped as a portable folder:
`MangaScroll.exe` + the app + a slim bundled Java 17 runtime (nothing to install).

**AI enhance**: per manga (Off / As you read / Whole manga), pages are upscaled on the GPU with
[Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN)'s anime model (`realesrgan-ncnn-vulkan`, bundled in the zip's `ai/`
folder), stitched from overlapping strips, and saved next to the originals at 1.5x as WebP
(`library/<manga>/enhanced/<chapter>/`). The reader switches between enhanced and original with the AI button or `A`.
Set `MANGASCROLL_AI` to use an engine in another folder.

Desktop extras: drag-and-drop importing, mouse-wheel / Space / Page Down reading, Ctrl+wheel and +/- zoom,
F11 full screen, auto-hiding controls, native file dialogs.

## Running and building
From this folder:
- Run it locally (any OS): `./gradlew run`
- Build the portable Windows zip from Linux/macOS: `packaging/build-windows.sh`
  (needs JDK 17+, `zig` for the small launcher exe; it downloads Temurin JDK 17 to make the runtime with jlink, and the Real-ESRGAN engine)
  → `build/windows/MangaScroll-<version>-windows-x64.zip`

The library is stored in `%APPDATA%\MangaScroll` (override with the `MANGASCROLL_DIR` environment variable).
Keyboard shortcuts are listed in `packaging/README-windows.txt`, which ships inside the zip.
