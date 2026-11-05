#define COBJMACROS
#define DIRECTDRAW_VERSION 0x0700
#include <windows.h>
#include <ddraw.h>
#include <stdint.h>
#include "PssNativeDraw.h"
#include "PssActivity.h"
#include "PssBridge.h"
/* Public DirectDraw COM presentation methods, not client code addresses.
 * Capture all compatible surface interface tables from an offscreen object.
 * Shared tables also cover surfaces created before the helper was attached. */
typedef HRESULT (WINAPI *BltFn)(void*,RECT*,void*,RECT*,DWORD,DDBLTFX*);
typedef HRESULT (WINAPI *FlipFn)(void*,void*,DWORD);
typedef HRESULT (WINAPI *CapsFn)(void*,DDSCAPS2*);
typedef HRESULT (WINAPI *AttachedFn)(void*,DDSCAPS2*,void**);
typedef HRESULT (WINAPI *DcFn)(void*,HDC*);
typedef HRESULT (WINAPI *ReleaseDcFn)(void*,HDC);
typedef ULONG (WINAPI *ReleaseFn)(void*);
typedef HRESULT (WINAPI *DescFn)(void*,void*);
typedef struct {void** table;BltFn blt;FlipFn flip;} Entry;
static Entry entries[8];
static volatile LONG count,active,drawing,painted,last_paint;
static HRESULT WINAPI frame_blt(void*,RECT*,void*,RECT*,DWORD,DDBLTFX*);
static HRESULT WINAPI frame_flip(void*,void*,DWORD);
static Entry* entry(void* surface){
    void** table=*(void***)surface;LONG size=InterlockedCompareExchange(&count,0,0);
    for(LONG n=0;n<size;++n)if(entries[n].table==table)return &entries[n];
    return 0;
}
static BOOL replace(void** slot,void* expected,void* value){
    DWORD old;if(!VirtualProtect(slot,sizeof(void*),PAGE_READWRITE,&old))return FALSE;
    LONG prior=InterlockedCompareExchange((LONG*)slot,(LONG)(uintptr_t)value,(LONG)(uintptr_t)expected);
    DWORD ignore;VirtualProtect(slot,sizeof(void*),old,&ignore);
    return (void*)(uintptr_t)(DWORD)prior==expected;
}
static BOOL install(void* surface){
    if(!surface)return FALSE;
    Entry* found=entry(surface);
    if(found){
        BOOL a=replace(&found->table[5],(void*)found->blt,(void*)frame_blt)||found->table[5]==(void*)frame_blt;
        BOOL b=replace(&found->table[11],(void*)found->flip,(void*)frame_flip)||found->table[11]==(void*)frame_flip;
        return a&&b;
    }
    LONG n=InterlockedCompareExchange(&count,0,0);if(n>=8)return FALSE;
    void** table=*(void***)surface;
    entries[n]=(Entry){table,(BltFn)table[5],(FlipFn)table[11]};
    if(!entries[n].blt||!entries[n].flip)return FALSE;
    InterlockedExchange(&count,n+1);
    BOOL a=replace(&table[5],(void*)entries[n].blt,(void*)frame_blt);
    BOOL b=replace(&table[11],(void*)entries[n].flip,(void*)frame_flip);
    if(!a||!b){
        if(a)replace(&table[5],(void*)frame_blt,(void*)entries[n].blt);
        if(b)replace(&table[11],(void*)frame_flip,(void*)entries[n].flip);
        return FALSE;
    }
    return TRUE;
}
static BOOL primary(void* surface){
    DDSCAPS2 caps={0};void** table=*(void***)surface;
    return SUCCEEDED(((CapsFn)table[14])(surface,&caps))&&(caps.dwCaps&DDSCAPS_PRIMARYSURFACE);
}
static BOOL bounds(void* surface,RECT* out){
    void** table=*(void***)surface;if(!table[22])return FALSE;
    DDSURFACEDESC2 desc={0};desc.dwSize=sizeof(desc);
    HRESULT hr=((DescFn)table[22])(surface,&desc);
    DWORD width=desc.dwWidth,height=desc.dwHeight;
    if(FAILED(hr)){
        DDSURFACEDESC old={0};old.dwSize=sizeof(old);
        hr=((DescFn)table[22])(surface,&old);width=old.dwWidth;height=old.dwHeight;
    }
    if(FAILED(hr)||!width||!height||width>65536||height>65536)return FALSE;
    *out=(RECT){0,0,(LONG)width,(LONG)height};return TRUE;
}
static void paint(void* surface,const RECT* source,const RECT* destination){
    if(!surface)return;
    void** table=*(void***)surface;HDC dc=0;
    if(FAILED(((DcFn)table[17])(surface,&dc))||!dc)return;
    BOOL ok=PssActivityPaintSurface(dc,source,destination);
    ((ReleaseDcFn)table[26])(surface,dc);
    if(ok){
        InterlockedExchange(&last_paint,(LONG)GetTickCount());
        if(InterlockedCompareExchange(&painted,1,0)==0)
            PssBridgeLog(L"PSS native DirectDraw activity painted into the game frame");
    }
}
static HRESULT WINAPI frame_blt(void* self,RECT* destination,void* source,RECT* source_rect,DWORD flags,DDBLTFX* effects){
    Entry* e=entry(self);if(!e)return DDERR_GENERIC;
    // Full presentation copies only. Never paint a sprite/atlas or a scroll copy.
    BOOL whole_destination=!destination||(destination->right-destination->left>=320&&destination->bottom-destination->top>=200);
    BOOL whole_source=!source_rect||(source_rect->right-source_rect->left>=320&&source_rect->bottom-source_rect->top>=200);
    if(InterlockedCompareExchange(&active,0,0)&&source&&source!=self&&whole_destination&&whole_source&&
       InterlockedCompareExchange(&drawing,1,0)==0){
        if(primary(self)){
            RECT display;
            if(destination)paint(source,source_rect,destination);
            else if(bounds(self,&display))paint(source,source_rect,&display);
        }
        InterlockedExchange(&drawing,0);
    }
    return e->blt(self,destination,source,source_rect,flags,effects);
}
static HRESULT WINAPI frame_flip(void* self,void* override,DWORD flags){
    Entry* e=entry(self);if(!e)return DDERR_GENERIC;
    if(InterlockedCompareExchange(&active,0,0)&&InterlockedCompareExchange(&drawing,1,0)==0){
        if(primary(self)){
            void* back=override;BOOL owned=FALSE;
            if(!back){DDSCAPS2 caps={0};caps.dwCaps=DDSCAPS_BACKBUFFER;
                void** table=*(void***)self;
                if(SUCCEEDED(((AttachedFn)table[12])(self,&caps,&back)))owned=back!=0;
            }
            if(back)paint(back,0,0);
            if(owned){void** table=*(void***)back;((ReleaseFn)table[2])(back);}
        }
        InterlockedExchange(&drawing,0);
    }
    return e->flip(self,override,flags);
}
BOOL PssNativeDrawInitialize(void){
    HMODULE module=GetModuleHandleW(L"ddraw.dll");if(!module){PssBridgeLog(L"PSS native draw unavailable: ddraw.dll is not loaded");return FALSE;}
    typedef HRESULT (WINAPI *CreateFn)(GUID*,void**,REFIID,IUnknown*);
    CreateFn create=(CreateFn)(uintptr_t)GetProcAddress(module,"DirectDrawCreateEx");
    IDirectDraw7* draw=0;IDirectDrawSurface7* surface=0;BOOL ok=FALSE;
    if(!create||FAILED(create(0,(void**)&draw,&IID_IDirectDraw7,0))||!draw){PssBridgeLog(L"PSS native draw unavailable: DirectDraw factory failed");return FALSE;}
    // A normal, offscreen system-memory surface; do not create a primary,
    // change display mode, or change the game's cooperative/exclusive mode.
    HRESULT hr=IDirectDraw7_SetCooperativeLevel(draw,0,DDSCL_NORMAL);
    if(SUCCEEDED(hr)){
        DDSURFACEDESC2 desc={0};desc.dwSize=sizeof(desc);desc.dwFlags=DDSD_CAPS|DDSD_WIDTH|DDSD_HEIGHT;
        desc.dwWidth=640;desc.dwHeight=480;desc.ddsCaps.dwCaps=DDSCAPS_OFFSCREENPLAIN|DDSCAPS_SYSTEMMEMORY;
        hr=IDirectDraw7_CreateSurface(draw,&desc,&surface,0);
        if(SUCCEEDED(hr)&&surface){
            const GUID* ids[]={&IID_IDirectDrawSurface7,&IID_IDirectDrawSurface4,&IID_IDirectDrawSurface3,&IID_IDirectDrawSurface2,&IID_IDirectDrawSurface};
            for(unsigned n=0;n<sizeof(ids)/sizeof(ids[0]);++n){void* iface=0;
                if(SUCCEEDED(IDirectDrawSurface7_QueryInterface(surface,ids[n],&iface))&&iface){
                    if(install(iface))ok=TRUE;
                    void** table=*(void***)iface;((ReleaseFn)table[2])(iface);
                }
            }
            IDirectDrawSurface7_Release(surface);
        }
    }
    IDirectDraw7_Release(draw);
    PssBridgeLog(ok?L"PSS native DirectDraw frame hook installed; awaiting presentation":L"PSS native DirectDraw hook unavailable; windowed overlay only");
    return ok;
}
BOOL PssNativeDrawHasPainted(void){
    // If presentation changes after a display-mode switch, allow the fallback
    // again instead of hiding it forever after one successful frame.
    return InterlockedCompareExchange(&active,0,0)&&InterlockedCompareExchange(&painted,0,0)&&
        (DWORD)(GetTickCount()-(DWORD)InterlockedCompareExchange(&last_paint,0,0))<500U;
}
void PssNativeDrawSetActive(BOOL value){InterlockedExchange(&active,value!=0);}
void PssNativeDrawDetach(void){
    InterlockedExchange(&active,0);
    InterlockedExchange(&painted,0);
    LONG size=InterlockedCompareExchange(&count,0,0);
    for(LONG n=0;n<size;++n){
        replace(&entries[n].table[5],(void*)frame_blt,(void*)entries[n].blt);
        replace(&entries[n].table[11],(void*)frame_flip,(void*)entries[n].flip);
    }
    // Preserve original-call records for any callback already in flight.
}
#ifdef PSS_NATIVE_DRAW_TEST
BOOL PssNativeDrawTestInstall(void* surface){return install(surface);}
#endif
