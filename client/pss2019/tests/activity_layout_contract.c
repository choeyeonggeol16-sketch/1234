#include "PssActivityLayout.h"
#include <assert.h>
#include <stdio.h>
static void same(RECT actual,RECT expected){assert(EqualRect(&actual,&expected));}
int main(void){
 RECT client={100,50,900,650},target,out;
 assert(PssActivityTarget(&client,144,60,0,20,&target));same(target,(RECT){428,370,572,430});
 RECT view={0,0,800,600},destination=client;
 assert(PssActivityMap(&target,&view,&destination,&out));same(out,(RECT){328,320,472,380});
 // A 1024x768 allocation must not alter placement when only 800x600 is copied.
 RECT buffer={0,0,1024,768};
 assert(out.left!=(buffer.right-144)/2&&out.top!=buffer.bottom/2+20);
 RECT crop={32,48,832,648};
 assert(PssActivityMap(&target,&crop,&destination,&out));same(out,(RECT){360,368,504,428});
 RECT small_client={100,50,500,350};
 assert(PssActivityTarget(&small_client,144,60,0,20,&target));
 assert(PssActivityMap(&target,&view,&small_client,&out));same(out,(RECT){256,340,544,460});
 // Map back to screen: same location and physical size as the layered fallback.
 RECT projected;assert(PssActivityMap(&out,&small_client,&view,&projected));same(projected,target);
 RECT negative={-900,80,-100,680};
 assert(PssActivityTarget(&negative,144,60,12,-8,&target));
 assert(PssActivityMap(&target,&view,&negative,&out));same(out,(RECT){340,292,484,352});
 RECT hud={100,500,900,650};assert(!PssActivityMap(&target,&view,&hud,&out));
 RECT empty={0};assert(!PssActivityMap(&target,&empty,&destination,&out));
 assert(!PssActivityTarget(&empty,144,60,0,20,&target));
 puts("PASS shared placement: 1024x768 buffer / 800x600 crop, nonzero source origin, window movement, stretched presentation, negative screen origin and rejecting unrelated HUD copies");
}
