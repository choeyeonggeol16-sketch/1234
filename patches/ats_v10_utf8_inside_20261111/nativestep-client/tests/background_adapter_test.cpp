// Runs the REAL message adapter + StepEngine with native-game and network fixtures.
// This does not emulate Mega.bin, a real game server, or native game memory.
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <cstdio>
#include "mega_client.h"
#include "step_network.h"
#include "ats_hud.h"
extern "C" int __cdecl NativeStep_Start(HWND);
extern "C" void __cdecl NativeStep_Stop();
extern "C" int __cdecl NativeStep_HasManualOverride();
extern "C" LRESULT CALLBACK NativeStep_HookProc(int,WPARAM,LPARAM);
namespace {
HWND game=nullptr,other=nullptr;int downs=0,ups=0,otherDowns=0,stops=0;
bool applied=false,goal=false,ready=true;
LRESULT CALLBACK proc(HWND w,UINT m,WPARAM a,LPARAM b){
    if(m==WM_LBUTTONDOWN){if(w==game){++downs;applied=goal=true;}else ++otherDowns;return 0;}
    if(m==WM_LBUTTONUP){if(w==game)++ups;return 0;}
    return DefWindowProcW(w,m,a,b);
}
void pump(unsigned duration){const auto until=GetTickCount64()+duration;MSG msg{};
    while(GetTickCount64()<until){while(PeekMessageW(&msg,nullptr,0,0,PM_REMOVE)){TranslateMessage(&msg);DispatchMessageW(&msg);}Sleep(1);}}
bool check(bool b,const char* s){if(!b)std::printf("FAIL %s\n",s);return b;}
}
bool MegaInitialize() noexcept{return true;}
bool MegaPlanClick(HWND h,int heading,MegaClick& c,unsigned candidate) noexcept {
    if(h!=game||heading<0||heading>7||candidate>=10)return false;
    applied=goal=false;c={{120,120},{120,120},{100+heading,100},heading,1,static_cast<int>(candidate)};return true;
}
bool MegaMouseDownReady() noexcept{return ready;}
bool MegaClickClear(const MegaClick&) noexcept{return true;}
void MegaLogState(const MegaClick&) noexcept{}
bool MegaGoalMatches(const MegaClick&) noexcept{return goal;}
bool MegaStepApplied(const MegaClick&) noexcept{return applied;}
void MegaCancelOwnedMovement() noexcept{goal=false;}
void NativeStep_NetworkStart(HMODULE){}
void NativeStep_NetworkStop(){}
void NativeStep_LogInput(const char*,int){}
void NativeStep_RequestManualStop(){++stops;}
extern "C" void NativeStep_Bootstrap(HMODULE){}
void AtsHud_Start(HWND) noexcept{}
void AtsHud_Status(ats::Status) noexcept{}
void AtsHud_Tick() noexcept{}
void AtsHud_Hide() noexcept{}
void AtsHud_Stop() noexcept{}
int main(){
    WNDCLASSW wc{};wc.hInstance=GetModuleHandleW(nullptr);wc.lpfnWndProc=proc;wc.lpszClassName=L"V9AdapterFixture";
    if(!RegisterClassW(&wc))return 1;
    game=CreateWindowW(wc.lpszClassName,L"V9 game fixture",WS_OVERLAPPEDWINDOW|WS_VISIBLE,10,10,320,240,nullptr,nullptr,wc.hInstance,nullptr);
    other=CreateWindowW(wc.lpszClassName,L"V9 other application fixture",WS_OVERLAPPEDWINDOW|WS_VISIBLE,500,10,320,240,nullptr,nullptr,wc.hInstance,nullptr);
    if(!game||!other)return 2;
    HHOOK hook=SetWindowsHookExW(WH_GETMESSAGE,NativeStep_HookProc,nullptr,GetCurrentThreadId());
    if(!hook||!NativeStep_Start(game))return 3;
    SetForegroundWindow(other);pump(80);POINT before{},after{};GetCursorPos(&before);
    if(!check(GetForegroundWindow()==other,"fixture has background game"))return 4;
    NativeStep_PostNetworkCommand(9);pump(25);NativeStep_PostNetworkCommand(2);pump(80);
    if(!check(downs==1&&ups==1&&stops==0,"one background step without stopping"))return 5;
    if(!check(GetForegroundWindow()==other&&otherDowns==0,"no focus theft or messages delivered to other app"))return 6;
    GetCursorPos(&after);if(!check(before.x==after.x&&before.y==after.y,"OS cursor untouched"))return 7;
    NativeStep_PostNetworkCommand(8);pump(20);ready=false;
    NativeStep_PostNetworkCommand(3);pump(500);
    if(!check(downs==1&&stops==0,"busy UI expires silently instead of disabling mode"))return 8;
    NativeStep_PostNetworkCommand(8);pump(20);ready=true;
    NativeStep_PostNetworkCommand(4);pump(80);
    if(!check(downs==2&&ups==2&&stops==0,"fresh authorization resumes after busy interval"))return 9;
    if(!check(!NativeStep_HasManualOverride(),"no manual stop in background"))return 10;
    NativeStep_Stop();UnhookWindowsHookEx(hook);DestroyWindow(game);DestroyWindow(other);
    std::puts("PASS real Win32 message adapter under Wine64: background step/no focus theft/no OS cursor change/busy expiry/fresh resume");
    return 0;
}
