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
#include "timer_wheel.hpp"
#include "user_socket.hpp"
#include "user_registry.hpp"
#include "io_server.hpp"
#include "object_map.hpp"
#include "socket_limiter.hpp"
#include "execution_serializer.hpp"
#include "perfmon.hpp"
#include <condition_variable>
#include <climits>
#include <cstdio>
#include <memory>
#include <shared_mutex>
#include <thread>
#include <unordered_map>
#include <vector>

namespace ctransport {
using AcceptFn = int (*)(void*, std::uint64_t, std::uint64_t);
using DataFn = int (*)(void*, std::uint64_t, const void*, int);
using CloseFn = int (*)(void*, std::uint64_t, int, int);
using PacketFn = int (*)(void*, std::uint64_t, std::uint64_t, const void*, int);
using DeadlineFn = int (*)(void*, std::uint64_t);
using NtStatusToErrorFn = ULONG (WINAPI *)(LONG);
constexpr ULONG CompletionBatchSize = 128;
struct Listener {
    std::atomic<AcceptFn> accept{nullptr};
    std::atomic<DataFn> receive{nullptr}, send{nullptr};
    std::atomic<CloseFn> close{nullptr}, closing{nullptr};
    std::atomic<PacketFn> packet{nullptr};
    std::atomic<DeadlineFn> deadline{nullptr};
    std::atomic<void (*)(void*, std::uint64_t, int)> task{nullptr};
};
class Server;
class CIOSocket;
class CSocket;
class CUserSocket;
class NativeTask;
using TaskFn = void (*)(void*, std::uint64_t, int);
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
    void reset() noexcept { overlapped = OVERLAPPED{}; }
};
static_assert(offsetof(Operation, overlapped) == 0, "OVERLAPPED address contract");
class Server : public CIOServer {
public:
    Listener* const callbacks;
    CIOBufferPool buffers;
    NtStatusToErrorFn ntStatusToError = nullptr;
    std::vector<std::unique_ptr<IOThreadCallback>> workers;
    std::unique_ptr<ListenThread> listenThread;
    std::unique_ptr<TimerWheel> listenTimer;
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
    std::unique_ptr<TimerBlockPool> timerBlocks;
    std::vector<std::unique_ptr<TimerWheel>> timers;
    std::atomic<std::uint64_t> timerInline{0}, timerPosted{0}, closeStarts{0}, closeTerminals{0};
    std::mutex taskGate;
    std::unordered_map<std::uint64_t, NativeTask*> tasks;
    std::atomic<unsigned> taskCount{0};
    std::atomic<std::uint64_t> nextId{1};
    std::atomic<int> connections{0};
    std::unique_ptr<IServerCallback> socketFactory;
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

    explicit Server(Listener* listener) : callbacks(listener), buffers(65536) {
        WSADATA data{}; wsaReady = WSAStartup(MAKEWORD(2, 2), &data) == 0;
    }
    ~Server() override;
    bool start(const char* address, unsigned short portNumber) noexcept;
    bool stop() noexcept;
    bool stopAccepting() noexcept;
    bool stopAcceptingImpl() noexcept;
    bool wait(unsigned timeout) noexcept {
        if (currentWorker == this || currentCallbackServer == this) return !running.load();
        std::unique_lock<std::mutex> lock(waitMutex);
        if (timeout == INFINITE) { changed.wait(lock, [&]{return !running.load();}); return true; }
        return changed.wait_for(lock, std::chrono::milliseconds(timeout), [&]{return !running.load();});
    }
    void NotifyPendingChanged()noexcept override{notifyChange();}
    void OnAcceptedSocket(SOCKET socket)noexcept override;
    void OnWaitFailure(unsigned error) noexcept override;
    TimerWheel* timerFor(unsigned owner) noexcept {return owner==17?listenTimer.get():owner<timers.size()?timers[owner].get():nullptr;}
    CIOSocket* CreateSocket(SOCKET socket) noexcept;

    void worker(unsigned owner = 0) noexcept;
    bool scheduleTask(std::uint64_t token, unsigned delay, CIOSocket* deadlineOwner=nullptr) noexcept;
    bool cancelTask(std::uint64_t token) noexcept;
    bool cancelTasks() noexcept;
    void finishTask(NativeTask* task) noexcept;
    CIOSocket* find(std::uint64_t id, bool includeRetiring=false) noexcept;
    bool Add(CIOSocket* c) noexcept;
    void Remove(SOCKET socket,CIOSocket* expected) noexcept;
    void ReleaseHandle(CIOSocket* c) noexcept;
    bool Shutdown() noexcept;
    bool post(Operation& op) noexcept {
        op.owner->retain();
        if (PostQueuedCompletionStatus(port, 0, reinterpret_cast<ULONG_PTR>(op.owner), &op.overlapped)) return true;
        op.owner->release(); return false;
    }
};

class CIOSocket : public CIOObject {
public:
    Server* const server;
    std::uint64_t id;
    SOCKET socket;
    SOCKET retiredSocket = INVALID_SOCKET;
    std::mutex gate;
    IoState state;
    SendChain writes;
    ReceiveWindow reads;
    Operation readOp, writeOp, closeOp, resumeOp;
    CExecutionSerializerFast execution_;
    virtual const sockaddr_storage& LocalAddress() const noexcept=0;
    virtual const sockaddr_storage& RemoteAddress() const noexcept=0;
    int closeOperation = 5, closeError = 0;
    virtual std::uint32_t SocketNumber() const noexcept{return 0;}
    virtual std::uint32_t AccountId()const noexcept{return 0;}
    virtual bool HasMacIdentity()const noexcept{return false;}
    virtual bool CharacterEntered()const noexcept{return false;}
    virtual bool SetSessionStateLocked(int phase)noexcept{sessionState=phase;return true;}
    virtual void OnSocketRetired() noexcept{}
    virtual CUserSocketParser* UserProtocol() noexcept {return nullptr;}
    virtual bool ConfigureUserProtocol(std::shared_ptr<const L1CodecApi>,unsigned) noexcept{return false;}
    bool packetBorrowed=false,packetDelivering=false,resumePosted=false,userPumpActive=false;
    bool ioTerminal=false,registrationReleased=false;
    std::uint64_t packetSequence=0,deadlineToken=0,deadlineDue=0,cancelDeadlineToken=0;
    std::uint64_t loginDue=0,writeProgressAt=0;
    int sessionState=0;

    CIOSocket(Server* s, SOCKET value, std::uint64_t valueId,std::size_t bufferBytes) : server(s), id(valueId), socket(value),
        writes(bufferBytes),
        reads(s->buffers,bufferBytes),
        readOp(OpKind::Read, this), writeOp(OpKind::Write, this), closeOp(OpKind::Close, this), resumeOp(OpKind::Resume,this) {
        server->retain();
    }
    ~CIOSocket() override {
        writes.clear(server->buffers);
        reads.clear();
        if (socket != INVALID_SOCKET) closesocket(socket);
        server->release();
    }
    void failLocked(int operation, int error) noexcept {
        if (state.closing) return;
        state.closing = true; state.paused = true;
        cancelDeadlineToken=deadlineToken;deadlineToken=deadlineDue=0;
        if (UserProtocol()) UserProtocol()->stop();
        closeOperation = operation; closeError = error;
        if (socket != INVALID_SOCKET) { retiredSocket = socket; socket = INVALID_SOCKET; state.closeRetiring = true; }
    }
    void retireSocket() noexcept {
        SOCKET previous;
        {
            std::lock_guard<std::mutex> lock(gate);
            previous = retiredSocket; retiredSocket = INVALID_SOCKET;
        }
        std::uint64_t cancel;
        {std::lock_guard<std::mutex> lock(gate);cancel=cancelDeadlineToken;cancelDeadlineToken=0;}
        if(cancel)server->cancelTask(cancel);
        if (previous == INVALID_SOCKET) return;
        server->registry.retire(id,this);
        server->Remove(previous,this);
        OnSocketRetired();
        // The original hook changes parser state/unregisters the socket before
        // linger/close. Java's final resource cleanup stays after I/O drains.
        ++server->closeStarts;
        CallbackScope callback(server);
        if (auto fn = server->callbacks->closing.load()) fn(server, id, closeOperation, closeError);
        linger reset{1, 0};
        setsockopt(previous, SOL_SOCKET, SO_LINGER, reinterpret_cast<const char*>(&reset), sizeof(reset));
        closesocket(previous);
        { std::lock_guard<std::mutex> lock(gate); state.closeRetiring = false; }
    }

    // The posting thread owns an API/I/O reference. Failed submissions balance
    // their speculative reference because they will not produce a completion.
    void readLocked() noexcept {
        if (!state.canRead() || (UserProtocol() && (packetBorrowed||packetDelivering||resumePosted||userPumpActive))) return;
        if (!reads.prepare()) { failLocked(2, 10055); return; }
        readOp.reset(); state.readPosted = true; retain();
        WSABUF buffer{static_cast<ULONG>(std::min<std::size_t>(server->recvBytes,reads.block()->capacity)), reinterpret_cast<char*>(reads.block()->data.data() + reads.block()->used)};
        DWORD bytes = 0, flags = 0;
        const int result = WSARecv(socket, &buffer, 1, &bytes, &flags, &readOp.overlapped, nullptr);
        if (result == SOCKET_ERROR) {
            const int error = WSAGetLastError();
            if (error != WSA_IO_PENDING) { state.readPosted = false; release(); failLocked(2, error); }
        }
    }
    void writeLocked() noexcept {
        if (!state.canWrite() || !writes.head()) return;
        auto* p = writes.head();
        writeOp.reset(); state.writePosted = true; retain();
        WSABUF buffer{static_cast<ULONG>(p->used - p->offset), reinterpret_cast<char*>(p->data.data() + p->offset)};
        DWORD bytes = 0;
        const int result = WSASend(socket, &buffer, 1, &bytes, 0, &writeOp.overlapped, nullptr);
        if (result == SOCKET_ERROR) {
            const int error = WSAGetLastError();
            if (error != WSA_IO_PENDING) { state.writePosted = false; release(); failLocked(3, error); }
        }
    }
    void finishIfReady() noexcept {
        retireSocket();
        bool ready;
        {
            std::lock_guard<std::mutex> lock(gate);
            ready = state.canClose() && !resumePosted && !userPumpActive && !packetDelivering;
            if (ready) { state.closeQueued = true; closeOp.reset(); }
        }
        if (ready && !server->post(closeOp)) closeCallback();
    }
    void closeCallback() noexcept {
        // No remaining kernel I/O/delivery can refer to a reused byte buffer.
        {std::lock_guard<std::mutex> lock(gate);ioTerminal=true;}
        ++server->closeTerminals;
        CallbackScope callback(server);
        if (auto fn = server->callbacks->close.load()) fn(server, id, closeOperation, closeError);
        finalizeRegistration();
    }
    void finalizeRegistration() noexcept {
        bool done;
        {std::lock_guard<std::mutex> lock(gate);done=ioTerminal&&!packetBorrowed&&!packetDelivering&&!registrationReleased;if(done)registrationReleased=true;}
        if(done){server->RemoveSocketNumber(this);server->ReleaseHandle(this);}
    }
    void pumpPackets() noexcept;
    bool releasePacket(std::uint64_t sequence) noexcept;
    bool queueResume() noexcept;
    bool requestDeadline(unsigned delay) noexcept;
    void deadlineTick(std::uint64_t token,int status) noexcept;
    bool initializeUserSocket(unsigned seed) noexcept {
        std::shared_ptr<const L1CodecApi> api;
        {std::lock_guard<std::mutex> lock(server->codecGate);api=server->codec;}
        if(!api||!server->callbacks->packet.load())return false;
        std::lock_guard<std::mutex> lock(gate);
        if(state.closing||state.readPosted||state.readDelivering||!state.paused)return false;
        if(UserProtocol())return false;
        return ConfigureUserProtocol(std::move(api),seed);
    }
    void disconnect(int operation = 5, int error = 0) noexcept {
        { std::lock_guard<std::mutex> lock(gate); failLocked(operation, error); }
        finishIfReady();
    }
    bool pause(bool paused) noexcept {
        bool open,profile;
        {std::lock_guard<std::mutex> lock(gate);open=!state.closing;profile=UserProtocol()!=nullptr;
         if(open){state.paused=paused;if(UserProtocol())UserProtocol()->pause(paused||packetBorrowed,monotonicMs());else readLocked();open=!state.closing;}}
        if(open&&profile&&!paused)open=queueResume();
        finishIfReady();return open;
    }
    bool send(const Span* spans, unsigned count) noexcept {
        bool ok;
        {
            std::lock_guard<std::mutex> lock(gate);
            const bool wasEmpty=!writes.head();
            ok = !state.closing && writes.append(server->buffers, spans, count);
            if(ok&&wasEmpty&&writes.head())writeProgressAt=monotonicMs();
            if (ok) { writeLocked(); ok = !state.closing; }
            else if (!state.closing) failLocked(3, 10055);
        }
        finishIfReady(); return ok;
    }
    void accepted(int result) noexcept {
        {
            std::lock_guard<std::mutex> lock(gate);
            state.acceptDelivering = false;
            if (result != 0) failLocked(1, ERROR_ACCESS_DENIED);
            readLocked(); writeLocked();
        }
        finishIfReady();
    }
    static void WriteCallback(CIOObject* owner,void* argument) noexcept {
        auto* socket=static_cast<CIOSocket*>(owner);const auto data=reinterpret_cast<std::uintptr_t>(argument);
        const auto error=static_cast<unsigned>(data);socket->ProcessIO(&socket->writeOp,error==0,static_cast<unsigned>(data>>32),error);
    }
    static void CloseCallback(CIOObject* owner,void*) noexcept{static_cast<CIOSocket*>(owner)->closeCallback();}
    void completed(void* operation,bool ok,unsigned bytes,unsigned error) noexcept override {
        auto& op=*static_cast<Operation*>(operation);
        if(op.kind==OpKind::Write){
            const auto status=ok?0u:(error?error:31u);
            const auto data=(static_cast<std::uintptr_t>(bytes)<<32)|status;
            if(execution_.AddExecutable(this,WriteCallback,reinterpret_cast<void*>(data)))return;
            ProcessIO(operation,false,0,10055);return;
        }
        if(op.kind==OpKind::Close){
            if(!execution_.AddExecutable(this,CloseCallback,nullptr))closeCallback();return;
        }
        ProcessIO(operation,ok,bytes,error);
    }
    void ProcessIO(void* operation, bool ok, unsigned bytes, unsigned error) noexcept {
        auto& op = *static_cast<Operation*>(operation);
        if (op.kind == OpKind::Close) { closeCallback(); return; }
        if (op.kind == OpKind::Resume) {
            {std::lock_guard<std::mutex> lock(gate);resumePosted=false;userPumpActive=true;}
            pumpPackets();
            {std::lock_guard<std::mutex> lock(gate);userPumpActive=false;readLocked();writeLocked();}
            finishIfReady();return;
        }
        CIOBuffer* retired = nullptr;
        const unsigned char* data = nullptr;
        bool deliver = false;
        {
            std::lock_guard<std::mutex> lock(gate);
            const bool reading = op.kind == OpKind::Read;
            if (reading) state.readPosted = false; else state.writePosted = false;
            if (!state.closing) {
                if (!ok || bytes == 0) failLocked(reading ? 2 : 3, static_cast<int>(error));
                else if (reading && bytes <= static_cast<unsigned>(server->recvBytes)) {
                    if (reads.append(bytes)) {
                        state.readDelivering = true; data = reads.block()->data.data() + reads.block()->offset; deliver = true;
                    } else failLocked(2, ERROR_INVALID_DATA);
                } else if (!reading && writes.head()) {
                    data = writes.head()->data.data() + writes.head()->offset;
                    if (writes.complete(bytes, retired)) { writeProgressAt=monotonicMs();state.writeDelivering = true; deliver = true; }
                    else failLocked(3, ERROR_INVALID_DATA);
                } else failLocked(reading ? 2 : 3, ERROR_INVALID_DATA);
            }
        }
        int result = 0;
        if (deliver) {
            const auto fn = (op.kind == OpKind::Read ? server->callbacks->receive.load() : server->callbacks->send.load());
            if (op.kind == OpKind::Read && UserProtocol()) {
                if (!UserProtocol()->feed(data,bytes,monotonicMs(),[&](const unsigned char* frame,unsigned size){return UserProtocol()->codec->feed(frame,size);}))result=2;
                else pumpPackets();
            } else if(fn)result=fn(server,id,data,static_cast<int>(bytes));
        }
        {
            std::lock_guard<std::mutex> lock(gate);
            if (op.kind == OpKind::Read) { if (deliver) reads.consume(bytes); state.readDelivering = false; } else state.writeDelivering = false;
            if (result != 0) failLocked(op.kind == OpKind::Read ? 2 : 3, ERROR_INVALID_DATA);
            readLocked(); writeLocked();
        }
        server->buffers.recycle(retired);
        finishIfReady();
    }
};

// Actual transport hierarchy: CIOSocket owns overlapped I/O and buffer life;
// CSocket adds peer addresses; CUserSocket owns L1 parsing/cipher state.
template<std::size_t Bytes> class CIOSocketEx : public CIOSocket {
public:
    static_assert(Bytes==8192||Bytes==16384,"original socket buffer families");
    CIOSocketEx(Server* server,SOCKET socket,std::uint64_t id):CIOSocket(server,socket,id,Bytes){}
};
class CSocket : public CIOSocketEx<16384> {
    sockaddr_storage local_{},remote_{};
    std::atomic<bool> ipReserved_{false};
    std::atomic<std::uint64_t> macIdentity_{0};
public:
    CSocket(Server* server,SOCKET socket,std::uint64_t id):CIOSocketEx<16384>(server,socket,id){
        int length=sizeof(local_);getsockname(socket,reinterpret_cast<sockaddr*>(&local_),&length);
        length=sizeof(remote_);getpeername(socket,reinterpret_cast<sockaddr*>(&remote_),&length);
    }
    ~CSocket()override{OnSocketRetired();}
    bool ReserveIP()noexcept{
        if(remote_.ss_family!=AF_INET)return false;
        const auto key=reinterpret_cast<const sockaddr_in*>(&remote_)->sin_addr.s_addr;
        if(!server->socketLimiter.IncUserSocketPerIP(key))return false;ipReserved_=true;return true;
    }
    bool SetMacIdentity(std::uint64_t identity)noexcept{
        std::lock_guard<std::mutex> lock(gate);if(state.closing||!identity||macIdentity_.load())return false;
        if(!server->socketLimiter.IncUserSocketPerMAC(identity))return false;macIdentity_=identity;return true;
    }
    bool HasMacIdentity()const noexcept override{return macIdentity_.load()!=0;}
    void OnSocketRetired()noexcept override{
        if(ipReserved_.exchange(false))server->socketLimiter.DecUserSocketPerIP(reinterpret_cast<const sockaddr_in*>(&remote_)->sin_addr.s_addr);
        if(auto mac=macIdentity_.exchange(0))server->socketLimiter.DecUserSocketPerMAC(mac);
    }
    const sockaddr_storage& LocalAddress()const noexcept override{return local_;}
    const sockaddr_storage& RemoteAddress()const noexcept override{return remote_;}
};
class CUserSocket final : public CSocket {
    std::unique_ptr<CUserSocketParser> protocol_;
    const std::uint32_t number_;
    std::uint32_t accountId_=0;
    bool playingRegistered_=false,characterEntered_=false;
    void ClearAccountLocked()noexcept{
        if(characterEntered_){server->userSocketMapEnterChar.DeleteObject(accountId_,this);characterEntered_=false;}
        if(playingRegistered_){server->userSocketMapPlaying.DeleteObject(number_,this);playingRegistered_=false;}
        accountId_=0;
    }
public:
    static CIOSocket* Allocate(Server* server,SOCKET socket)noexcept{return new(std::nothrow) CUserSocket(server,socket,server->nextId.fetch_add(1));}
    CUserSocket(Server* server,SOCKET socket,std::uint64_t id):CSocket(server,socket,id),number_(server->AllocateSocketNumber()){}
    ~CUserSocket()override{ClearAccountLocked();}
    std::uint32_t SocketNumber()const noexcept override{return number_;}
    std::uint32_t AccountId()const noexcept override{return accountId_;}
    bool CharacterEntered()const noexcept override{return characterEntered_;}
    bool SetAccountIdentityLocked(std::uint32_t account)noexcept{
        if(!account){ClearAccountLocked();return true;}
        if(server->socketLimiter.RequiresMacIdentity()&&!HasMacIdentity())return false;
        if(accountId_)return accountId_==account;
        if(!server->userSocketMapPlaying.Insert(number_,this))return false;
        accountId_=account;playingRegistered_=true;return true;
    }
    bool SetCharacterEnteredLocked(bool entered)noexcept{
        if(entered==characterEntered_)return true;
        if(!entered){server->userSocketMapEnterChar.DeleteObject(accountId_,this);characterEntered_=false;return true;}
        if(!accountId_||!playingRegistered_)return false;
        if(!server->userSocketMapEnterChar.Insert(accountId_,this)){++server->duplicateEnterDenied;return false;}
        characterEntered_=true;return true;
    }
    bool SetSessionStateLocked(int phase)noexcept override{
        if(phase==0||phase==4)ClearAccountLocked();
        else if(phase==1||phase==3)SetCharacterEnteredLocked(false);
        else if(phase==2&&accountId_&&!SetCharacterEnteredLocked(true))return false;
        sessionState=phase;return true;
    }
    void OnSocketRetired()noexcept override{
        {std::lock_guard<std::mutex> lock(gate);ClearAccountLocked();}
        CSocket::OnSocketRetired();
    }
    CUserSocketParser* UserProtocol()noexcept override{return protocol_.get();}
    bool ConfigureUserProtocol(std::shared_ptr<const L1CodecApi> api,unsigned seed)noexcept override {
        if(protocol_)return false;
        try{protocol_=std::make_unique<CUserSocketParser>(std::move(api),seed);return true;}catch(...){return false;}
    }
};


void CIOSocket::pumpPackets() noexcept {
    for(;;){
        PlainPacket packet;int size;
        {
            std::lock_guard<std::mutex> lock(gate);
            if(state.closing||state.paused||!UserProtocol()||packetBorrowed)return;
            size=UserProtocol()->codec->next(packet);
            if(size==-2)return;
            if(size!=-3&&size<=0){failLocked(2,ERROR_INVALID_DATA);return;}
            if(size>0){packetBorrowed=true;packetSequence=packet.sequence;retain();UserProtocol()->pause(true,monotonicMs());}
            packetDelivering=true;
        }
        int result=2;
        if(auto fn=server->callbacks->packet.load())result=fn(server,id,packet.sequence,packet.data,size==-3?0:size);
        bool rejectLease;
        {
            std::lock_guard<std::mutex> lock(gate);packetDelivering=false;
            rejectLease=result!=0&&size>0&&packetBorrowed&&packetSequence==packet.sequence;
            if(result!=0)failLocked(2,ERROR_INVALID_DATA);
        }
        if(rejectLease)releasePacket(packet.sequence);
        if(result!=0)return;
    }
}
bool CIOSocket::queueResume() noexcept {
    bool post=false;
    {
        std::lock_guard<std::mutex> lock(gate);
        if(state.closing)return false;
        if(!UserProtocol()||state.paused||packetBorrowed||packetDelivering||resumePosted||userPumpActive||state.readPosted||state.readDelivering||state.acceptDelivering)return true;
        resumePosted=true;resumeOp.reset();post=true;
    }
    if(post&&!server->post(resumeOp)){
        {std::lock_guard<std::mutex> lock(gate);resumePosted=false;failLocked(2,static_cast<int>(GetLastError()));}
        finishIfReady();return false;
    }
    return true;
}
bool CIOSocket::releasePacket(std::uint64_t sequence) noexcept {
    {
        std::lock_guard<std::mutex> lock(gate);
        if(!packetBorrowed||packetSequence!=sequence||!UserProtocol()||!UserProtocol()->codec->release(sequence))return false;
        packetBorrowed=false;UserProtocol()->pause(state.paused,monotonicMs());
    }
    queueResume();finalizeRegistration();release();return true;
}
bool CIOSocket::requestDeadline(unsigned delay) noexcept {
    if(delay>0x7fffffffu)return false;
    const auto now=monotonicMs(),due=now+std::max(delay,1u);
    std::uint64_t previous,token;
    {
        std::lock_guard<std::mutex> lock(gate);
        if(state.closing||!UserProtocol())return false;
        if(deadlineToken&&deadlineDue<=due)return true;
        previous=deadlineToken;
        token=0x8000000000000000ull|server->nextInternalTask.fetch_add(1);
        deadlineToken=token;deadlineDue=due;
    }
    if(previous)server->cancelTask(previous);
    const bool scheduled=server->scheduleTask(token,std::max(delay,1u),this);
    bool cancel=false;
    {
        std::lock_guard<std::mutex> lock(gate);
        cancel=state.closing||deadlineToken!=token;
        if(!scheduled&&deadlineToken==token)deadlineToken=deadlineDue=0;
    }
    if(scheduled&&cancel)server->cancelTask(token);
    return scheduled&&!cancel;
}
void CIOSocket::deadlineTick(std::uint64_t token,int status) noexcept {
    bool expired=false;unsigned next=5000;
    {
        std::lock_guard<std::mutex> lock(gate);
        if(state.closing||token!=deadlineToken)return;
        deadlineToken=deadlineDue=0;const auto now=monotonicMs();
        expired=status!=0||(loginDue&&now>=loginDue)||UserProtocol()->expired(now)
                ||(writes.head()&&now-writeProgressAt>=30000);
        if(loginDue&&loginDue>now)next=static_cast<unsigned>(std::min<std::uint64_t>(next,loginDue-now));
    }
    if(expired){disconnect(2,status?status:10060);return;}
    if(auto fn=server->callbacks->deadline.load()){
        const int result=fn(server,id);
        if(result<0){disconnect(2,10060);return;}
        if(result>0)next=std::min(next,static_cast<unsigned>(result));
    }
    if(!requestDeadline(next)){
        bool closed;{std::lock_guard<std::mutex> lock(gate);closed=state.closing||deadlineToken!=0;}
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
    Operation op{OpKind::Task, this};
    std::atomic<TaskState> state{TaskState::Pending};
    static unsigned registeringOwner(Server* s) noexcept {
        if (currentWorker == s) return currentWorkerIndex;
        const auto registered = s->buffers.currentOwner();
        return (registered >= 64 ? registered - 64 : registered) % static_cast<unsigned>(s->workerCount);
    }
    NativeTask(Server* s,std::uint64_t t,CIOSocket* deadline=nullptr):server(s),token(t),owner(registeringOwner(s)),deadlineOwner(deadline){server->retain();if(deadlineOwner)deadlineOwner->retain();}
    ~NativeTask() override {if(deadlineOwner)deadlineOwner->release();server->release();}
    void dispatch(int status) noexcept {
        if(deadlineOwner)deadlineOwner->deadlineTick(token,status);
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
    void completed(void*, bool, unsigned, unsigned) noexcept override {
        auto expected = TaskState::Queued;
        if (state.compare_exchange_strong(expected, TaskState::Running)) {
            dispatch(0);
            state.store(TaskState::Done);
        }
        server->finishTask(this);
    }
};
bool Server::scheduleTask(std::uint64_t token,unsigned delay,CIOSocket* deadlineOwner) noexcept {
    if (!token || delay > 0x7fffffffu) return false;
    std::lock_guard<std::mutex> lock(taskGate);
    if (!running.load() || stopping.load() || tasks.size() >= 65536 || tasks.find(token) != tasks.end()) return false;
    NativeTask* task = nullptr;
    try {
        task = new NativeTask(this,token,deadlineOwner);
        tasks.emplace(token, task);
    } catch (...) { if (task) task->release(); return false; }
    ++taskCount;
    bool queued;
    if (delay) queued = timerFor(task->owner)->schedule(token, task, monotonicMs() + delay);
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
        if (auto* queued = timerFor(task->owner)->cancel(token)) queued->release();
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
struct BufferLease {
    Server* server; CIOBuffer* block; std::size_t offset, length;
    BufferLease(Server* s, CIOBuffer* b) : server(s), block(b), offset(b->offset), length(b->used - b->offset) {
        server->retain(); CIOBufferPool::retain(block);
    }
    ~BufferLease() { server->buffers.recycle(block); server->release(); }
};

CIOSocket* Server::find(std::uint64_t id,bool includeRetiring) noexcept {return registry.find(id,includeRetiring);}
bool Server::Add(CIOSocket* c) noexcept {
    std::lock_guard<std::mutex> publication(c->gate);
    if(c->state.closing)return false;
    if(connections.fetch_add(1)>=maxConnections){connections.fetch_sub(1);return false;}
    if(!registry.insert(c)){connections.fetch_sub(1);notifyChange();return false;}
    bool added=false;
    try{std::lock_guard<std::mutex> lock(socketMapGate);added=socketMap.emplace(c->socket,c).second;if(added)c->retain();}catch(...){}
    if(!added){registry.remove(c->id,c);connections.fetch_sub(1);notifyChange();}return added;
}
void Server::Remove(SOCKET socket,CIOSocket* expected) noexcept {
    bool removed=false;
    {std::lock_guard<std::mutex> lock(socketMapGate);auto at=socketMap.find(socket);
     if(at!=socketMap.end()&&at->second==expected){socketMap.erase(at);removed=true;}}
    if(removed)expected->release();
}
void Server::ReleaseHandle(CIOSocket* c) noexcept {
    if(registry.remove(c->id,c)){connections.fetch_sub(1);notifyChange();}
}
bool Server::Shutdown() noexcept {
    std::vector<CIOSocket*> sockets;
    try{std::lock_guard<std::mutex> lock(socketMapGate);sockets.reserve(socketMap.size());
        for(auto& at:socketMap){sockets.push_back(at.second);at.second->retain();}}
    catch(...){for(auto* socket:sockets)socket->release();return false;}
    for(auto* socket:sockets){socket->disconnect();socket->release();}return true;
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
            for (;;) {
                auto* due = server->timers[owner]->takeDue(dueSnapshot);
                if (!due) break;
                if (timerDispatch.post(owner)) { ++server->timerPosted; static_cast<NativeTask*>(due)->submit(); }
                else { ++server->timerInline; static_cast<NativeTask*>(due)->runInline(); }
                due->release();
            }
        }
        ULONG removed = 0;
        if (!GetQueuedCompletionStatusEx(server->port, entries.data(), CompletionBatchSize,
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
                    const auto begin=monotonicMs();server->callbackStarted[owner]=begin;
                    object->completed(nullptr,true,entry.dwNumberOfBytesTransferred,0);
                    ++server->callbackCounts[owner];server->callbackMillis[owner].fetch_add(monotonicMs()-begin);
                    server->callbackStarted[owner]=0;object->release();
                }
                continue;
            }
            const LONG status = static_cast<LONG>(static_cast<ULONG>(entry.Internal));
            const bool ok = status >= 0; // NT_SUCCESS, independent of the batch BOOL.
            const DWORD error = ok ? 0 : server->ntStatusToError(status);
            auto* operation = reinterpret_cast<Operation*>(entry.lpOverlapped);
            auto* owner = reinterpret_cast<CIOObject*>(entry.lpCompletionKey);
            const auto begin=monotonicMs();server->callbackStarted[owner_]=begin;
            owner->completed(operation, ok, entry.dwNumberOfBytesTransferred, error);
            ++server->callbackCounts[owner_];server->callbackMillis[owner_].fetch_add(monotonicMs()-begin);
            server->callbackStarted[owner_]=0;owner->release();
        }
        if (exitTokens) {
            // A single worker can dequeue several workers' exit tokens. Finish
            // every real completion, consume one token, and return the others.
            // Breaking at the first token would strand I/O holds and workers.
            for (ULONG i = 1; i < exitTokens; ++i)
                PostQueuedCompletionStatus(server->port, 0, 0, nullptr);
            break;
        }
    }
    currentWorker = nullptr;
}
void IOThreadCallback::Start(){thread_=std::thread([this]{IOThread_common();});}
void Server::worker(unsigned owner) noexcept {IOThreadCallback callback(this,owner);callback.IOThread_common();}
void ListenThread::Tick() noexcept {
    if(!server_->listenTimer)return;
    while(auto* due=server_->listenTimer->takeDue(monotonicMs())){
        ++server_->timerPosted;static_cast<NativeTask*>(due)->submit();due->release();
    }
}
bool ListenThread::Start() noexcept {
    try{thread_=std::thread([this]{currentWorker=server_;currentWorkerIndex=17;
        CIOBufferPool::OwnerScope owner(server_->buffers,17);Run();currentWorker=nullptr;});return true;}
    catch(...){return false;}
}
void CIOServer::OnWaitCallback() noexcept {
    bool posted=false,held=false;unsigned error=0;const auto begin=monotonicMs();callbackStarted[17]=begin;
    {
        std::lock_guard<std::mutex> lock(acceptGate);
        if(!accepting.load()||!acceptEvent||acceptQueued){callbackStarted[17]=0;return;}
        if(!WSAResetEvent(acceptEvent))error=WSAGetLastError();
        else {
            ++waitCallbacks;acceptQueued=true;++pendingAccepts;retain();held=true;
            posted=PostQueuedCompletionStatus(port,0,reinterpret_cast<ULONG_PTR>(static_cast<CIOObject*>(this)),nullptr)!=0;
            if(!posted){error=GetLastError();acceptQueued=false;--pendingAccepts;}
        }
    }
    ++callbackCounts[17];callbackMillis[17].fetch_add(monotonicMs()-begin);callbackStarted[17]=0;
    if(!posted){if(held)release();NotifyPendingChanged();OnWaitFailure(error?error:31);}
}
void Server::OnWaitFailure(unsigned error) noexcept {
    ++waitFailures;lastWaitError=error;accepting=false;
    if(listenThread)listenThread->UnregisterWait(this);
    Close();notifyChange();
}
CIOSocket* Server::CreateSocket(SOCKET socket) noexcept {
    auto* object=static_cast<CUserSocket*>(socketFactory->CreateSocket(this,socket));
    if(object&&(!object->ReserveIP()||!socketNumbers.Insert(object->SocketNumber(),object))){
        object->socket=INVALID_SOCKET;object->release();return nullptr;
    }
    return object;
}
void Server::RemoveSocketNumber(CIOSocket* socket) noexcept {
    if(auto* user=dynamic_cast<CUserSocket*>(socket))socketNumbers.DeleteObject(user->SocketNumber(),user);
}
Server::~Server(){stop();if(wsaReady)WSACleanup();}

void CIOServer::OnIOCallback() noexcept {
    SOCKET socket=INVALID_SOCKET;
    {
        std::lock_guard<std::mutex> lock(acceptGate);
        if(accepting.load()){
            sockaddr_storage peer{};int length=sizeof(peer);
            socket=accept(listening,reinterpret_cast<sockaddr*>(&peer),&length);
        }
        // accept() reenables FD_ACCEPT. Publish the free completion slot while
        // holding the same gate as ResetEvent so no rearmed event is lost.
        acceptQueued=false;
    }
    ++acceptCallbacks;
    if(socket!=INVALID_SOCKET)OnAcceptedSocket(socket);
    pendingAccepts.fetch_sub(1);NotifyPendingChanged();
}
void Server::OnAcceptedSocket(SOCKET socket)noexcept {
    if(socket!=INVALID_SOCKET){
        auto* c=CreateSocket(socket);
        if(c){
            ObjectRef temporary(c,false);
            if(Add(c)){
                const auto fn=callbacks->accept.load();const int admission=fn?fn(this,c->id,static_cast<std::uint64_t>(socket)):2;
                bool initialized=false;
                {std::lock_guard<std::mutex> lock(c->gate);
                    if(!c->state.closing&&admission==0){
                        int zero=0,enabled=noDelay?1:0;
                        initialized=setsockopt(socket,SOL_SOCKET,SO_RCVBUF,reinterpret_cast<const char*>(&zero),sizeof(zero))==0
                            &&setsockopt(socket,SOL_SOCKET,SO_SNDBUF,reinterpret_cast<const char*>(&zero),sizeof(zero))==0
                            &&setsockopt(socket,IPPROTO_TCP,TCP_NODELAY,reinterpret_cast<const char*>(&enabled),sizeof(enabled))==0
                            &&CreateIoCompletionPort(reinterpret_cast<HANDLE>(socket),port,reinterpret_cast<ULONG_PTR>(static_cast<CIOObject*>(c)),0);
                    }
                }
                c->accepted(initialized?0:2);
            }else RemoveSocketNumber(c);
        }else closesocket(socket);
    }
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
    for(auto& count:server_->callbackCounts)values[14]+=count.load();
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
bool Server::start(const char* address, unsigned short number) noexcept {
    std::lock_guard<std::mutex> lock(lifecycle);
    if (running.load() || !wsaReady || !validSettings || !address || workerCount < 1 || workerCount > 64 || maxConnections < 1) return false;
    ntStatusToError = reinterpret_cast<NtStatusToErrorFn>(
        GetProcAddress(GetModuleHandleW(L"ntdll.dll"), "RtlNtStatusToDosError"));
    if (!ntStatusToError) return false;
    SYSTEM_INFO information{};GetSystemInfo(&information);
    workerCount=std::min(workerCount,static_cast<int>(std::min<DWORD>(information.dwNumberOfProcessors,8)));
    if(workerCount<1||!buffers.initialize(static_cast<unsigned>(workerCount),std::max<std::size_t>(static_cast<std::size_t>(maxConnections)*2,workerCount*200)))return false;
    if(!buffers.initializeOwner(15,0,100)||!buffers.initializeOwner(17,300,200)||!Create(address,number,backlog))return false;
    port=CreateIoCompletionPort(INVALID_HANDLE_VALUE,nullptr,0,workerCount);
    if(!port){Close();return false;}
    if(!registry.initialize(static_cast<unsigned>(maxConnections))){Close();CloseHandle(port);port=nullptr;return false;}
    running.store(true); stopping.store(false); accepting.store(true);
    try {
        socketFactory=std::make_unique<ServerCallbackAdapter>(CUserSocket::Allocate);
        timerBlocks.reset(new TimerBlockPool(static_cast<std::size_t>(workerCount+1)*200+409));
        for(int i=0;i<workerCount;++i)timers.emplace_back(new TimerWheel(monotonicMs(),timerBlocks.get()));
        listenTimer.reset(new TimerWheel(monotonicMs(),timerBlocks.get()));
        listenThread=std::make_unique<ListenThread>(this);
        for(int i=0;i<workerCount;++i){workers.emplace_back(new IOThreadCallback(this,static_cast<unsigned>(i)));workers.back()->Start();}
        auto monitor=std::make_shared<CPerfMon>(this);if(!monitor->Start(perfIntervalMs))throw std::bad_alloc();std::atomic_store(&perfmon,monitor);
        // Publish accept callbacks last: a monitor/thread startup failure must
        // never strand an already admitted socket in partial startup cleanup.
        if(!listenThread->RegisterWait(this,acceptEvent)||!listenThread->Start())throw std::bad_alloc();
    }catch(...){
        accepting=false;if(auto monitor=std::atomic_load(&perfmon))monitor->Stop();std::atomic_store(&perfmon,std::shared_ptr<CPerfMon>{});if(listenThread)listenThread->Stop();Close();
        for(std::size_t i=0;i<workers.size();++i)PostQueuedCompletionStatus(port,0,0,nullptr);
        for(auto& worker:workers)worker->Join();workers.clear();listenThread.reset();listenTimer.reset();timers.clear();timerBlocks.reset();CloseHandle(port);port=nullptr;running=false;notifyChange();return false;
    }
    return true;
}
bool Server::stopAcceptingImpl() noexcept {
    accepting=false;
    if(listenThread)listenThread->UnregisterWait(this);
    Close();
    std::unique_lock<std::mutex> lock(waitMutex);
    changed.wait(lock, [&]{return pendingAccepts.load() == 0;});
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
    if(!Shutdown())return false;
    { std::unique_lock<std::mutex> guard(waitMutex); changed.wait(guard, [&]{return connections.load() == 0;}); }
    for (std::size_t i = 0; i < workers.size(); ++i) PostQueuedCompletionStatus(port, 0, 0, nullptr);
    for (auto& thread : workers) thread->Join();
    workers.clear();std::atomic_store(&perfmon,std::shared_ptr<CPerfMon>{});listenThread.reset();listenTimer.reset();timers.clear();timerBlocks.reset(); CloseHandle(port); port = nullptr;
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
} // namespace ctransport

using namespace ctransport;
template<class F> static void configure(void* p, F apply) {
    if (!p) return;
    auto* server = static_cast<Server*>(p);
    std::lock_guard<std::mutex> lock(server->lifecycle);
    // Configuration is a startup contract, not unsynchronized live tuning.
    if (!server->running.load()) apply(*server);
}
CIO_API unsigned CTransport_Version() { return 0x00070000; }
CIO_API void* CIO_Create_TcpServerListener() { return new(std::nothrow) Listener; }
CIO_API void CIO_Destroy_TcpServerListener(void* p) { delete static_cast<Listener*>(p); }
CIO_API void* CIO_Create_TcpServer(void* p) { if (!p) return nullptr; try { return new Server(static_cast<Listener*>(p)); } catch (...) { return nullptr; } }
CIO_API void CIO_Destroy_TcpServer(void* p) { if (p) { auto* s = static_cast<Server*>(p); if (s->stop()) s->release(); } }
CIO_API void CIO_Set_FN_Server_OnAccept(void* p, AcceptFn fn) { if (p) static_cast<Listener*>(p)->accept.store(fn); }
CIO_API void CIO_Set_FN_Server_OnReceive(void* p, DataFn fn) { if (p) static_cast<Listener*>(p)->receive.store(fn); }
CIO_API void CIO_Set_FN_Server_OnSend(void* p, DataFn fn) { if (p) static_cast<Listener*>(p)->send.store(fn); }
CIO_API void CIO_Set_FN_Server_OnClose(void* p, CloseFn fn) { if (p) static_cast<Listener*>(p)->close.store(fn); }
CIO_API int CIO_Server_Start(void* p, const char* address, unsigned short port) { return p && static_cast<Server*>(p)->start(address, port); }
CIO_API int CIO_StopAccepting(void* p) { return p && static_cast<Server*>(p)->stopAccepting(); }
CIO_API int CIO_Server_Stop(void* p) { return p && static_cast<Server*>(p)->stop(); }
CIO_API int CIO_Server_Wait(void* p, unsigned timeout) { return p && static_cast<Server*>(p)->wait(timeout); }
CIO_API int CIO_Server_Disconnect(void* p, std::uint64_t id, int) {
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id,true); if (!c) return 0;
    ObjectRef hold(c, false); c->disconnect(); return 1;
}
CIO_API int CIO_Server_PauseReceive(void* p, std::uint64_t id, int paused) {
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); return c->pause(paused != 0);
}
CIO_API int CIO_Server_Send(void* p, std::uint64_t id, const void* data, int length) {
    if (!p || length < 0 || (!data && length)) return 0;
    auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); Span span{static_cast<const unsigned char*>(data), static_cast<std::size_t>(length)};
    return c->send(&span, 1);
}
CIO_API int CIO_Server_SendPackets(void* p, std::uint64_t id, const WSABUF* buffers, int count) {
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
CIO_API int CIO_Server_GetListenAddress(void* p, char* out, int* capacity, unsigned short* port) {
    return p && addressText(static_cast<Server*>(p)->listenAddress, out, capacity, port);
}
CIO_API int CIO_Server_GetLocalAddress(void* p, std::uint64_t id, char* out, int* capacity, unsigned short* port) {
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); return addressText(c->LocalAddress(), out, capacity, port);
}
CIO_API int CIO_Server_GetRemoteAddress(void* p, std::uint64_t id, char* out, int* capacity, unsigned short* port) {
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); return addressText(c->RemoteAddress(), out, capacity, port);
}
CIO_API void CIO_Server_SetMaxConnectionCount(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= 100000) s.maxConnections = value; else s.validSettings = false;}); }
CIO_API void CIO_Server_SetWorkerThreadCount(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= 8) s.workerCount = value; else s.validSettings = false;}); }
CIO_API void CIO_TcpServer_SetSocketListenQueue(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= 65535) s.backlog = value; else s.validSettings = false;}); }
CIO_API void CIO_TcpServer_SetSocketBufferSize(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= static_cast<int>(LargeBufferBytes)) s.recvBytes = value; else s.validSettings = false;}); }
CIO_API void CIO_TcpServer_SetNoDelay(void* p, int value) { configure(p, [=](Server& s){s.noDelay = value != 0;}); }
CIO_API void CIO_Server_SetSendPolicy(void* p, int value) { configure(p, [=](Server& s){if (value != 0) s.validSettings = false;}); }
CIO_API void CIO_Server_SetOnSendSyncPolicy(void* p, int value) { configure(p, [=](Server& s){if (value != 0) s.validSettings = false;}); }

CIO_API void CTransport_SetTaskCallback(void* p, TaskFn fn) { if (p) static_cast<Listener*>(p)->task.store(fn); }
CIO_API int CTransport_Server_PostTask(void* p, std::uint64_t token) { return p && static_cast<Server*>(p)->scheduleTask(token, 0); }
CIO_API int CTransport_Server_Schedule(void* p, std::uint64_t token, unsigned delay) { return p && static_cast<Server*>(p)->scheduleTask(token, delay); }
CIO_API int CTransport_Server_Cancel(void* p, std::uint64_t token) { return p && static_cast<Server*>(p)->cancelTask(token); }
CIO_API int CTransport_Server_SetCodecApi(void* p,const std::uintptr_t* functions,int count) {
    if(!p)return 0;auto* server=static_cast<Server*>(p);
    try{auto api=std::make_shared<L1CodecApi>();if(!api->assign(functions,count))return 0;
        std::lock_guard<std::mutex> lock(server->codecGate);if(server->codec)return 1;server->codec=std::move(api);return 1;
    }catch(...){return 0;}
}
CIO_API void CTransport_SetUserPacketCallback(void* p,PacketFn fn){if(p)static_cast<Listener*>(p)->packet.store(fn);}
CIO_API void CTransport_SetNetworkDeadlineCallback(void* p,DeadlineFn fn){if(p)static_cast<Listener*>(p)->deadline.store(fn);}
CIO_API int CTransport_UserSocket_Initialize(void* p,std::uint64_t id,unsigned seed) {
    if(!p)return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);return c->initializeUserSocket(seed);
}
CIO_API int CTransport_UserSocket_ReleasePacket(void* p,std::uint64_t id,std::uint64_t sequence) {
    if(!p)return 0;auto* c=static_cast<Server*>(p)->find(id,true);if(!c)return 0;ObjectRef hold(c,false);return c->releasePacket(sequence);
}
CIO_API int CTransport_UserSocket_Encrypt(void* p,std::uint64_t id,void* bytes,int size) {
    if(!p||size<0||(!bytes&&size))return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    std::lock_guard<std::mutex> lock(c->gate);return !c->state.closing&&c->UserProtocol()&&c->UserProtocol()->codec->encrypt(bytes,size);
}
CIO_API int CTransport_UserSocket_SetSessionState(void* p,std::uint64_t id,int phase) {
    if(!p||phase<0||phase>4)return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    std::lock_guard<std::mutex> lock(c->gate);if(c->state.closing||!c->UserProtocol())return 0;if(!c->SetSessionStateLocked(phase))return 0;if(phase!=0)c->loginDue=0;return 1;
}
CIO_API int CTransport_UserSocket_ConfigureDeadlines(void* p,std::uint64_t id,unsigned loginMs) {
    if(!p||loginMs>0x7fffffffu)return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    {std::lock_guard<std::mutex> lock(c->gate);if(c->state.closing||!c->UserProtocol())return 0;c->loginDue=loginMs&&c->sessionState==0?monotonicMs()+loginMs:0;}
    return c->requestDeadline(loginMs?std::min(loginMs,5000u):5000u);
}
CIO_API int CTransport_UserSocket_RequestDeadline(void* p,std::uint64_t id,unsigned delay) {
    if(!p||delay>0x7fffffffu)return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);return c->requestDeadline(delay);
}
CIO_API int CTransport_UserSocket_GetState(void* p,std::uint64_t id,std::uint64_t* out,int count) {
    if(!p||!out||count<6)return 0;auto* c=static_cast<Server*>(p)->find(id,true);if(!c)return 0;ObjectRef hold(c,false);
    std::lock_guard<std::mutex> lock(c->gate);if(!c->UserProtocol())return 0;c->UserProtocol()->snapshot(out,monotonicMs());
    if(count>=10){out[6]=c->packetBorrowed;out[7]=c->packetSequence;out[8]=c->UserProtocol()->codec->pending();out[9]=c->sessionState;}if(count>=11)out[10]=c->SocketNumber();if(count>=14){out[11]=c->AccountId();out[12]=c->CharacterEntered();out[13]=c->HasMacIdentity();}return 1;
}
CIO_API int CTransport_Server_GetRegistryDiagnostics(void* p,std::uint64_t* out,int count){if(!p||!out||count<6)return 0;static_cast<Server*>(p)->registry.diagnostics(out);return 1;}
CIO_API int CTransport_Server_ThreadOwner(void* p) {
    return p && currentWorker == p ? static_cast<int>(currentWorkerIndex) : -1;
}
CIO_API void* CTransport_Server_RetainReceiveBuffer(void* p, std::uint64_t id) {
    if (!p) return nullptr; auto* c = static_cast<Server*>(p)->find(id); if (!c) return nullptr;
    ObjectRef hold(c, false); std::lock_guard<std::mutex> lock(c->gate);
    if (!c->state.readDelivering || !c->reads.block() || c->UserProtocol()) return nullptr;
    return new(std::nothrow) BufferLease(c->server, c->reads.block());
}
CIO_API const void* CTransport_Buffer_Data(void* p) { auto* lease = static_cast<BufferLease*>(p); return lease ? lease->block->data.data() + lease->offset : nullptr; }
CIO_API unsigned CTransport_Buffer_Size(void* p) { return p ? static_cast<unsigned>(static_cast<BufferLease*>(p)->length) : 0; }
CIO_API void CTransport_Buffer_Release(void* p) { delete static_cast<BufferLease*>(p); }

CIO_API void CTransport_SetClosingCallback(void* p, CloseFn fn) { if (p) static_cast<Listener*>(p)->closing.store(fn); }
CIO_API int CTransport_Server_GetDiagnostics(void* p, std::uint64_t* values, unsigned count) {
    if (!p || !values || count < 8) return 0;
    auto* s = static_cast<Server*>(p);
    values[0] = s->buffers.allocated(); values[1] = s->buffers.exhaustions();
    values[2] = s->buffers.initializationFailures(); values[3] = s->buffers.corruptions();
    values[4] = s->timerInline.load(); values[5] = s->timerPosted.load();
    values[6] = s->closeStarts.load(); values[7] = s->closeTerminals.load();
    return 1;
}


CIO_API int CTransport_Server_ConfigureLimits(void* p,unsigned ip,unsigned mac){
    if(!p||ip>100000||mac>100000)return 0;auto* server=static_cast<Server*>(p);
    std::lock_guard<std::mutex> lock(server->lifecycle);
    return !server->running.load()&&server->socketLimiter.Configure(ip,mac);
}
CIO_API int CTransport_Server_GetLimiterDiagnostics(void* p,std::uint64_t* out,int count){
    if(!p||!out||count<4)return 0;static_cast<Server*>(p)->socketLimiter.Diagnostics(out);return 1;
}
CIO_API int CTransport_Server_GetListenerDiagnostics(void* p,std::uint64_t* out,int count){
    if(!p||!out||count<8)return 0;auto* s=static_cast<Server*>(p);
    out[0]=s->accepting.load();out[1]=s->waitCallbacks;out[2]=s->acceptCallbacks;out[3]=s->lastWaitError;
    out[4]=s->waitFailures;out[5]=s->pendingAccepts;out[6]=s->socketNumbers.size();out[7]=s->workerCount;if(count>=9)out[8]=s->SocketMapCount();return 1;
}
CIO_API int CTransport_Server_GetThreadDiagnostics(void* p,std::uint64_t* out,int count){
    if(!p||!out||count<54)return 0;auto* s=static_cast<Server*>(p);
    for(unsigned n=0;n<18;++n){out[n*3]=s->callbackCounts[n];out[n*3+1]=s->callbackMillis[n];out[n*3+2]=s->callbackStarted[n];}return 1;
}
CIO_API std::uint64_t CTransport_UserSocket_FindByNumber(void* p,unsigned number){
    if(!p||!number)return 0;auto* s=static_cast<Server*>(p);auto* user=s->socketNumbers.FindObject(number);
    if(!user)return 0;ObjectRef hold(user,false);std::uint64_t id;
    {std::lock_guard<std::mutex> lock(user->gate);if(user->state.closing)return 0;id=user->id;}
    auto* active=s->find(id);if(!active)return 0;
    ObjectRef activeHold(active,false);return active==user?id:0;
}
CIO_API int CTransport_UserSocket_SetMacIdentity(void* p,std::uint64_t id,std::uint64_t mac){
    if(!p||!mac)return 0;auto* s=static_cast<Server*>(p);auto* c=s->find(id);if(!c)return 0;ObjectRef hold(c,false);
    auto* socket=dynamic_cast<CSocket*>(c);return socket&&socket->SetMacIdentity(mac);
}
CIO_API int CTransport_Server_ReportSockets(void* p,std::uint64_t* out,int capacity,int stride){
    if(!p||capacity<0||stride!=12||(!out&&capacity))return -1;auto* s=static_cast<Server*>(p);
    std::vector<CIOSocket*> snapshot;if(!s->registry.snapshot(snapshot))return -1;
    int written=0;for(auto* c:snapshot){
        if(out&&written<capacity){auto* row=out+static_cast<std::size_t>(written++)*stride;std::lock_guard<std::mutex> lock(c->gate);
            row[0]=c->id;row[1]=c->SocketNumber();row[2]=c->socket;row[3]=c->state.closing;row[4]=c->ioTerminal;
            row[5]=c->packetBorrowed;row[6]=c->state.readPosted;row[7]=c->state.writePosted;row[8]=c->writes.size();
            const auto& address=c->RemoteAddress();row[9]=address.ss_family;row[10]=row[11]=0;
            if(address.ss_family==AF_INET){const auto* ipv4=reinterpret_cast<const sockaddr_in*>(&address);row[10]=ntohl(ipv4->sin_addr.s_addr);row[11]=ntohs(ipv4->sin_port);}
        }c->release();
    }
    return out?written:static_cast<int>(snapshot.size());
}

CIO_API int CTransport_UserSocket_SetAccountIdentity(void* p,std::uint64_t id,unsigned account){
    if(!p)return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    auto* user=dynamic_cast<CUserSocket*>(c);if(!user)return 0;
    std::lock_guard<std::mutex> lock(c->gate);return !c->state.closing&&user->SetAccountIdentityLocked(account);
}
CIO_API int CTransport_UserSocket_SetCharacterEntered(void* p,std::uint64_t id,int entered){
    if(!p||(entered!=0&&entered!=1))return 0;auto* c=static_cast<Server*>(p)->find(id);if(!c)return 0;ObjectRef hold(c,false);
    auto* user=dynamic_cast<CUserSocket*>(c);if(!user)return 0;
    std::lock_guard<std::mutex> lock(c->gate);return !c->state.closing&&user->SetCharacterEnteredLocked(entered!=0);
}
CIO_API std::uint64_t CTransport_UserSocket_FindEnteringAccount(void* p,unsigned account){
    if(!p||!account)return 0;auto* server=static_cast<Server*>(p);auto* user=server->userSocketMapEnterChar.FindObject(account);
    if(!user)return 0;ObjectRef hold(user,false);std::uint64_t id;
    {std::lock_guard<std::mutex> lock(user->gate);if(user->state.closing||user->AccountId()!=account||!user->CharacterEntered())return 0;id=user->id;}
    auto* active=server->find(id);if(!active)return 0;ObjectRef activeHold(active,false);return active==user?id:0;
}
CIO_API int CTransport_Server_GetAccountRegistryDiagnostics(void* p,std::uint64_t* out,int count){
    if(!p||!out||count<4)return 0;auto* s=static_cast<Server*>(p);
    out[0]=s->socketNumbers.size();out[1]=s->userSocketMapPlaying.size();out[2]=s->userSocketMapEnterChar.size();out[3]=s->duplicateEnterDenied.load();return 1;
}
CIO_API int CTransport_Server_ConfigurePerf(void* p,unsigned interval){
    if(!p||interval<100||interval>60000)return 0;auto* s=static_cast<Server*>(p);
    std::lock_guard<std::mutex> lock(s->lifecycle);if(s->running.load())return 0;s->perfIntervalMs=interval;return 1;
}
CIO_API int CTransport_Server_GetPerfDiagnostics(void* p,std::uint64_t* out,int count){
    if(!p||!out||count<16)return 0;auto* s=static_cast<Server*>(p);
    auto monitor=std::atomic_load(&s->perfmon);if(!monitor)return 0;monitor->Read(out);return 1;
}
