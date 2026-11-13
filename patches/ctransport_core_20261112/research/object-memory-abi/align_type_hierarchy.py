from pathlib import Path
import re
root=Path(__file__).resolve().parents[2]
p=root/'native/object_allocator.hpp';s=p.read_text();a=s.index('class ObjectAllocated {');body=s[a:].split('public:\n',1)[1].rsplit('\n};',1)[0];macro='#define CTRANSPORT_OBJECT_ALLOCATOR_OPERATORS \\\n'+' \\\n'.join(body.splitlines())+'\n';s=s[:a]+macro+'class ObjectAllocated {public:\n CTRANSPORT_OBJECT_ALLOCATOR_OPERATORS\n};\n';p.write_text(s)
p=root/'native/cio_core.hpp';s=p.read_text().replace('class MemoryObject : public ObjectAllocated {','class MemoryObject {\npublic:\n CTRANSPORT_OBJECT_ALLOCATOR_OPERATORS\nprivate:');p.write_text(s)
p=root/'native/io_server.hpp';s=p.read_text().replace('class CIOSocket;','template<class Buffer>class CIOSocketEx;\nusing CIOSocket=CIOSocketEx<CIOBufferEx<16384>>;');p.write_text(s)
p=root/'native/CTransport.cpp';s=p.read_text().replace('class CIOSocket;\n','')
s=s.replace('class CIOSocket : public CIOObject {','template<class Buffer>class CIOSocketEx : public CIOObject {')
s=s.replace('    CIOSocket(Server* s,SOCKET value,std::uint64_t valueId,std::size_t bufferBytes):socket(value){','    CIOSocketEx(Server* s,SOCKET value,std::uint64_t valueId,std::size_t bufferBytes=std::tuple_size<decltype(Buffer::payload)>::value):socket(value){')
s=s.replace('    ~CIOSocket() override {','    ~CIOSocketEx() override {')
a=s.index('template<class Buffer> class CIOSocketEx : public CIOSocket');b=s.index('class CSocket :',a);s=s[:a]+s[b:]
# Base template ends at original slot20;CSocket adds slots21..23.
s=s.replace('    virtual void SetAddress(in_addr)noexcept{} //21;CSocket overrides\n    virtual in_addr* GetAddress(in_addr* result)const noexcept{if(result)*result=in_addr{};return result;} //22\n    virtual void OnSending(unsigned char,unsigned short)const noexcept{} //23\n','')
s=s.replace('    virtual void OnClose()noexcept{OnSocketRetired();}', '    virtual void OnClose()noexcept{}')
s=s.replace('    virtual bool Slot14SendAllowed()const noexcept{return socket!=INVALID_SOCKET&&!state().closing;}', '    virtual bool Slot14SendAllowed()const noexcept{return true;}').replace('    virtual bool Slot15SendAllowed()const noexcept{return Slot14SendAllowed()&&reservedSendBytes<=MaxQueuedBytes;}', '    virtual bool Slot15SendAllowed()const noexcept{return true;}')
s=s.replace('    virtual void __async_AsyncSend(NativeSendRequest&)noexcept=0;', '    void __async_AsyncSend(NativeSendRequest& request)noexcept{ExecuteSend(request);}')
s=s.replace('    virtual void __async_AsyncSendConstBuffer(NativeSendRequest&)noexcept=0;', '    void __async_AsyncSendConstBuffer(NativeSendRequest& request)noexcept{ExecuteSend(request);}')
a=s.index('    virtual const sockaddr_storage& LocalAddress()');b=s.index('    CIOSocketEx(Server*',a)
s=s[:a]+'''    const sockaddr_storage& LocalAddress()const noexcept{return local_();}
    const sockaddr_storage& RemoteAddress()const noexcept{return remote_();}
    std::uint32_t SocketNumber()const noexcept{return l1().number;}
    std::uint32_t AccountId()const noexcept{return accountId_();}
    bool HasMacIdentity()const noexcept{return macIdentity_().load()!=0;}
    bool CharacterEntered()const noexcept{return characterEntered_();}
    bool SetSessionStateLocked(int phase)noexcept{if(l1().setSession)return l1().setSession(this,phase);sessionState()=phase;return true;}
    CUserSocketParser* UserProtocol()noexcept{return protocol_().get();}
    bool ConfigureUserProtocol(std::shared_ptr<const L1CodecApi> api,unsigned seed)noexcept{
        if(protocol_())return false;try{protocol_()=std::make_unique<CUserSocketParser>(std::move(api),seed);return true;}catch(...){return false;}
    }
    void NotifySending(unsigned char,unsigned short)const noexcept;
'''+s[b:]
s=s.replace('mutable std::atomic<std::uint32_t> lastSending{0};bool initialized=false;', 'mutable std::atomic<std::uint32_t> lastSending{0};bool initialized=false;\n std::uint32_t number=0;bool(*setSession)(void*,int)noexcept=nullptr;')
s=s.replace('OnSending(length>2?', 'NotifySending(length>2?')
s=s.replace('    void SetAddress(in_addr value)noexcept override', '    virtual void SetAddress(in_addr value)noexcept')
s=s.replace('    in_addr* GetAddress(in_addr* value)const noexcept override', '    virtual in_addr* GetAddress(in_addr* value)const noexcept')
needle='    bool SendV(const Span* spans,unsigned count,bool plain)noexcept'
a=s.index(needle);s=s[:a]+'    virtual void OnSending(unsigned char,unsigned short)const noexcept{}\n'+s[a:]
s=s.replace('void __async_AsyncSend(NativeSendRequest& request)noexcept override','void __async_AsyncSend(NativeSendRequest& request)noexcept').replace('void __async_AsyncSendConstBuffer(NativeSendRequest& request)noexcept override','void __async_AsyncSendConstBuffer(NativeSendRequest& request)noexcept')
s=s.replace('bool HasMacIdentity()const noexcept override','bool HasMacIdentity()const noexcept')
s=s.replace('void OnSocketRetired()noexcept override','void OnSocketRetired()noexcept',1)
s=s.replace('const sockaddr_storage& LocalAddress()const noexcept override','const sockaddr_storage& LocalAddress()const noexcept').replace('const sockaddr_storage& RemoteAddress()const noexcept override','const sockaddr_storage& RemoteAddress()const noexcept')
s=s.replace('std::uint32_t SocketNumber()const noexcept override','std::uint32_t SocketNumber()const noexcept').replace('std::uint32_t AccountId()const noexcept override','std::uint32_t AccountId()const noexcept').replace('bool CharacterEntered()const noexcept override','bool CharacterEntered()const noexcept').replace('bool SetSessionStateLocked(int phase)noexcept override','bool SetSessionStateLocked(int phase)noexcept')
s=s.replace('void OnSocketRetired()noexcept override','void OnClose()noexcept override',1)
# Reuse base protocol adapters;they no longer occupy C++ virtual slots.
a=s.index('    CUserSocketParser* UserProtocol()noexcept override');b=s.index('\n};',a);s=s[:a]+s[b:]
s=s.replace('number_(ownerServer->AllocateSocketNumber()){referencesTrace_', 'number_(ownerServer->AllocateSocketNumber()){l1().number=number_;l1().setSession=[](void* owner,int phase)noexcept{return static_cast<CUserSocket*>(owner)->SetSessionStateLocked(phase);};referencesTrace_')
# All out-of-line definitions belong to the actual original template type.
s=re.sub(r'\b(bool|void) CIOSocket::(\w+)\(',r'template<class Buffer>\n\1 CIOSocketEx<Buffer>::\2(',s)
a=s.index('template<class Buffer>\nvoid CIOSocketEx<Buffer>::Send(')
s=s[:a]+'''template<class Buffer>
void CIOSocketEx<Buffer>::NotifySending(unsigned char opcode,unsigned short length)const noexcept{
 static_cast<const CSocket*>(static_cast<const CIOSocket*>(this))->OnSending(opcode,length);
}
'''+s[a:]
p.write_text(s)
