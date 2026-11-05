#ifndef PSS_NAVIGATION_H
#define PSS_NAVIGATION_H
#include <stdint.h>
typedef struct { int x,y; } PssPoint;
enum { PSS_NAV_PATH_MAX=64, PSS_NAV_EDGES=4096 };
typedef struct {PssPoint points[PSS_NAV_PATH_MAX];unsigned count;} PssNavPath;
typedef struct {uint32_t from,to,when;int used;} PssNavEdge;
typedef struct {PssNavEdge edges[PSS_NAV_EDGES];int map;} PssNavCache;
int PssNavToInternal(PssPoint logical,PssPoint* internal);
int PssNavToLogical(PssPoint internal,PssPoint* logical);
void PssNavClear(PssNavCache* cache,int map);
void PssNavAdd(PssNavCache* cache,uint32_t now,const PssNavPath* path);
int PssNavHas(const PssNavCache* cache,uint32_t now,int map,int x,int y,int nx,int ny);
void PssNavRemove(PssNavCache* cache,int x,int y,int nx,int ny);
#endif
