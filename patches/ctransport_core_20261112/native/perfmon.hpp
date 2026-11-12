#pragma once
#include <array>
#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <thread>
namespace ctransport {
class Server;
// Original thread15 is CPerfMon, separate from I/O workers and ListenThread17.
// L1 samples process/system memory, process CPU and transport populations.
class CPerfMon {
    Server* const server_;std::thread thread_;
    std::atomic<bool> stopping_{false};
    std::mutex gate_;std::condition_variable changed_;
    std::array<std::uint64_t,16> latest_{};
    std::uint64_t previousCpu_=0,previousTime_=0;
    void Sample()noexcept;
public:
    explicit CPerfMon(Server* server):server_(server){}
    ~CPerfMon(){Stop();}
    bool Start(unsigned interval)noexcept;
    void Stop()noexcept{stopping_=true;changed_.notify_all();if(thread_.joinable())thread_.join();}
    void Read(std::uint64_t* out)noexcept{std::lock_guard<std::mutex> lock(gate_);std::copy(latest_.begin(),latest_.end(),out);}
};
}
