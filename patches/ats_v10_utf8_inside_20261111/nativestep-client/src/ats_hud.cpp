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
int bottom=124,offsetX=0,scalePercent=100; bool enabled=true,showSeconds=false;
int textScalePercent=90;
Gdiplus::Image* icon=nullptr;
wchar_t fontFace[64]=L"Batang";
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
        constexpr float center=115.f, cy=16.5f, radius=11.5f;
        if(icon) g.DrawImage(icon,center-14.75f,cy-14.75f,29.5f,29.5f);
        else {
            // The background has no baked text/rim: both stay crisp at every window scale.
            for(int i=4;i>0;--i) {
                Pen halo(Color(8,175,48,32),static_cast<REAL>(i*2));
                g.DrawEllipse(&halo,center-radius,cy-radius,radius*2,radius*2);
            }
            LinearGradientBrush disc(PointF(center,cy-radius),PointF(center,cy+radius),
                Color(190,112,32,24),Color(175,66,15,13));
            g.FillEllipse(&disc,center-radius,cy-radius,radius*2,radius*2);
        }
        // One aligned ring, with two tangent arrow tips. ATS text never rotates.
        const float stroke=std::max(0.85f,0.85f/scale);
        Pen baseRing(Color(95,243,166,143),stroke);
        g.DrawEllipse(&baseRing,center-radius,cy-radius,radius*2,radius*2);
        const float angle=stale ? -90.f : static_cast<float>(now%2000)*0.18f-90.f;
        for(int half=0;half<2;++half) {
            const float begin=angle+half*180.f+10.f, sweep=155.f;
            Pen glow(Color(stale?8:26,239,91,59),stroke+2.4f);
            Pen arc(Color(stale?90:245,255,216,195),stroke);
            arc.SetStartCap(LineCapRound);arc.SetEndCap(LineCapRound);
            g.DrawArc(&glow,center-radius,cy-radius,radius*2,radius*2,begin,sweep);
            g.DrawArc(&arc,center-radius,cy-radius,radius*2,radius*2,begin,sweep);
            const float a=(begin+sweep)*3.14159265358979323846f/180.f;
            const float nx=std::cos(a),ny=std::sin(a),tx=-ny,ty=nx;
            const PointF tip(center+radius*nx,cy+radius*ny);
            PointF arrow[3]={tip,
                PointF(tip.X-tx*3.1f+nx*1.3f,tip.Y-ty*3.1f+ny*1.3f),
                PointF(tip.X-tx*3.1f-nx*1.3f,tip.Y-ty*3.1f-ny*1.3f)};
            SolidBrush arrowColor(Color(stale?100:255,255,207,184));
            g.FillPolygon(&arrowColor,arrow,3);
        }
        FontFamily badgeFont(L"Georgia");
        const FontFamily* badgeFamily=badgeFont.GetLastStatus()==Ok ? &badgeFont : FontFamily::GenericSerif();
        StringFormat badgeFormat(StringFormat::GenericTypographic());
        badgeFormat.SetAlignment(StringAlignmentCenter);badgeFormat.SetLineAlignment(StringAlignmentCenter);
        GraphicsPath lettering;
        lettering.AddString(L"ATS",-1,badgeFamily,FontStyleBold,9.2f,RectF(center-13.f,cy-6.5f,26.f,13.f),&badgeFormat);
        Pen labelGlow(Color(115,238,83,26),2.8f);labelGlow.SetLineJoin(LineJoinRound);
        g.DrawPath(&labelGlow,&lettering);
        Pen letterEdge(Color(200,104,35,10),0.8f);SolidBrush letterFill(Color(255,255,232,210));
        g.DrawPath(&letterEdge,&lettering);g.FillPath(&letterFill,&lettering);
        wchar_t text[96]{};
        if(stale) std::swprintf(text,96,L"\uc2dc\uac04 \ub3d9\uae30\ud654 \uc911");
        else if(showSeconds) std::swprintf(text,96,L"\ub0a8\uc740\uc2dc\uac04: %u\uc2dc\uac04 %u\ubd84 %u\ucd08",remaining/3600,(remaining/60)%60,remaining%60);
        else std::swprintf(text,96,L"\ub0a8\uc740\uc2dc\uac04: %u\uc2dc\uac04 %u\ubd84",remaining/3600,(remaining/60)%60);
        FontFamily preferred(fontFace),fallback(L"Malgun Gothic");
        const FontFamily* family=preferred.GetLastStatus()==Ok ? &preferred : &fallback;
        if(family==&fallback && fallback.GetLastStatus()!=Ok) family=FontFamily::GenericSerif();
        StringFormat fmt(StringFormat::GenericTypographic());
        fmt.SetAlignment(StringAlignmentCenter);fmt.SetLineAlignment(StringAlignmentCenter);
        fmt.SetFormatFlags(fmt.GetFormatFlags()|StringFormatFlagsNoWrap);
        GraphicsPath path;
        path.AddString(text,-1,family,FontStyleBold,11.5f*textScalePercent/100.f,RectF(0.f,27.f,230.f,17.f),&fmt);
        Pen textGlow(Color(40,210,74,53),2.8f);textGlow.SetLineJoin(LineJoinRound);
        Pen shadow(Color(205,52,12,9),0.9f);shadow.SetLineJoin(LineJoinRound);
        SolidBrush color(Color(255,243,195,178));
        g.DrawPath(&textGlow,&path);g.DrawPath(&shadow,&path);g.FillPath(&color,&path);
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
    showSeconds=GetPrivateProfileIntW(L"ATS",L"ShowSeconds",0,ini)!=0;
    GetPrivateProfileStringW(L"ATS",L"FontFace",L"Batang",fontFace,64,ini);
    textScalePercent=std::clamp(static_cast<int>(GetPrivateProfileIntW(L"ATS",L"TextScalePercent",90,ini)),70,120);
    if(!enabled)return;
    Gdiplus::GdiplusStartupInput input;if(Gdiplus::GdiplusStartup(&gdip,&input,nullptr)!=Gdiplus::Ok){gdip=0;return;}
    wchar_t png[MAX_PATH]{};std::swprintf(png,MAX_PATH,L"%lsats_bg.png",path);
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
