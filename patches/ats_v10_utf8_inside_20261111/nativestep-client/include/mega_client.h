#pragma once
#include <windows.h>
#include "step_core.h"

struct MegaClick {
    nativestep::Point client{}, logical{}, world{};
    int heading = -1, mode = -1, candidate = 0;
};
// Supports only the uploaded Mega.bin SHA-256 and matching unpacked code.
bool MegaInitialize() noexcept;
bool MegaPlanClick(HWND hwnd, int heading, MegaClick& click, unsigned candidate = 0) noexcept;
bool MegaMouseDownReady() noexcept;
bool MegaClickClear(const MegaClick& click) noexcept;
void MegaLogState(const MegaClick& click) noexcept;
bool MegaGoalMatches(const MegaClick& click) noexcept;
bool MegaStepApplied(const MegaClick& click) noexcept;
void MegaCancelOwnedMovement() noexcept;
