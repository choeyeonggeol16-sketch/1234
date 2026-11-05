#define UNICODE
#define _UNICODE
#include "PssActivity.h"
#include "PssBridge.h"
#include "PssNativeDraw.h"
#include <stdint.h>
#include <string.h>
/* Cached activity artwork, drawn at DirectDraw presentation when available.
 * A click-through window remains as a windowed fallback. No native UI calls
 * or changes to the PSS start/stop/movement state are required. */
enum {WIDTH=230,HEIGHT=64};
static HWND owner,window;
static HDC canvas;
static HBITMAP bitmap,old_bitmap;
static HFONT font;
static uint32_t* pixels;
static BOOL enabled;
static int offset_x,offset_y;
static DWORD ui_thread;
static BOOL native_ready;
static SRWLOCK paint_guard=SRWLOCK_INIT;
static void render(void);
static LRESULT CALLBACK procedure(HWND hwnd,UINT msg,WPARAM wp,LPARAM lp){
    if(msg==WM_NCHITTEST)return HTTRANSPARENT;
    if(msg==WM_MOUSEACTIVATE)return MA_NOACTIVATE;
    return DefWindowProcW(hwnd,msg,wp,lp);
}
void PssActivityDetach(void){
    if(ui_thread&&GetCurrentThreadId()!=ui_thread)return;
    PssNativeDrawDetach();native_ready=FALSE;
    AcquireSRWLockExclusive(&paint_guard);
    if(window)DestroyWindow(window);
    if(canvas&&old_bitmap)SelectObject(canvas,old_bitmap);
    if(bitmap)DeleteObject(bitmap);
    if(font)DeleteObject(font);
    if(canvas)DeleteDC(canvas);
    window=0;owner=0;canvas=0;bitmap=old_bitmap=0;font=0;pixels=0;enabled=FALSE;
    ReleaseSRWLockExclusive(&paint_guard);
}
void PssActivityInitialize(HWND game,HMODULE module){
    if(!IsWindow(game))return;
    DWORD thread=GetWindowThreadProcessId(game,0);
    if(thread!=GetCurrentThreadId()){PssBridgeLog(L"PSS activity init rejected: caller is not the game UI thread");return;}
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
    if(!enabled){PssBridgeLog(L"PSS activity disabled by INI");return;}
    WNDCLASSW wc={0};wc.lpfnWndProc=procedure;wc.hInstance=module;wc.lpszClassName=L"Pss2019Activity";
    BOOL class_ready=RegisterClassW(&wc)||GetLastError()==ERROR_CLASS_ALREADY_EXISTS;
    if(!class_ready)PssBridgeLog(L"PSS activity overlay class unavailable; trying native frame rendering");
    if(class_ready)window=CreateWindowExW(WS_EX_LAYERED|WS_EX_TRANSPARENT|WS_EX_NOACTIVATE|WS_EX_TOOLWINDOW,
        wc.lpszClassName,L"PSS activity",WS_POPUP,0,0,WIDTH,HEIGHT,game,0,module,0);
    if(!window)PssBridgeLog(L"PSS activity overlay window unavailable; trying native frame rendering");
    BITMAPINFO info;memset(&info,0,sizeof(info));info.bmiHeader.biSize=sizeof(BITMAPINFOHEADER);
    info.bmiHeader.biWidth=WIDTH;info.bmiHeader.biHeight=-HEIGHT;info.bmiHeader.biPlanes=1;
    info.bmiHeader.biBitCount=32;info.bmiHeader.biCompression=BI_RGB;
    canvas=CreateCompatibleDC(0);
    if(canvas)bitmap=CreateDIBSection(canvas,&info,DIB_RGB_COLORS,(void**)&pixels,0,0);
    if(!bitmap){PssBridgeLog(L"PSS activity bitmap creation failed");PssActivityDetach();return;}
    old_bitmap=SelectObject(canvas,bitmap);
    font=CreateFontW(-14,0,0,0,FW_BOLD,FALSE,FALSE,FALSE,DEFAULT_CHARSET,
        OUT_DEFAULT_PRECIS,CLIP_DEFAULT_PRECIS,NONANTIALIASED_QUALITY,DEFAULT_PITCH,L"Malgun Gothic");
    // The artwork is static: allocate/draw GDI objects once, not per frame.
    render();
    PssBridgeLog(L"PSS activity initialized on the game UI thread");
    native_ready=PssNativeDrawInitialize();
}
static void render(void){
    memset(pixels,0,WIDTH*HEIGHT*sizeof(*pixels));
    HBRUSH background=CreateSolidBrush(RGB(164,61,9));
    RECT bar={20,41,WIDTH-20,62};FillRect(canvas,&bar,background);
    HBRUSH old_brush=SelectObject(canvas,background);
    HPEN pen=CreatePen(PS_SOLID,2,RGB(255,151,51)),old_pen=SelectObject(canvas,pen);
    Ellipse(canvas,WIDTH/2-13,7,WIDTH/2+13,33);
    SelectObject(canvas,old_pen);DeleteObject(pen);
    // Reference UI: a play triangle inside a circle, not a loading spinner.
    HBRUSH arrow=CreateSolidBrush(RGB(255,249,234));
    SelectObject(canvas,arrow);
    POINT points[3]={{WIDTH/2-4,12},{WIDTH/2+7,20},{WIDTH/2-4,28}};
    Polygon(canvas,points,3);SelectObject(canvas,old_brush);DeleteObject(arrow);DeleteObject(background);
    HFONT old_font=font?SelectObject(canvas,font):0;
    SetBkMode(canvas,TRANSPARENT);SetTextColor(canvas,RGB(255,242,219));
    DrawTextW(canvas,L"플레이 서포트 동작 중",-1,&bar,DT_CENTER|DT_VCENTER|DT_SINGLELINE|DT_NOPREFIX);
    if(old_font)SelectObject(canvas,old_font);
    GdiFlush();
    for(unsigned i=0;i<WIDTH*HEIGHT;++i){
        uint32_t c=pixels[i]&0xFFFFFFU;
        if(c){uint32_t b=(c&255U)*235U/255U,g=((c>>8)&255U)*235U/255U,r=((c>>16)&255U)*235U/255U;
            pixels[i]=0xEB000000U|(r<<16)|(g<<8)|b;}
    }
}
BOOL PssActivityPaintSurface(HDC dc){
    if(!dc||!TryAcquireSRWLockExclusive(&paint_guard))return FALSE;
    BOOL ok=FALSE;RECT clip;
    if(enabled&&canvas&&pixels&&GetClipBox(dc,&clip)!=ERROR&&clip.right-clip.left>=320&&clip.bottom-clip.top>=200){
        int x=clip.left+(clip.right-clip.left-WIDTH)/2+offset_x;
        int y=clip.top+(clip.bottom-clip.top)/2+offset_y;
        BLENDFUNCTION blend={AC_SRC_OVER,0,255,AC_SRC_ALPHA};
        ok=AlphaBlend(dc,x,y,WIDTH,HEIGHT,canvas,0,0,WIDTH,HEIGHT,blend);
        if(!ok)ok=TransparentBlt(dc,x,y,WIDTH,HEIGHT,canvas,0,0,WIDTH,HEIGHT,RGB(0,0,0));
    }
    ReleaseSRWLockExclusive(&paint_guard);return ok;
}
void PssActivityUpdate(BOOL running){
    if(GetCurrentThreadId()!=ui_thread||!canvas||!enabled)return;
    HWND foreground=GetForegroundWindow();
    BOOL show=running&&IsWindow(owner)&&IsWindowVisible(owner)&&!IsIconic(owner)&&
        (foreground==owner||IsChild(owner,foreground));
    PssNativeDrawSetActive(show);
    if(!show||(native_ready&&PssNativeDrawHasPainted())){if(window)ShowWindow(window,SW_HIDE);return;}
    if(!window)return;
    RECT area;
    if(!GetClientRect(owner,&area)){ShowWindow(window,SW_HIDE);return;}
    POINT center={(area.right-area.left)/2+offset_x,(area.bottom-area.top)/2+offset_y};
    if(!ClientToScreen(owner,&center)){ShowWindow(window,SW_HIDE);return;}
    POINT destination={center.x-WIDTH/2,center.y},source={0,0};SIZE size={WIDTH,HEIGHT};
    AcquireSRWLockExclusive(&paint_guard);
    BLENDFUNCTION blend={AC_SRC_OVER,0,255,AC_SRC_ALPHA};
    if(UpdateLayeredWindow(window,0,&destination,&size,canvas,&source,0,&blend,ULW_ALPHA))
        ShowWindow(window,SW_SHOWNOACTIVATE);
    ReleaseSRWLockExclusive(&paint_guard);
}
