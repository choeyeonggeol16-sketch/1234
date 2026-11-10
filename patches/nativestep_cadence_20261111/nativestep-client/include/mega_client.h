#pragma once
#include <windows.h>
#include "step_core.h"

struct MegaClick {
    nativestep::Point client{}, logical{}, world{};
    int heading = -1, mode = -1;
};
// Supports only the uploaded Mega.bin SHA-256 and matching unpacked code.
bool MegaInitialize() noexcept;
bool MegaPlanClick(HWND hwnd, int heading, MegaClick& click) noexcept;
bool MegaMouseDownReady() noexcept;
bool MegaGoalMatches(const MegaClick& click) noexcept;
bool MegaStepApplied(const MegaClick& click) noexcept;
void MegaCancelOwnedMovement() noexcept;
