#pragma once
// Test-only Windows completion API fixture. The native engine itself is real
// production code; no claim of testing the Windows kernel is made here.
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <deque>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <unordered_map>
#include <vector>
using SOCKET = std::uintptr_t;
using ULONG_PTR = std::uintptr_t;
using HANDLE = void*;
using DWORD = std::uint32_t;
using ULONG = std::uint32_t;
using BOOL = int;
using LONG = std::int32_t;
#define WINAPI
struct OVERLAPPED { std::uintptr_t Internal{}, InternalHigh{}; DWORD Offset{}, OffsetHigh{}; HANDLE event{}; };
struct OVERLAPPED_ENTRY { ULONG_PTR lpCompletionKey; OVERLAPPED* lpOverlapped; ULONG_PTR Internal; DWORD dwNumberOfBytesTransferred; };
struct WSABUF { ULONG len; char* buf; };
struct WSADATA {};
struct GUID { unsigned char bytes[16]; };
constexpr SOCKET INVALID_SOCKET = static_cast<SOCKET>(-1);
inline HANDLE INVALID_HANDLE_VALUE = reinterpret_cast<HANDLE>(static_cast<std::intptr_t>(-1));
constexpr BOOL FALSE = 0, TRUE = 1;
constexpr DWORD INFINITE = 0xffffffffu;
constexpr DWORD WAIT_TIMEOUT = 258;
constexpr int SOCKET_ERROR = -1, WSA_IO_PENDING = 997, WSA_FLAG_OVERLAPPED = 1;
constexpr int ERROR_ACCESS_DENIED = 5, ERROR_INVALID_DATA = 13, SO_UPDATE_ACCEPT_CONTEXT = 0x700b;
constexpr int TCP_NODELAY = 1;
constexpr unsigned SIO_GET_EXTENSION_FUNCTION_POINTER = 1;
#define MAKEWORD(a,b) ((a)|((b)<<8))
#define WSAID_ACCEPTEX GUID{}
using LPFN_ACCEPTEX = BOOL (*)(SOCKET, SOCKET, void*, DWORD, DWORD, DWORD, DWORD*, OVERLAPPED*);
namespace fakewin {
inline thread_local unsigned error = 0;
struct Event { DWORD bytes; ULONG_PTR key; OVERLAPPED* operation; unsigned failure; ULONG_PTR status = 0; };
struct Port { std::mutex mutex; std::condition_variable changed; std::deque<Event> queue; };
struct Read { char* data{}; unsigned capacity{}; OVERLAPPED* operation{}; };
struct Accept { SOCKET socket; OVERLAPPED* operation; };
struct Socket {
 bool open = true; bool resetClose=false; Port* port{}; ULONG_PTR key{};
 Read read; std::deque<Accept> accepts; std::deque<unsigned char> inbound;
 std::vector<unsigned char> wire; std::vector<Event> delayed;
 sockaddr_storage local{}; int localLength = sizeof(sockaddr_in);
};
inline std::mutex mutex;
inline std::unordered_map<SOCKET, std::shared_ptr<Socket>> sockets;
inline SOCKET nextSocket = 1000;
inline std::atomic<unsigned> sendChunk{8192};
inline std::atomic<bool> holdSend{false}, failSend{false}, failRead{false}, failAccept{false};
inline std::atomic<bool> failStatusResolver{false}, failPost{false};
inline std::atomic<unsigned> singleCalls{0}, exCalls{0}, maximumBatch{0}, requestedCapacity{0}, translatedStatuses{0};
inline std::array<std::atomic<unsigned>,129> batchSizes{};
inline ULONG statusError(LONG status) {
 ++translatedStatuses;
 switch(static_cast<ULONG>(status)) {
 case 0xc0000120u:return 995; // STATUS_CANCELLED -> ERROR_OPERATION_ABORTED
 case 0xc0000022u:return 5;   // STATUS_ACCESS_DENIED
 case 0xc0000001u:return 31;  // STATUS_UNSUCCESSFUL -> ERROR_GEN_FAILURE
 default:return 317;
 }
}
inline void post(Port* p, Event event) {
 { std::lock_guard<std::mutex> lock(p->mutex); p->queue.push_back(event); }
 p->changed.notify_one();
}
inline void completeRead(Socket& s) {
 if (!s.read.operation || s.inbound.empty()) return;
 unsigned n = std::min<std::size_t>(s.read.capacity, s.inbound.size());
 for (unsigned i = 0; i < n; ++i) { s.read.data[i] = s.inbound.front(); s.inbound.pop_front(); }
 post(s.port, {n, s.key, s.read.operation, 0}); s.read = {};
}
inline SOCKET connect(SOCKET listener) {
 std::lock_guard<std::mutex> lock(mutex);
 auto& s = *sockets.at(listener);
 if (s.accepts.empty()) throw std::runtime_error("no posted AcceptEx");
 auto accept = s.accepts.front(); s.accepts.pop_front();
 sockets.at(accept.socket)->local = s.local;
 post(s.port, {0, s.key, accept.operation, 0}); return accept.socket;
}
inline void feed(SOCKET socket, const std::vector<unsigned char>& data) {
 std::lock_guard<std::mutex> lock(mutex); auto& s = *sockets.at(socket);
 s.inbound.insert(s.inbound.end(), data.begin(), data.end()); completeRead(s);
}
inline std::vector<unsigned char> wire(SOCKET socket) {
 std::lock_guard<std::mutex> lock(mutex); return sockets.at(socket)->wire;
}
inline void flush(SOCKET socket) {
 std::lock_guard<std::mutex> lock(mutex); auto& s = *sockets.at(socket);
 for (auto event : s.delayed) post(s.port, event); s.delayed.clear();
}
inline BOOL acceptEx(SOCKET listener, SOCKET accepted, void*, DWORD, DWORD, DWORD, DWORD*, OVERLAPPED* ov) {
 std::lock_guard<std::mutex> lock(mutex);
 if (failAccept.exchange(false)) { error = 10022; return FALSE; }
 auto& s = *sockets.at(listener); s.accepts.push_back({accepted, ov}); error = WSA_IO_PENDING; return FALSE;
}
} // namespace fakewin
inline int WSAStartup(int, WSADATA*) { return 0; }
inline int WSACleanup() { return 0; }
inline unsigned GetLastError() { return fakewin::error; }
inline unsigned WSAGetLastError() { return fakewin::error; }
inline HANDLE GetModuleHandleW(const wchar_t*) { return reinterpret_cast<HANDLE>(1); }
using FARPROC = void (*)();
inline FARPROC GetProcAddress(HANDLE, const char* symbol) {
 if(fakewin::failStatusResolver.load() || std::strcmp(symbol,"RtlNtStatusToDosError"))return nullptr;
 return reinterpret_cast<FARPROC>(fakewin::statusError);
}
inline SOCKET WSASocketW(int, int, int, void*, unsigned, unsigned) {
 std::lock_guard<std::mutex> lock(fakewin::mutex); auto id = fakewin::nextSocket++;
 fakewin::sockets[id] = std::make_shared<fakewin::Socket>(); return id;
}
inline HANDLE CreateIoCompletionPort(HANDLE handle, HANDLE existing, ULONG_PTR key, DWORD) {
 if (handle == INVALID_HANDLE_VALUE) return new fakewin::Port;
 std::lock_guard<std::mutex> lock(fakewin::mutex); auto& s = *fakewin::sockets.at(reinterpret_cast<SOCKET>(handle));
 s.port = static_cast<fakewin::Port*>(existing); s.key = key; return existing;
}
inline BOOL PostQueuedCompletionStatus(HANDLE p, DWORD bytes, ULONG_PTR key, OVERLAPPED* operation) {
 if(fakewin::failPost.exchange(false)){fakewin::error=5;return FALSE;}
 fakewin::post(static_cast<fakewin::Port*>(p), {bytes, key, operation, 0}); return TRUE;
}
inline BOOL GetQueuedCompletionStatus(HANDLE handle, DWORD* bytes, ULONG_PTR* key, OVERLAPPED** operation, DWORD) {
 ++fakewin::singleCalls;
 auto& p = *static_cast<fakewin::Port*>(handle); std::unique_lock<std::mutex> lock(p.mutex);
 p.changed.wait(lock, [&]{return !p.queue.empty();}); auto e = p.queue.front(); p.queue.pop_front();
 *bytes = e.bytes; *key = e.key; *operation = e.operation; fakewin::error = e.failure; return !e.failure;
}
inline BOOL GetQueuedCompletionStatusEx(HANDLE handle, OVERLAPPED_ENTRY* entries, ULONG capacity, ULONG* removed, DWORD timeout, BOOL) {
 auto& p = *static_cast<fakewin::Port*>(handle);std::unique_lock<std::mutex> lock(p.mutex);
 if (!p.changed.wait_for(lock, std::chrono::milliseconds(timeout), [&]{return !p.queue.empty();})) { *removed=0;fakewin::error=WAIT_TIMEOUT;return FALSE; }
 const ULONG count=static_cast<ULONG>(std::min<std::size_t>(capacity,p.queue.size()));
 for(ULONG i=0;i<count;++i){
  auto e=p.queue.front();p.queue.pop_front();ULONG status=static_cast<ULONG>(e.status);
  if(!status && e.failure)status=e.failure==995?0xc0000120u:e.failure==5?0xc0000022u:0xc0000001u;
  // Preserve signed-extension as a real x64 status field can do.
  entries[i]={e.key,e.operation,static_cast<ULONG_PTR>(static_cast<std::int64_t>(static_cast<LONG>(status))),e.bytes};
 }
 *removed=count;++fakewin::exCalls;fakewin::requestedCapacity=capacity;
 unsigned old=fakewin::maximumBatch.load();while(old<count&&!fakewin::maximumBatch.compare_exchange_weak(old,count)){}
 if(count<=128)++fakewin::batchSizes[count];
 return TRUE; // Individual failed I/Os do NOT make this dequeue return FALSE.
}
inline BOOL CloseHandle(HANDLE p) { delete static_cast<fakewin::Port*>(p); return TRUE; }
inline int closesocket(SOCKET socket) {
 std::lock_guard<std::mutex> lock(fakewin::mutex); auto& s = *fakewin::sockets.at(socket);
 if (!s.open) return 0; s.open = false;
 if (s.read.operation) { fakewin::post(s.port, {0, s.key, s.read.operation, 995}); s.read = {}; }
 for (auto e : s.delayed) { e.failure = 995; e.bytes = 0; fakewin::post(s.port, e); } s.delayed.clear();
 for (auto a : s.accepts) fakewin::post(s.port, {0, s.key, a.operation, 995}); s.accepts.clear(); return 0;
}
inline int WSARecv(SOCKET socket, WSABUF* buffers, unsigned, DWORD*, DWORD*, OVERLAPPED* ov, void*) {
 std::lock_guard<std::mutex> lock(fakewin::mutex);
 if (fakewin::failRead.exchange(false)) { fakewin::error = 10054; return SOCKET_ERROR; }
 auto& s = *fakewin::sockets.at(socket);
 if (s.read.operation) throw std::runtime_error("overlapping receive");
 s.read = {buffers->buf, buffers->len, ov}; fakewin::completeRead(s); fakewin::error = WSA_IO_PENDING; return SOCKET_ERROR;
}
inline int WSASend(SOCKET socket, WSABUF* buffers, unsigned, DWORD*, DWORD, OVERLAPPED* ov, void*) {
 std::lock_guard<std::mutex> lock(fakewin::mutex);
 if (fakewin::failSend.exchange(false)) { fakewin::error = 10054; return SOCKET_ERROR; }
 auto& s = *fakewin::sockets.at(socket); unsigned n = std::min(buffers->len, fakewin::sendChunk.load());
 s.wire.insert(s.wire.end(), buffers->buf, buffers->buf + n);
 fakewin::Event event{n, s.key, ov, 0};
 if (fakewin::holdSend.load()) s.delayed.push_back(event); else fakewin::post(s.port, event);
 // Synchronous success still queues an IOCP completion (no skip-success mode).
 return 0;
}
inline int WSAIoctl(SOCKET, unsigned, void*, unsigned, void* out, unsigned, DWORD*, void*, void*) {
 *static_cast<LPFN_ACCEPTEX*>(out) = fakewin::acceptEx; return 0;
}
inline int fw_bind(SOCKET socket, const sockaddr* addr, int size) {
 std::lock_guard<std::mutex> lock(fakewin::mutex); auto& s = *fakewin::sockets.at(socket);
 std::memcpy(&s.local, addr, size); s.localLength = size; return 0;
}
inline int fw_listen(SOCKET, int) { return 0; }
inline int fw_setsockopt(SOCKET socket, int level, int option, const char* value, int length) {
 if(level==SOL_SOCKET && option==SO_LINGER){
  const auto* setting=reinterpret_cast<const linger*>(value);
  if(length!=sizeof(linger)||setting->l_onoff!=1||setting->l_linger!=0)throw std::runtime_error("wrong linger reset");
  std::lock_guard<std::mutex> lock(fakewin::mutex);fakewin::sockets.at(socket)->resetClose=true;
 }
 return 0;
}
inline int fw_getsockname(SOCKET socket, sockaddr* address, int* length) {
 std::lock_guard<std::mutex> lock(fakewin::mutex); auto& s = *fakewin::sockets.at(socket);
 if (*length < s.localLength) return SOCKET_ERROR;
 std::memcpy(address, &s.local, s.localLength); *length = s.localLength; return 0;
}
inline int fw_getpeername(SOCKET, sockaddr* address, int* length) {
 sockaddr_in a{}; a.sin_family = AF_INET; a.sin_port = htons(23456); a.sin_addr.s_addr = htonl(0x7f000001);
 std::memcpy(address, &a, sizeof(a)); *length = sizeof(a); return 0;
}
#define bind fw_bind
#define listen fw_listen
#define setsockopt fw_setsockopt
#define getsockname fw_getsockname
#define getpeername fw_getpeername
