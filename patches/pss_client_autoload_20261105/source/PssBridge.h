#ifndef PSS_BRIDGE_H
#define PSS_BRIDGE_H
#include <windows.h>
BOOL PssBridgeInitialize(HWND game, UINT message, HMODULE module);
void PssBridgeTick(void);
void PssBridgeDetach(void);
void PssBridgeLog(const wchar_t* text);
#endif
