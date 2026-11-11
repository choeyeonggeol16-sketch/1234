#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <objidl.h>
#include <gdiplus.h>
#include <algorithm>
#include <cmath>
#include <cwchar>
#include "ats_hud.h"
#include "step_network.h"
namespace {
HWND owner=nullptr,overlay=nullptr; HINSTANCE instance=nullptr;
ULONG_PTR gdip=0; ats::State state; uint64_t lastTick=0;
int lastSecond=-1,lastWidth=0,lastHeight=0; bool lastStale=false;
int bottom=124,offsetX=0,scalePercent=100; bool enabled=true,showSeconds=true;
Gdiplus::Image* icon=nullptr;
constexpr wchar_t kClass[]=L"ClientInput.ATS.Overlay.1";
LRESULT CALLBACK windowProc(HWND h,UINT m,WPARAM w,LPARAM l) {
    if(m==WM_NCHITTEST) return HTTRANSPARENT;
    if(m==WM_MOUSEACTIVATE) return MA_NOACTIVATE;
    if(m==WM_ERASEBKGND) return 1;
    return DefWindowProcW(h,m,w,l);
}
void render(int width,int height,float scale,uint32_t remaining,bool stale,uint64_t now) noexcept {
    using namespace Gdiplus;
    HDC screen=GetDC(nullptr),dc=CreateCompatibleDC(screen);
    BITMAPINFO info{};info.bmiHeader.biSize=sizeof(BITMAPINFOHEADER);
    info.bmiHeader.biWidth=width;info.bmiHeader.biHeight=-height;
    info.bmiHeader.biPlanes=1;info.bmiHeader.biBitCount=32;info.bmiHeader.biCompression=BI_RGB;
    void* bits=nullptr;HBITMAP dib=CreateDIBSection(screen,&info,DIB_RGB_COLORS,&bits,nullptr,0);
    if(!dc || !dib || !bits) {if(dib)DeleteObject(dib);if(dc)DeleteDC(dc);ReleaseDC(nullptr,screen);return;}
    HGDIOBJ old=SelectObject(dc,dib);
    {
        Bitmap bitmap(width,height,width*4,PixelFormat32bppPARGB,static_cast<BYTE*>(bits));
        Graphics g(&bitmap);g.Clear(Color(0,0,0,0));g.SetSmoothingMode(SmoothingModeAntiAlias);
        g.SetInterpolationMode(InterpolationModeHighQualityBicubic);g.SetPixelOffsetMode(PixelOffsetModeHighQuality);
        g.SetTextRenderingHint(TextRenderingHintAntiAliasGridFit);g.ScaleTransform(scale,scale);
        constexpr float center=115.f;
        if(icon) g.DrawImage(icon,center-18.f,0.f,36.f,36.f);
        else {
            // Replace with ats.png (straight-alpha PNG) beside dinput.dll when available.
            for(int i=5;i>0;--i) {Pen glow(Color(12,255,45,25),static_cast<REAL>(i*2));g.DrawEllipse(&glow,center-14.f,3.f,28.f,28.f);}
            SolidBrush fill(Color(170,95,15,12));Pen edge(Color(240,255,154,128),1.2f);
            g.FillEllipse(&fill,center-14.f,3.f,28.f,28.f);g.DrawEllipse(&edge,center-14.f,3.f,28.f,28.f);
            Font font(L"Georgia",10.f,FontStyleBold,UnitPixel);SolidBrush letters(Color(255,255,210,188));
            StringFormat fmt;fmt.SetAlignment(StringAlignmentCenter);fmt.SetLineAlignment(StringAlignmentCenter);
            g.DrawString(L"ATS",-1,&font,RectF(center-14.f,3.f,28.f,28.f),&fmt,&letters);
        }
        // Keep ATS lettering stationary; only the surrounding light sweeps around it.
        const float angle=static_cast<float>(now%2000)*0.18f-90.f;
        Pen arc(Color(stale?80:220,255,122,95),1.5f);arc.SetStartCap(LineCapRound);arc.SetEndCap(LineCapRound);
        g.DrawArc(&arc,center-17.f,1.f,34.f,34.f,angle,72.f);
        wchar_t text[96]{};
        if(stale) std::swprintf(text,96,L"\uc2dc\uac04 \ub3d9\uae30\ud654 \uc911");
        else if(showSeconds) std::swprintf(text,96,L"\ub0a8\uc740\uc2dc\uac04: %u\uc2dc\uac04 %u\ubd84 %u\ucd08",remaining/3600,(remaining/60)%60,remaining%60);
        else std::swprintf(text,96,L"\ub0a8\uc740\uc2dc\uac04: %u\uc2dc\uac04 %u\ubd84",remaining/3600,(remaining/60)%60);
        FontFamily family(L"Malgun Gothic");StringFormat fmt;
        fmt.SetAlignment(StringAlignmentCenter);fmt.SetLineAlignment(StringAlignmentCenter);
        GraphicsPath path;path.AddString(text,-1,&family,FontStyleRegular,12.f,RectF(0.f,26.25f,230.f,20.f),&fmt);
        Pen shadow(Color(210,25,0,0),2.6f);shadow.SetLineJoin(LineJoinRound);
        SolidBrush color(Color(255,255,151,142));g.DrawPath(&shadow,&path);g.FillPath(&color,&path);
        g.Flush(FlushIntentionSync);
    }
    POINT source{};SIZE size{width,height};BLENDFUNCTION blend{AC_SRC_OVER,0,255,AC_SRC_ALPHA};
    UpdateLayeredWindow(overlay,screen,nullptr,&size,dc,&source,0,&blend,ULW_ALPHA);
    SelectObject(dc,old);DeleteObject(dib);DeleteDC(dc);ReleaseDC(nullptr,screen);
}
}
void AtsHud_Start(HWND game) noexcept {
    AtsHud_Stop();owner=game;
    if(!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            reinterpret_cast<LPCWSTR>(&AtsHud_Start),&instance)) return;
    wchar_t path[MAX_PATH]{};GetModuleFileNameW(instance,path,MAX_PATH);
    wchar_t* slash=std::wcsrchr(path,L'\\');if(!slash)return;*(slash+1)=0;
    wchar_t ini[MAX_PATH]{};std::swprintf(ini,MAX_PATH,L"%lsats_hud.ini",path);
    enabled=GetPrivateProfileIntW(L"ATS",L"Enabled",1,ini)!=0;
    bottom=std::clamp(static_cast<int>(GetPrivateProfileIntW(L"ATS",L"Bottom",124,ini)),0,480);
    offsetX=std::clamp(static_cast<int>(GetPrivateProfileIntW(L"ATS",L"OffsetX",0,ini)),-640,640);
    scalePercent=std::clamp(static_cast<int>(GetPrivateProfileIntW(L"ATS",L"ScalePercent",100,ini)),50,200);
    showSeconds=GetPrivateProfileIntW(L"ATS",L"ShowSeconds",1,ini)!=0;
    if(!enabled)return;
    Gdiplus::GdiplusStartupInput input;if(Gdiplus::GdiplusStartup(&gdip,&input,nullptr)!=Gdiplus::Ok){gdip=0;return;}
    wchar_t png[MAX_PATH]{};std::swprintf(png,MAX_PATH,L"%lsats.png",path);
    if(GetFileAttributesW(png)!=INVALID_FILE_ATTRIBUTES) {
        icon=Gdiplus::Image::FromFile(png);
        if(icon && (icon->GetLastStatus()!=Gdiplus::Ok || icon->GetWidth()==0 || icon->GetHeight()==0)) {delete icon;icon=nullptr;}
    }
    WNDCLASSW cls{};cls.lpfnWndProc=windowProc;cls.hInstance=instance;cls.lpszClassName=kClass;
    RegisterClassW(&cls);
    overlay=CreateWindowExW(WS_EX_LAYERED|WS_EX_TRANSPARENT|WS_EX_NOACTIVATE|WS_EX_TOOLWINDOW,
        kClass,L"ATS",WS_POPUP,0,0,1,1,game,nullptr,instance,nullptr);
    if(!overlay) NativeStep_LogInput("ATS overlay creation failed",static_cast<int>(GetLastError()));
    state.hide();lastTick=0;lastSecond=-1;lastWidth=lastHeight=0;
}
void AtsHud_Status(ats::Status status) noexcept {state.update(status,GetTickCount64());lastSecond=-1;}
void AtsHud_Tick() noexcept {
    if(!overlay || !IsWindow(owner))return;
    const uint64_t now=GetTickCount64();if(now-lastTick<33)return;lastTick=now;
    HWND fg=GetForegroundWindow();
    if(!state.visible() || IsIconic(owner) || !IsWindowVisible(owner) || !fg || GetAncestor(fg,GA_ROOT)!=GetAncestor(owner,GA_ROOT)) {
        ShowWindow(overlay,SW_HIDE);return;
    }
    RECT client{};if(!GetClientRect(owner,&client))return;
    int cw=client.right,ch=client.bottom;if(cw<=0||ch<=0)return;
    const float base=std::min(cw/640.f,ch/480.f);
    const float scale=std::clamp(base*scalePercent/100.f,0.25f,8.f);
    const int width=static_cast<int>(std::ceil(230.f*scale)),height=static_cast<int>(std::ceil(48.f*scale));
    POINT origin{std::max(0,std::min(cw-width,(cw-width)/2+static_cast<int>(offsetX*base))),
        std::max(0,std::min(ch-height,ch-static_cast<int>(bottom*base)-height))};
    if(!ClientToScreen(owner,&origin))return;
    const uint32_t sec=state.remaining(now);const bool stale=state.stale(now);
    SetWindowPos(overlay,HWND_TOP,origin.x,origin.y,width,height,SWP_NOACTIVATE);
    if(state.visible() || lastSecond!=static_cast<int>(sec)||lastWidth!=width||lastHeight!=height||lastStale!=stale) {
        render(width,height,scale,sec,stale,now);lastSecond=static_cast<int>(sec);lastWidth=width;lastHeight=height;lastStale=stale;
    }
    ShowWindow(overlay,SW_SHOWNOACTIVATE);
}
void AtsHud_Hide() noexcept {state.hide();if(overlay)ShowWindow(overlay,SW_HIDE);}
void AtsHud_Stop() noexcept {
    AtsHud_Hide();if(overlay)DestroyWindow(overlay);overlay=nullptr;
    delete icon;icon=nullptr;if(gdip)Gdiplus::GdiplusShutdown(gdip);gdip=0;owner=nullptr;
}
