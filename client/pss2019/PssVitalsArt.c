#include "PssVitalsArt.h"
unsigned PssVitalsFill(int32_t current,int32_t maximum,unsigned width){
    if(maximum<=0||current<=0)return 0;
    if(current>=maximum)return width;
    return (unsigned)((uint64_t)(uint32_t)current*width/(uint32_t)maximum);
}
static uint32_t rgb(unsigned r,unsigned g,unsigned b){return 0xFF000000U|(r<<16)|(g<<8)|b;}
void PssVitalsArt(uint32_t* pixels,int32_t hp,int32_t max_hp,int32_t mp,int32_t max_mp){
    unsigned filled[2]={PssVitalsFill(hp,max_hp,226),PssVitalsFill(mp,max_mp,226)};
    for(int y=0;y<PSS_VITALS_HEIGHT;++y)for(int x=0;x<PSS_VITALS_WIDTH;++x){
        uint32_t c=0;
        if(y>=6&&y<44&&x>=3&&x<PSS_VITALS_WIDTH-3){
            c=rgb(20,19,18);
            if(y==6||y==43||x==3||x==PSS_VITALS_WIDTH-4)c=rgb(103,82,50);
            if(y==7||y==42)c=rgb(180,153,107);
            if(y==9||y==40)c=rgb(57,46,32);
        }
        for(int bar=0;bar<2;++bar){
            int left=bar?277:17;
            if(x>=left-2&&x<left+228&&y>=13&&y<35){
                c=rgb(7,7,9);
                if(x>=left&&x<left+226&&y>=15&&y<33){
                    c=bar?rgb(12,14,34):rgb(35,9,9);
                    if((unsigned)(x-left)<filled[bar]){
                        unsigned shine=(unsigned)(y<21?(y-15)*9:(32-y)*3);
                        c=bar?rgb(36+shine,47+shine,112+shine):rgb(128+shine,20+shine/2,19+shine/2);
                    }
                }
            }
        }
        int dx=x-PSS_VITALS_WIDTH/2,dy=y-24;
        if(dx<0)dx=-dx;
        if(dy<0)dy=-dy;
        if(dx+dy<22)c=dx+dy>=19?rgb(175,140,85):rgb(48,40,29);
        if(dx+dy<9)c=dx+dy>=7?rgb(213,180,112):rgb(90,60,30);
        pixels[y*PSS_VITALS_WIDTH+x]=c;
    }
}
