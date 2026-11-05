#include "PssVitalsArt.h"
#include <assert.h>
#include <limits.h>
#include <stdio.h>
static uint32_t empty[PSS_VITALS_WIDTH*PSS_VITALS_HEIGHT],full[PSS_VITALS_WIDTH*PSS_VITALS_HEIGHT];
int main(void){
 assert(!PssVitalsFill(10,0,226)&&!PssVitalsFill(-1,100,226));
 assert(PssVitalsFill(50,100,226)==113&&PssVitalsFill(200,100,226)==226);
 assert(PssVitalsFill(INT_MAX-1,INT_MAX,226)==225);
 PssVitalsArt(empty,0,100,0,100);PssVitalsArt(full,100,100,100,100);
 assert(!full[0]&&!empty[0]);
 assert(empty[23*PSS_VITALS_WIDTH+40]!=full[23*PSS_VITALS_WIDTH+40]);
 assert(empty[23*PSS_VITALS_WIDTH+300]!=full[23*PSS_VITALS_WIDTH+300]);
 for(unsigned n=0;n<PSS_VITALS_WIDTH*PSS_VITALS_HEIGHT;++n)assert(!full[n]||(full[n]>>24)==255);
 puts("PASS HP/MP bar art: empty/full/half/over-max/zero-max/overflow-safe fill, independent red/blue changes, opaque panel and transparent outside pixels");
}
