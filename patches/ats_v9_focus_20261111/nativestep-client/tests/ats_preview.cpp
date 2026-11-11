#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include "ats_hud.h"
void NativeStep_LogInput(const char*,int) {}
namespace {
bool active=true; unsigned left=70380; ULONGLONG started=0;
unsigned current() {const auto elapsed=active?(GetTickCount64()-started)/1000:0;return elapsed>=left?0:left-static_cast<unsigned>(elapsed);}
LRESULT CALLBACK proc(HWND h,UINT m,WPARAM w,LPARAM l) {
    if(m==WM_PAINT) {
        PAINTSTRUCT p{};HDC dc=BeginPaint(h,&p);RECT r{};GetClientRect(h,&r);
        HBRUSH b=CreateSolidBrush(RGB(25,29,31));FillRect(dc,&r,b);DeleteObject(b);
        SetBkMode(dc,TRANSPARENT);SetTextColor(dc,RGB(220,220,220));
        const wchar_t* text=L"ATS HUD Preview\nSpace: pause/resume (remaining time is retained)\nEsc: close\nMove / resize / minimize this window to check HUD tracking.";
        r.left+=20;r.top+=20;DrawTextW(dc,text,-1,&r,DT_LEFT);EndPaint(h,&p);return 0;
    }
    if(m==WM_TIMER) {
        AtsHud_Status({current(),active});AtsHud_Tick();return 0;
    }
    if(m==WM_KEYDOWN && w==VK_SPACE) {
        left=current();active=!active;started=GetTickCount64();
        if(!active) AtsHud_Hide();
        return 0;
    }
    if(m==WM_KEYDOWN && w==VK_ESCAPE){DestroyWindow(h);return 0;}
    if(m==WM_DESTROY){AtsHud_Stop();PostQuitMessage(0);return 0;}
    return DefWindowProcW(h,m,w,l);
}
}
int WINAPI WinMain(HINSTANCE instance,HINSTANCE,LPSTR,int) {
    WNDCLASSW c{};c.lpfnWndProc=proc;c.hInstance=instance;c.lpszClassName=L"ATSStandalonePreview";
    if(!RegisterClassW(&c))return 1;
    HWND h=CreateWindowW(c.lpszClassName,L"ATS HUD Preview - no game connection",WS_OVERLAPPEDWINDOW|WS_VISIBLE,
        CW_USEDEFAULT,CW_USEDEFAULT,976,759,nullptr,nullptr,instance,nullptr);
    if(!h)return 2;
    AtsHud_Start(h);started=GetTickCount64();SetTimer(h,1,33,nullptr);
    MSG msg{};while(GetMessageW(&msg,nullptr,0,0)>0){TranslateMessage(&msg);DispatchMessageW(&msg);}return 0;
}
