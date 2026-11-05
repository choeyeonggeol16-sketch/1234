#include "PssNavigation.h"
#include <string.h>
int PssNavToInternal(PssPoint p,PssPoint* out){
    if(p.x<0||p.x>65535||p.y<0||p.y>65535)return 0;
    out->x=(p.x*2-0x8000)&~1;out->y=p.y;return 1;
}
int PssNavToLogical(PssPoint p,PssPoint* out){
    int64_t x=(int64_t)p.x+0x8000;
    if(x<0||x>131071||p.y<0||p.y>65535)return 0;
    out->x=(int)(x/2);out->y=p.y;return 1;
}
static int adjacent(int x,int y,int nx,int ny){
    if(x<0||x>65535||y<0||y>65535||nx<0||nx>65535||ny<0||ny>65535)return 0;
    int dx=nx-x,dy=ny-y;
    return dx>=-1&&dx<=1&&dy>=-1&&dy<=1&&(dx||dy);
}
static uint32_t tile(int x,int y){return ((uint32_t)x<<16)|(uint32_t)y;}
static unsigned hash(uint32_t from,uint32_t to){
    uint32_t n=from*0x9e3779b1U^to*0x85ebca6bU;n^=n>>16;
    return n&(PSS_NAV_EDGES-1);
}
void PssNavClear(PssNavCache* c,int map){memset(c,0,sizeof(*c));c->map=map;}
static void add(PssNavCache* c,uint32_t now,PssPoint a,PssPoint b){
    if(!adjacent(a.x,a.y,b.x,b.y))return;
    uint32_t from=tile(a.x,a.y),to=tile(b.x,b.y);unsigned at=hash(from,to),slot=at;
    uint32_t age=0;
    for(unsigned i=0;i<8;++i){
        unsigned n=(at+i)&(PSS_NAV_EDGES-1);PssNavEdge* e=&c->edges[n];
        if(!e->used||(e->from==from&&e->to==to)){slot=n;break;}
        if(now-e->when>=age){age=now-e->when;slot=n;}
    }
    c->edges[slot]=(PssNavEdge){from,to,now,1};
}
void PssNavAdd(PssNavCache* c,uint32_t now,const PssNavPath* p){
    if(!p->count||p->count>PSS_NAV_PATH_MAX)return;
    for(unsigned i=1;i<p->count;++i)add(c,now,p->points[i-1],p->points[i]);
}
int PssNavHas(const PssNavCache* c,uint32_t now,int map,int x,int y,int nx,int ny){
    if(map!=c->map||!adjacent(x,y,nx,ny))return 0;
    uint32_t from=tile(x,y),to=tile(nx,ny);unsigned at=hash(from,to);
    for(unsigned i=0;i<8;++i){
        const PssNavEdge* e=&c->edges[(at+i)&(PSS_NAV_EDGES-1)];
        if(e->used&&e->from==from&&e->to==to&&now-e->when<3000U)return 1;
    }
    return 0;
}
void PssNavRemove(PssNavCache* c,int x,int y,int nx,int ny){
    uint32_t from=tile(x,y),to=tile(nx,ny);unsigned at=hash(from,to);
    for(unsigned i=0;i<8;++i){
        PssNavEdge* e=&c->edges[(at+i)&(PSS_NAV_EDGES-1)];
        if(e->used&&e->from==from&&e->to==to)e->used=0;
    }
}

int PssNavSegmentMatches(const PssNavPath* p,PssPoint from,PssPoint to,unsigned limit){
    PssPoint raw;
    if(!p||!PssNavToInternal(from,&raw)||!PssNavToInternal(to,&raw))return 0;
    int x=to.x-from.x,y=to.y-from.y,ax=x<0?-x:x,ay=y<0?-y:y;
    unsigned steps=(unsigned)(ax>ay?ax:ay);
    if(!steps||steps>limit||steps>=PSS_NAV_PATH_MAX||p->count!=steps+1||
       (ax&&ay&&ax!=ay))return 0;
    int dx=x>0?1:x<0?-1:0,dy=y>0?1:y<0?-1:0;
    for(unsigned n=0;n<=steps;++n)
        if(p->points[n].x!=from.x+dx*(int)n||p->points[n].y!=from.y+dy*(int)n)return 0;
    return 1;
}
