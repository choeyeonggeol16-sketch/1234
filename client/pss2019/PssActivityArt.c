#include "PssActivityArt.h"
#include <math.h>
typedef struct {float r,g,b,a;} Pixel;
static void over(Pixel* p,float r,float g,float b,float a){
    if(a<=0)return;
    if(a>1)a=1;
    float keep=1-a;p->r=r*a+p->r*keep;p->g=g*a+p->g*keep;p->b=b*a+p->b*keep;p->a=a+p->a*keep;
}
static unsigned byte(float x){return (unsigned)(x*255.0f+0.5f);}
void PssActivityArtFrame(uint32_t* out,const uint8_t* text,unsigned frame){
    const float pi=3.14159265358979323846f;
    float phase=(float)(frame%PSS_ART_FRAMES)*2*pi/PSS_ART_FRAMES;
    float hx=cosf(phase)*14,hy=sinf(phase)*14;
    for(int y=0;y<PSS_ART_HEIGHT;++y)for(int x=0;x<PSS_ART_WIDTH;++x){
        Pixel p={0};float dx=x-PSS_ART_WIDTH/2.0f,dy=y-19.0f;
        // Soft orange light behind the text, with no hard rectangular panel.
        float tx=dx/53.0f,ty=(y-48.0f)/4.2f;
        float light=expf(-2*(tx*tx*tx*tx+ty*ty))*0.57f;
        over(&p,1,0.30f,0.015f,light);
        if(y<39){
            float radius=sqrtf(dx*dx+dy*dy),edge=radius-14;
            over(&p,1,0.36f,0.025f,0.19f*expf(-edge*edge/9));
            over(&p,1,0.66f,0.29f,0.35f*expf(-edge*edge/0.9f));
            float trail=phase-atan2f(dy,dx);
            if(trail<0)trail+=2*pi;
            if(trail>=2*pi)trail-=2*pi;
            float arc=expf(-trail*0.85f);
            over(&p,1,0.87f,0.62f,arc*0.95f*expf(-edge*edge/0.85f));
            float px=dx-hx,py=dy-hy;
            over(&p,1,0.93f,0.76f,expf(-(px*px+py*py)/2.3f)*0.95f);
            // Four samples keep the stationary play triangle's edges smooth.
            float coverage=0;
            for(int sy=0;sy<2;++sy)for(int sx=0;sx<2;++sx){
                float ax=dx+(sx?0.25f:-0.25f),ay=dy+(sy?0.25f:-0.25f);
                if(ax>=-4&&ax<=7&&fabsf(ay)<=(7-ax)*7/11)coverage+=0.25f;
            }
            over(&p,1,0.94f,0.82f,coverage);
        }
        if(text){
            unsigned glow=0;
            for(int yy=y-1;yy<=y+1;++yy)for(int xx=x-1;xx<=x+1;++xx)
                if(xx>=0&&yy>=0&&xx<PSS_ART_WIDTH&&yy<PSS_ART_HEIGHT)glow+=text[yy*PSS_ART_WIDTH+xx];
            over(&p,1,0.44f,0.07f,(float)glow/(9*255)*0.55f);
            over(&p,1,0.95f,0.85f,text[y*PSS_ART_WIDTH+x]/255.0f);
        }
        unsigned a=byte(p.a),r=byte(p.r),g=byte(p.g),b=byte(p.b);
        out[y*PSS_ART_WIDTH+x]=(a<<24)|(r<<16)|(g<<8)|b;
    }
}
