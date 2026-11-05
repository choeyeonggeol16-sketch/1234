#define _WIN32_WINNT 0x0601
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <iphlpapi.h>
#include <bcrypt.h>
#include <stdint.h>
#include <string.h>
#include "PssBridge.h"
#ifdef PSS_BRIDGE_TEST
extern BOOL WINAPI PssBridgeTestPostMessage(HWND,UINT,WPARAM,LPARAM);
extern DWORD WINAPI PssBridgeTestTcpTable(PVOID,PDWORD,BOOL,ULONG,TCP_TABLE_CLASS,ULONG);
#define PostMessageW PssBridgeTestPostMessage
#define GetExtendedTcpTable PssBridgeTestTcpTable
#endif

enum { REGISTER_START=0x7DF0, REGISTER_COMMIT=0x7DF1, REGISTER_BYTE=0x7E00 };
typedef struct { DWORD remote; USHORT local_port,game_port,control_port; BYTE token[16]; LONG serial; } Target;
static CRITICAL_SECTION guard;
static HANDLE wake_event,worker_handle;
static BOOL initialized,active,target_ready,needs_registration;
static BOOL endpoint_warning,sender_warning;
static DWORD endpoint_missing_since;
static HWND game_window;
static UINT game_message;
static Target target;
static USHORT game_port=5000,control_port=5001;
static DWORD next_tick,retry_delay=1000,next_registration;
static BYTE tcp_table[262144];
typedef void (__attribute__((thiscall)) *NativeMethod)(void*);
typedef void (__cdecl *NativeSend)(int,void*);

static BOOL readmem(DWORD address,void* data,SIZE_T size) {
#ifdef PSS_BRIDGE_TEST
    /* QEMU/Wine self ReadProcessMemory sees the host mapping, not this guest fixture. */
    memcpy(data,(const void*)(uintptr_t)address,size);return TRUE;
#else
    SIZE_T got=0;return ReadProcessMemory(GetCurrentProcess(),(void*)(uintptr_t)address,data,size,&got)&&got==size;
#endif
}
static BOOL matches(DWORD address,const BYTE* expected,SIZE_T count) {
    BYTE bytes[32];BOOL read=count<=sizeof(bytes)&&readmem(address,bytes,count);
#ifdef PSS_BRIDGE_TEST
    if(!read||memcmp(bytes,expected,count)) {
        wchar_t message[128];wsprintfW(message,L"fixture signature mismatch address=%08lx read=%d bytes=%02x,%02x expected=%02x,%02x",(unsigned long)address,read,read?bytes[0]:0,read?bytes[1]:0,expected[0],expected[1]);PssBridgeLog(message);
    }
#endif
    return read&&!memcmp(bytes,expected,count);
}
static BOOL native_sender_ready(void) {
    static const BYTE head[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x28,0x5D,0x01,0x01};
    static const BYTE ctor[]={0xE8,0xA0,0xDB,0xFD,0xFF};
    static const BYTE send[]={0xE8,0xBB,0x4A,0x52,0x00};
    static const BYTE dtor[]={0xE8,0x29,0xE3,0xFD,0xFF};
    return matches(0x642B80,head,sizeof(head))&&matches(0x642BAB,ctor,sizeof(ctor))
        &&matches(0x642BC0,send,sizeof(send))&&matches(0x642BD2,dtor,sizeof(dtor));
}
static BOOL register_on_game_connection(const BYTE token[16]) {
    if(!native_sender_ready()) {
        if(!sender_warning)PssBridgeLog(L"PSS item registration skipped: native time-request code differs");
        sender_warning=TRUE;return FALSE;
    }
    sender_warning=FALSE;
    BYTE object[64] __attribute__((aligned(16)))={0};
    ((NativeMethod)0x620750)(object);
    ((NativeSend)0xB67680)(REGISTER_START,object);
    for(int i=0;i<16;i++)((NativeSend)0xB67680)(REGISTER_BYTE|token[i],object);
    ((NativeSend)0xB67680)(REGISTER_COMMIT,object);
    ((NativeMethod)0x620F00)(object);
    PssBridgeLog(L"PSS item capability queued on the native game connection");
    return TRUE;
}
static BOOL endpoint(Target* output) {
    DWORD size=sizeof(tcp_table),pid=GetCurrentProcessId();int found=0,prior_matches=0;
    Target preferred,only={0},matched={0};
    BOOL have_prior;
    EnterCriticalSection(&guard);
    preferred=target;have_prior=active&&target_ready;
    LeaveCriticalSection(&guard);
    if(GetExtendedTcpTable(tcp_table,&size,FALSE,AF_INET,TCP_TABLE_OWNER_PID_ALL,0)!=NO_ERROR)return FALSE;
    MIB_TCPTABLE_OWNER_PID* rows=(MIB_TCPTABLE_OWNER_PID*)tcp_table;
    for(DWORD i=0;i<rows->dwNumEntries;i++) {
        MIB_TCPROW_OWNER_PID* row=&rows->table[i];
        if(row->dwOwningPid!=pid||row->dwState!=MIB_TCP_STATE_ESTAB||ntohs((USHORT)row->dwRemotePort)!=game_port)continue;
        Target candidate={0};
        candidate.remote=row->dwRemoteAddr;candidate.local_port=(USHORT)row->dwLocalPort;
        candidate.game_port=game_port;candidate.control_port=control_port;
        only=candidate;found++;
        if(have_prior&&candidate.remote==preferred.remote&&candidate.local_port==preferred.local_port) {
            matched=candidate;prior_matches++;
        }
    }
    if(have_prior&&prior_matches==1){*output=matched;return TRUE;}
    if(found==1){*output=only;return TRUE;}
    return FALSE;
}
static BOOL current(LONG serial) {
    BOOL same;EnterCriticalSection(&guard);
    same=active&&target_ready&&!needs_registration&&target.serial==serial;
    LeaveCriticalSection(&guard);return same;
}
static BOOL writable(SOCKET socket,LONG serial,DWORD timeout) {
    DWORD started=GetTickCount();
    while(current(serial)&&GetTickCount()-started<timeout) {
        fd_set write,errors;FD_ZERO(&write);FD_ZERO(&errors);FD_SET(socket,&write);FD_SET(socket,&errors);
        struct timeval wait={0,200000};int result=select(0,0,&write,&errors,&wait);
        if(result==SOCKET_ERROR||FD_ISSET(socket,&errors))return FALSE;
        if(result>0&&FD_ISSET(socket,&write)) {
            int error=0,len=sizeof(error);
            return getsockopt(socket,SOL_SOCKET,SO_ERROR,(char*)&error,&len)==0&&error==0;
        }
    }
    return FALSE;
}
static BOOL send_all(SOCKET socket,const BYTE* bytes,int length,LONG serial) {
    DWORD started=GetTickCount();int sent=0;
    while(sent<length&&current(serial)&&GetTickCount()-started<2000) {
        int count=send(socket,(const char*)bytes+sent,length-sent,0);
        if(count>0)sent+=count;
        else if(count==SOCKET_ERROR&&WSAGetLastError()==WSAEWOULDBLOCK) {
            if(!writable(socket,serial,500))return FALSE;
        } else return FALSE;
    }
    return sent==length;
}
static SOCKET connect_target(const Target* selected,BYTE version) {
    SOCKET socket_handle=socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);
    if(socket_handle==INVALID_SOCKET)return INVALID_SOCKET;
    int nodelay=1;setsockopt(socket_handle,IPPROTO_TCP,TCP_NODELAY,(const char*)&nodelay,sizeof(nodelay));
    u_long nonblocking=1;
    if(ioctlsocket(socket_handle,FIONBIO,&nonblocking)!=0) {closesocket(socket_handle);return INVALID_SOCKET;}
    struct sockaddr_in address;memset(&address,0,sizeof(address));address.sin_family=AF_INET;
    address.sin_addr.s_addr=selected->remote;address.sin_port=htons(selected->control_port);
    int result=connect(socket_handle,(struct sockaddr*)&address,sizeof(address));
    if(result!=0&&(WSAGetLastError()!=WSAEWOULDBLOCK||!writable(socket_handle,selected->serial,2000))) {
        closesocket(socket_handle);return INVALID_SOCKET;
    }
    BYTE hello[24]={'P','S','S','U','I',1,0,0};hello[5]=version;memcpy(hello+8,selected->token,16);
    if(!send_all(socket_handle,hello,sizeof(hello),selected->serial)) {closesocket(socket_handle);return INVALID_SOCKET;}
    return socket_handle;
}
static BOOL receive_exact(SOCKET socket,BYTE* bytes,int length,LONG serial) {
    DWORD started=GetTickCount();int used=0;
    while(used<length&&current(serial)&&GetTickCount()-started<2000U) {
        int got=recv(socket,(char*)bytes+used,length-used,0);
        if(got>0){used+=got;continue;}
        if(got==0||WSAGetLastError()!=WSAEWOULDBLOCK)return FALSE;
        fd_set read;FD_ZERO(&read);FD_SET(socket,&read);struct timeval wait={0,200000};
        if(select(0,&read,0,0,&wait)==SOCKET_ERROR)return FALSE;
    }
    return used==length;
}
static BOOL connection_loop(SOCKET socket,const Target* selected,BYTE version) {
    DWORD started=GetTickCount(),last_ping=started;BOOL bound=FALSE;
    while(current(selected->serial)) {
        fd_set read;FD_ZERO(&read);FD_SET(socket,&read);struct timeval wait={0,250000};
        int result=select(0,&read,0,0,&wait);
        if(result==SOCKET_ERROR)break;
        if(result>0) {
            BYTE command=0;int got=recv(socket,(char*)&command,1,0);
            if(got!=1)break;
            if(command==16&&!bound) {
                bound=TRUE;EnterCriticalSection(&guard);retry_delay=1000;LeaveCriticalSection(&guard);
                PssBridgeLog(L"PSS item companion BOUND to the authenticated game session");
            } else if(command==1&&bound) {
                HWND hwnd;UINT message;EnterCriticalSection(&guard);hwnd=game_window;message=game_message;LeaveCriticalSection(&guard);
                PostMessageW(hwnd,message,3,0);
                PssBridgeLog(L"PSS item OPEN queued to the game UI thread");
            } else if(command==3&&bound&&version==2) {
                BYTE data[4];if(!receive_exact(socket,data,4,selected->serial))break;
                uint32_t id=(uint32_t)data[0]|((uint32_t)data[1]<<8)|((uint32_t)data[2]<<16)|((uint32_t)data[3]<<24);
                if(!id||id>INT32_MAX)break;
                HWND hwnd;UINT message;EnterCriticalSection(&guard);hwnd=game_window;message=game_message;LeaveCriticalSection(&guard);
                if(!PostMessageW(hwnd,message,4,(LPARAM)id))break;
            } else break;
        }
        DWORD now=GetTickCount();
        if(!bound&&now-started>=5000)break;
        if(bound&&now-last_ping>=10000) {
            BYTE ping=2;if(!send_all(socket,&ping,1,selected->serial))break;last_ping=now;
        }
    }
    return bound;
}
static DWORD WINAPI worker(LPVOID unused) {
    (void)unused;WSADATA data;
    if(WSAStartup(MAKEWORD(2,2),&data)!=0)return 1;
    for(;;) {
        WaitForSingleObject(wake_event,INFINITE);
        Target selected;BOOL ready;EnterCriticalSection(&guard);
        ready=active&&target_ready&&!needs_registration;selected=target;LeaveCriticalSection(&guard);
        if(!ready)continue;
        SOCKET socket=connect_target(&selected,2);BOOL bound=FALSE;
        if(socket!=INVALID_SOCKET) {bound=connection_loop(socket,&selected,2);closesocket(socket);}
        // Old servers reject v2 before claiming the single-use token. Retry v1
        // immediately so existing item/window support remains compatible.
        if(!bound&&current(selected.serial)) {
            socket=connect_target(&selected,1);
            if(socket!=INVALID_SOCKET) {connection_loop(socket,&selected,1);closesocket(socket);}
        }
        EnterCriticalSection(&guard);
        if(active&&target.serial==selected.serial) {
            needs_registration=TRUE;next_registration=GetTickCount()+retry_delay;
            if(retry_delay<30000)retry_delay=retry_delay*2>30000?30000:retry_delay*2;
            PssBridgeLog(L"PSS item companion unavailable/disconnected; bounded retry scheduled");
        }
        LeaveCriticalSection(&guard);
    }
    /* The DLL is pinned for process lifetime, as in the original helper. */
    return 0;
}
BOOL PssBridgeInitialize(HWND hwnd,UINT message,HMODULE module) {
    if(!initialized) {
        InitializeCriticalSection(&guard);
        wake_event=CreateEventW(0,FALSE,FALSE,0);
        if(!wake_event) {DeleteCriticalSection(&guard);return FALSE;}
        wchar_t ini[MAX_PATH];DWORD path_length=GetModuleFileNameW(module,ini,MAX_PATH);
        if(path_length==0||path_length>=MAX_PATH) {CloseHandle(wake_event);DeleteCriticalSection(&guard);return FALSE;}
        wchar_t* tail=ini;for(wchar_t* p=ini;*p;p++)if(*p==L'\\')tail=p+1;
        if((SIZE_T)(tail-ini)+lstrlenW(L"Pss2019.ini")>=MAX_PATH) {CloseHandle(wake_event);DeleteCriticalSection(&guard);return FALSE;}
        lstrcpyW(tail,L"Pss2019.ini");
        UINT port=GetPrivateProfileIntW(L"PssBridge",L"GamePort",5000,ini);
        UINT companion=GetPrivateProfileIntW(L"PssBridge",L"WindowPort",port+1,ini);
        if(port<1||port>65534||companion<1||companion>65535) {CloseHandle(wake_event);DeleteCriticalSection(&guard);return FALSE;}
        game_port=(USHORT)port;control_port=(USHORT)companion;
        initialized=TRUE;
        worker_handle=CreateThread(0,0,worker,0,0,0);
        if(!worker_handle) {initialized=FALSE;CloseHandle(wake_event);DeleteCriticalSection(&guard);return FALSE;}
        CloseHandle(worker_handle);worker_handle=0;
    }
    EnterCriticalSection(&guard);
    BOOL newly_active=!active;
    game_window=hwnd;game_message=message;active=TRUE;
    if(newly_active) {
        target_ready=FALSE;needs_registration=TRUE;
        target.serial++;next_registration=0;retry_delay=1000;endpoint_missing_since=0;
    }
    LeaveCriticalSection(&guard);
    if(newly_active)SetEvent(wake_event);
    return TRUE;
}
void PssBridgeTick(void) {
    if(!initialized)return;
    DWORD now=GetTickCount();
    if(next_tick!=0&&(LONG)(now-next_tick)<0)return;
    next_tick=now+1000;
    Target next;memset(&next,0,sizeof(next));
    if(!endpoint(&next)) {
        if(!endpoint_warning)PssBridgeLog(L"PSS item bridge: game TCP endpoint missing/ambiguous; retaining session during short transient");
        endpoint_warning=TRUE;
        if(endpoint_missing_since==0)endpoint_missing_since=now;
        // A brief TCP-table ambiguity (e.g. launcher restart) must not tear down
        // a still-authenticated PSS peer. The server independently validates logout.
        if((DWORD)(now-endpoint_missing_since)>=5000U) {
            BOOL invalidated=FALSE;
            EnterCriticalSection(&guard);
            if(target_ready) {target_ready=FALSE;needs_registration=TRUE;target.serial++;invalidated=TRUE;}
            LeaveCriticalSection(&guard);
            if(invalidated)SetEvent(wake_event);
        }
        return;
    }
    endpoint_missing_since=0;
    endpoint_warning=FALSE;
    BOOL needed;EnterCriticalSection(&guard);
    BOOL changed=!target_ready||target.remote!=next.remote||target.local_port!=next.local_port||target.game_port!=next.game_port;
    needed=active&&(changed||needs_registration)&&(changed||next_registration==0||(LONG)(now-next_registration)>=0);
    LeaveCriticalSection(&guard);
    if(!needed)return;
    if(BCryptGenRandom(0,next.token,sizeof(next.token),BCRYPT_USE_SYSTEM_PREFERRED_RNG)!=0||!register_on_game_connection(next.token))return;
    EnterCriticalSection(&guard);
    next.serial=target.serial+1;target=next;target_ready=TRUE;needs_registration=FALSE;next_registration=0;
    LeaveCriticalSection(&guard);SetEvent(wake_event);
}
void PssBridgeDetach(void) {
    if(!initialized)return;
    EnterCriticalSection(&guard);active=FALSE;target_ready=FALSE;target.serial++;LeaveCriticalSection(&guard);SetEvent(wake_event);
}
