#define UNICODE
#define _UNICODE
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdint.h>
#include <string.h>
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
    ORIGINAL_HOME_URL = 0x01255834
};
static char destination[2048];
static BOOL installed;
static const BYTE expected_code[] = {
    0x90,0x90,0x68,0x34,0x58,0x25,0x01,
    0x8D,0x4D,0xD8,0xE8,0x45,0xC5,0x83,0xFF
};
static const char original_home[] = "http://g.lineage.plaync.com/ingame/index";

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
static BOOL write_value(DWORD value) {
    DWORD previous=0,ignored=0;
    if(!VirtualProtect((LPVOID)(uintptr_t)APP_ROUTE_VALUE,sizeof(value),
                       PAGE_EXECUTE_READWRITE,&previous))return FALSE;
    CopyMemory((LPVOID)(uintptr_t)APP_ROUTE_VALUE,&value,sizeof(value));
    BOOL flushed=FlushInstructionCache(GetCurrentProcess(),
                         (LPCVOID)(uintptr_t)APP_ROUTE_VALUE,sizeof(value));
    BOOL protected_ok=VirtualProtect((LPVOID)(uintptr_t)APP_ROUTE_VALUE,
                                     sizeof(value),previous,&ignored);
    return flushed&&protected_ok;
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
static BOOL build_file_url(const wchar_t *path) {
    char path_utf8[MAX_PATH*4];
    static const char hex[]="0123456789ABCDEF";
    if(!to_utf8(path,path_utf8,sizeof(path_utf8)))return FALSE;
    const char *prefix="file:///";
    size_t used=8;
    memcpy(destination,prefix,used);
    for(const unsigned char *p=(const unsigned char *)path_utf8;*p;++p) {
        unsigned char ch=*p;
        if(ch=='\\')ch='/';
        BOOL normal=(ch>='A'&&ch<='Z')||(ch>='a'&&ch<='z')||
                    (ch>='0'&&ch<='9')||ch=='/'||ch==':'||ch=='.'||
                    ch=='-'||ch=='_'||ch=='~';
        if(used+(normal?1U:3U)+1U>sizeof(destination))return FALSE;
        if(normal)destination[used++]=(char)ch;
        else {
            destination[used++]='%';
            destination[used++]=hex[ch>>4];
            destination[used++]=hex[ch&15];
        }
    }
    destination[used]=0;
    return TRUE;
}
static BOOL from_config(const wchar_t *ini) {
    wchar_t wide[1024];
    DWORD n=GetPrivateProfileStringW(L"AppCenter",L"Url",L"",wide,1024,ini);
    if(!n)return FALSE;
    if(wcsncmp(wide,L"http://",7)!=0 && wcsncmp(wide,L"https://",8)!=0)
        return FALSE;
    if(!wide[7])return FALSE;
    for(const wchar_t *p=wide;*p;++p)
        if(*p<32||*p==127)return FALSE;
    return to_utf8(wide,destination,sizeof(destination));
}
BOOL PssAppCenterInitialize(HWND game, HMODULE module) {
    if(installed)return TRUE;
    if(!game||!module||GetWindowThreadProcessId(game,0)!=GetCurrentThreadId())
        return FALSE;
    wchar_t ini[MAX_PATH],page[MAX_PATH];
    if(!build_paths(module,ini,page))return FALSE;
    if(!GetPrivateProfileIntW(L"AppCenter",L"Enabled",1,ini)) {
        PssBridgeLog(L"AppCenter URL routing disabled by INI");
        return FALSE;
    }
    BOOL configured=from_config(ini);
    if(!configured) {
        DWORD attributes=GetFileAttributesW(page);
        if(attributes==INVALID_FILE_ATTRIBUTES||(attributes&FILE_ATTRIBUTE_DIRECTORY)) {
            PssBridgeLog(L"AppCenter test HTML missing: original URL unchanged");
            return FALSE;
        }
        if(!build_file_url(page))return FALSE;
    }
    if(!verified_site()) {
        PssBridgeLog(L"AppCenter exact home-navigation bytes mismatch; original URL unchanged");
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
    PssBridgeLog(configured?
        L"AppCenter exact home URL redirected to configured HTTP(S) page":
        L"AppCenter exact home URL redirected to local success-test HTML");
    return TRUE;
}
void PssAppCenterDetach(void) {
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
