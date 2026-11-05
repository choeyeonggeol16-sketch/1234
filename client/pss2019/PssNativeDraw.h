#ifndef PSS_NATIVE_DRAW_H
#define PSS_NATIVE_DRAW_H
#include <windows.h>
BOOL PssNativeDrawInitialize(void);
void PssNativeDrawSetActive(BOOL active);
BOOL PssNativeDrawHasPainted(void);
void PssNativeDrawDetach(void);
#ifdef PSS_NATIVE_DRAW_TEST
BOOL PssNativeDrawTestInstall(void* surface);
#endif
#endif
