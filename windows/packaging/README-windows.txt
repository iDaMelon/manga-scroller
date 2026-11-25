MangaScroll for Windows
=======================

Run MangaScroll.exe. Keep it together with the "app" and "runtime" folders
(you can move the whole MangaScroll folder anywhere, e.g. C:\Program Files or your Desktop).
Java is included in "runtime", so nothing else needs to be installed.

Windows may show "Windows protected your PC" the first time because the app isn't
code-signed. Click "More info" and then "Run anyway".

Importing
  - Click Import, or drag images / a folder / ZIP or CBZ files onto the window.
  - Pages are sorted by file name (01, 02 ... 10). A folder with sub-folders becomes
    one chapter per sub-folder.

AI enhance
  On a manga's page, turn on AI enhance: "As you read" (the chapter you're on plus the
  next 2) or "Whole manga". It runs on your graphics card and saves sharper 1.5x copies
  of the pages; your originals are kept. Use "Delete enhanced pages" to free the space.
  In the reader, the "AI on/off" button (or the A key) switches between enhanced and original.
  Settings > Storage can replace the originals with the AI pages to save space (this deletes
  the originals).
  If it fails, update your graphics driver (it needs Vulkan, which current AMD, NVIDIA
  and Intel drivers include).

Reading
  Mouse wheel / Space / Page Down    scroll   (Shift+Space or Page Up to go back)
  Up / Down                         small scroll
  Ctrl + mouse wheel, + / -, 0      zoom (0 resets)
  Right / Left  (or N / P)          next / previous chapter
  F11 or F                          full screen
  A                                 AI enhanced / original pages
  Esc                               exit full screen / go back
  Click the page                    keep the controls visible

Your library is stored in %APPDATA%\MangaScroll unless you pick another folder in
Settings > Storage > Library folder.
