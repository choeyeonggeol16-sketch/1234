#ifndef PSS_ROAM_CORE_H
#define PSS_ROAM_CORE_H
#include <stdint.h>

typedef struct { int x, y; } PssPoint;
typedef struct {
    unsigned step_tiles, command_ms, idle_ms, stuck_ms;
} PssRoamConfig;
typedef struct { PssPoint point; uint32_t when; int valid; } PssBlocked;
typedef struct {
    int initialized, moving, heading, failures;
    PssPoint position, goal, visited[24];
    unsigned visited_count, visited_next, blocked_next;
    PssBlocked blocked[16];
    uint32_t since, last_idle, last_command, progress, goal_since, random;
    unsigned blocked_count;
} PssRoamCore;

void PssRoamCoreReset(PssRoamCore* state);
/* Called only by the native Battle's idle leaf. Returns a goal when due. */
int PssRoamCorePlan(PssRoamCore* state, const PssRoamConfig* config,
                    uint32_t now, PssPoint position, uint32_t seed, PssPoint* goal);
#endif
