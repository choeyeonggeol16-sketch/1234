#define _WIN32_WINNT 0x0601
#include <winsock2.h>
#include <windows.h>
#include <iphlpapi.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "PssBridge.h"
static volatile BYTE reservation[0x1000000];
static SOCKET game_socket=INVALID_SOCKET;
static volatile LONG posted;
static int constructors,destructors,messages,errors;
DWORD WINAPI PssBridgeTestTcpTable(PVOID table,PDWORD size,BOOL sorted,ULONG family,TCP_TABLE_CLASS kind,ULONG reserved) {
    (void)sorted;(void)family;(void)kind;(void)reserved;
    DWORD needed=sizeof(MIB_TCPTABLE_OWNER_PID);
    if(*size<needed){*size=needed;return ERROR_INSUFFICIENT_BUFFER;}
    struct sockaddr_in local,remote;int length=sizeof(local);
    if(getsockname(game_socket,(struct sockaddr*)&local,&length)!=0)return ERROR_INVALID_HANDLE;
    length=sizeof(remote);if(getpeername(game_socket,(struct sockaddr*)&remote,&length)!=0)return ERROR_INVALID_HANDLE;
    MIB_TCPTABLE_OWNER_PID* output=(MIB_TCPTABLE_OWNER_PID*)table;memset(output,0,needed);output->dwNumEntries=1;
    output->table[0].dwState=MIB_TCP_STATE_ESTAB;output->table[0].dwOwningPid=GetCurrentProcessId();
    output->table[0].dwLocalAddr=local.sin_addr.s_addr;output->table[0].dwLocalPort=local.sin_port;
    output->table[0].dwRemoteAddr=remote.sin_addr.s_addr;output->table[0].dwRemotePort=remote.sin_port;return NO_ERROR;
}
void PssBridgeLog(const wchar_t* text) {wprintf(L"%ls\n",text);fflush(stdout);}
BOOL WINAPI PssBridgeTestPostMessage(HWND hwnd,UINT message,WPARAM command,LPARAM data) {
    if(hwnd!=(HWND)1||message!=WM_APP+0x5A1||command!=3||data!=0){errors++;return FALSE;}
    InterlockedIncrement(&posted);return TRUE;
}
static void __attribute__((thiscall)) ctor(void* object) {*(DWORD*)object=0x50535331;constructors++;}
static void __attribute__((thiscall)) dtor(void* object) {if(*(DWORD*)object!=0x50535331)errors++;*(DWORD*)object=0;destructors++;}
static void __cdecl native_send(int id,void* object) {
    if(*(DWORD*)object!=0x50535331){errors++;return;}
    BYTE frame[]={7,0,0x51,(BYTE)id,(BYTE)(id>>8),0,0};int sent=0;
    while(sent<(int)sizeof(frame)){int count=send(game_socket,(char*)frame+sent,sizeof(frame)-sent,0);if(count<=0){errors++;return;}sent+=count;}
    messages++;
}
static void jump(DWORD address,void* function) {
    BYTE code[5]={0xE9};*(int32_t*)(code+1)=(int32_t)((uintptr_t)function-(address+5));memcpy((void*)(uintptr_t)address,code,5);
}
int main(void) {
    reservation[sizeof(reservation)-1]=1;
    DWORD old;
    if(!VirtualProtect((void*)0x620000,0x30000,PAGE_EXECUTE_READWRITE,&old)||!VirtualProtect((void*)0xB60000,0x10000,PAGE_EXECUTE_READWRITE,&old))return 2;
    const BYTE head[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x28,0x5D,0x01,0x01};
    const BYTE call_ctor[]={0xE8,0xA0,0xDB,0xFD,0xFF},call_send[]={0xE8,0xBB,0x4A,0x52,0},call_dtor[]={0xE8,0x29,0xE3,0xFD,0xFF};
    memcpy((void*)0x642B80,head,sizeof(head));memcpy((void*)0x642BAB,call_ctor,5);memcpy((void*)0x642BC0,call_send,5);memcpy((void*)0x642BD2,call_dtor,5);
    jump(0x620750,ctor);jump(0x620F00,dtor);jump(0xB67680,native_send);
    BYTE probe[10];SIZE_T got=0;BOOL readable=ReadProcessMemory(GetCurrentProcess(),(void*)0x642B80,probe,sizeof(probe),&got);
    printf("fixture memory direct=%02x%02x ReadProcessMemory=%d count=%lu error=%lu\n",*(BYTE*)0x642B80,*(BYTE*)0x642B81,readable,(unsigned long)got,(unsigned long)GetLastError());fflush(stdout);
    printf("fixture signatures %d %d %d %d\n",memcmp((void*)0x642B80,head,sizeof(head)),memcmp((void*)0x642BAB,call_ctor,5),memcmp((void*)0x642BC0,call_send,5),memcmp((void*)0x642BD2,call_dtor,5));fflush(stdout);
    WSADATA data;if(WSAStartup(MAKEWORD(2,2),&data)!=0)return 3;
    game_socket=socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);
    struct sockaddr_in peer;memset(&peer,0,sizeof(peer));peer.sin_family=AF_INET;peer.sin_addr.s_addr=htonl(INADDR_LOOPBACK);peer.sin_port=htons(5000);
    if(connect(game_socket,(struct sockaddr*)&peer,sizeof(peer))!=0){printf("game connect error=%d\n",WSAGetLastError());return 4;}
    if(!PssBridgeInitialize((HWND)1,WM_APP+0x5A1,GetModuleHandleW(0)))return 5;
    DWORD started=GetTickCount();
    while(!posted&&GetTickCount()-started<12000){PssBridgeTick();Sleep(25);}
    PssBridgeDetach();closesocket(game_socket);Sleep(350);WSACleanup();
    printf("RESULT nativeCtor=%d nativeDtor=%d frames=%d uiOpen=%ld errors=%d\n",constructors,destructors,messages,posted,errors);fflush(stdout);
    return constructors==1&&destructors==1&&messages==18&&posted==1&&errors==0?0:6;
}
