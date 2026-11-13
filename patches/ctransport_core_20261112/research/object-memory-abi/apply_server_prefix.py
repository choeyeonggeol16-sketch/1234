from pathlib import Path
import re
root=Path(__file__).resolve().parents[2];p=root/'native/io_server.hpp';s=p.read_text();assert 'struct CIOServerState' not in s
fields='port acceptQueued pendingAccepts waitCallbacks acceptCallbacks waitFailures lastWaitError callbackCounts callbackMillis callbackStarted acceptGate accepting listenAddress listenLength'.split()
# The original factory interface takes only the socket;the L1 owning server
# is bound by a scoped TLS context around the call.
s=s.replace('CreateSocket(Server*,SOCKET)', 'CreateSocket(SOCKET)').replace('CIOSocket*(*)(Server*,SOCKET)', 'CIOSocket*(*)(SOCKET)').replace('CreateSocket(Server* server,SOCKET socket)noexcept override{return allocate_(server,socket);}', 'CreateSocket(SOCKET socket)noexcept override{return allocate_(socket);}')
a=s.index('class CIOServer : public CIOObject');b=s.index('    virtual void NotifyPendingChanged',a)
state='''struct CIOServerState final:ObjectAllocated {
 HANDLE port=nullptr;bool acceptQueued=false;std::atomic<int> pendingAccepts{0};
 std::atomic<std::uint64_t> waitCallbacks{0},acceptCallbacks{0},waitFailures{0},lastWaitError{0};
 std::array<std::atomic<std::uint64_t>,18> callbackCounts{},callbackMillis{},callbackStarted{};
 std::mutex acceptGate;std::atomic<bool> accepting{false};sockaddr_storage listenAddress{};int listenLength=sizeof(listenAddress);
 void(*notify)(CIOServer*)noexcept=nullptr;void(*accepted)(CIOServer*,SOCKET)noexcept=nullptr;void(*failure)(CIOServer*,unsigned)noexcept=nullptr;
};
'''
s=s[:a]+state+s[a:];a=s.index('class CIOServer : public CIOObject');b=s.index('    virtual void NotifyPendingChanged',a)
new='''class CIOServer : public CIOObject {
public:
 SOCKET listening=INVALID_SOCKET;HANDLE acceptEvent=nullptr;
 CIOServerState& host()noexcept{return *static_cast<CIOServerState*>(ObjectAllocator::Extension(this));}
 const CIOServerState& host()const noexcept{return *static_cast<const CIOServerState*>(ObjectAllocator::Extension(const_cast<CIOServer*>(this)));}
 CIOServer(){ObjectAllocator::Extension(this)=new CIOServerState;}
 ~CIOServer()override{auto* context=&host();ObjectAllocator::Extension(this)=nullptr;delete context;}
 virtual CIOSocket* CreateSocket(SOCKET,sockaddr_in*)noexcept=0; //original slot12
'''
for name in fields:new+=f' auto& {name}()noexcept{{return host().{name};}} const auto& {name}()const noexcept{{return host().{name};}}\n'
s=s[:a]+new+s[b:]
s=s.replace('    virtual void NotifyPendingChanged() noexcept=0;','    void NotifyPendingChanged()noexcept{if(host().notify)host().notify(this);}')
s=s.replace('    virtual void OnAcceptedSocket(SOCKET socket) noexcept=0;','    void OnAcceptedSocket(SOCKET socket)noexcept{if(host().accepted)host().accepted(this,socket);}')
s=s.replace('    virtual void OnWaitFailure(unsigned error) noexcept=0;','    void OnWaitFailure(unsigned error)noexcept{if(host().failure)host().failure(this,error);}')
# Convert data uses in the existing Create/Close bodies only.
a=s.index('    bool Create(const char*');b=s.index('// Original RegisterWait',a)
segment=s[a:b]
for name in fields:segment=re.sub(r'\b'+name+r'\b(?!\s*\()',name+'()',segment)
s=s[:a]+segment+s[b:];s=s.replace('class CIOSocket;','class CIOSocket;\nclass CIOServer;',1);p.write_text(s)
p=root/'native/CTransport.cpp';s=p.read_text()
# Factory TLS context is L1 ownership data,not part of the original callback.
s=s.replace('thread_local Server* currentWorker = nullptr;', 'thread_local Server* currentFactoryServer=nullptr;\nthread_local CIOSocket* expectedRemoval=nullptr;\nthread_local Server* expectedRemovalServer=nullptr;\nthread_local Server* currentWorker = nullptr;')
s=s.replace('    L1Callbacks* const callbacks;', '''    std::array<std::byte,0x68> originalServer28_{};
    std::unique_ptr<IServerCallback> socketFactory; //original+0x90
    L1Callbacks* const callbacks;''',1)
# Remove the old duplicate factory declaration.
a=s.index('    std::unique_ptr<IServerCallback> socketFactory;',s.index('L1Callbacks* const callbacks;'));s=s[:a]+s[a:].replace('    std::unique_ptr<IServerCallback> socketFactory;\n','',1)
s=s.replace('    void NotifyPendingChanged()noexcept override{notifyChange();}','    void NotifyPendingChanged()noexcept{notifyChange();}').replace('    void OnAcceptedSocket(SOCKET socket)noexcept override;','    void OnAcceptedSocket(SOCKET socket)noexcept;').replace('    void OnWaitFailure(unsigned error) noexcept override;','    void OnWaitFailure(unsigned error) noexcept;')
s=s.replace('    CIOSocket* CreateSocket(SOCKET socket) noexcept;','''    CIOSocket* CreateSocket(SOCKET socket)noexcept;
    CIOSocket* CreateSocket(SOCKET socket,sockaddr_in* peer)noexcept override;
    virtual void Remove(SOCKET socket)noexcept; //13
    virtual bool Add(SOCKET socket,CSocket* object)noexcept; //14
    virtual void Shutdown()noexcept{shutdownSucceeded=ShutdownImpl();} //15
    bool shutdownSucceeded=true;
    bool ShutdownSockets()noexcept{Shutdown();return shutdownSucceeded;}''')
s=s.replace('    bool Shutdown() noexcept;','    bool ShutdownImpl()noexcept;')
s=s.replace('bool Server::Shutdown() noexcept','bool Server::ShutdownImpl()noexcept').replace('!Shutdown()', '!ShutdownSockets()')
s=s.replace('WSADATA data{}; wsaReady = WSAStartup', '''host().notify=[](CIOServer* p)noexcept{static_cast<Server*>(p)->NotifyPendingChanged();};
        host().accepted=[](CIOServer* p,SOCKET socket)noexcept{static_cast<Server*>(p)->OnAcceptedSocket(socket);};
        host().failure=[](CIOServer* p,unsigned error)noexcept{static_cast<Server*>(p)->OnWaitFailure(error);};
        WSADATA data{}; wsaReady = WSAStartup''')
# Rewrite unqualified inherited state accesses only in Server/CIOServer bodies.
def endbrace(text,at):
 depth=0
 for m in re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/|[{}]',text[at:],re.S):
  if m[0]=='{':depth+=1
  elif m[0]=='}':
   depth-=1
   if depth==0:return at+m.end()
 raise ValueError(at)
tokens=re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/|[A-Za-z_]\w*|\s+|.',re.S)
def rewrite(body):
 out=[]
 for m in tokens.finditer(body):
  word=m[0]
  if word in fields and not body[:m.start()].rstrip().endswith(('->','.','::')) and not body[m.end():].lstrip().startswith('('):word+='()'
  out.append(word)
 return ''.join(out)
ranges=[];a=s.index('class Server : public CIOServer');b=endbrace(s,s.index('{',a));segment=s[a:b]
for m in re.finditer(r'\)\s*(?:const\s*)?(?:noexcept\s*)?(?:override\s*)?\{',segment):
 at=a+m.end()-1;end=endbrace(s,at)
 if not any(x<=at<y for x,y in ranges):ranges.append((at,end))
for m in re.finditer(r'(?:bool|void) (?:Server|CIOServer)::\w+\([^\n]*\)\s*noexcept\s*\{',s):ranges.append((m.end()-1,endbrace(s,m.end()-1)))
for a,b in sorted(ranges,reverse=True):s=s[:a]+rewrite(s[a:b])+s[b:]
for name in fields:s=re.sub(r'->'+name+r'\b(?!\s*\()', '->'+name+'()',s)
# Bind only for the callback invocation;restore on reentry.
s=s.replace('socketFactory->CreateSocket(this,socket)', 'socketFactory->CreateSocket(socket)')
s=s.replace('    auto* object=static_cast<CUserSocket*>(socketFactory->CreateSocket(socket));', '''    auto* previous=currentFactoryServer;currentFactoryServer=this;
    auto* object=static_cast<CUserSocket*>(socketFactory->CreateSocket(socket));currentFactoryServer=previous;''')
s=s.replace('socketFactory=std::make_unique<CDummyServerCallback>(CUserSocket::Allocate);','socketFactory=std::make_unique<CDummyServerCallback>([](SOCKET socket)noexcept->CIOSocket*{return currentFactoryServer?CUserSocket::Allocate(currentFactoryServer,socket):nullptr;});')
# Actual original Add/Remove slots with explicit L1 ownership adapters.
s=s.replace('bool Server::Add(CIOSocket* c) noexcept {', '''bool Server::Add(CIOSocket* c)noexcept{return Add(c->socket,static_cast<CSocket*>(c));}
bool Server::Add(SOCKET value,CSocket* object)noexcept{
    auto* c=static_cast<CIOSocket*>(object);if(!c||c->socket!=value)return false;''')
a=s.index('void Server::Remove(SOCKET socket,CIOSocket* expected) noexcept');b=s.index('bool Server::ShutdownImpl()',a)
s=s[:a]+'''void Server::Remove(SOCKET socket,CIOSocket* expected)noexcept{
 auto* oldExpected=expectedRemoval;auto* oldServer=expectedRemovalServer;expectedRemoval=expected;expectedRemovalServer=this;
 Remove(socket);expectedRemoval=oldExpected;expectedRemovalServer=oldServer;
}
void Server::Remove(SOCKET value)noexcept{
 CIOSocket* removed=nullptr;
 {std::lock_guard<std::mutex> lock(socketMapGate);auto at=socketMap.find(value);
  if(at!=socketMap.end()&&(expectedRemovalServer!=this||!expectedRemoval||at->second==expectedRemoval)){removed=at->second;socketMap.erase(at);}}
 if(removed)removed->release(ObjectReferenceType::ORT_SOCKET_MAP);
}
'''+s[b:]
# Original factory creates/registers,then hands the object to Initialize.
a=s.index('void Server::OnAcceptedSocket(SOCKET socket)noexcept');b=s.index('void CPerfMon::Sample()',a)
s=s[:a]+'''void Server::OnAcceptedSocket(SOCKET value)noexcept{
 if(value==INVALID_SOCKET)return;sockaddr_in peer{};int length=sizeof(peer);getpeername(value,reinterpret_cast<sockaddr*>(&peer),&length);
 auto* c=CreateSocket(value,&peer);if(!c){closesocket(value);return;}
 ObjectRef temporary(c,false,ObjectReferenceType::ORT_CREATE_DELETE);
 const auto fn=callbacks->accept.load();const int admission=fn?fn(this,c->id(),static_cast<std::uint64_t>(value)):2;
 bool initialized=false;
 {std::lock_guard<std::mutex> lock(c->gate());if(!c->state().closing&&admission==0){c->Initialize();initialized=c->l1().initialized;}}
 c->accepted(initialized?0:2);
}
'''+s[b:]
a=s.index('void Server::RemoveSocketNumber(')
s=s[:a]+'''CIOSocket* Server::CreateSocket(SOCKET value,sockaddr_in* peer)noexcept{
 auto* c=CreateSocket(value);if(!c)return nullptr;
 if(!Add(value,static_cast<CSocket*>(c))){RemoveSocketNumber(c);c->socket=INVALID_SOCKET;c->release(ObjectReferenceType::ORT_CREATE_DELETE);return nullptr;}
 if(peer)c->SetAddress(peer->sin_addr);return c;
}
'''+s[a:]
p.write_text(s)
# Existing fixture subclasses are allocated from MemoryObject's heap.
for p in(root/'tests').glob('*.cpp'):
 s=p.read_text()
 for name in fields:s=re.sub(r'->'+name+r'\b(?!\s*\()', '->'+name+'()',s)
 s=s.replace('void NotifyPendingChanged()noexcept override{}void OnAcceptedSocket(SOCKET)noexcept override{}', 'CIOSocket* CreateSocket(SOCKET,sockaddr_in*)noexcept override{return nullptr;}\n   void NotifyPendingChanged()noexcept{}void OnAcceptedSocket(SOCKET)noexcept{}').replace('void OnWaitFailure(unsigned)noexcept override{}','void OnWaitFailure(unsigned)noexcept{}')
 p.write_text(s)
