#ifndef PSS_VITALS_H
#define PSS_VITALS_H
#include <windows.h>
#include <stdint.h>
typedef struct {int32_t hp,max_hp,mp,max_mp;} PssVitals;
BOOL PssVitalsRead(PssVitals* out);
void PssVitalsReset(void);
#endif
