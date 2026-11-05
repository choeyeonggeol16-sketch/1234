#ifndef PSS_ACTIVITY_H
#define PSS_ACTIVITY_H
#include <windows.h>
void PssActivityInitialize(HWND game,HMODULE module);
void PssActivityUpdate(BOOL running);
void PssActivityDetach(void);
BOOL PssActivityActive(void);
BOOL PssActivityPaintSurface(HDC dc,const RECT* source,const RECT* destination);
#endif
