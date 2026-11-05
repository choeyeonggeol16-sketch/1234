#include "PssNavigation.h"
#include <assert.h>
#include <limits.h>
#include <stdio.h>
static PssNavCache cache;
int main(void){
    PssPoint raw,p;
    for(int x=0;x<=65535;x+=257){
        assert(PssNavToInternal((PssPoint){x,32750},&raw));
        assert(raw.x==2*x-32768&&raw.y==32750);
        assert(PssNavToLogical(raw,&p)&&p.x==x&&p.y==32750);
        raw.x++;assert(PssNavToLogical(raw,&p)&&p.x==x);
    }
    assert(!PssNavToInternal((PssPoint){INT_MIN,0},&raw));
    assert(!PssNavToLogical((PssPoint){INT_MAX,0},&p));
    assert(!PssNavToLogical((PssPoint){-32769,0},&p));
    PssNavClear(&cache,4);
    PssNavPath route={{{33000,32750},{33001,32750},{33002,32751}},3};
    PssNavAdd(&cache,1000,&route);
    assert(PssNavHas(&cache,1001,4,33000,32750,33001,32750));
    assert(!PssNavHas(&cache,1001,4,33001,32750,33000,32750));
    assert(!PssNavHas(&cache,1001,5,33000,32750,33001,32750));
    assert(!PssNavHas(&cache,4000,4,33000,32750,33001,32750));
    assert(!PssNavHas(&cache,1001,4,INT_MIN,0,INT_MAX,0));
    PssNavRemove(&cache,33000,32750,33001,32750);
    assert(!PssNavHas(&cache,1001,4,33000,32750,33001,32750));
    PssNavAdd(&cache,UINT32_MAX-100,&route);
    assert(PssNavHas(&cache,100,4,33000,32750,33001,32750));
    assert(!PssNavHas(&cache,3000,4,33000,32750,33001,32750));
    PssNavClear(&cache,5);assert(!PssNavHas(&cache,100,5,33000,32750,33001,32750));
    PssNavPath segment={{{100,100},{101,100},{102,100},{103,100}},4};
    assert(PssNavSegmentMatches(&segment,(PssPoint){100,100},(PssPoint){103,100},3));
    assert(!PssNavSegmentMatches(&segment,(PssPoint){100,100},(PssPoint){103,100},2));
    segment.points[2].y=101;
    assert(!PssNavSegmentMatches(&segment,(PssPoint){100,100},(PssPoint){103,100},3));
    segment.points[2].y=100;segment.count=3;
    assert(!PssNavSegmentMatches(&segment,(PssPoint){100,100},(PssPoint){103,100},3));
    puts("PASS native segment validation rejects turns, detours, partial routes and excessive stride");
    puts("PASS normalized/raw coordinates, odd X rounding, bounds, directed/map-scoped edges, invalidation, expiry and clock rollover");
}
