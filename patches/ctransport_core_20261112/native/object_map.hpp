#pragma once
#include <array>
#include <atomic>
#include <cstdint>
#include <map>
#include <memory>
#include <shared_mutex>
#include <mutex>
// Original CObjectMap<CUserSocket,8192,...>: hash to a bucket, RW locking,
// ordered key/object entries and one reference for every registered object.
template<class T,std::size_t Count=8192,unsigned Tag=26> class CObjectMap {
    struct Bucket{mutable std::shared_mutex gate;std::map<std::uint32_t,T*> entries;};
    std::unique_ptr<Bucket[]> buckets_{new Bucket[Count]};
    std::atomic<std::uint64_t> size_{0};
    static std::size_t hash(std::uint32_t key)noexcept{return key&(Count-1);}
public:
    static_assert(Count&&!(Count&(Count-1)),"power-of-two original bucket count");
    bool Insert(std::uint32_t key,T* object)noexcept{
        if(!key||!object)return false;auto& b=buckets_[hash(key)];
        try{std::unique_lock<std::shared_mutex> lock(b.gate);
            if(!b.entries.emplace(key,object).second)return false;
            object->retain();++size_;return true;}catch(...){return false;}
    }
    T* FindObject(std::uint32_t key)const noexcept{
        auto& b=buckets_[hash(key)];std::shared_lock<std::shared_mutex> lock(b.gate);
        auto at=b.entries.find(key);if(at==b.entries.end())return nullptr;
        at->second->retain();return at->second;
    }
    bool ExistKey(std::uint32_t key)const noexcept{
        auto& b=buckets_[hash(key)];std::shared_lock<std::shared_mutex> lock(b.gate);return b.entries.find(key)!=b.entries.end();
    }
    bool DeleteObject(std::uint32_t key,T* expected)noexcept{
        auto& b=buckets_[hash(key)];bool found=false;
        {std::unique_lock<std::shared_mutex> lock(b.gate);auto at=b.entries.find(key);
         if(at!=b.entries.end()&&at->second==expected){b.entries.erase(at);--size_;found=true;}}
        if(found)expected->release();return found;
    }
    std::uint64_t size()const noexcept{return size_.load();}
};
