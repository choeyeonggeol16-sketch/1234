#include "step_core.h"
#ifdef NDEBUG
#undef NDEBUG
#endif
#include <cassert>
#include <cstdio>
#include <cstring>
using namespace nativestep;
Action drive(StepEngine& e, uint64_t now, bool manual) {
    const Action result=e.tick(now, manual);
    if (result.type==Action::move_and_press) assert(e.confirm_press(now));
    return result;
}
int main() {
    // Explicit expected screen sectors. These geometry tests are not live
    // validation of all Mega.bin headings. User evidence: old due-left h7
    // generated server h6, so h7 must no longer default to due left.
    constexpr int clientW = 507, clientH = 380;
    const Point expected[8] = {{253,113},{289,136},{325,159},{289,182},
                               {253,205},{217,182},{181,159},{217,136}};
    for (int h = 0; h < 8; ++h) {
        char cmd[] = "@@NATIVESTEP:0@@";
        cmd[13] = char('0' + h);
        assert(parse_control(cmd, sizeof(cmd)-1) == h);
        const Point p = direction_point(h, clientW, clientH);
        assert(p.x==expected[h].x && p.y==expected[h].y);
    }
    const auto legacy=direction_point(7,clientW,clientH,{253,159},36,23,
                                      DirectionLayout::world_isometric);
    assert(legacy.x==181 && legacy.y==159);
    assert(direction_point(7,clientW,clientH).y < legacy.y);
    assert(direction_point(-1,clientW,clientH).x < 0);
    assert(parse_control("@@NATIVESTEP:CANCEL@@", 21) == -1);
    assert(parse_control("@@NATIVESTEP:START@@", 20) == -2);
    assert(parse_control("@@NATIVESTEP:STOP@@", 19) == -4);
    StepEngine e;
    e.submit(2, 999);
    assert(drive(e, 1000, false).type == Action::none);
    e.submit(-2, 1000);
    assert(e.is_enabled());
    e.submit(2, 1000);
    assert(drive(e, 1000, false).type == Action::move_and_press);
    // Preserve F8's held input long enough for the client to generate its
    // first C_MoveChar. But never repeat a press for a refreshed offer.
    e.submit(2, 1025);
    assert(drive(e, 1025, false).type == Action::none);
    assert(drive(e, 1299, false).type == Action::none);
    assert(drive(e, 1300, false).type == Action::release);
    for (unsigned n = 0; n < 10; ++n) {
        e.submit(2, 1400 + n*25);
        assert(drive(e, 1400 + n*25, false).type == Action::none);
    }
    // Server C_MoveChar accepted: ordered CANCEL permits another step.
    e.submit(-1, 1900);
    e.submit(3, 1901);
    assert(drive(e, 1901, false).type == Action::move_and_press);
    e.submit(-1, 1902);
    assert(drive(e, 1902, false).type == Action::release);
    e.submit(4, 1920);
    assert(drive(e, 1920, false).type == Action::move_and_press);
    assert(drive(e, 1921, true).type == Action::release);
    assert(e.has_manual_override());
    e.submit(4, 1950);
    assert(drive(e, 1950, false).type == Action::none);
    e.submit(-4, 1960);
    assert(!e.is_enabled());
    e.submit(3, 1970);
    assert(drive(e, 1970, false).type == Action::none);
    // Item click may still be down when START is received.
    // Regression: server CANCEL can arrive while mouse press is held.
    // Before this fix, the timed-release branch latched pulse_complete_ and
    // silently ignored all subsequent directions (move offered, no C_MoveChar).
    StepEngine r;
    r.submit(-2, 3000);
    r.submit(2, 3000);
    assert(drive(r, 3000, false).type == Action::move_and_press);
    r.submit(-1, 3015); // CANCEL before the scheduled 75ms release
    assert(drive(r, 3310, false).type == Action::release);
    r.submit(3, 3320);
    assert(drive(r, 3320, false).type == Action::move_and_press);
    assert(drive(r, 3621, false).type == Action::release);
    // The other release branch (CANCEL immediately ticked) must rearm too.
    StepEngine t;
    t.submit(-2, 4000);
    t.submit(2, 4000);
    assert(drive(t, 4000, false).type == Action::move_and_press);
    t.submit(-1, 4005);
    assert(drive(t, 4006, false).type == Action::release);
    t.submit(7, 4100);
    assert(drive(t, 4100, false).type == Action::move_and_press);

    StepEngine f;
    f.submit(-2, 2000);
    assert(drive(f, 2000, true).type == Action::none);
    f.submit(1, 2001);
    assert(drive(f, 2002, true).type == Action::none);
    assert(drive(f, 2003, false).type == Action::move_and_press);
    assert(drive(f, 2303, false).type == Action::release);
    f.shutdown();
    assert(!f.is_enabled());
    // 10,000 adversarial offer/CANCEL cycles: one click per offer and
    // no movement after STOP, including while a press is still held.
    for (int n = 0; n < 10000; ++n) {
        StepEngine stress;
        const uint64_t start = static_cast<uint64_t>(n) * 1000;
        stress.submit(-2, start);
        assert(drive(stress, start, false).type == Action::none);
        stress.submit(n & 7, start + 1);
        assert(drive(stress, start + 1, false).type == Action::move_and_press);
        stress.submit((n+1) & 7, start + 2);
        assert(drive(stress, start + 2, false).type == Action::none);
        stress.submit(-1, start + 3);
        assert(drive(stress, start + 3, false).type == Action::release);
        stress.submit((n+1) & 7, start + 4);
        assert(drive(stress, start + 4, false).type == Action::move_and_press);
        stress.submit(-4, start + 5);
        assert(drive(stress, start + 5, false).type == Action::release);
        stress.submit((n+2) & 7, start + 6);
        assert(drive(stress, start + 6, false).type == Action::none);
    }
    // Cursor staging may be delayed by a busy UI. It must not consume the
    // physical hold budget or allow a cancelled/expired reservation to click.
    StepEngine staged;
    staged.submit(-2, 10000);
    staged.submit(2, 10000);
    assert(staged.tick(10000, false).type == Action::move_and_press);
    assert(staged.tick(10310, false).type == Action::none);
    assert(staged.confirm_press(10310));
    assert(!staged.confirm_press(10311));
    assert(staged.tick(10609, false).type == Action::none);
    assert(staged.tick(10610, false).type == Action::release);
    StepEngine expired;
    expired.submit(-2, 20000);
    expired.submit(2, 20000);
    assert(expired.tick(20000, false).type == Action::move_and_press);
    assert(!expired.confirm_press(20450));
    assert(expired.tick(20450, false).type == Action::release);
    StepEngine cancelled;
    cancelled.submit(-2, 30000);
    cancelled.submit(2, 30000);
    assert(cancelled.tick(30000, false).type == Action::move_and_press);
    cancelled.submit(-1, 30001);
    assert(!cancelled.confirm_press(30002));
    // An ordered CANCEL followed by an offer before the next UI tick must
    // release the old step AND retain the next step.
    cancelled.submit(5, 30003);
    assert(cancelled.tick(30003, false).type == Action::release);
    const auto next=cancelled.tick(30004, false);
    assert(next.type==Action::move_and_press && next.heading==5);
    assert(cancelled.confirm_press(30004));
    cancelled.submit(-4, 30005);
    assert(cancelled.tick(30005, false).type==Action::release);
    assert(!cancelled.confirm_press(30005));
    // Calibration is independent of the old 42%-of-window heuristic.
    const Point ground{400, 276};
    assert(direction_point(0,800,600,ground,24,12).x==400);
    assert(direction_point(0,800,600,ground,24,12).y==252);
    assert(direction_point(2,800,600,ground,24,12).x==448);
    assert(direction_point(2,800,600,ground,24,12).y==276);
    assert(direction_point(0,800,600,{-1,200},24,12).x<0);
    assert(direction_point(0,800,600,ground,0,12).x<0);
    assert(direction_point(1,800,600,{400,15},24,12).x<0);
    const auto middle=absolute_mouse_point({-1,-2},{-2,-4},3,5);
    assert(middle.x==32768 && middle.y==32768);
    const auto first=absolute_mouse_point({-1920,-200},{-1920,-200},3840,1280);
    const auto last=absolute_mouse_point({1919,1079},{-1920,-200},3840,1280);
    assert(first.x==0 && first.y==0 && last.x==65535 && last.y==65535);
    assert(absolute_mouse_point({-1921,0},{-1920,0},1920,1080).x<0);
    assert(absolute_mouse_point({1920,0},{0,0},1920,1080).x<0);
    assert(absolute_mouse_point({0,0},{0,0},1,1).x<0);
    assert(direction_point(7,800,600,ground,24,12,static_cast<DirectionLayout>(2)).x<0);
    std::puts("NativeStep core tests: PASS (10,000 cycles, confirmed press, staged cancel/expiry, calibrated anchor, screen-heading-v2, virtual desktop)");
}
