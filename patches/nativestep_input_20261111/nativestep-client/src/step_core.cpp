#include "step_core.h"

#include <cstring>

namespace nativestep {
namespace {
constexpr char kPrefix[] = "@@NATIVESTEP:";
constexpr size_t kPrefixLen = sizeof(kPrefix) - 1;
// Keep a bounded expiry while awaiting input: refreshes cannot retrigger a
// press. STOP/CANCEL release immediately.
constexpr uint64_t kStepExpiryMs = 450;
// The original F8 input probe worked with a held left click. The later 75ms
// click could be released before Mega.bin recognized a walk. Keep it held
// briefly, but CANCEL from the server may release it sooner after C_MoveChar.
constexpr uint64_t kSinglePressMs = 300;
}

int parse_control(const char* text, size_t length) noexcept {
    if (!text || length < kPrefixLen + 3 || length > (1u << 20)) return -3;
    // Do not assume the command starts at a fixed packet offset.
    for (size_t i = 0; i + kPrefixLen + 3 <= length; ++i) {
        if (text[i] != '@' || std::memcmp(text + i, kPrefix, kPrefixLen) != 0) continue;
        const size_t pos = i + kPrefixLen;
        if (pos + 3 <= length && text[pos] >= '0' && text[pos] <= '7'
                && text[pos+1] == '@' && text[pos+2] == '@')
            return text[pos] - '0';
        constexpr char cancel[] = "CANCEL@@";
        constexpr char start[] = "START@@";
        constexpr char stop[] = "STOP@@";
        if (pos + sizeof(cancel)-1 <= length &&
                std::memcmp(text+pos, cancel, sizeof(cancel)-1) == 0)
            return -1;
        if (pos + sizeof(start)-1 <= length &&
                std::memcmp(text+pos, start, sizeof(start)-1) == 0)
            return -2;
        if (pos + sizeof(stop)-1 <= length &&
                std::memcmp(text+pos, stop, sizeof(stop)-1) == 0)
            return -4;
    }
    return -3;
}

Point direction_point(int heading, int width, int height) noexcept {
    return direction_point(heading, width, height, {width / 2, height * 42 / 100}, 36, 23);
}

Point direction_point(int heading, int width, int height, Point anchor,
                      int half_tile_width, int half_tile_height) noexcept {
    if (heading < 0 || heading > 7 || width < 24 || height < 24) return {-1, -1};
    if (anchor.x < 0 || anchor.x >= width || anchor.y < 0 || anchor.y >= height ||
        half_tile_width < 1 || half_tile_width > 256 ||
        half_tile_height < 1 || half_tile_height > 256) return {-1, -1};

    // Server headings are WORLD tile offsets, not compass sectors on screen.
    // Lineage 1 draws this grid as a diamond: +world-X points up/right,
    // +world-Y points down/right. Project the ACTUAL next world tile into
    // client coordinates instead of clicking the same-numbered screen sector.
    // For example, heading 1 (+1,-1) is straight UP on screen; the old
    // unprojected heading 1 clicked upper-right and was received as heading 2.
    static constexpr int worldX[8] = { 0, 1, 1, 1, 0,-1,-1,-1};
    static constexpr int worldY[8] = {-1,-1, 0, 1, 1, 1, 0,-1};
    const int x = anchor.x + (worldX[heading] + worldY[heading]) * half_tile_width;
    const int y = anchor.y + (worldY[heading] - worldX[heading]) * half_tile_height;
    constexpr int margin = 10;
    // Clamping changes the requested direction. Refuse an off-viewport click.
    if (x < margin || x >= width-margin || y < margin || y >= height-margin)
        return {-1, -1};
    return {x, y};
}

void StepEngine::submit(int command, uint64_t now_ms) noexcept {
    if (command == -2) {
        enabled_ = true;
        manual_override_ = false;
        awaiting_start_click_release_ = true;
        desired_ = -1;
        expires_ms_ = 0;
        pulse_complete_ = false;
        rearm_after_release_ = held_;
        ++revision_;
    } else if (command == -4) {
        enabled_ = false;
        awaiting_start_click_release_ = false;
        desired_ = -1;
        expires_ms_ = 0;
        pulse_complete_ = false;
        rearm_after_release_ = false;
        ++revision_;
    } else if (command == -1) {
        // CANCEL is an ordered acknowledgement: only after this may a new
        // server authorization produce another client click.
        desired_ = -1;
        expires_ms_ = 0;
        pulse_complete_ = false;
        rearm_after_release_ = held_;
        ++revision_;
    } else if (command >= 0 && command < 8 && enabled_ && !manual_override_) {
        if ((!pulse_complete_ && !held_) || rearm_after_release_) {
            desired_ = command;
            expires_ms_ = now_ms + kStepExpiryMs;
            ++revision_;
        } else if (held_) {
            // Refreshing an offer must NOT move the mouse or start another step.
            expires_ms_ = now_ms + kStepExpiryMs;
        }
    }
}

Action StepEngine::tick(uint64_t now_ms, bool user_left_button_down) noexcept {
    if (awaiting_start_click_release_) {
        if (user_left_button_down) {
            if (held_) {
                held_ = false;
                pressed_ = false;
                pulse_complete_ = false;
                rearm_after_release_ = false;
                applied_ = -1;
                return {Action::release, -1};
            }
            return {};
        }
        awaiting_start_click_release_ = false;
    } else if (enabled_ && user_left_button_down) {
        manual_override_ = true;
        desired_ = -1;
        pulse_complete_ = true;
        ++revision_;
    }
    if (held_ && (rearm_after_release_ ||
                 (pressed_ && now_ms - pressed_at_ms_ >= kSinglePressMs))) {
        held_ = false;
        pressed_ = false;
        if (!rearm_after_release_) desired_ = -1;
        // If CANCEL arrived while the mouse was pressed, the next server
        // authorization MUST be able to produce a new click. The old code
        // always set pulse_complete_ = true here, permanently swallowing
        // every subsequent offer until item 49027 was toggled again.
        pulse_complete_ = !rearm_after_release_;
        rearm_after_release_ = false;
        applied_ = -1;
        return {Action::release, -1};
    }
    if (desired_ >= 0 && !pressed_ && now_ms >= expires_ms_) {
        desired_ = -1;
        pulse_complete_ = true;
    }
    if (!enabled_ || desired_ < 0 || manual_override_) {
        if (held_) {
            held_ = false;
            pressed_ = false;
            applied_ = -1;
            pulse_complete_ = !rearm_after_release_;
            rearm_after_release_ = false;
            return {Action::release, -1};
        }
        return {};
    }
    if (pulse_complete_) return {};
    if (!held_) {
        held_ = true;
        pressed_ = false;
        applied_ = desired_;
        applied_revision_ = revision_;
        return {Action::move_and_press, desired_};
    }
    return {};
}

bool StepEngine::confirm_press(uint64_t now_ms) noexcept {
    if (!held_ || pressed_ || !enabled_ || manual_override_ ||
            desired_ < 0 || rearm_after_release_ || now_ms >= expires_ms_)
        return false;
    pressed_ = true;
    pressed_at_ms_ = now_ms;
    return true;
}

void StepEngine::shutdown() noexcept {
    enabled_ = false;
    awaiting_start_click_release_ = false;
    desired_ = -1;
    expires_ms_ = 0;
    ++revision_;
}

} // namespace nativestep
