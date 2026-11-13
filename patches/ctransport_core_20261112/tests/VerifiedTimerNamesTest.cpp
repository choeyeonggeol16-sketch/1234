#include "../native/timer_wheel.hpp"
#include <cassert>
#include <iostream>
#include <thread>
#include <type_traits>
struct TimerProbe final:CIOObject {
 std::atomic<unsigned>& destroyed;explicit TimerProbe(std::atomic<unsigned>& n):destroyed(n){}
 ~TimerProbe()override{++destroyed;}
 void OnIOCallback(int completionOk,unsigned completionBytes,OVERLAPPED* completionRaw)noexcept override{}
};
int main(){
 static_assert(std::is_same_v<CThreadLocalTimer::SlotPool,CMwmrQueue<CThreadLocalTimer::CSlot,4009>>);
 {
  CThreadLocalTimer::SlotPool pool(3);auto* a=pool.Pop();auto* b=pool.Pop();auto* c=pool.Pop();
  assert(a&&b&&c&&!pool.Pop()&&pool.inUse()==3);
  CThreadLocalTimer::CSlot foreign;foreign.count=777;
  assert(!pool.Return(nullptr)&&!pool.Return(&foreign)&&foreign.count==777&&pool.inUse()==3);
  assert(!pool.Return(reinterpret_cast<CThreadLocalTimer::CSlot*>(reinterpret_cast<std::uintptr_t>(a)+1)));
  assert(pool.Return(a)&&!pool.Return(a)&&pool.Return(b)&&pool.Return(c));
  for(unsigned i=0;i<10000;++i){
   auto* x=pool.Pop();auto* y=pool.Pop();auto* z=pool.Pop();assert(x==a&&y==b&&z==c);
   assert(pool.Return(x)&&pool.Return(y)&&pool.Return(z)&&pool.inUse()==0);
  }
  assert(pool.faults()==4);
 }
 std::atomic<unsigned> destroyed{0},executed{0},cancelled{0};
 CThreadLocalTimer::SlotPool pool(205);
 {
  CThreadLocalTimer timer(0,&pool);
  auto* p=new TimerProbe(destroyed);assert(timer.AddTimer(p,50,123,1));p->release();
  timer.Dispatch(50,[&](CIOObject*)noexcept{
   if(++executed==1){auto* next=new TimerProbe(destroyed);assert(timer.AddTimer(next,50,456,2));next->release();}
  });
  assert(executed==2&&destroyed==2&&timer.size()==0);
  std::atomic<unsigned> finished{0};std::vector<std::thread> threads;
  for(unsigned t=0;t<4;++t)threads.emplace_back([&,t]{
   for(unsigned i=0;i<1000;++i){
    auto token=10+t*1000+i;auto* object=new TimerProbe(destroyed);
    assert(timer.AddTimer(object,100,static_cast<int>(token),token));object->release();
    if(i%2==0)if(auto* pending=timer.cancel(token)){++cancelled;pending->release();}
   }
   ++finished;
  });
  while(finished!=4)timer.Dispatch(100,[&](CIOObject*)noexcept{++executed;});
  for(auto& t:threads)t.join();timer.Dispatch(1000,[&](CIOObject*)noexcept{++executed;});
  assert(timer.size()==0&&executed+cancelled==4002&&destroyed==4002);
 }
 assert(pool.inUse()==0&&pool.faults()==0);
 std::cout<<"PASS verified CThreadLocalTimer/CSlot/CMwmrQueue4009 names;10000 FIFO wraps;null/foreign/interior/duplicate returns unchanged;reentrant Dispatch;4000 concurrent AddTimer/cancel/Dispatch,exact release and zero borrowed at destruction\n";
}
