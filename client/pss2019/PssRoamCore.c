#include "PssRoamCore.h"
#include <string.h>

static const int dx[8]={0,1,2,1,0,-1,-2,-1};
static const int dy[8]={-2,-1,0,1,2,1,0,-1};
static int same(PssPoint a,PssPoint b){return a.x==b.x&&a.y==b.y;}
static int distance(PssPoint a,PssPoint b){
    int x=a.x-b.x,y=a.y-b.y;
    if(x<0)x=-x;
    if(y<0)y=-y;
    return x>y?x:y;
}
static int sign(int n){return n>0?1:n<0?-1:0;}
static uint32_t random_next(PssRoamCore* s){
    uint32_t n=s->random;n^=n<<13;n^=n>>17;n^=n<<5;
    return s->random=n;
}
void PssRoamCoreReset(PssRoamCore* s){memset(s,0,sizeof(*s));}
void PssRoamCorePause(PssRoamCore* s,uint32_t now){
    s->moving=0;s->route.count=0;s->route_index=0;
    s->since=s->last_idle=s->last_command=now;
}
static void remember(PssRoamCore* s,PssPoint p){
    s->visited[s->visited_next]=p;
    s->visited_next=(s->visited_next+1)%PSS_ROAM_HISTORY;
    if(s->visited_count<PSS_ROAM_HISTORY)++s->visited_count;
}
static void begin(PssRoamCore* s,uint32_t now,PssPoint p,uint32_t seed){
    PssRoamCoreReset(s);s->initialized=1;s->position=p;
    s->since=s->last_idle=s->last_command=s->progress=now;
    s->random=(seed^now)|1U;s->heading=(int)(random_next(s)&7);
    remember(s,p);
}
static void block(PssRoamCore* s,uint32_t now,PssPoint from,PssPoint to,int radius){
    PssBlocked* b=&s->blocked[s->blocked_next];
    b->from=from;b->point=to;b->when=now;b->valid=1;b->radius=radius;
    s->blocked_next=(s->blocked_next+1)%PSS_ROAM_BLOCKED;
}
static int cached_block(const PssRoamCore* s,uint32_t now,PssPoint p){
    for(unsigned j=0;j<PSS_ROAM_BLOCKED;++j){
        const PssBlocked* b=&s->blocked[j];
        if(b->valid&&now-b->when<12000U&&distance(p,b->point)<=b->radius)return 1;
    }
    return 0;
}
static int bad_first_steps(const PssRoamCore* s,uint32_t now,PssPoint from,const PssRoamPath* path){
    for(unsigned j=0;j<PSS_ROAM_BLOCKED;++j){
        const PssBlocked* b=&s->blocked[j];
        if(!b->valid||!b->radius||now-b->when>=12000U||distance(from,b->from)>4)continue;
        int initial=distance(from,b->point);
        for(unsigned i=0;i<path->count&&i<6;++i)
            if(distance(path->points[i],b->point)<initial&&
               distance(path->points[i],b->point)<=b->radius)return 1;
    }
    return 0;
}
static void waypoint(PssRoamCore* s,uint32_t now,PssPoint p){
    while(s->route_index<s->route.count&&same(s->route.points[s->route_index],p))++s->route_index;
    if(s->route_index>=s->route.count){s->moving=0;s->failures=0;return;}
    unsigned i=s->route_index;
    int vx=sign(s->route.points[i].x-p.x),vy=sign(s->route.points[i].y-p.y);
    /* Advance on a straight segment only; never skip a native path corner. */
    for(unsigned n=0;n<2&&i+1<s->route.count;++n){
        PssPoint a=s->route.points[i],b=s->route.points[i+1];
        if(sign(b.x-a.x)!=vx||sign(b.y-a.y)!=vy)break;
        ++i;
    }
    s->route_index=i;s->goal=s->route.points[i];
    s->best_distance=distance(p,s->goal);s->progress=now;
}
typedef struct{PssPoint point;int score,heading;} Candidate;
static int choose(PssRoamCore* s,const PssRoamConfig* c,uint32_t now,PssPoint p,
                  PssRoamQuery query,void* context){
    if(!query)return 0;
    unsigned max=c->step_tiles;
    if(max<1)max=1;
    if(max>16)max=16;
    const unsigned strides[]={max,max>1?max/2:1,1};
    Candidate candidates[24];unsigned count=0;
    for(unsigned level=0;level<3;++level){
        if(level&&strides[level]==strides[level-1])continue;
        for(int offset=0;offset<8;++offset){
            int dir=(s->heading+offset)&7;
            PssPoint q={p.x+dx[dir]*(int)strides[level],p.y+dy[dir]*(int)strides[level]};
            if(q.x < -1048576||q.x>1048576||q.y < -1048576||q.y>1048576||cached_block(s,now,q))continue;
            int turn=offset<4?offset:8-offset;
            int score=turn*8+(int)level*32+(int)(random_next(s)&3);
            for(unsigned j=0;j<s->visited_count;++j)
                if(distance(q,s->visited[j])<=3)score+=48;
            unsigned i=count++;
            while(i&&candidates[i-1].score>score){candidates[i]=candidates[i-1];--i;}
            candidates[i]=(Candidate){q,score,dir};
        }
    }
    unsigned queries=0;
    for(unsigned i=0;i<count&&queries<12;++i){
        PssRoamPath route={0};++queries;++s->path_queries;
        int result=query(context,p,candidates[i].point,&route);
        if(result<0)return 0;
        if(!result||!route.count||route.count>PSS_ROAM_PATH_MAX||
           !same(route.points[route.count-1],candidates[i].point)||
           bad_first_steps(s,now,p,&route)){
            ++s->rejected_paths;block(s,now,p,candidates[i].point,0);continue;
        }
        s->route=route;s->route_index=0;s->destination=candidates[i].point;
        s->heading=candidates[i].heading;s->moving=1;s->goal_since=now;
        waypoint(s,now,p);
        if(s->moving)return 1;
    }
    s->heading=(s->heading+2)&7;
    return 0;
}
int PssRoamCorePlan(PssRoamCore* s,const PssRoamConfig* c,uint32_t now,PssPoint p,
                    uint32_t seed,PssPoint* out,PssRoamQuery query,void* context){
    if(!s->initialized||(s->moving&&distance(p,s->position)>16)){
        begin(s,now,p,seed);return 0;
    }
    if(now-s->last_idle>600U)PssRoamCorePause(s,now);
    s->last_idle=now;
    if(!same(p,s->position)){
        s->position=p;remember(s,p);
        int d=distance(p,s->goal);
        /* Wall jitter must not reset the timer unless a waypoint gets closer. */
        if(s->moving&&d<s->best_distance){s->best_distance=d;s->progress=now;}
    }
    if(s->moving){
        for(unsigned i=s->route_index;i<s->route.count;++i)
            if(same(p,s->route.points[i])){s->route_index=i+1;waypoint(s,now,p);break;}
    }
    if(now-s->since<c->idle_ms||now-s->last_command<c->command_ms)return 0;
    if(s->moving&&(now-s->progress>=c->stuck_ms||now-s->goal_since>=30000U)){
        block(s,now,p,s->goal,2);++s->blocked_count;++s->failures;
        s->moving=0;s->route.count=0;s->heading=(s->heading+2+(int)(random_next(s)&1))&7;
        if(s->failures>=8){s->failures=0;s->since=s->last_command=now;return 0;}
    }
    if(!s->moving&&!choose(s,c,now,p,query,context)){
        s->last_command=now;s->since=now;return 0;
    }
    s->last_command=now;*out=s->goal;return 1;
}
