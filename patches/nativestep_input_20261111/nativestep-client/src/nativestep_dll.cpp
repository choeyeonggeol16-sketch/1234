// User-enabled client input bridge. Mega.bin imports dinput.dll; the game loads this DLL via DirectInput.
// Movement is sent as actual Win32 mouse input, not a forged window message.
// Some clients use GetCursorPos/GetAsyncKeyState/DirectInput when interpreting a
// click. PostMessage(WM_LBUTTONDOWN) does not update any of those states.
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <cstdint>
#include <atomic>
#include <cstdio>
#include <cstring>
#include <cwchar>
#include "step_core.h"
#include "step_network.h"

namespace {
constexpr wchar_t kControlName[] = L"ClientInput.WalkControl.1";
constexpr UINT_PTR kTimerId = 0x54A17301;
constexpr UINT kTimerPeriodMs = 15;
constexpr unsigned kCancelCommand = 8;
constexpr unsigned kStartCommand = 9;
constexpr unsigned kStopCommand = 10;
constexpr unsigned kManualStopCommand = 11;
constexpr unsigned kInitializeCommand = 12;
constexpr ULONG_PTR kInputTag = static_cast<ULONG_PTR>(0x4E535450u); // 'NSTP'
constexpr ULONGLONG kButtonStateSettleMs = 100;
// A SetCursorPos warp must reach the game's WM_MOUSEMOVE handler BEFORE the
// next mouse-down. The old same-stack warp+down could use the previous cached
// mouse coordinates to calculate a different C_MoveChar heading.
constexpr ULONGLONG kCursorMoveSettleMs = 32;

HWND game_window = nullptr; // Only the game's UI thread may touch these fields.
std::atomic<HWND> network_window{nullptr};
DWORD game_thread = 0;
UINT_PTR timer_id = 0;
nativestep::StepEngine engine;
bool synthetic_left_down = false;
int synthetic_heading = -1;
bool release_retry_needed = false;
POINT synthetic_cursor{};
bool staged_press = false;
int staged_heading = -1;
ULONGLONG staged_at = 0;
RECT staged_client_rect{};
POINT staged_client_origin{};
ULONGLONG button_state_settle_until = 0;
unsigned input_logs = 0;
HMODULE self_module = nullptr;
wchar_t settings_path[MAX_PATH]{};
struct Calibration {
    nativestep::Point anchor{-1, -1};
    int width = 0, height = 0;
    int tile_width = 36, tile_height = 23;
} calibration;
bool game_is_foreground(HWND hwnd) noexcept;

void load_calibration() noexcept {
    if (!GetModuleFileNameW(self_module, settings_path, MAX_PATH)) return;
    wchar_t* slash = std::wcsrchr(settings_path, L'\\');
    if (!slash || wcscpy_s(slash + 1, MAX_PATH - (slash + 1 - settings_path),
                           L"NativeStep.ini") != 0) {
        settings_path[0] = 0;
        return;
    }
    const auto read = [](const wchar_t* key, int fallback) {
        return static_cast<int>(GetPrivateProfileIntW(L"Viewport", key, fallback, settings_path));
    };
    calibration.anchor = {read(L"AnchorX", -1), read(L"AnchorY", -1)};
    calibration.width = read(L"ClientWidth", 0);
    calibration.height = read(L"ClientHeight", 0);
    calibration.tile_width = read(L"HalfTileWidth", 36);
    calibration.tile_height = read(L"HalfTileHeight", 23);
}

bool calibrate_anchor(HWND hwnd) noexcept {
    if (engine.is_enabled() || !game_is_foreground(hwnd)) return false;
    POINT p{};
    RECT r{};
    if (!GetCursorPos(&p) || !ScreenToClient(hwnd, &p) || !GetClientRect(hwnd, &r)) return false;
    // All eight next tiles must remain in the client area.
    for (int h = 0; h < 8; ++h)
        if (nativestep::direction_point(h, r.right, r.bottom, {p.x, p.y},
            calibration.tile_width, calibration.tile_height).x < 0) return false;
    calibration.anchor = {p.x, p.y};
    calibration.width = r.right;
    calibration.height = r.bottom;
    const auto write = [](const wchar_t* key, int value) {
        wchar_t text[24]{};
        swprintf_s(text, 24, L"%d", value);
        return WritePrivateProfileStringW(L"Viewport", key, text, settings_path) != FALSE;
    };
    const bool saved = settings_path[0] &&
        write(L"AnchorX", p.x) && write(L"AnchorY", p.y) &&
        write(L"ClientWidth", r.right) && write(L"ClientHeight", r.bottom) &&
        write(L"HalfTileWidth", calibration.tile_width) &&
        write(L"HalfTileHeight", calibration.tile_height);
    NativeStep_LogInput(saved ? "player ground anchor calibrated and saved" :
                                   "player ground anchor calibrated for this session only", 0);
    return true;
}

void signal_named_event(const wchar_t* label) noexcept {
    wchar_t name[120]{};
    wsprintfW(name, L"Local\\NativeStep.%s.%lu", label, GetCurrentProcessId());
    HANDLE h = OpenEventW(EVENT_MODIFY_STATE, FALSE, name);
    if (h) { SetEvent(h); CloseHandle(h); }
}

bool game_is_foreground(HWND hwnd) noexcept {
    if (!IsWindow(hwnd)) return false;
    HWND foreground = GetForegroundWindow();
    return foreground != nullptr &&
           GetAncestor(foreground, GA_ROOT) == GetAncestor(hwnd, GA_ROOT);
}

void log_click(const char* stage, int heading, const POINT& screen) noexcept {
    if (input_logs++ >= 32) return;
    char message[116]{};
    std::snprintf(message, sizeof(message), "%s direction=%d screen=(%ld,%ld)",
                  stage, heading, static_cast<long>(screen.x), static_cast<long>(screen.y));
    NativeStep_LogInput(message, 0);
}

// Must release a *real* synthetic button with SendInput too. Posting WM_LBUTTONUP
// leaves the OS button state down and can cause dragging/extra walking.
bool release_mouse() noexcept {
    if (!synthetic_left_down) return true;
    INPUT input{};
    input.type = INPUT_MOUSE;
    input.mi.dwFlags = MOUSEEVENTF_LEFTUP;
    input.mi.dwExtraInfo = kInputTag;
    if (SendInput(1, &input, sizeof(input)) != 1) {
        NativeStep_LogInput("SendInput left-up failed", static_cast<int>(GetLastError()));
        release_retry_needed = true;
        return false;
    }
    synthetic_left_down = false;
    release_retry_needed = false;
    button_state_settle_until = GetTickCount64() + kButtonStateSettleMs;
    log_click("physical click up", synthetic_heading, synthetic_cursor);
    synthetic_heading = -1;
    return true;
}

// The Windows cursor MUST be moved as well as the mouse button. The old code
// posted a desired lParam to Mega.bin while the OS cursor remained elsewhere:
// a client using GetCursorPos/DirectInput could always walk toward the old
// pointer, producing heading=2 regardless of a server direction=1 or 5.
// Stage the cursor warp on the UI thread, then allow the game to process a
// posted WM_MOUSEMOVE BEFORE injecting the mouse-down from a later timer tick.
// This is an input-ordering fix; it does not guess or relearn headings.
bool stage_mouse(HWND hwnd, int heading) noexcept {
    if (!game_is_foreground(hwnd) || synthetic_left_down || staged_press) return false;
    RECT r{};
    if (!GetClientRect(hwnd, &r)) return false;
    POINT origin{};
    if (!ClientToScreen(hwnd, &origin)) return false;
    if (calibration.width != r.right-r.left || calibration.height != r.bottom-r.top) {
        NativeStep_LogInput("movement blocked: calibrate feet with Ctrl+Alt+F9 while hunt is OFF", heading);
        return false;
    }
    const auto point = nativestep::direction_point(heading, r.right-r.left, r.bottom-r.top,
        calibration.anchor, calibration.tile_width, calibration.tile_height);
    if (point.x < 0 || point.y < 0) {
        NativeStep_LogInput("movement blocked: anchor or tile geometry outside viewport", heading);
        return false;
    }
    POINT screen{point.x, point.y};
    if (!ClientToScreen(hwnd, &screen)) return false;
    if (!SetCursorPos(screen.x, screen.y)) {
        NativeStep_LogInput("SetCursorPos failed", static_cast<int>(GetLastError()));
        return false;
    }
    POINT actual{};
    if (!GetCursorPos(&actual) ||
        actual.x < screen.x - 3 || actual.x > screen.x + 3 ||
        actual.y < screen.y - 3 || actual.y > screen.y + 3 ||
        !game_is_foreground(hwnd)) {
        NativeStep_LogInput("cursor did not reach game target", heading);
        return false;
    }
    // The game may calculate walk heading from the cursor position cached in
    // its WndProc. Post the move now; emit the button only on a later UI tick.
    // Actual physical cursor is already at exactly this location.
    if (!PostMessageW(hwnd, WM_MOUSEMOVE, 0, MAKELPARAM(point.x, point.y))) {
        NativeStep_LogInput("mouse move message failed", static_cast<int>(GetLastError()));
        return false;
    }
    synthetic_cursor = screen;
    staged_heading = heading;
    staged_at = GetTickCount64();
    staged_client_rect = r;
    staged_client_origin = origin;
    staged_press = true;
    log_click("cursor staged", heading, screen);
    return true;
}

bool finish_staged_mouse(HWND hwnd, ULONGLONG now) noexcept {
    if (!staged_press || now - staged_at < kCursorMoveSettleMs) return true;
    if (!game_is_foreground(hwnd) || synthetic_left_down) return false;
    RECT rect{};
    POINT origin{};
    if (!GetClientRect(hwnd, &rect) || !ClientToScreen(hwnd, &origin) ||
        !EqualRect(&rect, &staged_client_rect) ||
        origin.x != staged_client_origin.x || origin.y != staged_client_origin.y) {
        NativeStep_LogInput("game viewport changed before click", staged_heading);
        return false;
    }
    POINT actual{};
    if (!GetCursorPos(&actual) ||
        actual.x < synthetic_cursor.x - 3 || actual.x > synthetic_cursor.x + 3 ||
        actual.y < synthetic_cursor.y - 3 || actual.y > synthetic_cursor.y + 3) {
        NativeStep_LogInput("cursor changed before click", staged_heading);
        return false;
    }
    INPUT input{};
    input.type = INPUT_MOUSE;
    input.mi.dwFlags = MOUSEEVENTF_LEFTDOWN;
    input.mi.dwExtraInfo = kInputTag;
    if (SendInput(1, &input, sizeof(input)) != 1) {
        NativeStep_LogInput("SendInput left-down failed", static_cast<int>(GetLastError()));
        return false;
    }
    const int heading = staged_heading;
    staged_press = false;
    staged_heading = -1;
    synthetic_left_down = true;
    synthetic_heading = heading;
    if (!engine.confirm_press(now)) {
        (void)release_mouse();
        return false;
    }
    log_click("physical click down", heading, synthetic_cursor);
    return true;
}

// Do NOT read a SendInput-generated left-button-down as a human override.
// A real manual pointer movement away from the synthetic target still stops
// automation while the injected button is held.
bool physical_override(ULONGLONG now) noexcept {
    if (synthetic_left_down) {
        POINT actual{};
        if (!GetCursorPos(&actual)) return true;
        return actual.x < synthetic_cursor.x - 24 ||
               actual.x > synthetic_cursor.x + 24 ||
               actual.y < synthetic_cursor.y - 24 ||
               actual.y > synthetic_cursor.y + 24;
    }
    if (now < button_state_settle_until) return false;
    return (GetAsyncKeyState(VK_LBUTTON) & 0x8000) != 0;
}

bool apply_action(HWND hwnd, const nativestep::Action& action) noexcept {
    if (action.type == nativestep::Action::none) return true;
    if (action.type == nativestep::Action::release) {
        staged_press = false;
        staged_heading = -1;
        return release_mouse();
    }
    if (action.type == nativestep::Action::move_and_press)
        return stage_mouse(hwnd, action.heading);
    // This mode is unused in the single-ACK movement protocol. Never drag
    // the OS cursor to a new heading during one authorized step.
    return false;
}

// The physical OS button can be up while StepEngine still believes it is held.
// A failed click/foreground-loss MUST drain that logical state before START
// can arm a fresh offer. Otherwise the next move is silently swallowed.
void stop_failed_input(HWND hwnd, ULONGLONG now) noexcept {
    NativeStep_LogInput("automation stopped: input failed or viewport changed", 0);
    staged_press = false;
    staged_heading = -1;
    engine.submit(-4, now);
    (void)apply_action(hwnd, engine.tick(now, false));
    (void)release_mouse();
    NativeStep_RequestManualStop();
}

void on_timer(HWND hwnd) noexcept {
    if (hwnd != game_window || GetCurrentThreadId() != game_thread) return;
    if (release_retry_needed) (void)release_mouse();
    if ((synthetic_left_down || staged_press) && !game_is_foreground(hwnd)) {
        stop_failed_input(hwnd, GetTickCount64());
        NativeStep_LogInput("automation stopped: game lost focus", 0);
        return;
    }
    const ULONGLONG now = GetTickCount64();
    const bool manual = physical_override(now);
    const bool previously_manual = engine.has_manual_override();
    const auto action = engine.tick(now, manual);
    if (!previously_manual && engine.has_manual_override()) NativeStep_RequestManualStop();
    if (!apply_action(hwnd, action) ||
            (action.type == nativestep::Action::none &&
             !finish_staged_mouse(hwnd, now))) {
        stop_failed_input(hwnd, now);
    }
    // If a prior release was blocked, never leave the OS left button held.
    if (synthetic_left_down && action.type == nativestep::Action::release)
        (void)release_mouse();
}

bool on_client_thread() noexcept {
    return game_thread != 0 && game_thread == GetCurrentThreadId();
}

void shutdown_on_client_thread() noexcept {
    if (!on_client_thread()) return;
    NativeStep_NetworkStop();
    staged_press = false;
    staged_heading = -1;
    engine.shutdown();
    (void)engine.tick(GetTickCount64(), false);
    for (int attempt = 0; attempt < 3 && synthetic_left_down; ++attempt)
        (void)release_mouse();
    if (timer_id && IsWindow(game_window)) KillTimer(game_window, timer_id);
    timer_id = 0;
    network_window.store(nullptr, std::memory_order_release);
    game_window = nullptr;
    game_thread = 0;
    signal_named_event(L"Stopped");
}
} // namespace

void NativeStep_PostNetworkCommand(int command) {
    HWND hwnd = network_window.load(std::memory_order_acquire);
    if (IsWindow(hwnd) && command >= 0 && command <= 10) {
        const UINT message = RegisterWindowMessageW(kControlName);
        if (message) PostMessageW(hwnd, message, static_cast<WPARAM>(command), 0);
    }
}

extern "C" {
__declspec(dllexport) int __cdecl NativeStep_Start(HWND hwnd) {
    if (!IsWindow(hwnd)) return 0;
    DWORD pid = 0;
    DWORD tid = GetWindowThreadProcessId(hwnd, &pid);
    if (pid != GetCurrentProcessId() || tid != GetCurrentThreadId()) return 0;
    if (game_window != nullptr) return game_window == hwnd ? 1 : 0;
    const UINT_PTR timer = SetTimer(hwnd, kTimerId, kTimerPeriodMs, nullptr);
    if (!timer) return 0;
    engine = nativestep::StepEngine{};
    synthetic_left_down = false;
    synthetic_heading = -1;
    release_retry_needed = false;
    staged_press = false;
    staged_heading = -1;
    button_state_settle_until = 0;
    input_logs = 0;
    load_calibration();
    game_window = hwnd;
    network_window.store(hwnd, std::memory_order_release);
    game_thread = tid;
    timer_id = timer;
    signal_named_event(L"Ready");
    NativeStep_LogInput("physical SendInput confirmed-press-20261111 control initialized", 0);
    NativeStep_NetworkStart(nullptr);
    return 1;
}

// This must be called on the client window thread, after client packet decoding.
__declspec(dllexport) int __cdecl NativeStep_TryConsumeNotice(
        int noticeType, const void* bytes, int length) {
    if (!on_client_thread() || noticeType != 9 || !bytes || length < 0 || length > 65536) return 0;
    const int command = nativestep::parse_control(static_cast<const char*>(bytes),
                                                   static_cast<size_t>(length));
    if (command == -3) return 0;
    if (command == -4 || command == -2 || command == -1 || (command >= 0 && command <= 7)) {
        engine.submit(command, GetTickCount64());
        if (!apply_action(game_window, engine.tick(GetTickCount64(),
                physical_override(GetTickCount64())))) {
            stop_failed_input(game_window, GetTickCount64());
        }
        return 1;
    }
    return 0;
}

__declspec(dllexport) int __cdecl NativeStep_HasManualOverride() {
    return on_client_thread() && engine.has_manual_override() ? 1 : 0;
}

__declspec(dllexport) void __cdecl NativeStep_Stop() {
    shutdown_on_client_thread();
}

// WH_GETMESSAGE callback loaded by Windows in the target GUI process.
// It only passes control commands, never forges game mouse messages.
__declspec(dllexport) LRESULT CALLBACK NativeStep_HookProc(int code, WPARAM wp, LPARAM lp) {
    if (code >= 0 && wp == PM_REMOVE && lp != 0) {
        MSG* msg = reinterpret_cast<MSG*>(lp);
        static const UINT control = RegisterWindowMessageW(kControlName);
        if (control != 0 && msg->message == control) {
            const unsigned command = static_cast<unsigned>(msg->wParam);
            HWND hwnd = msg->hwnd;
            msg->message = WM_NULL;
            if (command == kInitializeCommand) {
                if (game_window == nullptr) (void)NativeStep_Start(hwnd);
            } else if (command == kStopCommand) {
                if (game_window == hwnd && on_client_thread()) {
                    engine.submit(-4, GetTickCount64());
                    staged_press = false;
                    staged_heading = -1;
                    (void)engine.tick(GetTickCount64(), false);
                    (void)release_mouse();
                }
            } else if (command == kManualStopCommand) {
                if (game_window == hwnd && on_client_thread()) {
                    const bool previously_manual = engine.has_manual_override();
                    staged_press = false;
                    staged_heading = -1;
                    (void)engine.tick(GetTickCount64(), true);
                    if (!previously_manual && engine.has_manual_override()) NativeStep_RequestManualStop();
                    (void)release_mouse();
                }
            } else if (command <= 7 || command == kCancelCommand || command == kStartCommand) {
                if (game_window == nullptr) (void)NativeStep_Start(hwnd);
                if (game_window == hwnd && on_client_thread()) {
                    const ULONGLONG now = GetTickCount64();
                    engine.submit(command == kStartCommand ? -2 :
                                  command == kCancelCommand ? -1 : static_cast<int>(command), now);
                    const bool previously_manual = engine.has_manual_override();
                    const auto action = engine.tick(now, physical_override(now));
                    if (!previously_manual && engine.has_manual_override()) NativeStep_RequestManualStop();
                    if (!apply_action(game_window, action)) {
                        stop_failed_input(game_window, now);
                    }
                }
            }
        } else if ((msg->message == WM_KEYDOWN || msg->message == WM_SYSKEYDOWN) &&
                   msg->wParam == VK_F9 &&
                   msg->hwnd == game_window && !(msg->lParam & (1L << 30)) &&
                   (GetAsyncKeyState(VK_CONTROL) & 0x8000) &&
                   (GetAsyncKeyState(VK_MENU) & 0x8000)) {
            if (!calibrate_anchor(game_window))
                NativeStep_LogInput("anchor calibration rejected: stop hunt and point at player feet", 0);
            msg->message = WM_NULL;
        } else if (msg->message == WM_TIMER && timer_id != 0 &&
                   msg->hwnd == game_window && msg->wParam == timer_id) {
            on_timer(game_window);
            msg->message = WM_NULL;
        }
    }
    return CallNextHookEx(nullptr, code, wp, lp);
}

void NativeStep_Bootstrap(HMODULE dll);
BOOL WINAPI DllMain(HINSTANCE dll, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) {
        self_module = dll;
        DisableThreadLibraryCalls(dll);
        NativeStep_Bootstrap(dll);
    }
    return TRUE;
}
} // extern C
