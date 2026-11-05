#pragma once
#include <stdint.h>
#ifndef _WIN32
#define __cdecl
#endif
#ifdef __cplusplus
#define SRLINK extern "C"
#else
#define SRLINK extern
#endif
#if defined(ROAM_EXPORTS) && defined(_WIN32)
#define SRAPI SRLINK __declspec(dllexport)
#else
#define SRAPI SRLINK
#endif
typedef struct SRConfig { int radius, search_budget, retry_ms, stuck_ms; uint32_t seed; } SRConfig;
typedef struct SRInput { uint64_t now_ms, earliest_move_ms; int map, x, y, enabled, can_move, dead, has_target; } SRInput;
// Coordinates must be normalized logical tiles by the adapter, not screen pixels.
typedef struct SRHost { void* user; int(__cdecl* can_step)(void*,int map,int x,int y,int nx,int ny); int(__cdecl* request_step)(void*,int map,int nx,int ny); } SRHost;
typedef enum SRResult { SR_INVALID=-1, SR_PAUSED=0, SR_STEP=1, SR_WAIT=2, SR_YIELD_TARGET=3, SR_NO_ROUTE=4 } SRResult;
typedef struct SRState SRState;
SRAPI SRState* __cdecl sr_create(const SRConfig* config);
SRAPI void __cdecl sr_destroy(SRState* state);
SRAPI void __cdecl sr_reset(SRState* state);
SRAPI int __cdecl sr_tick(SRState* state,const SRInput* input,const SRHost* host);
