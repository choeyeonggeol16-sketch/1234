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
#include <mswsock.h>
#define CIO_API extern "C" __declspec(dllexport)
#endif
#include "cio_core.hpp"
#include "timer_wheel.hpp"
#include "user_socket.hpp"
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
using NtStatusToErrorFn = ULONG (WINAPI *)(LONG);
constexpr ULONG CompletionBatchSize = 128;
struct Listener {
    std::atomic<AcceptFn> accept{nullptr};
    std::atomic<DataFn> receive{nullptr}, send{nullptr};
    std::atomic<CloseFn> close{nullptr}, closing{nullptr};
    std::atomic<void (*)(void*, std::uint64_t, int)> task{nullptr};
};
class Server;
class Connection;
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
enum class OpKind { Accept, Read, Write, Close, Task };
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
    NtStatusToErrorFn ntStatusToError = nullptr;
    std::vector<std::unique_ptr<AcceptOperation>> accepts;
    std::vector<std::thread> workers;
    std::array<RegistryBucket, 64> registry;
    std::unique_ptr<TimerBlockPool> timerBlocks;
    std::vector<std::unique_ptr<TimerWheel>> timers;
    std::atomic<std::uint64_t> timerInline{0}, timerPosted{0}, closeStarts{0}, closeTerminals{0};
    std::mutex taskGate;
    std::unordered_map<std::uint64_t, NativeTask*> tasks;
    std::atomic<unsigned> taskCount{0};
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
        if (currentWorker == this || currentCallbackServer == this) return !running.load();
        std::unique_lock<std::mutex> lock(waitMutex);
        if (timeout == INFINITE) { changed.wait(lock, [&]{return !running.load();}); return true; }
        return changed.wait_for(lock, std::chrono::milliseconds(timeout), [&]{return !running.load();});
    }
    bool postAccept(AcceptOperation& op) noexcept;
    void completed(void* operation, bool ok, unsigned bytes, unsigned error) noexcept override;
    void worker(unsigned owner = 0) noexcept;
    bool scheduleTask(std::uint64_t token, unsigned delay) noexcept;
    bool cancelTask(std::uint64_t token) noexcept;
    bool cancelTasks() noexcept;
    void finishTask(NativeTask* task) noexcept;
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
    SOCKET retiredSocket = INVALID_SOCKET;
    std::mutex gate;
    IoState state;
    SendChain writes;
    ReceiveWindow reads;
    Operation readOp, writeOp, closeOp;
    sockaddr_storage local{}, remote{};
    int localLength = sizeof(local), remoteLength = sizeof(remote);
    int closeOperation = 5, closeError = 0;
    std::unique_ptr<CUserSocket> userSocket;

    Connection(Server* s, SOCKET value, std::uint64_t valueId) : server(s), id(valueId), socket(value),
        writes(s->recvBytes > static_cast<int>(BufferBytes) ? LargeBufferBytes : BufferBytes),
        reads(s->buffers, s->recvBytes > static_cast<int>(BufferBytes) ? LargeBufferBytes : BufferBytes),
        readOp(OpKind::Read, this), writeOp(OpKind::Write, this), closeOp(OpKind::Close, this) {
        server->retain();
        getsockname(socket, reinterpret_cast<sockaddr*>(&local), &localLength);
        getpeername(socket, reinterpret_cast<sockaddr*>(&remote), &remoteLength);
    }
    ~Connection() override {
        writes.clear(server->buffers);
        reads.clear();
        if (socket != INVALID_SOCKET) closesocket(socket);
        server->release();
    }
    void failLocked(int operation, int error) noexcept {
        if (state.closing) return;
        state.closing = true; state.paused = true;
        if (userSocket) userSocket->stop();
        closeOperation = operation; closeError = error;
        if (socket != INVALID_SOCKET) { retiredSocket = socket; socket = INVALID_SOCKET; state.closeRetiring = true; }
    }
    void retireSocket() noexcept {
        SOCKET previous;
        {
            std::lock_guard<std::mutex> lock(gate);
            previous = retiredSocket; retiredSocket = INVALID_SOCKET;
        }
        if (previous == INVALID_SOCKET) return;
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
        if (!state.canRead()) return;
        if (!reads.prepare()) { failLocked(2, 10055); return; }
        readOp.reset(); state.readPosted = true; retain();
        WSABUF buffer{static_cast<ULONG>(server->recvBytes), reinterpret_cast<char*>(reads.block()->data.data() + reads.block()->used)};
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
            ready = state.canClose();
            if (ready) { state.closeQueued = true; closeOp.reset(); }
        }
        if (ready && !server->post(closeOp)) closeCallback();
    }
    void closeCallback() noexcept {
        // No remaining kernel I/O/delivery can refer to a reused byte buffer.
        ++server->closeTerminals;
        CallbackScope callback(server);
        if (auto fn = server->callbacks->close.load()) fn(server, id, closeOperation, closeError);
        server->remove(this);
    }
    void disconnect(int operation = 5, int error = 0) noexcept {
        { std::lock_guard<std::mutex> lock(gate); failLocked(operation, error); }
        finishIfReady();
    }
    bool pause(bool paused) noexcept {
        bool open;
        { std::lock_guard<std::mutex> lock(gate); open = !state.closing; if (open) { state.paused = paused; if (userSocket) userSocket->pause(paused, monotonicMs()); readLocked(); open = !state.closing; } }
        finishIfReady(); return open;
    }
    bool enableUserSocket() noexcept {
        std::lock_guard<std::mutex> lock(gate);
        if (state.closing || state.readPosted || state.readDelivering || !state.paused) return false;
        if (userSocket) return true;
        try { userSocket = std::make_unique<CUserSocket>(); return true; }
        catch (...) { return false; }
    }
    bool send(const Span* spans, unsigned count) noexcept {
        bool ok;
        {
            std::lock_guard<std::mutex> lock(gate);
            ok = !state.closing && writes.append(server->buffers, spans, count);
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
                    if (reads.append(bytes)) {
                        state.readDelivering = true; data = reads.block()->data.data() + reads.block()->offset; deliver = true;
                    } else failLocked(2, ERROR_INVALID_DATA);
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
            if (fn && op.kind == OpKind::Read && userSocket) {
                if (!userSocket->feed(data, bytes, monotonicMs(), [&](const unsigned char* frame, unsigned size) {
                    return fn(server, id, frame, static_cast<int>(size)) == 0;
                })) result = 2;
            } else if (fn) result = fn(server, id, data, static_cast<int>(bytes));
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

enum class TaskState { Pending, Queued, Running, Cancelled, Done };
class NativeTask final : public CIOObject {
public:
    Server* const server;
    const std::uint64_t token;
    const unsigned owner;
    Operation op{OpKind::Task, this};
    std::atomic<TaskState> state{TaskState::Pending};
    static unsigned registeringOwner(Server* s) noexcept {
        if (currentWorker == s) return currentWorkerIndex;
        const auto registered = s->buffers.currentOwner();
        return (registered >= 64 ? registered - 64 : registered) % static_cast<unsigned>(s->workerCount);
    }
    NativeTask(Server* s, std::uint64_t t) : server(s), token(t), owner(registeringOwner(s)) { server->retain(); }
    ~NativeTask() override { server->release(); }
    bool submit() noexcept {
        auto expected = TaskState::Pending;
        if (!state.compare_exchange_strong(expected, TaskState::Queued)) return false;
        if (server->post(op)) return true;
        const int error = static_cast<int>(GetLastError());
        expected = TaskState::Queued;
        if (state.compare_exchange_strong(expected, TaskState::Running)) {
            if (auto fn = server->callbacks->task.load()) fn(server, token, error ? error : 31);
            state.store(TaskState::Done);
        }
        server->finishTask(this); return false;
    }
    void runInline() noexcept {
        auto expected = TaskState::Pending;
        if (state.compare_exchange_strong(expected, TaskState::Running)) {
            if (auto fn = server->callbacks->task.load()) fn(server, token, 0);
            state.store(TaskState::Done);
        }
        server->finishTask(this);
    }
    void completed(void*, bool, unsigned, unsigned) noexcept override {
        auto expected = TaskState::Queued;
        if (state.compare_exchange_strong(expected, TaskState::Running)) {
            if (auto fn = server->callbacks->task.load()) fn(server, token, 0);
            state.store(TaskState::Done);
        }
        server->finishTask(this);
    }
};
bool Server::scheduleTask(std::uint64_t token, unsigned delay) noexcept {
    if (!token || delay > 0x7fffffffu) return false;
    std::lock_guard<std::mutex> lock(taskGate);
    if (!running.load() || stopping.load() || tasks.size() >= 65536 || tasks.find(token) != tasks.end()) return false;
    NativeTask* task = nullptr;
    try {
        task = new NativeTask(this, token);
        tasks.emplace(token, task);
    } catch (...) { if (task) task->release(); return false; }
    ++taskCount;
    bool queued;
    if (delay) queued = timers[task->owner]->schedule(token, task, monotonicMs() + delay);
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
        if (auto* queued = timers[task->owner]->cancel(token)) queued->release();
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
void Server::worker(unsigned owner) noexcept {
    currentWorker = this;
    currentWorkerIndex = owner;
    CIOBufferPool::OwnerScope bufferOwner(buffers, owner);
    TimerDispatchPolicy timerDispatch;
    // One stack batch per worker. Entry.Internal is the status snapshot from
    // this dequeue; a callback may immediately resubmit/reuse its OVERLAPPED.
    std::array<OVERLAPPED_ENTRY, CompletionBatchSize> entries{};
    for (;;) {
        if (owner < timers.size()) {
            const auto dueSnapshot = monotonicMs();
            for (;;) {
                auto* due = timers[owner]->takeDue(dueSnapshot);
                if (!due) break;
                if (timerDispatch.post(owner)) { ++timerPosted; static_cast<NativeTask*>(due)->submit(); }
                else { ++timerInline; static_cast<NativeTask*>(due)->runInline(); }
                due->release();
            }
        }
        ULONG removed = 0;
        if (!GetQueuedCompletionStatusEx(port, entries.data(), CompletionBatchSize,
                &removed, 50, FALSE)) {
            if (GetLastError() == WAIT_TIMEOUT) continue;
            break;
        }
        ULONG exitTokens = 0;
        for (ULONG i = 0; i < removed; ++i) {
            const auto& entry = entries[i];
            if (!entry.lpOverlapped) {
                if (entry.lpCompletionKey == 0) ++exitTokens;
                continue;
            }
            const LONG status = static_cast<LONG>(static_cast<ULONG>(entry.Internal));
            const bool ok = status >= 0; // NT_SUCCESS, independent of the batch BOOL.
            const DWORD error = ok ? 0 : ntStatusToError(status);
            auto* operation = reinterpret_cast<Operation*>(entry.lpOverlapped);
            auto* owner = reinterpret_cast<CIOObject*>(entry.lpCompletionKey);
            owner->completed(operation, ok, entry.dwNumberOfBytesTransferred, error);
            owner->release();
        }
        if (exitTokens) {
            // A single worker can dequeue several workers' exit tokens. Finish
            // every real completion, consume one token, and return the others.
            // Breaking at the first token would strand I/O holds and workers.
            for (ULONG i = 1; i < exitTokens; ++i)
                PostQueuedCompletionStatus(port, 0, 0, nullptr);
            break;
        }
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
    ntStatusToError = reinterpret_cast<NtStatusToErrorFn>(
        GetProcAddress(GetModuleHandleW(L"ntdll.dll"), "RtlNtStatusToDosError"));
    if (!ntStatusToError) return false;
    if (!buffers.initialize(static_cast<unsigned>(workerCount), std::max<std::size_t>(static_cast<std::size_t>(maxConnections) * 2, workerCount * 200))) return false;
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
        timerBlocks.reset(new TimerBlockPool(static_cast<std::size_t>(workerCount) * 200 + 409));
        for (int i = 0; i < workerCount; ++i) timers.emplace_back(new TimerWheel(monotonicMs(), timerBlocks.get()));
        for (int i = 0; i < workerCount; ++i) workers.emplace_back([this, i]{worker(static_cast<unsigned>(i));});
        for (int i = 0; i < std::min(workerCount * 2, 32); ++i) accepts.emplace_back(new AcceptOperation(this));
    } catch (...) {
        accepting.store(false); closesocket(listening); listening = INVALID_SOCKET;
        for (std::size_t i = 0; i < workers.size(); ++i) PostQueuedCompletionStatus(port, 0, 0, nullptr);
        for (auto& thread : workers) thread.join(); workers.clear(); accepts.clear(); timers.clear(); timerBlocks.reset(); CloseHandle(port); port = nullptr; running.store(false); notifyChange(); return false;
    }
    unsigned posted = 0; for (auto& op : accepts) if (postAccept(*op)) ++posted;
    // No accept can be posted: terminate the workers rather than advertising a
    // running listener which can never admit a client.
    if (!posted) {
        accepting.store(false); closesocket(listening); listening = INVALID_SOCKET;
        for (std::size_t i = 0; i < workers.size(); ++i) PostQueuedCompletionStatus(port, 0, 0, nullptr);
        for (auto& thread : workers) thread.join(); workers.clear(); accepts.clear(); timers.clear(); timerBlocks.reset(); CloseHandle(port); port = nullptr; running.store(false); notifyChange(); return false;
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
    if (currentWorker == this || currentCallbackServer == this) return false;
    std::lock_guard<std::mutex> lock(lifecycle);
    return stopAcceptingImpl();
}
bool Server::stop() noexcept {
    if (currentWorker == this || currentCallbackServer == this) return false;
    std::lock_guard<std::mutex> lock(lifecycle);
    if (!running.load()) return true;
    stopping.store(true); stopAcceptingImpl();
    if (!cancelTasks()) return false;
    { std::unique_lock<std::mutex> guard(waitMutex); changed.wait(guard, [&]{return taskCount.load() == 0;}); }
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
    workers.clear(); accepts.clear(); timers.clear(); timerBlocks.reset(); CloseHandle(port); port = nullptr;
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
CIO_API unsigned CTransport_Version() { return 0x00040000; }
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
CIO_API void CIO_TcpServer_SetSocketBufferSize(void* p, int value) { configure(p, [=](Server& s){if (value > 0 && value <= static_cast<int>(LargeBufferBytes)) s.recvBytes = value; else s.validSettings = false;}); }
CIO_API void CIO_TcpServer_SetNoDelay(void* p, int value) { configure(p, [=](Server& s){s.noDelay = value != 0;}); }
CIO_API void CIO_Server_SetSendPolicy(void* p, int value) { configure(p, [=](Server& s){if (value != 0) s.validSettings = false;}); }
CIO_API void CIO_Server_SetOnSendSyncPolicy(void* p, int value) { configure(p, [=](Server& s){if (value != 0) s.validSettings = false;}); }

CIO_API void CTransport_SetTaskCallback(void* p, TaskFn fn) { if (p) static_cast<Listener*>(p)->task.store(fn); }
CIO_API int CTransport_Server_PostTask(void* p, std::uint64_t token) { return p && static_cast<Server*>(p)->scheduleTask(token, 0); }
CIO_API int CTransport_Server_Schedule(void* p, std::uint64_t token, unsigned delay) { return p && static_cast<Server*>(p)->scheduleTask(token, delay); }
CIO_API int CTransport_Server_Cancel(void* p, std::uint64_t token) { return p && static_cast<Server*>(p)->cancelTask(token); }
CIO_API int CTransport_UserSocket_Enable(void* p, std::uint64_t id) {
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); return c->enableUserSocket();
}
CIO_API int CTransport_UserSocket_Expired(void* p, std::uint64_t id) {
    if (!p) return 0; auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); std::lock_guard<std::mutex> lock(c->gate);
    return c->userSocket && c->userSocket->expired(monotonicMs());
}
CIO_API int CTransport_UserSocket_GetState(void* p, std::uint64_t id, std::uint64_t* out, int count) {
    if (!p || !out || count < 6) return 0; auto* c = static_cast<Server*>(p)->find(id); if (!c) return 0;
    ObjectRef hold(c, false); std::lock_guard<std::mutex> lock(c->gate);
    if (!c->userSocket) return 0; c->userSocket->snapshot(out, monotonicMs()); return 1;
}
CIO_API int CTransport_Server_ThreadOwner(void* p) {
    return p && currentWorker == p ? static_cast<int>(currentWorkerIndex) : -1;
}
CIO_API void* CTransport_Server_RetainReceiveBuffer(void* p, std::uint64_t id) {
    if (!p) return nullptr; auto* c = static_cast<Server*>(p)->find(id); if (!c) return nullptr;
    ObjectRef hold(c, false); std::lock_guard<std::mutex> lock(c->gate);
    if (!c->state.readDelivering || !c->reads.block() || c->userSocket) return nullptr;
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
