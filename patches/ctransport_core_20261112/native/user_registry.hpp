#pragma once
#include <cstdint>
#include <map>
#include <mutex>
#include <vector>

namespace ctransport {
// L1 adaptation of the original ordered registry's lookup/retain/erase contract.
// A network handle carries slot+generation. World/account ownership stays Java.
template<class T> class CUserSocketManager {
    mutable std::mutex gate_;
    std::map<std::uint64_t,T*> active_, retiring_;
    std::vector<std::uint32_t> generations_, free_;
    unsigned capacity_=0;
    std::uint64_t added_=0, removed_=0, stale_=0;
public:
    bool initialize(unsigned capacity) noexcept {
        std::lock_guard<std::mutex> lock(gate_);
        if (!active_.empty() || !retiring_.empty() || !capacity) return false;
        try {
            if(generations_.size()<capacity)generations_.resize(capacity,0);
            free_.clear(); free_.reserve(generations_.size());
            capacity_=capacity;
            for(unsigned n=capacity;n>0;--n)free_.push_back(n-1);
            return true;
        } catch (...) { return false; }
    }
    std::uint64_t insert(T* object) noexcept {
        std::lock_guard<std::mutex> lock(gate_);
        while(!free_.empty()) {
            const auto slot=free_.back();
            if (generations_[slot]==UINT32_MAX) { free_.pop_back(); continue; }
            const auto generation=generations_[slot]+1;
            const std::uint64_t id=(std::uint64_t(generation)<<32)|(slot+1);
            try {
                object->id=id;
                if(!active_.emplace(id,object).second){object->id=0;return 0;}
                generations_[slot]=generation;free_.pop_back();object->retain();++added_;return id;
            } catch (...) { object->id=0;return 0; }
        }
        return 0;
    }
    T* find(std::uint64_t id,bool includeRetiring=false) noexcept {
        std::lock_guard<std::mutex> lock(gate_);
        auto at=active_.find(id);
        if(at!=active_.end()){at->second->retain();return at->second;}
        if(includeRetiring){auto old=retiring_.find(id);if(old!=retiring_.end()){old->second->retain();return old->second;}}
        ++stale_;return nullptr;
    }
    void retire(std::uint64_t id,T* expected) noexcept {
        std::lock_guard<std::mutex> lock(gate_);
        auto at=active_.find(id);
        if(at!=active_.end()&&at->second==expected)retiring_.insert(active_.extract(at));
    }
    bool remove(std::uint64_t id,T* expected) noexcept {
        bool found=false;
        {
            std::lock_guard<std::mutex> lock(gate_);
            for(auto* table:{&active_,&retiring_}) {
                auto at=table->find(id);
                if(at==table->end()||at->second!=expected)continue;
                table->erase(at);free_.push_back(static_cast<std::uint32_t>(id)-1);
                ++removed_;found=true;break;
            }
        }
        if(found)expected->release();return found;
    }
    bool snapshot(std::vector<T*>& out) noexcept {
        std::lock_guard<std::mutex> lock(gate_);
        try {
            out.reserve(active_.size()+retiring_.size());
            for(auto* table:{&active_,&retiring_})for(auto& entry:*table){out.push_back(entry.second);entry.second->retain();}
            return true;
        }catch(...){for(auto* p:out)p->release();out.clear();return false;}
    }
    void diagnostics(std::uint64_t* out) const noexcept {
        std::lock_guard<std::mutex> lock(gate_);
        out[0]=active_.size();out[1]=retiring_.size();out[2]=capacity_;
        out[3]=added_;out[4]=removed_;out[5]=stale_;
    }
};
}
