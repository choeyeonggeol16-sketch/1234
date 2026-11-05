#include "vendor/roam/RoamCore.h"
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <set>
#include <utility>
struct World {int x=64,y=64,commands=0,reversals=0,dx=0,dy=0;std::set<std::pair<int,int>> sectors;};
static int __cdecl step(void*,int,int x,int y,int nx,int ny){return nx>=0&&nx<128&&ny>=0&&ny<128&&std::abs(nx-x)<=1&&std::abs(ny-y)<=1&&(x!=nx||y!=ny);}
static int __cdecl move(void* p,int,int x,int y){
 auto& w=*static_cast<World*>(p);int dx=(x>w.x)-(x<w.x),dy=(y>w.y)-(y<w.y);
 if(w.commands&&dx==-w.dx&&dy==-w.dy)++w.reversals;
 w.dx=dx;w.dy=dy;w.x=x;w.y=y;++w.commands;w.sectors.insert({x/16,y/16});return 1;
}
int main(){
 World w;SRConfig cfg{20,1024,300,2000,12345};SRHost h{&w,step,move};
 SRState* s=sr_create(&cfg);assert(s);sr_set_lookahead(s,3);
 SRInput i{1000,0,4,64,64,1,1,0,0};
 for(int n=0;n<2000;++n){i.now_ms+=100;i.x=w.x;i.y=w.y;assert(sr_tick(s,&i,&h)!=SR_INVALID);}
 printf("coverage sectors=%zu commands=%d reversals=%d\n",w.sectors.size(),w.commands,w.reversals);
#ifndef PSS_COVERAGE_BASELINE
 assert(w.sectors.size()>=24);assert(w.reversals<w.commands/8);
 puts("PASS sector coverage and direction continuity across a rolling search horizon");
#endif
 sr_destroy(s);
}
