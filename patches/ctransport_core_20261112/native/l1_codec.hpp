#pragma once
#include <algorithm>
#include <array>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

namespace ctransport {
// The JVM verifies and loads the unchanged L1 DLL once, then supplies its native
// entry addresses. Feed/Next/Encrypt execute directly in C++; no wire-data JNI/FFM
// round trip and no reimplementation of the L1 rolling-key algorithm.
struct L1CodecApi {
    unsigned (*version)()=nullptr;
    void* (*create)(unsigned)=nullptr;
    void (*destroy)(void*)=nullptr;
    int (*feed)(void*,const void*,int)=nullptr;
    int (*next)(void*,void*,int)=nullptr;
    int (*encrypt)(void*,void*,int)=nullptr;
    int (*pending)(void*)=nullptr;
    bool assign(const std::uintptr_t* entries,int count) noexcept {
        if(!entries||count!=7)return false;
        for(int n=0;n<7;++n)if(!entries[n])return false;
        version=reinterpret_cast<decltype(version)>(entries[0]);
        create=reinterpret_cast<decltype(create)>(entries[1]);destroy=reinterpret_cast<decltype(destroy)>(entries[2]);
        feed=reinterpret_cast<decltype(feed)>(entries[3]);next=reinterpret_cast<decltype(next)>(entries[4]);
        encrypt=reinterpret_cast<decltype(encrypt)>(entries[5]);pending=reinterpret_cast<decltype(pending)>(entries[6]);
        return version()==0x10000;
    }
};
struct PlainPacket { const unsigned char* data=nullptr;int length=0;std::uint64_t sequence=0; };
class L1CodecState {
    const std::shared_ptr<const L1CodecApi> api_;
    void* handle_=nullptr;
    mutable std::mutex gate_;
    std::vector<unsigned char> plaintext_;
    bool borrowed_=false;
    std::uint64_t sequence_=0;
public:
    L1CodecState(std::shared_ptr<const L1CodecApi> api,unsigned seed):api_(std::move(api)),plaintext_(4096) {
        handle_=api_->create(seed);if(!handle_)throw std::bad_alloc();
    }
    ~L1CodecState(){api_->destroy(handle_);}
    bool feed(const void* bytes,unsigned length) noexcept {
        std::lock_guard<std::mutex> lock(gate_);return api_->feed(handle_,bytes,static_cast<int>(length))==static_cast<int>(length);
    }
    // -2 incomplete/leased, -3 empty, positive plaintext; other values fatal.
    int next(PlainPacket& packet) noexcept {
        std::lock_guard<std::mutex> lock(gate_);
        if(borrowed_)return -2;
        for(;;){
            const int n=api_->next(handle_,plaintext_.data(),static_cast<int>(plaintext_.size()));
            if(n==-5&&plaintext_.size()<65535){
                try{plaintext_.resize(std::min<std::size_t>(65535,plaintext_.size()*2));}catch(...){return -6;}
                continue;
            }
            if(n>0){
                if(static_cast<std::size_t>(n)>plaintext_.size()||sequence_==UINT64_MAX)return -6;
                borrowed_=true;packet={plaintext_.data(),n,++sequence_};
            }
            return n;
        }
    }
    bool release(std::uint64_t sequence) noexcept {
        std::lock_guard<std::mutex> lock(gate_);
        if(!borrowed_||sequence!=sequence_)return false;
        borrowed_=false;return true;
    }
    bool borrowed() const noexcept {std::lock_guard<std::mutex> lock(gate_);return borrowed_;}
    bool encrypt(void* bytes,int length) noexcept {std::lock_guard<std::mutex> lock(gate_);return api_->encrypt(handle_,bytes,length)==0;}
    int pending() const noexcept {std::lock_guard<std::mutex> lock(gate_);return api_->pending(handle_);}
};
}
