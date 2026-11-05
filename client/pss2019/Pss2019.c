#define UNICODE
#define _UNICODE
#include <windows.h>
#include <stdint.h>
#include "PssBridge.h"
#include "PssRoaming.h"
#include "PssActivity.h"
#include "PssNativeDraw.h"
#include "PssAppCenter.h"

#define CONTROL (WM_APP+0x5A1)
static HMODULE module;
static HWND game, panel, status;
static WNDPROC previous;
static BOOL installed, patched, user_hidden, enabled;
static BOOL item_only;
static BOOL activity_initialized;
static BYTE original[5];
static BYTE patch_bytes[5];
static BOOL ui_patch_owned;
static const BYTE ui_code[]={0x55,0x8B,0xEC,0x80,0x7D,0x08,0x00,0x75,0x0D,0xC7,0x45,0x08,0,0,0,0,0x5D,0xE9,0xBA,0xC1,0x4C,0,0x5D,0xC2,0x04,0};
static void *start_address=(void*)0x669C20;
static wchar_t logfile[MAX_PATH];
static SRWLOCK log_guard=SRWLOCK_INIT;
typedef void (__attribute__((thiscall)) *Method)(void*);
typedef void (__attribute__((thiscall)) *BoolMethod)(void*,int);
typedef void (__cdecl *Plain)(void);

static void logline(const wchar_t* s) {
 AcquireSRWLockExclusive(&log_guard);
 if(!logfile[0]) {
  DWORD size=GetModuleFileNameW(module,logfile,MAX_PATH);
  if(size==0||size>=MAX_PATH){logfile[0]=0;ReleaseSRWLockExclusive(&log_guard);return;}
  wchar_t* p=logfile; wchar_t* last=p;
  while(*p) { if(*p==L'\\') last=p+1; ++p; }
  if((SIZE_T)(last-logfile)+lstrlenW(L"PSS2019_trace.txt")>=MAX_PATH){logfile[0]=0;ReleaseSRWLockExclusive(&log_guard);return;}
  lstrcpyW(last,L"PSS2019_trace.txt");
 }
 HANDLE f=CreateFileW(logfile,FILE_APPEND_DATA,FILE_SHARE_READ|FILE_SHARE_WRITE,0,OPEN_ALWAYS,FILE_ATTRIBUTE_NORMAL,0);
 if(f!=INVALID_HANDLE_VALUE) {
  SYSTEMTIME timestamp;GetLocalTime(&timestamp);
  wchar_t line[640];
  int prefix=wsprintfW(line,L"[%02u:%02u:%02u.%03u pid=%lu] ",
                        (unsigned)timestamp.wHour,(unsigned)timestamp.wMinute,
                        (unsigned)timestamp.wSecond,(unsigned)timestamp.wMilliseconds,
                        (unsigned long)GetCurrentProcessId());
  SIZE_T size=lstrlenW(s);
  if(size>(sizeof(line)/sizeof(line[0]))-(SIZE_T)prefix-3)
    size=(sizeof(line)/sizeof(line[0]))-(SIZE_T)prefix-3;
  CopyMemory(line+prefix,s,size*sizeof(wchar_t));
  size+=(SIZE_T)prefix;line[size++]=L'\r';line[size++]=L'\n';
  DWORD n;WriteFile(f,line,(DWORD)(size*sizeof(wchar_t)),&n,0);CloseHandle(f);
 }
 ReleaseSRWLockExclusive(&log_guard);
}
void PssBridgeLog(const wchar_t* text) { logline(text); }
static BOOL readmem(const void* p,void* dst,SIZE_T n) {
 SIZE_T got=0;return ReadProcessMemory(GetCurrentProcess(),p,dst,n,&got)&&got==n;
}
static BOOL pointer_at(DWORD address,void** p) {
 return readmem((void*)address,p,4)&&*p;
}
static BOOL bytes_are(DWORD address,const BYTE* bytes,SIZE_T n) {
 BYTE v[32]; if(n>sizeof(v)||!readmem((void*)address,v,n))return FALSE;
 for(SIZE_T i=0;i<n;i++)if(v[i]!=bytes[i])return FALSE;
 return TRUE;
}
static BOOL version_ok(void) {
 BYTE* base=(BYTE*)GetModuleHandleW(0);
 if(base!=(BYTE*)0x400000)return FALSE;
 IMAGE_DOS_HEADER dos; IMAGE_NT_HEADERS32 nt;
 if(!readmem(base,&dos,sizeof(dos))||dos.e_magic!=IMAGE_DOS_SIGNATURE)return FALSE;
 if(!readmem(base+dos.e_lfanew,&nt,sizeof(nt)))return FALSE;
 return nt.Signature==IMAGE_NT_SIGNATURE&&nt.FileHeader.TimeDateStamp==0x5C663F2A&&
        nt.FileHeader.Machine==IMAGE_FILE_MACHINE_I386&&nt.OptionalHeader.SizeOfImage==28504064;
}
static BOOL code_ready(void) {
 const BYTE stub[]={0xC3,0xCC,0xCC,0xCC,0xCC};
 const BYTE reset[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0xE8,0xE4,0x01,0x01};
 const BYTE flow[]={0x55,0x8B,0xEC,0x6A,0xFF,0x68,0x60,0xE8,0x01,0x01};
 const BYTE next[]={0x56,0x8B,0xF1,0x83,0x7E,0x20,0x00};
 return bytes_are(0x669C20,stub,sizeof(stub))&&bytes_are(0x6688D0,reset,sizeof(reset))&&
        bytes_are(0x669840,flow,sizeof(flow))&&bytes_are(0x669B20,next,sizeof(next));
}
static BOOL ui_signature(BYTE* branch) {
 BYTE live[sizeof(ui_code)];
 if(!readmem((void*)0x7918D0,live,sizeof(live)))return FALSE;
 for(SIZE_T i=0;i<sizeof(live);i++) {
  if(i==8) {if(live[i]!=0x0D&&live[i]!=0x07)return FALSE;}
  else if(live[i]!=ui_code[i])return FALSE;
 }
 *branch=live[8];return TRUE;
}
static BOOL write_ui_branch(BYTE value) {
 DWORD old,ignore;
 if(!VirtualProtect((void*)0x7918D8,1,PAGE_EXECUTE_READWRITE,&old))return FALSE;
 *((volatile BYTE*)0x7918D8)=value;
 BOOL flushed=FlushInstructionCache(GetCurrentProcess(),(void*)0x7918D8,1);
 BOOL protected=VirtualProtect((void*)0x7918D8,1,old,&ignore);
 return flushed&&protected;
}
static BOOL ensure_ui_patch(void) {
 BYTE branch;
 if(!ui_signature(&branch))return FALSE;
 if(branch==0x07)return TRUE;
 ui_patch_owned=TRUE;
 if(!write_ui_branch(0x07))return FALSE;
 logline(L"PlaySupportUI Show(true) enabled: one-byte 0D -> 07 branch");return TRUE;
}
static void restore_ui_patch(void) {
 if(!ui_patch_owned)return;
 BYTE branch;
 if(!ui_signature(&branch)) {logline(L"PSS UI code changed; restore skipped");return;}
 if(branch==0x0D||write_ui_branch(0x0D))ui_patch_owned=FALSE;
}
static void __attribute__((fastcall)) restored_start(void* self,void* unused) {
 (void)unused;
 BYTE snapshot[0xB8];DWORD phase=0;
 logline(L"2102 VALID callback reached");
 if(!readmem(self,snapshot,sizeof(snapshot))||!readmem((void*)0x15D8E44,&phase,4)||phase!=3||
    !*(void**)(snapshot+8)||!*(void**)(snapshot+0x54)||!*(void**)(snapshot+0x5C)) {
  logline(L"Start skipped: world/config not ready");return;
 }
 PssRoamingReset(TRUE);
 ((Method)0x6688D0)(self);
 ((Method)0x669840)(self);
 void* flow=0;
 if(!readmem((BYTE*)self+0x20,&flow,4)||!flow) {
  logline(L"Start skipped: Battle flow is empty");return;
 }
 ((Method)0x669B20)(self);
 *((BYTE*)self+0x58)=1;
 void* ui=0;if(pointer_at(0x15D80E0,&ui))((BoolMethod)0xA330D0)(ui,1);
 PssRoamingObserve();
 logline(L"PSS start sequence completed (runtime test required)");
}
static BOOL apply_patch(void) {
 if(patched)return TRUE;
 if(!version_ok()||!code_ready())return FALSE;
 BYTE branch;if(!ui_signature(&branch)||!ensure_ui_patch())return FALSE;
 DWORD old;BYTE jump[5]={0xE9};
 *(int32_t*)(jump+1)=(int32_t)((BYTE*)restored_start-((BYTE*)start_address+5));
 if(!VirtualProtect(start_address,5,PAGE_EXECUTE_READWRITE,&old)){restore_ui_patch();return FALSE;}
 CopyMemory(original,start_address,5);CopyMemory(start_address,jump,5);
 CopyMemory(patch_bytes,jump,5);
 FlushInstructionCache(GetCurrentProcess(),start_address,5);
 DWORD ignore;VirtualProtect(start_address,5,old,&ignore);
 patched=TRUE;logline(L"Exact 19,02,15,1002 start stub patched");return TRUE;
}
static void unpatch(void) {
 restore_ui_patch();
 if(!patched)return;
 if(!bytes_are((DWORD)start_address,patch_bytes,5)){patched=FALSE;logline(L"Start code changed by another component; restore skipped");return;}
 DWORD old;if(VirtualProtect(start_address,5,PAGE_EXECUTE_READWRITE,&old)) {
  CopyMemory(start_address,original,5);FlushInstructionCache(GetCurrentProcess(),start_address,5);
  DWORD ignore;VirtualProtect(start_address,5,old,&ignore);patched=FALSE;
 }
}
static BOOL world_ready(void) {
 DWORD phase=0;return readmem((void*)0x15D8E44,&phase,4)&&phase==3;
}
static void command(int id) {
 if(!patched||!world_ready()) { SetWindowTextW(status,L"캐릭터 접속 후 사용");return; }
 void* ui=0;
 if(id==101) {
  if(!ensure_ui_patch()){SetWindowTextW(status,L"PSS UI 코드 불일치");return;}
  if(!pointer_at(0x15B4BCC,&ui)){SetWindowTextW(status,L"PSS UI 리소스 없음");logline(L"PSS UI singleton missing");return;}
  DWORD table=0,fn=0;
  if(!readmem(ui,&table,4)||table!=0x11B09C8||!readmem((void*)(table+0x44),&fn,4)||fn!=0x7918D0||
     !bytes_are((DWORD)(uintptr_t)ui+4,(const BYTE*)"PlaySupportUI",14)) {
   logline(L"PSS UI object/vtable differs; open skipped");return;
  }
  ((BoolMethod)fn)(ui,1);SetWindowTextW(status,L"PSS 설정 열기");logline(L"PSS UI show requested");
 } else if(id==102) {
  ((Plain)0x642BF0)();SetWindowTextW(status,L"시작 응답 대기");logline(L"START request 2101 sent using native client framing");
 } else if(id==103) {
  ((Plain)0x642B10)();SetWindowTextW(status,L"중지 응답 대기");logline(L"FINISH request 2103 sent");
 } else if(id==104) {
  ((Plain)0x642B80)();SetWindowTextW(status,L"시간 응답 대기");logline(L"TIME request 2106 sent");
 }
}
static LRESULT CALLBACK panel_proc(HWND hwnd,UINT msg,WPARAM wp,LPARAM lp) {
 if(msg==WM_COMMAND){command(LOWORD(wp));return 0;}
 if(msg==WM_CLOSE){user_hidden=TRUE;ShowWindow(hwnd,SW_HIDE);return 0;}
 return DefWindowProcW(hwnd,msg,wp,lp);
}
static void make_panel(void) {
 WNDCLASSW wc={0};wc.lpfnWndProc=panel_proc;wc.hInstance=module;wc.lpszClassName=L"Pss2019Panel";
 wc.hCursor=LoadCursorW(0,IDC_ARROW);wc.hbrBackground=(HBRUSH)(COLOR_BTNFACE+1);RegisterClassW(&wc);
 panel=CreateWindowExW(WS_EX_TOOLWINDOW|WS_EX_NOACTIVATE,wc.lpszClassName,L"PSS",WS_POPUP|WS_CAPTION|WS_SYSMENU,
  0,0,300,92,game,0,module,0);
 const wchar_t* labels[]={L"PSS 설정",L"시작",L"중지",L"시간"};
 int widths[]={86,54,54,54};int x=5;
 for(int i=0;i<4;i++) {
  CreateWindowW(L"BUTTON",labels[i],WS_CHILD|WS_VISIBLE|BS_PUSHBUTTON,x,5,widths[i],27,panel,(HMENU)(INT_PTR)(101+i),module,0);
  x+=widths[i]+3;
 }
 status=CreateWindowW(L"STATIC",L"캐릭터 접속 후 사용",WS_CHILD|WS_VISIBLE,7,38,260,20,panel,0,module,0);
 ShowWindow(panel,SW_SHOWNOACTIVATE);
}
static void place_panel(void) {
 if(!panel||!IsWindow(game)||user_hidden)return;
 if(IsIconic(game)||!IsWindowVisible(game)){ShowWindow(panel,SW_HIDE);return;}
 RECT rc;GetWindowRect(game,&rc);
 SetWindowPos(panel,HWND_TOP,rc.right-308,rc.top+35,0,0,SWP_NOSIZE|SWP_NOACTIVATE|SWP_SHOWWINDOW);
}
static LRESULT CALLBACK game_proc(HWND hwnd,UINT msg,WPARAM wp,LPARAM lp) {
 if(msg==CONTROL) {
  if(wp==1){user_hidden=FALSE;ShowWindow(panel,SW_SHOWNOACTIVATE);place_panel();return 1;}
  if(wp==3){if(enabled)command(101);return 1;}
  if(wp==4){if(enabled)PssRoamingBlockedShot((int)lp);return 1;}
  if(wp==2) {
   PssAppCenterDetach();PssNativeDrawDetach();PssActivityDetach();activity_initialized=FALSE;
   PssRoamingDetach();
   PssBridgeDetach();
   enabled=FALSE;KillTimer(game,0x505353);unpatch();if(panel){DestroyWindow(panel);panel=0;}status=0;
   WNDPROC old=previous;
   if((WNDPROC)GetWindowLongPtrW(game,GWLP_WNDPROC)==game_proc){SetWindowLongPtrW(game,GWLP_WNDPROC,(LONG_PTR)old);installed=FALSE;}
   logline(L"Detached; original process code restored");return 1;
  }
 }
 if(msg==WM_TIMER&&wp==0x505353) {
  if(!enabled)return 0;
  // auto_initialize/PssAttach run on a bootstrap worker. Window creation must
  // run here, on the owner window's actual message thread.
  if(!activity_initialized){
   activity_initialized=TRUE;
   PssActivityInitialize(game,module);
   PssNativeDrawInitialize();
  }
  if(!patched&&apply_patch()) {
   if(!item_only){make_panel();logline(L"PSS button panel created");}
   else logline(L"PSS item-only mode ready; no external button panel");
  }
  if(patched)PssAppCenterInitialize(game,module);
  if(patched&&world_ready())PssBridgeTick();
  if(patched)PssRoamingObserve();
  if(patched){
   void* manager=0;BYTE active=0,dead=1;
   BOOL running=world_ready()&&pointer_at(0x15B3D14,&manager)&&
       readmem((BYTE*)manager+0x58,&active,1)&&active&&
       readmem((void*)0x15D8E6E,&dead,1)&&!dead;
   PssActivityUpdate(running);
  }
  else PssActivityUpdate(FALSE);
  PssNativeDrawSetActive(PssActivityActive());
  if(status&&!world_ready())SetWindowTextW(status,L"캐릭터 접속 후 사용");
  else if(status) {
   void* manager=0;BYTE active=0;
   if(pointer_at(0x15B3D14,&manager)&&readmem((BYTE*)manager+0x58,&active,1)&&active)SetWindowTextW(status,L"PSS 실행 중");
  }
  place_panel();return 0;
 }
 if(msg==WM_MOVE||msg==WM_SIZE)place_panel();
 if(msg==WM_NCDESTROY){PssAppCenterDetach();PssNativeDrawDetach();PssActivityDetach();activity_initialized=FALSE;PssRoamingDetach();PssBridgeDetach();unpatch();if(panel)DestroyWindow(panel);panel=0;installed=FALSE;}
 return CallWindowProcW(previous,hwnd,msg,wp,lp);
}
__declspec(dllexport) DWORD WINAPI PssAttach(LPVOID arg) {
 HWND hwnd=(HWND)arg;DWORD pid=0;
 GetWindowThreadProcessId(hwnd,&pid);
 if(pid!=GetCurrentProcessId()||!IsWindow(hwnd)||!version_ok())return 0;
 if(installed){if(game!=hwnd)return 0;enabled=TRUE;user_hidden=FALSE;PssRoamingInitialize(game,module);PssBridgeInitialize(game,CONTROL,module);SetTimer(game,0x505353,100,0);return 1;}
 game=hwnd;
 HMODULE pin;GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_PIN,(LPCWSTR)PssAttach,&pin);
 previous=(WNDPROC)GetWindowLongPtrW(game,GWLP_WNDPROC);
 if(!previous)return 0;
 SetLastError(0);
 if(!SetWindowLongPtrW(game,GWLP_WNDPROC,(LONG_PTR)game_proc)&&GetLastError())return 0;
 installed=TRUE;enabled=TRUE;user_hidden=FALSE;SetTimer(game,0x505353,100,0);
 PssRoamingInitialize(game,module);
 if(!PssBridgeInitialize(game,CONTROL,module))logline(L"PSS item bridge initialization failed");
 logline(L"UI thread attached; waiting for unpacked code");return 1;
}
/* Queue on the game UI thread. The item-message bridge can reuse this entry. */
__declspec(dllexport) DWORD WINAPI PssOpenSettings(LPVOID arg) {
 if(!installed||!enabled||(HWND)arg!=game||!IsWindow(game))return 0;
 return PostMessageW(game,CONTROL,3,0)?1:0;
}

/* Imported by the exact-version client patch. Loading the DLL is sufficient;
 * Windows does not call this imported function during normal startup. */
__declspec(dllexport) DWORD WINAPI PssClientInit(void) { return 1; }

typedef struct { HWND window; LONG area; } MainWindow;
static BOOL CALLBACK find_game(HWND hwnd,LPARAM parameter) {
 MainWindow* best=(MainWindow*)parameter;DWORD pid=0;RECT rect;
 GetWindowThreadProcessId(hwnd,&pid);
 if(pid!=GetCurrentProcessId()||!IsWindowVisible(hwnd)||GetWindow(hwnd,GW_OWNER)||!GetClientRect(hwnd,&rect))return TRUE;
 LONG width=rect.right-rect.left,height=rect.bottom-rect.top;
 if(width<320||height<200||width>16384||height>16384)return TRUE;
 LONG area=width*height;if(area>best->area){best->window=hwnd;best->area=area;}return TRUE;
}
static DWORD WINAPI auto_initialize(LPVOID ignored) {
 (void)ignored;logline(L"PSS V9 bottom-docked HP/MP HUD trial loaded");logline(L"PSS loaded by client import; waiting for the game world");
 for(;;) {
  BYTE branch;
  if(world_ready()&&code_ready()&&ui_signature(&branch)) {
   MainWindow found={0,0};EnumWindows(find_game,(LPARAM)&found);
   if(found.window&&PssAttach(found.window)==1) {
    logline(L"PSS automatically attached in item-only mode");return 0;
   }
  }
  Sleep(1000);
 }
}
static BOOL client_auto_marker(void) {
 const BYTE marker[16]={'P','S','S','U','I','-','A','U','T','O','-','V','1',0,0,0};
 return version_ok()&&bytes_are(0x400000+0x1477F00,marker,sizeof(marker));
}
BOOL WINAPI DllMain(HINSTANCE h,DWORD reason,LPVOID reserved) {
 (void)reserved;
 if(reason==DLL_PROCESS_ATTACH) {
  module=h;DisableThreadLibraryCalls(h);
  if(client_auto_marker()) {
   item_only=TRUE;
   /* No waits and no game calls under the loader lock. */
   HANDLE thread=CreateThread(0,0,auto_initialize,0,0,0);
   if(thread)CloseHandle(thread);
  }
 }
 return TRUE;
}
