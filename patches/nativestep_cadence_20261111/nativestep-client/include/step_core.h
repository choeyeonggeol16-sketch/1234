#pragma once

#include <cstddef>
#include <cstdint>

namespace nativestep {

enum class Command : int { invalid = -3, start = -2, cancel = -1, north = 0 };

// Only call after decoding a server-supplied notification on the client side.
// This parser deliberately does not interpret arbitrary game packet bytes.
// Returns -4 for STOP, -3 for no command, -2 for START, -1 for CANCEL, 0..7 for headings.
int parse_control(const char* text, size_t length) noexcept;

struct Point { int x, y; };
enum class DirectionLayout { screen_compass = 0, world_isometric = 1 };
// This client's player is horizontally centered in the viewport. Preserve
// measured ground Y while correcting stale/off-center pointer calibration X.
// A manual-X override remains available for a different viewport layout.
Point player_anchor(Point saved, int width, int height, bool center_x = true) noexcept;
Point direction_point(int heading, int width, int height) noexcept;
// Anchor is the character's ground position, measured in CLIENT coordinates.
// Tile dimensions may be changed for a differently scaled client.
Point direction_point(int heading, int width, int height, Point anchor,
                      int half_tile_width, int half_tile_height,
                      DirectionLayout layout = DirectionLayout::screen_compass) noexcept;
// Convert a virtual-desktop pixel to SendInput's 0..65535 absolute coordinates.
// Invalid/off-desktop targets return {-1,-1}; negative monitor origins are valid.
Point absolute_mouse_point(Point pixel, Point desktop_origin, int width, int height) noexcept;

struct Action {
    enum Type : uint8_t { none, move_and_press, move_held, release } type = none;
    int heading = -1;
};

// Pure state engine. No OS calls; testable on Linux and Windows.
// Call submit() and tick() on the same game window thread (first-test adapter).
// The engine does not move the server character or generate C_MoveChar itself.
class StepEngine final {
public:
    void submit(int command, uint64_t now_ms) noexcept;
    Action tick(uint64_t now_ms, bool user_left_button_down) noexcept;
    // move_and_press reserves a step; the adapter may need several UI ticks
    // to stage the cursor. Start the hold budget only after SendInput succeeds.
    bool confirm_press(uint64_t now_ms) noexcept;
    void shutdown() noexcept;
    bool has_manual_override() const noexcept { return manual_override_; }
    bool is_enabled() const noexcept { return enabled_; }

private:
    int desired_ = -1;
    int applied_ = -1;
    uint64_t expires_ms_ = 0;
    uint64_t revision_ = 0;
    uint64_t applied_revision_ = 0;
    bool held_ = false;
    bool pressed_ = false;
    bool pulse_complete_ = false;
    bool rearm_after_release_ = false;
    uint64_t pressed_at_ms_ = 0;
    bool enabled_ = false; // Only item 49027 START can arm input.
    bool manual_override_ = false;
    // Item 49027 is clicked with the physical left button. Ignore that same
    // press until it has been observed released, then detect new manual clicks.
    bool awaiting_start_click_release_ = false;
};

} // namespace nativestep
