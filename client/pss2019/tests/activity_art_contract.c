#include "PssActivityArt.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static uint32_t first[PSS_ART_WIDTH*PSS_ART_HEIGHT],current[PSS_ART_WIDTH*PSS_ART_HEIGHT];
static uint8_t mask[PSS_ART_WIDTH*PSS_ART_HEIGHT];
int main(void){
 for(int x=25;x<115;++x)mask[48*PSS_ART_WIDTH+x]=255;
 PssActivityArtFrame(first,mask,0);unsigned moving=0;
 for(unsigned n=0;n<PSS_ART_FRAMES;++n){
  PssActivityArtFrame(current,mask,n);
  for(int y=0;y<PSS_ART_HEIGHT;++y)for(int x=0;x<PSS_ART_WIDTH;++x){
   unsigned at=y*PSS_ART_WIDTH+x;uint32_t c=current[at];unsigned a=c>>24;
   assert(((c>>16)&255)<=a&&((c>>8)&255)<=a&&(c&255)<=a);
   if(y>=40)assert(c==first[at]);
  }
  assert(current[19*PSS_ART_WIDTH+72]==first[19*PSS_ART_WIDTH+72]); // stationary play triangle
  if(memcmp(current,first,sizeof(first)))++moving;
 }
 assert(moving>40);PssActivityArtFrame(current,mask,PSS_ART_FRAMES);assert(!memcmp(first,current,sizeof(first)));
 assert((first[0]>>24)==0);
 puts("PASS 48-frame ring animation: premultiplied alpha, fixed play triangle and text, transparent corner, moving highlight and seamless cycle; font rasterization is not simulated");
}
