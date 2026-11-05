#define UNICODE
#define _UNICODE
#include "PssActivity.h"
#include <stdint.h>
#include <string.h>
/* A separate click-through activity indicator. It does not call undocumented
 * native UI functions or alter the PSS start/stop/movement state. */
enum {WIDTH=230,HEIGHT=64};
static HWND owner,window;
static HDC canvas;
static HBITMAP bitmap,old_bitmap;
static HFONT font;
static uint32_t* pixels;
static BOOL enabled;
static int offset_x,offset_y;
static DWORD ui_thread;
static LRESULT CALLBACK procedure(HWND hwnd,UINT msg,WPARAM wp,LPARAM lp){
    if(msg==WM_NCHITTEST)return HTTRANSPARENT;
    if(msg==WM_MOUSEACTIVATE)return MA_NOACTIVATE;
    return DefWindowProcW(hwnd,msg,wp,lp);
}
void PssActivityDetach(void){
    if(ui_thread&&GetCurrentThreadId()!=ui_thread)return;
    if(window)DestroyWindow(window);
    if(canvas&&old_bitmap)SelectObject(canvas,old_bitmap);
    if(bitmap)DeleteObject(bitmap);
    if(font)DeleteObject(font);
    if(canvas)DeleteDC(canvas);
    window=0;owner=0;canvas=0;bitmap=old_bitmap=0;font=0;pixels=0;enabled=FALSE;
}
void PssActivityInitialize(HWND game,HMODULE module){
    if(!IsWindow(game))return;
    DWORD thread=GetWindowThreadProcessId(game,0);
    if(thread!=GetCurrentThreadId())return;
    if(window&&owner==game)return;
    PssActivityDetach();ui_thread=thread;owner=game;
    wchar_t path[MAX_PATH];DWORD n=GetModuleFileNameW(module,path,MAX_PATH);
    if(!n||n>=MAX_PATH)return;
    wchar_t* last=path;
    for(wchar_t* p=path;*p;++p)if(*p==L'\\')last=p+1;
    if((SIZE_T)(last-path)+12>=MAX_PATH)return;
    lstrcpyW(last,L"Pss2019.ini");
    enabled=GetPrivateProfileIntW(L"PssIndicator",L"Enabled",1,path)!=0;
    offset_x=(int)GetPrivateProfileIntW(L"PssIndicator",L"OffsetX",0,path);
    offset_y=(int)GetPrivateProfileIntW(L"PssIndicator",L"OffsetY",20,path);
    if(offset_x < -8192||offset_x>8192)offset_x=0;
    if(offset_y < -8192||offset_y>8192)offset_y=20;
    if(!enabled)return;
    WNDCLASSW wc={0};wc.lpfnWndProc=procedure;wc.hInstance=module;wc.lpszClassName=L"Pss2019Activity";
    if(!RegisterClassW(&wc)&&GetLastError()!=ERROR_CLASS_ALREADY_EXISTS){enabled=FALSE;return;}
    window=CreateWindowExW(WS_EX_LAYERED|WS_EX_TRANSPARENT|WS_EX_NOACTIVATE|WS_EX_TOOLWINDOW,
        wc.lpszClassName,L"PSS activity",WS_POPUP,0,0,WIDTH,HEIGHT,game,0,module,0);
    if(!window){enabled=FALSE;return;}
    BITMAPINFO info;memset(&info,0,sizeof(info));info.bmiHeader.biSize=sizeof(BITMAPINFOHEADER);
    info.bmiHeader.biWidth=WIDTH;info.bmiHeader.biHeight=-HEIGHT;info.bmiHeader.biPlanes=1;
    info.bmiHeader.biBitCount=32;info.bmiHeader.biCompression=BI_RGB;
    canvas=CreateCompatibleDC(0);
    if(canvas)bitmap=CreateDIBSection(canvas,&info,DIB_RGB_COLORS,(void**)&pixels,0,0);
    if(!bitmap){PssActivityDetach();return;}
    old_bitmap=SelectObject(canvas,bitmap);
    font=CreateFontW(-14,0,0,0,FW_BOLD,FALSE,FALSE,FALSE,DEFAULT_CHARSET,
        OUT_DEFAULT_PRECIS,CLIP_DEFAULT_PRECIS,NONANTIALIASED_QUALITY,DEFAULT_PITCH,L"Malgun Gothic");
}
static void render(void){
    memset(pixels,0,WIDTH*HEIGHT*sizeof(*pixels));
    HBRUSH background=CreateSolidBrush(RGB(80,40,8));
    RECT bar={20,41,WIDTH-20,62};FillRect(canvas,&bar,background);DeleteObject(background);
    HBRUSH old_brush=SelectObject(canvas,GetStockObject(NULL_BRUSH));
    HPEN pen=CreatePen(PS_SOLID,2,RGB(185,126,45)),old_pen=SelectObject(canvas,pen);
    Ellipse(canvas,WIDTH/2-13,7,WIDTH/2+13,33);
    SelectObject(canvas,old_pen);DeleteObject(pen);
    /* Activity animation, not a countdown or progress percentage. */
    static const int x[8]={0,9,13,9,0,-9,-13,-9};
    static const int y[8]={-13,-9,0,9,13,9,0,-9};
    unsigned phase=(GetTickCount()/125U)&7U;
    pen=CreatePen(PS_SOLID,3,RGB(255,230,160));old_pen=SelectObject(canvas,pen);
    MoveToEx(canvas,WIDTH/2+x[phase],20+y[phase],0);
    LineTo(canvas,WIDTH/2+x[(phase+1)&7],20+y[(phase+1)&7]);
    SelectObject(canvas,old_pen);DeleteObject(pen);SelectObject(canvas,old_brush);
    HFONT old_font=font?SelectObject(canvas,font):0;
    SetBkMode(canvas,TRANSPARENT);SetTextColor(canvas,RGB(255,224,150));
    DrawTextW(canvas,L"플레이 서포트 동작 중",-1,&bar,DT_CENTER|DT_VCENTER|DT_SINGLELINE|DT_NOPREFIX);
    if(old_font)SelectObject(canvas,old_font);
    GdiFlush();
    for(unsigned i=0;i<WIDTH*HEIGHT;++i){
        uint32_t c=pixels[i]&0xFFFFFFU;
        if(c){uint32_t b=(c&255U)*235U/255U,g=((c>>8)&255U)*235U/255U,r=((c>>16)&255U)*235U/255U;
            pixels[i]=0xEB000000U|(r<<16)|(g<<8)|b;}
    }
}
void PssActivityUpdate(BOOL running){
    if(GetCurrentThreadId()!=ui_thread||!window||!enabled)return;
    HWND foreground=GetForegroundWindow();
    if(!running||!IsWindow(owner)||!IsWindowVisible(owner)||IsIconic(owner)||
       (foreground!=owner&&!IsChild(owner,foreground))){ShowWindow(window,SW_HIDE);return;}
    RECT area;
    if(!GetClientRect(owner,&area)){ShowWindow(window,SW_HIDE);return;}
    POINT center={(area.right-area.left)/2+offset_x,(area.bottom-area.top)/2+offset_y};
    if(!ClientToScreen(owner,&center)){ShowWindow(window,SW_HIDE);return;}
    POINT destination={center.x-WIDTH/2,center.y},source={0,0};SIZE size={WIDTH,HEIGHT};
    render();BLENDFUNCTION blend={AC_SRC_OVER,0,255,AC_SRC_ALPHA};
    if(UpdateLayeredWindow(window,0,&destination,&size,canvas,&source,0,&blend,ULW_ALPHA))
        ShowWindow(window,SW_SHOWNOACTIVATE);
}
