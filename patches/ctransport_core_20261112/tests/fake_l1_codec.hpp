#pragma once
#include <atomic>
#include <vector>
#include <cstring>
// Test-only codec. Real L1 rolling-key compatibility is tested against the
// unchanged shipped DLL on Windows; these functions exercise native ownership.
namespace fixturecodec {
inline std::atomic<bool> failEncrypt{false};
inline std::atomic<unsigned> created{0},destroyed{0},feeds{0},nexts{0},encrypted{0};
struct State {std::vector<unsigned char> wire;std::size_t offset=0;};
inline unsigned version(){return 0x10000;}
inline void* create(unsigned){++created;return new State;}
inline void destroy(void* p){++destroyed;delete static_cast<State*>(p);}
inline int feed(void* p,const void* bytes,int count){
    auto& state=*static_cast<State*>(p);++feeds;
    if(count<0||(!bytes&&count))return -6;
    try{const auto* b=static_cast<const unsigned char*>(bytes);state.wire.insert(state.wire.end(),b,b+count);return count;}catch(...){return -6;}
}
inline int next(void* p,void* out,int capacity){
    auto& s=*static_cast<State*>(p);++nexts;
    if(s.wire.size()-s.offset<2)return -2;
    const auto* frame=s.wire.data()+s.offset;unsigned n=frame[0]|(unsigned(frame[1])<<8);
    if(n<2||(n>2&&n<10))return -4;
    if(s.wire.size()-s.offset<n)return -2;
    int result=-3;
    if(n>2){if(capacity<static_cast<int>(n-6))return -5;std::memcpy(out,frame+6,n-6);result=static_cast<int>(n-6);}
    s.offset+=n;if(s.offset==s.wire.size()){s.wire.clear();s.offset=0;}return result;
}
inline int encrypt(void*,void* out,int count){++encrypted;if(failEncrypt.exchange(false))return -1;auto* b=static_cast<unsigned char*>(out);for(int n=0;n<count;++n)b[n]^=0xa7;return 0;}
inline int pending(void* p){auto& s=*static_cast<State*>(p);return static_cast<int>(s.wire.size()-s.offset);}
inline std::array<std::uintptr_t,7> functions(){return {reinterpret_cast<std::uintptr_t>(&version),reinterpret_cast<std::uintptr_t>(&create),reinterpret_cast<std::uintptr_t>(&destroy),reinterpret_cast<std::uintptr_t>(&feed),reinterpret_cast<std::uintptr_t>(&next),reinterpret_cast<std::uintptr_t>(&encrypt),reinterpret_cast<std::uintptr_t>(&pending)};}
}
