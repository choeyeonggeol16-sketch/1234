#pragma once
#include <windows.h>
void NativeStep_NetworkStart(HMODULE module);
void NativeStep_NetworkStop();
// Implemented in nativestep_dll.cpp; only posts to the game's UI message queue.
void NativeStep_PostNetworkCommand(int command);

// Sanitized, rate-limited local input diagnostics (never prints host addresses).
void NativeStep_LogInput(const char* event, int code);

void NativeStep_RequestManualStop();
