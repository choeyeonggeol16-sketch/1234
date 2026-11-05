#define UNICODE
#define _UNICODE
#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <windows.h>
#include <iphlpapi.h>
#include <stdint.h>
#include <string.h>
#include <wchar.h>
#include "PssAppCenter.h"
#include "PssBridge.h"

/* 19.02.15.1002 AppCenterUI home-navigation literal:
 * BEBBBE: push 0x01255834
 * BEBBC3: lea ecx,[ebp-0x28]
 * BEBBC6: call std::string constructor
 * BEBBE6: call 0xC2B290 (web-view navigation).
 * Patch only that literal's address, never the auth/login handlers.
 */
enum {
    APP_ROUTE_PUSH = 0x00BEBBBE,
    APP_ROUTE_VALUE = 0x00BEBBBF,
    ORIGINAL_HOME_URL = 0x01255834,
    MARKET_URL_PUSH = 0x00BF2575,
    MARKET_URL_VALUE = 0x00BF2576,
    ORIGINAL_MARKET_URL = 0x01257090
};
static char destination[2048];
static BOOL installed;
static BOOL market_installed;
static BOOL warned_market;
static DWORD last_attempt;
static BOOL warned_mismatch,warned_server;
static BYTE game_tcp_rows[262144];
static const BYTE expected_code[] = {
    0x90,0x90,0x68,0x34,0x58,0x25,0x01,
    0x8D,0x4D,0xD8,0xE8,0x45,0xC5,0x83,0xFF
};
static const char original_home[] = "http://g.lineage.plaync.com/ingame/index";
static const char original_market[] = "http://g.lineage.plaync.com/ingame/item/search";
/* 2019 client's native item-search URL builder at BF2570, identified in the
 * loaded 19.02.15.1002 image. Do not patch the comparison at BEB811.
 * This builder copies the URL to the same embedded AppCenter navigator. */
static const BYTE expected_market_code[] = {
    0x68,0xff,0x03,0x00,0x00,0x68,0x90,0x70,0x25,0x01,
    0x8d,0x41,0x2c,0x68,0x00,0x04,0x00,0x00,0x50,0x90,
    0xe8,0x07,0xb8,0x2a,0x76
};

static BOOL read_exact(DWORD address, void *result, SIZE_T size) {
    SIZE_T done=0;
    return ReadProcessMemory(GetCurrentProcess(),(LPCVOID)(uintptr_t)address,result,size,&done)
           &&done==size;
}
static BOOL verified_site(void) {
    BYTE code[sizeof(expected_code)],home[sizeof(original_home)];
    if(GetModuleHandleW(0)!=(HMODULE)(uintptr_t)0x400000)return FALSE;
    if(!read_exact(APP_ROUTE_PUSH-2,code,sizeof(code)) ||
       memcmp(code,expected_code,sizeof(code))!=0)return FALSE;
    if(!read_exact(ORIGINAL_HOME_URL,home,sizeof(home)) ||
       memcmp(home,original_home,sizeof(home))!=0)return FALSE;
    return TRUE;
}
static BOOL write_word(DWORD address,DWORD value) {
    DWORD previous=0,ignored=0;
    if(!VirtualProtect((LPVOID)(uintptr_t)address,sizeof(value),
                       PAGE_EXECUTE_READWRITE,&previous))return FALSE;
    CopyMemory((LPVOID)(uintptr_t)address,&value,sizeof(value));
    BOOL flushed=FlushInstructionCache(GetCurrentProcess(),
                         (LPCVOID)(uintptr_t)address,sizeof(value));
    BOOL protected_ok=VirtualProtect((LPVOID)(uintptr_t)address,
                                     sizeof(value),previous,&ignored);
    return flushed&&protected_ok;
}
static BOOL write_value(DWORD value) {
    return write_word(APP_ROUTE_VALUE,value);
}
static BOOL verified_market_site(void) {
    BYTE code[sizeof(expected_market_code)],market[sizeof(original_market)];
    if(GetModuleHandleW(0)!=(HMODULE)(uintptr_t)0x400000)return FALSE;
    if(!read_exact(MARKET_URL_PUSH-5,code,sizeof(code)) ||
       memcmp(code,expected_market_code,sizeof(code))!=0)return FALSE;
    if(!read_exact(ORIGINAL_MARKET_URL,market,sizeof(market)) ||
       memcmp(market,original_market,sizeof(market))!=0)return FALSE;
    return TRUE;
}
static void route_native_market(DWORD new_url) {
    if(market_installed)return;
    if(!verified_market_site()) {
        if(!warned_market) {
            warned_market=TRUE;
            PssBridgeLog(L"AppCenter native item-search code differs; market routing unchanged");
        }
        return;
    }
    if(!write_word(MARKET_URL_VALUE,new_url)) {
        DWORD actual=0;
        if(read_exact(MARKET_URL_VALUE,&actual,4) && actual==new_url)
            write_word(MARKET_URL_VALUE,(DWORD)ORIGINAL_MARKET_URL);
        PssBridgeLog(L"AppCenter native item-search route patch failed");
        return;
    }
    market_installed=TRUE;
    PssBridgeLog(L"AppCenter native /시세 item-search URL redirected to RKern market page");
}

static BOOL build_paths(HMODULE module,wchar_t *ini,wchar_t *page) {
    wchar_t file[MAX_PATH];
    DWORD n=GetModuleFileNameW(module,file,MAX_PATH);
    if(!n||n>=MAX_PATH)return FALSE;
    wchar_t *last=file;
    for(wchar_t *p=file;*p;++p)if(*p==L'\\'||*p==L'/')last=p+1;
    size_t used=(size_t)(last-file);
    if(used+27>=MAX_PATH)return FALSE;
    lstrcpyW(last,L"Pss2019.ini");
    lstrcpyW(ini,file);
    lstrcpyW(last,L"Pss2019AppCenter.html");
    lstrcpyW(page,file);
    return TRUE;
}
static BOOL to_utf8(const wchar_t *source,char *out,size_t capacity) {
    int len=WideCharToMultiByte(CP_UTF8,WC_ERR_INVALID_CHARS,source,-1,
                               out,(int)capacity,0,0);
    return len>0&&(size_t)len<=capacity;
}
static BOOL from_config(const wchar_t *ini) {
    wchar_t wide[1024];
    DWORD n=GetPrivateProfileStringW(L"AppCenter",L"Url",L"",wide,1024,ini);
    if(!n)return FALSE;
    if(wcsncmp(wide,L"http://",7)!=0 && wcsncmp(wide,L"https://",8)!=0)
        return FALSE;
    const wchar_t *host=wide+(wcsncmp(wide,L"https://",8)==0?8:7);
    if(!*host||*host==L'/'||*host==L'?')return FALSE;
    for(const wchar_t *p=wide;*p;++p)
        if(*p<32||*p==127)return FALSE;
    return to_utf8(wide,destination,sizeof(destination));
}

/* Route AppCenter to the SAME remote host as this game's actual TCP session.
 * No GamePort, WindowPort, launcher PID, or client INI network values.
 * If several unrelated endpoints exist, refuse to guess the game's server.
 * AppCenter HTTP remains an independent service on the server (TCP 18085). */
static BOOL from_live_game_connection(void) {
    DWORD size=(DWORD)sizeof(game_tcp_rows);
    DWORD result=GetExtendedTcpTable(game_tcp_rows,&size,FALSE,AF_INET,TCP_TABLE_OWNER_PID_ALL,0);
    if(result!=NO_ERROR)return FALSE;
    const MIB_TCPTABLE_OWNER_PID *tcp=(const MIB_TCPTABLE_OWNER_PID*)game_tcp_rows;
    const DWORD pid=GetCurrentProcessId();
    DWORD remote=0;
    BOOL found=FALSE;
    for(DWORD i=0;i<tcp->dwNumEntries;++i) {
        const MIB_TCPROW_OWNER_PID *row=&tcp->table[i];
        if(row->dwOwningPid!=pid||row->dwState!=MIB_TCP_STATE_ESTAB)continue;
        const USHORT port=ntohs((USHORT)row->dwRemotePort);
        if(port==0||port==80||port==443||port==18085)continue;
        if(row->dwRemoteAddr==0||row->dwRemoteAddr==0xffffffffUL)continue;
        if(found&&remote!=row->dwRemoteAddr)return FALSE;
        remote=row->dwRemoteAddr;
        found=TRUE;
    }
    if(!found)return FALSE;
    const BYTE *ip=(const BYTE*)&remote;
    wsprintfA(destination,"http://%u.%u.%u.%u:18085/",
              (unsigned)ip[0],(unsigned)ip[1],(unsigned)ip[2],(unsigned)ip[3]);
    return TRUE;
}

BOOL PssAppCenterInitialize(HWND game, HMODULE module) {
    if(installed)return TRUE;
    DWORD now=GetTickCount();
    if(last_attempt&&(DWORD)(now-last_attempt)<1000U)return FALSE;
    last_attempt=now;
    if(!game||!module||GetWindowThreadProcessId(game,0)!=GetCurrentThreadId())
        return FALSE;
    wchar_t ini[MAX_PATH],page[MAX_PATH];
    if(!build_paths(module,ini,page))return FALSE;
    if(!GetPrivateProfileIntW(L"AppCenter",L"Enabled",1,ini)) {
        PssBridgeLog(L"AppCenter URL routing disabled by INI");
        return FALSE;
    }
    BOOL configured=from_config(ini);
    BOOL discovered=FALSE;
    if(!configured) {
        discovered=from_live_game_connection();
        if(!discovered) {
            if(!warned_server) {
                PssBridgeLog(L"AppCenter game server endpoint unavailable/ambiguous; retrying without changing original page");
                warned_server=TRUE;
            }
            return FALSE;
        }
        warned_server=FALSE;
    }
    if(!verified_site()) {
        if(!warned_mismatch){PssBridgeLog(L"AppCenter home-navigation signature unavailable; retrying after client code loads");warned_mismatch=TRUE;}
        return FALSE;
    }
    DWORD candidate=(DWORD)(uintptr_t)destination;
    if(!candidate || !write_value(candidate)) {
        /* If an attempted write partially succeeded, restore immediately. */
        DWORD actual=0;
        if(read_exact(APP_ROUTE_VALUE,&actual,4) && actual==candidate)
            write_value((DWORD)ORIGINAL_HOME_URL);
        PssBridgeLog(L"AppCenter URL patch rejected; no appcenter route installed");
        return FALSE;
    }
    installed=TRUE;
    route_native_market(candidate);
    PssBridgeLog(configured?
        L"AppCenter exact home URL redirected to configured HTTP(S) page":
        L"AppCenter exact home URL redirected to auto-detected game server HTTP port 18085");
    return TRUE;
}
void PssAppCenterDetach(void) {
    if(market_installed) {
        DWORD actual=0;
        if(read_exact(MARKET_URL_VALUE,&actual,4) &&
           actual==(DWORD)(uintptr_t)destination &&
           write_word(MARKET_URL_VALUE,(DWORD)ORIGINAL_MARKET_URL)) {
            market_installed=FALSE;
        } else {
            PssBridgeLog(L"AppCenter market route ownership changed; restore skipped");
        }
    }
    if(!installed)return;
    DWORD actual=0;
    if(read_exact(APP_ROUTE_VALUE,&actual,4) &&
       actual==(DWORD)(uintptr_t)destination &&
       write_value((DWORD)ORIGINAL_HOME_URL)) {
        installed=FALSE;
        PssBridgeLog(L"AppCenter home URL restored");
    }else {
        /* Keep the destination storage valid while client code might refer to it. */
        PssBridgeLog(L"AppCenter URL ownership changed; original code not overwritten");
    }
}
