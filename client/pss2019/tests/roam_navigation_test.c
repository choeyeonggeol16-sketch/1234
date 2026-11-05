#include "PssRoamCore.h"
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

enum { SIDE=40, CELLS=SIDE*SIDE };
static unsigned char walls[CELLS];
static int parents[CELLS],queue[CELLS];
static PssPoint raw(int x,int y){return (PssPoint){65590+x-y,32750+x+y};}
static int tile(PssPoint p,int* x,int* y){
    int a=p.x-65590,b=p.y-32750;
    if((a+b)&1)return 0;
    *x=(a+b)/2;*y=(b-a)/2;
    return *x>=0&&*x<SIDE&&*y>=0&&*y<SIDE;
}
static int open_cell(int x,int y){return x>=0&&x<SIDE&&y>=0&&y<SIDE&&!walls[y*SIDE+x];}
static void empty_map(void){
    memset(walls,0,sizeof(walls));
    for(int i=0;i<SIDE;++i)walls[i]=walls[(SIDE-1)*SIDE+i]=walls[i*SIDE]=walls[i*SIDE+SIDE-1]=1;
}
static int route(void* context,PssPoint from,PssPoint to,PssRoamPath* out){
    (void)context;int ax,ay,bx,by;
    out->count=0;
    if(!tile(from,&ax,&ay)||!tile(to,&bx,&by)||!open_cell(bx,by))return 0;
    for(int i=0;i<CELLS;++i)parents[i]=-1;
    int start=ay*SIDE+ax,target=by*SIDE+bx,read=0,write=1;
    queue[0]=start;parents[start]=start;
    static const int dx[8]={0,1,1,1,0,-1,-1,-1},dy[8]={-1,-1,0,1,1,1,0,-1};
    while(read<write&&parents[target]<0){
        int at=queue[read++],x=at%SIDE,y=at/SIDE;
        for(int i=0;i<8;++i){
            int nx=x+dx[i],ny=y+dy[i];
            if(!open_cell(nx,ny)||(dx[i]&&dy[i]&&(!open_cell(x+dx[i],y)||!open_cell(x,y+dy[i]))))continue;
            int next=ny*SIDE+nx;
            if(parents[next]>=0)continue;
            parents[next]=at;queue[write++]=next;
        }
    }
    if(parents[target]<0)return 0;
    PssPoint reverse[PSS_ROAM_PATH_MAX];unsigned count=0;
    for(int at=target;;at=parents[at]){
        if(count==PSS_ROAM_PATH_MAX)return 0;
        reverse[count++]=raw(at%SIDE,at/SIDE);
        if(at==start)break;
    }
    out->count=count;
    for(unsigned i=0;i<count;++i)out->points[i]=reverse[count-1-i];
    return 1;
}
static int same(PssPoint a,PssPoint b){return a.x==b.x&&a.y==b.y;}
static int sign(int n){return n>0?1:n<0?-1:0;}
static int clear_segment(PssPoint a,PssPoint b){
    int ax,ay,bx,by;
    if(!tile(a,&ax,&ay)||!tile(b,&bx,&by))return 0;
    int sx=sign(bx-ax),sy=sign(by-ay),steps=0;
    while(ax!=bx||ay!=by){
        if(++steps>10)return 0;
        int nx=ax+(ax!=bx?sx:0),ny=ay+(ay!=by?sy:0);
        if(!open_cell(nx,ny)||(nx!=ax&&ny!=ay&&(!open_cell(nx,ay)||!open_cell(ax,ny))))return 0;
        ax=nx;ay=ny;
    }
    return 1;
}
static void detour(int u_shape){
    empty_map();
    if(!u_shape){for(int y=1;y<SIDE-1;++y)if(y<8||y>10)walls[y*SIDE+20]=1;}
    else {
        for(int y=10;y<=25;++y)walls[y*SIDE+15]=walls[y*SIDE+25]=1;
        for(int x=15;x<=25;++x)walls[10*SIDE+x]=1;
    }
    PssRoamConfig c={8,300,500,1000};PssRoamCore s={0};
    PssPoint p=u_shape?raw(20,20):raw(15,20),goal,destination={0};
    assert(!PssRoamCorePlan(&s,&c,1000,p,19,&goal,route,0));
    s.heading=u_shape?7:3;s.random=1;
    int reached=0,passed_opening=0;unsigned commands=0;
    for(uint32_t now=1100;now<25000;++now){
        if(now%100)continue;
        unsigned before=s.path_queries;
        if(PssRoamCorePlan(&s,&c,now,p,19,&goal,route,0)){
            if(!commands)destination=s.destination;
            assert(clear_segment(p,goal));
            PssRoamPath steps;
            assert(route(0,p,goal,&steps)&&steps.count>1);
            p=steps.points[1];++commands;
        }
        assert(s.path_queries-before<=12);
        int x,y;assert(tile(p,&x,&y));
        if(u_shape?y>=26:(x==20&&y>=8&&y<=10))passed_opening=1;
        if(commands&&same(p,destination)){reached=1;break;}
    }
    assert(reached&&passed_opening&&s.blocked_count==0);
    printf("PASS %s: follows the opening/corners, never issues a wall-crossing segment (%u commands)\n",
           u_shape?"U-shaped dead end":"wall with a distant doorway",commands);
}
static int clear_path(void* context,PssPoint from,PssPoint to,PssRoamPath* path){
    (void)context;path->count=2;path->points[0]=from;path->points[1]=to;return 1;
}
static int unavailable(void* context,PssPoint from,PssPoint to,PssRoamPath* path){
    (void)context;(void)from;(void)to;(void)path;return -1;
}
static int unreachable(void* context,PssPoint from,PssPoint to,PssRoamPath* path){
    (void)context;(void)from;(void)to;path->count=0;return 0;
}
int main(void){
    detour(0);detour(1);
    PssRoamConfig c={8,300,500,1000};PssRoamCore s={0};PssPoint p=raw(15,15),goal;
    PssRoamCorePlan(&s,&c,1000,p,19,&goal,clear_path,0);s.heading=0;
    for(uint32_t now=1100;now<=1500;now+=100)PssRoamCorePlan(&s,&c,now,p,19,&goal,clear_path,0);
    assert(s.moving);
    for(uint32_t now=1600;now<=2900;now+=100){
        PssPoint jitter=p;jitter.x+=(now/100)&1?2:0;
        PssRoamCorePlan(&s,&c,now,jitter,19,&goal,clear_path,0);
    }
    assert(s.blocked_count>0);
    puts("PASS lateral wall jitter is a stall, not forward progress");
    unsigned history=s.visited_count,blocked=s.blocked_count;
    PssRoamCorePause(&s,3000);
    assert(!s.moving&&s.visited_count==history&&s.blocked_count==blocked);
    p.x+=10;PssRoamCorePlan(&s,&c,9000,p,19,&goal,clear_path,0);
    assert(s.visited_count>=history&&s.blocked_count==blocked);
    puts("PASS combat pauses preserve visited and blocked-location memory");
    PssRoamCoreReset(&s);
    for(uint32_t now=1000;now<10000;now+=100)
        assert(!PssRoamCorePlan(&s,&c,now,p,19,&goal,unavailable,0));
    assert(s.path_queries<25);
    puts("PASS missing native navigation never produces blind movement");
    PssRoamCoreReset(&s);
    for(uint32_t now=1000;now<10000;now+=100){
        unsigned before=s.path_queries;
        assert(!PssRoamCorePlan(&s,&c,now,p,19,&goal,unreachable,0));
        assert(s.path_queries-before<=12);
    }
    assert(s.path_queries<200);
    puts("PASS unreachable destinations are cached and path queries stay bounded");
    return 0;
}
