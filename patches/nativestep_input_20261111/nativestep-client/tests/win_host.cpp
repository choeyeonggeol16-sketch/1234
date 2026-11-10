// Interactive Win32 test: this version uses actual SendInput mouse events.
// Run this executable deliberately on a real desktop, never as an unattended
// part of the DLL build. It moves the OS cursor and clicks the test window.
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <windowsx.h>
#include <cstdio>
#include <cstring>
#include <atomic>

using StartFn = int (__cdecl *)(HWND);
using FeedFn = int (__cdecl *)(int, const void*, int);
using StopFn = void (__cdecl *)();

std::atomic<int> downCount{0}, upCount{0}, moveCount{0}, correctCursorCount{0};
// This harness uses a fake player anchor, preserving any existing settings.
struct TestCalibration {
    bool ready = false, existed = false;
    wchar_t backup[MAX_PATH]{};
    TestCalibration(HWND hwnd) {
        wchar_t temp[MAX_PATH]{};
        if (!GetTempPathW(MAX_PATH, temp) || !GetTempFileNameW(temp, L"NST", 0, backup)) return;
        existed = GetFileAttributesW(L".\\NativeStep.ini") != INVALID_FILE_ATTRIBUTES;
        if (existed && !CopyFileW(L".\\NativeStep.ini", backup, FALSE)) {
            DeleteFileW(backup); backup[0]=0; return;
        }
        RECT r{};
        if (!GetClientRect(hwnd, &r)) return;
        const auto set=[](const wchar_t* key, int value) {
            wchar_t text[24]{}; swprintf_s(text, 24, L"%d", value);
            return WritePrivateProfileStringW(L"Viewport",key,text,L".\\NativeStep.ini")!=FALSE;
        };
        ready=set(L"AnchorX",r.right/2) && set(L"AnchorY",r.bottom/2) &&
              set(L"ClientWidth",r.right) && set(L"ClientHeight",r.bottom) &&
              set(L"HalfTileWidth",36) && set(L"HalfTileHeight",23);
    }
    ~TestCalibration() {
        if (!backup[0]) return;
        if (existed) CopyFileW(backup,L".\\NativeStep.ini",FALSE);
        else DeleteFileW(L".\\NativeStep.ini");
        DeleteFileW(backup);
    }
};
LRESULT CALLBACK TestWndProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    if (message == WM_MOUSEMOVE || message == WM_LBUTTONDOWN || message == WM_LBUTTONUP) {
        if (message == WM_MOUSEMOVE) ++moveCount;
        if (message == WM_LBUTTONDOWN) {
            ++downCount;
            POINT actual{};
            if (GetCursorPos(&actual) && ScreenToClient(hwnd, &actual) &&
                actual.x >= GET_X_LPARAM(lParam)-3 &&
                actual.x <= GET_X_LPARAM(lParam)+3 &&
                actual.y >= GET_Y_LPARAM(lParam)-3 &&
                actual.y <= GET_Y_LPARAM(lParam)+3) ++correctCursorCount;
        }
        if (message == WM_LBUTTONUP) ++upCount;
        std::printf("[host] WM=%04x x=%d y=%d\n", unsigned(message),
                    GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
        return 0;
    }
    if (message == WM_DESTROY) { PostQuitMessage(0); return 0; }
    return DefWindowProcW(hwnd, message, wParam, lParam);
}

void pump(unsigned ms) {
    const ULONGLONG end = GetTickCount64() + ms;
    while (GetTickCount64() < end) {
        MSG msg{};
        while (PeekMessageW(&msg, nullptr, 0, 0, PM_REMOVE)) {
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
        Sleep(2);
    }
}

int feed(FeedFn fn, const char* msg) {
    return fn(9, msg, static_cast<int>(std::strlen(msg)));
}

int main() {
    HINSTANCE inst = GetModuleHandleW(nullptr);
    WNDCLASSW wc{};
    wc.lpfnWndProc = TestWndProc;
    wc.hInstance = inst;
    wc.lpszClassName = L"NativeStepPhysicalInputHost";
    if (!RegisterClassW(&wc)) return 1;
    HWND hwnd = CreateWindowExW(0, wc.lpszClassName, L"NativeStep physical input test (intentional clicks)",
        WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, 820, 640,
        nullptr, nullptr, inst, nullptr);
    if (!hwnd) return 2;
    ShowWindow(hwnd, SW_SHOW);
    SetForegroundWindow(hwnd);
    pump(200);
    if (GetForegroundWindow() != hwnd) {
        std::puts("[host] ERROR: test window must be foreground. No input injected.");
        DestroyWindow(hwnd);
        return 6;
    }
    TestCalibration config(hwnd);
    if (!config.ready) return 12;
    HMODULE dll = LoadLibraryW(L".\\dinput.dll");
    if (!dll) { std::printf("LoadLibrary error=%lu\n", GetLastError()); return 3; }
    auto start = reinterpret_cast<StartFn>(GetProcAddress(dll, "NativeStep_Start"));
    auto fn = reinterpret_cast<FeedFn>(GetProcAddress(dll, "NativeStep_TryConsumeNotice"));
    auto stop = reinterpret_cast<StopFn>(GetProcAddress(dll, "NativeStep_Stop"));
    if (!start || !fn || !stop || !start(hwnd)) return 4;
    if (!feed(fn, "@@NATIVESTEP:START@@")) return 7;
    for (int i = 0; i < 2; ++i) {
        if (GetForegroundWindow() != hwnd) return 8;
        const char* direction = i == 0 ? "@@NATIVESTEP:2@@" : "@@NATIVESTEP:3@@";
        if (!feed(fn, direction)) return 9;
        pump(90);
        if (!feed(fn, "@@NATIVESTEP:CANCEL@@")) return 10;
        pump(150);
    }
    if (!feed(fn, "@@NATIVESTEP:STOP@@")) return 11;
    stop();
    FreeLibrary(dll);
    DestroyWindow(hwnd);
    std::printf("RESULT down=%d up=%d cursorMatches=%d move=%d\n",
                downCount.load(), upCount.load(), correctCursorCount.load(), moveCount.load());
    if (downCount.load() != 2 || upCount.load() < 2 || correctCursorCount.load() != 2) return 5;
    std::puts("NativeStep physical SendInput test: PASS");
    return 0;
}
