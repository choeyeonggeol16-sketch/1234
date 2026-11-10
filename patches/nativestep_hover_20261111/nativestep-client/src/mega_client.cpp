#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <wincrypt.h>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include "click_points.h"
#include "mega_client.h"
#include "step_network.h"

namespace {
static_assert(sizeof(void*) == 4, "Mega.bin is an x86 client");
using Point = nativestep::Point;
constexpr uintptr_t kBase=0x400000;
bool supported=false;
bool fail(const char* reason) noexcept {NativeStep_LogInput(reason,0);return false;}
template<class T> bool read(uintptr_t address,T& value) noexcept {
    SIZE_T got=0;
    return ReadProcessMemory(GetCurrentProcess(),reinterpret_cast<const void*>(address),
                             &value,sizeof(value),&got) && got==sizeof(value);
}
bool signature(uintptr_t address,const unsigned char* bytes,size_t length) noexcept {
    unsigned char actual[32]{}; SIZE_T got=0;
    return length<=sizeof(actual) && ReadProcessMemory(GetCurrentProcess(),
        reinterpret_cast<const void*>(address),actual,length,&got) && got==length &&
        std::memcmp(actual,bytes,length)==0;
}
bool file_matches() noexcept {
    static constexpr unsigned char expected[32]={
        0x09,0x80,0x4d,0x95,0x79,0x44,0x6f,0xb9,0xa6,0x30,0xc3,0x9c,0x1f,0x1b,0x2d,0x08,
        0x08,0x37,0x18,0x26,0x1d,0x2f,0x3e,0x57,0xb2,0xe3,0x6f,0x80,0xba,0x86,0x54,0x90};
    wchar_t path[MAX_PATH]{};
    if (!GetModuleFileNameW(nullptr,path,MAX_PATH)) return false;
    HANDLE file=CreateFileW(path,GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_DELETE,nullptr,OPEN_EXISTING,0,nullptr);
    if (file==INVALID_HANDLE_VALUE) return false;
    HCRYPTPROV provider=0; HCRYPTHASH hash=0; bool ok=false;
    if (CryptAcquireContextW(&provider,nullptr,nullptr,PROV_RSA_AES,CRYPT_VERIFYCONTEXT) &&
        CryptCreateHash(provider,CALG_SHA_256,0,0,&hash)) {
        unsigned char block[32768]{}; DWORD got=0; bool complete=true;
        while (true) {
            if (!ReadFile(file,block,sizeof(block),&got,nullptr)) {complete=false;break;}
            if (!got) break;
            if (!CryptHashData(hash,block,got,0)) {complete=false;break;}
        }
        unsigned char digest[32]{}; DWORD size=sizeof(digest);
        ok=complete && CryptGetHashParam(hash,HP_HASHVAL,digest,&size,0) && size==32 &&
            std::memcmp(digest,expected,32)==0;
    }
    if(hash) CryptDestroyHash(hash);
    if(provider) CryptReleaseContext(provider,0);
    CloseHandle(file); return ok;
}
bool code_matches() noexcept {
    static constexpr unsigned char scale[]={0x55,0x8b,0xec,0xa1,0x88,0x64,0x79,0x00,0x56,0x83,0xf8,0x07,0x77,0x7b,0xff,0x24};
    static constexpr unsigned char project[]={0x55,0x8b,0xec,0x83,0xec,0x08,0x56,0x8b,0x75,0x08,0x8d,0x45,0xf8,0x56,0x50,0xe8};
    static constexpr unsigned char step[]={0x55,0x8b,0xec,0x8b,0x45,0x0c,0x56,0x8b,0x71,0x04,0x8b,0x14,0xc5,0x3c,0x39,0x8e};
    static constexpr unsigned char cancel[]={0x55,0x8b,0xec,0x83,0xec,0x10,0x53,0x8d,0x45,0xf0,0x33,0xdb,0x50,0x8d,0x4d,0xf8};
    static constexpr unsigned char clock[]={0x55,0x8b,0xec,0x83,0xec,0x08,0xa1,0x10,0x3f,0x9b,0x00,0x85,0xc0,0x74,0x37,0x8d};
    static constexpr unsigned char busy[]={0xa1,0x90,0x09,0x9b,0x00,0x8b,0x15,0x94,0x09,0x9b,0x00,0x35,0x21,0x79,0x01,0xc0};
    static constexpr unsigned char kind[]={0x53,0x56,0x8b,0xf1,0x8b,0x0d,0xbc,0x09,0x7c,0x00,0x0f,0xbf,0x46,0x18,0x8a,0x1c};
    static constexpr unsigned char label[]={0x55,0x8b,0xec,0x83,0xec,0x14,0xa1,0x54,0x63,0x8d,0x00,0x53,0x8b,0xd9,0x56,0x3b};
    static constexpr unsigned char dispatch[]={0x55,0x8b,0xec,0x83,0xec,0x40,0x8b,0x45,0x0c,0x53,0x56,0x8b,0xf1,0x8b,0x4d,0x08};
    return reinterpret_cast<uintptr_t>(GetModuleHandleW(nullptr))==kBase &&
        signature(0x45e2b0,scale,sizeof(scale)) && signature(0x49f4c0,project,sizeof(project)) &&
        signature(0x49f590,project,sizeof(project)) && signature(0x49f810,step,sizeof(step)) &&
        signature(0x4cc830,cancel,sizeof(cancel)) && signature(0x4bf790,clock,sizeof(clock)) &&
        signature(0x45e370,dispatch,sizeof(dispatch)) && signature(0x45cbd0,busy,sizeof(busy)) &&
        signature(0x4d31a0,kind,sizeof(kind)) && signature(0x4d13a0,label,sizeof(label));
}
using Project = Point* (__thiscall *)(const Point*,Point*);
using Step = Point* (__thiscall *)(const Point*,Point*,int);
bool world_active(HWND hwnd) noexcept {
    uintptr_t game=0,dispatcher=0,player=0;
    return supported && read(0x7c290c,game) && game==reinterpret_cast<uintptr_t>(hwnd) &&
        read(0x796244,dispatcher) && dispatcher==0x45e370 && read(0x9b4214,player) && player;
}
}

bool MegaInitialize() noexcept {
    supported=false;
    if(!file_matches()) return fail("Mega executable SHA256 mismatch: input disabled");
    if(!code_matches()) return fail("Mega native code signature mismatch: input disabled");
    supported=true;
    NativeStep_LogInput(supported ? "Mega SHA256 and native input signatures verified" :
        "unsupported Mega executable or native code: input disabled",0);
    return supported;
}

bool MegaPlanClick(HWND hwnd,int heading,MegaClick& click,unsigned candidate) noexcept {
    if(candidate>=nativestep::kClickPointCount) return false;
    if (heading<0 || heading>7 || !world_active(hwnd)) return fail("native plan rejected: game world or dispatcher inactive");
    DWORD owner=GetWindowThreadProcessId(hwnd,nullptr);
    if (owner!=GetCurrentThreadId()) return fail("native plan rejected: wrong UI thread");
    uintptr_t player=0; Point source{}; int mode=-1; RECT rect{};
    if (!read(0x9b4214,player) || !read(player+0x34,source) || !read(0x796488,mode) ||
        mode<0 || mode>7 || !GetClientRect(hwnd,&rect)) return fail("native plan rejected: player or resolution state unavailable");
    static constexpr Point expected[8]={{0,-1},{2,-1},{2,0},{2,1},{0,1},{-2,1},{-2,0},{-2,-1}};
    Point delta{};
    if (!read(0x8e3938+heading*sizeof(Point),delta) || delta.x!=expected[heading].x ||
        delta.y!=expected[heading].y) return fail("native plan rejected: heading table mismatch");
    Point next{},logical{},roundtrip{};
    reinterpret_cast<Step>(0x49f810)(&source,&next,heading);
    reinterpret_cast<Project>(0x49f4c0)(&next,&logical);
    // Native projection returns the diamond's left vertex. Click its 48x24
    // cell interior; the original inverse routine below verifies the tile.
    logical.x+=24+nativestep::kClickOffsets[candidate].x;
    logical.y+=12+nativestep::kClickOffsets[candidate].y;
    if(logical.x<0 || logical.x>=640 || logical.y<0 || logical.y>=380) return fail("native plan rejected: next tile outside game viewport");
    reinterpret_cast<Project>(0x49f590)(&logical,&roundtrip);
    if ((roundtrip.x&~1)!=next.x || roundtrip.y!=next.y) return fail("native plan rejected: world-screen roundtrip mismatch");
    static constexpr Point corners[]={{-2,-2},{2,-2},{-2,2},{2,2}};
    for (const Point d : corners) {
        Point inside{logical.x+d.x,logical.y+d.y},check{};
        reinterpret_cast<Project>(0x49f590)(&inside,&check);
        if((check.x&~1)!=next.x || check.y!=next.y) return fail("native plan rejected: click is on tile boundary");
    }
    Point raw=logical;
    switch(mode&3) {
    case 0: if((raw.x&1)||(raw.y&1)) return false; raw.x/=2;raw.y/=2;break;
    case 1: break;
    case 2: raw.x=(raw.x*3+1)/2;raw.y=(raw.y*3+1)/2;break;
    case 3: raw.x*=2;raw.y*=2;break;
    }
    if(raw.x<0 || raw.y<0 || raw.x>=rect.right || raw.y>=rect.bottom) return fail("native plan rejected: click outside client window");
    // Verify the actual native resolution conversion too, not only our inverse.
    Point mapped{};
    using Scale=Point* (__thiscall *)(Point*,int,int);
    reinterpret_cast<Scale>(0x45e2b0)(&mapped,raw.x,raw.y);
    if(mapped.x!=logical.x || mapped.y!=logical.y) return fail("native plan rejected: resolution roundtrip mismatch");
    click={raw,logical,next,heading,mode,static_cast<int>(candidate)}; return true;
}

bool MegaMouseDownReady() noexcept {
    uint32_t last=0,interaction=0,callback=0;
    unsigned char input_blocked=0,forced_action=0;
    if(!supported || !read(0x7c1f44,last) || !read(0x7c260c,input_blocked) ||
       !read(0x8d6228,interaction) || !read(0x8d621c,callback) ||
       !read(0x7c2616,forced_action) || input_blocked || interaction || callback || forced_action) return false;
    // These are the original walk-goal function's early-return conditions.
    // Wait for the client to accept a walk click instead of clicking while busy.
    using BusyQuery=bool (__thiscall *)(const void*);
    if(reinterpret_cast<BusyQuery>(0x45cbd0)(reinterpret_cast<const void*>(0x18))) return false;
    const uint32_t now=reinterpret_cast<uint32_t (__cdecl *)()>(0x4bf790)();
    return static_cast<int32_t>(now-(last+102))>=0;
}

bool MegaGoalMatches(const MegaClick& click) noexcept {
    Point goal{};
    return read(0x7c28e8,goal) && goal.x==click.world.x && goal.y==click.world.y;
}

bool MegaStepApplied(const MegaClick& click) noexcept {
    uintptr_t player=0; Point actual{};
    return read(0x9b4214,player) && player && read(player+0x34,actual) &&
        actual.x==click.world.x && actual.y==click.world.y;
}

void MegaCancelOwnedMovement() noexcept {
    uintptr_t player=0;
    if(supported && read(0x9b4214,player) && player)
        reinterpret_cast<void (__cdecl *)()>(0x4cc830)();
}

// Called after a non-button MOVE, before committing a DOWN. It never chooses
// another world tile, presses a button, or changes the client's selected goal.
bool MegaClickClear(const MegaClick& click) noexcept {
    uintptr_t player=0,hover=0,objects=0;
    unsigned char labels=0,names=0,randomized=0,special=0;
    int count=0; Point scroll{};
    if(!supported || !read(0x9b4214,player) || !read(0x8d6354,hover) ||
       (hover && hover!=player) || !read(0x7c295b,randomized) || randomized ||
       !read(0x7c2945,special) || (special && hover) || !read(0x7c2979,labels) ||
       !read(0x79a3b0,names)) return false;
    if(!labels && !names) return true;
    if(!read(0x9b4280,count) || count<0 || count>4096 ||
       !read(0x9b4288,objects) || !read(0x8d6368,scroll)) return false;
    using Kind=unsigned char (__thiscall *)(const void*);
    using Bounds=nativestep::LabelRect* (__thiscall *)(const void*,nativestep::LabelRect*,int);
    for(int i=0;i<count;++i) {
        uintptr_t object=0,name=0;unsigned char hidden=0;
        if(!read(objects+i*sizeof(uintptr_t),object) || !object) return false;
        if(!read(object+0x60,name) || !name) continue;
        if(reinterpret_cast<Kind>(0x4d31a0)(reinterpret_cast<const void*>(object))!=9) continue;
        if(!read(object+0x26,hidden)) return false;
        if(hidden) continue;
        int length=0;
        for(;length<256;++length) {char c=0;if(!read(name+length,c)) return false;if(!c) break;}
        if(length==256) return false;
        nativestep::LabelRect box{};
        // The native label scanner itself installs each object as the hover
        // context for this bounds call, then restores it (0x4723cb..0x4724c5).
        // Bounds is synchronous and does not dispatch UI messages.
        auto* slot=reinterpret_cast<uintptr_t*>(0x8d6354);
        *slot=object;
        reinterpret_cast<Bounds>(0x4d13a0)(reinterpret_cast<const void*>(object),&box,1);
        *slot=hover;
        if(nativestep::label_contains(box,length,scroll,click.logical)) return false;
    }
    return true;
}

void MegaLogState(const MegaClick& click) noexcept {
    uintptr_t player=0,hover=0,callback=0,interaction=0;
    Point actual{},goal{},hover_world{};unsigned char blocked=0,pending=0;
    read(0x9b4214,player);if(player) read(player+0x34,actual);
    read(0x7c28e8,goal);read(0x8d6354,hover);if(hover) read(hover+0x34,hover_world);
    read(0x8d621c,callback);read(0x8d6228,interaction);read(0x7c260c,blocked);read(0x7c28e3,pending);
    char line[320]{};
    std::snprintf(line,sizeof(line),"native state direction=%d candidate=%d expected=(%d,%d) goal=(%d,%d) player=(%d,%d) hover=%d hover-world=(%d,%d) callback=%d interaction=%d blocked=%u pending=%u",
        click.heading,click.candidate,click.world.x,click.world.y,goal.x,goal.y,actual.x,actual.y,
        hover!=0,hover_world.x,hover_world.y,callback!=0,interaction!=0,blocked,pending);
    NativeStep_LogInput(line,0);
}
