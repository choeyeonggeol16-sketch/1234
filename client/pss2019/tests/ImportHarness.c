#define UNICODE
#include <windows.h>
#include <stdio.h>
__declspec(dllimport) DWORD WINAPI PssClientInit(void);
int main(void) {
 HMODULE loaded=GetModuleHandleW(L"Pss2019.dll");
 if(!loaded){puts("FAIL DLL was not imported before main");return 2;}
 if(PssClientInit()!=1){puts("FAIL imported symbol");return 3;}
 if(FindWindowW(L"Pss2019Activity",0)){puts("FAIL unsupported client created activity indicator");return 5;}
 if(FindWindowW(L"Pss2019Panel",0)){puts("FAIL unexpected helper panel");return 4;}
 puts("PASS PE loader loaded Pss2019.dll before main; init export resolved; wrong-client auto-start refused");return 0;
}
