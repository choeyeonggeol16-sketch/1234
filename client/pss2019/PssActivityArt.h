#ifndef PSS_ACTIVITY_ART_H
#define PSS_ACTIVITY_ART_H
#include <stdint.h>
enum {PSS_ART_WIDTH=144,PSS_ART_HEIGHT=60,PSS_ART_FRAMES=48,PSS_ART_FRAME_MS=40};
/* Premultiplied ARGB. The supplied mask is the static white text coverage. */
void PssActivityArtFrame(uint32_t* out,const uint8_t* text,unsigned frame);
#endif
