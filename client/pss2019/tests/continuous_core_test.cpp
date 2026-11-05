#include "vendor/roam/RoamCore.h"
#include <cassert>
#include <algorithm>
#include <cstdlib>
#include <cstdio>
struct World {
    int x=0,y=0,gx=0,gy=0,commands=0,largest=0;
    bool moving=false;
};
static bool tile(int x,int y){return (y==0&&x>=0&&x<=14)||(x==14&&y>=0&&y<=10);}
static int __cdecl pass(void*,int,int x,int y,int nx,int ny){
    if(!tile(x,y)||!tile(nx,ny)||std::abs(nx-x)>1||std::abs(ny-y)>1)return 0;
    if(nx!=x&&ny!=y&&(!tile(nx,y)||!tile(x,ny)))return 0;
    return nx!=x||ny!=y;
}
static int sign(int n){return n>0?1:n<0?-1:0;}
static int __cdecl request(void* p,int map,int x,int y){
    World& w=*static_cast<World*>(p);
    int dx=x-w.x,dy=y-w.y,n=(std::max)(std::abs(dx),std::abs(dy));
    assert(n>=1&&n<=3&&(!dx||!dy||std::abs(dx)==std::abs(dy)));
    int px=w.x,py=w.y;
    for(int k=0;k<n;++k){int nx=px+sign(dx),ny=py+sign(dy);assert(pass(p,map,px,py,nx,ny));px=nx;py=ny;}
    w.gx=x;w.gy=y;w.moving=true;++w.commands;w.largest=(std::max)(w.largest,n);return 1;
}
int main(){
    SRConfig cfg{20,1024,300,2000,12345};World w;SRHost h{&w,pass,request};
    SRState* s=sr_create(&cfg);assert(s);sr_set_lookahead(s,3);
    SRInput i{1000,0,4,0,0,1,1,0,0};
    bool vertical=false;int no_command_while_moving=0;
    for(int n=0;n<2000;++n){
        bool was_moving=w.moving;
        if(w.moving){w.x+=sign(w.gx-w.x);w.y+=sign(w.gy-w.y);w.moving=w.x!=w.gx||w.y!=w.gy;}
        int before=w.commands;i.now_ms+=100;i.x=w.x;i.y=w.y;
        int result=sr_tick(s,&i,&h);assert(result!=SR_INVALID);
        if(was_moving&&w.moving&&(w.x!=w.gx||w.y!=w.gy)&&w.commands==before)++no_command_while_moving;
        if(w.y>0)vertical=true;
        assert(tile(w.x,w.y));
    }
    assert(w.largest==3&&vertical&&no_command_while_moving>10);
    std::puts("PASS 3-tile continuous goals, intermediate progress, L-corner routing and no repeated command while moving");

    sr_reset(s);w=World{};i.x=i.y=0;i.now_ms+=1000;
    assert(sr_tick(s,&i,&h)==SR_STEP);int before=w.commands;
    i.now_ms+=2100;assert(sr_tick(s,&i,&h)==SR_NO_ROUTE&&w.commands==before);
    i.now_ms+=300;assert(sr_tick(s,&i,&h)==SR_NO_ROUTE&&w.commands==before);
    std::puts("PASS frozen multi-tile command excludes its first adjacent edge, preventing an immediate same-route retry");

    sr_reset(s);w=World{};i.x=i.y=0;i.now_ms+=3000;
    assert(sr_tick(s,&i,&h)==SR_STEP);before=w.commands;
    i.has_target=1;i.now_ms+=100;assert(sr_tick(s,&i,&h)==SR_YIELD_TARGET&&w.commands==before);
    i.has_target=0;i.can_move=0;assert(sr_tick(s,&i,&h)==SR_PAUSED&&w.commands==before);
    i.can_move=1;i.dead=1;assert(sr_tick(s,&i,&h)==SR_PAUSED&&w.commands==before);
    sr_destroy(s);
    std::puts("PASS target, stun and death still preempt continuous roaming");
}
