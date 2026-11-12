#define CTRANSPORT_TEST
#include "../native/CTransport.cpp"
#include <cassert>
#include <iostream>
#include <random>
#include <future>

static void check(bool ok, const char* message) { if (!ok) throw std::runtime_error(message); }
template<class Predicate> static void until(Predicate p) {
 const auto end = std::chrono::steady_clock::now() + std::chrono::seconds(5);
 while (!p()) { if (std::chrono::steady_clock::now() > end) throw std::runtime_error("completion deadline"); std::this_thread::yield(); }
}
struct Probe {
 std::mutex mutex;
 std::vector<std::uint64_t> ids;
 std::vector<std::uint64_t> taskTokens;
 std::atomic<void*> retained{nullptr};
 std::atomic<bool> retainRead{false},blockTask{false},taskEntered{false},releaseTask{false};
 std::vector<unsigned char> received;
 std::atomic<int> closed{0}, sent{0};
 std::atomic<bool> echo{false}, pauseInCallback{false}, reject{false}, attemptStop{false};
 std::atomic<bool> blockReceive{false}, readEntered{false}, releaseRead{false};
};
static Probe* probe;
static int acceptCallback(void* server, std::uint64_t id, std::uint64_t) {
 { std::lock_guard<std::mutex> lock(probe->mutex); probe->ids.push_back(id); }
 if (probe->attemptStop.load()) check(!CIO_Server_Stop(server), "worker joined itself");
 return probe->reject ? 2 : 0;
}
static int receiveCallback(void* server, std::uint64_t id, const void* data, int length) {
 {
  std::lock_guard<std::mutex> lock(probe->mutex);
  const auto* p = static_cast<const unsigned char*>(data); probe->received.insert(probe->received.end(), p, p + length);
 }
 if (probe->retainRead.exchange(false)) probe->retained=CTransport_Server_RetainReceiveBuffer(server,id);
 if (probe->pauseInCallback) CIO_Server_PauseReceive(server, id, 1);
 if (probe->blockReceive) { probe->readEntered=true; while(!probe->releaseRead.load())std::this_thread::yield(); }
 if (probe->echo) check(CIO_Server_Send(server, id, data, length), "reentrant receive/send failed");
 return 0;
}
static int sendCallback(void*, std::uint64_t, const void*, int length) { probe->sent.fetch_add(length); return 0; }
static int closeCallback(void*, std::uint64_t, int, int) { ++probe->closed; return 0; }

static void taskCallback(void* server, std::uint64_t token, int status) {
 check(status==0 && currentWorker==server,"task not dispatched on native worker");
 if(probe->blockTask){probe->taskEntered=true;while(!probe->releaseTask)std::this_thread::yield();}
 {std::lock_guard<std::mutex> lock(probe->mutex);probe->taskTokens.push_back(token);}
}
struct BatchOperation {
 Operation op;
 unsigned index, expectedError;
 bool expectedSuccess;
 std::atomic<unsigned> visits{0};
 BatchOperation(CIOObject* owner,unsigned i,unsigned error,bool success)
  : op(OpKind::Read,owner),index(i),expectedError(error),expectedSuccess(success) {}
};
struct BatchOwner final : CIOObject {
 std::atomic<unsigned> calls{0},wrong{0};std::atomic<bool>* destroyed;
 explicit BatchOwner(std::atomic<bool>* flag):destroyed(flag){}
 ~BatchOwner()override{*destroyed=true;}
 void completed(void* raw,bool ok,unsigned bytes,unsigned error)noexcept override{
  auto& operation=*reinterpret_cast<BatchOperation*>(raw);
  if(ok!=operation.expectedSuccess||error!=operation.expectedError||bytes!=operation.index+1||operation.visits.fetch_add(1)!=0)++wrong;
  ++calls;
 }
};
static void batchTests(){
 for(bool mixed: {false,true}){
  Listener listener;auto* server=new Server(&listener);
  server->port=CreateIoCompletionPort(INVALID_HANDLE_VALUE,nullptr,0,4);
  server->ntStatusToError=fakewin::statusError;
  std::atomic<bool> destroyed{false};auto* owner=new BatchOwner(&destroyed);
  std::vector<std::unique_ptr<BatchOperation>> operations;
  const unsigned count=mixed?124:273, workers=mixed?4:1;
  auto* port=static_cast<fakewin::Port*>(server->port);
  // Four exit tokens at the front force one dequeue to contain every token
  // plus 124 real completions. It must finish them and wake the other workers.
  if(mixed)for(unsigned n=0;n<workers;++n)PostQueuedCompletionStatus(server->port,0,0,nullptr);
  for(unsigned i=0;i<count;++i){
   ULONG status=0;unsigned error=0;bool success=true;
   if(mixed)switch(i%5){
    case 1:status=0xc0000120u;error=995;success=false;break;
    case 2:status=0xc0000022u;error=5;success=false;break;
    case 3:status=0x40000000u;break; // NT_SUCCESS informational status.
    case 4:status=0xdead1234u;error=317;success=false;break;
   }
   operations.emplace_back(new BatchOperation(owner,i,error,success));
   // Deliberately contradict the dequeue snapshot. Reading the live reused
   // OVERLAPPED.Internal instead of ENTRY.Internal must fail this test.
   operations.back()->op.overlapped.Internal=0xc0000022u;
   owner->retain();fakewin::post(port,{i+1,reinterpret_cast<ULONG_PTR>(static_cast<CIOObject*>(owner)),&operations.back()->op.overlapped,0,status});
  }
  if(!mixed)PostQueuedCompletionStatus(server->port,0,0,nullptr);
  std::vector<std::thread> threads;for(unsigned n=0;n<workers;++n)threads.emplace_back([&]{server->worker();});
  for(auto& thread:threads)thread.join();
  check(owner->calls==count&&owner->wrong==0,"batch lost/duplicated or misclassified entry");
  owner->release();check(destroyed.load(),"batch I/O reference leaked");
  {std::lock_guard<std::mutex> lock(port->mutex);check(port->queue.empty(),"worker exit token was lost/left over");}
  CloseHandle(server->port);server->port=nullptr;server->release();
 }
 check(fakewin::singleCalls==0&&fakewin::requestedCapacity==128&&fakewin::maximumBatch==128,"worker did not use batch128");
 check(fakewin::batchSizes[18]>0,"273 completions did not drain 128/128/18 including exit");
 check(fakewin::translatedStatuses>0,"failed entries never translated NTSTATUS");
 {
  Listener listener;auto* server=new Server(&listener);fakewin::failStatusResolver=true;
  check(!server->start("127.0.0.1",0),"missing NTSTATUS resolver silently accepted");
  fakewin::failStatusResolver=false;server->release();
 }
 std::cout<<"PASS real worker: 273 completions drain 128/128/18; 124 mixed-status entries plus four front-loaded exit tokens; exact ref release and all workers exit\n";
 std::cout<<"PASS per-entry NTSTATUS snapshots, cancel/access-denied/informational/unknown statuses; missing resolver fails startup\n";
}

struct Fixture {
 Probe state; void* listener; Server* server;
 Fixture() {
  probe = &state; listener = CIO_Create_TcpServerListener();
  CIO_Set_FN_Server_OnAccept(listener, acceptCallback); CIO_Set_FN_Server_OnReceive(listener, receiveCallback);
  CIO_Set_FN_Server_OnSend(listener, sendCallback); CIO_Set_FN_Server_OnClose(listener, closeCallback);
  CTransport_SetTaskCallback(listener, taskCallback);
  server = static_cast<Server*>(CIO_Create_TcpServer(listener)); CIO_Server_SetWorkerThreadCount(server, 4);
  check(CIO_Server_Start(server, "127.0.0.1", 0), "start failed");
 }
 std::pair<SOCKET,std::uint64_t> connect() {
  std::size_t count; { std::lock_guard<std::mutex> lock(state.mutex); count = state.ids.size(); }
  auto socket = fakewin::connect(server->listening);
  until([&]{std::lock_guard<std::mutex> lock(state.mutex);return state.ids.size() > count;});
  std::lock_guard<std::mutex> lock(state.mutex); return {socket,state.ids[count]};
 }
 ~Fixture() {
  fakewin::holdSend = false;
  check(CIO_Server_Stop(server), "stop failed"); check(CIO_Server_Wait(server, 0), "wait confused with running");
  CIO_Destroy_TcpServer(server); CIO_Destroy_TcpServerListener(listener);
 }
};
static void coreTests() {
 using namespace ctransport;
 CIOBufferPool pool(1000); SendChain q;
 std::vector<unsigned char> payload(31000); for(std::size_t i=0;i<payload.size();++i)payload[i]=static_cast<unsigned char>(i*37);
 Span parts[]={{payload.data(),100},{payload.data()+100,9000},{payload.data()+9100,payload.size()-9100}};
 check(q.append(pool,parts,3),"copy gathering failed");
 std::vector<unsigned char> got; std::mt19937 random(27);
 while(q.head()) {
  auto* block=q.head(); auto remaining=block->used-block->offset; auto n=std::min<std::size_t>(remaining,1+random()%97);
  got.insert(got.end(),block->data.data()+block->offset,block->data.data()+block->offset+n);
  CIOBuffer* retired;check(q.complete(n,retired),"partial completion rejected");pool.recycle(retired);
 }
 check(got==payload&&q.size()==0,"partial sends reordered/lost bytes");
 Span one{payload.data(),100};check(q.append(pool,&one,1),"append failed");CIOBuffer* p;
 check(!q.complete(0,p)&&!q.complete(101,p)&&q.size()==100,"invalid completion changed queue");q.clear(pool);
 CIOBufferPool tiny(1);SendChain bounded;Span large{payload.data(),9000};
 check(!bounded.append(tiny,&large,1)&&!bounded.head()&&bounded.size()==0,"partial gather admission on OOM");
 IoState life;life.closing=true;check(!life.canClose(),"close bypassed accept callback");life.acceptDelivering=false;
 check(life.canClose(),"idle closing gate");life.readPosted=true;check(!life.canClose(),"close bypassed read");life.readPosted=false;
 life.writeDelivering=true;check(!life.canClose(),"close bypassed send callback");
 std::cout<<"PASS production 8192-byte pool/FIFO: gathering, randomized partial completion, invalid lengths, atomic allocation failure, lifetime gates\n";
}
static void engineTests() {
 {
  Fixture f;auto [socket,id]=f.connect();
  std::vector<unsigned char> input(17000);for(std::size_t i=0;i<input.size();++i)input[i]=static_cast<unsigned char>(i*19);
  fakewin::feed(socket,input);
  {std::lock_guard<std::mutex> lock(f.state.mutex);check(f.state.received.empty(),"accepted socket was not paused");}
  f.state.echo=true;fakewin::sendChunk=37;
  check(CIO_Server_PauseReceive(f.server,id,0),"resume failed");
  until([&]{return f.state.sent==static_cast<int>(input.size());});
  check(fakewin::wire(socket)==input,"echo/partial-send ordering mismatch");
  {std::lock_guard<std::mutex> lock(f.state.mutex);check(f.state.received==input,"receive data changed while borrowed");}
  char address[64];int capacity=64;unsigned short port;
  check(CIO_Server_GetRemoteAddress(f.server,id,address,&capacity,&port)&&std::string(address)=="127.0.0.1"&&port==23456,"address ABI");
  check(CIO_Server_Disconnect(f.server,id,1),"disconnect failed");until([&]{return f.state.closed==1;});
  until([&]{return !f.server->connections.load();});
  check(!CIO_Server_PauseReceive(f.server,id,0),"stale ID resolved after close");
 }
 std::cout<<"PASS actual native adapter: AcceptEx admission paused, 17000-byte receive/echo, synchronous success plus 37-byte completions, address lookup, stale ID rejection\n";
 {
  Fixture f;auto [socket,id]=f.connect();fakewin::holdSend=true;fakewin::sendChunk=8192;
  std::array<std::vector<unsigned char>,8> data;std::array<WSABUF,8> buffers;std::vector<unsigned char> expected;
  for(int i=0;i<8;++i){data[i].assign(7000,static_cast<unsigned char>(i+1));buffers[i]={7000,reinterpret_cast<char*>(data[i].data())};expected.insert(expected.end(),data[i].begin(),data[i].end());}
  check(CIO_Server_SendPackets(f.server,id,buffers.data(),8),"gathering ABI failed");
  for(auto& bytes:data)std::fill(bytes.begin(),bytes.end(),0);
  fakewin::holdSend=false;fakewin::flush(socket);until([&]{return f.state.sent==56000;});
  check(fakewin::wire(socket)==expected,"temporary gather source retained after return");
 }
 std::cout<<"PASS actual eight-WSABUF API, 56000-byte ordered send and immediate caller buffer reuse after native copy\n";
 {
  Fixture f;auto [socket,id]=f.connect();f.state.blockReceive=true;
  CIO_Server_PauseReceive(f.server,id,0);fakewin::feed(socket,{1,2,3});until([&]{return f.state.readEntered.load();});
  CIO_Server_Disconnect(f.server,id,1);check(f.state.closed==0,"terminal callback bypassed active receive callback");
  f.state.releaseRead=true;until([&]{return f.state.closed==1;});
 }
 std::cout<<"PASS remote disconnect while receive callback is active preserves its buffer and delays terminal delivery\n";
 {
  Fixture f;auto [socket,id]=f.connect();f.state.pauseInCallback=true;
  fakewin::feed(socket,std::vector<unsigned char>(9000,7));CIO_Server_PauseReceive(f.server,id,0);
  until([&]{std::lock_guard<std::mutex> lock(f.state.mutex);return f.state.received.size()==4096;});
  {auto* c=f.server->find(id);ObjectRef hold(c,false);std::lock_guard<std::mutex> lock(c->gate);check(!c->state.readPosted&&c->state.paused,"reentrant pause did not stop next receive");}
  f.state.pauseInCallback=false;CIO_Server_PauseReceive(f.server,id,0);
  until([&]{std::lock_guard<std::mutex> lock(f.state.mutex);return f.state.received.size()==9000;});
  fakewin::holdSend=true;const unsigned char data[]={1,2,3,4};check(CIO_Server_Send(f.server,id,data,4),"held send failed");
  CIO_Server_Disconnect(f.server,id,1);until([&]{return f.state.closed==1;});
 }
 std::cout<<"PASS callback-reentrant pause/resume; close with both a posted read and held write drains cancellations before one terminal callback\n";
 {
  Fixture f;auto [socket,id]=f.connect();fakewin::holdSend=false;fakewin::failSend=true;
  const unsigned char data[]={9,8,7};check(!CIO_Server_Send(f.server,id,data,3),"immediate WSASend failure accepted");
  until([&]{return f.state.closed==1;});
 }
 {
  Fixture f;auto [socket,id]=f.connect();fakewin::failRead=true;CIO_Server_PauseReceive(f.server,id,0);
  until([&]{return f.state.closed==1;});
 }
 {
  Fixture f;f.state.reject=true;auto connection=f.connect();until([&]{return f.state.closed==1;});
 }
 {
  Fixture f;f.state.attemptStop=true;auto connection=f.connect();
 }
 std::cout<<"PASS immediate submission failures balance I/O holds; rejected accept cleanup; completion worker cannot self-join\n";
 for(int cycle=0;cycle<10;++cycle) {
  Fixture f;std::vector<std::pair<SOCKET,std::uint64_t>> connections;
  for(int i=0;i<12;++i)connections.push_back(f.connect());
  for(auto [socket,id]:connections)CIO_Server_PauseReceive(f.server,id,0);
  std::vector<std::thread> closers;
  for(auto [socket,id]:connections)closers.emplace_back([&,id]{for(int k=0;k<5;++k)CIO_Server_Disconnect(f.server,id,1);});
  for(auto& t:closers)t.join();
  check(CIO_StopAccepting(f.server),"accept-stop failed");until([&]{return f.state.closed==12;});
  check(f.server->pendingAccepts==0,"accept operation hold leaked");
 }
 std::cout<<"PASS 120 connections/600 concurrent repeated closes; pending accepts cancelled and all workers drained over ten server lifetimes\n";
}
static std::atomic<unsigned> taskPostFailures{0};
static void failedTaskCallback(void* server,std::uint64_t token,int status){check(currentWorker==server&&token==55&&status==5,"scheduled post error lost");++taskPostFailures;}
static void recoveredStructureTests(){
 {
  CIOBufferPool pool(4096);
  CIOBuffer* block;
  {CIOBufferPool::OwnerScope owner(pool,3);block=pool.acquire();check(block&&block->owner==3,"TLS owner missing");}
  std::thread returner([&]{pool.recycle(block);});returner.join();
  {CIOBufferPool other(4);CIOBufferPool::OwnerScope nativeOwner(pool,7);auto* foreign=other.acquire();other.recycle(foreign);
   auto* local=pool.acquire();check(local->owner==7,"nested server call overwrote worker TLS owner");pool.recycle(local);}
  {CIOBufferPool::OwnerScope owner(pool,3);auto* again=pool.acquire();check(again==block,"buffer returned to foreign pool");pool.recycle(again);
   for(int i=0;i<5000;++i){again=pool.acquire();check(again==block,"ring wrap corrupted owner slots");pool.recycle(again);}}
  {CIOBufferPool::OwnerScope owner(pool,4);auto* big=pool.acquire(16384);check(big&&big->capacity==16384&&big->owner==4,"16K family missing");pool.recycle(big);}
  std::vector<std::thread> producers;
  for(unsigned i=0;i<12;++i)producers.emplace_back([&,i]{CIOBufferPool::OwnerScope owner(pool,i);for(unsigned j=0;j<2000;++j){auto* p=pool.acquire(j%2?8192:16384);check(p,"cross-owner stress allocation");CIOBufferPool::retain(p);pool.recycle(p);pool.recycle(p);}});
  for(auto& t:producers)t.join();
 }
 {
  CIOBufferPool pool(32);ReceiveWindow receive(pool,8192);check(receive.prepare(),"RX allocation");auto* original=receive.block();
  const unsigned char bytes[]={1,2,3,4,5};std::memcpy(original->data.data(),bytes,5);receive.append(5);receive.consume(2);
  CIOBufferPool::retain(original);check(receive.prepare()&&receive.block()!=original,"shared receive mutated");
  check(receive.block()->used==3&&!std::memcmp(receive.block()->data.data(),bytes+2,3),"COW did not preserve remainder");
  check(original->used==5&&!std::memcmp(original->data.data(),bytes,5),"shared consumer bytes changed");pool.recycle(original);
  auto* unique=receive.block();receive.consume(1);check(receive.prepare()&&receive.block()==unique&&unique->used==2,"unique compaction missing");
 }
 std::cout<<"PASS recovered owner-index rings/cross-thread returns/wraparound, 8K+16K families, shared RX COW and unique compaction\n";
 {
  CIOBufferPool pool(4);SendChain chain(16384);std::vector<unsigned char> bytes(30000,9);Span span{bytes.data(),bytes.size()};
  check(chain.append(pool,&span,1)&&chain.head()->capacity==16384&&chain.head()->next->used==13616,"16K sender not selected");chain.clear(pool);
 }
 {
  std::atomic<bool> destroyed{false};auto* owner=new BatchOwner(&destroyed);
  {
   TimerWheel wheel(0);check(wheel.schedule(1,owner,49)&&wheel.schedule(2,owner,10050)&&wheel.schedule(3,owner,100000),"wheel schedule");
   check(!wheel.schedule(1,owner,100),"duplicate timer admitted");check(!wheel.takeDue(49),"timer fired early");
   auto* due=wheel.takeDue(50);check(due==owner,"50ms timer not due");due->release();
   check(!wheel.takeDue(10049),"overflow timer fired early");due=wheel.takeDue(10050);check(due==owner,"overflow timer lost");due->release();
   auto* cancelled=wheel.cancel(3);check(cancelled==owner,"heap cancellation missing");cancelled->release();
   check(!wheel.takeDue(999999999999ull)&&wheel.size()==0,"empty wheel catch-up/cancel failed");
  }
  owner->release();check(destroyed,"wheel retained cancelled object");
 }
 std::cout<<"PASS deterministic 50ms/200-slot wheel, long-deadline heap promotion, cancellation and reference release\n";
 {
  void* retained=nullptr;
  {Fixture f;auto [socket,id]=f.connect();f.state.retainRead=true;CIO_Server_PauseReceive(f.server,id,0);fakewin::feed(socket,{1,2,3,4});
   until([&]{return f.state.retained.load()!=nullptr;});retained=f.state.retained;
   fakewin::feed(socket,{9,8,7});until([&]{std::lock_guard<std::mutex> lock(f.state.mutex);return f.state.received.size()==7;});
   check(CTransport_Buffer_Size(retained)==4&&!std::memcmp(CTransport_Buffer_Data(retained),"\1\2\3\4",4),"retained RX overwritten by next I/O");
   CIO_Server_Disconnect(f.server,id,1);until([&]{return f.state.closed==1;});
   std::lock_guard<std::mutex> lock(fakewin::mutex);check(fakewin::sockets.at(socket)->resetClose,"original linger0 close missing");
  }
  check(CTransport_Buffer_Size(retained)==4&&static_cast<const unsigned char*>(CTransport_Buffer_Data(retained))[3]==4,"buffer lease died with server");
  CTransport_Buffer_Release(retained);
 }
 std::cout<<"PASS native receive leases survive subsequent reads/disconnect/server destroy; SO_LINGER(1,0) applied\n";
 {
  Fixture f;
  for(unsigned i=1;i<=512;++i)check(CTransport_Server_PostTask(f.server,i),"native work post");
  check(CTransport_Server_Schedule(f.server,1000,55),"native timer schedule");
  check(CTransport_Server_Schedule(f.server,2000,200000),"long timer schedule");
  check(!CTransport_Server_Schedule(f.server,2000,20),"duplicate native token admitted");
  check(CTransport_Server_Cancel(f.server,2000),"long timer cancellation");
  until([&]{std::lock_guard<std::mutex> lock(f.state.mutex);return f.state.taskTokens.size()==513;});
  {std::lock_guard<std::mutex> lock(f.state.mutex);auto copy=f.state.taskTokens;std::sort(copy.begin(),copy.end());check(std::adjacent_find(copy.begin(),copy.end())==copy.end()&&copy.back()==1000,"work callbacks duplicated or cancellation ran");}
  for(unsigned i=3000;i<4000;++i){check(CTransport_Server_Schedule(f.server,i,100000),"timer cancellation stress registration");check(CTransport_Server_Cancel(f.server,i),"timer cancellation stress");}
  check(CTransport_Server_Schedule(f.server,4000,100000),"shutdown timer registration");
 }
 {
  Fixture f;f.state.blockTask=true;check(CTransport_Server_PostTask(f.server,1),"blocked task post");until([&]{return f.state.taskEntered.load();});
  check(!CTransport_Server_Cancel(f.server,1),"executing callback cancelled successfully");
  std::atomic<bool> stopped{false};std::thread stopper([&]{check(CIO_Server_Stop(f.server),"stop with task");stopped=true;});
  until([&]{return f.server->stopping.load();});check(!stopped,"Stop bypassed running task callback");
  f.state.releaseTask=true;stopper.join();check(stopped&&f.server->taskCount==0,"task stop leaked callback");
 }
 {
  Fixture f;fakewin::failPost=true;
  check(!CTransport_Server_PostTask(f.server,54)&&f.server->taskCount==0,"immediate task post failure leaked registration");
  CTransport_SetTaskCallback(f.listener,failedTaskCallback);
  check(CTransport_Server_Schedule(f.server,55,100),"scheduled failure setup");fakewin::failPost=true;
  until([&]{return taskPostFailures.load()==1;});until([&]{return f.server->taskCount==0;});
 }
 std::cout<<"PASS immediate/scheduled PostQueuedCompletionStatus failure balances references and reports scheduled error on worker\n";
 std::cout<<"PASS 512 native work callbacks, scheduled expiry, duplicate rejection, 1000 cancels, shutdown cancellation and callback fence\n";
}
int main(){coreTests();batchTests();engineTests();recoveredStructureTests();std::cout<<"PASS all native tests. Windows completion APIs are fixtures, not the Windows kernel.\n";}
