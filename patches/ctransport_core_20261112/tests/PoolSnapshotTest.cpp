#include "../native/cio_core.hpp"
#include <cassert>
#include <iostream>
int main(){
 CIOBufferPool pool(8192);assert(pool.initialize(2,32));
 std::uint64_t s[9];auto snapshot=[&]{pool.poolSnapshot(s);assert(s[1]==s[2]-s[3]&&s[5]==s[6]-s[7]);};
 {CIOBufferPool::OwnerScope scope(pool,0);auto* a=pool.acquire(8192);auto* b=pool.acquire(16384);assert(a&&b);
  CIOBufferPool::retain(a);pool.recycle(a);snapshot();assert(s[1]==1&&s[5]==1);
  pool.recycle(a);pool.recycle(b);snapshot();assert(s[1]==0&&s[5]==0&&s[2]==1&&s[3]==1);}
 std::vector<std::thread> threads;std::atomic<bool> go{false};
 for(int t=0;t<4;t++)threads.emplace_back([&]{while(!go.load())std::this_thread::yield();for(int i=0;i<2000;i++){auto* p=pool.acquire(i&1?8192:16384);assert(p);CIOBufferPool::retain(p);pool.recycle(p);pool.recycle(p);}});
 go=true;for(int i=0;i<1000;i++)snapshot();for(auto& t:threads)t.join();snapshot();
 assert(s[1]==0&&s[5]==0&&s[2]+s[6]==8002&&s[3]+s[7]==8002&&s[8]>0);
 std::cout<<"PASS native pool snapshot:existing-ring borrow/final-return accounting,shared retain,8000 concurrent leases,snapshot equality,zero borrowed after drain;slabBytes="<<s[8]<<"\n";
}
