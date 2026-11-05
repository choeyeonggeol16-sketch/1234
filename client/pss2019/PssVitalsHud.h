#ifndef PSS_VITALS_HUD_H
#define PSS_VITALS_HUD_H
#include <windows.h>
void PssVitalsHudInitialize(HWND game,HMODULE module);
void PssVitalsHudUpdate(BOOL in_world);
void PssVitalsHudDetach(void);
BOOL PssVitalsHudActive(void);
BOOL PssVitalsHudPaintSurface(HDC dc,const RECT* source,const RECT* destination);
#endif
