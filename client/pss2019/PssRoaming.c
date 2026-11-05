/* Exact 19.02.15.1002 adapter. Only one Battle idle node receives a shadow
 * vtable; no movement packets, fake targets, or shared executable-code hooks.
 * Movement is issued on the native PSS tick, using its existing point mover.
 */
#include "PssRoaming.h"
#include "PssRoamCore.h"
#include "PssBridge.h"
#include <stdint.h>
#include <string.h>
#ifdef PSS_ROAM_TEST
extern DWORD PssRoamingTestNow(void);
extern BOOL PssRoamingTestRead(const void*,void*,SIZE_T);
#define GetTickCount PssRoamingTestNow
#endif

enum { NODE_VTABLE=0x118CA64, NODE_TICK=0x67F000, BATTLE_VTABLE=0x1184C78 };
typedef struct { void* object; void* control; } NativeShared;
typedef int (__attribute__((thiscall)) *NativeTick)(void*,float);
typedef PssPoint* (__attribute__((thiscall)) *NativePosition)(void*,PssPoint*);
typedef NativeShared* (__cdecl *NativeFactory)(NativeShared*,const PssPoint*);
typedef void (__attribute__((thiscall)) *NativeGoal)(void*,int,int);
typedef void (__attribute__((thiscall)) *NativeVoid)(void*);
typedef struct { void* head; unsigned count; } NativePathList;
typedef void* (__attribute__((thiscall)) *NativePathHead)(void*,void*,void*);
typedef void (__cdecl *NativeFindPath)(const PssPoint*,const PssPoint*,NativePathList*,int);
typedef struct {
    void *manager,*task,*context,*actor,*config,*path;
    int map,monster,item;
    uintptr_t action_begin,action_end;
    BYTE fixed;
} Frame;

static HWND game_window;
static DWORD ui_thread;
static BOOL enabled, ready, attached, warned;
static volatile LONG thread_warned;
static void *idle_node,*node_task,*owner_task,*owner_actor,*owner_manager;
static int owner_map;
static uintptr_t shadow[4]; /* MSVC RTTI locator at [-1], then exactly 3 slots. */
static NativeShared point;
static PssRoamCore brain;
static PssRoamConfig config={8,300,500,1000};
static DWORD last_observe,last_log;
static unsigned depth;

static BOOL read_at(const void* address,void* out,SIZE_T length) {
#ifdef PSS_ROAM_TEST
    return PssRoamingTestRead(address,out,length);
#else
    SIZE_T got=0;
    return address && ReadProcessMemory(GetCurrentProcess(),address,out,length,&got)&&got==length;
#endif
}
static BOOL ptr(const void* base,SIZE_T offset,void** out) {
    return read_at((const BYTE*)base+offset,out,4)&&*out;
}
static BOOL executable(uintptr_t address) {
    MEMORY_BASIC_INFORMATION m;
    if(address<0x401000||address>=0x180D000)return FALSE;
    if(!VirtualQuery((void*)address,&m,sizeof(m))||m.State!=MEM_COMMIT||
       (m.Protect&(PAGE_GUARD|PAGE_NOACCESS)))return FALSE;
    return (m.Protect&0xF0)!=0;
}
static BOOL bytes(uintptr_t address,const BYTE* expected,SIZE_T count) {
    BYTE actual[32];
    return count<=sizeof(actual)&&read_at((void*)address,actual,count)&&!memcmp(actual,expected,count);
}
static BOOL native_ready(void) {
    const BYTE factory[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x34,0xBC,0x04,0x01};
    const BYTE tick[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x68,0x36,0x02,0x01};
    const BYTE set_goal[]={0xE8,0xD6,0x22,0xFC,0xFF}; /* 68DB05 -> 64FDE0 */
    const BYTE move[]={0xE8,0x5E,0x23,0xFC,0xFF}; /* 68DB0D -> 64FE70 */
    const BYTE head[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x70,0xAA,0x01,0x01};
    const BYTE clear[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0xA0,0xA9,0x01,0x01};
    const BYTE query[]={0xE8,0x11,0x83,0x84,0x00}; /* 6868FA -> ECEC10 */
    return bytes(0x75E9D0,factory,sizeof(factory))&&bytes(NODE_TICK,tick,sizeof(tick))&&
           bytes(0x68DB05,set_goal,sizeof(set_goal))&&bytes(0x68DB0D,move,sizeof(move))&&
           executable(0x64FDA0)&&executable(0x64FDE0)&&executable(0x64FE70)&&
           executable(0xB9DF20)&&bytes(0x65B750,head,sizeof(head))&&
           bytes(0x65B420,clear,sizeof(clear))&&bytes(0x6868FA,query,sizeof(query))&&
           executable(0xECEC10);
}
static BOOL snapshot(Frame* f) {
    DWORD phase=0,table=0;BYTE running=0;
    memset(f,0,sizeof(*f));
    if(!read_at((void*)0x15D8E44,&phase,4)||phase!=3||
       !ptr((void*)0x15B3D14,0,&f->manager)||
       !read_at((BYTE*)f->manager+0x58,&running,1)||!running||
       !ptr(f->manager,0x24,&f->task)||!read_at(f->task,&table,4)||table!=BATTLE_VTABLE||
       !ptr((void*)0x15B3E68,0,&f->context)||!ptr((void*)0x16E3864,0,&f->actor)||
       !read_at((void*)0x156A34C,&f->map,4)||!ptr(f->task,4,&f->config)||
       !read_at((BYTE*)f->context+0xA8,&f->monster,4)||
       !read_at((BYTE*)f->context+0xBC,&f->item,4)||
       !read_at((BYTE*)f->context+0xF4,&f->action_begin,4)||
       !read_at((BYTE*)f->context+0xF8,&f->action_end,4)||
       !read_at((BYTE*)f->context+0xCC,&f->path,4))return FALSE;
    void* group=0;
    if(!read_at((BYTE*)f->config+0x20,&group,4))return FALSE;
    if(!group) {
        void* defaults=0;
        if(!ptr((void*)0x15B4BB0,0,&defaults)||!ptr(defaults,0x20,&group))return FALSE;
    }
    return read_at((BYTE*)group+0xE,&f->fixed,1);
}
static BOOL tag_is(void* node,const char* expected) {
    BYTE string[24];
    if(!read_at((BYTE*)node+0xC,string,sizeof(string)))return FALSE;
    uint32_t length=0,capacity=0;
    memcpy(&length,string+16,4);memcpy(&capacity,string+20,4);
    SIZE_T want=strlen(expected);
    if(length!=want||length>48)return FALSE;
    char text[49];const void* source=string;
    if(capacity>=16)memcpy(&source,string,4);
    return read_at(source,text,length)&&!memcmp(text,expected,length);
}
static void* child(void* node,unsigned index) {
    DWORD table=0,count=0;void *head=0,*link=0,*result=0;
    if(!read_at(node,&table,4)||(table!=0x11B4084&&table!=0x11B4094)||
       !read_at((BYTE*)node+0x28,&count,4)||count>64||index>=count||
       !ptr(node,0x24,&head)||!ptr(head,0,&link))return 0;
    for(unsigned n=0;n<index;++n) {
        if(link==head||!ptr(link,0,&link))return 0;
    }
    if(link==head||!ptr(link,8,&result))return 0;
    return result;
}
static void* find_idle(const Frame* f) {
    /* Verified exported Battle tree path: 0/0/1/2/0/0. The two tags below
     * prevent silently treating a different tree/escape node as idle. */
    static const unsigned path[]={0,0,1,2};
    void* node=0;DWORD table=0;
    if(!ptr(f->task,0x38,&node))return 0;
    for(unsigned n=0;n<4&&node;++n)node=child(node,path[n]);
    if(!node||!tag_is(node,"MoveNextSelector"))return 0;
    node=child(node,0);
    if(!node||!tag_is(node,"PlayPathSequence"))return 0;
    node=child(node,0);
    if(!node||!tag_is(node,"PlayPath")||!read_at(node,&table,4)||
       (table!=NODE_VTABLE&&table!=(uintptr_t)&shadow[1]))return 0;
    return node;
}
static BOOL position(void* actor,PssPoint* p) {
    ((NativePosition)0xB9DF20)(actor,p);
    return p->x>=-1048576&&p->x<=1048576&&p->y>=-1048576&&p->y<=1048576;
}
static int query_path(void* ignored,PssPoint from,PssPoint to,PssRoamPath* out) {
    (void)ignored;
    NativePathList list={0};out->count=0;
    list.head=((NativePathHead)0x65B750)(&list,0,0);
    if(!list.head)return -1;
    ((NativeFindPath)0xECEC10)(&from,&to,&list,50);
    int valid=0;
    if(list.count&&list.count<PSS_ROAM_PATH_MAX) {
        void* link=0;
        if(ptr(list.head,0,&link)) {
            unsigned n=0;
            while(n<list.count&&link!=list.head) {
                PssPoint p;
                if(!read_at((BYTE*)link+8,&p,sizeof(p))||
                   p.x < -1048576||p.x>1048576||p.y < -1048576||p.y>1048576)break;
                out->points[n++]=p;
                if(!ptr(link,0,&link))break;
            }
            if(n==list.count&&link==list.head) {
                if(out->points[0].x==to.x&&out->points[0].y==to.y) {
                    for(unsigned i=0;i<n/2;++i) {
                        PssPoint p=out->points[i];out->points[i]=out->points[n-1-i];out->points[n-1-i]=p;
                    }
                }
                PssPoint previous=from;
                valid=out->points[n-1].x==to.x&&out->points[n-1].y==to.y;
                for(unsigned i=0;i<n&&valid;++i) {
                    int x=out->points[i].x-previous.x,y=out->points[i].y-previous.y;
                    if(x < -4||x>4||y < -4||y>4)valid=0;
                    previous=out->points[i];
                }
                if(valid)out->count=n;
            }
        }
    }
    /* Matches the native SearchMonster caller's std::list<Point> lifecycle. */
    ((NativeVoid)0x65B420)(&list);
    return valid;
}
static void release_point(void) {
    if(!point.control){point.object=0;return;}
    uintptr_t table=0,functions[2];
    if(!read_at(point.control,&table,4)||table!=0x1191238||
       !read_at((void*)table,functions,sizeof(functions))||
       !executable(functions[0])||!executable(functions[1])) {
        /* Keep ownership on an unexpected layout; never overwrite/leak repeatedly. */
        enabled=FALSE;
        PssBridgeLog(L"PSS roaming disabled: native point ownership layout differs");return;
    }
    void* control=point.control;point.object=0;point.control=0;
    if(InterlockedDecrement((LONG*)((BYTE*)control+4))==0) {
        ((NativeVoid)functions[0])(control);
        if(InterlockedDecrement((LONG*)((BYTE*)control+8))==0)
            ((NativeVoid)functions[1])(control);
    }
}
static BOOL same_owner(const Frame* f) {
    return owner_actor==f->actor&&owner_task==f->task&&owner_manager==f->manager&&owner_map==f->map;
}
static void pause_walking(void) {
    PssRoamCorePause(&brain,GetTickCount());release_point();
}
void PssRoamingReset(BOOL cancel_owned_move) {
    if(GetCurrentThreadId()!=ui_thread)return;
    if(cancel_owned_move&&ready&&brain.moving&&point.object) {
        DWORD phase=0;void* actor=0;int map=0;PssPoint here;
        if(read_at((void*)0x15D8E44,&phase,4)&&phase==3&&
           ptr((void*)0x16E3864,0,&actor)&&actor==owner_actor&&
           read_at((void*)0x156A34C,&map,4)&&map==owner_map&&position(actor,&here)) {
            ((NativeGoal)0x64FDE0)(point.object,here.x,here.y);
            ((NativeVoid)0x64FE70)(point.object);
        }
    }
    PssRoamCoreReset(&brain);release_point();
    owner_task=owner_actor=owner_manager=0;owner_map=0;
    last_observe=0;
}
static int __attribute__((fastcall)) idle_tick(void* self,void* unused,float elapsed) {
    (void)unused;
    NativeTick original=(NativeTick)NODE_TICK;
    if(GetCurrentThreadId()!=ui_thread) {
        if(InterlockedCompareExchange(&thread_warned,1,0)==0) {
            PssBridgeLog(L"PSS idle walking skipped: native tick thread differs from game UI thread");
        }
        return original(self,elapsed);
    }
    if(!enabled||!ready||depth||self!=idle_node)
        return original(self,elapsed);
    Frame f;
    if(!snapshot(&f)||f.task!=node_task) {
        PssRoamingReset(FALSE);return original(self,elapsed);
    }
    /* These conditions retain native combat, pickup, recorded paths, fixed
     * hunting, and pending escape/return actions. No teleport leaf is changed. */
    DWORD path_count=0;
    if(f.fixed||f.monster>=0||f.item>=0||f.action_begin!=f.action_end||
       (f.path&&(!read_at((BYTE*)f.path+0x14,&path_count,4)||path_count))) {
        pause_walking();return original(self,elapsed);
    }
    ++depth;
    if(!same_owner(&f)) {
        PssRoamingReset(FALSE);
        owner_task=f.task;owner_actor=f.actor;owner_manager=f.manager;owner_map=f.map;
    }
    PssPoint here,goal;
    if(!position(f.actor,&here)) {--depth;return original(self,elapsed);}
    DWORD now=GetTickCount();
    if(PssRoamCorePlan(&brain,&config,now,here,(uint32_t)(uintptr_t)f.actor^(uint32_t)f.map,&goal,query_path,0)) {
        if(!point.object) {
            ((NativeFactory)0x75E9D0)(&point,&goal);
            if(!point.control||(uintptr_t)point.object!=(uintptr_t)point.control+0xC) {
                enabled=FALSE;
                PssBridgeLog(L"PSS roaming disabled: native movement point could not be created");
                PssRoamingReset(FALSE);--depth;return original(self,elapsed);
            }
        }
        ((NativeGoal)0x64FDE0)(point.object,goal.x,goal.y);
        ((NativeVoid)0x64FE70)(point.object);
        if(now-last_log>=10000U) {
            wchar_t message[240];
            wsprintfW(message,L"PSS idle walking V2 map=%d position=%d,%d waypoint=%d,%d destination=%d,%d blocked=%u rejected=%u",
                      f.map,here.x,here.y,goal.x,goal.y,brain.destination.x,brain.destination.y,
                      brain.blocked_count,brain.rejected_paths);
            PssBridgeLog(message);last_log=now;
        }
    } else if(brain.initialized&&!brain.moving&&brain.path_queries&&now-last_log>=10000U) {
        wchar_t message[200];
        wsprintfW(message,L"PSS idle walking V2 waiting for reachable path map=%d position=%d,%d queries=%u rejected=%u",
                  f.map,here.x,here.y,brain.path_queries,brain.rejected_paths);
        PssBridgeLog(message);last_log=now;
    }
    --depth;
    /* RUNNING blocks only the idle Teleport fallback. Native selectors start
     * from their first child on every tick, so new targets preempt this leaf. */
    return 1;
}
static unsigned setting(const wchar_t* file,const wchar_t* name,unsigned value,unsigned lo,unsigned hi) {
    unsigned n=GetPrivateProfileIntW(L"PssRoaming",name,(INT)value,file);
    return n<lo?lo:n>hi?hi:n;
}
void PssRoamingInitialize(HWND game,HMODULE module) {
    game_window=game;ui_thread=GetWindowThreadProcessId(game,0);
    wchar_t file[MAX_PATH];
    DWORD length=GetModuleFileNameW(module,file,MAX_PATH);
    if(!length||length>=MAX_PATH)return;
    wchar_t* last=file;
    for(wchar_t* p=file;*p;++p)if(*p==L'\\')last=p+1;
    if((SIZE_T)(last-file)+12>=MAX_PATH)return;
    lstrcpyW(last,L"Pss2019.ini");
    enabled=GetPrivateProfileIntW(L"PssRoaming",L"Enabled",1,file)!=0;
    config.step_tiles=setting(file,L"StepTiles",8,1,16);
    config.command_ms=setting(file,L"CommandIntervalMs",300,200,1000);
    config.idle_ms=setting(file,L"IdleDelayMs",500,300,3000);
    config.stuck_ms=setting(file,L"StuckTimeoutMs",1000,600,5000);
    warned=FALSE;last_observe=0;
    if(!enabled)PssBridgeLog(L"PSS idle walking disabled by PssRoaming.Enabled=0");
}
void PssRoamingObserve(void) {
    if(!game_window||GetCurrentThreadId()!=ui_thread||!enabled||depth)return;
    DWORD now=GetTickCount();
    if(now-last_observe<100U)return;
    last_observe=now;
    if(!ready) {
        if(!native_ready()) {
            if(!warned){warned=TRUE;PssBridgeLog(L"PSS idle walking waiting: native code signatures not ready");}
            return;
        }
        if(!read_at((void*)(NODE_VTABLE-4),shadow,sizeof(shadow))||shadow[2]!=NODE_TICK)return;
        shadow[2]=(uintptr_t)idle_tick;ready=TRUE;
        PssBridgeLog(L"PSS idle walking V2 ready: native path query and corner waypoints");
    }
    Frame f;
    if(!snapshot(&f)) {
        /* Native stop sets 16E386C and resets Battle. Cancel only our own
         * outstanding movement; never move across a map/actor change. */
        void* manager=0;BYTE running=1;
        BOOL stopped=ptr((void*)0x15B3D14,0,&manager)&&manager==owner_manager&&
                     read_at((BYTE*)manager+0x58,&running,1)&&!running;
        PssRoamingReset(stopped);idle_node=0;node_task=0;return;
    }
    if(brain.initialized) {
        if(!same_owner(&f))PssRoamingReset(FALSE);
        else if(f.monster>=0||f.item>=0||f.action_begin!=f.action_end||f.fixed)pause_walking();
    }
    void* leaf=find_idle(&f);
    if(!leaf) {
        PssRoamingReset(FALSE);idle_node=0;node_task=0;
        if(!warned){warned=TRUE;PssBridgeLog(L"PSS idle walking unavailable: Battle idle tree layout differs");}
        return;
    }
    if(leaf!=idle_node)PssRoamingReset(FALSE);
    idle_node=leaf;node_task=f.task;
    if(!owner_task) {
        owner_task=f.task;owner_actor=f.actor;owner_manager=f.manager;owner_map=f.map;
    }
    LONG old=InterlockedCompareExchange((LONG*)leaf,(LONG)(uintptr_t)&shadow[1],NODE_VTABLE);
    if(old==NODE_VTABLE&&!attached) {
        attached=TRUE;warned=FALSE;
        PssBridgeLog(L"PSS idle walking attached; new targets retain native priority");
    }
}
void PssRoamingDetach(void) {
    if(GetCurrentThreadId()!=ui_thread)return;
    PssRoamingReset(TRUE);
    Frame f;
    if(snapshot(&f)) {
        void* leaf=find_idle(&f);
        if(leaf)InterlockedCompareExchange((LONG*)leaf,NODE_VTABLE,(LONG)(uintptr_t)&shadow[1]);
    }
    enabled=FALSE;attached=FALSE;idle_node=0;node_task=0;
    /* Shadow storage stays in the pinned DLL; any old, still-owned native
     * node safely delegates to its original method after detach. */
}
