// Newly implemented transport from the documented behaviour of the supplied
// x86/x64 CIO code. No original instruction bytes or game code are embedded.
#ifdef CIONC_TEST
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
#include <mswsock.h>
#define CIO_API extern "C" __declspec(dllexport)
#endif
#include "nc_core.hpp"
#include <condition_variable>
#include <climits>
#include <cstdio>
#include <memory>
#include <shared_mutex>
#include <thread>
#include <unordered_map>
#include <vector>

namespace cionc {
using AcceptFn = int (*)(void*, std::uint64_t, std::uint64_t);
using DataFn = int (*)(void*, std::uint64_t, const void*, int);
using CloseFn = int (*)(void*, std::uint64_t, int, int);
struct Listener {
    std::atomic<AcceptFn> accept{nullptr};
    std::atomic<DataFn> receive{nullptr}, send{nullptr};
    std::atomic<CloseFn> close{nullptr};
};
class Server;
class Connection;
thread_local Server* currentWorker = nullptr;
enum class OpKind { Accept, Read, Write, Close };
struct Operation {
    OVERLAPPED overlapped{}; // Must remain first: the OS returns this address.
    OpKind kind;
    CIOObject* owner;
    explicit Operation(OpKind k, CIOObject* p) : kind(k), owner(p) {}
    void reset() noexcept { overlapped = OVERLAPPED{}; }
};
static_assert(offsetof(Operation, overlapped) == 0, "OVERLAPPED address contract");
struct AcceptOperation : Operation {
    SOCKET socket = INVALID_SOCKET;
    std::array<unsigned char, 2 * (sizeof(sockaddr_storage) + 16)> addresses{};
    explicit AcceptOperation(Server* p);
};
struct RegistryBucket {
    std::mutex mutex;
    std::unordered_map<std::uint64_t, Connection*> entries;
};

class Server final : public CIOObject {
public:
    Listener* const callbacks;
    CIOBufferPool buffers;
    HANDLE port = nullptr;
    SOCKET listening = INVALID_SOCKET;
    LPFN_ACCEPTEX acceptEx = nullptr;
    std::vector<std::unique_ptr<AcceptOperation>> accepts;
    std::vector<std::thread> workers;
    std::array<RegistryBucket, 64> registry;
    std::atomic<std::uint64_t> nextId{1};
    std::atomic<int> connections{0}, pendingAccepts{0};
    std::atomic<bool> accepting{false}, stopping{false}, running{false};
    int maxConnections = 5000, workerCount = 8, backlog = 1024, recvBytes = 4096;
    bool noDelay = true;
    sockaddr_storage listenAddress{}; int listenLength = sizeof(listenAddress);
    std::mutex lifecycle, acceptGate, waitMutex;
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
    ~Server() override { stop(); if (wsaReady) WSACleanup(); }
    bool start(const char* address, unsigned short portNumber) noexcept;
    bool stop() noexcept;
    bool stopAccepting() noexcept;
    bool stopAcceptingImpl() noexcept;
    bool wait(unsigned timeout) noexcept {
        std::unique_lock<std::mutex> lock(waitMutex);
        if (timeout == INFINITE) { changed.wait(lock, [&]{return !running.load();}); return true; }
        return changed.wait_for(lock, std::chrono::milliseconds(timeout), [&]{return !running.load();});
    }
    bool postAccept(AcceptOperation& op) noexcept;
    void completed(void* operation, bool ok, unsigned bytes, unsigned error) noexcept override;
    void worker() noexcept;
    Connection* find(std::uint64_t id) noexcept;
    bool insert(Connection* c) noexcept;
    void remove(Connection* c) noexcept;
    bool post(Operation& op) noexcept {
        op.owner->retain();
        if (PostQueuedCompletionStatus(port, 0, reinterpret_cast<ULONG_PTR>(op.owner), &op.overlapped)) return true;
        op.owner->release(); return false;
    }
};

class Connection final : public CIOObject {
public:
    Server* const server;
    const std::uint64_t id;
    SOCKET socket;
    std::mutex gate;
    IoState state;
    SendChain writes;
    Operation readOp, writeOp, closeOp;
    std::array<unsigned char, BufferBytes> readData{};
    sockaddr_storage local{}, remote{};
    int localLength = sizeof(local), remoteLength = sizeof(remote);
    int closeOperation = 5, closeError = 0;

    Connection(Server* s, SOCKET value, std::uint64_t valueId) : server(s), id(valueId), socket(value),
        readOp(OpKind::Read, this), writeOp(OpKind::Write, this), closeOp(OpKind::Close, this) {
        server->retain();
        getsockname(socket, reinterpret_cast<sockaddr*>(&local), &localLength);
        getpeername(socket, reinterpret_cast<sockaddr*>(&remote), &remoteLength);
    }
    ~Connection() override {
        writes.clear(server->buffers);
        if (socket != INVALID_SOCKET) closesocket(socket);
        server->release();
    }
    void failLocked(int operation, int error) noexcept {
        if (state.closing) return;
        state.closing = true; state.paused = true;
        closeOperation = operation; closeError = error;
        if (socket != INVALID_SOCKET) { auto previous = socket; socket = INVALID_SOCKET; closesocket(previous); }
    }
    // The posting thread owns an API/I/O reference. Failed submissions balance
    // their speculative reference because they will not produce a completion.
    void readLocked() noexcept {
        if (!state.canRead()) return;
        readOp.reset(); state.readPosted = true; retain();
        WSABUF buffer{static_cast<ULONG>(server->recvBytes), reinterpret_cast<char*>(readData.data())};
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
        bool ready;
        {
            std::lock_guard<std::mutex> lock(gate);
            ready = state.canClose();
            if (ready) { state.closeQueued = true; closeOp.reset(); }
        }
        if (ready && !server->post(closeOp)) closeCallback();
    }
    void closeCallback() noexcept {
        // No remaining kernel I/O/delivery can refer to a reused byte buffer.
        if (auto fn = server->callbacks->close.load()) fn(server, id, closeOperation, closeError);
        server->remove(this);
    }
    void disconnect(int operation = 5, int error = 0) noexcept {
        { std::lock_guard<std::mutex> lock(gate); failLocked(operation, error); }
        finishIfReady();
    }
    bool pause(bool paused) noexcept {
        bool open;
        { std::lock_guard<std::mutex> lock(gate); open = !state.closing; if (open) { state.paused = paused; readLocked(); open = !state.closing; } }
        finishIfReady(); return open;
    }
    bool send(const Span* spans, unsigned count) noexcept {
        bool ok;
        {
            std::lock_guard<std::mutex> lock(gate);
            ok = !state.closing && writes.append(server->buffers, spans, count);
            if (ok) { writeLocked(); ok = !state.closing; }
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
    void completed(void* operation, bool ok, unsigned bytes, unsigned error) noexcept override {
        auto& op = *static_cast<Operation*>(operation);
        if (op.kind == OpKind::Close) { closeCallback(); return; }
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
                    state.readDelivering = true; data = readData.data(); deliver = true;
                } else if (!reading && writes.head()) {
                    data = writes.head()->data.data() + writes.head()->offset;
                    if (writes.complete(bytes, retired)) { state.writeDelivering = true; deliver = true; }
                    else failLocked(3, ERROR_INVALID_DATA);
                } else failLocked(reading ? 2 : 3, ERROR_INVALID_DATA);
            }
        }
        int result = 0;
        if (deliver) {
            const auto fn = (op.kind == OpKind::Read ? server->callbacks->receive.load() : server->callbacks->send.load());
            if (fn) result = fn(server, id, data, static_cast<int>(bytes));
        }
        {
            std::lock_guard<std::mutex> lock(gate);
            if (op.kind == OpKind::Read) state.readDelivering = false; else state.writeDelivering = false;
            if (result != 0) failLocked(op.kind == OpKind::Read ? 2 : 3, ERROR_INVALID_DATA);
            readLocked(); writeLocked();
        }
        server->buffers.recycle(retired);
        finishIfReady();
    }
};

AcceptOperation::AcceptOperation(Server* s) : Operation(OpKind::Accept, s) {}
Connection* Server::find(std::uint64_t id) noexcept {
    auto& bucket = registry[id % registry.size()];
    std::lock_guard<std::mutex> lock(bucket.mutex);
    const auto at = bucket.entries.find(id);
    if (at == bucket.entries.end()) return nullptr;
    at->second->retain(); return at->second;
}
bool Server::insert(Connection* c) noexcept {
    if (connections.fetch_add(1) >= maxConnections) { connections.fetch_sub(1); return false; }
    try {
        auto& bucket = registry[c->id % registry.size()];
        std::lock_guard<std::mutex> lock(bucket.mutex);
        if (!bucket.entries.emplace(c->id, c).second) { connections.fetch_sub(1); return false; }
        c->retain(); return true;
    } catch (...) { connections.fetch_sub(1); return false; }
}
void Server::remove(Connection* c) noexcept {
    bool removed;
    {
        auto& bucket = registry[c->id % registry.size()];
        std::lock_guard<std::mutex> lock(bucket.mutex);
        removed = bucket.entries.erase(c->id) != 0;
    }
    if (removed) { connections.fetch_sub(1); c->release(); notifyChange(); }
}
void Server::worker() noexcept {
    currentWorker = this;
    for (;;) {
        DWORD bytes = 0; ULONG_PTR key = 0; OVERLAPPED* overlapped = nullptr;
        const BOOL ok = GetQueuedCompletionStatus(port, &bytes, &key, &overlapped, INFINITE);
        const DWORD error = ok ? 0 : GetLastError();
        if (!overlapped) { if (key == 0) break; else continue; }
        auto* operation = reinterpret_cast<Operation*>(overlapped);
        auto* owner = reinterpret_cast<CIOObject*>(key);
        // Each accepted kernel/synthetic request acquired exactly this hold.
        owner->completed(operation, ok != FALSE, bytes, error);
        owner->release();
    }
    currentWorker = nullptr;
}
bool Server::postAccept(AcceptOperation& op) noexcept {
    std::lock_guard<std::mutex> lock(acceptGate);
    if (!accepting.load()) return false;
    const int family = reinterpret_cast<sockaddr*>(&listenAddress)->sa_family;
    op.socket = WSASocketW(family, SOCK_STREAM, IPPROTO_TCP, nullptr, 0, WSA_FLAG_OVERLAPPED);
    if (op.socket == INVALID_SOCKET) return false;
    op.reset(); retain(); pendingAccepts.fetch_add(1);
    DWORD received = 0;
    const BOOL ok = acceptEx(listening, op.socket, op.addresses.data(), 0,
        sizeof(sockaddr_storage) + 16, sizeof(sockaddr_storage) + 16, &received, &op.overlapped);
    if (!ok && WSAGetLastError() != WSA_IO_PENDING) {
        closesocket(op.socket); op.socket = INVALID_SOCKET;
        pendingAccepts.fetch_sub(1); release(); notifyChange(); return false;
    }
    return true;
}
void Server::completed(void* operation, bool ok, unsigned, unsigned) noexcept {
    auto& op = *static_cast<AcceptOperation*>(operation);
    const SOCKET acceptedSocket = op.socket; op.socket = INVALID_SOCKET;
    bool admit;
    SOCKET listenerSnapshot;
    {
        std::lock_guard<std::mutex> lock(acceptGate);
        admit = ok && accepting.load(); listenerSnapshot = listening;
    }
    if (admit) {
        if (setsockopt(acceptedSocket, SOL_SOCKET, SO_UPDATE_ACCEPT_CONTEXT,
                reinterpret_cast<const char*>(&listenerSnapshot), sizeof(listenerSnapshot)) != 0) {
            closesocket(acceptedSocket);
            pendingAccepts.fetch_sub(1); notifyChange();
            if (accepting.load()) postAccept(op);
            return;
        }
        int enabled = noDelay ? 1 : 0;
        setsockopt(acceptedSocket, IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<const char*>(&enabled), sizeof(enabled));
        auto* c = new(std::nothrow) Connection(this, acceptedSocket, nextId.fetch_add(1));
        if (c) {
            ObjectRef temporary(c, false);
            if (CreateIoCompletionPort(reinterpret_cast<HANDLE>(acceptedSocket), port,
                    reinterpret_cast<ULONG_PTR>(static_cast<CIOObject*>(c)), 0) && insert(c)) {
                const auto fn = callbacks->accept.load();
                const int result = fn ? fn(this, c->id, static_cast<std::uint64_t>(acceptedSocket)) : 2;
                c->accepted(result);
            }
        } else closesocket(acceptedSocket);
    } else if (acceptedSocket != INVALID_SOCKET) closesocket(acceptedSocket);
    // Count remains live until the admission callback has returned. StopAccept
    // therefore cannot return while an old accept callback is still executing.
    pendingAccepts.fetch_sub(1); notifyChange();
    if (accepting.load()) postAccept(op);
}
bool Server::start(const char* address, unsigned short number) noexcept {
    std::lock_guard<std::mutex> lock(lifecycle);
    if (running.load() || !wsaReady || !validSettings || !address || workerCount < 1 || workerCount > 64 || maxConnections < 1) return false;
    addrinfo hints{}; hints.ai_family = AF_UNSPEC; hints.ai_socktype = SOCK_STREAM; hints.ai_protocol = IPPROTO_TCP; hints.ai_flags = AI_PASSIVE;
    char service[8]{}; std::snprintf(service, sizeof(service), "%u", static_cast<unsigned>(number));
    addrinfo* result = nullptr;
    if (getaddrinfo(address, service, &hints, &result) != 0) return false;
    for (auto* at = result; at; at = at->ai_next) {
        listening = WSASocketW(at->ai_family, SOCK_STREAM, IPPROTO_TCP, nullptr, 0, WSA_FLAG_OVERLAPPED);
        if (listening == INVALID_SOCKET) continue;
        if (bind(listening, at->ai_addr, static_cast<int>(at->ai_addrlen)) == 0 && listen(listening, backlog) == 0) break;
        closesocket(listening); listening = INVALID_SOCKET;
    }
    freeaddrinfo(result);
    if (listening == INVALID_SOCKET) return false;
    listenLength = sizeof(listenAddress);
    getsockname(listening, reinterpret_cast<sockaddr*>(&listenAddress), &listenLength);
    DWORD returned = 0; GUID guid = WSAID_ACCEPTEX;
    if (WSAIoctl(listening, SIO_GET_EXTENSION_FUNCTION_POINTER, &guid, sizeof(guid), &acceptEx, sizeof(acceptEx), &returned, nullptr, nullptr) != 0) {
        closesocket(listening); listening = INVALID_SOCKET; return false;
    }
    port = CreateIoCompletionPort(INVALID_HANDLE_VALUE, nullptr, 0, workerCount);
    if (!port || !CreateIoCompletionPort(reinterpret_cast<HANDLE>(listening), port, reinterpret_cast<ULONG_PTR>(static_cast<CIOObject*>(this)), 0)) {
        closesocket(listening); listening = INVALID_SOCKET; if (port) CloseHandle(port); port = nullptr; return false;
    }
    running.store(true); stopping.store(false); accepting.store(true);
    try {
        for (int i = 0; i < workerCount; ++i) workers.emplace_back([this]{worker();});
        for (int i = 0; i < std::min(workerCount * 2, 32); ++i) accepts.emplace_back(new AcceptOperation(this));
    } catch (...) {
        accepting.store(false); closesocket(listening); listening = INVALID_SOCKET;
        for (std::size_t i = 0; i < workers.size(); ++i) PostQueuedCompletionStatus(port, 0, 0, nullptr);
        for (auto& thread : workers) thread.join(); workers.clear(); accepts.clear(); CloseHandle(port); port = nullptr; running.store(false); notifyChange(); return false;
    }
    unsigned posted = 0; for (auto& op : accepts) if (postAccept(*op)) ++posted;
    // No accept can be posted: terminate the workers rather than advertising a
    // running listener which can never admit a client.
    if (!posted) {
        accepting.store(false); closesocket(listening); listening = INVALID_SOCKET;
        for (std::size_t i = 0; i < workers.size(); ++i) PostQueuedCompletionStatus(port, 0, 0, nullptr);
        for (auto& thread : workers) thread.join(); workers.clear(); accepts.clear(); CloseHandle(port); port = nullptr; running.store(false); notifyChange(); return false;
    }
    return true;
}
bool Server::stopAcceptingImpl() noexcept {
    {
        std::lock_guard<std::mutex> lock(acceptGate);
        accepting.store(false);
        if (listening != INVALID_SOCKET) { closesocket(listening); listening = INVALID_SOCKET; }
    }
    std::unique_lock<std::mutex> lock(waitMutex);
    changed.wait(lock, [&]{return pendingAccepts.load() == 0;});
    return true;
}
bool Server::stopAccepting() noexcept {
    if (currentWorker == this) return false;
    std::lock_guard<std::mutex> lock(lifecycle);
    return stopAcceptingImpl();
}
bool Server::stop() noexcept {
    if (currentWorker == this) return false;
    std::lock_guard<std::mutex> lock(lifecycle);
    if (!running.load()) return true;
    stopping.store(true); stopAcceptingImpl();
    // Copy one bucket at a time. No map lock is held while socket close or a
    // callback can run; an allocation failure leaves native resources alive.
    for (auto& bucket : registry) {
        std::vector<Connection*> copy;
        {
            std::lock_guard<std::mutex> guard(bucket.mutex);
            try { copy.reserve(bucket.entries.size()); for (auto& at : bucket.entries) { copy.push_back(at.second); at.second->retain(); } }
            catch (...) { for (auto* c : copy) c->release(); return false; }
        }
        for (auto* c : copy) { c->disconnect(); c->release(); }
    }
    { std::unique_lock<std::mutex> guard(waitMutex); changed.wait(guard, [&]{return connections.load() == 0;}); }
    for (std::size_t i = 0; i < workers.size(); ++i) PostQueuedCompletionStatus(port, 0, 0, nullptr);
    for (auto& thread : workers) thread.join();
    workers.clear(); accepts.clear(); CloseHandle(port); port = nullptr;
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
} // namespace cionc

using namespace cionc;
template<class F> static void configure(void* p, F apply) {
    if (!p) return;
    auto* server = static_cast<Server*>(p);
    std::lock_guard<std::mutex> lock(server->lifecycle);
    // Configuration is a startup contract, not unsynchronized live tuning.
    if (!server->running.load()) apply(*server);
}
CIO_API unsigned CIONC_Version() { return 0x00010000; }
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
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
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
    ObjectRef hold(c, false); return addressText(c->local, out, capacity, port);
}
CIO_API int CIO_Server_GetRemoteAddress(void* p, std::uint64_t id, char* out, int* capacity, unsigned short* port) {
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); return addressText(c->remote, out, capacity, port);
}
CIO_API void CIO_Server_SetMaxConnectionCount(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= 100000) s.maxConnections = value; else s.validSettings = false;}); }
CIO_API void CIO_Server_SetWorkerThreadCount(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= 64) s.workerCount = value; else s.validSettings = false;}); }
CIO_API void CIO_TcpServer_SetSocketListenQueue(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= 65535) s.backlog = value; else s.validSettings = false;}); }
CIO_API void CIO_TcpServer_SetSocketBufferSize(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= static_cast<int>(BufferBytes)) s.recvBytes = value; else s.validSettings = false;}); }
CIO_API void CIO_TcpServer_SetNoDelay(void* p, int value) { configure(p, [=](Server& s){s.noDelay = value != 0;}); }
CIO_API void CIO_Server_SetSendPolicy(void* p, int value) { configure(p, [=](Server& s){if (value != 0) s.validSettings = false;}); }
CIO_API void CIO_Server_SetOnSendSyncPolicy(void* p, int value) { configure(p, [=](Server& s){if (value != 0) s.validSettings = false;}); }
