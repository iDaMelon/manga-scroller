// MangaScroll.exe: starts the bundled Java runtime with the app jar.
// Layout next to this exe:  runtime\bin\javaw.exe   app\MangaScroll.jar
#define WIN32_LEAN_AND_MEAN
#define UNICODE
#define _UNICODE
#include <windows.h>
#include <wchar.h>

int WINAPI wWinMain(HINSTANCE hInst, HINSTANCE hPrev, PWSTR args, int show) {
    (void)hInst; (void)hPrev; (void)show;
    wchar_t dir[MAX_PATH];
    DWORD n = GetModuleFileNameW(NULL, dir, MAX_PATH);
    if (n == 0 || n >= MAX_PATH) return 1;
    wchar_t *slash = wcsrchr(dir, L'\\');
    if (slash) *slash = 0;

    wchar_t java[MAX_PATH + 64];
    _snwprintf(java, MAX_PATH + 64, L"%s\\runtime\\bin\\javaw.exe", dir);
    if (GetFileAttributesW(java) == INVALID_FILE_ATTRIBUTES) {
        MessageBoxW(NULL,
            L"The 'runtime' folder is missing.\n\nKeep MangaScroll.exe together with its 'app' and 'runtime' folders "
            L"(extract the whole zip, don't run the exe from inside it).",
            L"MangaScroll", MB_ICONERROR);
        return 1;
    }

    static wchar_t cmd[32768];
    _snwprintf(cmd, 32768,
        L"\"%s\" -XX:MaxRAMPercentage=50 -Dfile.encoding=UTF-8 -Dsun.java2d.uiScale.enabled=true -jar \"%s\\app\\MangaScroll.jar\" %s",
        java, dir, args ? args : L"");

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof si);
    si.cb = sizeof si;
    ZeroMemory(&pi, sizeof pi);
    if (!CreateProcessW(NULL, cmd, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        MessageBoxW(NULL, L"Couldn't start MangaScroll (failed to launch the Java runtime).", L"MangaScroll", MB_ICONERROR);
        return 1;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}
