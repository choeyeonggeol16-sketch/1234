#pragma once
#include <stdint.h>
#ifdef ROAM_EXPORTS
#define SRAPI extern "C" __declspec(dllexport)
#else
#define SRAPI extern "C"
#endif
struct SRConfig { int radius, search_budget, retry_ms, stuck_ms; uint32_t seed; };
struct SRInput { uint64_t now_ms, earliest_move_ms; int map, x, y, enabled, can_move, dead, has_target; };
// Coordinates must be normalized logical tiles by the adapter, not screen pixels.
struct SRHost { void* user; int(__cdecl* can_step)(void*,int map,int x,int y,int nx,int ny); int(__cdecl* request_step)(void*,int map,int nx,int ny); };
enum SRResult { SR_INVALID=-1, SR_PAUSED=0, SR_STEP=1, SR_WAIT=2, SR_YIELD_TARGET=3, SR_NO_ROUTE=4 };
struct SRState;
SRAPI SRState* __cdecl sr_create(const SRConfig* config);
SRAPI void __cdecl sr_destroy(SRState* state);
SRAPI void __cdecl sr_reset(SRState* state);
SRAPI int __cdecl sr_tick(SRState* state,const SRInput* input,const SRHost* host);
