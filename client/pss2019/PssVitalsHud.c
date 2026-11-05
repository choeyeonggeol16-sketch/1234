#define UNICODE
#define _UNICODE
#include "PssVitalsHud.h"
#include "PssVitals.h"
#include "PssVitalsArt.h"
#include "PssActivityLayout.h"
#include "PssNativeDraw.h"
#include "PssBridge.h"
#include <string.h>
static HWND owner,window;
static HDC canvas;
static HBITMAP bitmap,old_bitmap;
static HFONT font;
static uint32_t* pixels;
static DWORD ui_thread;
static BOOL enabled,have_values;
static LONG active;
static int offset_x,top;
static RECT game_area,target;
static PssVitals previous;
static SRWLOCK guard=SRWLOCK_INIT;
static LRESULT CALLBACK procedure(HWND hwnd,UINT msg,WPARAM wp,LPARAM lp){
    if(msg==WM_NCHITTEST)return HTTRANSPARENT;
    if(msg==WM_MOUSEACTIVATE)return MA_NOACTIVATE;
    return DefWindowProcW(hwnd,msg,wp,lp);
}
BOOL PssVitalsHudActive(void){return InterlockedCompareExchange(&active,0,0)!=0;}
void PssVitalsHudDetach(void){
    if(ui_thread&&ui_thread!=GetCurrentThreadId())return;
    InterlockedExchange(&active,0);AcquireSRWLockExclusive(&guard);
    if(window)DestroyWindow(window);
    if(canvas&&old_bitmap)SelectObject(canvas,old_bitmap);
    if(bitmap)DeleteObject(bitmap);
    if(font)DeleteObject(font);
    if(canvas)DeleteDC(canvas);
    window=owner=0;canvas=0;bitmap=old_bitmap=0;font=0;pixels=0;enabled=have_values=FALSE;
    ReleaseSRWLockExclusive(&guard);PssVitalsReset();
}
void PssVitalsHudInitialize(HWND game,HMODULE module){
    if(GetWindowThreadProcessId(game,0)!=GetCurrentThreadId())return;
    PssVitalsHudDetach();ui_thread=GetCurrentThreadId();owner=game;
    wchar_t path[MAX_PATH];DWORD size=GetModuleFileNameW(module,path,MAX_PATH);
    if(!size||size>=MAX_PATH)return;
    wchar_t* last=path;
    for(wchar_t* p=path;*p;++p)if(*p==L'\\')last=p+1;
    if(last-path+12>=MAX_PATH)return;
    lstrcpyW(last,L"Pss2019.ini");
    enabled=GetPrivateProfileIntW(L"VitalsHud",L"Enabled",1,path)!=0;
    offset_x=(int)GetPrivateProfileIntW(L"VitalsHud",L"OffsetX",0,path);
    top=(int)GetPrivateProfileIntW(L"VitalsHud",L"Top",44,path);
    if(offset_x< -8192||offset_x>8192)offset_x=0;
    if(top<0||top>4096)top=44;
    if(!enabled)return;
    WNDCLASSW wc={0};wc.lpfnWndProc=procedure;wc.hInstance=module;wc.lpszClassName=L"Pss2019VitalsHud";
    if(RegisterClassW(&wc)||GetLastError()==ERROR_CLASS_ALREADY_EXISTS)
        window=CreateWindowExW(WS_EX_LAYERED|WS_EX_TRANSPARENT|WS_EX_NOACTIVATE|WS_EX_TOOLWINDOW,
            wc.lpszClassName,L"HP MP trial",WS_POPUP,0,0,PSS_VITALS_WIDTH,PSS_VITALS_HEIGHT,game,0,module,0);
    BITMAPINFO info={0};info.bmiHeader.biSize=sizeof(BITMAPINFOHEADER);
    info.bmiHeader.biWidth=PSS_VITALS_WIDTH;info.bmiHeader.biHeight=-PSS_VITALS_HEIGHT;
    info.bmiHeader.biPlanes=1;info.bmiHeader.biBitCount=32;info.bmiHeader.biCompression=BI_RGB;
    canvas=CreateCompatibleDC(0);
    if(canvas)bitmap=CreateDIBSection(canvas,&info,DIB_RGB_COLORS,(void**)&pixels,0,0);
    if(!bitmap){PssBridgeLog(L"HUD HP/MP bitmap creation failed");PssVitalsHudDetach();return;}
    old_bitmap=SelectObject(canvas,bitmap);
    font=CreateFontW(-12,0,0,0,FW_BOLD,FALSE,FALSE,FALSE,DEFAULT_CHARSET,OUT_DEFAULT_PRECIS,
        CLIP_DEFAULT_PRECIS,ANTIALIASED_QUALITY,DEFAULT_PITCH,L"Tahoma");
    PssBridgeLog(L"HUD HP/MP read-only trial ready; independent of PSS start; original HUD retained");
}
static void render(PssVitals value){
    PssVitalsArt(pixels,value.hp,value.max_hp,value.mp,value.max_mp);
    HFONT old=font?SelectObject(canvas,font):0;SetBkMode(canvas,TRANSPARENT);SetTextColor(canvas,RGB(255,244,221));
    wchar_t text[80];RECT hp={17,13,243,35},mp={277,13,503,35};
    wsprintfW(text,L"HP %ld / %ld",(long)value.hp,(long)value.max_hp);
    DrawTextW(canvas,text,-1,&hp,DT_CENTER|DT_VCENTER|DT_SINGLELINE|DT_NOPREFIX);
    wsprintfW(text,L"MP %ld / %ld",(long)value.mp,(long)value.max_mp);
    DrawTextW(canvas,text,-1,&mp,DT_CENTER|DT_VCENTER|DT_SINGLELINE|DT_NOPREFIX);
    if(old)SelectObject(canvas,old);
    GdiFlush();
    // GDI text writes RGB but not alpha; all painted panel pixels are opaque.
    for(unsigned n=0;n<PSS_VITALS_WIDTH*PSS_VITALS_HEIGHT;++n)
        if(pixels[n]&0xFFFFFFU)pixels[n]|=0xFF000000U;
}
void PssVitalsHudUpdate(BOOL in_world){
    if(GetCurrentThreadId()!=ui_thread||!enabled||!canvas)return;
    HWND foreground=GetForegroundWindow();RECT area={0};POINT first={0,0},last;
    BOOL show=in_world&&IsWindowVisible(owner)&&!IsIconic(owner)&&(foreground==owner||IsChild(owner,foreground));
    BOOL located=GetClientRect(owner,&area);last=(POINT){area.right,area.bottom};
    located=located&&ClientToScreen(owner,&first)&&ClientToScreen(owner,&last);
    PssVitals value;show=show&&located&&PssVitalsRead(&value);
    AcquireSRWLockExclusive(&guard);
    game_area=(RECT){first.x,first.y,last.x,last.y};
    target.left=first.x+(last.x-first.x-PSS_VITALS_WIDTH)/2+offset_x;
    target.top=first.y+top;target.right=target.left+PSS_VITALS_WIDTH;target.bottom=target.top+PSS_VITALS_HEIGHT;
    show=show&&target.left>=game_area.left&&target.right<=game_area.right&&target.top>=game_area.top&&target.bottom<=game_area.bottom;
    if(show&&(!have_values||memcmp(&previous,&value,sizeof(value)))){render(value);previous=value;have_values=TRUE;}
    if(!show)have_values=FALSE;
    InterlockedExchange(&active,show);
    if(window){
        if(!show||PssNativeDrawHudHasPainted())ShowWindow(window,SW_HIDE);
        else{
            POINT destination={target.left,target.top},source={0,0};SIZE size={PSS_VITALS_WIDTH,PSS_VITALS_HEIGHT};
            BLENDFUNCTION blend={AC_SRC_OVER,0,255,AC_SRC_ALPHA};
            if(UpdateLayeredWindow(window,0,&destination,&size,canvas,&source,0,&blend,ULW_ALPHA))ShowWindow(window,SW_SHOWNOACTIVATE);
        }
    }
    ReleaseSRWLockExclusive(&guard);
}
BOOL PssVitalsHudPaintSurface(HDC dc,const RECT* source,const RECT* destination){
    if(!dc||!PssVitalsHudActive()||!TryAcquireSRWLockExclusive(&guard))return FALSE;
    BOOL ok=FALSE;RECT clip,mapped;
    if(have_values&&GetClipBox(dc,&clip)!=ERROR){
        RECT view=source?*source:clip,display=destination?*destination:game_area;
        if(PssActivityMap(&target,&view,&display,&mapped)&&mapped.left>=clip.left&&mapped.top>=clip.top&&mapped.right<=clip.right&&mapped.bottom<=clip.bottom){
            BLENDFUNCTION blend={AC_SRC_OVER,0,255,AC_SRC_ALPHA};
            ok=AlphaBlend(dc,mapped.left,mapped.top,mapped.right-mapped.left,mapped.bottom-mapped.top,canvas,0,0,PSS_VITALS_WIDTH,PSS_VITALS_HEIGHT,blend);
            if(!ok)ok=TransparentBlt(dc,mapped.left,mapped.top,mapped.right-mapped.left,mapped.bottom-mapped.top,canvas,0,0,PSS_VITALS_WIDTH,PSS_VITALS_HEIGHT,RGB(0,0,0));
        }
    }
    ReleaseSRWLockExclusive(&guard);return ok;
}
