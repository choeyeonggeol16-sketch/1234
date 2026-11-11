#pragma once
#include <cstddef>
#include <cstdint>
namespace ats {
constexpr uint32_t kMaxSeconds=31536000;
struct Status { uint32_t seconds=0; bool active=false; };
bool parse_status(const char* line,size_t length,Status& out) noexcept;
class State {
public:
    void update(Status status,uint64_t now) noexcept {status_=status;received_=now;valid_=true;}
    void hide() noexcept {status_.active=false;valid_=false;}
    bool visible() const noexcept {return valid_ && status_.active;}
    bool stale(uint64_t now) const noexcept {return valid_ && now>=received_ && now-received_>5000;}
    uint32_t remaining(uint64_t now) const noexcept {
        const uint64_t elapsed=now>=received_ && status_.active ? (now-received_)/1000:0;
        return elapsed>=status_.seconds ? 0:status_.seconds-static_cast<uint32_t>(elapsed);
    }
private:
    Status status_{}; uint64_t received_=0; bool valid_=false;
};
}
