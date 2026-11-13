#define CTRANSPORT_TEST
#include "../native/CTransport.cpp"
#include <cassert>
#include <iostream>
#include <random>
#include <future>
#include "fake_l1_codec.hpp"

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
 std::vector<int> receiveLengths;
 std::atomic<bool> userProfile{false},holdPacket{false};
 std::atomic<std::uint64_t> heldSequence{0};
 std::atomic<const unsigned char*> heldPlaintext{nullptr};
 std::atomic<int> packetCalls{0},deadlineCalls{0};
 std::atomic<int> closed{0}, sent{0},closing{0};
 std::atomic<bool> terminalStopCheck{false}, reenterClose{false},blockClosing{false},closingEntered{false},releaseClosing{false};
 std::atomic<bool> echo{false}, pauseInCallback{false}, reject{false}, attemptStop{false};
 std::atomic<bool> blockReceive{false}, readEntered{false}, releaseRead{false};
};
static Probe* probe;
static int acceptCallback(void* server, std::uint64_t id, std::uint64_t) {
 if (probe->userProfile) check(CSocket_SetKey(server,id,73461), "user-socket profile enable");
 { std::lock_guard<std::mutex> lock(probe->mutex); probe->ids.push_back(id); }
 if (probe->attemptStop.load()) check(!L1Interop_StopServer(server), "worker joined itself");
 return probe->reject ? 2 : 0;
}
static int receiveCallback(void* server, std::uint64_t id, const void* data, int length) {
 {
  std::lock_guard<std::mutex> lock(probe->mutex);
  probe->receiveLengths.push_back(length);
  const auto* p = static_cast<const unsigned char*>(data); probe->received.insert(probe->received.end(), p, p + length);
 }
 if (probe->retainRead.exchange(false)) probe->retained=L1Interop_RetainReceiveBuffer(server,id);
 if (probe->pauseInCallback) L1Interop_PauseRead(server, id, 1);
 if (probe->blockReceive) { probe->readEntered=true; while(!probe->releaseRead.load())std::this_thread::yield(); }
 if (probe->echo) check(CIOSocket__Write(server, id, data, length), "reentrant receive/send failed");
 return 0;
}
static int packetCallback(void* server,std::uint64_t id,std::uint64_t sequence,const void* data,int length) {
 if(!length)return 0;
 {std::lock_guard<std::mutex> lock(probe->mutex);auto* bytes=static_cast<const unsigned char*>(data);probe->received.insert(probe->received.end(),bytes,bytes+length);probe->receiveLengths.push_back(length);}
 ++probe->packetCalls;
 if(probe->holdPacket.exchange(false)){probe->heldPlaintext=static_cast<const unsigned char*>(data);probe->heldSequence=sequence;return 0;}
 check(L1Interop_ReleasePacket(server,id,sequence),"native packet release rejected");return 0;
}
static int deadlineCallback(void*,std::uint64_t){++probe->deadlineCalls;return 5000;}
static int sendCallback(void*, std::uint64_t, const void*, int length) { probe->sent.fetch_add(length); return 0; }
static int closingCallback(void* server,std::uint64_t id,int,int) {
 ++probe->closing;
 if(probe->reenterClose){
  check(!L1Interop_StopServer(server)&&!CIOServer_Close(server)&&!L1Interop_WaitServer(server,INFINITE),"callback blocked in self stop/wait");
  check(CIOSocketEx_Close(server,id),"reentrant pre-close rejected");
 }
 if(probe->blockClosing){probe->closingEntered=true;while(!probe->releaseClosing)std::this_thread::yield();}
 return 0;
}
static int closeCallback(void* server, std::uint64_t, int, int) {
 if(probe->terminalStopCheck)check(!L1Interop_StopServer(server)&&!CIOServer_Close(server)&&!L1Interop_WaitServer(server,INFINITE),"terminal callback self-wait");
 ++probe->closed;return 0;
}

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
 void OnIOCallback(void* raw,bool ok,unsigned bytes,unsigned error)noexcept override{
  auto& operation=*reinterpret_cast<BatchOperation*>(raw);
  if(ok!=operation.expectedSuccess||error!=operation.expectedError||bytes!=operation.index+1||operation.visits.fetch_add(1)!=0)++wrong;
  ++calls;
 }
};
static void batchTests(){
 for(bool mixed: {false,true}){
  L1Callbacks listener;auto* server=new Server(&listener);
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
  L1Callbacks listener;auto* server=new Server(&listener);fakewin::failStatusResolver=true;
  check(!server->Run("127.0.0.1",0),"missing NTSTATUS resolver silently accepted");
  fakewin::failStatusResolver=false;server->release();
 }
 std::cout<<"PASS real worker: 273 completions drain 128/128/18; 124 mixed-status entries plus four front-loaded exit tokens; exact ref release and all workers exit\n";
 std::cout<<"PASS per-entry NTSTATUS snapshots, cancel/access-denied/informational/unknown statuses; missing resolver fails startup\n";
}

struct Fixture {
 Probe state; void* listener; Server* server;
 Fixture(unsigned ipLimit=0,unsigned macLimit=0) {
  probe = &state; listener = L1Interop_CreateCallbacks();
  L1Interop_SetOnAccept(listener, acceptCallback); L1Interop_SetOnRead(listener, receiveCallback);
  L1Interop_SetOnWrite(listener, sendCallback); L1Interop_SetOnClose(listener, closeCallback);
  L1Interop_SetOnIOCallback(listener, taskCallback);
  L1Interop_SetOnClosing(listener, closingCallback);
  L1Interop_SetOnPacket(listener,packetCallback);L1Interop_SetOnTimerExpired(listener,deadlineCallback);
  server = static_cast<Server*>(L1Interop_CreateServer(listener));auto codec=fixturecodec::functions();check(L1Interop_SetCodecApi(server,codec.data(),7),"fixture codec API install"); L1Interop_ConfigureWorkers(server, 4); L1Interop_ConfigureCapacity(server, 128);
  check(L1Interop_ConfigureSocketLimits(server,ipLimit,macLimit),"configure native limits");
  check(L1Interop_ConfigurePerf(server,100),"configure native performance sampling");
  check(Server_Run(server, "127.0.0.1", 0), "start failed");
 }
 std::pair<SOCKET,std::uint64_t> connect() {
  std::size_t count; { std::lock_guard<std::mutex> lock(state.mutex); count = state.ids.size(); }
  auto socket = fakewin::connect(server->listening);
  until([&]{std::lock_guard<std::mutex> lock(state.mutex);return state.ids.size() > count;});
  std::lock_guard<std::mutex> lock(state.mutex); return {socket,state.ids[count]};
 }
 ~Fixture() {
  fakewin::holdSend = false;
  check(L1Interop_StopServer(server), "stop failed"); check(L1Interop_WaitServer(server, 0), "wait confused with running");
  check(state.closing==state.closed,"pre-close and terminal callback count differ");
  L1Interop_ReleaseServer(server); L1Interop_ReleaseCallbacks(listener);
 }
};
static void coreTests() {

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
  check(L1Interop_PauseRead(f.server,id,0),"resume failed");
  until([&]{return f.state.sent==static_cast<int>(input.size());});
  check(fakewin::wire(socket)==input,"echo/partial-send ordering mismatch");
  {std::lock_guard<std::mutex> lock(f.state.mutex);check(f.state.received==input,"receive data changed while borrowed");}
  char address[64];int capacity=64;unsigned short port;
  check(CSocket_GetIPAddressString(f.server,id,address,&capacity,&port)&&std::string(address)=="127.0.0.1"&&port==23456,"address ABI");
  check(CIOSocketEx_Close(f.server,id),"disconnect failed");until([&]{return f.state.closed==1;});
  until([&]{return !f.server->connections.load();});
  check(!L1Interop_PauseRead(f.server,id,0),"stale ID resolved after close");
 }
 std::cout<<"PASS actual native adapter: event/IOCP accept admission paused, 17000-byte receive/echo, synchronous success plus 37-byte completions, address lookup, stale ID rejection\n";
 {
  Fixture f;auto [socket,id]=f.connect();fakewin::holdSend=true;fakewin::sendChunk=8192;
  std::array<std::vector<unsigned char>,8> data;std::array<WSABUF,8> buffers;std::vector<unsigned char> expected;
  for(int i=0;i<8;++i){data[i].assign(7000,static_cast<unsigned char>(i+1));buffers[i]={7000,reinterpret_cast<char*>(data[i].data())};expected.insert(expected.end(),data[i].begin(),data[i].end());}
  check(L1Interop_WriteBuffers(f.server,id,buffers.data(),8),"gathering ABI failed");
  for(auto& bytes:data)std::fill(bytes.begin(),bytes.end(),0);
  fakewin::holdSend=false;fakewin::flush(socket);until([&]{return f.state.sent==56000;});
  check(fakewin::wire(socket)==expected,"temporary gather source retained after return");
 }
 std::cout<<"PASS actual eight-WSABUF API, 56000-byte ordered send and immediate caller buffer reuse after native copy\n";
 {
  Fixture f;auto [socket,id]=f.connect();f.state.blockReceive=true;
  L1Interop_PauseRead(f.server,id,0);fakewin::feed(socket,{1,2,3});until([&]{return f.state.readEntered.load();});
  CIOSocketEx_Close(f.server,id);check(f.state.closed==0,"terminal callback bypassed active receive callback");
  f.state.releaseRead=true;until([&]{return f.state.closed==1;});
 }
 std::cout<<"PASS remote disconnect while receive callback is active preserves its buffer and delays terminal delivery\n";
 {
  Fixture f;auto [socket,id]=f.connect();f.state.pauseInCallback=true;
  fakewin::feed(socket,std::vector<unsigned char>(9000,7));L1Interop_PauseRead(f.server,id,0);
  until([&]{std::lock_guard<std::mutex> lock(f.state.mutex);return f.state.received.size()==4096;});
  until([&]{auto* c=f.server->find(id);ObjectRef hold(c,false);std::lock_guard<std::mutex> lock(c->gate);return !c->state.readPosted&&c->state.paused&&!c->state.readDelivering;});
  f.state.pauseInCallback=false;L1Interop_PauseRead(f.server,id,0);
  until([&]{std::lock_guard<std::mutex> lock(f.state.mutex);return f.state.received.size()==9000;});
  fakewin::holdSend=true;const unsigned char data[]={1,2,3,4};check(CIOSocket__Write(f.server,id,data,4),"held send failed");
  CIOSocketEx_Close(f.server,id);until([&]{return f.state.closed==1;});
 }
 std::cout<<"PASS callback-reentrant pause/resume; close with both a posted read and held write drains cancellations before one terminal callback\n";
 {
  Fixture f;auto [socket,id]=f.connect();fakewin::holdSend=false;fakewin::failSend=true;
  const unsigned char data[]={9,8,7};const bool admitted=CIOSocket__Write(f.server,id,data,3);
  // Admission can run behind the accept/write owner. An inline failure returns
  // false; a deferred failure must close once without reporting sent bytes.
  until([&]{return f.state.closed==1;});
  check(f.state.sent==0&&fakewin::wire(socket).empty(),"failed WSASend delivered bytes");
  std::uint64_t stats[9]{};check(L1Interop_GetSendDiagnostics(f.server,stats,9),"failed send diagnostics");
  check(stats[0]==1&&stats[1]==1&&stats[5]==1&&stats[7]==0&&stats[8]==0,"failed send lifetime/owner");
  (void)admitted;
 }
 {
  Fixture f;auto [socket,id]=f.connect();fakewin::failRead=true;L1Interop_PauseRead(f.server,id,0);
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
  for(auto [socket,id]:connections)L1Interop_PauseRead(f.server,id,0);
  std::vector<std::thread> closers;
  for(auto [socket,id]:connections)closers.emplace_back([&,id]{for(int k=0;k<5;++k)CIOSocketEx_Close(f.server,id);});
  for(auto& t:closers)t.join();
  check(CIOServer_Close(f.server),"accept-stop failed");until([&]{return f.state.closed==12;});
  check(f.server->pendingAccepts==0,"accept operation hold leaked");
 }
 std::cout<<"PASS 120 connections/600 concurrent repeated closes; pending accepts cancelled and all workers drained over ten server lifetimes\n";
}
static std::atomic<unsigned> taskPostFailures{0};
static void failedTaskCallback(void* server,std::uint64_t token,int status){check(currentWorker==server&&token==56&&status==5,"scheduled post error lost");++taskPostFailures;}
static void recoveredStructureTests(){
 {
  CIOBufferPool pool(4096);
  CIOBuffer* block;
  {CIOBufferPool::OwnerScope owner(pool,3);block=pool.acquire();check(block&&block->owner==3,"TLS owner missing");}
  std::thread returner([&]{pool.recycle(block);});returner.join();
  {CIOBufferPool other(4);CIOBufferPool::OwnerScope nativeOwner(pool,7);auto* foreign=other.acquire();other.recycle(foreign);
   auto* local=pool.acquire();check(local->owner==7,"nested server call overwrote worker TLS owner");pool.recycle(local);}
  {CIOBufferPool::OwnerScope owner(pool,3);auto* again=pool.acquire();check(again&&again->owner==3,"buffer returned to foreign pool");pool.recycle(again);
   for(int i=0;i<5000;++i){again=pool.acquire();check(again&&again->owner==3,"ring wrap corrupted owner slots");pool.recycle(again);}}
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
   CThreadLocalTimer wheel(0);check(wheel.AddTimer(owner,49,static_cast<int>(1),1)&&wheel.AddTimer(owner,10050,static_cast<int>(2),2)&&wheel.AddTimer(owner,100000,static_cast<int>(3),3),"wheel schedule");
   check(!wheel.AddTimer(owner,100,static_cast<int>(1),1),"duplicate timer admitted");check(!wheel.takeDue(49),"timer fired early");
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
  {Fixture f;auto [socket,id]=f.connect();f.state.retainRead=true;L1Interop_PauseRead(f.server,id,0);fakewin::feed(socket,{1,2,3,4});
   until([&]{return f.state.retained.load()!=nullptr;});retained=f.state.retained;
   fakewin::feed(socket,{9,8,7});until([&]{std::lock_guard<std::mutex> lock(f.state.mutex);return f.state.received.size()==7;});
   check(L1Interop_ReceiveBufferSize(retained)==4&&!std::memcmp(L1Interop_ReceiveBufferData(retained),"\1\2\3\4",4),"retained RX overwritten by next I/O");
   CIOSocketEx_Close(f.server,id);until([&]{return f.state.closed==1;});
   std::lock_guard<std::mutex> lock(fakewin::mutex);check(fakewin::sockets.at(socket)->resetClose,"original linger0 close missing");
  }
  check(L1Interop_ReceiveBufferSize(retained)==4&&static_cast<const unsigned char*>(L1Interop_ReceiveBufferData(retained))[3]==4,"buffer lease died with server");
  L1Interop_ReleaseReceiveBuffer(retained);
 }
 std::cout<<"PASS native receive leases survive subsequent reads/disconnect/server destroy; SO_LINGER(1,0) applied\n";
 {
  Fixture f;
  for(unsigned i=1;i<=512;++i)check(L1Interop_PostIOCallback(f.server,i),"native work post");
  check(L1Interop_ScheduleTimer(f.server,1000,55),"native timer schedule");
  check(L1Interop_ScheduleTimer(f.server,2000,200000),"long timer schedule");
  check(!L1Interop_ScheduleTimer(f.server,2000,20),"duplicate native token admitted");
  check(L1Interop_CancelTimer(f.server,2000),"long timer cancellation");
  until([&]{std::lock_guard<std::mutex> lock(f.state.mutex);return f.state.taskTokens.size()==513;});
  {std::lock_guard<std::mutex> lock(f.state.mutex);auto copy=f.state.taskTokens;std::sort(copy.begin(),copy.end());check(std::adjacent_find(copy.begin(),copy.end())==copy.end()&&copy.back()==1000,"work callbacks duplicated or cancellation ran");}
  for(unsigned i=3000;i<4000;++i){check(L1Interop_ScheduleTimer(f.server,i,100000),"timer cancellation stress registration");check(L1Interop_CancelTimer(f.server,i),"timer cancellation stress");}
  check(L1Interop_ScheduleTimer(f.server,4000,100000),"shutdown timer registration");
 }
 {
  Fixture f;f.state.blockTask=true;check(L1Interop_PostIOCallback(f.server,1),"blocked task post");until([&]{return f.state.taskEntered.load();});
  check(!L1Interop_CancelTimer(f.server,1),"executing callback cancelled successfully");
  std::atomic<bool> stopped{false};std::thread stopper([&]{check(L1Interop_StopServer(f.server),"stop with task");stopped=true;});
  until([&]{return f.server->stopping.load();});check(!stopped,"Stop bypassed running task callback");
  f.state.releaseTask=true;stopper.join();check(stopped&&f.server->taskCount==0,"task stop leaked callback");
 }
 {
  Fixture f;fakewin::failPost=true;
  check(!L1Interop_PostIOCallback(f.server,54)&&f.server->taskCount==0,"immediate task post failure leaked registration");
  L1Interop_SetOnIOCallback(f.listener,failedTaskCallback);
  L1Interop_SetOnIOCallback(f.listener,taskCallback);
  for(unsigned i=1;i<=100;++i)check(L1Interop_ScheduleTimer(f.server,40000+i*4,1),"dispatch warmup");
  until([&]{std::lock_guard<std::mutex> lock(f.state.mutex);return f.state.taskTokens.size()==100;});
  L1Interop_SetOnIOCallback(f.listener,failedTaskCallback);
  check(L1Interop_ScheduleTimer(f.server,55+1,100),"scheduled failure setup");fakewin::failPost=true;
  until([&]{return taskPostFailures.load()==1;});until([&]{return f.server->taskCount==0;});
 }
 std::cout<<"PASS immediate/scheduled PostQueuedCompletionStatus failure balances references and reports scheduled error on worker\n";
 std::cout<<"PASS 512 native work callbacks, scheduled expiry, duplicate rejection, 1000 cancels, shutdown cancellation and callback fence\n";
}
struct TimerOwner final : CIOObject {
 unsigned token;std::atomic<unsigned>& destroyed;
 TimerOwner(unsigned t,std::atomic<unsigned>& d):token(t),destroyed(d){}
 ~TimerOwner()override{++destroyed;}
 void OnIOCallback(void*,bool,unsigned,unsigned)noexcept override{}
};
static void ownershipCallback(void* raw,std::uint64_t token,int status){
 auto* server=static_cast<Server*>(raw);check(token==7000&&status==0,"ownership callback");
 check(L1Interop_ScheduleTimer(server,7001,100000),"callback timer registration");
 {std::lock_guard<std::mutex> lock(server->taskGate);check(server->tasks.at(7001)->owner==currentWorkerIndex,"callback timer left registering worker");}
 check(L1Interop_CancelTimer(server,7001),"callback timer cancellation");++probe->sent;
}
static void exactStructureTests(){
 {
  CIOBufferPool pool(4);CIOBufferPool::OwnerScope owner(pool,1);
  std::vector<CIOBuffer*> blocks;
  for(unsigned i=0;i<4;++i){blocks.push_back(pool.acquire());check(blocks.back(),"preallocated block missing");}
  check(blocks[1]->data.data()-blocks[0]->data.data()==0x2038,"pool slab is not contiguous at recovered stride");
  check(pool.allocated()==4&&!pool.acquire()&&pool.exhaustions()==1,"pool grew after exhaustion");
  for(auto* p:blocks)pool.recycle(p);
  check(pool.acquire()==blocks[0],"original FIFO ring return order changed");pool.recycle(blocks[0]);
  pool.recycle(blocks[0]);check(pool.corruptions()==1,"double return was not diagnosed");
 }
 {
  CIOBufferPool pool(10);check(!pool.initialize(1,200)&&pool.initializationFailures()>0,"startup accepted insufficient preallocation budget");
 }
 {
  std::atomic<unsigned> destroyed{0};CThreadLocalTimer wheel(0);
  for(unsigned i=1;i<=1001;++i){auto* owner=new TimerOwner(i,destroyed);check(wheel.AddTimer(owner,50,static_cast<int>(i),i),"1000-node block schedule failed");owner->release();}
  check(wheel.blocksInUse()==201,"1001st timer did not extend block chain");
  auto* first=static_cast<TimerOwner*>(wheel.takeDue(50));check(first&&first->token==1001,"original prepended overflow block order changed");first->release();
  for(unsigned i=1;i<=1000;++i){auto* due=static_cast<TimerOwner*>(wheel.takeDue(50));check(due&&due->token==i,"block array dispatch order changed");due->release();}
  check(wheel.size()==0&&wheel.blocksInUse()==200&&destroyed==1001,"timer blocks/references not returned");
 }
 {
  std::atomic<unsigned> destroyed{0};CThreadLocalTimer::SlotPool pool(200);
  {
   CThreadLocalTimer wheel(0,&pool);
   for(unsigned i=1;i<=1000;++i){auto* owner=new TimerOwner(i,destroyed);check(wheel.AddTimer(owner,50,static_cast<int>(i),i),"bounded block schedule");owner->release();}
   auto* rejected=new TimerOwner(1001,destroyed);check(!wheel.AddTimer(rejected,50,static_cast<int>(1001),1001),"block pool grew on exhaustion");rejected->release();
   for(unsigned i=1;i<=1000;i+=2){auto* cancelled=wheel.cancel(i);check(cancelled,"block cancellation missing");cancelled->release();}
   for(unsigned i=1002;i<1502;++i){auto* owner=new TimerOwner(i,destroyed);check(wheel.AddTimer(owner,50,static_cast<int>(i),i),"cancelled slot compaction failed");owner->release();}
   check(wheel.blocksInUse()==200&&wheel.size()==1000,"cancelled holes leaked blocks");
   while(auto* due=wheel.takeDue(50))due->release();
   check(wheel.size()==0,"compacted bucket retained timer");
  }
  check(pool.inUse()==0&&destroyed==1501,"bounded block pool/references leaked");
  CThreadLocalTimer::SlotPool tooSmall(199);try{CThreadLocalTimer failed(0,&tooSmall);check(false,"incomplete initial wheel admitted");}catch(const std::bad_alloc&){}
  check(tooSmall.inUse()==0,"partial wheel initialization leaked blocks");
 }
 {
  TimerDispatchPolicy policy;
  for(unsigned i=0;i<100;++i)check(!policy.post(0),"first100 timers not inline");
  check(policy.post(0),"101st timer not reposted");
  for(unsigned i=0;i<202;++i)check(policy.post(17),"owner17 did not post");
  check(!policy.post(0),"special-owner path changed ordinary counter");
 }
 {
  Fixture f;auto [socket,id]=f.connect();f.state.reenterClose=true;f.state.blockClosing=true;
  check(L1Interop_PauseRead(f.server,id,0),"closing test resume");
  std::thread closer([&]{CIOSocketEx_Close(f.server,id);});until([&]{return f.state.closingEntered.load();});
  {std::lock_guard<std::mutex> lock(fakewin::mutex);check(fakewin::sockets.at(socket)->open&&!fakewin::sockets.at(socket)->resetClose,"socket closed before pre-close hook returned");}
  check(f.state.closed==0&&!L1Interop_PauseRead(f.server,id,0),"terminal/admission bypassed pre-close");
  fakewin::feed(socket,{9,8,7});
  {std::lock_guard<std::mutex> lock(f.state.mutex);check(f.state.received.empty(),"closed parser delivered new data");}
  f.state.releaseClosing=true;closer.join();until([&]{return f.state.closed==1;});
  check(f.state.closing==1,"closing hook repeated");
  std::uint64_t stats[8]{};check(L1Interop_GetDiagnostics(f.server,stats,8)&&stats[6]==1&&stats[7]==1,"close diagnostics changed");
 }
 {
  Fixture f;for(unsigned i=1;i<=8;++i)check(L1Interop_ScheduleTimer(f.server,6000+i,100000),"producer timer registration");
  unsigned owner;
  {std::lock_guard<std::mutex> lock(f.server->taskGate);owner=f.server->tasks.at(6001)->owner;for(unsigned i=2;i<=8;++i)check(f.server->tasks.at(6000+i)->owner==owner,"token value changed timer owner");}
  std::thread producer([&]{check(L1Interop_ScheduleTimer(f.server,6100,100000),"second producer registration");});producer.join();
  {std::lock_guard<std::mutex> lock(f.server->taskGate);check(f.server->tasks.at(6100)->owner!=owner,"external producers did not register separate owners");}
  L1Interop_SetOnIOCallback(f.listener,ownershipCallback);check(L1Interop_PostIOCallback(f.server,7000),"owner callback post");until([&]{return f.state.sent==1;});
 }
 {
  Fixture f;auto [socket,id]=f.connect();
  until([&]{auto* c=f.server->find(id);ObjectRef hold(c,false);std::lock_guard<std::mutex> lock(c->gate);return !c->state.acceptDelivering;});
  // accepted() now also submits a serialized WRITE kick and checks terminal
  // readiness. Finish that owner before asserting a caller-thread fallback.
  until([&]{return f.server->pendingAccepts.load()==0;});
  f.state.terminalStopCheck=true;fakewin::failPost=true;
  check(CIOSocketEx_Close(f.server,id)&&f.state.closed==1,"caller-thread terminal fallback failed");
 }
 std::cout<<"PASS closing/terminal callback self-stop/wait guards, including failed close-post on a foreign caller\n";
 std::cout<<"PASS registering-worker/foreign-producer timer ownership; token values do not choose wheels\n";
 std::cout<<"PASS contiguous preallocated slab/FIFO exhaustion/corruption diagnostics, exact1000-slot blocks/overflow/compaction/rollback, owner17+101st policy and pre-close-before-OS-close fence\n";
}

static std::vector<unsigned char> l1Frame(unsigned size,unsigned seed=1) {
 std::vector<unsigned char> frame(size);frame[0]=size&255;frame[1]=(size>>8)&255;
 for(unsigned i=2;i<size;++i)frame[i]=static_cast<unsigned char>(i*seed+17);return frame;
}
static void userSocketTests() {
 for(unsigned chunk : {1u,2u,3u,7u,4096u,8192u,65535u}) {
  CUserSocketParser parser;std::vector<unsigned char> wire;std::vector<std::vector<unsigned char>> expected{l1Frame(2),l1Frame(10),l1Frame(65535,3),l1Frame(112,7)},seen;
  for(const auto& frame:expected)wire.insert(wire.end(),frame.begin(),frame.end());
  for(std::size_t at=0;at<wire.size();at+=chunk)check(parser.feed(wire.data()+at,std::min<std::size_t>(chunk,wire.size()-at),10,[&](auto* p,unsigned n){seen.emplace_back(p,p+n);return true;}),"fragmented L1 parser rejected valid bytes");
  check(seen==expected,"fragmented/coalesced/max-size L1 frames changed bytes/order");
  std::uint64_t stats[6]{};parser.snapshot(stats,100);check(stats[0]==CUserSocketParser::Header&&stats[3]==4&&stats[5]==0,"parser did not return to header state");
 }
 for(unsigned size : {0u,1u,3u,4u,5u,6u,7u,8u,9u}) {
  CUserSocketParser parser;unsigned callbacks=0;unsigned char bytes[]{static_cast<unsigned char>(size),0};
  check(!parser.feed(bytes,2,0,[&](auto*,unsigned){++callbacks;return true;})&&callbacks==0,"invalid L1 wire length reached callback");
  std::uint64_t stats[6]{};parser.snapshot(stats,100);check(stats[0]==2&&stats[4]==1,"invalid length did not stop parser");
 }
 {
  CUserSocketParser parser;auto frame=l1Frame(10);
  check(parser.feed(frame.data(),1,10,[](auto*,unsigned){return true;}),"partial header");
  check(!parser.expired(30009)&&parser.expired(30010),"partial header deadline");
  parser.pause(true,110);check(!parser.expired(100000),"backpressure pause expired partial packet");
  parser.pause(false,100110);check(!parser.expired(130009)&&parser.expired(130010),"pause did not shift partial deadline");
  check(parser.feed(frame.data()+1,9,130010,[](auto*,unsigned){return true;})&&!parser.expired(200000),"complete frame retained deadline");
 }
 {
  CUserSocketParser parser;auto frame=l1Frame(10);std::atomic<bool>entered{false},release{false};
  auto owner=std::async(std::launch::async,[&]{return parser.feed(frame.data(),frame.size(),1,[&](auto* p,unsigned n){entered=true;while(!release)std::this_thread::yield();check(n==10&&std::equal(p,p+n,frame.data()),"close freed callback frame");return true;});});
  until([&]{return entered.load();});parser.stop();release=true;check(!owner.get(),"close did not stop parser after callback");
 }
 {
  Fixture f;f.state.userProfile=true;auto [socket,id]=f.connect();
  check(L1Interop_GetThreadOwner(f.server)==-1,"external caller impersonated native worker");
  check(L1Interop_PauseRead(f.server,id,0),"user profile unpause");auto frame=l1Frame(10000,9);
  fakewin::feed(socket,{frame[0]});
  until([&]{std::uint64_t st[6]{};return L1Interop_GetSocketState(f.server,id,st,6)&&st[0]==0&&st[2]==1;});
  {std::lock_guard<std::mutex>lock(f.state.mutex);check(f.state.received.empty(),"partial header leaked into raw gameplay callback");}
  fakewin::feed(socket,std::vector<unsigned char>(frame.begin()+1,frame.end()));
  until([&]{std::lock_guard<std::mutex>lock(f.state.mutex);return f.state.received.size()==frame.size()-6;});
  {std::lock_guard<std::mutex>lock(f.state.mutex);check(f.state.received==std::vector<unsigned char>(frame.begin()+6,frame.end())&&f.state.receiveLengths==std::vector<int>{9994},"production user socket did not emit complete plaintext");}
  fakewin::feed(socket,{3,0});until([&]{return f.state.closed==1;});
 }
 std::cout<<"PASS L1 CUserSocketParser: bytewise/coalesced/65535-byte framing, empty envelope, invalid lengths, header/body deadlines with backpressure, concurrent close, production connection integration\n";
}

struct RegistryObject {
 std::uint64_t id=0;std::atomic<int> refs{1};
 void retain(){++refs;}void release(){check(refs.fetch_sub(1)>0,"registry double release");}
};
static void managerAndNativeLeaseTests() {
 {
  CUserSocketManager<RegistryObject> registry;check(registry.initialize(1),"registry init");
  RegistryObject a,b;auto old=registry.insert(&a);check(old&&a.refs==2,"registry insertion hold");
  auto* found=registry.find(old);check(found==&a,"registry lookup");found->release();
  registry.retire(old,&a);check(!registry.find(old),"retired handle admitted new work");
  found=registry.find(old,true);check(found==&a,"retired release lookup lost");found->release();
  check(!registry.remove(old,&b)&&registry.remove(old,&a)&&a.refs==1,"registry exact erase");
  auto current=registry.insert(&b);check(current!=old&&std::uint32_t(current)==std::uint32_t(old)&&!registry.find(old,true),"slot generation ABA");
  check(registry.remove(current,&b)&&registry.initialize(1),"registry restart");
  auto restarted=registry.insert(&a);check(restarted!=current&&restarted!=old,"restart reused generation");check(registry.remove(restarted,&a),"restart erase");
  check(registry.initialize(64),"concurrent registry init");
  std::vector<std::thread> threads;
  for(int worker=0;worker<8;++worker)threads.emplace_back([&]{for(int n=0;n<400;++n){RegistryObject object;auto id=registry.insert(&object);check(id!=0,"concurrent insert");auto* copy=registry.find(id);check(copy==&object,"concurrent lookup");copy->release();registry.retire(id,&object);check(registry.remove(id,&object)&&object.refs==1,"concurrent retire release");}});
  for(auto& t:threads)t.join();std::uint64_t diag[6]{};registry.diagnostics(diag);check(diag[0]==0&&diag[1]==0&&diag[3]==diag[4],"registry totals leaked");
 }
 {
  Fixture f;f.state.userProfile=true;f.state.holdPacket=true;auto [socket,id]=f.connect();check(L1Interop_PauseRead(f.server,id,0),"lease read resume");
  auto first=l1Frame(4096,3),second=l1Frame(8192,7);std::vector<unsigned char> wire=first;wire.insert(wire.end(),second.begin(),second.end());fakewin::feed(socket,wire);
  until([&]{return f.state.heldSequence.load()!=0;});const auto seq=f.state.heldSequence.load();auto* view=f.state.heldPlaintext.load();
  check(std::equal(view,view+first.size()-6,first.data()+6),"leased plaintext changed");
  unsigned char outbound[]{1,2,3,4};check(CSocket_Encrypt(f.server,id,outbound,4)&&outbound[0]==(1^0xa7),"native outbound codec");
  check(std::equal(view,view+first.size()-6,first.data()+6)&&f.state.packetCalls==1,"send/next overwrote borrowed plaintext");
  check(!L1Interop_ReleasePacket(f.server,id,seq+1),"wrong sequence released packet");
  check(L1Interop_ReleasePacket(f.server,id,seq),"foreign caller lease release");until([&]{return f.state.packetCalls==2;});
  check(!L1Interop_ReleasePacket(f.server,id,seq),"duplicate packet release");
 }
 {
  Fixture f;f.state.userProfile=true;f.state.holdPacket=true;auto [socket,id]=f.connect();L1Interop_PauseRead(f.server,id,0);auto frame=l1Frame(128,5);fakewin::feed(socket,frame);until([&]{return f.state.heldSequence.load()!=0;});
  const auto seq=f.state.heldSequence.load();auto* view=f.state.heldPlaintext.load();check(CIOSocketEx_Close(f.server,id),"close held lease");until([&]{return f.state.closed==1;});
  std::uint64_t diag[6]{};L1Interop_GetRegistryDiagnostics(f.server,diag,6);check(diag[0]==0&&diag[1]==1,"terminal dropped borrowed native context");
  check(std::equal(view,view+frame.size()-6,frame.data()+6),"terminal invalidated outstanding plaintext");
  check(L1Interop_ReleasePacket(f.server,id,seq),"retired native lease release");until([&]{return f.server->connections.load()==0;});
  auto [newSocket,newId]=f.connect();check(newId!=id&&std::uint32_t(newId)==std::uint32_t(id),"production registry did not rotate generation");
  check(!L1Interop_ReleasePacket(f.server,id,seq)&&!CIOSocket__Write(f.server,id,frame.data(),frame.size()),"stale generation affected new socket");
 }
 {
  Fixture f;f.state.userProfile=true;auto [socket,id]=f.connect();check(L1Interop_ConfigureDeadlines(f.server,id,60),"short login timer");until([&]{return f.state.closed==1;});until([&]{return f.server->taskCount==0;});
 }
 {
  Fixture f;f.state.userProfile=true;auto [socket,id]=f.connect();check(L1Interop_ConfigureDeadlines(f.server,id,80)&&L1Interop_SetSessionState(f.server,id,1),"authenticated login timer");
  until([&]{return f.state.deadlineCalls.load()>0;});check(f.state.closed==0,"authenticated connection expired login timer");
  auto calls=f.state.deadlineCalls.load();check(L1Interop_RequestDeadline(f.server,id,1),"earlier application deadline");until([&]{return f.state.deadlineCalls.load()>calls;});
  CIOSocketEx_Close(f.server,id);until([&]{return f.state.closed==1&&f.server->taskCount==0;});
 }
 {
  Fixture f;f.state.userProfile=true;auto [socket,id]=f.connect();auto* c=f.server->find(id);check(c!=nullptr,"partial deadline lookup");
  {std::lock_guard<std::mutex> lock(c->gate);unsigned char partial=10;check(c->UserProtocol()->feed(&partial,1,monotonicMs()-30001,[](auto*,unsigned){return true;}),"old partial header");}
  c->release();check(L1Interop_RequestDeadline(f.server,id,1),"partial native deadline");until([&]{return f.state.closed==1;});
 }
 {
  Fixture f;f.state.userProfile=true;f.state.holdPacket=true;auto [socket,id]=f.connect();L1Interop_PauseRead(f.server,id,0);fakewin::feed(socket,l1Frame(128));until([&]{return f.state.heldSequence.load()!=0;});
  auto stop=std::async(std::launch::async,[&]{return L1Interop_StopServer(f.server);});until([&]{return f.state.closed==1;});check(stop.wait_for(std::chrono::milliseconds(20))==std::future_status::timeout,"stop bypassed native packet lease");
  check(L1Interop_ReleasePacket(f.server,id,f.state.heldSequence),"shutdown drain release rejected");check(stop.get(),"stop after lease release");
 }
 check(fixturecodec::created==fixturecodec::destroyed,"native codec instances leaked");
 std::cout<<"PASS native manager: generation/retired lookup/exact erase/restart/3200 concurrent registrations; stale handles cannot affect replacements\n";
 std::cout<<"PASS direct native codec: stable plaintext lease across send/queued packets/IO terminal; foreign release, wrong/duplicate sequence, shutdown drain and exact codec destruction\n";
 std::cout<<"PASS native network timers: login expiry, authenticated cancellation, earlier coalesced application deadline, partial header expiry and close cancellation\n";
}


struct SerialProbe final : CIOObject {
 CExecutionSerializerFast queue;
 std::atomic<unsigned> calls{0},inside{0},maximum{0};
 std::atomic<bool> entered{false},releaseBlocked{false};
 static void Work(MemoryObject* object,void*)noexcept{
  auto& p=*static_cast<SerialProbe*>(object);const auto active=p.inside.fetch_add(1)+1;
  auto old=p.maximum.load();while(old<active&&!p.maximum.compare_exchange_weak(old,active)){}
  ++p.calls;--p.inside;
 }
 static void Reenter(MemoryObject* object,void*)noexcept{
  auto& p=*static_cast<SerialProbe*>(object);++p.calls;
  check(p.queue.AddExecutable(object,Work,nullptr),"same-owner serializer reentry");
 }
 static void Block(MemoryObject* object,void*)noexcept{
  auto& p=*static_cast<SerialProbe*>(object);p.entered=true;
  while(!p.releaseBlocked.load())std::this_thread::yield();++p.calls;
 }
 void OnIOCallback(void*,bool,unsigned,unsigned)noexcept override{}
};
static void originalStructureTests(){
 {
  auto* p=new SerialProbe;
  std::vector<std::thread> producers;
  for(unsigned i=0;i<8;++i)producers.emplace_back([&]{for(unsigned n=0;n<1000;++n){
   while(!p->queue.AddExecutable(p,SerialProbe::Work,nullptr))std::this_thread::yield();
  }});
  for(auto& t:producers)t.join();
  check(p->calls==8000&&p->maximum==1&&p->queue.Pending()==0,"serializer lost/doubled/concurrent callback");
  check(p->queue.AddExecutable(p,SerialProbe::Reenter,nullptr)&&p->calls==8002,"serializer reentry did not execute");
  std::thread blocked([&]{check(p->queue.AddExecutable(p,SerialProbe::Block,nullptr),"serializer blocked owner");});
  until([&]{return p->entered.load();});
  for(unsigned n=0;n<503;++n)check(p->queue.AddExecutable(p,SerialProbe::Work,nullptr),"serializer rejected within original margin");
  check(!p->queue.AddExecutable(p,SerialProbe::Work,nullptr)&&p->queue.Pending()==504,"serializer overflow did not preserve eight spare slots");
  p->releaseBlocked=true;blocked.join();check(p->queue.Pending()==0&&p->calls==8506,"serializer overflow rollback/drain");p->release();
 }
 {
  struct WaitObject final:CIOServer {
   bool* destroyed;explicit WaitObject(bool* value):destroyed(value){}
   ~WaitObject()override{*destroyed=true;}
   void NotifyPendingChanged()noexcept override{}void OnAcceptedSocket(SOCKET)noexcept override{}
   void OnWaitFailure(unsigned)noexcept override{}
  };
  WaitThread wait;std::array<bool,65> destroyed{};std::array<WaitObject*,65> objects{};std::array<HANDLE,65> events{};
  for(unsigned n=0;n<65;++n){objects[n]=new WaitObject(&destroyed[n]);events[n]=WSACreateEvent();
   check(wait.RegisterWait(objects[n],events[n])==(n<64),"RegisterWait bounded64 admission");objects[n]->release();}
  check(destroyed[64],"failed RegisterWait retained object");
  for(unsigned n=0;n<64;++n){check(!destroyed[n]&&wait.UnregisterWait(objects[n])&&destroyed[n],"UnregisterWait reference balance");}
  for(auto event:events)WSACloseEvent(event);
 }
 {
  Fixture f;std::vector<SOCKET> clients;
  for(unsigned n=0;n<48;++n)clients.push_back(fakewin::connect(f.server->listening));
  until([&]{std::lock_guard<std::mutex> lock(f.state.mutex);return f.state.ids.size()==48;});
  std::uint64_t listener[9]{},threads[54]{};
  check(L1Interop_GetListenerDiagnostics(f.server,listener,9)&&listener[1]>=48&&listener[2]>=48&&listener[6]==48&&listener[8]==48,"burst rearm/listener/raw registry");
  check(L1Interop_GetThreadDiagnostics(f.server,threads,54)&&threads[51]>0,"ListenThread owner17 did not run");
  std::uint64_t rows[48*12]{};check(Server_ReportSocket(f.server,rows,48,12)==48,"ReportSocket snapshot size");
  for(unsigned n=0;n<48;++n)check(rows[n*12+1]&&L1Interop_FindSocketByNumber(f.server,rows[n*12+1])==rows[n*12],"user-number secondary lookup");
  check(CIOServer_Close(f.server),"stop accepting burst");
  check(L1Interop_StopServer(f.server),"shutdown raw SOCKET snapshot");
  check(L1Interop_GetListenerDiagnostics(f.server,listener,9)&&listener[5]==0&&listener[6]==0&&listener[8]==0,"listener/secondary/raw registration leaked");
 }
 {
  Fixture f(2,1);auto [a,idA]=f.connect();auto [b,idB]=f.connect();
  check(L1Interop_BindMacIdentity(f.server,idA,0x123456789abcull),"MAC identity reservation");
  check(!L1Interop_BindMacIdentity(f.server,idA,0x123456789abcull)&&!L1Interop_BindMacIdentity(f.server,idB,0x123456789abcull),"duplicate/overlimit MAC identity accepted");
  const auto callbacks=f.server->acceptCallbacks.load();fakewin::connect(f.server->listening);
  until([&]{return f.server->acceptCallbacks.load()>callbacks&&f.server->pendingAccepts==0;});
  {std::lock_guard<std::mutex> lock(f.state.mutex);check(f.state.ids.size()==2,"native IP limiter admitted third socket");}
  auto* socket=f.server->find(idA);auto number=socket->SocketNumber();socket->release();
  check(CIOSocketEx_Close(f.server,idA),"limited socket close");until([&]{return f.state.closed==1;});
  check(!L1Interop_FindSocketByNumber(f.server,number),"retired number still active");
  check(L1Interop_BindMacIdentity(f.server,idB,0x123456789abcull),"MAC reservation not returned on close");
  auto [c,idC]=f.connect();check(idC!=idA,"replacement generation reused");
  check(!L1Interop_BindMacIdentity(f.server,idA,7),"stale handle changed limiter");
  check(L1Interop_StopServer(f.server),"limited shutdown");std::uint64_t counts[4]{};
  check(L1Interop_GetSocketLimitDiagnostics(f.server,counts,4)&&counts[0]==0&&counts[1]==0,"IP/MAC close accounting leaked");
 }
 std::cout<<"PASS original structure: serializer 8000 concurrent+reentry+504 capacity; RegisterWait64; 48 event accepts; owner17; raw/secondary/generation maps; native IP/MAC rejection and release; ReportSocket\n";
}


static void remainingNetworkTests(){
 {
  Fixture f;f.state.userProfile=true;auto [a,idA]=f.connect();auto [b,idB]=f.connect();
  check(!L1Interop_SetEnterChar(f.server,idA,1),"character entered without authenticated account");
  check(L1Interop_BindAccount(f.server,idA,77),"bind account A");
  check(L1Interop_BindAccount(f.server,idA,77)&&!L1Interop_BindAccount(f.server,idA,78),"account rebinding was not fenced");
  check(L1Interop_BindAccount(f.server,idB,77),"bind account B");
  check(L1Interop_SetEnterChar(f.server,idA,1),"claim account enter map");
  check(!L1Interop_SetEnterChar(f.server,idB,1)&&L1Interop_FindEnteringAccount(f.server,77)==idA,"duplicate account enter displaced live owner");
  std::uint64_t counts[4]{},state[14]{};check(L1Interop_GetAccountRegistryDiagnostics(f.server,counts,4)&&counts[0]==2&&counts[1]==2&&counts[2]==1&&counts[3]==1,"26/27/28 counts or keys");
  check(L1Interop_GetSocketState(f.server,idA,state,14)&&state[11]==77&&state[12]==1,"account state snapshot");
  check(L1Interop_SetSessionState(f.server,idA,1)&&!L1Interop_FindEnteringAccount(f.server,77),"restart did not release enter map");
  check(L1Interop_SetEnterChar(f.server,idB,1),"restart account slot not reusable");
  check(CIOSocketEx_Close(f.server,idA),"old account close");until([&]{return f.state.closed==1;});
  check(L1Interop_FindEnteringAccount(f.server,77)==idB,"old close removed replacement account entry");
  f.state.holdPacket=true;L1Interop_PauseRead(f.server,idB,0);fakewin::feed(b,l1Frame(128));until([&]{return f.state.heldSequence.load()!=0;});
  check(CIOSocketEx_Close(f.server,idB),"held account close");until([&]{return f.state.closed==2;});
  check(L1Interop_GetAccountRegistryDiagnostics(f.server,counts,4)&&counts[0]==1&&counts[1]==0&&counts[2]==0,"close-start kept game identities or prematurely dropped packet owner");
  check(L1Interop_ReleasePacket(f.server,idB,f.state.heldSequence),"retired account packet release");until([&]{return f.server->connections==0;});
 }
 {
  Fixture f;std::vector<std::uint64_t> ids;
  for(unsigned i=0;i<16;++i){auto [socket,id]=f.connect();ids.push_back(id);check(L1Interop_BindAccount(f.server,id,900),"contender account bind");}
  std::atomic<unsigned> winners{0};std::vector<std::thread> contenders;
  for(auto id:ids)contenders.emplace_back([&,id]{if(L1Interop_SetEnterChar(f.server,id,1))++winners;});
  for(auto& t:contenders)t.join();check(winners==1,"parallel duplicate character entries both won");
  for(auto id:ids)CIOSocketEx_Close(f.server,id);until([&]{return f.server->connections==0;});
  std::uint64_t counts[4]{};L1Interop_GetAccountRegistryDiagnostics(f.server,counts,4);check(counts[0]==0&&counts[1]==0&&counts[2]==0,"contending account maps leaked");
 }
 {
  Fixture f(0,1);auto [a,idA]=f.connect();auto [b,idB]=f.connect();
  check(!L1Interop_BindAccount(f.server,idA,1),"MAC-enabled authentication admitted unidentified client");
  check(L1Interop_BindMacIdentity(f.server,idA,0xaabbccddeeffull)&&L1Interop_BindAccount(f.server,idA,1),"verified MAC authentication failed");
  check(!L1Interop_BindMacIdentity(f.server,idB,0xaabbccddeeffull),"MAC cap ignored");
  CIOSocketEx_Close(f.server,idA);until([&]{return f.state.closed==1;});
  check(L1Interop_BindMacIdentity(f.server,idB,0xaabbccddeeffull)&&L1Interop_BindAccount(f.server,idB,2),"closed MAC capacity not returned");
 }
 {
  Fixture f;std::uint64_t sample[16]{};
  until([&]{return L1Interop_ReadPerf(f.server,sample,16)&&sample[0]>=2;});
  check(sample[2]==15&&sample[1]>0&&sample[15]==0,"native CPerfMon TLS identity or fixture OS availability");
  auto old=sample[0];std::atomic<bool> done{false};std::thread reader([&]{std::uint64_t values[16];while(!done)L1Interop_ReadPerf(f.server,values,16);});
  check(L1Interop_StopServer(f.server),"stop with concurrent performance read");done=true;reader.join();
  check(!L1Interop_ReadPerf(f.server,sample,16),"stopped monitor remained published");
  check(Server_Run(f.server,"127.0.0.1",0),"restart after performance stop");until([&]{return L1Interop_ReadPerf(f.server,sample,16)&&sample[0]>0;});
 }
 {
  Fixture f;f.state.userProfile=true;
  for(unsigned cycle=0;cycle<128;++cycle){auto [socket,id]=f.connect();check(L1Interop_BindAccount(f.server,id,123)&&L1Interop_SetEnterChar(f.server,id,1),"repeat account reuse admission");
   check(L1Interop_SetSessionState(f.server,id,0),"logout identity clear");
   check(!L1Interop_FindEnteringAccount(f.server,123),"logout retained entering account");
   CIOSocketEx_Close(f.server,id);until([&]{return f.server->connections==0;});
  }
 }
 std::cout<<"PASS remaining network: original Playing(socket number)/EnterChar(account ID); authentication/enter/restart/logout/close;16-way duplicate entry;held plaintext/stale close;MAC-required auth/cap/reuse;actual CPerfMon owner15/restart/concurrent read-stop;128 account reconnects\n";
}


static std::vector<unsigned char> outboundFrame(unsigned n,unsigned seed=3){
 std::vector<unsigned char> out(n);out[0]=static_cast<unsigned char>(n);out[1]=static_cast<unsigned char>(n>>8);
 for(unsigned i=2;i<n;++i)out[i]=static_cast<unsigned char>(i*seed+7);return out;
}
static void sendCloseTests(){
 {
  // The original fast serializer accepts MemoryObject, not only I/O objects.
  struct MemoryOnly final:MemoryObject{
   unsigned& calls;bool& destroyed;MemoryOnly(unsigned& c,bool& d):calls(c),destroyed(d){}
   ~MemoryOnly()override{destroyed=true;}
   static void Execute(MemoryObject* object,void*)noexcept{++static_cast<MemoryOnly*>(object)->calls;}
  };
  unsigned calls=0;bool destroyed=false;CExecutionSerializerFast execution;auto* object=new MemoryOnly(calls,destroyed);
  check(execution.AddExecutable(object,MemoryOnly::Execute,nullptr)&&calls==1,"MemoryObject serializer admission");
  object->release();check(destroyed,"MemoryObject virtual release did not destroy derived object");
 }
 {
  Fixture f;f.state.userProfile=true;auto [socket,id]=f.connect();auto* c=f.server->find(id);ObjectRef socketHold(c,false);
  std::vector<unsigned char> source;for(unsigned size:{16383u,10u,65535u,8u}){auto frame=outboundFrame(size,size);source.insert(source.end(),frame.begin(),frame.end());}
  auto expected=source;std::size_t at=0;while(at<source.size()){unsigned size=source[at]|(source[at+1]<<8);for(unsigned i=2;i<size;++i)expected[at+i]^=0xa7;at+=size;}
  Span input{source.data(),source.size()};auto* request=new NativeSendRequest(c,&input,1,source.size(),true,false,true);ObjectRef requestHold(request,false);
  {std::lock_guard<std::mutex> lock(c->gate);c->reservedSendBytes+=source.size();++c->pendingSendRequests;++f.server->pendingSends;}
  check(c->execution_.AddExecutable(request,NativeSendRequest::Execute,nullptr),"constant request owner");
  until([&]{return request->result.load()!=0;});check(request->result==1&&request->bytes==source,"constant SEND modified its source");
  until([&]{return f.state.sent==static_cast<int>(source.size());});check(fakewin::wire(socket)==expected,"constant frame/header split or max-frame encryption mismatch");
 }
 std::cout<<"PASS original MemoryObject-only fast-serializer target and virtual destruction;CSocket constant source remains unchanged across16K headers/max65535 frames;pooled encrypted output adopts without re-copy\n";
 {
  Fixture f;f.state.userProfile=true;auto [socket,id]=f.connect();
  auto plain=outboundFrame(11),encrypted=outboundFrame(65535,9);std::vector<unsigned char> wire=plain;wire.insert(wire.end(),encrypted.begin(),encrypted.end());auto expected=wire;
  for(std::size_t i=plain.size()+2;i<expected.size();++i)expected[i]^=0xa7;
  auto* c=f.server->find(id);auto* blocker=new SerialProbe;
  std::thread owner([&]{check(c->execution_.AddExecutable(blocker,SerialProbe::Block,nullptr),"block native send owner");});until([&]{return blocker->entered.load();});
  check(CSocket_SendV(f.server,id,wire.data(),wire.size(),1),"queued native frame send");std::fill(wire.begin(),wire.end(),0);
  check(CIOSocketEx_GracefulClose(f.server,id,5000),"queued graceful close");unsigned char late=9;check(!CIOSocket__Write(f.server,id,&late,1),"send admitted after graceful fence");
  check(f.state.closed==0,"graceful close bypassed unpublished send request");blocker->releaseBlocked=true;owner.join();blocker->release();c->release();
  until([&]{return fakewin::sendShutdown(socket);});check(f.state.closed==0,"normal close did not await peer FIN");fakewin::peerFin(socket);
  until([&]{return f.state.closed==1;});check(fakewin::wire(socket)==expected,"copy/handshake/frame cipher or graceful prefix lost");
  {std::lock_guard<std::mutex> lock(fakewin::mutex);check(!fakewin::sockets.at(socket)->resetClose,"GracefulClose applied RST linger");}
  std::uint64_t diag[9]{};check(L1Interop_GetSendDiagnostics(f.server,diag,9)&&diag[0]==1&&diag[1]==1&&diag[2]==1&&diag[3]==1&&diag[4]==1&&diag[5]==0&&diag[7]==0&&diag[8]==0,"native send owner/normal close diagnostics");
 }
 {
  Fixture f;f.state.userProfile=true;auto [socket,id]=f.connect();
  unsigned char bad[]{12,0,1,2};const auto before=fixturecodec::encrypted.load();
  check(!CSocket_SendV(f.server,id,bad,sizeof(bad),0)&&fixturecodec::encrypted==before,"invalid frame advanced cipher");
  auto bytes=outboundFrame(32769);auto expected=bytes;for(std::size_t i=2;i<bytes.size();++i)expected[i]^=0xa7;
  WSABUF parts[]={{1,reinterpret_cast<char*>(bytes.data())},{4,reinterpret_cast<char*>(bytes.data()+1)},{static_cast<ULONG>(bytes.size()-5),reinterpret_cast<char*>(bytes.data()+5)}};
  fakewin::sendChunk=37;check(CSocket___async_AsyncSendConstBuffer(f.server,id,parts,3,0),"cross-descriptor frame admission");
  until([&]{return f.state.sent==static_cast<int>(bytes.size());});check(fakewin::wire(socket)==expected&&fixturecodec::encrypted==before+1,"partial completion re-encrypted or split frame lost");fakewin::sendChunk=8192;
 }
 {
  Fixture f;f.state.userProfile=true;auto [socket,id]=f.connect();auto bytes=outboundFrame(10);
  fixturecodec::failEncrypt=true;CSocket_SendV(f.server,id,bytes.data(),bytes.size(),0);
  until([&]{return f.state.closed==1;});check(fakewin::wire(socket).empty(),"failed cipher data reached wire");
 }
 {
  Fixture f;auto [socket,id]=f.connect();fakewin::holdSend=true;
  std::vector<unsigned char> data(20000,19);check(CIOSocket__Write(f.server,id,data.data(),data.size()),"held raw send");
  check(CIOSocketEx_GracefulClose(f.server,id,5000)&&f.state.closed==0,"graceful close did not await pending WRITE");
  fakewin::peerFin(socket);check(f.state.closed==0,"early peer FIN bypassed pending WRITE");
  until([&]{fakewin::flush(socket);return f.state.closed==1;});check(fakewin::sendShutdown(socket)&&fakewin::wire(socket)==data&&f.state.sent==20000,"graceful pending WRITE lost prefix");
  {std::lock_guard<std::mutex> lock(fakewin::mutex);check(!fakewin::sockets.at(socket)->resetClose,"raw graceful used abortive close");}
  fakewin::holdSend=false;
 }
 {
  Fixture f;auto [socket,id]=f.connect();fakewin::holdSend=true;unsigned char data[100]{};
  check(CIOSocket__Write(f.server,id,data,100)&&CIOSocketEx_GracefulClose(f.server,id,60),"short graceful deadline");
  until([&]{return f.state.closed==1;});
  {std::lock_guard<std::mutex> lock(fakewin::mutex);check(fakewin::sockets.at(socket)->resetClose,"expired graceful close did not force cancellation");}
  fakewin::holdSend=false;std::uint64_t d[9]{};L1Interop_GetSendDiagnostics(f.server,d,9);check(d[4]==0&&d[5]==1,"grace deadline close mode");
 }
 {
  Fixture f;auto [socket,id]=f.connect();fakewin::holdSend=true;unsigned char data[100]{};check(CIOSocket__Write(f.server,id,data,100),"force override send");
  check(CIOSocketEx_GracefulClose(f.server,id,30000)&&CIOSocketEx_Close(f.server,id),"grace/force compatibility API");until([&]{return f.state.closed==1;});fakewin::holdSend=false;
  std::uint64_t d[9]{};L1Interop_GetSendDiagnostics(f.server,d,9);check(d[5]==1&&d[4]==0,"force did not override draining graceful close");
 }
 {
  Fixture f;auto [socket,id]=f.connect();check(CIOSocketEx_GracefulClose(f.server,id,60),"peer FIN deadline admission");
  until([&]{return fakewin::sendShutdown(socket);});check(f.state.closed==0,"half-close did not wait for peer");
  until([&]{return f.state.closed==1;});std::uint64_t d[9]{};L1Interop_GetSendDiagnostics(f.server,d,9);check(d[5]==1&&d[4]==0,"missing FIN deadline did not force close");
 }
 std::cout<<"PASS native SEND/close:shared execution owner;copied queued source;plain first/max65535 frame;cross-descriptor frames;37-byte completions without re-encryption;invalid/cipher failure;graceful pending-send/WRITE drain,SD_SEND and peer FIN without RST;WRITE/peer-FIN deadlines and forced override\n";
}
static void sharedBroadcastTests(){
 {
  Fixture f;f.state.userProfile=true;auto a=f.connect(),b=f.connect();
  auto input=outboundFrame(16384,11),expected=input;for(unsigned i=2;i<expected.size();++i)expected[i]^=0xa7;
  auto* source=static_cast<L1ConstBuffer*>(L1Interop_CreateConstBuffer(f.server,input.data(),input.size()));check(source,"create shared source");
  auto* block=source->block;CIOBufferPool::retain(block);
  auto* ca=f.server->find(a.second);ObjectRef ah(ca,false);auto* cb=f.server->find(b.second);ObjectRef bh(cb,false);
  auto* x=new SerialProbe;auto* y=new SerialProbe;
  std::thread first([&]{check(ca->execution_.AddExecutable(x,SerialProbe::Block,nullptr),"shared first owner");});
  std::thread second([&]{check(cb->execution_.AddExecutable(y,SerialProbe::Block,nullptr),"shared second owner");});
  until([&]{return x->entered&&y->entered;});
  check(L1Interop_SendConstBuffer(f.server,a.second,source,0)&&L1Interop_SendConstBuffer(f.server,b.second,source,0),"shared queued sends");
  check(block->references==4,"shared buffer lacks producer/inspection/two recipient references");
  L1Interop_ReleaseConstBuffer(source);check(block->references==3,"producer release lost recipient ownership");
  std::fill(input.begin(),input.end(),0);check(CIOSocketEx_Close(f.server,b.second),"shared recipient close");
  x->releaseBlocked=true;y->releaseBlocked=true;first.join();second.join();x->release();y->release();
  until([&]{return f.state.sent==static_cast<int>(expected.size())&&f.state.closed==1&&block->references==1;});
  check(fakewin::wire(a.first)==expected&&fakewin::wire(b.first).empty(),"shared partial/cancel changed another recipient");
  for(unsigned i=0;i<expected.size();++i)check(block->data.data()[i]==(i<2?expected[i]:static_cast<unsigned char>(expected[i]^0xa7)),"shared plaintext was mutated");
  check(f.server->pendingSends==0&&f.server->sendOwnerErrors==0,"shared send accounting stranded");f.server->buffers.recycle(block);
 }
 {
  Fixture f;f.state.userProfile=true;auto pair=f.connect();auto input=outboundFrame(10);
  check(!L1Interop_CreateConstBuffer(f.server,input.data(),9),"bad shared frame accepted");
  auto* source=static_cast<L1ConstBuffer*>(L1Interop_CreateConstBuffer(f.server,input.data(),input.size()));check(source,"bounded source");
  auto* c=f.server->find(pair.second);ObjectRef hold(c,false);auto* blocker=new SerialProbe;
  std::thread owner([&]{check(c->execution_.AddExecutable(blocker,SerialProbe::Block,nullptr),"shared saturation owner");});until([&]{return blocker->entered.load();});
  for(unsigned i=0;i<503;++i)check(L1Interop_SendConstBuffer(f.server,pair.second,source,0),"shared queued capacity");
  auto references=source->block->references.load();auto pending=f.server->pendingSends.load();
  check(!L1Interop_SendConstBuffer(f.server,pair.second,source,0)&&source->block->references==references&&f.server->pendingSends==pending,"shared overflow leaked reference/reservation");
  check(CIOSocketEx_Close(f.server,pair.second),"shared saturated close");blocker->releaseBlocked=true;owner.join();blocker->release();
  until([&]{return f.state.closed==1&&source->block->references==1;});check(f.server->pendingSends==0,"cancelled shared queue leaked count");
  L1Interop_ReleaseConstBuffer(source);
 }
 {
  void* held=nullptr;
  {Fixture f;auto input=outboundFrame(15);held=L1Interop_CreateConstBuffer(f.server,input.data(),input.size());check(held,"shutdown producer handle");}
  L1Interop_ReleaseConstBuffer(held); // block and server remain alive after Stop/ReleaseServer
 }
 std::cout<<"PASS shared const broadcast: one frozen16K source/two queued recipients;producer released before execution;close isolation;unchanged plaintext;504-slot overflow rolls back refs/reservations;503 queued cancellations;source outlives server shutdown\n";
}
int main(){std::cout.setf(std::ios::unitbuf);sharedBroadcastTests();sendCloseTests();remainingNetworkTests();originalStructureTests();userSocketTests();managerAndNativeLeaseTests();coreTests();batchTests();engineTests();recoveredStructureTests();exactStructureTests();std::cout<<"PASS all native tests. Windows completion APIs are fixtures, not the Windows kernel.\n";}
