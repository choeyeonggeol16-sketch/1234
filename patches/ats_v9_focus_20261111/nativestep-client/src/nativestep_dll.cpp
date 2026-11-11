// Mega.bin-specific native message adapter. OS cursor/button state is untouched.
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <atomic>
#include <cstdio>
#include "step_core.h"
#include "step_network.h"
#include "mega_client.h"
#include "click_points.h"
#include "ats_hud.h"
#include "input_scope.h"

namespace {
constexpr wchar_t kControlName[]=L"ClientInput.WalkControl.1";
constexpr UINT_PTR kTimerId=0x54A17301;
HWND game_window=nullptr;
std::atomic<HWND> network_window{nullptr};
DWORD game_thread=0;
UINT_PTR timer_id=0;
nativestep::StepEngine engine;
bool staged=false, pressed=false;
int staged_heading=-1;
bool waiting_logged=false;
MegaClick active_click{};
unsigned input_logs=0;
nativestep::ManualPressScope click_scope;
bool focus_known=false,last_focus=false;

bool on_client_thread() noexcept {return game_thread && game_thread==GetCurrentThreadId();}
bool foreground(HWND hwnd) noexcept {
    HWND f=GetForegroundWindow();
    return IsWindow(hwnd) && f && GetAncestor(f,GA_ROOTOWNER)==GetAncestor(hwnd,GA_ROOTOWNER);
}
struct MouseSample { bool down, manual; };
MouseSample poll_mouse() noexcept {
    const bool down=(GetAsyncKeyState(VK_LBUTTON)&0x8000)!=0;
    POINT screen{}; RECT client{}; bool inside=false;
    if(IsWindow(game_window) && GetCursorPos(&screen) && GetClientRect(game_window,&client)) {
        POINT local=screen;
        HWND at=WindowFromPoint(screen);
        const bool ours=at && (at==game_window || IsChild(game_window,at) ||
            GetAncestor(at,GA_ROOTOWNER)==GetAncestor(game_window,GA_ROOTOWNER));
        inside=ours && ScreenToClient(game_window,&local) && PtInRect(&client,local);
    }
    return {down,click_scope.sample(down,foreground(game_window),inside)};
}
void log_click(const char* stage,const MegaClick& click) noexcept {
    if(input_logs++>=128) return;
    char text[180]{};
    std::snprintf(text,sizeof(text),"%s direction=%d client=(%d,%d) logical=(%d,%d) mode=%d candidate=%d",
        stage,click.heading,click.client.x,click.client.y,click.logical.x,click.logical.y,click.mode,click.candidate);
    NativeStep_LogInput(text,0);
}
void release_input() noexcept {
    staged=false;staged_heading=-1;
    if(!pressed) return;
    pressed=false;
    if(IsWindow(game_window)) SendMessageW(game_window,WM_LBUTTONUP,0,
        MAKELPARAM(active_click.client.x,active_click.client.y));
    // Button-up alone does not clear a client's already selected walk target.
    // Use this exact client's own stop-target routine for the step we own.
    if(MegaGoalMatches(active_click)) MegaCancelOwnedMovement();
    log_click("native input released and target cancelled",active_click);
}
void stop_failed(const char* reason) noexcept {
    AtsHud_Hide();
    NativeStep_LogInput(reason,0);
    MegaLogState(active_click);
    const auto now=GetTickCount64();
    engine.submit(-4,now);(void)engine.tick(now,false);
    release_input();NativeStep_RequestManualStop();
    NativeStep_LogInput("native input stopped: unsupported state or target validation failed",0);
}
bool apply(const nativestep::Action& action) noexcept {
    if(action.type==nativestep::Action::none) return true;
    if(action.type==nativestep::Action::release) {release_input();return true;}
    if(action.type!=nativestep::Action::move_and_press || staged || pressed) return false;
    // Stage only the server's direction. finish_staged verifies every candidate
    // immediately before any DOWN, including when this window is not foreground.
    staged=true;staged_heading=action.heading;waiting_logged=false;
    if(input_logs++<128) NativeStep_LogInput("native direction staged awaiting validated tile",action.heading);
    return true;
}
void finish_staged() noexcept {
    if(!staged || !MegaMouseDownReady()) return;
    MegaClick click{};bool clear=false;
    // Try points inside the SAME authorized tile, checking each native inverse.
    // MOVE updates the client's hover context; no DOWN is sent during the search.
    for(unsigned i=0;i<nativestep::kClickPointCount;++i) {
        if(!MegaPlanClick(game_window,staged_heading,click,i)) continue;
        SendMessageW(game_window,WM_MOUSEMOVE,0,MAKELPARAM(click.client.x,click.client.y));
        if(MegaClickClear(click)) {clear=true;break;}
    }
    if(!clear || !MegaMouseDownReady()) {
        if(!waiting_logged) {
            NativeStep_LogInput("native click deferred: tile points obstructed or client action busy",0);
            MegaLogState(click);waiting_logged=true;
        }
        return;
    }
    if(poll_mouse().manual) {stop_failed("native manual stop: physical press began inside active game client");return;}
    // Validate the authorization BEFORE delivering a DOWN. V5 checked it
    // afterward and merged lease expiry with a different-target error.
    if(!engine.confirm_press(GetTickCount64())) {
        NativeStep_LogInput("native click skipped: authorization expired or cancelled before press",0);
        return;
    }
    staged=false;staged_heading=-1;active_click=click;pressed=true;
    SendMessageW(game_window,WM_LBUTTONDOWN,MK_LBUTTON,MAKELPARAM(click.client.x,click.client.y));
    if(!MegaGoalMatches(click) && !MegaStepApplied(click)) {
        stop_failed("native stop: click selected a different goal");return;
    }
    log_click("native next-tile input committed",click);
    if(MegaStepApplied(active_click)) release_input();
}
void tick() noexcept {
    if(!on_client_thread()) return;
    AtsHud_Tick();
    const MouseSample mouse=poll_mouse();
    const bool focused=foreground(game_window);
    if(engine.is_enabled() && (!focus_known || focused!=last_focus)) {
        NativeStep_LogInput(focused ? "native focus: foreground; auto mode retained" :
            "native focus: background; auto mode retained",0);
        focus_known=true;last_focus=focused;
    }
    const bool was_manual=engine.has_manual_override();
    const auto action=engine.tick(GetTickCount64(),mouse.down,mouse.manual);
    if(!was_manual && engine.has_manual_override()) {
        AtsHud_Hide();NativeStep_LogInput("native manual stop: in-client physical click",0);NativeStep_RequestManualStop();
    }
    if(!apply(action)) {stop_failed("native stop: action could not be staged");return;}
    if(staged) finish_staged();
    // End the local hold as soon as this one tile was applied. The logical
    // StepEngine still waits for server CANCEL before accepting another offer.
    if(pressed && MegaStepApplied(active_click)) release_input();
}
void submit(int command) noexcept {
    if(!on_client_thread()) return;
    if(command==-4) AtsHud_Hide();
    engine.submit(command,GetTickCount64());tick();
}
void shutdown_on_client_thread() noexcept {
    if(!on_client_thread()) return;
    NativeStep_NetworkStop();AtsHud_Stop();engine.shutdown();
    (void)engine.tick(GetTickCount64(),false);release_input();
    if(timer_id && IsWindow(game_window)) KillTimer(game_window,timer_id);
    timer_id=0;network_window.store(nullptr,std::memory_order_release);
    game_window=nullptr;game_thread=0;
}
}

void NativeStep_PostNetworkCommand(int command) {
    HWND hwnd=network_window.load(std::memory_order_acquire);
    if(IsWindow(hwnd) && command>=0 && command<=10) {
        UINT message=RegisterWindowMessageW(kControlName);
        if(message) PostMessageW(hwnd,message,static_cast<WPARAM>(command),0);
    }
}
void NativeStep_PostAtsStatus(unsigned seconds,bool active) {
    HWND hwnd=network_window.load(std::memory_order_acquire);
    UINT msg=RegisterWindowMessageW(L"ClientInput.AtsHud.1");
    if(IsWindow(hwnd) && msg && seconds<=ats::kMaxSeconds)
        PostMessageW(hwnd,msg,static_cast<WPARAM>(seconds),active?1:0);
}
extern "C" {
__declspec(dllexport) int __cdecl NativeStep_Start(HWND hwnd) {
    if(!IsWindow(hwnd)) return 0;
    DWORD pid=0,thread=GetWindowThreadProcessId(hwnd,&pid);
    if(pid!=GetCurrentProcessId() || thread!=GetCurrentThreadId()) return 0;
    if(game_window) return game_window==hwnd ? 1:0;
    if(!MegaInitialize()) return 0;
    UINT_PTR timer=SetTimer(hwnd,kTimerId,15,nullptr);
    if(!timer) return 0;
    engine=nativestep::StepEngine{};staged=pressed=false;input_logs=0;
    game_window=hwnd;game_thread=thread;timer_id=timer;
    click_scope=nativestep::ManualPressScope{};focus_known=false;
    network_window.store(hwnd,std::memory_order_release);
    NativeStep_LogInput("Mega native-tile-input-v9-focus-silent-20261111 initialized; OS cursor untouched",0);
    AtsHud_Start(hwnd);
    NativeStep_NetworkStart(nullptr);return 1;
}
__declspec(dllexport) int __cdecl NativeStep_TryConsumeNotice(int type,const void* bytes,int length) {
    if(!on_client_thread() || type!=9 || !bytes || length<0 || length>65536) return 0;
    int command=nativestep::parse_control(static_cast<const char*>(bytes),static_cast<size_t>(length));
    if(command==-3) return 0;
    submit(command);return 1;
}
__declspec(dllexport) int __cdecl NativeStep_HasManualOverride() {
    return on_client_thread() && engine.has_manual_override() ? 1:0;
}
__declspec(dllexport) void __cdecl NativeStep_Stop() {shutdown_on_client_thread();}
__declspec(dllexport) LRESULT CALLBACK NativeStep_HookProc(int code,WPARAM wp,LPARAM lp) {
    if(code>=0 && wp==PM_REMOVE && lp) {
        MSG* msg=reinterpret_cast<MSG*>(lp);
        static const UINT control=RegisterWindowMessageW(kControlName);
        static const UINT hud=RegisterWindowMessageW(L"ClientInput.AtsHud.1");
        if(hud && msg->message==hud && msg->hwnd==game_window && on_client_thread()) {
            if(msg->wParam<=ats::kMaxSeconds && (msg->lParam==0 || msg->lParam==1))
                AtsHud_Status({static_cast<uint32_t>(msg->wParam),msg->lParam==1});
            msg->message=WM_NULL;
        } else if(control && msg->message==control) {
            const unsigned command=static_cast<unsigned>(msg->wParam);
            HWND hwnd=msg->hwnd;msg->message=WM_NULL;
            if(command==12) {if(!game_window) (void)NativeStep_Start(hwnd);}
            else if(command<=10) {
                if(!game_window) (void)NativeStep_Start(hwnd);
                if(game_window==hwnd && on_client_thread())
                    submit(command==8 ? -1:command==9 ? -2:command==10 ? -4:static_cast<int>(command));
            }
        } else if(msg->message==WM_TIMER && timer_id && msg->hwnd==game_window && msg->wParam==timer_id) {
            tick();msg->message=WM_NULL;
        }
    }
    return CallNextHookEx(nullptr,code,wp,lp);
}
void NativeStep_Bootstrap(HMODULE dll);
BOOL WINAPI DllMain(HINSTANCE dll,DWORD reason,LPVOID) {
    if(reason==DLL_PROCESS_ATTACH) {DisableThreadLibraryCalls(dll);NativeStep_Bootstrap(dll);}
    return TRUE;
}
}
