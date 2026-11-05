#include "PssRoamCore.h"
#include <string.h>
#include <limits.h>

/* Even-parity destinations also work with the client's isometric point
 * representation. Do not assume a native point is a uint16 server tile. */
static const int dx[8] = {0,1,2,1,0,-1,-2,-1};
static const int dy[8] = {-2,-1,0,1,2,1,0,-1};
static int same(PssPoint a, PssPoint b) { return a.x == b.x && a.y == b.y; }
static int distance(PssPoint a, PssPoint b) {
    int x=a.x-b.x, y=a.y-b.y;
    if(x<0)x=-x;
    if(y<0)y=-y;
    return x>y?x:y;
}
static uint32_t next_random(PssRoamCore* s) {
    uint32_t n=s->random;
    n^=n<<13; n^=n>>17; n^=n<<5;
    s->random=n;
    return n;
}
void PssRoamCoreReset(PssRoamCore* s) { memset(s,0,sizeof(*s)); }
static void remember(PssRoamCore* s, PssPoint p) {
    s->visited[s->visited_next]=p;
    s->visited_next=(s->visited_next+1)%24;
    if(s->visited_count<24)++s->visited_count;
}
static void begin(PssRoamCore* s, uint32_t now, PssPoint p, uint32_t seed) {
    PssRoamCoreReset(s);
    s->initialized=1; s->position=p;
    s->since=s->last_idle=s->last_command=s->progress=now;
    s->random=(seed^now)|1U;
    s->heading=(int)(next_random(s)&7);
    remember(s,p);
}
static int choose(PssRoamCore* s, const PssRoamConfig* c, uint32_t now, PssPoint p) {
    unsigned stride=s->failures?1:c->step_tiles;
    int best=INT_MAX, found=0, direction=s->heading;
    PssPoint goal=p;
    for(int offset=0;offset<8;++offset) {
        int dir=(s->heading+offset)&7;
        PssPoint q={p.x+dx[dir]*(int)stride,p.y+dy[dir]*(int)stride};
        if(q.x < -1048576 || q.x > 1048576 || q.y < -1048576 || q.y > 1048576)continue;
        int blocked=0;
        for(unsigned j=0;j<16;++j)
            if(s->blocked[j].valid && now-s->blocked[j].when<10000U &&
               same(s->blocked[j].point,q)) {blocked=1;break;}
        if(blocked)continue;
        int turn=offset<4?offset:8-offset;
        int score=turn*8+(int)(next_random(s)&3);
        for(unsigned j=0;j<s->visited_count;++j)
            if(distance(q,s->visited[j])<=1)score+=40;
        if(score<best) {best=score;goal=q;direction=dir;found=1;}
    }
    if(!found)return 0;
    s->goal=goal; s->heading=direction; s->moving=1;
    s->goal_since=s->progress=now;
    return 1;
}
int PssRoamCorePlan(PssRoamCore* s, const PssRoamConfig* c,
                    uint32_t now, PssPoint p, uint32_t seed, PssPoint* out) {
    if(!s->initialized || now-s->last_idle>600U || distance(p,s->position)>8) {
        begin(s,now,p,seed);
        return 0;
    }
    s->last_idle=now;
    if(!same(p,s->position)) {
        s->position=p; s->progress=now; s->failures=0;
        remember(s,p);
    }
    if(now-s->since<c->idle_ms || now-s->last_command<c->command_ms)return 0;
    if(s->moving && same(p,s->goal))s->moving=0;
    if(s->moving && (now-s->progress>=c->stuck_ms || now-s->goal_since>=6000U)) {
        PssBlocked* b=&s->blocked[s->blocked_next];
        b->point=s->goal;b->when=now;b->valid=1;
        s->blocked_next=(s->blocked_next+1)%16;
        s->moving=0;++s->blocked_count;++s->failures;
        s->heading=(s->heading+2+(int)(next_random(s)&1))&7;
        if(s->failures>=8) {
            /* Surrounded: pause briefly; native target searches keep running. */
            s->failures=0;s->since=now;
            s->last_command=now;
            return 0;
        }
    }
    if(!s->moving && !choose(s,c,now,p)) {
        s->last_command=now;
        return 0;
    }
    s->last_command=now;*out=s->goal;
    return 1;
}
