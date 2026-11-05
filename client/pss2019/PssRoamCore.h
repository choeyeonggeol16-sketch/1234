#ifndef PSS_ROAM_CORE_H
#define PSS_ROAM_CORE_H
#include <stdint.h>

typedef struct { int x, y; } PssPoint;
typedef struct {
    unsigned step_tiles, command_ms, idle_ms, stuck_ms;
} PssRoamConfig;
enum { PSS_ROAM_PATH_MAX=64, PSS_ROAM_HISTORY=96, PSS_ROAM_BLOCKED=48 };
typedef struct { PssPoint points[PSS_ROAM_PATH_MAX]; unsigned count; } PssRoamPath;
typedef int (*PssRoamQuery)(void*,PssPoint,PssPoint,PssRoamPath*);
typedef struct { PssPoint from,point; uint32_t when; int valid,radius; } PssBlocked;
typedef struct {
    int initialized, moving, heading, failures, best_distance;
    PssPoint position, goal, destination, visited[PSS_ROAM_HISTORY];
    unsigned visited_count, visited_next, blocked_next;
    PssBlocked blocked[PSS_ROAM_BLOCKED];
    PssRoamPath route;
    unsigned route_index;
    uint32_t since, last_idle, last_command, progress, goal_since, random;
    unsigned blocked_count,rejected_paths,path_queries;
} PssRoamCore;

void PssRoamCoreReset(PssRoamCore* state);
void PssRoamCorePause(PssRoamCore* state,uint32_t now);
/* Called only by the native Battle's idle leaf. Returns a goal when due. */
int PssRoamCorePlan(PssRoamCore* state, const PssRoamConfig* config,
                    uint32_t now, PssPoint position, uint32_t seed, PssPoint* goal,
                    PssRoamQuery query,void* query_context);
#endif
