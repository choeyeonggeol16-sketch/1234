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
#include "PssNavigation.h"

typedef struct {void* object;void* control;} Shared;
typedef struct {void* head;unsigned count;} PathList;
typedef int (__attribute__((thiscall)) *Tick)(void*,float);
static BYTE manager[0x80],task[0x80],context[0x140],settings[0x50],group[0x80],actor[0x100];
static BYTE leaf[0x40],path_object[0x40],containers[6][0x40],dummy[0x40];
static PssPoint here={33000,32750},last_goal;
static DWORD now=10000;
static DWORD first_command,second_command;
static unsigned longest_command;
static unsigned commands,allocations,releases,destroys,original_calls;
static unsigned path_allocations,path_releases,path_queries;
static int raw_parity=1;
static int path_mode;
static BOOL blocked,simulate_steps,native_pending;
static unsigned intermediate_ticks;
DWORD PssRoamingTestNow(void){return now;}
ULONGLONG PssRoamingTestNow64(void){return now;}
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
    (void)unused;assert(self==actor);*out=here;
    PssPoint raw={here.x*2-32768+raw_parity,here.y};
    memcpy(actor+0x10,&raw,8);return out;
}
static void __attribute__((fastcall)) set_goal(void* self,void* unused,int x,int y) {
    (void)unused;((PssPoint*)self)->x=x;((PssPoint*)self)->y=y;
}
static void __attribute__((fastcall)) move(void* self,void* unused) {
    (void)unused;last_goal=*(PssPoint*)self;
    unsigned dx=(unsigned)abs(last_goal.x-here.x),dy=(unsigned)abs(last_goal.y-here.y);
    unsigned length=dx>dy?dx:dy;assert(length<=3);
    if(length>longest_command)longest_command=length;
    if(!commands)first_command=now;else if(commands==1)second_command=now;
    ++commands;
    if(!blocked){if(simulate_steps)native_pending=here.x!=last_goal.x||here.y!=last_goal.y;else here=last_goal;}
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
static void* __attribute__((fastcall)) path_head(void* self,void* unused,void* left,void* right) {
    (void)self;(void)unused;assert(!left&&!right);
    BYTE* p=HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,16);assert(p);++path_allocations;
    put32(p,0,(uintptr_t)p);put32(p,4,(uintptr_t)p);return p;
}
static void __attribute__((fastcall)) path_clear(void* self,void* unused) {
    (void)unused;PathList* list=self;void* p=(void*)(uintptr_t)get32(list->head,0);
    while(p!=list->head){void* next=(void*)(uintptr_t)get32(p,0);HeapFree(GetProcessHeap(),0,p);++path_releases;p=next;}
    HeapFree(GetProcessHeap(),0,list->head);++path_releases;
}
static void __cdecl find_path(const PssPoint* from,const PssPoint* to,PathList* list,int limit) {
    ++path_queries;
    assert(from->x==here.x*2-32768+raw_parity&&from->y==here.y&&limit==50);
    assert(!(to->x&1));
    if(path_mode==1)return;
    PssPoint destination,p=here,points[60];unsigned count=0;
    assert(PssNavToLogical(*to,&destination));
    assert(PssNavToInternal(p,&points[count++]));
    /* Include an odd raw X, as returned by native B9DF20's rounding. */
    points[0].x+=raw_parity;
    while((p.x!=destination.x||p.y!=destination.y)&&count<50){
        p.x+=destination.x>p.x?1:destination.x<p.x?-1:0;
        p.y+=destination.y>p.y?1:destination.y<p.y?-1:0;
        assert(PssNavToInternal(p,&points[count++]));
    }
    if(path_mode==2&&count>1)--count;
    if(path_mode==4&&count<=5)return; /* Dynamic blocker after cache seeding. */
    if(path_mode==5&&count>1)points[1].x+=200; /* Broken/nonadjacent route. */
    for(unsigned i=0;i<count;++i){
        BYTE* node=HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,16);assert(node);++path_allocations;
        void* previous=(void*)(uintptr_t)get32(list->head,4);
        put32(node,0,(uintptr_t)list->head);put32(node,4,(uintptr_t)previous);
        memcpy(node+8,&points[path_mode==3?count-1-i:i],8);
        put32(previous,0,(uintptr_t)node);put32(list->head,4,(uintptr_t)node);++list->count;
    }
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
    reserve(0xEC0000,0x10000);
    reserve(0x1180000,0x20000);reserve(0x1560000,0x10000);
    reserve(0x15B0000,0x10000);reserve(0x15D0000,0x10000);reserve(0x16E0000,0x10000);
    const BYTE factory[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x34,0xBC,0x04,0x01};
    const BYTE tick[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x68,0x36,0x02,0x01};
    const BYTE goal_call[]={0xE8,0xD6,0x22,0xFC,0xFF};
    const BYTE move_call[]={0xE8,0x5E,0x23,0xFC,0xFF};
    const BYTE head[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x70,0xAA,0x01,0x01};
    const BYTE clear[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0xA0,0xA9,0x01,0x01};
    const BYTE query[]={0xE8,0x11,0x83,0x84,0x00};
    prefixed(0x75E9D0,factory,create_point);prefixed(0x67F000,tick,original_tick);
    jump(0x64FDA0,set_goal);jump(0x64FDE0,set_goal);jump(0x64FE70,move);jump(0xB9DF20,get_position);
    memcpy((void*)0x68DB05,goal_call,5);memcpy((void*)0x68DB0D,move_call,5);
    prefixed(0x65B750,head,path_head);prefixed(0x65B420,clear,path_clear);
    jump(0xECEC10,find_path);memcpy((void*)0x6868FA,query,5);
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
    for(unsigned n=0;n<milliseconds;n+=100){
        unsigned prior=path_queries,before=commands;BOOL intermediate=FALSE;
        if(simulate_steps&&native_pending&&!blocked){
            here.x+=last_goal.x>here.x?1:last_goal.x<here.x?-1:0;
            here.y+=last_goal.y>here.y?1:last_goal.y<here.y?-1:0;
            native_pending=here.x!=last_goal.x||here.y!=last_goal.y;
            intermediate=native_pending;if(intermediate)++intermediate_ticks;
        }
        now+=100;PssRoamingObserve();tick();assert(path_queries-prior<=2);
        if(intermediate)assert(commands==before);
    }
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
    assert(commands>=1&&allocations==1&&path_allocations==path_releases);
    unsigned before=commands;advance(3000);
    assert(commands>before&&allocations==1);
    assert(longest_command==3&&second_command-first_command<300);
    puts("PASS multi-tile native movement goals and no mandatory 300ms gap after observed arrival");
    puts("PASS real x86 shadow-vtable dispatch, native point/goal/move ABI and point reuse");

    PssRoamingReset(FALSE);last_goal=here;native_pending=FALSE;simulate_steps=TRUE;
    before=commands;advance(3000);
    assert(commands>before&&intermediate_ticks>0);
    simulate_steps=FALSE;native_pending=FALSE;
    PssRoamingReset(FALSE);advance(1000);
    puts("PASS native multi-tile travel keeps its destination through intermediate tile updates");

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
    assert(commands-before<=5);
    puts("PASS accepted but frozen movement is not spammed; stuck steps are retried at a bounded rate");

    before=commands;*((BYTE*)0x16E385F)=1;advance(1000);assert(commands==before);
    *((BYTE*)0x16E385F)=0;*((BYTE*)0x15D8E6E)=1;advance(1000);assert(commands==before);
    *((BYTE*)0x15D8E6E)=0;advance(1000);assert(commands>before);
    puts("PASS movement-block and death flags prevent idle movement without stopping native PSS");

    before=commands;manager[0x58]=0;now+=100;PssRoamingObserve();
    assert(commands==before+1&&last_goal.x==here.x&&last_goal.y==here.y);
    assert(allocations==releases&&allocations==destroys);
    now+=100;PssRoamingObserve();assert(commands==before+1);
    puts("PASS Stop cancels the owned goal once and releases native shared ownership");

    manager[0x58]=1;advance(1100);before=commands;
    put32((void*)0x156A34C,0,5);here=(PssPoint){35000,40000};now+=100;PssRoamingObserve();
    assert(commands==before);assert(tick()==1&&commands==before);advance(800);assert(commands>before);
    before=commands;put32((void*)0x15D8E44,0,0);now+=100;PssRoamingObserve();
    assert(commands==before&&allocations==releases);
    puts("PASS map changes and world exit discard stale destinations without cross-map movement");

    put32((void*)0x15D8E44,0,3);now+=100;PssRoamingObserve();advance(1000);
    before=commands;tag(containers[4],"DifferentTree");now+=100;PssRoamingObserve();
    assert(tick()==2&&commands==before);
    tag(containers[4],"MoveNextSelector");now+=100;PssRoamingObserve();
    PssRoamingReset(FALSE);path_mode=1;before=commands;advance(1500);
    assert(commands==before&&path_allocations==path_releases);
    PssRoamingReset(FALSE);path_mode=2;advance(1500);
    assert(commands==before&&path_allocations==path_releases);
    PssRoamingReset(FALSE);path_mode=4;advance(1500);
    assert(commands==before&&path_allocations==path_releases);
    PssRoamingReset(FALSE);path_mode=5;advance(1500);
    assert(commands==before&&path_allocations==path_releases);
    PssRoamingReset(FALSE);path_mode=3;advance(1500);
    assert(commands>before&&path_allocations==path_releases);
    puts("PASS raw/logical coordinate conversion including odd actor X, reversed routes, empty/partial/corrupt paths, dynamic blockers and list cleanup");
    PssRoamingDetach();assert(get32(leaf,0)==0x118CA64&&allocations==releases);
    printf("PASS tree mismatch and detach; allocations=%u destroys=%u releases=%u original_calls=%u\n",
           allocations,destroys,releases,original_calls);
    printf("PASS native path allocations=%u releases=%u queries=%u (max 2 per tick)\n",path_allocations,path_releases,path_queries);
    return 0;
}
