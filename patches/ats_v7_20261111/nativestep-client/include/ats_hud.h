#pragma once
#include <windows.h>
#include "ats_state.h"
void AtsHud_Start(HWND game) noexcept;
void AtsHud_Status(ats::Status status) noexcept;
void AtsHud_Tick() noexcept;
void AtsHud_Hide() noexcept;
void AtsHud_Stop() noexcept;
