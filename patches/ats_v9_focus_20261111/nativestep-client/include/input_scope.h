#pragma once
namespace nativestep {
// Track where a physical press BEGAN, not where the pointer later moves.
// Activation-only clicks do not interrupt a hunt that continued in the background.
class ManualPressScope {
public:
    bool sample(bool down, bool game_foreground, bool inside_game_client) noexcept {
        if (!sampled_) owns_press_ = down && game_foreground && inside_game_client;
        else if (!down) owns_press_ = false;
        else if (!was_down_) owns_press_ = game_foreground && was_foreground_ && inside_game_client;
        sampled_ = true; was_down_ = down; was_foreground_ = game_foreground;
        return down && owns_press_;
    }
private:
    bool sampled_=false, was_down_=false, was_foreground_=false, owns_press_=false;
};
}
