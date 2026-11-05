#ifndef PSS_VITALS_ART_H
#define PSS_VITALS_ART_H
#include <stdint.h>
enum {PSS_VITALS_WIDTH=784,PSS_VITALS_HEIGHT=80};
unsigned PssVitalsFill(int32_t current,int32_t maximum,unsigned width);
void PssVitalsArt(uint32_t* pixels,int32_t hp,int32_t max_hp,int32_t mp,int32_t max_mp);
#endif
