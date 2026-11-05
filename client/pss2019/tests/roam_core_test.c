#include "PssRoamCore.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

static int clear_path(void* context,PssPoint from,PssPoint to,PssRoamPath* path) {
    (void)context;path->count=2;path->points[0]=from;path->points[1]=to;return 1;
}
static int test_plan(PssRoamCore* s,const PssRoamConfig* c,uint32_t now,PssPoint p,uint32_t seed,PssPoint* out) {
    return PssRoamCorePlan(s,c,now,p,seed,out,clear_path,0);
}
static const PssRoamConfig config={2,300,750,1500};
static int equal(PssPoint a,PssPoint b){return a.x==b.x&&a.y==b.y;}
static PssPoint first_goal(PssRoamCore* s,uint32_t start,PssPoint p) {
    PssPoint goal={0,0};
    assert(!test_plan(s,&config,start,p,19,&goal));
    for(unsigned t=100;t<800;t+=100)assert(!test_plan(s,&config,start+t,p,19,&goal));
    assert(test_plan(s,&config,start+800,p,19,&goal));
    assert(!equal(p,goal));
    return goal;
}
int main(void) {
    PssRoamCore state={0};PssPoint p={65590,32750},next;
    PssPoint goal=first_goal(&state,1000,p);
    assert(((goal.x-p.x+goal.y-p.y)&1)==0);
    assert(!test_plan(&state,&config,1900,p,19,&next));
    assert(!test_plan(&state,&config,2000,p,19,&next));
    assert(test_plan(&state,&config,2100,p,19,&next)&&equal(next,goal));
    for(uint32_t now=2200;now<=3300;now+=100)test_plan(&state,&config,now,p,19,&next);
    assert(state.blocked_count==1);
    assert(!equal(state.goal,goal));
    puts("PASS idle delay, native coordinate range/parity, throttling and blocked-goal replacement");

    PssRoamCoreReset(&state);goal=first_goal(&state,10000,p);
    unsigned commands=0;
    for(uint32_t now=10900;now<41000;now+=100)
        commands+=(unsigned)test_plan(&state,&config,now,p,19,&next);
    assert(state.blocked_count>=8&&commands<101);
    puts("PASS surrounded character keeps trying at a bounded rate");

    PssRoamCoreReset(&state);goal=first_goal(&state,50000,p);
    for(uint32_t now=50900;now<=51500;now+=100) {
        PssPoint at=now<51200?p:goal;
        test_plan(&state,&config,now,at,19,&next);
    }
    assert(equal(state.position,goal)&&state.blocked_count==0);
    assert(!equal(state.goal,goal));
    puts("PASS arrival continues walking instead of stopping at the first destination");

    assert(!test_plan(&state,&config,53000,goal,19,&next));
    assert(!state.moving);
    assert(!test_plan(&state,&config,53100,(PssPoint){70000,40000},19,&next));
    assert(equal(state.position,(PssPoint){70000,40000})&&!state.moving);
    puts("PASS combat/idle gap and same-map teleport discard stale goals");

    PssRoamCoreReset(&state);
    goal=first_goal(&state,UINT32_MAX-500,p);
    assert(!equal(goal,p));
    puts("PASS tick counter wraparound");

    PssRoamCoreReset(&state);
    for(uint32_t now=100;now<3600100;now+=100) {
        if(test_plan(&state,&config,now,p,19,&next))p=next;
        assert(state.visited_count<=PSS_ROAM_HISTORY&&state.visited_next<PSS_ROAM_HISTORY&&state.blocked_next<PSS_ROAM_BLOCKED);
    }
    puts("PASS one-hour simulated roaming uses fixed-size history without allocations");
    return 0;
}
