#define UNICODE
#define _UNICODE
#include <windows.h>
#include <assert.h>
#include <stdio.h>
#include "PssActivity.h"
// Windowed visual fixture: isolate the optional DirectDraw hook.
void PssBridgeLog(const wchar_t* text){(void)text;}
BOOL PssNativeDrawInitialize(void){return FALSE;}
BOOL PssNativeDrawHasPainted(void){return FALSE;}
void PssNativeDrawSetActive(BOOL active){(void)active;}
void PssNativeDrawDetach(void){}
static void focus(HWND game){
    ShowWindow(game,SW_SHOWNORMAL);SetForegroundWindow(game);SetActiveWindow(game);
    MSG m;while(PeekMessageW(&m,0,0,0,PM_REMOVE)){TranslateMessage(&m);DispatchMessageW(&m);}
}
int main(void){
    HWND game=CreateWindowExW(0,L"STATIC",L"Activity fixture",WS_OVERLAPPEDWINDOW,50,50,800,600,0,0,GetModuleHandleW(0),0);
    assert(game);focus(game);assert(GetForegroundWindow()==game);
    PssActivityInitialize(game,GetModuleHandleW(0));
    HWND indicator=FindWindowW(L"Pss2019Activity",0);assert(indicator&&!IsWindowVisible(indicator));
    PssActivityUpdate(FALSE);assert(!IsWindowVisible(indicator));
    PssActivityUpdate(TRUE);assert(IsWindowVisible(indicator));
    LONG style=GetWindowLongW(indicator,GWL_EXSTYLE);
    assert((style&(WS_EX_LAYERED|WS_EX_TRANSPARENT|WS_EX_NOACTIVATE|WS_EX_TOOLWINDOW))==
        (WS_EX_LAYERED|WS_EX_TRANSPARENT|WS_EX_NOACTIVATE|WS_EX_TOOLWINDOW));
    assert((HWND)GetWindowLongPtrW(indicator,GWLP_HWNDPARENT)==game);
    assert(GetForegroundWindow()==game);
    RECT before,after;assert(GetWindowRect(indicator,&before));
    for(int n=0;n<100;++n)PssActivityUpdate(TRUE);
    assert(FindWindowW(L"Pss2019Activity",0)==indicator);
    MoveWindow(game,100,80,800,600,FALSE);PssActivityUpdate(TRUE);
    assert(GetWindowRect(indicator,&after)&&after.left-before.left==50&&after.top-before.top==30);
    PssActivityUpdate(FALSE);assert(!IsWindowVisible(indicator));
    HWND other=CreateWindowExW(0,L"STATIC",L"Other fixture",WS_OVERLAPPEDWINDOW,0,0,320,240,0,0,GetModuleHandleW(0),0);
    assert(other);focus(other);PssActivityUpdate(TRUE);assert(!IsWindowVisible(indicator));
    DestroyWindow(other);focus(game);PssActivityUpdate(TRUE);assert(IsWindowVisible(indicator));
    PssActivityDetach();assert(!FindWindowW(L"Pss2019Activity",0));
    PssActivityInitialize(game,GetModuleHandleW(0));assert(FindWindowW(L"Pss2019Activity",0));
    PssActivityDetach();DestroyWindow(game);
    puts("PASS active/inactive display, click-through/no-focus styles, game-relative position, window reuse, foreground hiding and detach/reinitialize");
}
