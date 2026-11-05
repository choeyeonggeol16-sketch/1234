/* Controlled 32-bit PE fixture, not the protected game executable. Native
 * entry points are test doubles with the real x86 calling conventions. */
#define UNICODE
#define _UNICODE
#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "PssRoaming.h"
#include "PssRoamCore.h"

typedef struct {void* object;void* control;} Shared;
typedef int (__attribute__((thiscall)) *Tick)(void*,float);
static BYTE manager[0x80],task[0x80],context[0x140],settings[0x50],group[0x80],actor[0x100];
static BYTE leaf[0x40],path_object[0x40],containers[6][0x40],dummy[0x40];
static PssPoint here={65590,32750},last_goal;
static DWORD now=10000;
static unsigned commands,allocations,releases,destroys,original_calls;
static BOOL blocked;
DWORD PssRoamingTestNow(void){return now;}
/* Wine under QEMU cannot read the guest's private image pages using the
 * host process-memory API. Only this fixture substitutes a checked guest
 * read; the shipping DLL always uses ReadProcessMemory. */
BOOL PssRoamingTestRead(const void* p,void* out,SIZE_T length) {
    MEMORY_BASIC_INFORMATION m;
    if(!p||!VirtualQuery(p,&m,sizeof(m))||m.State!=MEM_COMMIT||
       (m.Protect&(PAGE_GUARD|PAGE_NOACCESS))||
       (uintptr_t)p+length>(uintptr_t)m.BaseAddress+m.RegionSize)return FALSE;
    memcpy(out,p,length);return TRUE;
}
void PssBridgeLog(const wchar_t* line){wprintf(L"TRACE %ls\n",line);}
static void put32(void* base,SIZE_T offset,uintptr_t value) {
    DWORD n=(DWORD)value;memcpy((BYTE*)base+offset,&n,4);
}
static DWORD get32(void* base,SIZE_T offset){DWORD n;memcpy(&n,(BYTE*)base+offset,4);return n;}
static void reserve(uintptr_t base,SIZE_T size) {
    void* p=VirtualAlloc((void*)base,size,MEM_RESERVE|MEM_COMMIT,PAGE_EXECUTE_READWRITE);
    if(p!=(void*)base) {
        MEMORY_BASIC_INFORMATION m;DWORD old;
        assert(VirtualQuery((void*)base,&m,sizeof(m))&&m.AllocationBase==GetModuleHandleW(0));
        assert(VirtualProtect((void*)base,size,PAGE_EXECUTE_READWRITE,&old));
        memset((void*)base,0,size);
    }
}
static void jump(uintptr_t address,const void* function) {
    BYTE* p=(BYTE*)address;p[0]=0xE9;
    int32_t relative=(int32_t)((uintptr_t)function-address-5);memcpy(p+1,&relative,4);
}
static void prefixed(uintptr_t address,const BYTE* prefix,const void* function) {
    memcpy((void*)address,prefix,10);
    const BYTE unwind[]={0x83,0xC4,8,0x5D};
    memcpy((void*)(address+10),unwind,sizeof(unwind));jump(address+14,function);
}
static PssPoint* __attribute__((fastcall)) get_position(void* self,void* unused,PssPoint* out) {
    (void)unused;assert(self==actor);*out=here;return out;
}
static void __attribute__((fastcall)) set_goal(void* self,void* unused,int x,int y) {
    (void)unused;((PssPoint*)self)->x=x;((PssPoint*)self)->y=y;
}
static void __attribute__((fastcall)) move(void* self,void* unused) {
    (void)unused;last_goal=*(PssPoint*)self;++commands;
    if(!blocked)here=last_goal;
}
static void __attribute__((fastcall)) destroy(void* self,void* unused) {
    (void)unused;assert(get32(self,4)==0);++destroys;
}
static void __attribute__((fastcall)) release(void* self,void* unused) {
    (void)unused;assert(get32(self,8)==0);++releases;HeapFree(GetProcessHeap(),0,self);
}
static Shared* __cdecl create_point(Shared* out,const PssPoint* position) {
    BYTE* p=HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,20);assert(p);
    put32(p,0,0x1191238);put32(p,4,1);put32(p,8,1);
    memcpy(p+12,position,8);out->object=p+12;out->control=p;++allocations;return out;
}
static int __attribute__((fastcall)) original_tick(void* self,void* unused,float elapsed) {
    (void)unused;assert(self==leaf);assert(elapsed>0.09f&&elapsed<0.11f);++original_calls;return 2;
}
static void tag(BYTE* node,const char* value) {
    SIZE_T n=strlen(value);
    memset(node+0xC,0,24);
    if(n>=16)put32(node,0xC,(uintptr_t)value);else memcpy(node+0xC,value,n+1);
    put32(node,0x1C,n);put32(node,0x20,n>=16?n:15);
}
static void children(BYTE* node,unsigned count,void** values) {
    BYTE* head=HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,16);assert(head);
    BYTE* links=HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,count*16);assert(links);
    put32(head,0,(uintptr_t)links);put32(head,4,(uintptr_t)(links+(count-1)*16));
    for(unsigned i=0;i<count;++i) {
        BYTE* link=links+i*16;
        put32(link,0,(uintptr_t)(i+1==count?head:link+16));
        put32(link,4,(uintptr_t)(i?link-16:head));
        put32(link,8,(uintptr_t)values[i]);
    }
    put32(node,0x24,(uintptr_t)head);put32(node,0x28,count);
}
static void world(void) {
    reserve(0x640000,0x180000);reserve(0xB90000,0x10000);
    reserve(0x1180000,0x20000);reserve(0x1560000,0x10000);
    reserve(0x15B0000,0x10000);reserve(0x15D0000,0x10000);reserve(0x16E0000,0x10000);
    const BYTE factory[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x34,0xBC,0x04,0x01};
    const BYTE tick[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x68,0x36,0x02,0x01};
    const BYTE goal_call[]={0xE8,0xD6,0x22,0xFC,0xFF};
    const BYTE move_call[]={0xE8,0x5E,0x23,0xFC,0xFF};
    prefixed(0x75E9D0,factory,create_point);prefixed(0x67F000,tick,original_tick);
    jump(0x64FDA0,set_goal);jump(0x64FDE0,set_goal);jump(0x64FE70,move);jump(0xB9DF20,get_position);
    memcpy((void*)0x68DB05,goal_call,5);memcpy((void*)0x68DB0D,move_call,5);
    put32((void*)0x1191238,0,(uintptr_t)destroy);put32((void*)0x1191238,4,(uintptr_t)release);
    put32((void*)0x118CA60,0,0x1372A10);put32((void*)0x118CA64,0,0x67EEB0);
    put32((void*)0x118CA64,4,0x67F000);put32((void*)0x118CA64,8,0x653DB0);
    put32((void*)0x15D8E44,0,3);put32((void*)0x15B3D14,0,(uintptr_t)manager);
    put32((void*)0x15B3E68,0,(uintptr_t)context);put32((void*)0x16E3864,0,(uintptr_t)actor);
    put32((void*)0x156A34C,0,4);put32(manager,0x24,(uintptr_t)task);manager[0x58]=1;
    put32(task,0,0x1184C78);put32(task,4,(uintptr_t)settings);put32(settings,0x20,(uintptr_t)group);
    put32(context,0xA8,0xFFFFFFFF);put32(context,0xBC,0xFFFFFFFF);
    for(unsigned i=0;i<6;++i)put32(containers[i],0,0x11B4084);
    put32(containers[0],0,0x11B4094);put32(containers[5],0,0x11B4094);
    void* a[]={containers[1]};children(containers[0],1,a);
    void* b[]={containers[2]};children(containers[1],1,b);
    void* c[]={dummy,containers[3]};children(containers[2],2,c);
    void* d[]={dummy,dummy,containers[4]};children(containers[3],3,d);
    void* e[]={containers[5],dummy};children(containers[4],2,e);
    void* f[]={leaf,dummy};children(containers[5],2,f);
    tag(containers[4],"MoveNextSelector");tag(containers[5],"PlayPathSequence");
    tag(leaf,"PlayPath");put32(leaf,0,0x118CA64);put32(task,0x38,(uintptr_t)containers[0]);
    FlushInstructionCache(GetCurrentProcess(),0,0);
}
static int tick(void) {
    Tick fn=(Tick)(uintptr_t)get32((void*)(uintptr_t)get32(leaf,0),4);
    return fn(leaf,0.1f);
}
static void advance(unsigned milliseconds) {
    for(unsigned n=0;n<milliseconds;n+=100){now+=100;PssRoamingObserve();tick();}
}
static DWORD WINAPI other_thread(void* unused){(void)unused;tick();return 0;}
int main(int argc,char** argv) {
    (void)argv;world();
    HWND window=CreateWindowExW(0,L"STATIC",L"PSS fixture",0,0,0,1,1,HWND_MESSAGE,0,GetModuleHandleW(0),0);
    assert(window);
    if(argc>1)*((BYTE*)0x75E9D0)=0xC3;
    PssRoamingInitialize(window,GetModuleHandleW(0));PssRoamingObserve();
    if(argc>1) {
        assert(get32(leaf,0)==0x118CA64&&commands==0);
        puts("PASS unsupported native signature leaves the PSS node unchanged");return 0;
    }
    assert(get32(leaf,0)!=0x118CA64);
    assert(get32((void*)(uintptr_t)get32(leaf,0),-4)==0x1372A10);
    assert(tick()==1&&commands==0);advance(800);
    assert(commands==1&&allocations==1);
    unsigned before=commands;advance(3000);
    assert(commands>before&&allocations==1);
    puts("PASS real x86 shadow-vtable dispatch, native point/goal/move ABI and point reuse");

    before=commands;put32(context,0xA8,123);now+=100;PssRoamingObserve();
    assert(tick()==2&&commands==before&&releases==allocations);
    put32(context,0xA8,0xFFFFFFFF);now+=100;PssRoamingObserve();advance(1000);
    assert(commands>before);
    puts("PASS monster preemption and resuming idle walking after combat");

    before=commands;put32(context,0xBC,456);assert(tick()==2&&commands==before);
    put32(context,0xBC,0xFFFFFFFF);group[0xE]=1;assert(tick()==2&&commands==before);
    group[0xE]=0;put32(path_object,0x14,1);put32(context,0xCC,(uintptr_t)path_object);
    assert(tick()==2&&commands==before);put32(context,0xCC,0);
    put32(context,0xF4,0x100);put32(context,0xF8,0x108);assert(tick()==2&&commands==before);
    put32(context,0xF4,0);put32(context,0xF8,0);
    puts("PASS loot, fixed hunting, recorded path and pending escape/return actions delegate unchanged");

    advance(1000);before=commands;
    HANDLE thread=CreateThread(0,0,other_thread,0,0,0);assert(thread);
    WaitForSingleObject(thread,INFINITE);CloseHandle(thread);assert(commands==before);
    puts("PASS background-thread callbacks never issue roaming movement");

    blocked=TRUE;advance(9000);blocked=FALSE;
    assert(commands>before);
    puts("PASS blocked native movement retries different goals at a bounded rate");

    before=commands;manager[0x58]=0;now+=100;PssRoamingObserve();
    assert(commands==before+1&&last_goal.x==here.x&&last_goal.y==here.y);
    assert(allocations==releases&&allocations==destroys);
    now+=100;PssRoamingObserve();assert(commands==before+1);
    puts("PASS Stop cancels the owned goal once and releases native shared ownership");

    manager[0x58]=1;advance(1100);before=commands;
    put32((void*)0x156A34C,0,5);here=(PssPoint){90000,40000};now+=100;PssRoamingObserve();
    assert(commands==before);assert(tick()==1&&commands==before);advance(800);assert(commands>before);
    before=commands;put32((void*)0x15D8E44,0,0);now+=100;PssRoamingObserve();
    assert(commands==before&&allocations==releases);
    puts("PASS map changes and world exit discard stale destinations without cross-map movement");

    put32((void*)0x15D8E44,0,3);now+=100;PssRoamingObserve();advance(1000);
    before=commands;tag(containers[4],"DifferentTree");now+=100;PssRoamingObserve();
    assert(tick()==2&&commands==before);
    tag(containers[4],"MoveNextSelector");now+=100;PssRoamingObserve();
    PssRoamingDetach();assert(get32(leaf,0)==0x118CA64&&allocations==releases);
    printf("PASS tree mismatch and detach; allocations=%u destroys=%u releases=%u original_calls=%u\n",
           allocations,destroys,releases,original_calls);
    return 0;
}
