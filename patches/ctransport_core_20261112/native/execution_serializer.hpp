#pragma once
#include "cio_core.hpp"
#include <array>
#include <atomic>
#include <cstdint>
namespace ctransport {
// CExecutionSerializerFast::AddExecutable: owner-thread reentry, ticket ring,
// function publication and a single drain owner. The eight spare slots match
// the original overflow margin; admission failure is reported to the socket.
class CExecutionSerializerFast {
    using Function=void(*)(CIOObject*,void*) noexcept;
    struct Entry{CIOObject* object=nullptr;void* argument=nullptr;std::atomic<Function> function{nullptr};std::atomic<std::uint64_t> available{0};};
    static constexpr unsigned Slots=512,Limit=Slots-8;
    std::array<Entry,Slots> entries_{};
    std::atomic<std::uint64_t> tail_{0};
    std::uint64_t head_=0;
    std::atomic<unsigned> pending_{0};
    std::atomic<std::uint64_t> owner_{0};
    inline static std::atomic<std::uint64_t> nextThread_{1};
    inline static thread_local std::uint64_t thread_=nextThread_.fetch_add(1);
public:
    CExecutionSerializerFast()noexcept{
        for(unsigned i=0;i<Slots;++i)entries_[i].available.store(i,std::memory_order_relaxed);
    }
    bool AddExecutable(CIOObject* object,Function function,void* argument) noexcept {
        if(!object||!function)return false;
        if(owner_.load(std::memory_order_acquire)==thread_){function(object,argument);return true;}
        auto count=pending_.load(std::memory_order_acquire);
        do{if(count>=Limit)return false;}while(!pending_.compare_exchange_weak(count,count+1,std::memory_order_acq_rel));
        object->retain();
        const auto ticket=tail_.fetch_add(1,std::memory_order_relaxed);auto& entry=entries_[ticket%Slots];
        // pending reservation can precede ticket allocation by many ring turns.
        // Acquire this exact slot generation, not just a global queue count.
        while(entry.available.load(std::memory_order_acquire)!=ticket)std::this_thread::yield();
        entry.object=object;entry.argument=argument;entry.function.store(function,std::memory_order_release);
        if(count)return true;
        owner_.store(thread_,std::memory_order_release);
        for(;;){
            const auto current=head_++;auto& next=entries_[current%Slots];Function execute;
            while(!(execute=next.function.load(std::memory_order_acquire)))std::this_thread::yield();
            auto* target=next.object;void* data=next.argument;
            execute(target,data);next.object=nullptr;next.argument=nullptr;
            next.function.store(nullptr,std::memory_order_relaxed);
            next.available.store(current+Slots,std::memory_order_release);target->release();
            // Clear before the count handoff. A concurrent producer either
            // keeps this owner draining or becomes the next unique owner.
            owner_.store(0,std::memory_order_release);
            if(pending_.fetch_sub(1,std::memory_order_acq_rel)==1)return true;
            owner_.store(thread_,std::memory_order_release);
        }
    }
    unsigned Pending()const noexcept{return pending_.load(std::memory_order_acquire);}
};
}
