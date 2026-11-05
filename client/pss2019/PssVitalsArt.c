/* V11 - ornaments reconstructed from the user-provided Lineage Classic footage.
 * HP/MP are still the original read-only V8 snapshots. No network or PSS changes.
 * The optional graphic data is parsed once, then reused without allocations. */
#include "PssVitalsArt.h"
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#else
#include <stdio.h>
#include <stdlib.h>
#endif

enum { W=PSS_VITALS_WIDTH, H=PSS_VITALS_HEIGHT,
       BAR_Y=43, BAR_H=25, BAR_W=289, HP_X=56, MP_X=439, ART_COLORS=256 };
static uint32_t ornament[W*H];
static int art_state;  /* 0 unread; 1 valid; -1 invalid or missing */

unsigned PssVitalsFill(int32_t current,int32_t maximum,unsigned width){
    if(maximum<=0||current<=0)return 0;
    if(current>=maximum)return width;
    return (unsigned)((uint64_t)(uint32_t)current*width/(uint32_t)maximum);
}
static uint32_t rgb(unsigned r,unsigned g,unsigned b){
    return 0xFF000000U|(r<<16)|(g<<8)|b;
}
static unsigned read16(const unsigned char *s){return (unsigned)s[0]|((unsigned)s[1]<<8);}
static uint32_t read32(const unsigned char *s){
    return (uint32_t)s[0]|((uint32_t)s[1]<<8)|((uint32_t)s[2]<<16)|((uint32_t)s[3]<<24);
}
static uint32_t crc_step(uint32_t crc,unsigned char b){
    crc^=b;
    for(unsigned i=0;i<8;++i)crc=(crc>>1)^((crc&1U)?0xEDB88320U:0U);
    return crc;
}
static FILE *open_asset(void){
#ifdef _WIN32
    MEMORY_BASIC_INFORMATION info;
    if(!VirtualQuery((const void*)&PssVitalsArt,&info,sizeof(info))||!info.AllocationBase)return NULL;
    wchar_t path[MAX_PATH];
    DWORD count=GetModuleFileNameW((HMODULE)info.AllocationBase,path,MAX_PATH);
    if(!count||count>=MAX_PATH)return NULL;
    wchar_t *name=path;
    for(wchar_t *p=path;*p;++p)if(*p==L'\\'||*p==L'/')name=p+1;
    if((size_t)(name-path)+25U>=MAX_PATH)return NULL;
    lstrcpyW(name,L"Pss2019ClassicHud.dat");
    return _wfopen(path,L"rb");
#else
    const char *override=getenv("PSS_CLASSIC_HUD_ART");
    return fopen(override?override:"Pss2019ClassicHud.dat","rb");
#endif
}
static void load_art(void){
    if(art_state)return;
    art_state=-1;
    FILE *fp=open_asset();
    if(!fp)return;
    unsigned char head[14];
    uint32_t palette[ART_COLORS];
    memset(palette,0,sizeof(palette));
    if(fread(head,1,sizeof(head),fp)!=sizeof(head)||memcmp(head,"CRHU",4)!=0||
       read16(head+4)!=W||read16(head+6)!=H){fclose(fp);return;}
    unsigned ncolors=read16(head+8);
    if(ncolors<2||ncolors>ART_COLORS){fclose(fp);return;}
    for(unsigned n=0;n<ncolors;++n){
        unsigned char color[4];
        if(fread(color,1,4,fp)!=4){fclose(fp);return;}
        if(color[3])palette[n]=rgb(color[0],color[1],color[2]);
    }
    uint32_t crc=0xFFFFFFFFU;
    unsigned index=0,failed=0;
    while(index<W*H){
        int ch=fgetc(fp);
        if(ch==EOF){failed=1;break;}
        unsigned value=(unsigned)ch,run=1;
        if(value==255U){
            int size=fgetc(fp),packed=fgetc(fp);
            if(size<=0||packed==EOF){failed=1;break;}
            run=(unsigned)size;
            value=(unsigned)packed;
        }
        if(value>=ncolors||run>W*H-index){failed=1;break;}
        uint32_t pixel=palette[value];
        for(unsigned j=0;j<run;++j){
            ornament[index++]=pixel;
            crc=crc_step(crc,(unsigned char)value);
        }
    }
    if(!failed&&index==W*H&&(crc^0xFFFFFFFFU)==read32(head+10)&&fgetc(fp)==EOF){
        art_state=1;
    }else memset(ornament,0,sizeof(ornament));
    fclose(fp);
}
static void bar(uint32_t *pixels,int x,int hp,unsigned fill){
    if(fill>BAR_W)fill=BAR_W;
    for(int y=BAR_Y;y<BAR_Y+BAR_H;++y){
        int row=y-BAR_Y;
        uint32_t neutral;
        if(row==0)neutral=rgb(119,90,76);
        else if(row==1)neutral=rgb(206,188,176);
        else if(row==2)neutral=rgb(128,118,123);
        else if(row==3)neutral=rgb(76,70,79);
        else if(row<21)neutral=rgb(63+row/8,62+row/8,76+row/8);
        else if(row==21)neutral=rgb(39,32,42);
        else if(row==22)neutral=rgb(20,15,23);
        else neutral=rgb(12,10,15);
        for(int i=0;i<BAR_W;++i){
            int on=hp?(i>=BAR_W-(int)fill):(i<(int)fill);
            uint32_t c=neutral;
            if(on&&row>=2&&row<21){
                if(hp){
                    if(row<=5)c=rgb(178-(row-2)*12,38-(row-2)*8,33-(row-2)*5);
                    else c=rgb(112-row,9+row/2,11+row/2);
                }else{
                    if(row<=5)c=rgb(177-(row-2)*9,177-(row-2)*8,190-(row-2)*7);
                    else c=rgb(74+row/3,69+row/3,93+row/3);
                }
            }
            pixels[y*W+x+i]=c;
        }
    }
}
void PssVitalsArt(uint32_t* pixels,int32_t hp,int32_t max_hp,int32_t mp,int32_t max_mp){
    if(!pixels)return;
    memset(pixels,0,(size_t)W*H*sizeof(*pixels));
    bar(pixels,HP_X,1,PssVitalsFill(hp,max_hp,BAR_W));
    bar(pixels,MP_X,0,PssVitalsFill(mp,max_mp,BAR_W));
    load_art();
    if(art_state==1){
        for(unsigned i=0;i<(unsigned)W*H;++i)
            if(ornament[i])pixels[i]=ornament[i];
    }
}
