#pragma once
#include <cstdint>
#include <map>
#include <mutex>
// Native CSocketLimiter keeps transport admission counts, separate from L1
// account/world ownership. MAC admission requires an actual trusted identity.
class CSocketLimiter {
    std::mutex gate_;
    std::map<std::uint32_t,unsigned> ip_;
    std::map<std::uint64_t,unsigned> mac_;
    unsigned ipLimit_=0,macLimit_=0;
    template<class K>static bool Inc(std::map<K,unsigned>& map,K key,unsigned limit)noexcept{
        if(!limit)return true;
        try{auto at=map.find(key);if(at!=map.end()){if(at->second>=limit)return false;++at->second;}
            else map.emplace(key,1);return true;}catch(...){return false;}
    }
    template<class K>static void Dec(std::map<K,unsigned>& map,K key)noexcept{
        auto at=map.find(key);if(at==map.end())return;if(!--at->second)map.erase(at);
    }
public:
    bool Configure(unsigned ip,unsigned mac)noexcept{
        std::lock_guard<std::mutex> lock(gate_);if(!ip_.empty()||!mac_.empty())return false;ipLimit_=ip;macLimit_=mac;return true;
    }
    bool IncUserSocketPerIP(std::uint32_t key)noexcept{std::lock_guard<std::mutex> lock(gate_);return Inc(ip_,key,ipLimit_);}
    void DecUserSocketPerIP(std::uint32_t key)noexcept{std::lock_guard<std::mutex> lock(gate_);Dec(ip_,key);}
    bool IncUserSocketPerMAC(std::uint64_t key)noexcept{std::lock_guard<std::mutex> lock(gate_);return key&&Inc(mac_,key,macLimit_);}
    void DecUserSocketPerMAC(std::uint64_t key)noexcept{std::lock_guard<std::mutex> lock(gate_);Dec(mac_,key);}
    bool RequiresMacIdentity()noexcept{std::lock_guard<std::mutex> lock(gate_);return macLimit_!=0;}
    void Diagnostics(std::uint64_t* out)noexcept{std::lock_guard<std::mutex> lock(gate_);out[0]=ip_.size();out[1]=mac_.size();out[2]=ipLimit_;out[3]=macLimit_;}
};
