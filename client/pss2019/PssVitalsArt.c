/* Classic-inspired HP/MP frame, rendered without per-frame allocation.
 * The original client resource is not embedded. All artwork here is new,
 * and may be replaced by licensed source art later. */
#include "PssVitalsArt.h"
#include <stddef.h>
#include <string.h>

enum { W=PSS_VITALS_WIDTH, H=PSS_VITALS_HEIGHT,
       BAR_Y=42, BAR_H=22, BAR_W=214, LEFT_X=69, RIGHT_X=333 };

unsigned PssVitalsFill(int32_t current,int32_t maximum,unsigned width){
    if(maximum<=0||current<=0)return 0;
    if(current>=maximum)return width;
    return (unsigned)((uint64_t)(uint32_t)current*width/(uint32_t)maximum);
}
static uint32_t rgba(int r,int g,int b){
    if(r<0)r=0;else if(r>255)r=255;
    if(g<0)g=0;else if(g>255)g=255;
    if(b<0)b=0;else if(b>255)b=255;
    return 0xFF000000U|((uint32_t)r<<16)|((uint32_t)g<<8)|(uint32_t)b;
}
static void point(uint32_t* px,int x,int y,uint32_t c){
    if((unsigned)x<W&&(unsigned)y<H)px[(size_t)y*W+(unsigned)x]=c;
}
static void line(uint32_t* px,int x0,int y0,int x1,int y1,uint32_t c,int width){
    int dx=x1-x0;if(dx<0)dx=-dx;
    int dy=y1-y0;if(dy<0)dy=-dy;
    int sx=x0<x1?1:-1,sy=y0<y1?1:-1,err=dx-dy;
    for(;;){
        for(int yy=-width;yy<=width;++yy)
            for(int xx=-width;xx<=width;++xx)
                if(xx*xx+yy*yy<=width*width)point(px,x0+xx,y0+yy,c);
        if(x0==x1&&y0==y1)break;
        int e2=err*2;
        if(e2>-dy){err-=dy;x0+=sx;}
        if(e2<dx){err+=dx;y0+=sy;}
    }
}
static void oval(uint32_t* px,int cx,int cy,int rx,int ry,uint32_t c){
    if(rx<1||ry<1)return;
    for(int y=-ry;y<=ry;++y)
        for(int x=-rx;x<=rx;++x)
            if((int64_t)x*x*ry*ry+(int64_t)y*y*rx*rx<=(int64_t)rx*rx*ry*ry)
                point(px,cx+x,cy+y,c);
}
static void triangle(uint32_t* px,int ax,int ay,int bx,int by,int cx,int cy,uint32_t c){
    int minx=ax,maxx=ax,miny=ay,maxy=ay;
    if(bx<minx)minx=bx;
    if(cx<minx)minx=cx;
    if(bx>maxx)maxx=bx;
    if(cx>maxx)maxx=cx;
    if(by<miny)miny=by;
    if(cy<miny)miny=cy;
    if(by>maxy)maxy=by;
    if(cy>maxy)maxy=cy;
    int64_t area=(int64_t)(bx-ax)*(cy-ay)-(int64_t)(by-ay)*(cx-ax);
    if(!area)return;
    for(int y=miny;y<=maxy;++y)for(int x=minx;x<=maxx;++x){
        int64_t w0=(int64_t)(bx-ax)*(y-ay)-(int64_t)(by-ay)*(x-ax);
        int64_t w1=(int64_t)(cx-bx)*(y-by)-(int64_t)(cy-by)*(x-bx);
        int64_t w2=(int64_t)(ax-cx)*(y-cy)-(int64_t)(ay-cy)*(x-cx);
        if(area>0?(w0>=0&&w1>=0&&w2>=0):(w0<=0&&w1<=0&&w2<=0))point(px,x,y,c);
    }
}
/* Etched metal border and glassy bar, HP anchored at the dragon (right).
 * The leftmost HP pixels disappear first. MP keeps its original direction. */
static void bar(uint32_t* p,int x,int hp,unsigned filled){
    for(int y=BAR_Y-4;y<BAR_Y+BAR_H+5;++y)
        for(int k=-5;k<BAR_W+5;++k){
            int xx=x+k;
            uint32_t c=0;
            if(y==BAR_Y-4||y==BAR_Y+BAR_H+4)c=rgba(35,25,21);
            else if(y==BAR_Y-3||y==BAR_Y+BAR_H+3)c=rgba(177,142,99);
            else if(y==BAR_Y-2||y==BAR_Y+BAR_H+2)c=rgba(76,59,47);
            else if(y==BAR_Y-1||y==BAR_Y+BAR_H+1)c=rgba(22,18,22);
            else if(k>=0&&k<BAR_W){
                int iy=y-BAR_Y;
                int lit=iy<6?25-iy*2:iy>BAR_H-4?6:0;
                c=hp?rgba(37+lit/4,10+lit/7,15+lit/9)
                    :rgba(16+lit/6,18+lit/6,34+lit/3);
                int charged=hp?(k>=BAR_W-(int)filled):(k<(int)filled);
                if(charged){
                    int glow=iy<5?(5-iy)*9:iy>BAR_H-4?(iy-(BAR_H-4))*-3:0;
                    if(hp)c=rgba(155+glow,19+glow/4,30+glow/5);
                    else c=rgba(59+glow/3,75+glow/2,139+glow);
                }
                if(iy==0)c=rgba(207,160,125);
                else if(iy==1)c=charged?(hp?rgba(230,110,105):rgba(184,189,234)):rgba(65,55,62);
                else if(iy==BAR_H-1)c=rgba(18,13,22);
            }
            if(c)point(p,xx,y,c);
        }
    line(p,x-4,BAR_Y-4,x+BAR_W+3,BAR_Y-4,rgba(82,65,52),0);
    line(p,x-4,BAR_Y+BAR_H+3,x+BAR_W+3,BAR_Y+BAR_H+3,rgba(106,87,74),0);
}
static void endcap(uint32_t* p,int mirror){
    int mid=mirror?W-30:30;
    int sign=mirror?-1:1;
    oval(p,mid,47,13,24,rgba(38,27,28));
    oval(p,mid,47,9,22,rgba(115,80,57));
    oval(p,mid,46,6,21,rgba(207,171,123));
    oval(p,mid,48,3,21,rgba(72,49,41));
    line(p,mid,20,mid,69,rgba(212,180,136),1);
    line(p,mid+sign*5,33,mid+sign*5,63,rgba(86,56,42),1);
    triangle(p,mid-sign*17,48,mid-sign*12,20,mid+sign*7,4,rgba(71,49,40));
    triangle(p,mid-sign*16,46,mid-sign*11,20,mid+sign*6,6,rgba(168,137,97));
    triangle(p,mid+sign*12,35,mid+sign*6,14,mid+sign*17,2,rgba(178,149,111));
    triangle(p,mid+sign*13,34,mid+sign*8,12,mid+sign*19,0,rgba(80,60,48));
    line(p,mid-sign*15,39,mid+sign*10,12,rgba(226,192,145),1);
    line(p,mid-sign*10,53,mid+sign*22,32,rgba(193,155,106),1);
}
static void rail(uint32_t* p,int from,int to){
    for(int x=from;x<to;++x){
        int y=34;
        if(x<from+38)y=34-((x-from)*3/38);
        else if(x>to-40)y=33+((x-(to-40))*4/40);
        else if((x-from)%167>145)y-=((x-from)%167-145)/7;
        point(p,x,y+2,rgba(56,39,37));
        point(p,x,y+1,rgba(151,113,79));
        point(p,x,y,rgba(219,186,139));
        point(p,x,y-1,rgba(103,75,59));
    }
    for(int j=0;j<4;++j){
        int x=from+23+j*52;
        if(x>to-28)break;
        line(p,x,33,x+18,39,rgba(62,40,36),1);
        line(p,x,31,x+18,36,rgba(178,136,97),0);
    }
}
static void head(uint32_t* p){
    const int cx=W/2;
    oval(p,cx,46,46,30,rgba(21,17,23));
    oval(p,cx,42,38,29,rgba(76,58,48));
    oval(p,cx,43,32,25,rgba(185,149,108));
    oval(p,cx,45,27,24,rgba(73,55,46));
    for(int sign=-1;sign<=1;sign+=2){
        triangle(p,cx+sign*17,40,cx+sign*40,36,cx+sign*48,8,rgba(44,34,36));
        triangle(p,cx+sign*17,36,cx+sign*37,30,cx+sign*43,4,rgba(171,139,101));
        triangle(p,cx+sign*24,33,cx+sign*34,20,cx+sign*35,0,rgba(224,192,150));
        triangle(p,cx+sign*16,26,cx+sign*18,5,cx+sign*26,18,rgba(95,73,55));
        triangle(p,cx+sign*17,29,cx+sign*17,9,cx+sign*22,22,rgba(215,181,138));
        oval(p,cx+sign*27,40,9,8,rgba(52,37,35));
        oval(p,cx+sign*27,38,6,6,rgba(204,161,117));
        triangle(p,cx+sign*12,41,cx+sign*32,43,cx+sign*24,61,rgba(104,73,54));
        triangle(p,cx+sign*13,39,cx+sign*25,48,cx+sign*21,61,rgba(184,139,93));
        line(p,cx+sign*6,37,cx+sign*23,35,rgba(232,200,156),1);
        line(p,cx+sign*11,40,cx+sign*20,41,rgba(12,7,12),2);
        point(p,cx+sign*16,40,rgba(210,33,22));
    }
    triangle(p,cx-14,22,cx,11,cx+14,22,rgba(196,162,116));
    triangle(p,cx-7,25,cx,16,cx+7,25,rgba(247,220,176));
    oval(p,cx,42,14,18,rgba(157,117,77));
    triangle(p,cx-13,38,cx,33,cx+13,38,rgba(221,183,127));
    oval(p,cx,51,11,10,rgba(32,16,17));
    triangle(p,cx-12,49,cx,64,cx+12,49,rgba(142,98,65));
    triangle(p,cx-7,51,cx,67,cx+7,51,rgba(27,18,20));
    triangle(p,cx-8,51,cx-4,62,cx-1,51,rgba(225,204,169));
    triangle(p,cx+8,51,cx+4,62,cx+1,51,rgba(225,204,169));
    line(p,cx-27,57,cx-19,68,rgba(214,172,115),1);
    line(p,cx+27,57,cx+19,68,rgba(214,172,115),1);
    for(int side=-1;side<=1;side+=2){
        int x=cx+side*45;
        line(p,x,39,x,69,rgba(51,35,32),3);
        line(p,x-side*2,41,x-side*2,65,rgba(168,132,99),1);
    }
}
void PssVitalsArt(uint32_t* pixels,int32_t hp,int32_t max_hp,int32_t mp,int32_t max_mp){
    if(!pixels)return;
    memset(pixels,0,(size_t)W*H*sizeof(*pixels));
    bar(pixels,LEFT_X,1,PssVitalsFill(hp,max_hp,BAR_W));
    bar(pixels,RIGHT_X,0,PssVitalsFill(mp,max_mp,BAR_W));
    rail(pixels,31,W/2-33);
    rail(pixels,W/2+33,W-31);
    endcap(pixels,0);
    endcap(pixels,1);
    head(pixels);
}
