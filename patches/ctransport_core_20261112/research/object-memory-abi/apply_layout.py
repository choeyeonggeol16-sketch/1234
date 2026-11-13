from pathlib import Path
import re
root=Path(__file__).resolve().parents[2];p=root/'native/CTransport.cpp';s=p.read_text();assert 'struct CIOSocketState' not in s
fields='server id retiredSocket gate state writes reads readOp writeOp closeOp resumeOp pendingSendRequests gracefulRequested gracefulSendShutdown gracefulPeerEof closeAbortive gracefulDue closeOperation closeError packetBorrowed packetDelivering resumePosted userPumpActive ioTerminal registrationReleased packetSequence deadlineToken deadlineDue cancelDeadlineToken loginDue writeProgressAt sessionState local_ remote_ ipReserved_ macIdentity_ protocol_ retiredAccountId_ accountId_ playingRegistered_ characterEntered_'.split()
state='''struct CIOSocketState final:ObjectAllocated {
 Server* const server;std::uint64_t id;SOCKET retiredSocket=INVALID_SOCKET;
 std::mutex gate;IoState state;SendChain writes;ReceiveWindow reads;
 Operation readOp,writeOp,closeOp,resumeOp;
 unsigned pendingSendRequests=0;
 bool gracefulRequested=false,gracefulSendShutdown=false,gracefulPeerEof=false,closeAbortive=true;
 std::uint64_t gracefulDue=0;int closeOperation=5,closeError=0;
 bool packetBorrowed=false,packetDelivering=false,resumePosted=false,userPumpActive=false;
 bool ioTerminal=false,registrationReleased=false;
 std::uint64_t packetSequence=0,deadlineToken=0,deadlineDue=0,cancelDeadlineToken=0,loginDue=0,writeProgressAt=0;
 int sessionState=0;sockaddr_storage local_{},remote_{};
 std::atomic<bool> ipReserved_{false};std::atomic<std::uint64_t> macIdentity_{0};
 std::unique_ptr<CUserSocketParser> protocol_;
 std::uint32_t retiredAccountId_=0,accountId_=0;bool playingRegistered_=false,characterEntered_=false;
 mutable std::atomic<std::uint32_t> lastSending{0};
 CIOSocketState(CIOObject* owner,Server* s,std::uint64_t value,std::size_t bytes):server(s),id(value),writes(bytes),reads(s->buffers,bytes),readOp(OpKind::Read,owner),writeOp(OpKind::Write,owner),closeOp(OpKind::Close,owner),resumeOp(OpKind::Resume,owner){}
};
'''
a=s.index('class CIOSocket : public CIOObject');s=s[:a]+state+s[a:]
a=s.index('    Server* const server;',s.index('class CIOSocket :'));b=s.index('    bool sendConstBuffer',a)
access='''    // Verified x64 CIOSocketEx prefix0xb0. L1 state lives in the allocator
    // extension pointer,so it does not move original network fields.
    SOCKET socket;
    std::uint32_t reservedSendBytes=0,original24_=0;
    CExecutionSerializerFast execution_;
    OVERLAPPED readOverlapped_{},writeOverlapped_{};
    void* nativeReadBuffer_=nullptr;void* nativeWriteBuffer_=nullptr;std::uint64_t originalA8_=0;
    CIOSocketState& l1()noexcept{return *static_cast<CIOSocketState*>(ObjectAllocator::Extension(this));}
    const CIOSocketState& l1()const noexcept{return *static_cast<const CIOSocketState*>(ObjectAllocator::Extension(const_cast<CIOSocket*>(this)));}
'''
for name in fields:access+=f'    auto& {name}()noexcept{{return l1().{name};}} const auto& {name}()const noexcept{{return l1().{name};}}\n'
s=s[:a]+access+s[b:]
s=s.replace('    bool gracefulRequested=false,gracefulSendShutdown=false,gracefulPeerEof=false,closeAbortive=true;\n    std::uint64_t gracefulDue=0;\n','')
s=s.replace('    int closeOperation = 5, closeError = 0;\n','')
a=s.index('    bool packetBorrowed=false',s.index('class CIOSocket :'));b=s.index('    void failLocked',a)
s=s[:a]+'''    CIOSocket(Server* s,SOCKET value,std::uint64_t valueId,std::size_t bufferBytes):socket(value){
        ObjectAllocator::Extension(this)=new CIOSocketState(this,s,valueId,bufferBytes);
        readOp().external=&readOverlapped_;writeOp().external=&writeOverlapped_;
        s->retain();
    }
    ~CIOSocket() override {
        auto* extra=&l1();auto* ownerServer=extra->server;
        extra->writes.clear(ownerServer->buffers);extra->reads.clear();
        if(socket!=INVALID_SOCKET)closesocket(socket);
        ObjectAllocator::Extension(this)=nullptr;delete extra;ownerServer->release();
    }
    Operation* DecodeOperation(OVERLAPPED* raw)noexcept{
        if(raw==&readOverlapped_)return &readOp();if(raw==&writeOverlapped_)return &writeOp();return reinterpret_cast<Operation*>(raw);
    }
'''+s[b:]
s=s.replace('    sockaddr_storage local_{},remote_{};\n    std::atomic<bool> ipReserved_{false};\n    std::atomic<std::uint64_t> macIdentity_{0};','''    std::array<std::byte,8> originalB0_{};
    std::uint32_t peerAddress_=0,originalBC_=0;
    std::array<std::byte,0x40> originalC0_{};''')
s=s.replace('CSocket(Server* server,SOCKET socket,std::uint64_t id):CIOSocketEx<CIOBufferEx<16384>>(server,socket,id)', 'CSocket(Server* ownerServer,SOCKET sockValue,std::uint64_t connectionId):CIOSocketEx<CIOBufferEx<16384>>(ownerServer,sockValue,connectionId)')
s=s.replace('    std::unique_ptr<CUserSocketParser> protocol_;\n    const std::uint32_t number_;\n    ReferenceTrace referencesTrace_;\n    std::uint32_t retiredAccountId_=0;\n    std::uint32_t accountId_=0;\n    bool playingRegistered_=false,characterEntered_=false;', '''    ReferenceTrace referencesTrace_; //0x100..0x4df
    std::array<std::byte,8> original4E0_{};
    const std::uint32_t number_; //0x4e8
    std::array<std::byte,0xcd8-0x4ec> originalL2Profile_{};''')
s=s.replace('CUserSocket(Server* server,SOCKET socket,std::uint64_t id):CSocket(server,socket,id),number_(server->AllocateSocketNumber())', 'CUserSocket(Server* ownerServer,SOCKET sockValue,std::uint64_t connectionId):CSocket(ownerServer,sockValue,connectionId),number_(ownerServer->AllocateSocketNumber())')
s=s.replace('static CIOSocket* Allocate(Server* server,SOCKET socket)noexcept{return new(std::nothrow) CUserSocket(server,socket,server->nextId.fetch_add(1));}', 'static CIOSocket* Allocate(Server* ownerServer,SOCKET sockValue)noexcept{try{return new CUserSocket(ownerServer,sockValue,ownerServer->nextId.fetch_add(1));}catch(...){return nullptr;}}')
# Rewrite only instance-method bodies,not declarations,parameters or literals.
def endbrace(text,at):
 depth=0
 for m in re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/|[{}]',text[at:],re.S):
  if m[0]=='{':depth+=1
  elif m[0]=='}':
   depth-=1
   if depth==0:return at+m.end()
 raise ValueError(at)
tokens=re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/|[A-Za-z_]\w*|\s+|.',re.S)
def bodyrewrite(body):
 parts=list(tokens.finditer(body));out=[]
 for i,m in enumerate(parts):
  word=m[0];prev=body[:m.start()].rstrip();after=body[m.end():].lstrip()
  if word in fields and not prev.endswith(('->','.','::')) and not after.startswith('('):word+='()'
  out.append(word)
 return ''.join(out)
ranges=[]
for classname in ['CIOSocket','CSocket','CUserSocket']:
 start=s.index('class '+classname+' ');end=endbrace(s,s.index('{',start));chunk=s[start:end]
 # Match every method opening;all argument lists here are single-line.
 for m in re.finditer(r'\)\s*(?:const\s*)?(?:noexcept\s*)?(?:override\s*)?\{',chunk):
  brace=start+m.end()-1;finish=endbrace(s,brace)
  # nested lambda/function bodies are covered by their owning method.
  if not any(a<=brace<b for a,b in ranges):ranges.append((brace,finish))
for m in re.finditer(r'(?:bool|void) CIOSocket::\w+\([^\n]*\)\s*noexcept\s*\{',s):
 brace=m.end()-1;ranges.append((brace,endbrace(s,brace)))
for a,b in sorted(ranges,reverse=True):s=s[:a]+bodyrewrite(s[a:b])+s[b:]
# External typed socket accesses in this translation unit.
for name in fields:s=re.sub(r'\b(c|socket|socket_|user)->'+name+r'\b(?!\s*\()',lambda m:m[1]+'->'+name+'()',s)
# Actual native OVERLAPPEDs stay at+0x58/+0x78;Operation metadata is separate.
s=s.replace('    void reset() noexcept { overlapped = OVERLAPPED{}; }','    OVERLAPPED* external=nullptr;\n    OVERLAPPED* address()noexcept{return external?external:&overlapped;}\n    void reset()noexcept{*address()=OVERLAPPED{};}')
s=s.replace('&op.overlapped','op.address()').replace('&readOp().overlapped','readOp().address()').replace('&writeOp().overlapped','writeOp().address()')
s=s.replace('auto* operation = reinterpret_cast<Operation*>(entry.lpOverlapped);\n            auto* owner = reinterpret_cast<CIOObject*>(entry.lpCompletionKey);','auto* owner=reinterpret_cast<CIOObject*>(entry.lpCompletionKey);\n            auto* socketOwner=dynamic_cast<CIOSocket*>(owner);\n            auto* operation=socketOwner?socketOwner->DecodeOperation(entry.lpOverlapped):reinterpret_cast<Operation*>(entry.lpOverlapped);')
s=s.replace('owner->OnIOCallback(operation, ok, entry.dwNumberOfBytesTransferred, error);','owner->OnIOCallback(operation,ok,entry.dwNumberOfBytesTransferred,error,entry.lpOverlapped);')
p.write_text(s)
# Tests refer to typed socket internals;preserve their assertions via accessors.
for p in (root/'tests').glob('*.cpp'):
 s=p.read_text()
 for name in fields:s=re.sub(r'\b(c|ca|cb|socket|socket_|user)->'+name+r'\b(?!\s*\()',lambda m:m[1]+'->'+name+'()',s)
 p.write_text(s)
