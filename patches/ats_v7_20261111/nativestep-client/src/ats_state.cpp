#include "ats_state.h"
namespace ats {
bool parse_status(const char* line,size_t length,Status& out) noexcept {
    if(!line || length<4 || length>13 || line[0]!='H') return false;
    uint32_t seconds=0;size_t i=1;
    for(;i<length && line[i]>='0' && line[i]<='9';++i) {
        const uint32_t digit=static_cast<uint32_t>(line[i]-'0');
        if(seconds>(kMaxSeconds-digit)/10) return false;
        seconds=seconds*10+digit;
    }
    if(i==1 || i+2!=length || line[i]!=',' || (line[i+1]!='0' && line[i+1]!='1')) return false;
    out={seconds,line[i+1]=='1'};return true;
}
}
