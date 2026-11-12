#pragma once
#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>

namespace ctransport {
// CUserSocket 0xa0372c: header(0), body(1), stopped(2), then opcode dispatch.
// L1 adaptation: unsigned LE16 total size, legal empty frame(2), otherwise
// four envelope bytes + >=4 encrypted plaintext bytes (wire >=10). L2's
// 0x4000 ceiling, cipher, opcode IDs and handler-return convention do not apply.
class CUserSocket {
public:
    enum State : unsigned { Header = 0, Body = 1, Stopped = 2 };
private:
    mutable std::mutex gate_;
    std::array<unsigned char, 2> header_{};
    std::vector<unsigned char> frame_;
    State state_ = Header;
    unsigned headerBytes_ = 0, expected_ = 0, filled_ = 0;
    std::uint64_t frames_ = 0, invalid_ = 0, partialSince_ = 0, pausedAt_ = 0;
    bool partial_ = false, paused_ = false;
    void beginPartial(std::uint64_t now) noexcept {
        if (partial_) return;
        partial_ = true; partialSince_ = now;
        if (paused_) pausedAt_ = now;
    }
public:
    // One READ-delivery owner calls feed. Other threads can only stop, pause,
    // or inspect it. Never invoke Java/game callbacks while holding gate_.
    template<class Emit> bool feed(const unsigned char* bytes, std::size_t length,
                                  std::uint64_t now, Emit emit) noexcept {
        std::size_t at = 0;
        while (at < length) {
            unsigned ready = 0;
            const unsigned char* readyBytes = nullptr;
            {
                std::lock_guard<std::mutex> hold(gate_);
                if (state_ == Stopped) return false;
                // Complete frames borrow the current receive buffer, whose I/O
                // reference spans the synchronous upcall. Only fragmented
                // frames need the bounded assembly storage below.
                if (state_ == Header && !headerBytes_ && length - at >= 2) {
                    const unsigned size = bytes[at] | (static_cast<unsigned>(bytes[at + 1]) << 8);
                    if (size < 2 || (size > 2 && size < 10)) {
                        ++invalid_; state_ = Stopped; return false;
                    }
                    if (length - at >= size) {
                        ready = expected_ = filled_ = size; state_ = Body;
                        readyBytes = bytes + at; at += size;
                        ++frames_; partial_ = false; partialSince_ = pausedAt_ = 0;
                    }
                }
                if (!ready) {
                    beginPartial(now);
                    if (state_ == Header) {
                        while (at < length && headerBytes_ < 2) header_[headerBytes_++] = bytes[at++];
                        if (headerBytes_ < 2) break;
                        expected_ = static_cast<unsigned>(header_[0]) | (static_cast<unsigned>(header_[1]) << 8);
                        if (expected_ < 2 || (expected_ > 2 && expected_ < 10)) {
                            ++invalid_; state_ = Stopped; return false;
                        }
                        try { frame_.resize(expected_); }
                        catch (...) { state_ = Stopped; return false; }
                        std::memcpy(frame_.data(), header_.data(), 2);
                        filled_ = 2; state_ = Body;
                    }
                    const auto count = std::min<std::size_t>(expected_ - filled_, length - at);
                    if (count) std::memcpy(frame_.data() + filled_, bytes + at, count);
                    at += count; filled_ += static_cast<unsigned>(count);
                    if (filled_ != expected_) break;
                    ready = expected_;
                    readyBytes = frame_.data();
                    ++frames_; partial_ = false; partialSince_ = pausedAt_ = 0;
                }
            }
            // The complete wire frame remains valid for this synchronous upcall.
            // Closing marks the parser stopped but cannot clear its storage.
            if (!emit(readyBytes, ready)) { stop(); return false; }
            {
                std::lock_guard<std::mutex> hold(gate_);
                if (state_ == Stopped) return false;
                state_ = Header; headerBytes_ = expected_ = filled_ = 0;
            }
        }
        return true;
    }
    void stop() noexcept { std::lock_guard<std::mutex> hold(gate_); state_ = Stopped; }
    void pause(bool pause, std::uint64_t now) noexcept {
        std::lock_guard<std::mutex> hold(gate_);
        if (paused_ == pause) return;
        if (partial_) {
            if (pause) pausedAt_ = now;
            else { partialSince_ += now - pausedAt_; pausedAt_ = 0; }
        }
        paused_ = pause;
    }
    bool expired(std::uint64_t now) const noexcept {
        std::lock_guard<std::mutex> hold(gate_);
        return state_ != Stopped && partial_ && !paused_ && now - partialSince_ >= 30000;
    }
    void snapshot(std::uint64_t* out, std::uint64_t now) const noexcept {
        std::lock_guard<std::mutex> hold(gate_);
        out[0] = state_; out[1] = expected_; out[2] = state_ == Header ? headerBytes_ : filled_;
        out[3] = frames_; out[4] = invalid_;
        out[5] = partial_ ? (paused_ ? pausedAt_ : now) - partialSince_ : 0;
    }
};
}
