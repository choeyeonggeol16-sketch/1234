/* Exercise actual bootstrap attachment and message-thread initialization.
 * The visual component is a boundary double; this is not a rendering test. */
#define UNICODE
#define _UNICODE
#include <windows.h>
#include <assert.h>
#include <stdio.h>
#include <string.h>
static volatile BYTE image_reservation[0x1C00000];
static DWORD owner_thread,initializer_thread;
static int initializations;
static int hud_initializations,draw_initializations;
static BOOL hud_world,native_active;
static BOOL WINAPI fixture_read(HANDLE process,LPCVOID address,LPVOID out,SIZE_T size,SIZE_T* got){
    (void)process;memcpy(out,address,size);if(got)*got=size;return TRUE;
}
#define ReadProcessMemory fixture_read
#include "../Pss2019.c"
#undef ReadProcessMemory
void PssActivityInitialize(HWND hwnd,HMODULE dll){
    (void)hwnd;(void)dll;initializer_thread=GetCurrentThreadId();++initializations;
}
void PssActivityUpdate(BOOL active){(void)active;}
void PssActivityDetach(void){}
BOOL PssActivityActive(void){return FALSE;}
void PssVitalsHudInitialize(HWND hwnd,HMODULE dll){(void)hwnd;(void)dll;assert(GetCurrentThreadId()==owner_thread);++hud_initializations;}
void PssVitalsHudDetach(void){hud_world=FALSE;}
void PssVitalsHudUpdate(BOOL world){hud_world=world;}
BOOL PssVitalsHudActive(void){return hud_world;}
BOOL PssNativeDrawInitialize(void){assert(GetCurrentThreadId()==owner_thread);++draw_initializations;return TRUE;}
void PssNativeDrawDetach(void){native_active=FALSE;}
void PssNativeDrawSetActive(BOOL active){native_active=active;}
void PssRoamingInitialize(HWND hwnd,HMODULE dll){(void)hwnd;(void)dll;}
void PssRoamingObserve(void){}
void PssRoamingReset(BOOL cancel){(void)cancel;}
void PssRoamingDetach(void){}
void PssRoamingBlockedShot(int target){(void)target;}
BOOL PssBridgeInitialize(HWND hwnd,UINT msg,HMODULE dll){(void)hwnd;(void)msg;(void)dll;return TRUE;}
void PssBridgeTick(void){}
void PssBridgeDetach(void){}
static DWORD WINAPI attach_worker(void* hwnd){assert(GetCurrentThreadId()!=owner_thread);return PssAttach(hwnd);}
int main(void){
    owner_thread=GetCurrentThreadId();module=GetModuleHandleW(0);image_reservation[sizeof(image_reservation)-1]=1;
    BYTE* base=(BYTE*)module;assert(base==(BYTE*)0x400000);
    IMAGE_DOS_HEADER* dos=(IMAGE_DOS_HEADER*)base;IMAGE_NT_HEADERS32* nt=(IMAGE_NT_HEADERS32*)(base+dos->e_lfanew);
    DWORD protect;assert(VirtualProtect(base,4096,PAGE_READWRITE,&protect));
    nt->FileHeader.TimeDateStamp=0x5C663F2A;nt->OptionalHeader.SizeOfImage=28504064;
    DWORD ignore;VirtualProtect(base,4096,protect,&ignore);
    HWND hwnd=CreateWindowExW(0,L"STATIC",L"PSS thread fixture",0,0,0,1,1,HWND_MESSAGE,0,module,0);assert(hwnd);
    for(int round=0;round<2;++round){
        *(DWORD*)0x15D8E44=3;
        HANDLE thread=CreateThread(0,0,attach_worker,hwnd,0,0);assert(thread);
        assert(WaitForSingleObject(thread,5000)==WAIT_OBJECT_0);DWORD result=0;GetExitCodeThread(thread,&result);CloseHandle(thread);assert(result==1);
        assert(initializations==round);
        SendMessageW(hwnd,WM_TIMER,0x505353,0);
        assert(initializations==round+1&&initializer_thread==owner_thread);
        assert(hud_initializations==round+1&&draw_initializations==round+1&&native_active&&hud_world&&!patched);
        SendMessageW(hwnd,WM_TIMER,0x505353,0);assert(initializations==round+1);
        *(DWORD*)0x15D8E44=0;SendMessageW(hwnd,WM_TIMER,0x505353,0);assert(!native_active&&!hud_world);
        SendMessageW(hwnd,CONTROL,2,0);
    }
    DestroyWindow(hwnd);
    puts("PASS actual worker-thread PssAttach defers activity/HUD/draw initialization to owner WM_TIMER, once per attach; HUD runs without PSS patch/start and hides outside world; detach/re-attach");
}
