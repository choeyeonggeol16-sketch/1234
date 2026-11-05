#define UNICODE
#define _UNICODE
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdint.h>
#include <string.h>
#include "PssGamePacket.h"
#include "PssBridge.h"

/*
 * Exact 19.02.15.1002 game client: its native 2102 ACK decoder starts at
 * 006429B0. The marker is carried INSIDE the authenticated game stream.
 * Nothing here opens TCP sockets or uses the launcher PID or Pss2019.ini.
 *
 * Wire (protobuf payload after native extended-opcode dispatch):
 * 08 04 7A 10 "RKERNPSSOPEN2026" [00 00]
 * result=4 is outside native result values 0..3, so an absent/late DLL
 * receives a safely ignored ACK. DLL consumes only the full marker.
 * Every ordinary 2102 result is forwarded byte-for-byte to native code.
 */
enum { ACK_ENTRY=0x006429B0, ACK_CALL_SITE=0x00642A61 };
static const BYTE native_head[5]={0x55,0x8B,0xEC,0x6A,0xFF};
static const BYTE ack_caller[5]={0xE8,0xBA,0x71,0x02,0x00};
static const BYTE open_marker[16]={
    'R','K','E','R','N','P','S','S','O','P','E','N','2','0','2','6'
};
typedef void (__cdecl *AckDecoder)(const BYTE *, int);
static AckDecoder native_ack;
static BYTE *trampoline;
static BYTE patched[5];
static HWND game_window;
static UINT game_message;
static BOOL installed;
static volatile LONG observed_ack;

static BOOL bytes_at(DWORD address,const BYTE *expected,SIZE_T length) {
    BYTE actual[32];
    SIZE_T count=0;
    if(length>sizeof(actual) ||
       !ReadProcessMemory(GetCurrentProcess(),(LPCVOID)(uintptr_t)address,actual,length,&count) ||
       count!=length)return FALSE;
    return memcmp(actual,expected,length)==0;
}

static BOOL marker_matches(const BYTE* payload,int length) {
    if(!payload||(length!=20&&length!=22))return FALSE;
    if(payload[0]!=0x08||payload[1]!=0x04||
       payload[2]!=0x7a||payload[3]!=sizeof(open_marker))return FALSE;
    if(memcmp(payload+4,open_marker,sizeof(open_marker))!=0)return FALSE;
    if(length==22&&(payload[20]!=0||payload[21]!=0))return FALSE;
    return TRUE;
}

static void __cdecl ack_dispatch(const BYTE *payload,int length) {
    if(marker_matches(payload,length)) {
        if(game_window&&game_message&&IsWindow(game_window)&&
           PostMessageW(game_window,game_message,3,0)) {
            PssBridgeLog(L"[PSS-INBAND] native 2102 marker received; settings OPEN queued on UI thread");
        } else {
            PssBridgeLog(L"[PSS-INBAND] native marker received; no game UI receiver");
        }
        return; // A control marker is NEVER passed to native PSS ack logic.
    }
    if(InterlockedCompareExchange(&observed_ack,1,0)==0) {
        wchar_t message[128];
        wsprintfW(message,L"[PSS-INBAND] normal native 2102 ACK forwarded length=%d",length);
        PssBridgeLog(message);
    }
    if(native_ack)native_ack(payload,length);
}

/* No separate receiver thread, no new queue; native decoder executes on its
 * existing game packet thread. UI is dispatched through the game's HWND. */
BOOL PssGamePacketInitialize(HWND game,UINT message) {
    if(installed) {
        if(game_window!=game)return FALSE;
        game_message=message;
        return TRUE;
    }
    if(!game||!message||!IsWindow(game))return FALSE;
    if(!bytes_at(ACK_ENTRY,native_head,sizeof(native_head)) ||
       !bytes_at(ACK_CALL_SITE,ack_caller,sizeof(ack_caller))) {
        PssBridgeLog(L"[PSS-INBAND] native 2102 decoder signature differs; hook refused");
        return FALSE;
    }
    BYTE *fresh=(BYTE*)VirtualAlloc(NULL,16,MEM_COMMIT|MEM_RESERVE,PAGE_EXECUTE_READWRITE);
    if(!fresh)return FALSE;
    CopyMemory(fresh,native_head,sizeof(native_head));
    fresh[5]=0xe9;
    *(int32_t*)(void*)(fresh+6)=(int32_t)((uintptr_t)(ACK_ENTRY+5)-(uintptr_t)(fresh+10));
    FlushInstructionCache(GetCurrentProcess(),fresh,10);
    BYTE jump[5]={0xe9,0,0,0,0};
    *(int32_t*)(void*)(jump+1)=(int32_t)((uintptr_t)&ack_dispatch-(uintptr_t)(ACK_ENTRY+5));
    DWORD protection=0,ignored=0;
    if(!VirtualProtect((LPVOID)(uintptr_t)ACK_ENTRY,5,PAGE_EXECUTE_READWRITE,&protection)) {
        VirtualFree(fresh,0,MEM_RELEASE);
        return FALSE;
    }
    /* Check ownership again after unlocking the page; do not overwrite any
     * concurrent third-party patch. */
    BOOL ownership=bytes_at(ACK_ENTRY,native_head,sizeof(native_head));
    if(ownership) {
        /* Publish immutable target before exposing the entry-point jump. */
        native_ack=(AckDecoder)(void*)fresh;
        game_window=game;
        game_message=message;
        CopyMemory((LPVOID)(uintptr_t)ACK_ENTRY,jump,sizeof(jump));
        FlushInstructionCache(GetCurrentProcess(),(LPCVOID)(uintptr_t)ACK_ENTRY,5);
    }
    VirtualProtect((LPVOID)(uintptr_t)ACK_ENTRY,5,protection,&ignored);
    if(!ownership) {
        VirtualFree(fresh,0,MEM_RELEASE);
        return FALSE;
    }
    trampoline=fresh;
    CopyMemory(patched,jump,sizeof(patched));
    installed=TRUE;
    PssBridgeLog(L"[PSS-INBAND] native game packet 2102 decoder hook installed; no companion TCP port");
    return TRUE;
}

void PssGamePacketDetach(void) {
    if(!installed)return;
    if(bytes_at(ACK_ENTRY,patched,sizeof(patched))) {
        DWORD protection=0,ignored=0;
        if(VirtualProtect((LPVOID)(uintptr_t)ACK_ENTRY,5,PAGE_EXECUTE_READWRITE,&protection)) {
            if(bytes_at(ACK_ENTRY,patched,sizeof(patched))) {
                CopyMemory((LPVOID)(uintptr_t)ACK_ENTRY,native_head,sizeof(native_head));
                FlushInstructionCache(GetCurrentProcess(),(LPCVOID)(uintptr_t)ACK_ENTRY,5);
            }
            VirtualProtect((LPVOID)(uintptr_t)ACK_ENTRY,5,protection,&ignored);
        }
    } else {
        PssBridgeLog(L"[PSS-INBAND] native ACK hook ownership changed; restore skipped");
    }
    installed=FALSE;
    game_window=NULL;
    game_message=0;
    native_ack=NULL;
    /* Keep the trampoline allocated: a concurrently running native call can
     * still be returning through it during detach. 16 bytes per process. */
    (void)trampoline;
}