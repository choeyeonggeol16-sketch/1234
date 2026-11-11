// Client-side auto-initialization after Mega.bin's own loader imports dinput.dll.
// This does not claim compatibility with executables that do NOT load dinput.dll.
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <cstdio>
#include <cstring>

extern "C" LRESULT CALLBACK NativeStep_HookProc(int, WPARAM, LPARAM);

namespace {
constexpr wchar_t kCommandName[] = L"ClientInput.WalkControl.1";
constexpr UINT kInitialize = 12;

// Diagnostic log contains no hostnames, addresses, account names or port
// values. It lets a player distinguish a DLL that was never loaded from a
// DLL that loaded but could not attach its UI hook.
void trace_loader(HMODULE dll, const char* event, DWORD status = 0) {
    char file[MAX_PATH]{};
    if (!GetModuleFileNameA(dll, file, MAX_PATH)) return;
    char* separator = std::strrchr(file, '\\');
    if (!separator) return;
    if (strcpy_s(separator + 1, MAX_PATH - (separator + 1 - file),
                 "client_io.log") != 0) return;
    FILE* out = nullptr;
    if (fopen_s(&out, file, "a") == 0 && out) {
        std::fprintf(out, "%s status=%lu\n", event,
                     static_cast<unsigned long>(status));
        std::fclose(out);
    }
}

struct WindowSelection {
    DWORD processId;
    HWND window;
    unsigned long long area;
};

BOOL CALLBACK chooseWindow(HWND window, LPARAM context) {
    auto* selected = reinterpret_cast<WindowSelection*>(context);
    DWORD owner = 0;
    GetWindowThreadProcessId(window, &owner);
    if (owner != selected->processId || !IsWindowVisible(window) || GetWindow(window, GW_OWNER)) return TRUE;
    RECT r{};
    if (!GetWindowRect(window, &r)) return TRUE;
    const LONG width = r.right - r.left, height = r.bottom - r.top;
    if (width < 320 || height < 200) return TRUE;
    const auto area = static_cast<unsigned long long>(width) * static_cast<unsigned long long>(height);
    if (area > selected->area) {
        selected->window = window;
        selected->area = area;
    }
    return TRUE;
}

DWORD WINAPI loaderWorker(LPVOID data) {
    auto* dll = static_cast<HMODULE>(data);
    trace_loader(dll, "module loaded");
    HMODULE pinned = nullptr;
    // An installed GUI hook or a receiving network worker must never run
    // after its code has been unloaded by the host process.
    if (!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_PIN | GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,
            reinterpret_cast<LPCWSTR>(&NativeStep_HookProc), &pinned)) return 0;
    const UINT msg = RegisterWindowMessageW(kCommandName);
    if (!msg) return 0;

    // The main game window may appear seconds after DirectInput was loaded,
    // and can be recreated on logout/login. Re-discover it without external tools.
    while (true) {
        WindowSelection best{ GetCurrentProcessId(), nullptr, 0 };
        EnumWindows(chooseWindow, reinterpret_cast<LPARAM>(&best));
        if (!best.window) { Sleep(500); continue; }
        const DWORD thread = GetWindowThreadProcessId(best.window, nullptr);
        if (!thread) { Sleep(500); continue; }
        HHOOK hook = SetWindowsHookExW(WH_GETMESSAGE, NativeStep_HookProc, dll, thread);
        if (!hook) {
            static unsigned errors = 0;
            if (errors++ == 0) trace_loader(dll, "ui hook failed", GetLastError());
            Sleep(1000);
            continue;
        }
        trace_loader(dll, "ui hook installed");
        if (!PostMessageW(best.window, msg, kInitialize, 0))
            trace_loader(dll, "ui initialization post failed", GetLastError());

        // Re-hook only when the real game window is destroyed/replaced.
        while (IsWindow(best.window)) Sleep(500);
        // If the UI loop was destroyed, OS hooks are no longer useful.
        UnhookWindowsHookEx(hook);
    }
}
} // namespace

extern "C" void NativeStep_Bootstrap(HMODULE dll) {
    // Called once by DllMain. Do not wait on the worker from the loader lock.
    HANDLE worker = CreateThread(nullptr, 0, loaderWorker, dll, 0, nullptr);
    if (worker) CloseHandle(worker);
}
