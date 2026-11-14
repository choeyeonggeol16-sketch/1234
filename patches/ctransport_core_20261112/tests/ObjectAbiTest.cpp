#ifndef _WIN32
#define CTRANSPORT_TEST
#endif
#include "../native/CTransport.cpp"
#include <cassert>
#include <iostream>
struct ObjectAbiTestAccess {
 static std::size_t refs(const MemoryObject& p){return reinterpret_cast<const char*>(&p.references_)-reinterpret_cast<const char*>(&p);}
 static std::size_t timer(const MemoryObject& p){return reinterpret_cast<const char*>(&p.inTimer_)-reinterpret_cast<const char*>(&p);}
 static std::size_t id(const MemoryObject& p){return reinterpret_cast<const char*>(&p.objectId_)-reinterpret_cast<const char*>(&p);}
 static bool serializer(const CExecutionSerializerFast& p){auto* b=reinterpret_cast<const char*>(&p);return reinterpret_cast<const char*>(&p.head_)-b==0&&reinterpret_cast<const char*>(&p.tail_)-b==8&&reinterpret_cast<const char*>(&p.pending_)-b==0x10&&reinterpret_cast<const char*>(&p.owner_)-b==0x14&&reinterpret_cast<const char*>(&p.capacity_)-b==0x1c&&reinterpret_cast<const char*>(&p.arguments_)-b==0x20&&reinterpret_cast<const char*>(&p.functions_)-b==0x28&&p.capacity_==512&&p.arguments_&&p.functions_;}
};
struct TimerAbiProbe final:CIOObject{
 int value=0;std::int64_t argument=0;unsigned waits=0,bytes=0;OVERLAPPED* raw=nullptr;
 void TimerExpired(int n)noexcept override{value=n;}
 void TimerExpired2(int n,std::int64_t p)noexcept override{value=n;argument=p;}
 void OnWaitCallback()noexcept override{++waits;}
 void OnIOCallback(int n,unsigned b,OVERLAPPED* p)noexcept override{value=n;bytes=b;raw=p;}
};
static SOCKET factoryArgument=0;
static void* slot(void* object,unsigned number){
#ifdef _MSC_VER
 return (*static_cast<void***>(object))[number];
#else
 return (*static_cast<void***>(object))[number+1]; //Itanium has two destructor entries.
#endif
}
static bool format(std::vector<unsigned char>& out,const char* f,...){va_list args;va_start(args,f);bool ok=AssemblePacket(f,args,out);va_end(args);return ok;}
int main(){
#ifdef _WIN32
 WSADATA wsa{};assert(WSAStartup(MAKEWORD(2,2),&wsa)==0);
#endif
 auto* probe=new TimerAbiProbe;assert(sizeof(MemoryObject)==0x18&&sizeof(CIOObject)==0x18);
 static_assert(!std::is_base_of_v<ObjectAllocated,MemoryObject>);static_assert(std::is_base_of_v<CISocket,CIOSocket>);
 assert(ObjectAbiTestAccess::refs(*probe)==8&&ObjectAbiTestAccess::timer(*probe)==0xc&&ObjectAbiTestAccess::id(*probe)==0x10);
 using Hold=MemoryObject*(*)(MemoryObject*,const char*,unsigned,ObjectReferenceType);
 using Drop=unsigned(*)(MemoryObject*,const char*,unsigned,ObjectReferenceType,bool);
 assert(reinterpret_cast<Hold>(slot(probe,1))(probe,__FILE__,19,ObjectReferenceType::ORT_TEMP_USE)==probe&&probe->referenceCount()==2);
 assert(reinterpret_cast<Drop>(slot(probe,2))(probe,__FILE__,20,ObjectReferenceType::ORT_TEMP_USE,false)==1);
 using Timer=void(*)(CIOObject*,int);using Timer64=void(*)(CIOObject*,int,std::int64_t);
 reinterpret_cast<Timer>(slot(probe,4))(probe,41);assert(probe->value==41);
 probe->linkTimer();reinterpret_cast<Timer>(slot(probe,5))(probe,42);assert(probe->value==42&&probe->timerCount()==0);
 reinterpret_cast<Timer64>(slot(probe,6))(probe,43,0x123456789LL);assert(probe->value==43&&probe->argument==0x123456789LL);
 probe->linkTimer();reinterpret_cast<Timer64>(slot(probe,7))(probe,44,0x234567891LL);assert(probe->value==44&&probe->argument==0x234567891LL&&probe->timerCount()==0);
 reinterpret_cast<void(*)(CIOObject*)>(slot(probe,9))(probe);assert(probe->waits==1);
 OVERLAPPED raw{};reinterpret_cast<void(*)(CIOObject*,int,unsigned,OVERLAPPED*)>(slot(probe,10))(probe,1,417,&raw);assert(probe->value==1&&probe->bytes==417&&probe->raw==&raw);
 reinterpret_cast<void(*)(CIOObject*,unsigned,OVERLAPPED*)>(slot(probe,8))(probe,7,nullptr);assert(probe->referenceCount()==1);
 reinterpret_cast<void(*)(CIOObject*,int)>(slot(probe,11))(probe,8);assert(probe->referenceCount()==1);probe->release();
 auto* callbacks=L1Interop_CreateCallbacks();auto* server=static_cast<Server*>(L1Interop_CreateServer(callbacks));assert(server);
 assert(sizeof(CIOServer)==0x28&&reinterpret_cast<char*>(&server->socketFactory)-reinterpret_cast<char*>(server)==0x90);
 server->socketFactory=std::make_unique<CDummyServerCallback>([](SOCKET value)noexcept->CIOSocket*{factoryArgument=value;return nullptr;});
 auto* factory=server->socketFactory.get();assert(sizeof(CDummyServerCallback)==16);
 assert(reinterpret_cast<CIOSocket*(*)(IServerCallback*,SOCKET)>(slot(factory,1))(factory,12345)==nullptr&&factoryArgument==12345);
 assert(reinterpret_cast<CIOSocket*(*)(CIOServer*,SOCKET,sockaddr_in*)>(slot(server,12))(server,4321,nullptr)==nullptr&&factoryArgument==4321);
 reinterpret_cast<void(*)(Server*,SOCKET)>(slot(server,13))(server,INVALID_SOCKET);
 assert(!reinterpret_cast<bool(*)(Server*,SOCKET,CSocket*)>(slot(server,14))(server,INVALID_SOCKET,nullptr));
 reinterpret_cast<void(*)(Server*)>(slot(server,15))(server);assert(server->shutdownSucceeded);
 assert(reinterpret_cast<char*>(&server->listening)-reinterpret_cast<char*>(server)==0x18&&reinterpret_cast<char*>(&server->acceptEvent)-reinterpret_cast<char*>(server)==0x20);
 SOCKET socketValue=INVALID_SOCKET;
#ifndef _WIN32
 socketValue=WSASocketW(AF_INET,SOCK_STREAM,0,nullptr,0,0);
#endif
 auto* socket=static_cast<CUserSocket*>(CUserSocket::Allocate(server,socketValue));assert(socket&&sizeof(CIOSocket)==0xb0&&sizeof(CSocket)==0x100&&sizeof(CUserSocket)==0xcd8);
 auto* start=reinterpret_cast<unsigned char*>(socket);assert(reinterpret_cast<unsigned char*>(&socket->socket)-start==0x18);
 assert(reinterpret_cast<unsigned char*>(&socket->execution_)-start==0x28&&reinterpret_cast<unsigned char*>(&socket->readOverlapped_)-start==0x58&&reinterpret_cast<unsigned char*>(&socket->writeOverlapped_)-start==0x78);
 assert(reinterpret_cast<unsigned char*>(&socket->nativeReadBuffer_)-start==0x98&&reinterpret_cast<unsigned char*>(&socket->nativeWriteBuffer_)-start==0xa0);
 assert(ObjectAbiTestAccess::serializer(socket->execution_));
 assert(*reinterpret_cast<std::uint32_t*>(start+0x110+32)==1&&*reinterpret_cast<std::uint32_t*>(start+0x4e8)==socket->SocketNumber());
 auto* held=reinterpret_cast<Hold>(slot(socket,1))(socket,"ObjectAbiTest.cpp",321,ObjectReferenceType::ORT_TEMP_USE);assert(held==socket);
 assert(*reinterpret_cast<std::int32_t*>(start+0x110+14*32)==1&&*reinterpret_cast<unsigned*>(start+0x114+14*32)==321);
 assert(std::strcmp(*reinterpret_cast<const char**>(start+0x100+14*32),"ObjectAbiTest.cpp")==0);
 assert(reinterpret_cast<Drop>(slot(socket,2))(socket,"ObjectAbiTest.cpp",322,ObjectReferenceType::ORT_TEMP_USE,false)==1);
 assert(*reinterpret_cast<unsigned*>(start+0x118+14*32)==322);
 reinterpret_cast<void(*)(CUserSocket*,const char*,unsigned)>(slot(socket,3))(socket,__FILE__,33);assert(server->referenceDiagnostics.checked==0);
 std::array<std::uint64_t,ReferenceReportWords> report{};server->referenceDiagnostics.snapshot(report.data(),0);assert(report[8]==1&&report[24]==4);
#ifdef _WIN32
 reinterpret_cast<void(*)(CIOSocket*)>(slot(socket,12))(socket);assert(!socket->l1().initialized);
#endif
 reinterpret_cast<void(*)(CIOSocket*)>(slot(socket,13))(socket);
 auto savedSocket=socket->socket;socket->socket=INVALID_SOCKET;
 using HardCheck=bool(*)(const CIOSocket*);using MarginCheck=bool(*)(const CIOSocket*,int);
 auto hardCheck=reinterpret_cast<HardCheck>(slot(socket,14));auto marginCheck=reinterpret_cast<MarginCheck>(slot(socket,15));
 assert(!hardCheck(socket)&&!marginCheck(socket,0));
 socket->socket=static_cast<SOCKET>(1); //predicate-only fixture;no OS call
 {
  std::lock_guard<std::mutex> lock(socket->gate());
  assert(hardCheck(socket)&&marginCheck(socket,0)&&!marginCheck(socket,-1)&&!marginCheck(socket,INT_MAX));
  socket->reservedSendBytes=0x180000;assert(marginCheck(socket,0x80000));
  socket->reservedSendBytes=0x180001;assert(!marginCheck(socket,0x80000));
  socket->reservedSendBytes=0x200000;assert(hardCheck(socket)&&marginCheck(socket,0)&&!marginCheck(socket,1));
  socket->reservedSendBytes=0x200001;assert(!hardCheck(socket)&&!marginCheck(socket,0));socket->reservedSendBytes=0;
  const unsigned char byte=0x12;Span one{&byte,1};assert(socket->writes().append(server->buffers,&one,1));
  socket->reservedSendBytes=0x1fffff;assert(hardCheck(socket)&&marginCheck(socket,0)&&!marginCheck(socket,1));
  socket->writes().clear(server->buffers);socket->reservedSendBytes=0;
 }
 socket->socket=savedSocket;
 auto pointerGetter=reinterpret_cast<void*(*)(const CUserSocket*)>(slot(socket,24));
 auto numberGetter=reinterpret_cast<std::uint32_t(*)(const CUserSocket*)>(slot(socket,25));
 assert(!pointerGetter(socket)&&numberGetter(socket)==socket->SocketNumber());
 int marker=123;void* originalPointer=&marker;std::memcpy(start+0x4f0,&originalPointer,sizeof(originalPointer));
 assert(pointerGetter(socket)==&marker);originalPointer=nullptr;std::memcpy(start+0x4f0,&originalPointer,sizeof(originalPointer));
 in_addr address{};address.s_addr=0x12345678;reinterpret_cast<void(*)(CSocket*,in_addr)>(slot(socket,21))(socket,address);
 in_addr copy{};auto* returned=reinterpret_cast<in_addr*(*)(const CSocket*,in_addr*)>(slot(socket,22))(socket,&copy);assert(returned==&copy&&copy.s_addr==address.s_addr);
 assert(*reinterpret_cast<std::uint32_t*>(start+0xb8)==address.s_addr);
 reinterpret_cast<void(*)(const CUserSocket*,unsigned char,unsigned short)>(slot(socket,23))(socket,0x51,0x1234);assert(socket->l1().lastSending==0x511234);
 reinterpret_cast<void(*)(CIOSocket*)>(slot(socket,20))(socket);reinterpret_cast<void(*)(CIOSocket*)>(slot(socket,19))(socket);
 for(unsigned words:{8u,18u,24u,1720u,2030u}){std::array<std::uint64_t,2031> values;values.fill(0xacac);assert(L1Interop_GetDiagnostics(server,values.data(),words)&&values[words]==0xacac);if(words==2030)assert(values[1720]==ObjectAllocator::Marker);}
 assert(server->referenceDiagnostics.reserve());auto* verifier=new CUserSocketReleaseVerifier(server,socket);auto* image=reinterpret_cast<unsigned char*>(verifier);
 assert(sizeof(CUserSocketReleaseVerifier)==40&&*reinterpret_cast<CUserSocket**>(image+0x18)==socket&&*reinterpret_cast<const wchar_t**>(image+0x20));
 verifier->release();assert(server->referenceDiagnostics.active==0);
 assert(slot(socket,16)&&slot(socket,17)&&slot(socket,18));socket->release(ObjectReferenceType::ORT_CREATE_DELETE);server->release();L1Interop_ReleaseCallbacks(callbacks);
 std::vector<unsigned char> bytes;unsigned char block[]{0x11,0x22};assert(format(bytes,"cChdQpfSsb",0x12,0x34,0x5678,0x12345678,std::uint64_t(0x1122334455667788ULL),static_cast<void*>(nullptr),1.0,u"가","a",2,static_cast<const void*>(block)));
 assert(bytes.size()==1+1+2+4+8+8+8+4+2+2&&bytes[0]==0x12&&bytes[2]==0x78&&bytes.back()==0x22);
 bytes.clear();assert(!format(bytes,"b",-1,static_cast<const void*>(nullptr)));bytes.assign(65533,0);assert(!format(bytes,"c",1));
 std::array<std::uint64_t,ObjectAllocator::ReportWords> heap{};ObjectAllocator::Instance().Snapshot(heap.data());assert(heap[1]==0&&heap[2]==0&&heap[8]==0);
 std::cout<<"PASS original MemoryObject/CIOObject slots1..11;socket prefix0xb0/CSocket0x100/CUserSocket0xcd8;OV58/78,buffer98/a0,ORT100/110/114/118,number4e8,serializer48-byte offsets;socket virtuals12..25;slot15 int margin,boundaries/overflow,FIFO+reservations;slot24 pointer getter;slot25 socket number;packet format cases/bounds;zero live allocator objects\n";
#ifdef _WIN32
 WSACleanup();
#endif
}
