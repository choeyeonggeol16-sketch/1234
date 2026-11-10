#pragma once
#include "step_core.h"

namespace nativestep {
// All alternatives stay inside the same 48x24 logical tile. Native inverse
// and resolution roundtrips still validate each individual click at runtime.
constexpr Point kClickOffsets[] = {
    {0,0}, {0,6}, {0,-6}, {12,0}, {-12,0}, {-8,4},
    {8,-4}, {-8,-4}, {-16,0}, {0,-8}
};
constexpr unsigned kClickPointCount=sizeof(kClickOffsets)/sizeof(kClickOffsets[0]);
struct LabelRect { int left,top,right,bottom; };
inline bool label_contains(LabelRect box,int name_length,Point scroll,Point pixel) noexcept {
    // Same signed division and half-open bounds as native 0x472428..0x472493.
    const int offset=(box.right-box.left-name_length*6)/2;
    box.left+=offset-scroll.x;box.right+=offset-scroll.x;
    box.top-=3+scroll.y;box.bottom-=3+scroll.y;
    return pixel.x>=box.left && pixel.x<box.right && pixel.y>=box.top && pixel.y<box.bottom;
}
}
