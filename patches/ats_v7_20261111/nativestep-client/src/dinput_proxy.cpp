// DirectInput 1-7 compatibility bridge for the game's existing dinput.dll import.
// The game keeps its original DirectInput semantics: all calls are forwarded to
// the *real Windows system DLL*. Never load a bare relative "dinput.dll" here.
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <unknwn.h>
#include <cwchar>

namespace {
INIT_ONCE load_once = INIT_ONCE_STATIC_INIT;
HMODULE windows_dinput = nullptr;

BOOL CALLBACK load_system_dinput(PINIT_ONCE, PVOID, PVOID*) noexcept {
    wchar_t full_path[MAX_PATH]{};
    const UINT len = GetSystemDirectoryW(full_path, MAX_PATH);
    if (len == 0 || len >= MAX_PATH) return FALSE;
    if (wcscat_s(full_path, MAX_PATH, L"\\dinput.dll") != 0) return FALSE;
    // For 32-bit clients on x64 Windows, GetSystemDirectoryW gives SysWOW64.
    // Loading the full path avoids loading this proxy recursively.
    windows_dinput = LoadLibraryW(full_path);
    return windows_dinput != nullptr;
}

FARPROC original(const char* name) noexcept {
    if (!InitOnceExecuteOnce(&load_once, load_system_dinput, nullptr, nullptr)) return nullptr;
    return windows_dinput ? GetProcAddress(windows_dinput, name) : nullptr;
}
}

extern "C" {
// Use pointer-to-pointer ABI-equivalent signatures, avoiding a dependency on
// DirectX SDK headers and their version-specific typedef macros.
HRESULT WINAPI DirectInputCreateA(HINSTANCE instance, DWORD version,
                                   void** out, LPUNKNOWN outer) noexcept {
    using Function = HRESULT (WINAPI*)(HINSTANCE, DWORD, void**, LPUNKNOWN);
    const auto fn = reinterpret_cast<Function>(original("DirectInputCreateA"));
    if (!fn) { if (out) *out = nullptr; return E_FAIL; }
    return fn(instance, version, out, outer);
}

HRESULT WINAPI DirectInputCreateEx(HINSTANCE instance, DWORD version, REFIID iid,
                                    void** out, LPUNKNOWN outer) noexcept {
    using Function = HRESULT (WINAPI*)(HINSTANCE, DWORD, REFIID, void**, LPUNKNOWN);
    const auto fn = reinterpret_cast<Function>(original("DirectInputCreateEx"));
    if (!fn) { if (out) *out = nullptr; return E_FAIL; }
    return fn(instance, version, iid, out, outer);
}

HRESULT WINAPI DirectInputCreateW(HINSTANCE instance, DWORD version,
                                   void** out, LPUNKNOWN outer) noexcept {
    using Function = HRESULT (WINAPI*)(HINSTANCE, DWORD, void**, LPUNKNOWN);
    const auto fn = reinterpret_cast<Function>(original("DirectInputCreateW"));
    if (!fn) { if (out) *out = nullptr; return E_FAIL; }
    return fn(instance, version, out, outer);
}

HRESULT WINAPI DllCanUnloadNow() {
    using Function = HRESULT (WINAPI*)();
    const auto fn = reinterpret_cast<Function>(original("DllCanUnloadNow"));
    return fn ? fn() : S_FALSE;
}

HRESULT WINAPI DllGetClassObject(REFCLSID clsid, REFIID iid, void** out) {
    using Function = HRESULT (WINAPI*)(REFCLSID, REFIID, void**);
    const auto fn = reinterpret_cast<Function>(original("DllGetClassObject"));
    if (!fn) { if (out) *out = nullptr; return CLASS_E_CLASSNOTAVAILABLE; }
    return fn(clsid, iid, out);
}

HRESULT WINAPI DllRegisterServer() noexcept {
    using Function = HRESULT (WINAPI*)();
    const auto fn = reinterpret_cast<Function>(original("DllRegisterServer"));
    return fn ? fn() : E_FAIL;
}

HRESULT WINAPI DllUnregisterServer() noexcept {
    using Function = HRESULT (WINAPI*)();
    const auto fn = reinterpret_cast<Function>(original("DllUnregisterServer"));
    return fn ? fn() : E_FAIL;
}
} // extern "C"
