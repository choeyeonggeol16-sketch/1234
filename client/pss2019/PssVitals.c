/* Read-only adapter for the exact 19.02.15.1002 client. The HP/MP condition
 * functions identify the values and max-value getters. Simple getters are
 * decoded as data loads; no native function or initializer is executed. */
#include "PssVitals.h"
#include "PssBridge.h"
#include <string.h>
#ifdef PSS_VITALS_TEST
extern BOOL PssVitalsTestRead(uint32_t,void*,SIZE_T);
#endif
static const BYTE hp_signature[]={0x56,0x8B,0xF1,0x8B,0x0D,0x94,0x3D,0x5B,0x01,0x85,0xC9,0x75,0x0B,0xE8,0x9E,0x6B,0xFC,0xFF,0x8B,0x0D,0x94,0x3D,0x5B,0x01,0xE8,0xA3,0x88,0x83,0x00,0x8B,0xC8,0xE8,0xBC,0x88,0x83,0x00,0x8B,0x0D,0xB0,0x2F,0x6E,0x01,0x0F,0xBF,0xD0,0x81,0xF1,0x21,0x79,0x01,0xC0,0xA1,0xB4,0x2F,0x6E,0x01,0x66,0x0F,0x6E,0xC2,0x8B,0x04,0x88,0x33,0x05,0xB8,0x2F,0x6E,0x01,0x0F,0x5B,0xC0,0x8B,0x4E,0x04,0x5E,0x80,0x79,0x10,0x00,0x66,0x0F,0x6E,0xC8,0x0F,0x5B,0xC9,0xF3,0x0F,0x5E,0xC8,0xF3,0x0F,0x59,0x0D,0xBC,0xFE,0x17,0x01,0xF3,0x0F,0x2C,0xD1,0x75};
static const BYTE mp_signature[]={0x56,0x8B,0xF1,0x8B,0x0D,0x94,0x3D,0x5B,0x01,0x85,0xC9,0x75,0x0B,0xE8,0x7E,0x63,0xFC,0xFF,0x8B,0x0D,0x94,0x3D,0x5B,0x01,0xE8,0x83,0x80,0x83,0x00,0x8B,0xC8,0xE8,0xBC,0x80,0x83,0x00,0x8B,0x0D,0xBC,0x2F,0x6E,0x01,0x0F,0xBF,0xD0,0x81,0xF1,0x21,0x79,0x01,0xC0,0xA1,0xC0,0x2F,0x6E,0x01,0x66,0x0F,0x6E,0xC2,0x8B,0x04,0x88,0x33,0x05,0xC4,0x2F,0x6E,0x01,0x0F,0x5B,0xC0,0x8B,0x4E,0x04,0x5E,0x80,0x79,0x10,0x00,0x66,0x0F,0x6E,0xC8,0x0F,0x5B,0xC9,0xF3,0x0F,0x5E,0xC8,0xF3,0x0F,0x59,0x0D,0xBC,0xFE,0x17,0x01,0xF3,0x0F,0x2C,0xD1,0x75};
static BOOL verified,warned,reported;
static BOOL read_at(uint32_t address,void* out,SIZE_T size){
#ifdef PSS_VITALS_TEST
    return PssVitalsTestRead(address,out,size);
#else
    SIZE_T got=0;
    return address&&ReadProcessMemory(GetCurrentProcess(),(void*)(uintptr_t)address,out,size,&got)&&got==size;
#endif
}
static BOOL matches(uint32_t address,const BYTE* expected,SIZE_T size){
    BYTE bytes[104];return size<=sizeof(bytes)&&read_at(address,bytes,size)&&!memcmp(bytes,expected,size);
}
static BOOL simple_getter(uint32_t function,uint32_t object,uint32_t* out){
    BYTE code[12];if(!object||!read_at(function,code,sizeof(code)))return FALSE;
    unsigned at=0,width=4;BOOL sign=FALSE,lea=FALSE;
    if(code[0]==0x66&&code[1]==0x8B){width=2;at=1;}
    else if(code[0]==0x0F&&(code[1]==0xB7||code[1]==0xBF)){width=2;sign=code[1]==0xBF;at=1;}
    else if(code[0]!=0x8B&&code[0]!=0x8D)goto unsupported;
    lea=code[at]==0x8D;
    BYTE mode=code[at+1];int32_t displacement=0;unsigned end;
    if(mode==0x01)end=at+2;
    else if(mode==0x41){displacement=(int8_t)code[at+2];end=at+3;}
    else if(mode==0x81){memcpy(&displacement,code+at+2,4);end=at+6;}
    else goto unsupported;
    if(code[end]!=0xC3)goto unsupported;
    int64_t address=(int64_t)object+displacement;if(address<=0||address>UINT32_MAX)return FALSE;
    if(lea){if(function!=0xEB19E0)goto unsupported;*out=(uint32_t)address;return TRUE;}
    uint32_t value=0;if(!read_at((uint32_t)address,&value,width))return FALSE;
    *out=sign?(uint32_t)(int32_t)(int16_t)value:value;return TRUE;
unsupported:
    if(!warned){
        wchar_t text[180];wsprintfW(text,L"HUD HP/MP unsupported getter %08lX bytes=%02X%02X%02X%02X%02X%02X%02X%02X; hidden",
            (unsigned long)function,code[0],code[1],code[2],code[3],code[4],code[5],code[6],code[7]);
        PssBridgeLog(text);warned=TRUE;
    }
    return FALSE;
}
static BOOL current(uint32_t descriptor,int32_t* out){
    uint32_t a[3],b[3],value;
    if(!read_at(descriptor,a,sizeof(a)))return FALSE;
    uint64_t address=(uint64_t)a[1]+(uint64_t)(a[0]^0xC0017921U)*4;
    if(!a[1]||address>UINT32_MAX||!read_at((uint32_t)address,&value,4)||
       !read_at(descriptor,b,sizeof(b))||memcmp(a,b,sizeof(a)))return FALSE;
    *out=(int32_t)(value^a[2]);return TRUE;
}
void PssVitalsReset(void){verified=warned=reported=FALSE;}
BOOL PssVitalsRead(PssVitals* out){
    if(!out)return FALSE;
    memset(out,0,sizeof(*out));
    uint32_t phase,root,again,stats,max_hp,max_mp;PssVitals value;
    if(!read_at(0x15D8E44,&phase,4)||phase!=3)return FALSE;
    if(!verified){
        if(!matches(0x679120,hp_signature,sizeof(hp_signature))||!matches(0x679940,mp_signature,sizeof(mp_signature)))return FALSE;
        verified=TRUE;PssBridgeLog(L"HUD native HP/MP condition signatures matched; read-only adapter enabled");
    }
    if(!read_at(0x15B3D94,&root,4)||!root||!simple_getter(0xEB19E0,root,&stats)||
       !simple_getter(0xEB1A00,stats,&max_hp)||!simple_getter(0xEB1A20,stats,&max_mp)||
       !current(0x16E2FB0,&value.hp)||!current(0x16E2FBC,&value.mp))return FALSE;
    // The original condition functions explicitly sign-extend AX.
    value.max_hp=(int16_t)max_hp;value.max_mp=(int16_t)max_mp;
    if(value.hp<0||value.mp<0||value.max_hp<=0||value.max_mp<0||
       !read_at(0x15D8E44,&phase,4)||phase!=3||!read_at(0x15B3D94,&again,4)||again!=root)return FALSE;
    *out=value;
    if(!reported){
        wchar_t text[160];wsprintfW(text,L"HUD HP/MP live snapshot HP=%ld/%ld MP=%ld/%ld",(long)value.hp,(long)value.max_hp,(long)value.mp,(long)value.max_mp);
        PssBridgeLog(text);reported=TRUE;
    }
    return TRUE;
}
