// Phase72 forced fixed-ring exhaustion regression.
// Compile against the EXACT phase72 CIOBufferPool and verify the same
// refcount/recycle path for dynamically leased shared-source slabs.
// No sockets are needed to force the fixed pool to run out of owner slots.
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <windows.h>
#include "cio_core.hpp"
#include <cstdio>
#include <cstdlib>
#include <thread>
#include <vector>

static void check(bool ok,const char* why){
    if(!ok){std::fprintf(stderr,"PHASE72_POOL_FAIL: %s\n",why);std::abort();}
}
int main(){
    CIOBufferPool pool(32);
    check(pool.initializeOwner(0,1,1),"initialize owner slot");
    {
        CIOBufferPool::OwnerScope owner(pool,0);
        auto* ordinary=pool.acquire(LargeBufferBytes);
        check(ordinary && !ordinary->sourceOverflowRecord,"first fixed slot");

        // Exhaust the only ordinary 16KiB source slot. New sources MUST
        // receive a *valid Native refcounted source*, not nullptr.
        auto* extra=pool.acquireSharedSource();
        check(extra && extra->sourceOverflowRecord,"overflow source on fixed ring exhaustion");
        check(extra->references.load()==1,"overflow initial producer reference");
        extra->used=17;
        CIOBufferPool::retain(extra);      // Simulate async NativeSendRequest
        pool.recycle(extra);              // Producer closes first
        check(extra->references.load()==1,"queued recipient keeps slab alive");
        check(pool.sourceOverflowActive()==1,"outstanding request retains overflow slab");
        pool.recycle(extra);              // Async sender completion
        check(pool.sourceOverflowActive()==0,"last release returns overflow memory");
        pool.recycle(ordinary);

        // Exhaust while multiple recipients retain the SAME source.
        ordinary=pool.acquireSharedSource();
        check(ordinary && !ordinary->sourceOverflowRecord,"ordinary slot available again");
        extra=pool.acquireSharedSource();
        check(extra && extra->sourceOverflowRecord,"second overflow source");
        for(int i=0;i<30;i++)CIOBufferPool::retain(extra);
        pool.recycle(extra); // Producer release while all 30 SENDs retain
        for(int i=0;i<29;i++)pool.recycle(extra);
        check(pool.sourceOverflowActive()==1,"not freed before final recipient");
        pool.recycle(extra);
        check(pool.sourceOverflowActive()==0,"30-recipient release exact");
        pool.recycle(ordinary);

        // Hold the last regular slab and emulate many simultaneous moves.
        ordinary=pool.acquire(LargeBufferBytes);
        check(ordinary!=nullptr,"fixed source");
        constexpr int N=512;
        std::vector<CIOBuffer*> refs;
        refs.reserve(N);
        for(int i=0;i<N;i++){
            auto* p=pool.acquireSharedSource();
            check(p && p->sourceOverflowRecord,"no drops while 512 source events overlap");
            refs.push_back(p);
        }
        check(pool.sourceOverflowActive()==N,"512 active Native shared sources");
        for(auto* p:refs)pool.recycle(p);
        check(pool.sourceOverflowActive()==0,"512 released without leaks");
        pool.recycle(ordinary);
    }
    // Concurrent pool exhaustion and cross-thread final decrements. The
    // standard ring is held by main, and all owners point to owner 0.
    {
        CIOBufferPool::OwnerScope owner(pool,0);
        auto* ordinary=pool.acquire(LargeBufferBytes);
        check(ordinary!=nullptr,"concurrent regular slot");
        std::vector<std::thread> threads;
        for(int i=0;i<4;i++)threads.emplace_back([&pool](){
            CIOBufferPool::OwnerScope owner(pool,0);
            for(int j=0;j<800;j++){
                auto* p=pool.acquireSharedSource();
                check(p && p->sourceOverflowRecord,"concurrent reserve no drop");
                CIOBufferPool::retain(p);
                pool.recycle(p);
                pool.recycle(p);
            }
        });
        for(auto& t:threads)t.join();
        check(pool.sourceOverflowActive()==0,"all concurrently borrowed source slabs returned");
        pool.recycle(ordinary);
    }
    check(pool.sourceOverflowFailures()==0,"no Native OS source allocation failure");
    check(pool.corruptions()==0,"no refcount/ring corruption");
    check(pool.sourceOverflowCreated()>=3714,"Native owner overflow path was actually exercised");
    std::printf("PHASE72_NATIVE_FORCED_POOL_EXHAUSTION_PASS created=%llu active=%llu failures=%llu corruptions=%llu\n",
        static_cast<unsigned long long>(pool.sourceOverflowCreated()),
        static_cast<unsigned long long>(pool.sourceOverflowActive()),
        static_cast<unsigned long long>(pool.sourceOverflowFailures()),
        static_cast<unsigned long long>(pool.corruptions()));
    return 0;
}
