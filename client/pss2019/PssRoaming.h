#ifndef PSS_ROAMING_H
#define PSS_ROAMING_H
#include <windows.h>
void PssRoamingInitialize(HWND game, HMODULE module);
void PssRoamingObserve(void);
void PssRoamingReset(BOOL cancel_owned_move);
void PssRoamingDetach(void);
#endif
