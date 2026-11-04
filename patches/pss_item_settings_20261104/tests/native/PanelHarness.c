#define UNICODE
#include <windows.h>
#include <stdio.h>
#include <string.h>
static HWND window;static int result=1, stage=0;static BOOL hold;
static BOOL prepatched_ui,bad_ui;
static BYTE ui_object[256];
static volatile BYTE reservation[0x1C00000];
static void put(DWORD a,const BYTE* p,SIZE_T n){CopyMemory((void*)a,p,n);}
static LRESULT CALLBACK proc(HWND h,UINT m,WPARAM w,LPARAM l) {
 if(m==WM_TIMER) {
  if(!stage) {
   BOOL patched=*(BYTE*)0x669C20==0xE9;
   HWND panel=FindWindowW(L"Pss2019Panel",L"PSS");
   if(bad_ui) {
    BOOL refused=!patched&&!panel&&*(BYTE*)0x7918D3==0x81;
    printf("bad_ui_refused=%d\n",refused);fflush(stdout);
    SendMessageW(h,WM_APP+0x5A1,2,0);result=refused?0:1;DestroyWindow(h);return 0;
   }
   printf("patch=%d panel=%d\n",patched,panel!=0);fflush(stdout);
   if(!patched
#ifndef PSS_HEADLESS_TEST
      ||!panel
#endif
      ){DestroyWindow(h);return 0;}
   BYTE self[0xB8]={0};typedef void (__attribute__((thiscall)) *Start)(void*);
   ((Start)0x669C20)(self);
   BOOL guarded=self[0x58]==0;printf("world_guard=%d\n",guarded);fflush(stdout);
   if(!guarded){DestroyWindow(h);return 0;}
   *(DWORD*)0x15D8E44=3;
#ifdef PSS_HEADLESS_TEST
   SendMessageW(h,WM_APP+0x5A1,3,0);
#else
   SendMessageW(panel,WM_COMMAND,101,0);
#endif
   BOOL shown=*(DWORD*)(ui_object+0x80)==1;
   printf("native_ui_show=%d\n",shown);fflush(stdout);
   if(!shown){DestroyWindow(h);return 0;}
   *(DWORD*)(ui_object+0x80)=0;
   HMODULE live=GetModuleHandleW(L"Pss2019.dll");
   typedef DWORD(WINAPI *OpenUi)(LPVOID);
   OpenUi open=(OpenUi)GetProcAddress(live,"PssOpenSettings@4");
   if(!open)open=(OpenUi)GetProcAddress(live,"PssOpenSettings");
   BOOL queued=open&&open(h)&&!open(0);MSG queued_msg;
   while(PeekMessageW(&queued_msg,h,WM_APP+0x5A1,WM_APP+0x5A1,PM_REMOVE))DispatchMessageW(&queued_msg);
   BOOL exported=queued&&*(DWORD*)(ui_object+0x80)==1;
   printf("export_ui_request=%d\n",exported);fflush(stdout);
   *(DWORD*)0x15D8E44=0;
   if(!exported){DestroyWindow(h);return 0;}
   stage=1;
   if(hold){KillTimer(h,1);return 0;}
   SendMessageW(h,WM_APP+0x5A1,2,0);
   BOOL restored=*(BYTE*)0x669C20==0xC3&&*(BYTE*)0x7918D8==(prepatched_ui?0x07:0x0D);
   printf("restored=%d panel_removed=%d\n",restored,!IsWindow(panel));fflush(stdout);
   result=restored&&!IsWindow(panel)?0:1;DestroyWindow(h);return 0;
  }
 }
 if(m==WM_DESTROY){PostQuitMessage(0);return 0;}
 return DefWindowProcW(h,m,w,l);
}
int main(int argc,char** argv) {
 wchar_t directory[MAX_PATH];GetModuleFileNameW(0,directory,MAX_PATH);
 wchar_t* last=directory;for(wchar_t* p=directory;*p;p++)if(*p==L'\\')last=p;
 *last=0;SetCurrentDirectoryW(directory);
 hold=(argc>1&&!strcmp(argv[1],"--hold"))||strstr(argv[0],"Preview")!=0;
 prepatched_ui=argc>1&&!strcmp(argv[1],"--prepatched-ui");
 bad_ui=argc>1&&!strcmp(argv[1],"--bad-ui");
 reservation[sizeof(reservation)-1]=1;
 BYTE* base=(BYTE*)GetModuleHandleW(0);
 if(base!=(BYTE*)0x400000){printf("Unexpected harness base\n");return 2;}
 IMAGE_DOS_HEADER* dos=(IMAGE_DOS_HEADER*)base;
 IMAGE_NT_HEADERS32* nt=(IMAGE_NT_HEADERS32*)(base+dos->e_lfanew);
 DWORD old;VirtualProtect(base,4096,PAGE_READWRITE,&old);
 BOOL invalid=argc>1&&!strcmp(argv[1],"--wrong");
 nt->FileHeader.TimeDateStamp=invalid?0:0x5C663F2A;nt->OptionalHeader.SizeOfImage=28504064;
 DWORD ignore;VirtualProtect(base,4096,old,&ignore);
 if(!VirtualProtect((void*)0x660000,0x10000,PAGE_EXECUTE_READWRITE,&old))return 3;
 if(!VirtualProtect((void*)0x15D0000,0x10000,PAGE_READWRITE,&old))return 4;
 if(!VirtualProtect((void*)0x15B0000,0x10000,PAGE_READWRITE,&old))return 4;
 if(!VirtualProtect((void*)0x790000,0x10000,PAGE_EXECUTE_READWRITE,&old))return 4;
 if(!VirtualProtect((void*)0xC50000,0x10000,PAGE_EXECUTE_READWRITE,&old))return 4;
 if(!VirtualProtect((void*)0x11B0000,0x10000,PAGE_READWRITE,&old))return 4;
 const BYTE show[]={0x55,0x8B,0xEC,0x80,0x7D,0x08,0,0x75,0x0D,0xC7,0x45,0x08,0,0,0,0,0x5D,0xE9,0xBA,0xC1,0x4C,0,0x5D,0xC2,4,0};
 const BYTE base_show[]={0x8B,0x44,0x24,0x04,0x89,0x81,0x80,0,0,0,0xC2,4,0};
 put(0x7918D0,show,sizeof(show));put(0xC5DAA0,base_show,sizeof(base_show));
 if(prepatched_ui)*(BYTE*)0x7918D8=0x07;
 if(bad_ui)*(BYTE*)0x7918D3=0x81;
 *(DWORD*)(0x11B09C8+0x44)=0x7918D0;*(DWORD*)ui_object=0x11B09C8;memcpy(ui_object+4,"PlaySupportUI",14);
 *(DWORD*)0x15B4BCC=(DWORD)(uintptr_t)ui_object;
 const BYTE stub[]={0xC3,0xCC,0xCC,0xCC,0xCC};
 const BYTE reset[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0xE8,0xE4,0x01,0x01};
 const BYTE flow[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x60,0xE8,0x01,0x01};
 const BYTE next[]={0x56,0x8B,0xF1,0x83,0x7E,0x20,0x00};
 put(0x669C20,stub,sizeof(stub));put(0x6688D0,reset,sizeof(reset));put(0x669840,flow,sizeof(flow));put(0x669B20,next,sizeof(next));
 WNDCLASSW wc={0};wc.hInstance=GetModuleHandleW(0);wc.lpfnWndProc=proc;wc.lpszClassName=L"PssPanelHarness";
 wc.hbrBackground=(HBRUSH)(COLOR_WINDOW+1);RegisterClassW(&wc);
 window=CreateWindowW(wc.lpszClassName,L"PSS 버튼 UI 검증용 — 게임 아님",WS_OVERLAPPEDWINDOW|WS_VISIBLE,180,180,800,540,
#ifdef PSS_HEADLESS_TEST
 HWND_MESSAGE,
#else
 0,
#endif
 0,wc.hInstance,0);
 if(argc>1&&!strcmp(argv[1],"--passive")) {
  MSG msg;while(GetMessageW(&msg,0,0,0)>0){TranslateMessage(&msg);DispatchMessageW(&msg);}
  return *(BYTE*)0x669C20==0xC3?0:1;
 }
 HMODULE dll=LoadLibraryW(L"Pss2019.dll");if(!dll)return 5;
 typedef DWORD (WINAPI *Attach)(LPVOID);
 Attach callback=(Attach)GetProcAddress(dll,"PssAttach@4");
 if(!callback)callback=(Attach)GetProcAddress(dll,"PssAttach");
 if(!callback){printf("Export missing\n");return 6;}
 DWORD attached=callback(window);
 if(invalid){printf("version_rejected=%d code_unchanged=%d\n",!attached,*(BYTE*)0x669C20==0xC3);return !attached&&*(BYTE*)0x669C20==0xC3?0:8;}
 if(!attached)return 7;
 SendMessageW(window,WM_NULL,0,0);SetTimer(window,1,2000,0);
 MSG msg;while(GetMessageW(&msg,0,0,0)>0){TranslateMessage(&msg);DispatchMessageW(&msg);}
 return result;
}
