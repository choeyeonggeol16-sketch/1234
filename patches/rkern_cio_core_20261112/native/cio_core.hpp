#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <new>
#include <utility>

// Behavioural reconstruction from the supplied x86/x64 server executables.
// This is new source, not recovered NC source or a binary code transplant.
namespace rkern_cio {
constexpr std::size_t BufferBytes = 0x2000;
constexpr std::size_t MaxQueuedBytes = 4u * 1024u * 1024u;

class CIOObject {
    std::atomic<unsigned> references_{1};
protected:
    virtual ~CIOObject() = default;
public:
    void retain() noexcept { references_.fetch_add(1, std::memory_order_relaxed); }
    void release() noexcept {
        if (references_.fetch_sub(1, std::memory_order_acq_rel) == 1) delete this;
    }
    virtual void completed(void* operation, bool success, unsigned bytes, unsigned error) noexcept = 0;
};

class ObjectRef {
    CIOObject* p_;
public:
    explicit ObjectRef(CIOObject* p, bool add = true) noexcept : p_(p) { if (p_ && add) p_->retain(); }
    ~ObjectRef() { if (p_) p_->release(); }
    ObjectRef(const ObjectRef&) = delete;
    ObjectRef& operator=(const ObjectRef&) = delete;
};

struct CIOBuffer {
    std::array<unsigned char, BufferBytes> data{};
    std::size_t used = 0, offset = 0;
    CIOBuffer* next = nullptr;
};

// The queue owns each block exclusively until the matching completion. An
// active head is immutable; only a queued, inactive tail may be coalesced.
class CIOBufferPool {
    std::mutex mutex_;
    CIOBuffer* free_ = nullptr;
    std::size_t allocated_ = 0;
    const std::size_t limit_;
public:
    explicit CIOBufferPool(std::size_t limit) : limit_(limit) {}
    ~CIOBufferPool() {
        while (free_) { auto* p = free_; free_ = p->next; delete p; }
    }
    CIOBuffer* acquire() noexcept {
        std::lock_guard<std::mutex> lock(mutex_);
        CIOBuffer* p;
        if (free_) { p = free_; free_ = p->next; }
        else {
            if (allocated_ == limit_) return nullptr;
            p = new(std::nothrow) CIOBuffer;
            if (!p) return nullptr;
            ++allocated_;
        }
        p->used = p->offset = 0; p->next = nullptr;
        return p;
    }
    void recycle(CIOBuffer* p) noexcept {
        if (!p) return;
        std::lock_guard<std::mutex> lock(mutex_);
        p->next = free_; free_ = p;
    }
};

struct Span { const unsigned char* data; std::size_t size; };

class SendChain {
    CIOBuffer* head_ = nullptr;
    CIOBuffer* tail_ = nullptr;
    std::size_t bytes_ = 0;
public:
    SendChain() = default;
    SendChain(const SendChain&) = delete;
    SendChain& operator=(const SendChain&) = delete;
    CIOBuffer* head() const noexcept { return head_; }
    std::size_t size() const noexcept { return bytes_; }
    bool append(CIOBufferPool& pool, const Span* spans, std::size_t count) noexcept {
        std::size_t total = 0;
        for (std::size_t i = 0; i < count; ++i) {
            if ((!spans[i].data && spans[i].size) || spans[i].size > MaxQueuedBytes - total) return false;
            total += spans[i].size;
        }
        if (total > MaxQueuedBytes - bytes_) return false;
        if (!total) return true;
        // Prepare a complete owned chain before changing the live queue. A
        // failed allocation must not admit half of a multi-WSABUF request.
        CIOBuffer* first = nullptr; CIOBuffer* last = nullptr;
        for (std::size_t i = 0; i < count; ++i) {
            std::size_t at = 0;
            while (at < spans[i].size) {
                if (!last || last->used == BufferBytes) {
                    auto* p = pool.acquire();
                    if (!p) {
                        while (first) { auto* next = first->next; pool.recycle(first); first = next; }
                        return false;
                    }
                    if (last) last->next = p; else first = p;
                    last = p;
                }
                const auto n = std::min(BufferBytes - last->used, spans[i].size - at);
                std::memcpy(last->data.data() + last->used, spans[i].data + at, n);
                last->used += n; at += n;
            }
        }
        if (tail_ && tail_ != head_ && first->used <= BufferBytes - tail_->used) {
            std::memcpy(tail_->data.data() + tail_->used, first->data.data(), first->used);
            tail_->used += first->used;
            auto* merged = first; first = first->next;
            if (last == merged) last = tail_;
            pool.recycle(merged);
        }
        if (first) { if (tail_) tail_->next = first; else head_ = first; tail_ = last; }
        bytes_ += total;
        return true;
    }
    // Return a detached completed block. Caller keeps it alive through OnSend.
    bool complete(std::size_t bytes, CIOBuffer*& retired) noexcept {
        retired = nullptr;
        if (!head_ || bytes == 0 || bytes > head_->used - head_->offset) return false;
        bytes_ -= bytes; head_->offset += bytes;
        if (head_->offset == head_->used) {
            retired = head_; head_ = head_->next; retired->next = nullptr;
            if (!head_) tail_ = nullptr;
        }
        return true;
    }
    void clear(CIOBufferPool& pool) noexcept {
        while (head_) { auto* next = head_->next; pool.recycle(head_); head_ = next; }
        tail_ = nullptr; bytes_ = 0;
    }
};

// Protected by the socket gate. No callback runs while that gate is held.
struct IoState {
    bool closing = false, readPosted = false, writePosted = false;
    bool readDelivering = false, writeDelivering = false, acceptDelivering = true;
    bool closeQueued = false, paused = true;
    bool canRead() const noexcept { return !closing && !paused && !readPosted && !readDelivering && !acceptDelivering; }
    bool canWrite() const noexcept { return !closing && !writePosted && !writeDelivering && !acceptDelivering; }
    bool canClose() const noexcept {
        return closing && !readPosted && !writePosted && !readDelivering && !writeDelivering && !acceptDelivering && !closeQueued;
    }
};
} // namespace rkern_cio
