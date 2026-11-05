#define UNICODE
#define _UNICODE
#include "PssActivity.h"
#include "PssBridge.h"
#include "PssNativeDraw.h"
#include "PssActivityLayout.h"
#include "PssActivityArt.h"
#include <stdint.h>
#include <string.h>
/* Cached activity artwork, drawn at DirectDraw presentation when available.
 * A click-through window remains as a windowed fallback. No native UI calls
 * or changes to the PSS start/stop/movement state are required. */
enum {WIDTH=PSS_ART_WIDTH,HEIGHT=PSS_ART_HEIGHT};
static HWND owner,window;
static HDC canvas;
static HBITMAP bitmap,old_bitmap;
static HFONT font;
static uint32_t* pixels;
static BOOL enabled;
static int offset_x,offset_y;
static DWORD ui_thread;
static BOOL native_ready;
static RECT game_area;
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
    SetRectEmpty(&game_area);
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
    info.bmiHeader.biWidth=WIDTH;info.bmiHeader.biHeight=-HEIGHT*PSS_ART_FRAMES;info.bmiHeader.biPlanes=1;
    info.bmiHeader.biBitCount=32;info.bmiHeader.biCompression=BI_RGB;
    canvas=CreateCompatibleDC(0);
    if(canvas)bitmap=CreateDIBSection(canvas,&info,DIB_RGB_COLORS,(void**)&pixels,0,0);
    if(!bitmap){PssBridgeLog(L"PSS activity bitmap creation failed");PssActivityDetach();return;}
    old_bitmap=SelectObject(canvas,bitmap);
    font=CreateFontW(-10,0,0,0,FW_BOLD,FALSE,FALSE,FALSE,DEFAULT_CHARSET,
        OUT_DEFAULT_PRECIS,CLIP_DEFAULT_PRECIS,ANTIALIASED_QUALITY,DEFAULT_PITCH,L"Malgun Gothic");
    // Pre-render the animation strip once; no GDI objects per frame.
    render();
    PssBridgeLog(L"PSS activity initialized on the game UI thread");
    native_ready=PssNativeDrawInitialize();
}
static void render(void){
    memset(pixels,0,WIDTH*HEIGHT*PSS_ART_FRAMES*sizeof(*pixels));
    HFONT old_font=font?SelectObject(canvas,font):0;
    RECT bar={2,40,WIDTH-2,56};
    SetBkMode(canvas,TRANSPARENT);SetTextColor(canvas,RGB(255,255,255));
    DrawTextW(canvas,L"플레이 서포트 동작 중",-1,&bar,DT_CENTER|DT_VCENTER|DT_SINGLELINE|DT_NOPREFIX);
    if(old_font)SelectObject(canvas,old_font);
    GdiFlush();
    uint8_t mask[WIDTH*HEIGHT];
    for(unsigned i=0;i<WIDTH*HEIGHT;++i){
        uint32_t c=pixels[i];unsigned r=(c>>16)&255,g=(c>>8)&255,b=c&255;
        mask[i]=(uint8_t)((r+g+b)/3);
    }
    for(unsigned n=0;n<PSS_ART_FRAMES;++n)PssActivityArtFrame(pixels+n*WIDTH*HEIGHT,mask,n);
}
BOOL PssActivityPaintSurface(HDC dc,const RECT* source,const RECT* destination){
    if(!dc||!TryAcquireSRWLockExclusive(&paint_guard))return FALSE;
    BOOL ok=FALSE;RECT clip;
    if(enabled&&canvas&&pixels&&GetClipBox(dc,&clip)!=ERROR&&clip.right-clip.left>=320&&clip.bottom-clip.top>=200){
        RECT target,mapped,view=source?*source:clip,display;
        if(destination)display=*destination;
        else display=game_area; // Flip: the complete backbuffer is the client image.
        if(!PssActivityTarget(&game_area,WIDTH,HEIGHT,offset_x,offset_y,&target)||
           !PssActivityMap(&target,&view,&display,&mapped)||
           mapped.left<clip.left||mapped.top<clip.top||mapped.right>clip.right||mapped.bottom>clip.bottom){
            ReleaseSRWLockExclusive(&paint_guard);return FALSE;
        }
        int x=mapped.left,y=mapped.top,w=mapped.right-mapped.left,h=mapped.bottom-mapped.top;
        BLENDFUNCTION blend={AC_SRC_OVER,0,255,AC_SRC_ALPHA};
        unsigned frame=(GetTickCount()/PSS_ART_FRAME_MS)%PSS_ART_FRAMES;
        ok=AlphaBlend(dc,x,y,w,h,canvas,0,(int)frame*HEIGHT,WIDTH,HEIGHT,blend);
        if(!ok)ok=TransparentBlt(dc,x,y,w,h,canvas,0,(int)frame*HEIGHT,WIDTH,HEIGHT,RGB(0,0,0));
    }
    ReleaseSRWLockExclusive(&paint_guard);return ok;
}
void PssActivityUpdate(BOOL running){
    if(GetCurrentThreadId()!=ui_thread||!canvas||!enabled)return;
    HWND foreground=GetForegroundWindow();
    BOOL show=running&&IsWindow(owner)&&IsWindowVisible(owner)&&!IsIconic(owner)&&
        (foreground==owner||IsChild(owner,foreground));
    RECT area={0},target;
    POINT first={0,0},last;
    BOOL located=GetClientRect(owner,&area);
    last=(POINT){area.right,area.bottom};
    located=located&&ClientToScreen(owner,&first)&&ClientToScreen(owner,&last);
    AcquireSRWLockExclusive(&paint_guard);
    if(located)game_area=(RECT){first.x,first.y,last.x,last.y};
    else SetRectEmpty(&game_area);
    located=located&&PssActivityTarget(&game_area,WIDTH,HEIGHT,offset_x,offset_y,&target);
    ReleaseSRWLockExclusive(&paint_guard);
    show=show&&located;
    PssNativeDrawSetActive(show);
    if(!show||(native_ready&&PssNativeDrawHasPainted())){if(window)ShowWindow(window,SW_HIDE);return;}
    if(!window)return;
    unsigned frame=(GetTickCount()/PSS_ART_FRAME_MS)%PSS_ART_FRAMES;
    POINT destination={target.left,target.top},source={0,(LONG)frame*HEIGHT};SIZE size={WIDTH,HEIGHT};
    AcquireSRWLockExclusive(&paint_guard);
    BLENDFUNCTION blend={AC_SRC_OVER,0,255,AC_SRC_ALPHA};
    if(UpdateLayeredWindow(window,0,&destination,&size,canvas,&source,0,&blend,ULW_ALPHA))
        ShowWindow(window,SW_SHOWNOACTIVATE);
    ReleaseSRWLockExclusive(&paint_guard);
}
