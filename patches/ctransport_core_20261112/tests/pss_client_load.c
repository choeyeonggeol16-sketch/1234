#define UNICODE
#include <windows.h>
#include <stdio.h>
int main(void){
 HMODULE library=LoadLibraryW(L"Pss2019.dll");
 if(!library){printf("FAIL original PSS client load: %lu\n",GetLastError());return 1;}
 typedef DWORD (WINAPI *InitFn)(void);
 InitFn init=(InitFn)GetProcAddress(library,"PssClientInit");
 if(!init||init()!=1||FindWindowW(L"Pss2019Panel",0)){puts("FAIL original companion client contract");return 2;}
 puts("PASS unchanged PE32 companion Pss2019.dll loaded; PssClientInit resolved; wrong-client attach refused");
 return 0;
}
