// Boundary test: this executable is not the supported Mega.bin. It must never
// produce synthetic mouse input. Native behavior is exercised by x86 replay.
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <cstdio>
using StartFn=int (__cdecl *)(HWND);
using StopFn=void (__cdecl *)();
int main() {
    WNDCLASSW wc{};wc.lpfnWndProc=DefWindowProcW;wc.hInstance=GetModuleHandleW(nullptr);
    wc.lpszClassName=L"NativeStepUnsupportedHost";
    if(!RegisterClassW(&wc)) return 1;
    HWND hwnd=CreateWindowW(wc.lpszClassName,L"NativeStep guard test",WS_OVERLAPPEDWINDOW,
        0,0,640,480,nullptr,nullptr,wc.hInstance,nullptr);
    if(!hwnd) return 2;
    HMODULE dll=LoadLibraryW(L".\\dinput.dll");
    if(!dll) return 3;
    auto start=reinterpret_cast<StartFn>(GetProcAddress(dll,"NativeStep_Start"));
    auto stop=reinterpret_cast<StopFn>(GetProcAddress(dll,"NativeStep_Stop"));
    if(!start || !stop) return 4;
    const int allowed=start(hwnd);
    // The bootstrap pins its module for the process lifetime.
    stop();DestroyWindow(hwnd);
    if(allowed) {std::puts("FAIL unsupported executable accepted");return 5;}
    std::puts("PASS unsupported executable refused; no mouse input generated");return 0;
}
