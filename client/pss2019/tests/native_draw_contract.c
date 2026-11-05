#define COBJMACROS
#define DIRECTDRAW_VERSION 0x0700
#include <windows.h>
#include <ddraw.h>
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "PssNativeDraw.h"
typedef struct Surface {void** vt;DWORD caps;struct Surface* back;int refs,blts,flips,dc_acquire,dc_release;BOOL fail;} Surface;
typedef HRESULT(WINAPI *Blt)(void*,RECT*,void*,RECT*,DWORD,DDBLTFX*);
typedef HRESULT(WINAPI *Flip)(void*,void*,DWORD);
static int paints;
void PssBridgeLog(const wchar_t* text){(void)text;}
BOOL PssActivityPaintSurface(HDC dc){assert(dc==(HDC)3);++paints;return TRUE;}
static HRESULT WINAPI blt(void* self,RECT* a,void* b,RECT* c,DWORD d,DDBLTFX* e){(void)a;(void)b;(void)c;(void)d;(void)e;++((Surface*)self)->blts;return S_OK;}
static HRESULT WINAPI flip(void* self,void* a,DWORD b){(void)a;(void)b;++((Surface*)self)->flips;return S_OK;}
static HRESULT WINAPI caps(void* self,DDSCAPS2* value){value->dwCaps=((Surface*)self)->caps;return S_OK;}
static HRESULT WINAPI attached(void* self,DDSCAPS2* value,void** out){assert(value->dwCaps==DDSCAPS_BACKBUFFER);Surface* s=((Surface*)self)->back;if(!s)return DDERR_NOTFOUND;++s->refs;*out=s;return S_OK;}
static ULONG WINAPI release(void* self){return (ULONG)--((Surface*)self)->refs;}
static HRESULT WINAPI getdc(void* self,HDC* dc){Surface* s=self;++s->dc_acquire;if(s->fail)return DDERR_CANTCREATEDC;*dc=(HDC)3;return S_OK;}
static HRESULT WINAPI releasedc(void* self,HDC dc){assert(dc==(HDC)3);++((Surface*)self)->dc_release;return S_OK;}
int main(void){
 void* vt[50]={0};vt[2]=(void*)release;vt[5]=(void*)blt;vt[11]=(void*)flip;vt[12]=(void*)attached;vt[14]=(void*)caps;vt[17]=(void*)getdc;vt[26]=(void*)releasedc;
 Surface back={vt,DDSCAPS_BACKBUFFER,0,1,0,0,0,0,FALSE},front={vt,DDSCAPS_PRIMARYSURFACE,&back,1,0,0,0,0,FALSE};
 assert(PssNativeDrawTestInstall(&front));Blt hooked=(Blt)vt[5];Flip flip_hook=(Flip)vt[11];
 assert(hooked(&front,0,&back,0,0,0)==S_OK&&paints==0&&front.blts==1);
 assert(!PssNativeDrawHasPainted());
 PssNativeDrawSetActive(TRUE);assert(hooked(&front,0,&back,0,0,0)==S_OK&&paints==1&&front.blts==2&&back.dc_acquire==back.dc_release);
 assert(PssNativeDrawHasPainted());
 RECT tiny={0,0,32,32};assert(hooked(&front,&tiny,&back,0,0,0)==S_OK&&paints==1);
 assert(hooked(&front,0,&back,&tiny,0,0)==S_OK&&paints==1);
 assert(hooked(&front,0,&front,0,0,0)==S_OK&&paints==1);
 assert(hooked(&back,0,&front,0,0,0)==S_OK&&paints==1);
 assert(flip_hook(&front,0,0)==S_OK&&paints==2&&back.refs==1);
 assert(flip_hook(&front,&back,0)==S_OK&&paints==3&&back.refs==1);
 back.fail=TRUE;assert(hooked(&front,0,&back,0,0,0)==S_OK&&paints==3);back.fail=FALSE;
 PssNativeDrawDetach();assert(vt[5]==(void*)blt&&vt[11]==(void*)flip);
 assert(!PssNativeDrawHasPainted());
 assert(hooked(&front,0,&back,0,0,0)==S_OK&&paints==3); // callback already in flight
 assert(PssNativeDrawTestInstall(&front));PssNativeDrawSetActive(TRUE);
 assert(((Blt)vt[5])(&front,0,&back,0,0,0)==S_OK&&paints==4);
 PssNativeDrawDetach();assert(back.refs==1&&back.dc_release==paints);
 puts("PASS shared COM-table capture, primary full-frame filtering, Flip backbuffer ownership, DC cleanup/failure, inactive bypass and detach/in-flight/re-attach");
 puts("Controlled COM test doubles; actual game DirectDraw interface matching and fullscreen visibility remain unverified.");
}
