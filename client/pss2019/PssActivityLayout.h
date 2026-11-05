#ifndef PSS_ACTIVITY_LAYOUT_H
#define PSS_ACTIVITY_LAYOUT_H
#include <windows.h>
#include <stdint.h>
/* One screen-space target for both the window and the presented image. */
static inline BOOL PssActivityTarget(const RECT* client,int width,int height,int dx,int dy,RECT* out){
    if(!client||!out||client->right<=client->left||client->bottom<=client->top||width<=0||height<=0)return FALSE;
    out->left=client->left+(client->right-client->left-width)/2+dx;
    out->top=client->top+(client->bottom-client->top)/2+dy;
    out->right=out->left+width;out->bottom=out->top+height;
    return TRUE;
}
static inline BOOL PssActivityMap(const RECT* target,const RECT* source,const RECT* destination,RECT* out){
    if(!target||!source||!destination||!out)return FALSE;
    int64_t sw=(int64_t)source->right-source->left,sh=(int64_t)source->bottom-source->top;
    int64_t dw=(int64_t)destination->right-destination->left,dh=(int64_t)destination->bottom-destination->top;
    if(sw<=0||sh<=0||dw<=0||dh<=0||sw>65536||sh>65536||dw>65536||dh>65536||
       target->right<=target->left||target->bottom<=target->top||
       target->left<destination->left||target->top<destination->top||
       target->right>destination->right||target->bottom>destination->bottom)return FALSE;
    out->left=source->left+(LONG)(((int64_t)target->left-destination->left)*sw/dw);
    out->top=source->top+(LONG)(((int64_t)target->top-destination->top)*sh/dh);
    out->right=source->left+(LONG)(((int64_t)target->right-destination->left)*sw/dw);
    out->bottom=source->top+(LONG)(((int64_t)target->bottom-destination->top)*sh/dh);
    return out->right>out->left&&out->bottom>out->top;
}
#endif
