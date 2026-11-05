#ifndef PSS_APP_CENTER_H
#define PSS_APP_CENTER_H
#include <windows.h>
/* Routes only the verified AppCenterUI home navigation. No auth/network hooks. */
BOOL PssAppCenterInitialize(HWND game, HMODULE module);
void PssAppCenterDetach(void);
#endif
