// Newly implemented transport from the documented behaviour of the supplied
// x86/x64 CIO code. No original instruction bytes or game code are embedded.
#ifdef CTRANSPORT_TEST
#include "fake_windows.hpp"
#define CIO_API extern "C"
#else
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0601
#endif
#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>
#include <psapi.h>
#define CIO_API extern "C" __declspec(dllexport)
#endif
#include "cio_core.hpp"
#include "interop_abi.hpp"
#include "timer_wheel.hpp"
#include "user_socket.hpp"
#include "user_registry.hpp"
#include "io_server.hpp"
#include "object_map.hpp"
#include "socket_limiter.hpp"
#include "execution_serializer.hpp"
#include "perfmon.hpp"
#include "packet_format.hpp"
#include <condition_variable>
#include <climits>
#include <cstdarg>
#include <cstdio>
#include <memory>
#include <shared_mutex>
#include <thread>
#include <unordered_map>
#include <vector>

using AcceptFn = int (*)(void*, std::uint64_t, std::uint64_t);
using DataFn = int (*)(void*, std::uint64_t, const void*, int);
using CloseFn = int (*)(void*, std::uint64_t, int, int);
using PacketFn = int (*)(void*, std::uint64_t, std::uint64_t, const void*, int);
using DeadlineFn = int (*)(void*, std::uint64_t);
using NtStatusToErrorFn = ULONG (WINAPI *)(LONG);
constexpr ULONG CompletionBatchSize = 128;
struct L1Callbacks {
    std::atomic<AcceptFn> accept{nullptr};
    std::atomic<DataFn> receive{nullptr}, send{nullptr};
    std::atomic<CloseFn> close{nullptr}, closing{nullptr};
    std::atomic<PacketFn> packet{nullptr};
    std::atomic<DeadlineFn> deadline{nullptr};
    std::atomic<void (*)(void*, std::uint64_t, int)> task{nullptr};
};
class Server;
class CSocket;
class CUserSocket;
class NativeTask;
class NativeSendRequest;
class CUserSocketReleaseVerifier;
struct L1ConstBuffer;
using TaskFn = void (*)(void*, std::uint64_t, int);
thread_local Server* currentFactoryServer=nullptr;
thread_local CIOSocket* expectedRemoval=nullptr;
thread_local Server* expectedRemovalServer=nullptr;
thread_local Server* currentWorker = nullptr;
thread_local unsigned currentWorkerIndex = 0;
thread_local Server* currentCallbackServer = nullptr;
struct CallbackScope {
    Server* previous;
    explicit CallbackScope(Server* server) noexcept : previous(currentCallbackServer) { currentCallbackServer = server; }
    ~CallbackScope() { currentCallbackServer = previous; }
};
enum class OpKind { Accept, Read, Write, Close, Task, Resume };
struct Operation {
    OVERLAPPED overlapped{}; // Must remain first: the OS returns this address.
    OpKind kind;
    CIOObject* owner;
    explicit Operation(OpKind k, CIOObject* p) : kind(k), owner(p) {}
    OVERLAPPED* external=nullptr;
    OVERLAPPED* address()noexcept{return external?external:&overlapped;}
    void reset()noexcept{*address()=OVERLAPPED{};}
};
static_assert(offsetof(Operation, overlapped) == 0, "OVERLAPPED address contract");
class Server : public CIOServer {
public:
    std::array<std::byte,0x68> originalServer28_{};
    std::unique_ptr<IServerCallback> socketFactory; //original+0x90
    L1Callbacks* const callbacks;
    CIOBufferPool buffers;
    NtStatusToErrorFn ntStatusToError = nullptr;
    std::vector<std::unique_ptr<IOThreadCallback>> workers;
    std::unique_ptr<ListenThread> listenThread;
    std::unique_ptr<CThreadLocalTimer> listenTimer;
    CUserSocketManager<CIOSocket> registry;
    std::mutex socketMapGate;
    std::map<SOCKET,CIOSocket*> socketMap;
    std::size_t SocketMapCount() noexcept {std::lock_guard<std::mutex> lock(socketMapGate);return socketMap.size();}
    std::mutex codecGate;
    std::shared_ptr<const L1CodecApi> codec;
    std::atomic<std::uint64_t> nextInternalTask{1};
    std::atomic<std::uint32_t> nextSocketNumber{1};
    std::uint32_t AllocateSocketNumber() noexcept {
        for(;;){auto number=nextSocketNumber.fetch_add(1);if(number&&!socketNumbers.ExistKey(number))return number;}
    }
    CObjectMap<CUserSocket> socketNumbers;
    CObjectMap<CUserSocket,8192,27> userSocketMapPlaying;
    CObjectMap<CUserSocket,8192,28> userSocketMapEnterChar;
    std::atomic<std::uint64_t> duplicateEnterDenied{0};
    std::shared_ptr<CPerfMon> perfmon;
    unsigned perfIntervalMs=1000;
    CSocketLimiter socketLimiter;
    void RemoveSocketNumber(CIOSocket* socket) noexcept;
    std::unique_ptr<CThreadLocalTimer::SlotPool> timerBlocks;
    std::vector<std::unique_ptr<CThreadLocalTimer>> timers;
    std::atomic<std::uint64_t> sendsAccepted{0},sendsExecuted{0},framesEncrypted{0},framesPlain{0},gracefulCloses{0},abortiveCloses{0},sendsRejected{0},pendingSends{0},sendOwnerErrors{0};
    std::atomic<std::uint64_t> timerInline{0}, timerPosted{0}, closeStarts{0}, closeTerminals{0};
    std::mutex taskGate;
    std::unordered_map<std::uint64_t, NativeTask*> tasks;
    std::atomic<unsigned> taskCount{0};
    std::atomic<std::uint64_t> nextId{1};
    ReferenceDiagnostics referenceDiagnostics;
    unsigned referenceCheckDelayMs=5000;
    void VerifySocketRelease(CIOSocket*)noexcept;
    std::atomic<int> connections{0};
    std::atomic<bool> stopping{false}, running{false};
    int maxConnections = 5000, workerCount = 8, backlog = 5, recvBytes = 4096;
    bool noDelay = true;
    std::mutex lifecycle, waitMutex;
    std::condition_variable changed;
    bool wsaReady = false;
    bool validSettings = true;
    void notifyChange() noexcept {
        std::lock_guard<std::mutex> lock(waitMutex);
        changed.notify_all();
    }

    explicit Server(L1Callbacks* listener) : callbacks(listener), buffers(65536) {
        host().notify=[](CIOServer* p)noexcept{static_cast<Server*>(p)->NotifyPendingChanged();};
        host().accepted=[](CIOServer* p,SOCKET socket)noexcept{static_cast<Server*>(p)->OnAcceptedSocket(socket);};
        host().failure=[](CIOServer* p,unsigned error)noexcept{static_cast<Server*>(p)->OnWaitFailure(error);};
        WSADATA data{}; wsaReady = WSAStartup(MAKEWORD(2, 2), &data) == 0;
    }
    ~Server() override;
    bool Run(const char* address, unsigned short portNumber) noexcept;
    bool stop() noexcept;
    bool stopAccepting() noexcept;
    bool stopAcceptingImpl() noexcept;
    bool wait(unsigned timeout) noexcept {
        if (currentWorker == this || currentCallbackServer == this) return !running.load();
        std::unique_lock<std::mutex> lock(waitMutex);
        if (timeout == INFINITE) { changed.wait(lock, [&]{return !running.load();}); return true; }
        return changed.wait_for(lock, std::chrono::milliseconds(timeout), [&]{return !running.load();});
    }
    void NotifyPendingChanged()noexcept{notifyChange();}
    void OnAcceptedSocket(SOCKET socket)noexcept;
    void OnWaitFailure(unsigned error) noexcept;
    CThreadLocalTimer* timerFor(unsigned owner) noexcept {return owner==17?listenTimer.get():owner<timers.size()?timers[owner].get():nullptr;}
    CIOSocket* CreateSocket(SOCKET socket)noexcept;
    CIOSocket* CreateSocket(SOCKET socket,sockaddr_in* peer)noexcept override;
    virtual void Remove(SOCKET socket)noexcept; //13
    virtual bool Add(SOCKET socket,CSocket* object)noexcept; //14
    virtual void Shutdown()noexcept{shutdownSucceeded=ShutdownImpl();} //15
    bool shutdownSucceeded=true;
    bool ShutdownSockets()noexcept{Shutdown();return shutdownSucceeded;}

    void worker(unsigned owner = 0) noexcept;
    bool scheduleTask(std::uint64_t token, unsigned delay, CIOSocket* deadlineOwner=nullptr,CIOObject* observer=nullptr) noexcept;
    bool cancelTask(std::uint64_t token) noexcept;
    bool cancelTasks() noexcept;
    void finishTask(NativeTask* task) noexcept;
    CIOSocket* find(std::uint64_t id, bool includeRetiring=false) noexcept;
    bool Add(CIOSocket* c) noexcept;
    void Remove(SOCKET socket,CIOSocket* expected) noexcept;
    void ReleaseHandle(CIOSocket* c) noexcept;
    bool ShutdownImpl()noexcept;
    bool post(Operation& op) noexcept {
        op.owner->retain(ObjectReferenceType::ORT_IOCP_MESSAGE_RETAIN);
        if (PostQueuedCompletionStatus(port(), 0, reinterpret_cast<ULONG_PTR>(op.owner), op.address())) return true;
        op.owner->release(ObjectReferenceType::ORT_IOCP_MESSAGE_RETAIN); return false;
    }
};

struct CIOSocketState final:ObjectAllocated {
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
 mutable std::atomic<std::uint32_t> lastSending{0};bool initialized=false;
 std::uint32_t number=0;bool(*setSession)(void*,int)noexcept=nullptr;
 CIOSocketState(CIOObject* owner,Server* s,std::uint64_t value,std::size_t bytes):server(s),id(value),writes(bytes),reads(s->buffers,bytes),readOp(OpKind::Read,owner),writeOp(OpKind::Write,owner),closeOp(OpKind::Close,owner),resumeOp(OpKind::Resume,owner){}
};
template<class Buffer>class CIOSocketEx : public CIOObject {
public:
    // Verified x64 CIOSocketEx prefix0xb0. L1 state lives in the allocator
    // extension pointer,so it does not move original network fields.
    SOCKET socket;
    std::uint32_t reservedSendBytes=0,original24_=0;
    CExecutionSerializerFast execution_;
    OVERLAPPED readOverlapped_{},writeOverlapped_{};
    void* nativeReadBuffer_=nullptr;void* nativeWriteBuffer_=nullptr;std::uint64_t originalA8_=0;
    CIOSocketState& l1()noexcept{return *static_cast<CIOSocketState*>(ObjectAllocator::Extension(this));}
    const CIOSocketState& l1()const noexcept{return *static_cast<const CIOSocketState*>(ObjectAllocator::Extension(const_cast<CIOSocket*>(this)));}
    auto& server()noexcept{return l1().server;} const auto& server()const noexcept{return l1().server;}
    auto& id()noexcept{return l1().id;} const auto& id()const noexcept{return l1().id;}
    auto& retiredSocket()noexcept{return l1().retiredSocket;} const auto& retiredSocket()const noexcept{return l1().retiredSocket;}
    auto& gate()noexcept{return l1().gate;} const auto& gate()const noexcept{return l1().gate;}
    auto& state()noexcept{return l1().state;} const auto& state()const noexcept{return l1().state;}
    auto& writes()noexcept{return l1().writes;} const auto& writes()const noexcept{return l1().writes;}
    auto& reads()noexcept{return l1().reads;} const auto& reads()const noexcept{return l1().reads;}
    auto& readOp()noexcept{return l1().readOp;} const auto& readOp()const noexcept{return l1().readOp;}
    auto& writeOp()noexcept{return l1().writeOp;} const auto& writeOp()const noexcept{return l1().writeOp;}
    auto& closeOp()noexcept{return l1().closeOp;} const auto& closeOp()const noexcept{return l1().closeOp;}
    auto& resumeOp()noexcept{return l1().resumeOp;} const auto& resumeOp()const noexcept{return l1().resumeOp;}
    auto& pendingSendRequests()noexcept{return l1().pendingSendRequests;} const auto& pendingSendRequests()const noexcept{return l1().pendingSendRequests;}
    auto& gracefulRequested()noexcept{return l1().gracefulRequested;} const auto& gracefulRequested()const noexcept{return l1().gracefulRequested;}
    auto& gracefulSendShutdown()noexcept{return l1().gracefulSendShutdown;} const auto& gracefulSendShutdown()const noexcept{return l1().gracefulSendShutdown;}
    auto& gracefulPeerEof()noexcept{return l1().gracefulPeerEof;} const auto& gracefulPeerEof()const noexcept{return l1().gracefulPeerEof;}
    auto& closeAbortive()noexcept{return l1().closeAbortive;} const auto& closeAbortive()const noexcept{return l1().closeAbortive;}
    auto& gracefulDue()noexcept{return l1().gracefulDue;} const auto& gracefulDue()const noexcept{return l1().gracefulDue;}
    auto& closeOperation()noexcept{return l1().closeOperation;} const auto& closeOperation()const noexcept{return l1().closeOperation;}
    auto& closeError()noexcept{return l1().closeError;} const auto& closeError()const noexcept{return l1().closeError;}
    auto& packetBorrowed()noexcept{return l1().packetBorrowed;} const auto& packetBorrowed()const noexcept{return l1().packetBorrowed;}
    auto& packetDelivering()noexcept{return l1().packetDelivering;} const auto& packetDelivering()const noexcept{return l1().packetDelivering;}
    auto& resumePosted()noexcept{return l1().resumePosted;} const auto& resumePosted()const noexcept{return l1().resumePosted;}
    auto& userPumpActive()noexcept{return l1().userPumpActive;} const auto& userPumpActive()const noexcept{return l1().userPumpActive;}
    auto& ioTerminal()noexcept{return l1().ioTerminal;} const auto& ioTerminal()const noexcept{return l1().ioTerminal;}
    auto& registrationReleased()noexcept{return l1().registrationReleased;} const auto& registrationReleased()const noexcept{return l1().registrationReleased;}
    auto& packetSequence()noexcept{return l1().packetSequence;} const auto& packetSequence()const noexcept{return l1().packetSequence;}
    auto& deadlineToken()noexcept{return l1().deadlineToken;} const auto& deadlineToken()const noexcept{return l1().deadlineToken;}
    auto& deadlineDue()noexcept{return l1().deadlineDue;} const auto& deadlineDue()const noexcept{return l1().deadlineDue;}
    auto& cancelDeadlineToken()noexcept{return l1().cancelDeadlineToken;} const auto& cancelDeadlineToken()const noexcept{return l1().cancelDeadlineToken;}
    auto& loginDue()noexcept{return l1().loginDue;} const auto& loginDue()const noexcept{return l1().loginDue;}
    auto& writeProgressAt()noexcept{return l1().writeProgressAt;} const auto& writeProgressAt()const noexcept{return l1().writeProgressAt;}
    auto& sessionState()noexcept{return l1().sessionState;} const auto& sessionState()const noexcept{return l1().sessionState;}
    auto& local_()noexcept{return l1().local_;} const auto& local_()const noexcept{return l1().local_;}
    auto& remote_()noexcept{return l1().remote_;} const auto& remote_()const noexcept{return l1().remote_;}
    auto& ipReserved_()noexcept{return l1().ipReserved_;} const auto& ipReserved_()const noexcept{return l1().ipReserved_;}
    auto& macIdentity_()noexcept{return l1().macIdentity_;} const auto& macIdentity_()const noexcept{return l1().macIdentity_;}
    auto& protocol_()noexcept{return l1().protocol_;} const auto& protocol_()const noexcept{return l1().protocol_;}
    auto& retiredAccountId_()noexcept{return l1().retiredAccountId_;} const auto& retiredAccountId_()const noexcept{return l1().retiredAccountId_;}
    auto& accountId_()noexcept{return l1().accountId_;} const auto& accountId_()const noexcept{return l1().accountId_;}
    auto& playingRegistered_()noexcept{return l1().playingRegistered_;} const auto& playingRegistered_()const noexcept{return l1().playingRegistered_;}
    auto& characterEntered_()noexcept{return l1().characterEntered_;} const auto& characterEntered_()const noexcept{return l1().characterEntered_;}
    bool sendConstBuffer(L1ConstBuffer*,bool)noexcept;
    bool send(const Span*,unsigned,bool framed=false,bool plainFirst=false,bool constant=false)noexcept;
    // Verified socket virtual prefix12..23. Slot14/15 names are adapter
    // names because their original symbols were not recovered.
    virtual void Initialize()noexcept{ //12;caller owns gate
        int zero=0,enabled=server()->noDelay?1:0;
        l1().initialized=setsockopt(socket,SOL_SOCKET,SO_RCVBUF,reinterpret_cast<const char*>(&zero),sizeof(zero))==0
            &&setsockopt(socket,SOL_SOCKET,SO_SNDBUF,reinterpret_cast<const char*>(&zero),sizeof(zero))==0
            &&setsockopt(socket,IPPROTO_TCP,TCP_NODELAY,reinterpret_cast<const char*>(&enabled),sizeof(enabled))==0
            &&CreateIoCompletionPort(reinterpret_cast<HANDLE>(socket),server()->port(),reinterpret_cast<ULONG_PTR>(static_cast<CIOObject*>(this)),0);
    }
    virtual void OnCreate()noexcept{OnRead();} //13
    virtual bool Slot14SendAllowed()const noexcept{return true;} //14
    virtual bool Slot15SendAllowed()const noexcept{return true;} //15
    virtual void Send(const char* format,...)noexcept; //16
    virtual void SendV(const char* format,va_list arguments)noexcept; //17
    virtual void GracefulClose()noexcept{GracefulClose(30000);} //18
    virtual void OnClose()noexcept{} //19
    virtual void OnRead()noexcept=0; //20
    // L1 extension virtuals follow the original prefix.
    void __async_AsyncSend(NativeSendRequest& request)noexcept{ExecuteSend(request);}
    void __async_AsyncSendConstBuffer(NativeSendRequest& request)noexcept{ExecuteSend(request);}
    void ExecuteSend(NativeSendRequest&)noexcept;
    bool GracefulClose(unsigned timeoutMs)noexcept;
    static void KickWriteCallback(MemoryObject* object,void*)noexcept{
        auto* c=static_cast<CIOSocket*>(object);
        {std::lock_guard<std::mutex> lock(c->gate());c->_Write();}
        c->finishIfReady();
    }
    const sockaddr_storage& LocalAddress()const noexcept{return local_();}
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
    CIOSocketEx(Server* s,SOCKET value,std::uint64_t valueId,std::size_t bufferBytes=std::tuple_size<decltype(Buffer::payload)>::value):socket(value){
        ObjectAllocator::Extension(this)=new CIOSocketState(this,s,valueId,bufferBytes);
        readOp().external=&readOverlapped_;writeOp().external=&writeOverlapped_;
        s->retain();
    }
    ~CIOSocketEx() override {
        auto* extra=&l1();auto* ownerServer=extra->server;
        extra->writes.clear(ownerServer->buffers);extra->reads.clear();
        if(socket!=INVALID_SOCKET)closesocket(socket);
        ObjectAllocator::Extension(this)=nullptr;delete extra;ownerServer->release();
    }
    Operation* DecodeOperation(OVERLAPPED* raw)noexcept{
        if(raw==&readOverlapped_)return &readOp();if(raw==&writeOverlapped_)return &writeOp();return reinterpret_cast<Operation*>(raw);
    }
    void failLocked(int operation, int error,bool abortive=true) noexcept {
        if (state().closing) return;
        state().closing = true; state().paused = true;closeAbortive()=abortive;
        cancelDeadlineToken()=deadlineToken();deadlineToken()=deadlineDue()=0;
        if (UserProtocol()) UserProtocol()->stop();
        closeOperation() = operation; closeError() = error;
        if (socket != INVALID_SOCKET) { retiredSocket() = socket; socket = INVALID_SOCKET; state().closeRetiring = true; }
    }
    void retireSocket() noexcept {
        SOCKET previous;bool abortive;
        {
            std::lock_guard<std::mutex> lock(gate());
            previous = retiredSocket(); retiredSocket() = INVALID_SOCKET;abortive=closeAbortive();
        }
        std::uint64_t cancel;
        {std::lock_guard<std::mutex> lock(gate());cancel=cancelDeadlineToken();cancelDeadlineToken()=0;}
        if(cancel)server()->cancelTask(cancel);
        if (previous == INVALID_SOCKET) return;
        server()->registry.retire(id(),this);
        server()->Remove(previous,this);
        OnClose();
        // The original hook changes parser state/unregisters the socket before
        // linger/close. Java's final resource cleanup stays after I/O drains.
        ++server()->closeStarts;
        CallbackScope callback(server());
        if (auto fn = server()->callbacks->closing.load()) fn(server(), id(), closeOperation(), closeError());
        if(abortive){
            ++server()->abortiveCloses;linger reset{1,0};
            setsockopt(previous,SOL_SOCKET,SO_LINGER,reinterpret_cast<const char*>(&reset),sizeof(reset));
        }else ++server()->gracefulCloses;
        closesocket(previous);
        { std::lock_guard<std::mutex> lock(gate()); state().closeRetiring = false; }
        server()->VerifySocketRelease(this);
    }

    // The posting thread owns an API/I/O reference. Failed submissions balance
    // their speculative reference because they will not produce a completion.
    void readLocked() noexcept {
        const bool graceRead=gracefulRequested()&&gracefulSendShutdown()&&!gracefulPeerEof()&&!state().closing
                &&!state().readPosted&&!state().readDelivering&&!state().acceptDelivering;
        if ((!state().canRead()&&!graceRead) || (!graceRead&&UserProtocol() && (packetBorrowed()||packetDelivering()||resumePosted()||userPumpActive()))) return;
        if (!reads().prepare()) { failLocked(2, 10055); return; }
        nativeReadBuffer_=reads().block()?static_cast<void*>(reads().block()->data.data()-16):nullptr;
        readOp().reset(); state().readPosted = true; retain(ObjectReferenceType::ORT_IO_RETAIN);
        WSABUF buffer{static_cast<ULONG>(std::min<std::size_t>(server()->recvBytes,reads().block()->capacity)), reinterpret_cast<char*>(reads().block()->data.data() + reads().block()->used)};
        DWORD bytes = 0, flags = 0;
        const int result = WSARecv(socket, &buffer, 1, &bytes, &flags, readOp().address(), nullptr);
        if (result == SOCKET_ERROR) {
            const int error = WSAGetLastError();
            if (error != WSA_IO_PENDING) { state().readPosted = false; release(ObjectReferenceType::ORT_IO_RETAIN); failLocked(2, error); }
        }
    }
    void _Write() noexcept {
        if (!execution_.IsOwner() || !state().canWrite() || !writes().head()) return;
        auto* p = writes().head();
        nativeWriteBuffer_=writes().head()?static_cast<void*>(writes().head()->data.data()-16):nullptr;
        writeOp().reset(); state().writePosted = true; retain(ObjectReferenceType::ORT_IO_RETAIN);
        WSABUF buffer{static_cast<ULONG>(p->used - p->offset), reinterpret_cast<char*>(p->data.data() + p->offset)};
        DWORD bytes = 0;
        const int result = WSASend(socket, &buffer, 1, &bytes, 0, writeOp().address(), nullptr);
        if (result == SOCKET_ERROR) {
            const int error = WSAGetLastError();
            if (error != WSA_IO_PENDING) { state().writePosted = false; release(ObjectReferenceType::ORT_IO_RETAIN); failLocked(3, error); }
        }
    }
    void finishIfReady() noexcept {
        {std::lock_guard<std::mutex> lock(gate());
         if(gracefulRequested()&&!state().closing&&!pendingSendRequests()&&!writes().head()
                &&!state().writePosted&&!state().writeDelivering&&!state().acceptDelivering){
             // Windows can reset a zero-buffer overlapped socket on bare
             // closesocket. Explicit half-close sends FIN; consume the peer's
             // FIN before releasing the handle, bounded by gracefulDue.
             if(!gracefulSendShutdown()){
                 if(shutdown(socket,SD_SEND)==SOCKET_ERROR)failLocked(5,WSAGetLastError());
                 else gracefulSendShutdown()=true;
             }
             if(!state().closing){if(gracefulPeerEof())failLocked(5,0,false);else readLocked();}
         }}
        retireSocket();
        bool ready;
        {
            std::lock_guard<std::mutex> lock(gate());
            ready = state().canClose() && !pendingSendRequests() && !resumePosted() && !userPumpActive() && !packetDelivering();
            if (ready) { state().closeQueued = true; closeOp().reset(); }
        }
        if (ready && !server()->post(closeOp())) closeCallback();
    }
    void closeCallback() noexcept {
        // No remaining kernel I/O/delivery can refer to a reused byte buffer.
        {std::lock_guard<std::mutex> lock(gate());ioTerminal()=true;}
        ++server()->closeTerminals;
        CallbackScope callback(server());
        if (auto fn = server()->callbacks->close.load()) fn(server(), id(), closeOperation(), closeError());
        finalizeRegistration();
    }
    void finalizeRegistration() noexcept {
        bool done;
        {std::lock_guard<std::mutex> lock(gate());done=ioTerminal()&&!packetBorrowed()&&!packetDelivering()&&!registrationReleased();if(done){registrationReleased()=true;reads().clear();nativeReadBuffer_=nullptr;}}
        if(done){server()->RemoveSocketNumber(this);server()->ReleaseHandle(this);}
    }
    void pumpPackets() noexcept;
    bool releasePacket(std::uint64_t sequence) noexcept;
    bool queueResume() noexcept;
    bool requestDeadline(unsigned delay) noexcept;
    void deadlineTick(std::uint64_t token,int status) noexcept;
    bool initializeUserSocket(unsigned seed) noexcept {
        std::shared_ptr<const L1CodecApi> api;
        {std::lock_guard<std::mutex> lock(server()->codecGate);api=server()->codec;}
        if(!api||!server()->callbacks->packet.load())return false;
        std::lock_guard<std::mutex> lock(gate());
        if(state().closing||state().readPosted||state().readDelivering||!state().paused)return false;
        if(UserProtocol())return false;
        return ConfigureUserProtocol(std::move(api),seed);
    }
    void disconnect(int operation = 5, int error = 0) noexcept {
        { std::lock_guard<std::mutex> lock(gate()); failLocked(operation, error); }
        finishIfReady();
    }
    bool pause(bool paused) noexcept {
        bool open,profile;
        {std::lock_guard<std::mutex> lock(gate());open=!state().closing;profile=UserProtocol()!=nullptr;
         if(open){state().paused=paused;if(UserProtocol())UserProtocol()->pause(paused||packetBorrowed(),monotonicMs());else readLocked();open=!state().closing;}}
        if(open&&profile&&!paused)open=queueResume();
        finishIfReady();return open;
    }
    void accepted(int result) noexcept {
        {
            std::lock_guard<std::mutex> lock(gate());
            state().acceptDelivering = false;
            if (result != 0) failLocked(1, ERROR_ACCESS_DENIED);
            readLocked();
        }
        if(result==0)OnCreate();
        if(!execution_.TryAddExecutable(this,KickWriteCallback,nullptr))disconnect(3,10055);
        finishIfReady();
    }
    static void WriteCallback(MemoryObject* owner,void* argument) noexcept {
        auto* socket=static_cast<CIOSocket*>(owner);const auto data=reinterpret_cast<std::uintptr_t>(argument);
        const auto error=static_cast<unsigned>(data);socket->ProcessIO(&socket->writeOp(),error==0,static_cast<unsigned>(data>>32),error);
    }
    static void CloseCallback(MemoryObject* owner,void*) noexcept{static_cast<CIOSocket*>(owner)->closeCallback();}
    void OnIOCallback(int completionOk,unsigned completionBytes,OVERLAPPED* completionRaw)noexcept override{auto operation=IOCompletionContext::operation?IOCompletionContext::operation:static_cast<void*>(completionRaw);auto ok=completionOk!=0;auto bytes=completionBytes;auto error=IOCompletionContext::error;
        if(!operation){OnTimerExpired(static_cast<int>(completionBytes));return;}
        auto& op=*static_cast<Operation*>(operation);
        if(op.kind==OpKind::Write){
            const auto status=ok?0u:(error?error:31u);
            const auto data=(static_cast<std::uintptr_t>(bytes)<<32)|status;
            if(execution_.TryAddExecutable(this,WriteCallback,reinterpret_cast<void*>(data)))return;
            ProcessIO(operation,false,0,10055);return;
        }
        if(op.kind==OpKind::Close){
            if(!execution_.TryAddExecutable(this,CloseCallback,nullptr))closeCallback();return;
        }
        ProcessIO(operation,ok,bytes,error);
    }
    void ProcessIO(void* operation, bool ok, unsigned bytes, unsigned error) noexcept {
        auto& op = *static_cast<Operation*>(operation);
        if (op.kind == OpKind::Close) { closeCallback(); return; }
        if (op.kind == OpKind::Resume) {
            {std::lock_guard<std::mutex> lock(gate());resumePosted()=false;userPumpActive()=true;}
            OnRead();
            {std::lock_guard<std::mutex> lock(gate());userPumpActive()=false;readLocked();_Write();}
            finishIfReady();return;
        }
        CIOBuffer* retired = nullptr;
        const unsigned char* data = nullptr;
        bool deliver = false;
        {
            std::lock_guard<std::mutex> lock(gate());
            const bool reading = op.kind == OpKind::Read;
            if (reading) state().readPosted = false; else state().writePosted = false;
            if (!state().closing) {
                if(reading&&gracefulRequested()&&ok){if(!bytes)gracefulPeerEof()=true;} // discard input while draining; consume peer FIN
                else if (!ok || bytes == 0) failLocked(reading ? 2 : 3, static_cast<int>(error));
                else if (reading && bytes <= static_cast<unsigned>(server()->recvBytes)) {
                    if (reads().append(bytes)) {
                        state().readDelivering = true; data = reads().block()->data.data() + reads().block()->offset; deliver = true;
                    } else failLocked(2, ERROR_INVALID_DATA);
                } else if (!reading && writes().head()) {
                    data = writes().head()->data.data() + writes().head()->offset;
                    if (writes().complete(bytes, retired)) {nativeWriteBuffer_=writes().head()?static_cast<void*>(writes().head()->data.data()-16):nullptr; writeProgressAt()=monotonicMs();state().writeDelivering = true; deliver = true; }
                    else failLocked(3, ERROR_INVALID_DATA);
                } else failLocked(reading ? 2 : 3, ERROR_INVALID_DATA);
            }
        }
        int result = 0;
        if (deliver) {
            const auto fn = (op.kind == OpKind::Read ? server()->callbacks->receive.load() : server()->callbacks->send.load());
            if (op.kind == OpKind::Read && UserProtocol()) {
                if (!UserProtocol()->feed(data,bytes,monotonicMs(),[&](const unsigned char* frame,unsigned size){return UserProtocol()->codec->feed(frame,size);}))result=2;
                else OnRead();
            } else if(fn)result=fn(server(),id(),data,static_cast<int>(bytes));
        }
        {
            std::lock_guard<std::mutex> lock(gate());
            if (op.kind == OpKind::Read) { if (deliver) reads().consume(bytes); state().readDelivering = false; } else state().writeDelivering = false;
            if (result != 0) failLocked(op.kind == OpKind::Read ? 2 : 3, ERROR_INVALID_DATA);
            readLocked(); _Write();
        }
        server()->buffers.recycle(retired);
        finishIfReady();
    }
};

// A request owns a copy before the C API returns. It can outlive Java buffers
// and the submitting thread, while sharing the WRITE-completion serializer.
// The FFM handle owns the producer reference. Requests retain the original
// CIOBufferEx block directly; a queued request never borrows a Java pointer.
struct L1ConstBuffer {
    Server* const server;CIOBuffer* const block;
    L1ConstBuffer(Server* s,CIOBuffer* b)noexcept:server(s),block(b){server->retain();}
    ~L1ConstBuffer(){server->buffers.recycle(block);server->release();}
};
class NativeSendRequest final:public CIOObject {
public:
    CIOSocket* const socket;std::vector<unsigned char> bytes;
    CIOBuffer* const sharedBlock=nullptr;
    const bool framed,plainFirst,constant;std::atomic<int> result{0};
    NativeSendRequest(CIOSocket* owner,const Span* spans,unsigned count,std::size_t total,bool frames,bool plain,bool immutable)
        :socket(owner),bytes(total),framed(frames),plainFirst(plain),constant(immutable){
        socket->retain(ObjectReferenceType::ORT_IO_SOCKET_WRITE);std::size_t at=0;
        for(unsigned i=0;i<count;++i){if(spans[i].size)std::memcpy(bytes.data()+at,spans[i].data,spans[i].size);at+=spans[i].size;}
    }
    NativeSendRequest(CIOSocket* owner,L1ConstBuffer& source,bool plain)noexcept
        :socket(owner),sharedBlock(source.block),framed(true),plainFirst(plain),constant(true){
        socket->retain(ObjectReferenceType::ORT_IO_SOCKET_WRITE);CIOBufferPool::retain(sharedBlock);
    }
    const unsigned char* data()const noexcept{return sharedBlock?sharedBlock->data.data():bytes.data();}
    std::size_t size()const noexcept{return sharedBlock?sharedBlock->used:bytes.size();}
    ~NativeSendRequest()override{if(sharedBlock)socket->server()->buffers.recycle(sharedBlock);socket->release(ObjectReferenceType::ORT_IO_SOCKET_WRITE);}
    static void Execute(MemoryObject* object,void*)noexcept{
        auto& request=*static_cast<NativeSendRequest*>(object);
        if(request.constant)request.socket->__async_AsyncSendConstBuffer(request);else request.socket->__async_AsyncSend(request);
    }
    void OnIOCallback(int completionOk,unsigned completionBytes,OVERLAPPED* completionRaw)noexcept override{}
    bool ValidFrames()const noexcept{
        const auto* input=data();const auto length=size();std::size_t at=0;while(at<length){
            if(length-at<2)return false;
            const auto n=static_cast<unsigned>(input[at])|(static_cast<unsigned>(input[at+1])<<8);
            if(n<2||n>length-at)return false;at+=n;
        }return at==length;
    }
};
template<class Buffer>
bool CIOSocketEx<Buffer>::send(const Span* spans,unsigned count,bool framed,bool plainFirst,bool constant)noexcept {
    ObjectRef lifetime(this);std::size_t total=0;
    for(unsigned i=0;i<count;++i){if((!spans[i].data&&spans[i].size)||spans[i].size>MaxQueuedBytes-total){++server()->sendsRejected;return false;}total+=spans[i].size;}
    if(!total)return true;
    {
        std::lock_guard<std::mutex> lock(gate());
        if(state().closing||gracefulRequested()||(framed&&!UserProtocol())||total>MaxQueuedBytes-writes().size()-reservedSendBytes){++server()->sendsRejected;return false;}
        reservedSendBytes+=total;++pendingSendRequests();++server()->pendingSends;
    }
    NativeSendRequest* request=nullptr;
    try{request=new NativeSendRequest(this,spans,count,total,framed,plainFirst,constant);}catch(...){}
    if(!request||(framed&&!request->ValidFrames())){
        if(request)request->release();
        {std::lock_guard<std::mutex> lock(gate());reservedSendBytes-=total;--pendingSendRequests();--server()->pendingSends;}
        ++server()->sendsRejected;finishIfReady();return false;
    }
    ObjectRef requestHold(request,false);
    if(!execution_.TryAddExecutable(request,NativeSendRequest::Execute,nullptr)){
        {std::lock_guard<std::mutex> lock(gate());reservedSendBytes-=total;--pendingSendRequests();--server()->pendingSends;}
        ++server()->sendsRejected;finishIfReady();return false;
    }
    ++server()->sendsAccepted;return request->result.load(std::memory_order_acquire)>=0;
}
template<class Buffer>
bool CIOSocketEx<Buffer>::sendConstBuffer(L1ConstBuffer* source,bool plainFirst)noexcept {
    ObjectRef lifetime(this);
    if(!source||source->server!=server())return false;
    const auto total=source->block->used;
    {
        std::lock_guard<std::mutex> lock(gate());
        if(state().closing||gracefulRequested()||!UserProtocol()||!total||total>MaxQueuedBytes-writes().size()-reservedSendBytes){++server()->sendsRejected;return false;}
        reservedSendBytes+=total;++pendingSendRequests();++server()->pendingSends;
    }
    auto* request=new(std::nothrow) NativeSendRequest(this,*source,plainFirst);
    ObjectRef requestHold(request,false);
    if(!request||!execution_.TryAddExecutable(request,NativeSendRequest::Execute,nullptr)){
        {std::lock_guard<std::mutex> lock(gate());reservedSendBytes-=total;--pendingSendRequests();--server()->pendingSends;}
        ++server()->sendsRejected;finishIfReady();return false;
    }
    ++server()->sendsAccepted;return request->result.load(std::memory_order_acquire)>=0;
}
template<class Buffer>
void CIOSocketEx<Buffer>::ExecuteSend(NativeSendRequest& request)noexcept {
    bool ok=false;
    {
        std::lock_guard<std::mutex> lock(gate());
        reservedSendBytes-=request.size();--pendingSendRequests();--server()->pendingSends;++server()->sendsExecuted;
        if(!execution_.IsOwner()){++server()->sendOwnerErrors;failLocked(3,31);}
        if(!state().closing){
            ok=true;
            if(request.framed){for(std::size_t at=0;at<request.size();){const auto length=request.data()[at]|(static_cast<unsigned>(request.data()[at+1])<<8);NotifySending(length>2?request.data()[at+2]:0,static_cast<unsigned short>(length));at+=length;}}
        const bool wasEmpty=!writes().head();
            if(request.constant){
                // Original __async_AsyncSendConstBuffer copies/encrypts into
                // socket-owned output, leaving its reference-counted source
                // untouched. L1 frames can span several 16K pool blocks.
                SendChain prepared(LargeBufferBytes);Span source{request.data(),request.size()};
                ok=prepared.append(server()->buffers,&source,1);
                if(ok){
                    SendChain::Cursor cursor(prepared);std::vector<unsigned char> scratch;
                    std::size_t at=0;bool plain=request.plainFirst;
                    while(at<request.size()){
                        const unsigned size=request.data()[at]|(static_cast<unsigned>(request.data()[at+1])<<8);
                        const auto body=size-2;cursor.advance(2);
                        if(plain){++server()->framesPlain;plain=false;cursor.advance(body);}
                        else {
                            unsigned char empty=0;auto* contiguous=body?cursor.contiguous(body):&empty;
                            if(contiguous){
                                if(!UserProtocol()->codec->encrypt(contiguous,static_cast<int>(body))){ok=false;break;}
                                cursor.advance(body);
                            }else{
                                try{scratch.assign(request.data()+at+2,request.data()+at+size);}catch(...){ok=false;break;}
                                if(!UserProtocol()->codec->encrypt(scratch.data(),static_cast<int>(body))){ok=false;break;}
                                cursor.copy(scratch.data(),body);
                            }
                            ++server()->framesEncrypted;
                        }
                        at+=size;
                    }
                    if(ok)ok=writes().adopt(server()->buffers,prepared);
                }
                prepared.clear(server()->buffers);
            }else if(request.framed){
                std::size_t at=0;bool plain=request.plainFirst;
                while(at<request.bytes.size()){
                    const unsigned size=request.bytes[at]|(static_cast<unsigned>(request.bytes[at+1])<<8);
                    if(plain){++server()->framesPlain;plain=false;}
                    else if(!UserProtocol()->codec->encrypt(request.bytes.data()+at+2,static_cast<int>(size-2))){ok=false;break;}
                    else ++server()->framesEncrypted;
                    at+=size;
                }
            }
            Span bytes{request.bytes.data(),request.bytes.size()};
            if(ok&&!request.constant)ok=writes().append(server()->buffers,&bytes,1);
            if(ok){if(wasEmpty)writeProgressAt()=monotonicMs();_Write();ok=!state().closing;}
            else failLocked(3,10055); // a failed cipher/post cannot leave a usable key stream
        }
        request.result.store(ok?1:-1,std::memory_order_release);
    }
    finishIfReady();
}
template<class Buffer>
bool CIOSocketEx<Buffer>::GracefulClose(unsigned timeoutMs)noexcept {
    ObjectRef lifetime(this);
    {
        std::lock_guard<std::mutex> lock(gate());if(state().closing)return true;if(gracefulRequested())return true;
        gracefulRequested()=true;gracefulDue()=monotonicMs()+timeoutMs;state().paused=true;
        if(UserProtocol())UserProtocol()->pause(true,monotonicMs());
    }
    if(!execution_.TryAddExecutable(this,KickWriteCallback,nullptr)){disconnect(5,10055);return false;}
    {std::lock_guard<std::mutex> lock(gate());if(state().closing)return true;}
    if(!requestDeadline(std::min(timeoutMs,5000u))){
        {std::lock_guard<std::mutex> lock(gate());if(state().closing)return true;}
        disconnect(5,10055);return false;
    }
    return true;
}

// Actual transport hierarchy: CIOSocket owns overlapped I/O and buffer life;
// CSocket adds peer addresses; CUserSocket owns L1 parsing/cipher state.
class CSocket : public CIOSocketEx<CIOBufferEx<16384>> {
    std::array<std::byte,8> originalB0_{};
    std::uint32_t peerAddress_=0,originalBC_=0;
    std::array<std::byte,0x40> originalC0_{};
public:
    using CIOSocket::SendV;using CIOSocket::GracefulClose;
    virtual void SetAddress(in_addr value)noexcept{peerAddress_=value.s_addr;}
    virtual in_addr* GetAddress(in_addr* value)const noexcept{if(value)value->s_addr=peerAddress_;return value;}
    virtual void OnSending(unsigned char,unsigned short)const noexcept{}
    bool SendV(const Span* spans,unsigned count,bool plain)noexcept{return send(spans,count,true,plain);}
    bool SendConstBuffer(const Span* spans,unsigned count,bool plain)noexcept{return send(spans,count,true,plain,true);}
    void __async_AsyncSend(NativeSendRequest& request)noexcept{ExecuteSend(request);}
    void __async_AsyncSendConstBuffer(NativeSendRequest& request)noexcept{ExecuteSend(request);}
    CSocket(Server* ownerServer,SOCKET sockValue,std::uint64_t connectionId):CIOSocketEx<CIOBufferEx<16384>>(ownerServer,sockValue,connectionId){
        int length=sizeof(local_());getsockname(socket,reinterpret_cast<sockaddr*>(&local_()),&length);
        length=sizeof(remote_());getpeername(socket,reinterpret_cast<sockaddr*>(&remote_()),&length);
        if(remote_().ss_family==AF_INET)SetAddress(reinterpret_cast<const sockaddr_in*>(&remote_())->sin_addr);
    }
    ~CSocket()override{OnSocketRetired();}
    bool ReserveIP()noexcept{
        if(remote_().ss_family!=AF_INET)return false;
        const auto key=reinterpret_cast<const sockaddr_in*>(&remote_())->sin_addr.s_addr;
        if(!server()->socketLimiter.IncUserSocketPerIP(key))return false;ipReserved_()=true;return true;
    }
    bool SetMacIdentity(std::uint64_t identity)noexcept{
        std::lock_guard<std::mutex> lock(gate());if(state().closing||!identity||macIdentity_().load())return false;
        if(!server()->socketLimiter.IncUserSocketPerMAC(identity))return false;macIdentity_()=identity;return true;
    }
    bool HasMacIdentity()const noexcept{return macIdentity_().load()!=0;}
    void OnSocketRetired()noexcept{
        if(ipReserved_().exchange(false))server()->socketLimiter.DecUserSocketPerIP(reinterpret_cast<const sockaddr_in*>(&remote_())->sin_addr.s_addr);
        if(auto mac=macIdentity_().exchange(0))server()->socketLimiter.DecUserSocketPerMAC(mac);
    }
    const sockaddr_storage& LocalAddress()const noexcept{return local_();}
    const sockaddr_storage& RemoteAddress()const noexcept{return remote_();}
};
class CUserSocket final : public CSocket {
    ReferenceTrace referencesTrace_; //0x100..0x4df
    std::array<std::byte,8> original4E0_{};
    const std::uint32_t number_; //0x4e8
    std::array<std::byte,0xcd8-0x4ec> originalL2Profile_{};
    void ClearAccountLocked()noexcept{
        if(characterEntered_()){server()->userSocketMapEnterChar.DeleteObject(accountId_(),this);characterEntered_()=false;}
        if(playingRegistered_()){server()->userSocketMapPlaying.DeleteObject(number_,this);playingRegistered_()=false;}
        accountId_()=0;
    }
public:
    static CIOSocket* Allocate(Server* ownerServer,SOCKET sockValue)noexcept{try{return new CUserSocket(ownerServer,sockValue,ownerServer->nextId.fetch_add(1));}catch(...){return nullptr;}}
    CUserSocket(Server* ownerServer,SOCKET sockValue,std::uint64_t connectionId):CSocket(ownerServer,sockValue,connectionId),number_(ownerServer->AllocateSocketNumber()){l1().number=number_;l1().setSession=[](void* owner,int phase)noexcept{return static_cast<CUserSocket*>(owner)->SetSessionStateLocked(phase);};referencesTrace_.add(ObjectReferenceType::ORT_CREATE_DELETE,__FILE__,__LINE__);}
    ~CUserSocket()override{ClearAccountLocked();}
    using MemoryObject::retain;using MemoryObject::release;
    MemoryObject* retain(const char* file,unsigned line,ObjectReferenceType type)noexcept override{return retainTracked(&referencesTrace_,type,file,line);}
    unsigned release(const char* file,unsigned line,ObjectReferenceType type,bool=false)noexcept override{return releaseTracked(&referencesTrace_,type,file,line);}
    void ReportReferences(const char*,unsigned)const noexcept override{
        std::array<std::uint64_t,ReferenceRecordWords> row{};row[1]=monotonicMs();row[2]=objectId();row[3]=number_;row[4]=id();row[6]=referenceCount();row[7]=row[6];row[8]=4;row[9]=timerCount();row[10]=static_cast<std::uint64_t>(referencesTrace_.snapshot(row.data()+12));server()->referenceDiagnostics.record(row);
    }
    bool Slot14SendAllowed()const noexcept override{return socket!=INVALID_SOCKET&&!state().closing;}
    bool Slot15SendAllowed()const noexcept override{return Slot14SendAllowed()&&reservedSendBytes<=MaxQueuedBytes;}
    void OnRead()noexcept override{pumpPackets();}
    void OnSending(unsigned char opcode,unsigned short length)const noexcept override{l1().lastSending.store((std::uint32_t(opcode)<<16)|length,std::memory_order_relaxed);}
    std::int64_t ReferenceSnapshot(std::uint64_t* out)const noexcept{return referencesTrace_.snapshot(out);}
    std::uint32_t ReferenceAccount()const noexcept{return accountId_()?accountId_():retiredAccountId_();}
    std::uint32_t SocketNumber()const noexcept{return number_;}
    std::uint32_t AccountId()const noexcept{return accountId_();}
    bool CharacterEntered()const noexcept{return characterEntered_();}
    bool SetAccountIdentityLocked(std::uint32_t account)noexcept{
        if(!account){ClearAccountLocked();return true;}
        if(server()->socketLimiter.RequiresMacIdentity()&&!HasMacIdentity())return false;
        if(accountId_())return accountId_()==account;
        if(!server()->userSocketMapPlaying.Insert(number_,this))return false;
        accountId_()=account;retiredAccountId_()=account;playingRegistered_()=true;return true;
    }
    bool SetCharacterEnteredLocked(bool entered)noexcept{
        if(entered==characterEntered_())return true;
        if(!entered){server()->userSocketMapEnterChar.DeleteObject(accountId_(),this);characterEntered_()=false;return true;}
        if(!accountId_()||!playingRegistered_())return false;
        if(!server()->userSocketMapEnterChar.Insert(accountId_(),this)){++server()->duplicateEnterDenied;return false;}
        characterEntered_()=true;return true;
    }
    bool SetSessionStateLocked(int phase)noexcept{
        if(phase==0||phase==4)ClearAccountLocked();
        else if(phase==1||phase==3)SetCharacterEnteredLocked(false);
        else if(phase==2&&accountId_()&&!SetCharacterEnteredLocked(true))return false;
        sessionState()=phase;return true;
    }
    void OnClose()noexcept override{
        {std::lock_guard<std::mutex> lock(gate());if(!retiredAccountId_())retiredAccountId_()=accountId_();ClearAccountLocked();}
        CSocket::OnSocketRetired();
    }

};


template<class Buffer>
void CIOSocketEx<Buffer>::NotifySending(unsigned char opcode,unsigned short length)const noexcept{
 static_cast<const CSocket*>(static_cast<const CIOSocket*>(this))->OnSending(opcode,length);
}
template<class Buffer>
void CIOSocketEx<Buffer>::Send(const char* format,...)noexcept{va_list args;va_start(args,format);SendV(format,args);va_end(args);}
template<class Buffer>
void CIOSocketEx<Buffer>::SendV(const char* format,va_list arguments)noexcept{
 std::vector<unsigned char> body;
 if(!AssemblePacket(format,arguments,body)){disconnect(3,ERROR_INVALID_DATA);return;}
 try{
  const auto size=body.size()+2;std::vector<unsigned char> frame(size);frame[0]=static_cast<unsigned char>(size);frame[1]=static_cast<unsigned char>(size>>8);
  if(!body.empty())std::memcpy(frame.data()+2,body.data(),body.size());Span span{frame.data(),frame.size()};send(&span,1,true,false);
 }catch(...){disconnect(3,10055);}
}
static_assert(sizeof(CExecutionSerializerFast)==0x30);
static_assert(sizeof(CIOSocket)==0xb0);
static_assert(sizeof(CSocket)==0x100);
static_assert(sizeof(CUserSocket)==0xcd8);

template<class Buffer>
void CIOSocketEx<Buffer>::pumpPackets() noexcept {
    for(;;){
        PlainPacket packet;int size;
        {
            std::lock_guard<std::mutex> lock(gate());
            if(state().closing||state().paused||!UserProtocol()||packetBorrowed())return;
            size=UserProtocol()->codec->next(packet);
            if(size==-2)return;
            if(size!=-3&&size<=0){failLocked(2,ERROR_INVALID_DATA);return;}
            if(size>0){packetBorrowed()=true;packetSequence()=packet.sequence;retain(ObjectReferenceType::ORT_SHARED_CONTAINER);UserProtocol()->pause(true,monotonicMs());}
            packetDelivering()=true;
        }
        int result=2;
        if(auto fn=server()->callbacks->packet.load())result=fn(server(),id(),packet.sequence,packet.data,size==-3?0:size);
        bool rejectLease;
        {
            std::lock_guard<std::mutex> lock(gate());packetDelivering()=false;
            rejectLease=result!=0&&size>0&&packetBorrowed()&&packetSequence()==packet.sequence;
            if(result!=0)failLocked(2,ERROR_INVALID_DATA);
        }
        if(rejectLease)releasePacket(packet.sequence);
        if(result!=0)return;
    }
}
template<class Buffer>
bool CIOSocketEx<Buffer>::queueResume() noexcept {
    bool post=false;
    {
        std::lock_guard<std::mutex> lock(gate());
        if(state().closing)return false;
        if(!UserProtocol()||state().paused||packetBorrowed()||packetDelivering()||resumePosted()||userPumpActive()||state().readPosted||state().readDelivering||state().acceptDelivering)return true;
        resumePosted()=true;resumeOp().reset();post=true;
    }
    if(post&&!server()->post(resumeOp())){
        {std::lock_guard<std::mutex> lock(gate());resumePosted()=false;failLocked(2,static_cast<int>(GetLastError()));}
        finishIfReady();return false;
    }
    return true;
}
template<class Buffer>
bool CIOSocketEx<Buffer>::releasePacket(std::uint64_t sequence) noexcept {
    {
        std::lock_guard<std::mutex> lock(gate());
        if(!packetBorrowed()||packetSequence()!=sequence||!UserProtocol()||!UserProtocol()->codec->release(sequence))return false;
        packetBorrowed()=false;UserProtocol()->pause(state().paused,monotonicMs());
    }
    queueResume();finalizeRegistration();release(ObjectReferenceType::ORT_SHARED_CONTAINER);return true;
}
template<class Buffer>
bool CIOSocketEx<Buffer>::requestDeadline(unsigned delay) noexcept {
    if(delay>0x7fffffffu)return false;
    const auto now=monotonicMs(),due=now+std::max(delay,1u);
    std::uint64_t previous,token;
    {
        std::lock_guard<std::mutex> lock(gate());
        if(state().closing||(!UserProtocol()&&!gracefulRequested()))return false;
        if(deadlineToken()&&deadlineDue()<=due)return true;
        previous=deadlineToken();
        token=0x8000000000000000ull|server()->nextInternalTask.fetch_add(1);
        deadlineToken()=token;deadlineDue()=due;
    }
    if(previous)server()->cancelTask(previous);
    const bool scheduled=server()->scheduleTask(token,std::max(delay,1u),this);
    bool cancel=false;
    {
        std::lock_guard<std::mutex> lock(gate());
        cancel=state().closing||deadlineToken()!=token;
        if(!scheduled&&deadlineToken()==token)deadlineToken()=deadlineDue()=0;
    }
    if(scheduled&&cancel)server()->cancelTask(token);
    return scheduled&&!cancel;
}
template<class Buffer>
void CIOSocketEx<Buffer>::deadlineTick(std::uint64_t token,int status) noexcept {
    bool expired=false;unsigned next=5000;
    {
        std::lock_guard<std::mutex> lock(gate());
        if(state().closing||token!=deadlineToken())return;
        deadlineToken()=deadlineDue()=0;const auto now=monotonicMs();
        expired=status!=0||(gracefulRequested()&&now>=gracefulDue())||(loginDue()&&now>=loginDue())||(UserProtocol()&&UserProtocol()->expired(now))
                ||(writes().head()&&now-writeProgressAt()>=30000);
        if(gracefulRequested()&&gracefulDue()>now)next=static_cast<unsigned>(std::min<std::uint64_t>(next,gracefulDue()-now));
        if(loginDue()&&loginDue()>now)next=static_cast<unsigned>(std::min<std::uint64_t>(next,loginDue()-now));
    }
    if(expired){disconnect(2,status?status:10060);return;}
    if(auto fn=server()->callbacks->deadline.load()){
        const int result=fn(server(),id());
        if(result<0){disconnect(2,10060);return;}
        if(result>0)next=std::min(next,static_cast<unsigned>(result));
    }
    if(!requestDeadline(next)){
        bool closed;{std::lock_guard<std::mutex> lock(gate());closed=state().closing||deadlineToken()!=0;}
        if(!closed)disconnect(2,10055);
    }
}

enum class TaskState { Pending, Queued, Running, Cancelled, Done };
class NativeTask final : public CIOObject {
public:
    Server* const server;
    const std::uint64_t token;
    const unsigned owner;
    CIOSocket* const deadlineOwner;
    CIOObject* const observer;bool ownersLinked=false;
    Operation op{OpKind::Task, this};
    std::atomic<TaskState> state{TaskState::Pending};
    static unsigned registeringOwner(Server* s) noexcept {
        if (currentWorker == s) return currentWorkerIndex;
        const auto registered = s->buffers.currentOwner();
        return (registered >= 64 ? registered - 64 : registered) % static_cast<unsigned>(s->workerCount);
    }
    NativeTask(Server* s,std::uint64_t t,CIOSocket* deadline=nullptr,CIOObject* observe=nullptr):server(s),token(t),owner(registeringOwner(s)),deadlineOwner(deadline),observer(observe){
        server->retain();if(deadlineOwner){deadlineOwner->retain(ObjectReferenceType::ORT_TIMER);deadlineOwner->linkTimer();}
        if(observer){observer->retain(ObjectReferenceType::ORT_TIMER);observer->linkTimer();}ownersLinked=true;
    }
    void UnlinkOwners()noexcept{if(!ownersLinked)return;ownersLinked=false;if(deadlineOwner)deadlineOwner->unlinkTimer();if(observer)observer->unlinkTimer();}
    ~NativeTask() override {UnlinkOwners();if(deadlineOwner)deadlineOwner->release(ObjectReferenceType::ORT_TIMER);if(observer)observer->release(ObjectReferenceType::ORT_TIMER);server->release();}
    void dispatch(int status) noexcept {
        UnlinkOwners();
        if(observer)observer->OnIOCallback(nullptr,status==0,0,status);
        else if(deadlineOwner)deadlineOwner->deadlineTick(token,status);
        else if(auto fn=server->callbacks->task.load())fn(server,token,status);
    }
    bool submit() noexcept {
        auto expected = TaskState::Pending;
        if (!state.compare_exchange_strong(expected, TaskState::Queued)) return false;
        if (server->post(op)) return true;
        const int error = static_cast<int>(GetLastError());
        expected = TaskState::Queued;
        if (state.compare_exchange_strong(expected, TaskState::Running)) {
            dispatch(error ? error : 31);
            state.store(TaskState::Done);
        }
        server->finishTask(this); return false;
    }
    void runInline() noexcept {
        auto expected = TaskState::Pending;
        if (state.compare_exchange_strong(expected, TaskState::Running)) {
            dispatch(0);
            state.store(TaskState::Done);
        }
        server->finishTask(this);
    }
    void OnIOCallback(int completionOk,unsigned completionBytes,OVERLAPPED* completionRaw)noexcept override{
        auto expected = TaskState::Queued;
        if (state.compare_exchange_strong(expected, TaskState::Running)) {
            dispatch(0);
            state.store(TaskState::Done);
        }
        server->finishTask(this);
    }
};
bool Server::scheduleTask(std::uint64_t token,unsigned delay,CIOSocket* deadlineOwner,CIOObject* observer) noexcept {
    if (!token || delay > 0x7fffffffu) return false;
    std::lock_guard<std::mutex> lock(taskGate);
    if (!running.load() || stopping.load() || tasks.size() >= 65536 || tasks.find(token) != tasks.end()) return false;
    NativeTask* task = nullptr;
    try {
        task = new NativeTask(this,token,deadlineOwner,observer);
        tasks.emplace(token, task);
    } catch (...) { if (task) task->release(); return false; }
    ++taskCount;
    bool queued;
    if (delay) queued = timerFor(task->owner)->AddTimer(task, monotonicMs() + delay, static_cast<int>(token), token);
    else {
        task->state.store(TaskState::Queued);
        queued = post(task->op);
    }
    if (!queued) { tasks.erase(token); --taskCount; task->release(); return false; }
    return true;
}
void Server::finishTask(NativeTask* task) noexcept {
    bool removed = false;
    {
        std::lock_guard<std::mutex> lock(taskGate);
        auto found = tasks.find(task->token);
        if (found != tasks.end() && found->second == task) { tasks.erase(found); --taskCount; removed = true; }
    }
    if (removed) { task->release(); notifyChange(); }
}
bool Server::cancelTask(std::uint64_t token) noexcept {
    NativeTask* task;
    {
        std::lock_guard<std::mutex> lock(taskGate);
        auto found = tasks.find(token); if (found == tasks.end()) return false;
        task = found->second; task->retain();
    }
    ObjectRef hold(task, false);
    auto state = task->state.load();
    while (state == TaskState::Pending || state == TaskState::Queued) {
        if (!task->state.compare_exchange_weak(state, TaskState::Cancelled)) continue;
        if (auto* queued = timerFor(task->owner)->cancel(token)) queued->release(ObjectReferenceType::ORT_TIMER);
        finishTask(task); return true;
    }
    return false;
}
bool Server::cancelTasks() noexcept {
    std::vector<std::uint64_t> tokens;
    try {
        std::lock_guard<std::mutex> lock(taskGate);
        tokens.reserve(tasks.size()); for (auto& item : tasks) tokens.push_back(item.first);
    } catch (...) { return false; }
    for (auto token : tokens) cancelTask(token);
    return true;
}
// Name/TimerExpired are confirmed at0xa12674/0xa1291c. L2 game-state
// transitions stay in the L1 owner; this object only observes native holds.
class CUserSocketReleaseVerifier final:public CIOObject {
 struct Context final:ObjectAllocated {
  Server* const server;bool complete=false;unsigned phase=0;
  std::array<std::uint64_t,4> identity{};std::uint64_t closeCause=0;
  explicit Context(Server* s):server(s){}
 };
 CUserSocket* const socket_;const wchar_t* const caller_; //original18/20
 Context& context()noexcept{return *static_cast<Context*>(ObjectAllocator::Extension(this));}
 unsigned Capture(unsigned requested)noexcept{
  auto& owner=context();std::array<std::uint64_t,ReferenceRecordWords> record{};std::uint64_t pending=0;
  record[1]=monotonicMs();for(unsigned i=0;i<4;++i)record[2+i]=owner.identity[i];
  {std::lock_guard<std::mutex> lock(socket_->gate());
   pending=(socket_->state().readPosted?1:0)|(socket_->state().writePosted?2:0)|(socket_->state().readDelivering?4:0)|(socket_->state().writeDelivering?8:0)
    |(socket_->packetBorrowed()?16:0)|(socket_->packetDelivering()?32:0)|(!socket_->ioTerminal()?64:0)|(!socket_->registrationReleased()?128:0)
    |(socket_->pendingSendRequests()?256:0)|((socket_->resumePosted()||socket_->userPumpActive())?512:0);
   auto count=socket_->referenceCount();record[6]=count;record[7]=count?count-1:0;record[9]=pending<<32|socket_->timerCount();record[10]=static_cast<std::uint64_t>(socket_->ReferenceSnapshot(record.data()+12));
  }
  record[8]=requested==2?2:pending?3:record[7]?1:0;record[11]=owner.closeCause;
  if(requested!=2){++owner.server->referenceDiagnostics.checked;if(record[8]==1)++owner.server->referenceDiagnostics.suspects;}
  owner.server->referenceDiagnostics.record(record);return static_cast<unsigned>(record[8]);
 }
public:
 CUserSocketReleaseVerifier(Server* server,CUserSocket* socket,const wchar_t* caller=L"L1 retireSocket"):socket_(socket),caller_(caller){
  auto value=std::make_unique<Context>(server);socket_->retain(ObjectReferenceType::ORT_VERIFIER_RETAIN);
  try{
   std::lock_guard<std::mutex> lock(socket_->gate());value->identity[0]=socket_->objectId();value->identity[1]=socket_->SocketNumber();value->identity[2]=socket_->id();value->identity[3]=socket_->ReferenceAccount();
   value->closeCause=std::uint64_t(static_cast<unsigned>(socket_->closeOperation()))<<32|static_cast<unsigned>(socket_->closeError());
  }catch(...){socket_->release(ObjectReferenceType::ORT_VERIFIER_RETAIN);throw;}
  ObjectAllocator::Extension(this)=value.release();
 }
 ~CUserSocketReleaseVerifier()override{
  auto* owner=&context();if(!owner->complete){++owner->server->referenceDiagnostics.cancelled;Capture(2);}
  socket_->release(ObjectReferenceType::ORT_VERIFIER_RETAIN);--owner->server->referenceDiagnostics.active;
  ObjectAllocator::Extension(this)=nullptr;delete owner;
 }
 void TimerExpired(int error)noexcept override{
  auto& owner=context();if(owner.complete)return;auto status=Capture(error?2:0);
  if(error){++owner.server->referenceDiagnostics.cancelled;owner.complete=true;return;}
  if(status==3&&owner.phase++==0){const auto token=0x8000000000000000ULL|owner.server->nextInternalTask.fetch_add(1);
   if(owner.server->scheduleTask(token,25000,nullptr,this))return;++owner.server->referenceDiagnostics.skipped;}
  owner.complete=true;
 }
 void OnIOCallback(int completionOk,unsigned,OVERLAPPED*)noexcept override{TimerExpired(completionOk?0:static_cast<int>(IOCompletionContext::error?IOCompletionContext::error:995));}
};
static_assert(sizeof(CUserSocketReleaseVerifier)==0x28);
static_assert(sizeof(CIOServer)==0x28);
static_assert(sizeof(CDummyServerCallback)==0x10);
void Server::VerifySocketRelease(CIOSocket* socket)noexcept{
 if(stopping.load()){++referenceDiagnostics.skipped;return;}
 auto* user=dynamic_cast<CUserSocket*>(socket);if(!user||!referenceDiagnostics.reserve())return;
 CUserSocketReleaseVerifier* verifier=nullptr;try{verifier=new CUserSocketReleaseVerifier(this,user);}catch(...){}
 if(!verifier){--referenceDiagnostics.active;++referenceDiagnostics.skipped;return;}
 const auto token=0x8000000000000000ULL|nextInternalTask.fetch_add(1);
 if(scheduleTask(token,referenceCheckDelayMs,nullptr,verifier))++referenceDiagnostics.started;
 else ++referenceDiagnostics.skipped;
 verifier->release();
}
static bool PostOriginalCompletion(CIOObject* object,unsigned bytes,OVERLAPPED* raw)noexcept{
 Server* owner=nullptr;
 if(auto* socket=dynamic_cast<CIOSocket*>(object))owner=socket->server();
 else if(auto* task=dynamic_cast<NativeTask*>(object))owner=task->server;
 else owner=dynamic_cast<Server*>(object);
 return owner&&owner->running.load()&&!owner->stopping.load()&&PostQueuedCompletionStatus(owner->port(),bytes,reinterpret_cast<ULONG_PTR>(object),raw);
}
struct BufferLease {
    Server* server; CIOBuffer* block; std::size_t offset, length;
    BufferLease(Server* s, CIOBuffer* b) : server(s), block(b), offset(b->offset), length(b->used - b->offset) {
        server->retain(); CIOBufferPool::retain(block);
    }
    ~BufferLease() { server->buffers.recycle(block); server->release(); }
};

CIOSocket* Server::find(std::uint64_t id,bool includeRetiring) noexcept {return registry.find(id,includeRetiring);}
bool Server::Add(CIOSocket* c)noexcept{return Add(c->socket,static_cast<CSocket*>(c));}
bool Server::Add(SOCKET value,CSocket* object)noexcept{
    auto* c=static_cast<CIOSocket*>(object);if(!c||c->socket!=value)return false;
    std::lock_guard<std::mutex> publication(c->gate());
    if(c->state().closing)return false;
    if(connections.fetch_add(1)>=maxConnections){connections.fetch_sub(1);return false;}
    if(!registry.insert(c)){connections.fetch_sub(1);notifyChange();return false;}
    bool added=false;
    try{std::lock_guard<std::mutex> lock(socketMapGate);added=socketMap.emplace(c->socket,c).second;if(added)c->retain(ObjectReferenceType::ORT_SOCKET_MAP);}catch(...){}
    if(!added){registry.remove(c->id(),c);connections.fetch_sub(1);notifyChange();}return added;
}
void Server::Remove(SOCKET socket,CIOSocket* expected)noexcept{
 auto* oldExpected=expectedRemoval;auto* oldServer=expectedRemovalServer;expectedRemoval=expected;expectedRemovalServer=this;
 Remove(socket);expectedRemoval=oldExpected;expectedRemovalServer=oldServer;
}
void Server::Remove(SOCKET value)noexcept{
 CIOSocket* removed=nullptr;
 {std::lock_guard<std::mutex> lock(socketMapGate);auto at=socketMap.find(value);
  if(at!=socketMap.end()&&(expectedRemovalServer!=this||!expectedRemoval||at->second==expectedRemoval)){removed=at->second;socketMap.erase(at);}}
 if(removed)removed->release(ObjectReferenceType::ORT_SOCKET_MAP);
}
void Server::ReleaseHandle(CIOSocket* c)noexcept{if(registry.remove(c->id(),c)){connections.fetch_sub(1);notifyChange();}}
bool Server::ShutdownImpl()noexcept {
    std::vector<CIOSocket*> sockets;
    try{std::lock_guard<std::mutex> lock(socketMapGate);sockets.reserve(socketMap.size());
        for(auto& at:socketMap){sockets.push_back(at.second);at.second->retain(ObjectReferenceType::ORT_TEMP_USE);}}
    catch(...){for(auto* socket:sockets)socket->release(ObjectReferenceType::ORT_TEMP_USE);return false;}
    for(auto* socket:sockets){socket->disconnect();socket->release(ObjectReferenceType::ORT_TEMP_USE);}return true;
}
void IOThreadCallback::IOThread_common() noexcept {
    Server* const server=server_;const unsigned owner=owner_;
    SYSTEM_INFO information{};GetSystemInfo(&information);
    if(information.dwNumberOfProcessors)SetThreadAffinityMask(GetCurrentThread(),static_cast<ULONG_PTR>(1)<<(owner%information.dwNumberOfProcessors));

    currentWorker = server;
    currentWorkerIndex = owner;
    CIOBufferPool::OwnerScope bufferOwner(server->buffers, owner);
    TimerDispatchPolicy timerDispatch;
    // One stack batch per worker. Entry.Internal is the status snapshot from
    // this dequeue; a callback may immediately resubmit/reuse its OVERLAPPED.
    std::array<OVERLAPPED_ENTRY, CompletionBatchSize> entries{};
    for (;;) {
        if (owner < server->timers.size()) {
            const auto dueSnapshot = monotonicMs();
            server->timers[owner]->Dispatch(dueSnapshot,[&](CIOObject* due)noexcept{
                if (timerDispatch.post(owner)) { ++server->timerPosted; static_cast<NativeTask*>(due)->submit(); }
                else { ++server->timerInline; static_cast<NativeTask*>(due)->runInline(); }
            });
        }
        ULONG removed = 0;
        if (!GetQueuedCompletionStatusEx(server->port(), entries.data(), CompletionBatchSize,
                &removed, 50, FALSE)) {
            if (GetLastError() == WAIT_TIMEOUT) continue;
            break;
        }
        ULONG exitTokens = 0;
        for (ULONG i = 0; i < removed; ++i) {
            const auto& entry = entries[i];
            if (!entry.lpOverlapped) {
                if (entry.lpCompletionKey == 0) ++exitTokens;
                else {
                    auto* object=reinterpret_cast<CIOObject*>(entry.lpCompletionKey);
                    const auto begin=monotonicMs();server->callbackStarted()[owner]=begin;
                    object->OnIOCallback(nullptr,true,entry.dwNumberOfBytesTransferred,0);
                    ++server->callbackCounts()[owner];server->callbackMillis()[owner].fetch_add(monotonicMs()-begin);
                    server->callbackStarted()[owner]=0;object->release(ObjectReferenceType::ORT_IOCP_MESSAGE_RETAIN);
                }
                continue;
            }
            const LONG status = static_cast<LONG>(static_cast<ULONG>(entry.Internal));
            const bool ok = status >= 0; // NT_SUCCESS, independent of the batch BOOL.
            const DWORD error = ok ? 0 : server->ntStatusToError(status);
            auto* owner=reinterpret_cast<CIOObject*>(entry.lpCompletionKey);
            auto* socketOwner=dynamic_cast<CIOSocket*>(owner);
            auto* operation=socketOwner?socketOwner->DecodeOperation(entry.lpOverlapped):reinterpret_cast<Operation*>(entry.lpOverlapped);
            const auto begin=monotonicMs();server->callbackStarted()[owner_]=begin;
            owner->OnIOCallback(operation,ok,entry.dwNumberOfBytesTransferred,error,entry.lpOverlapped);
            ++server->callbackCounts()[owner_];server->callbackMillis()[owner_].fetch_add(monotonicMs()-begin);
            server->callbackStarted()[owner_]=0;owner->release(operation->kind==OpKind::Read||operation->kind==OpKind::Write?ObjectReferenceType::ORT_IO_RETAIN:ObjectReferenceType::ORT_IOCP_MESSAGE_RETAIN);
        }
        if (exitTokens) {
            // A single worker can dequeue several workers' exit tokens. Finish
            // every real completion, consume one token, and return the others.
            // Breaking at the first token would strand I/O holds and workers.
            for (ULONG i = 1; i < exitTokens; ++i)
                PostQueuedCompletionStatus(server->port(), 0, 0, nullptr);
            break;
        }
    }
    currentWorker = nullptr;
}
void IOThreadCallback::Start(){thread_=std::thread([this]{IOThread_common();});}
void Server::worker(unsigned owner) noexcept {IOThreadCallback callback(this,owner);callback.IOThread_common();}
void ListenThread::Tick() noexcept {
    if(!server_->listenTimer)return;
    server_->listenTimer->Dispatch(monotonicMs(),[&](CIOObject* due)noexcept{
        ++server_->timerPosted;static_cast<NativeTask*>(due)->submit();
    });
}
bool ListenThread::Start() noexcept {
    try{thread_=std::thread([this]{currentWorker=server_;currentWorkerIndex=17;
        CIOBufferPool::OwnerScope owner(server_->buffers,17);Run();currentWorker=nullptr;});return true;}
    catch(...){return false;}
}
void CIOServer::OnWaitCallback() noexcept {
    bool posted=false,held=false;unsigned error=0;const auto begin=monotonicMs();callbackStarted()[17]=begin;
    {
        std::lock_guard<std::mutex> lock(acceptGate());
        if(!accepting().load()||!acceptEvent||acceptQueued()){callbackStarted()[17]=0;return;}
        if(!WSAResetEvent(acceptEvent))error=WSAGetLastError();
        else {
            ++waitCallbacks();acceptQueued()=true;++pendingAccepts();retain();held=true;
            posted=PostQueuedCompletionStatus(port(),0,reinterpret_cast<ULONG_PTR>(static_cast<CIOObject*>(this)),nullptr)!=0;
            if(!posted){error=GetLastError();acceptQueued()=false;--pendingAccepts();}
        }
    }
    ++callbackCounts()[17];callbackMillis()[17].fetch_add(monotonicMs()-begin);callbackStarted()[17]=0;
    if(!posted){if(held)release();NotifyPendingChanged();OnWaitFailure(error?error:31);}
}
void Server::OnWaitFailure(unsigned error) noexcept {
    ++waitFailures();lastWaitError()=error;accepting()=false;
    if(listenThread)listenThread->UnregisterWait(this);
    Close();notifyChange();
}
CIOSocket* Server::CreateSocket(SOCKET socket) noexcept {
    auto* previous=currentFactoryServer;currentFactoryServer=this;
    auto* object=static_cast<CUserSocket*>(socketFactory->CreateSocket(socket));currentFactoryServer=previous;
    if(object&&(!object->ReserveIP()||!socketNumbers.Insert(object->SocketNumber(),object))){
        object->socket=INVALID_SOCKET;object->release(ObjectReferenceType::ORT_CREATE_DELETE);return nullptr;
    }
    return object;
}
CIOSocket* Server::CreateSocket(SOCKET value,sockaddr_in* peer)noexcept{
 auto* c=CreateSocket(value);if(!c)return nullptr;
 if(!Add(value,static_cast<CSocket*>(c))){RemoveSocketNumber(c);c->socket=INVALID_SOCKET;c->release(ObjectReferenceType::ORT_CREATE_DELETE);return nullptr;}
 if(peer)static_cast<CSocket*>(c)->SetAddress(peer->sin_addr);return c;
}
void Server::RemoveSocketNumber(CIOSocket* socket) noexcept {
    if(auto* user=dynamic_cast<CUserSocket*>(socket))socketNumbers.DeleteObject(user->SocketNumber(),user);
}
Server::~Server(){stop();if(wsaReady)WSACleanup();}

void CIOServer::OnIOCallback() noexcept {
    SOCKET socket=INVALID_SOCKET;
    {
        std::lock_guard<std::mutex> lock(acceptGate());
        if(accepting().load()){
            sockaddr_storage peer{};int length=sizeof(peer);
            socket=accept(listening,reinterpret_cast<sockaddr*>(&peer),&length);
        }
        // accept() reenables FD_ACCEPT. Publish the free completion slot while
        // holding the same gate as ResetEvent so no rearmed event is lost.
        acceptQueued()=false;
    }
    ++acceptCallbacks();
    if(socket!=INVALID_SOCKET)OnAcceptedSocket(socket);
    pendingAccepts().fetch_sub(1);NotifyPendingChanged();
}
void Server::OnAcceptedSocket(SOCKET value)noexcept{
 if(value==INVALID_SOCKET)return;sockaddr_in peer{};int length=sizeof(peer);getpeername(value,reinterpret_cast<sockaddr*>(&peer),&length);
 auto* c=CreateSocket(value,&peer);if(!c){closesocket(value);return;}
 ObjectRef temporary(c,false,ObjectReferenceType::ORT_CREATE_DELETE);
 const auto fn=callbacks->accept.load();const int admission=fn?fn(this,c->id(),static_cast<std::uint64_t>(value)):2;
 bool initialized=false;
 {std::lock_guard<std::mutex> lock(c->gate());if(!c->state().closing&&admission==0){c->Initialize();initialized=c->l1().initialized;}}
 c->accepted(initialized?0:2);
}
void CPerfMon::Sample()noexcept {
    std::array<std::uint64_t,16> values{};
    values[1]=monotonicMs();values[2]=currentWorker==server_?currentWorkerIndex:~0u;
#ifndef CTRANSPORT_TEST
    MEMORYSTATUSEX memory{};memory.dwLength=sizeof(memory);
    if(GlobalMemoryStatusEx(&memory)){values[6]=memory.dwMemoryLoad;values[7]=memory.ullTotalPhys;values[8]=memory.ullAvailPhys;values[15]|=1;}
    using MemoryFn=BOOL(WINAPI*)(HANDLE,PPROCESS_MEMORY_COUNTERS,DWORD);
    const auto memoryFn=reinterpret_cast<MemoryFn>(GetProcAddress(GetModuleHandleW(L"kernel32.dll"),"K32GetProcessMemoryInfo"));
    PROCESS_MEMORY_COUNTERS process{};process.cb=sizeof(process);
    if(memoryFn&&memoryFn(GetCurrentProcess(),&process,sizeof(process))){values[4]=process.WorkingSetSize;values[5]=process.PagefileUsage;values[15]|=2;}
    FILETIME create{},exit{},kernel{},user{};
    if(GetProcessTimes(GetCurrentProcess(),&create,&exit,&kernel,&user)){
        const auto ticks=(static_cast<std::uint64_t>(kernel.dwHighDateTime)<<32)+kernel.dwLowDateTime
            +(static_cast<std::uint64_t>(user.dwHighDateTime)<<32)+user.dwLowDateTime;
        if(previousTime_&&values[1]>previousTime_&&ticks>=previousCpu_){
            SYSTEM_INFO info{};GetSystemInfo(&info);const auto elapsed=(values[1]-previousTime_)*10000;
            const auto cpus=std::max<DWORD>(info.dwNumberOfProcessors,1);
            values[3]=std::min<std::uint64_t>(1000,(ticks-previousCpu_)*1000/elapsed/cpus);values[15]|=4;
        }
        previousCpu_=ticks;previousTime_=values[1];
    }
#endif
    values[9]=server_->connections.load();values[10]=server_->taskCount.load();
    values[11]=server_->socketNumbers.size();values[12]=server_->userSocketMapPlaying.size();values[13]=server_->userSocketMapEnterChar.size();
    for(auto& count:server_->callbackCounts())values[14]+=count.load();
    std::lock_guard<std::mutex> lock(gate_);values[0]=latest_[0]+1;latest_=values;
}
bool CPerfMon::Start(unsigned interval)noexcept {
    try{thread_=std::thread([this,interval]{
        currentWorker=server_;currentWorkerIndex=15;CIOBufferPool::OwnerScope owner(server_->buffers,15);
        while(!stopping_.load()){
            Sample();std::unique_lock<std::mutex> lock(gate_);
            changed_.wait_for(lock,std::chrono::milliseconds(interval),[this]{return stopping_.load();});
        }
        currentWorker=nullptr;
    });return true;}catch(...){return false;}
}
bool Server::Run(const char* address, unsigned short number) noexcept {
    std::lock_guard<std::mutex> lock(lifecycle);
    if (running.load() || !wsaReady || !validSettings || !address || workerCount < 1 || workerCount > 64 || maxConnections < 1) return false;
    ntStatusToError = reinterpret_cast<NtStatusToErrorFn>(
        GetProcAddress(GetModuleHandleW(L"ntdll.dll"), "RtlNtStatusToDosError"));
    if (!ntStatusToError) return false;
    SYSTEM_INFO information{};GetSystemInfo(&information);
    workerCount=std::min(workerCount,static_cast<int>(std::min<DWORD>(information.dwNumberOfProcessors,8)));
    if(workerCount<1||!buffers.initialize(static_cast<unsigned>(workerCount),std::max<std::size_t>(static_cast<std::size_t>(maxConnections)*2,workerCount*200)))return false;
    if(!buffers.initializeOwner(15,0,100)||!buffers.initializeOwner(17,300,200)||!Create(address,number,backlog))return false;
    port()=CreateIoCompletionPort(INVALID_HANDLE_VALUE,nullptr,0,workerCount);
    if(!port()){Close();return false;}
    if(!registry.initialize(static_cast<unsigned>(maxConnections))){Close();CloseHandle(port());port()=nullptr;return false;}
    running.store(true); stopping.store(false); accepting().store(true);
    try {
        socketFactory=std::make_unique<CDummyServerCallback>([](SOCKET socket)noexcept->CIOSocket*{return currentFactoryServer?CUserSocket::Allocate(currentFactoryServer,socket):nullptr;});
        timerBlocks.reset(new CThreadLocalTimer::SlotPool(static_cast<std::size_t>(workerCount+1)*200+409));
        for(int i=0;i<workerCount;++i)timers.emplace_back(new CThreadLocalTimer(monotonicMs(),timerBlocks.get()));
        listenTimer.reset(new CThreadLocalTimer(monotonicMs(),timerBlocks.get()));
        listenThread=std::make_unique<ListenThread>(this);
        for(int i=0;i<workerCount;++i){workers.emplace_back(new IOThreadCallback(this,static_cast<unsigned>(i)));workers.back()->Start();}
        auto monitor=std::make_shared<CPerfMon>(this);if(!monitor->Start(perfIntervalMs))throw std::bad_alloc();std::atomic_store(&perfmon,monitor);
        // Publish accept callbacks last: a monitor/thread startup failure must
        // never strand an already admitted socket in partial startup cleanup.
        if(!listenThread->RegisterWait(this,acceptEvent)||!listenThread->Start())throw std::bad_alloc();
    }catch(...){
        accepting()=false;if(auto monitor=std::atomic_load(&perfmon))monitor->Stop();std::atomic_store(&perfmon,std::shared_ptr<CPerfMon>{});if(listenThread)listenThread->Stop();Close();
        for(std::size_t i=0;i<workers.size();++i)PostQueuedCompletionStatus(port(),0,0,nullptr);
        for(auto& worker:workers)worker->Join();workers.clear();listenThread.reset();listenTimer.reset();timers.clear();timerBlocks.reset();CloseHandle(port());port()=nullptr;running=false;notifyChange();return false;
    }
    return true;
}
bool Server::stopAcceptingImpl() noexcept {
    accepting()=false;
    if(listenThread)listenThread->UnregisterWait(this);
    Close();
    std::unique_lock<std::mutex> lock(waitMutex);
    changed.wait(lock, [&]{return pendingAccepts().load() == 0;});
    return true;
}
bool Server::stopAccepting() noexcept {
    if (currentWorker == this || currentCallbackServer == this) return false;
    std::lock_guard<std::mutex> lock(lifecycle);
    return stopAcceptingImpl();
}
bool Server::stop() noexcept {
    if (currentWorker == this || currentCallbackServer == this) return false;
    std::lock_guard<std::mutex> lock(lifecycle);
    if (!running.load()) return true;
    stopping.store(true);if(auto monitor=std::atomic_load(&perfmon))monitor->Stop(); stopAcceptingImpl();
    if(listenThread)listenThread->Stop();
    if (!cancelTasks()) return false;
    { std::unique_lock<std::mutex> guard(waitMutex); changed.wait(guard, [&]{return taskCount.load() == 0;}); }
    if(!ShutdownSockets())return false;
    { std::unique_lock<std::mutex> guard(waitMutex); changed.wait(guard, [&]{return connections.load() == 0;}); }
    for (std::size_t i = 0; i < workers.size(); ++i) PostQueuedCompletionStatus(port(), 0, 0, nullptr);
    for (auto& thread : workers) thread->Join();
    workers.clear();std::atomic_store(&perfmon,std::shared_ptr<CPerfMon>{});listenThread.reset();listenTimer.reset();timers.clear();timerBlocks.reset(); CloseHandle(port()); port() = nullptr;
    running.store(false); notifyChange(); return true;
}

static bool addressText(const sockaddr_storage& storage, char* output, int* capacity, unsigned short* port) noexcept {
    if (!output || !capacity || *capacity <= 0 || !port) return false;
    char text[INET6_ADDRSTRLEN]{}; const void* bytes; int family = storage.ss_family;
    if (family == AF_INET) { auto& a = reinterpret_cast<const sockaddr_in&>(storage); bytes = &a.sin_addr; *port = ntohs(a.sin_port); }
    else if (family == AF_INET6) { auto& a = reinterpret_cast<const sockaddr_in6&>(storage); bytes = &a.sin6_addr; *port = ntohs(a.sin6_port); }
    else return false;
    if (!inet_ntop(family, bytes, text, sizeof(text))) return false;
    const int needed = static_cast<int>(std::strlen(text)) + 1;
    if (*capacity < needed) { *capacity = needed; return false; }
    std::memcpy(output, text, needed); *capacity = needed - 1; return true;
}

template<class F> static void configure(void* p, F apply) {
    if (!p) return;
    auto* server = static_cast<Server*>(p);
    std::lock_guard<std::mutex> lock(server->lifecycle);
    // Configuration is a startup contract, not unsynchronized live tuning.
    if (!server->running.load()) apply(*server);
}
CIO_API unsigned L1Interop_GetAbiVersion() { return L1InteropAbiVersion; }
CIO_API void* L1Interop_CreateCallbacks() { return new(std::nothrow) L1Callbacks; }
CIO_API void L1Interop_ReleaseCallbacks(void* p) { delete static_cast<L1Callbacks*>(p); }
CIO_API void* L1Interop_CreateServer(void* p) { if (!p) return nullptr; try { CIOObject::completionPoster.store(PostOriginalCompletion);return new Server(static_cast<L1Callbacks*>(p)); } catch (...) { return nullptr; } }
CIO_API void L1Interop_ReleaseServer(void* p) { if (p) { auto* s = static_cast<Server*>(p); if (s->stop()) s->release(); } }
CIO_API void L1Interop_SetOnAccept(void* p, AcceptFn fn) { if (p) static_cast<L1Callbacks*>(p)->accept.store(fn); }
CIO_API void L1Interop_SetOnRead(void* p, DataFn fn) { if (p) static_cast<L1Callbacks*>(p)->receive.store(fn); }
CIO_API void L1Interop_SetOnWrite(void* p, DataFn fn) { if (p) static_cast<L1Callbacks*>(p)->send.store(fn); }
CIO_API void L1Interop_SetOnClose(void* p, CloseFn fn) { if (p) static_cast<L1Callbacks*>(p)->close.store(fn); }
CIO_API int Server_Run(void* p, const char* address, unsigned short port) { return p && static_cast<Server*>(p)->Run(address, port); }
CIO_API int CIOServer_Close(void* p) { return p && static_cast<Server*>(p)->stopAccepting(); }
CIO_API int L1Interop_StopServer(void* p) { return p && static_cast<Server*>(p)->stop(); }
CIO_API int L1Interop_WaitServer(void* p, unsigned timeout) { return p && static_cast<Server*>(p)->wait(timeout); }
CIO_API int CIOSocketEx_Close(void* p, std::uint64_t id) {
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id,true); if (!c) return 0;
    ObjectRef hold(c, false);c->disconnect(); return 1;
}
CIO_API int L1Interop_PauseRead(void* p, std::uint64_t id, int paused) {
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); return c->pause(paused != 0);
}
CIO_API int CIOSocket__Write(void* p, std::uint64_t id, const void* data, int length) {
    if (!p || length < 0 || (!data && length)) return 0;
    auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); Span span{static_cast<const unsigned char*>(data), static_cast<std::size_t>(length)};
    return c->send(&span, 1);
}
CIO_API int L1Interop_WriteBuffers(void* p, std::uint64_t id, const WSABUF* buffers, int count) {
    if (!p || !buffers || count < 1 || count > 1024) return 0;
    auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false);
    try {
        std::array<Span, 8> local{};
        std::vector<Span> larger;
        if (count > static_cast<int>(local.size())) larger.resize(count);
        Span* spans = larger.empty() ? local.data() : larger.data();
        for (int i = 0; i < count; ++i) spans[i] = {reinterpret_cast<const unsigned char*>(buffers[i].buf), buffers[i].len};
        return c->send(spans, count);
    } catch (...) { return 0; }
}
CIO_API int L1Interop_GetListenAddress(void* p, char* out, int* capacity, unsigned short* port) {
    return p && addressText(static_cast<Server*>(p)->listenAddress(), out, capacity, port);
}
CIO_API int L1Interop_GetLocalAddress(void* p, std::uint64_t id, char* out, int* capacity, unsigned short* port) {
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); return addressText(c->LocalAddress(), out, capacity, port);
}
CIO_API int CSocket_GetIPAddressString(void* p, std::uint64_t id, char* out, int* capacity, unsigned short* port) {
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); return addressText(c->RemoteAddress(), out, capacity, port);
}
CIO_API void L1Interop_ConfigureCapacity(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= 100000) s.maxConnections = value; else s.validSettings = false;}); }
CIO_API void L1Interop_ConfigureWorkers(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= 8) s.workerCount = value; else s.validSettings = false;}); }
CIO_API void L1Interop_ConfigureBacklog(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= 65535) s.backlog = value; else s.validSettings = false;}); }
CIO_API void L1Interop_ConfigureReadSize(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= static_cast<int>(LargeBufferBytes)) s.recvBytes = value; else s.validSettings = false;}); }
CIO_API void L1Interop_ConfigureNoDelay(void* p, int value) { configure(p, [=](Server& s){s.noDelay = value != 0;}); }

CIO_API void L1Interop_SetOnIOCallback(void* p, TaskFn fn) { if (p) static_cast<L1Callbacks*>(p)->task.store(fn); }
CIO_API int L1Interop_PostIOCallback(void* p, std::uint64_t token) { return p && static_cast<Server*>(p)->scheduleTask(token, 0); }
CIO_API int L1Interop_ScheduleTimer(void* p, std::uint64_t token, unsigned delay) { return p && static_cast<Server*>(p)->scheduleTask(token, delay); }
CIO_API int L1Interop_CancelTimer(void* p, std::uint64_t token) { return p && static_cast<Server*>(p)->cancelTask(token); }
CIO_API int L1Interop_SetCodecApi(void* p,const std::uintptr_t* functions,int count) {
    if(!p)return 0;auto* server=static_cast<Server*>(p);
    try{auto api=std::make_shared<L1CodecApi>();if(!api->assign(functions,count))return 0;
        std::lock_guard<std::mutex> lock(server->codecGate);if(server->codec)return 1;server->codec=std::move(api);return 1;
    }catch(...){return 0;}
}
CIO_API void L1Interop_SetOnPacket(void* p,PacketFn fn){if(p)static_cast<L1Callbacks*>(p)->packet.store(fn);}
CIO_API void L1Interop_SetOnTimerExpired(void* p,DeadlineFn fn){if(p)static_cast<L1Callbacks*>(p)->deadline.store(fn);}
CIO_API int CSocket_SetKey(void* p,std::uint64_t id,unsigned seed) {
    if(!p)return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);return c->initializeUserSocket(seed);
}
CIO_API int L1Interop_ReleasePacket(void* p,std::uint64_t id,std::uint64_t sequence) {
    if(!p)return 0;auto* c=static_cast<Server*>(p)->find(id,true);if(!c)return 0;ObjectRef hold(c,false);return c->releasePacket(sequence);
}
CIO_API int CSocket_Encrypt(void* p,std::uint64_t id,void* bytes,int size) {
    if(!p||size<0||(!bytes&&size))return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    std::lock_guard<std::mutex> lock(c->gate());return !c->state().closing&&c->UserProtocol()&&c->UserProtocol()->codec->encrypt(bytes,size);
}
CIO_API int L1Interop_SetSessionState(void* p,std::uint64_t id,int phase) {
    if(!p||phase<0||phase>4)return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    std::lock_guard<std::mutex> lock(c->gate());if(c->state().closing||!c->UserProtocol())return 0;if(!c->SetSessionStateLocked(phase))return 0;if(phase!=0)c->loginDue()=0;return 1;
}
CIO_API int L1Interop_ConfigureDeadlines(void* p,std::uint64_t id,unsigned loginMs) {
    if(!p||loginMs>0x7fffffffu)return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    {std::lock_guard<std::mutex> lock(c->gate());if(c->state().closing||!c->UserProtocol())return 0;c->loginDue()=loginMs&&c->sessionState()==0?monotonicMs()+loginMs:0;}
    return c->requestDeadline(loginMs?std::min(loginMs,5000u):5000u);
}
CIO_API int L1Interop_RequestDeadline(void* p,std::uint64_t id,unsigned delay) {
    if(!p||delay>0x7fffffffu)return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);return c->requestDeadline(delay);
}
CIO_API int L1Interop_GetSocketState(void* p,std::uint64_t id,std::uint64_t* out,int count) {
    if(!p||!out||count<6)return 0;auto* c=static_cast<Server*>(p)->find(id,true);if(!c)return 0;ObjectRef hold(c,false);
    std::lock_guard<std::mutex> lock(c->gate());if(!c->UserProtocol())return 0;c->UserProtocol()->snapshot(out,monotonicMs());
    if(count>=10){out[6]=c->packetBorrowed();out[7]=c->packetSequence();out[8]=c->UserProtocol()->codec->pending();out[9]=c->sessionState();}if(count>=11)out[10]=c->SocketNumber();if(count>=14){out[11]=c->AccountId();out[12]=c->CharacterEntered();out[13]=c->HasMacIdentity();}return 1;
}
CIO_API int L1Interop_GetRegistryDiagnostics(void* p,std::uint64_t* out,int count){if(!p||!out||count<6)return 0;static_cast<Server*>(p)->registry.diagnostics(out);return 1;}
CIO_API int L1Interop_GetThreadOwner(void* p) {
    return p && currentWorker == p ? static_cast<int>(currentWorkerIndex) : -1;
}
CIO_API void* L1Interop_RetainReceiveBuffer(void* p, std::uint64_t id) {
    if (!p) return nullptr; auto* c = static_cast<Server*>(p)->find(id); if (!c) return nullptr;
    ObjectRef hold(c, false); std::lock_guard<std::mutex> lock(c->gate());
    if (!c->state().readDelivering || !c->reads().block() || c->UserProtocol()) return nullptr;
    return new(std::nothrow) BufferLease(c->server(), c->reads().block());
}
CIO_API const void* L1Interop_ReceiveBufferData(void* p) { auto* lease = static_cast<BufferLease*>(p); return lease ? lease->block->data.data() + lease->offset : nullptr; }
CIO_API unsigned L1Interop_ReceiveBufferSize(void* p) { return p ? static_cast<unsigned>(static_cast<BufferLease*>(p)->length) : 0; }
CIO_API void L1Interop_ReleaseReceiveBuffer(void* p) { delete static_cast<BufferLease*>(p); }

CIO_API void L1Interop_SetOnClosing(void* p, CloseFn fn) { if (p) static_cast<L1Callbacks*>(p)->closing.store(fn); }
CIO_API int L1Interop_GetDiagnostics(void* p, std::uint64_t* values, unsigned count) {
    if (!p || !values || count < 8) return 0;
    auto* s = static_cast<Server*>(p);
    values[0] = s->buffers.allocated(); values[1] = s->buffers.exhaustions();
    values[2] = s->buffers.initializationFailures(); values[3] = s->buffers.corruptions();
    values[4] = s->timerInline.load(); values[5] = s->timerPosted.load();
    values[6] = s->closeStarts.load(); values[7] = s->closeTerminals.load();
    // Backwards compatible extension: callers requesting8 values are unchanged.
    if(count>=18){
        values[8]=0x4354504c00010000ULL;s->buffers.poolSnapshot(values+9,count>=24?values+19:nullptr);
        if(count>=24){values[18]=0x4354464100010000ULL;MemoryObject::referenceDiagnostics(values+21);}
    }
    if(count>=24+ReferenceReportWords)s->referenceDiagnostics.snapshot(values+24,MemoryObject::timerErrors());
    if(count>=24+ReferenceReportWords+ObjectAllocator::ReportWords)ObjectAllocator::Instance().Snapshot(values+24+ReferenceReportWords);
    return 1;
}


CIO_API int L1Interop_ConfigureSocketLimits(void* p,unsigned ip,unsigned mac){
    if(!p||ip>100000||mac>100000)return 0;auto* server=static_cast<Server*>(p);
    std::lock_guard<std::mutex> lock(server->lifecycle);
    return !server->running.load()&&server->socketLimiter.Configure(ip,mac);
}
CIO_API int L1Interop_GetSocketLimitDiagnostics(void* p,std::uint64_t* out,int count){
    if(!p||!out||count<4)return 0;static_cast<Server*>(p)->socketLimiter.Diagnostics(out);return 1;
}
CIO_API int L1Interop_GetListenerDiagnostics(void* p,std::uint64_t* out,int count){
    if(!p||!out||count<8)return 0;auto* s=static_cast<Server*>(p);
    out[0]=s->accepting().load();out[1]=s->waitCallbacks();out[2]=s->acceptCallbacks();out[3]=s->lastWaitError();
    out[4]=s->waitFailures();out[5]=s->pendingAccepts();out[6]=s->socketNumbers.size();out[7]=s->workerCount;if(count>=9)out[8]=s->SocketMapCount();return 1;
}
CIO_API int L1Interop_GetThreadDiagnostics(void* p,std::uint64_t* out,int count){
    if(!p||!out||count<54)return 0;auto* s=static_cast<Server*>(p);
    for(unsigned n=0;n<18;++n){out[n*3]=s->callbackCounts()[n];out[n*3+1]=s->callbackMillis()[n];out[n*3+2]=s->callbackStarted()[n];}return 1;
}
CIO_API std::uint64_t L1Interop_FindSocketByNumber(void* p,unsigned number){
    if(!p||!number)return 0;auto* s=static_cast<Server*>(p);auto* user=s->socketNumbers.FindObject(number);
    if(!user)return 0;ObjectRef hold(user,false);std::uint64_t id;
    {std::lock_guard<std::mutex> lock(user->gate());if(user->state().closing)return 0;id=user->id();}
    auto* active=s->find(id);if(!active)return 0;
    ObjectRef activeHold(active,false);return active==user?id:0;
}
CIO_API int L1Interop_BindMacIdentity(void* p,std::uint64_t id,std::uint64_t mac){
    if(!p||!mac)return 0;auto* s=static_cast<Server*>(p);auto* c=s->find(id);if(!c)return 0;ObjectRef hold(c,false);
    auto* socket=dynamic_cast<CSocket*>(c);return socket&&socket->SetMacIdentity(mac);
}
CIO_API int Server_ReportSocket(void* p,std::uint64_t* out,int capacity,int stride){
    if(!p||capacity<0||stride!=12||(!out&&capacity))return -1;auto* s=static_cast<Server*>(p);
    std::vector<CIOSocket*> snapshot;if(!s->registry.snapshot(snapshot))return -1;
    int written=0;for(auto* c:snapshot){
        if(out&&written<capacity){auto* row=out+static_cast<std::size_t>(written++)*stride;std::lock_guard<std::mutex> lock(c->gate());
            row[0]=c->id();row[1]=c->SocketNumber();row[2]=c->socket;row[3]=c->state().closing;row[4]=c->ioTerminal();
            row[5]=c->packetBorrowed();row[6]=c->state().readPosted;row[7]=c->state().writePosted;row[8]=c->writes().size();
            const auto& address=c->RemoteAddress();row[9]=address.ss_family;row[10]=row[11]=0;
            if(address.ss_family==AF_INET){const auto* ipv4=reinterpret_cast<const sockaddr_in*>(&address);row[10]=ntohl(ipv4->sin_addr.s_addr);row[11]=ntohs(ipv4->sin_port);}
        }c->release(ObjectReferenceType::ORT_TEMP_USE);
    }
    return out?written:static_cast<int>(snapshot.size());
}

CIO_API int L1Interop_BindAccount(void* p,std::uint64_t id,unsigned account){
    if(!p)return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    auto* user=dynamic_cast<CUserSocket*>(c);if(!user)return 0;
    std::lock_guard<std::mutex> lock(c->gate());return !c->state().closing&&user->SetAccountIdentityLocked(account);
}
CIO_API int L1Interop_SetEnterChar(void* p,std::uint64_t id,int entered){
    if(!p||(entered!=0&&entered!=1))return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    auto* user=dynamic_cast<CUserSocket*>(c);if(!user)return 0;
    std::lock_guard<std::mutex> lock(c->gate());return !c->state().closing&&user->SetCharacterEnteredLocked(entered!=0);
}
CIO_API std::uint64_t L1Interop_FindEnteringAccount(void* p,unsigned account){
    if(!p||!account)return 0;auto* server=static_cast<Server*>(p);auto* user=server->userSocketMapEnterChar.FindObject(account);
    if(!user)return 0;ObjectRef hold(user,false);std::uint64_t id;
    {std::lock_guard<std::mutex> lock(user->gate());if(user->state().closing||user->AccountId()!=account||!user->CharacterEntered())return 0;id=user->id();}
    auto* active=server->find(id);if(!active)return 0;ObjectRef activeHold(active,false);return active==user?id:0;
}
CIO_API int L1Interop_GetAccountRegistryDiagnostics(void* p,std::uint64_t* out,int count){
    if(!p||!out||count<4)return 0;auto* s=static_cast<Server*>(p);
    out[0]=s->socketNumbers.size();out[1]=s->userSocketMapPlaying.size();out[2]=s->userSocketMapEnterChar.size();out[3]=s->duplicateEnterDenied.load();return 1;
}
CIO_API int L1Interop_ConfigurePerf(void* p,unsigned interval){
    if(!p||interval<100||interval>60000)return 0;auto* s=static_cast<Server*>(p);
    std::lock_guard<std::mutex> lock(s->lifecycle);if(s->running.load())return 0;s->perfIntervalMs=interval;return 1;
}
CIO_API int L1Interop_ReadPerf(void* p,std::uint64_t* out,int count){
    if(!p||!out||count<16)return 0;auto* s=static_cast<Server*>(p);
    auto monitor=std::atomic_load(&s->perfmon);if(!monitor)return 0;monitor->Read(out);return 1;
}

CIO_API void* L1Interop_CreateConstBuffer(void* p,const void* bytes,int count){
    if(!p||!bytes||count<3||count>static_cast<int>(LargeBufferBytes))return nullptr;
    const auto* input=static_cast<const unsigned char*>(bytes);
    if((static_cast<unsigned>(input[0])|(static_cast<unsigned>(input[1])<<8))!=static_cast<unsigned>(count))return nullptr;
    auto* server=static_cast<Server*>(p);ObjectRef hold(server);
    if(!server->running.load()||server->stopping.load())return nullptr;
    auto* block=server->buffers.acquire(LargeBufferBytes);if(!block)return nullptr;
    std::memcpy(block->data.data(),bytes,count);block->used=count;
    auto* source=new(std::nothrow) L1ConstBuffer(server,block);
    if(!source)server->buffers.recycle(block);return source;
}
CIO_API void L1Interop_ReleaseConstBuffer(void* p){delete static_cast<L1ConstBuffer*>(p);}
CIO_API int L1Interop_SendConstBuffer(void* p,std::uint64_t id,void* source,int plainFirst){
    if(!p||!source)return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;
    ObjectRef hold(c,false);return c->sendConstBuffer(static_cast<L1ConstBuffer*>(source),plainFirst!=0);
}
CIO_API int CSocket_SendV(void* p,std::uint64_t id,const void* bytes,int count,int plainFirst){
    if(!p||count<0||(!bytes&&count)||(plainFirst!=0&&plainFirst!=1))return 0;
    auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    Span span{static_cast<const unsigned char*>(bytes),static_cast<std::size_t>(count)};
    auto* socket=dynamic_cast<CSocket*>(c);return socket&&socket->SendV(&span,1,plainFirst!=0);
}
CIO_API int CSocket___async_AsyncSendConstBuffer(void* p,std::uint64_t id,const WSABUF* buffers,int count,int plainFirst){
    if(!p||!buffers||count<1||count>8||(plainFirst!=0&&plainFirst!=1))return 0;
    auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    std::array<Span,8> spans{};for(int i=0;i<count;++i)spans[i]={reinterpret_cast<const unsigned char*>(buffers[i].buf),buffers[i].len};
    auto* socket=dynamic_cast<CSocket*>(c);return socket&&socket->SendConstBuffer(spans.data(),count,plainFirst!=0);
}
CIO_API int CIOSocketEx_GracefulClose(void* p,std::uint64_t id,unsigned timeout){
    if(!p||timeout<1||timeout>30000)return 0;auto* c=static_cast<Server*>(p)->find(id,true);if(!c)return 0;ObjectRef hold(c,false);return c->GracefulClose(timeout);
}
CIO_API int L1Interop_GetSendDiagnostics(void* p,std::uint64_t* out,int count){
    if(!p||!out||count<9)return 0;auto* s=static_cast<Server*>(p);
    out[0]=s->sendsAccepted;out[1]=s->sendsExecuted;out[2]=s->framesEncrypted;out[3]=s->framesPlain;
    out[4]=s->gracefulCloses;out[5]=s->abortiveCloses;out[6]=s->sendsRejected;out[7]=s->pendingSends;out[8]=s->sendOwnerErrors;return 1;
}
