#pragma once
#include "cio_core.hpp"
#include <array>
#include <cstdio>
#include <chrono>
#include <mutex>
#include <thread>

class Server;
class CIOSocket;
class IServerCallback {
public:
    virtual ~IServerCallback()=default;
    virtual CIOSocket* CreateSocket(Server*,SOCKET) noexcept=0;
};
class CDummyServerCallback final : public IServerCallback {
    using AllocateFunction=CIOSocket*(*)(Server*,SOCKET) noexcept;
    const AllocateFunction allocate_;
public:
    explicit CDummyServerCallback(AllocateFunction allocate):allocate_(allocate){}
    CIOSocket* CreateSocket(Server* server,SOCKET socket)noexcept override{return allocate_(server,socket);}
};
// Original CIOServer owns the listening SOCKET/event and wait/I/O callbacks.
// The derived Server supplies socket allocation/admission and registration.
class CIOServer : public CIOObject {
public:
    SOCKET listening=INVALID_SOCKET; //original+0x18
    HANDLE acceptEvent=nullptr; //original+0x20
    HANDLE port=nullptr;
    bool acceptQueued=false;
    std::atomic<int> pendingAccepts{0};
    std::atomic<std::uint64_t> waitCallbacks{0},acceptCallbacks{0},waitFailures{0},lastWaitError{0};
    std::array<std::atomic<std::uint64_t>,18> callbackCounts{},callbackMillis{},callbackStarted{};
    std::mutex acceptGate;
    std::atomic<bool> accepting{false};
    sockaddr_storage listenAddress{};
    int listenLength=sizeof(listenAddress);
    virtual void NotifyPendingChanged() noexcept=0;
    virtual void OnAcceptedSocket(SOCKET socket) noexcept=0;
    void OnWaitCallback() noexcept;
    void OnIOCallback() noexcept;
    void OnIOCallback(int completionOk,unsigned completionBytes,OVERLAPPED* completionRaw)noexcept override{OnIOCallback();}
    virtual void OnWaitFailure(unsigned error) noexcept=0;
    bool Create(const char* address,unsigned short number,int backlog) noexcept {
        addrinfo hints{};hints.ai_family=AF_INET;hints.ai_socktype=SOCK_STREAM;
        hints.ai_protocol=IPPROTO_TCP;hints.ai_flags=AI_PASSIVE;
        char service[8]{};std::snprintf(service,sizeof(service),"%u",static_cast<unsigned>(number));
        addrinfo* result=nullptr;if(getaddrinfo(address,service,&hints,&result)!=0)return false;
        for(auto* at=result;at;at=at->ai_next){
            listening=WSASocketW(at->ai_family,SOCK_STREAM,IPPROTO_TCP,nullptr,0,WSA_FLAG_OVERLAPPED);
            if(listening==INVALID_SOCKET)continue;
            if(bind(listening,at->ai_addr,static_cast<int>(at->ai_addrlen))==0&&listen(listening,backlog)==0)break;
            closesocket(listening);listening=INVALID_SOCKET;
        }
        freeaddrinfo(result);if(listening==INVALID_SOCKET)return false;
        getsockname(listening,reinterpret_cast<sockaddr*>(&listenAddress),&listenLength);
        acceptEvent=WSACreateEvent();
        if(!acceptEvent||WSAEventSelect(listening,acceptEvent,FD_ACCEPT)!=0){Close();return false;}
        return true;
    }
    // Caller unregisters from WaitThread before closing a wait handle.
    void Close() noexcept {
        std::lock_guard<std::mutex> lock(acceptGate);accepting=false;
        if(listening!=INVALID_SOCKET){closesocket(listening);listening=INVALID_SOCKET;}
        if(acceptEvent){WSACloseEvent(acceptEvent);acceptEvent=nullptr;}
    }
};
// Original RegisterWait/WaitThread: a bounded handle/object table. The mutex
// spans the OS wait, so UnregisterWait cannot close a handle being waited on.
// The selected object is retained before unlocking and invoking its callback.
class WaitThread {
    struct Entry{HANDLE handle=nullptr;CIOServer* object=nullptr;};
    std::array<Entry,64> entries_{};
    unsigned count_=0;
    std::atomic<unsigned> writers_{0};
    struct Mutation {
        std::atomic<unsigned>& count;
        explicit Mutation(std::atomic<unsigned>& value):count(value){count.fetch_add(1,std::memory_order_acq_rel);}
        ~Mutation(){count.fetch_sub(1,std::memory_order_release);}
    };
    std::mutex gate_;
protected:
    std::atomic<bool> stopRequested_{false};
    virtual void Tick() noexcept {}
public:
    virtual ~WaitThread(){Clear();}
    bool RegisterWait(CIOServer* object,HANDLE handle) noexcept {
        if(!object||!handle)return false;Mutation writer(writers_);std::lock_guard<std::mutex> lock(gate_);
        if(count_==entries_.size())return false;
        for(unsigned n=0;n<count_;++n)if(entries_[n].handle==handle||entries_[n].object==object)return false;
        object->retain();entries_[count_++]={handle,object};return true;
    }
    bool UnregisterWait(CIOServer* object) noexcept {
        Mutation writer(writers_);bool found=false;
        {std::lock_guard<std::mutex> lock(gate_);for(unsigned n=0;n<count_;++n)if(entries_[n].object==object){entries_[n]=entries_[--count_];entries_[count_]={};found=true;break;}}
        if(found)object->release();return found;
    }
    void Clear() noexcept {
        std::array<Entry,64> old{};unsigned count;
        {std::lock_guard<std::mutex> lock(gate_);count=count_;old=entries_;entries_={};count_=0;}
        for(unsigned n=0;n<count;++n)old[n].object->release();
    }
    void RequestStop() noexcept{stopRequested_=true;}
    void Run() noexcept {
        while(!stopRequested_.load()){
            // A waiter must not repeatedly reacquire the registry mutex while
            // UnregisterWait is waiting to remove/close an OS event handle.
            if(writers_.load(std::memory_order_acquire)){std::this_thread::yield();continue;}
            Tick();CIOServer* selected=nullptr;unsigned error=0;bool idle=false;
            {
                std::lock_guard<std::mutex> lock(gate_);
                if(!count_)idle=true;
                else {
                    std::array<HANDLE,64> handles{};for(unsigned n=0;n<count_;++n)handles[n]=entries_[n].handle;
                    const DWORD index=WaitForMultipleObjects(count_,handles.data(),FALSE,50);
                    if(index<count_){selected=entries_[index].object;selected->retain();}
                    else if(index!=WAIT_TIMEOUT){error=GetLastError();if(!error)error=31;selected=entries_[0].object;selected->retain();}
                }
            }
            if(selected){ObjectRef keep(selected,false);if(error)selected->OnWaitFailure(error);else selected->OnWaitCallback();}
            if(idle)std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }
};
class ListenThread final : public WaitThread {
    Server* const server_;
    std::thread thread_;
protected:void Tick() noexcept override;
public:
    explicit ListenThread(Server* server):server_(server){}
    ~ListenThread() override {Stop();}
    bool Start() noexcept;
    void Stop() noexcept{RequestStop();if(thread_.joinable())thread_.join();Clear();}
};
// L1 worker host; the original CIOThreadCallback performs World_Sync/GC.
// This adapter is deliberately not renamed to that unrelated callback type.
class IOThreadCallback {
    Server* const server_;const unsigned owner_;std::thread thread_;
public:
    IOThreadCallback(Server* server,unsigned owner):server_(server),owner_(owner){}
    ~IOThreadCallback(){Join();}
    void Start();
    void Join(){if(thread_.joinable())thread_.join();}
    void IOThread_common() noexcept;
};
