#include "ats_state.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
void check(bool b) {if(!b)std::abort();}
int main(){
    ats::Status s{};
    const char* valid[]={"H0,0","H0,1","H70380,1","H31536000,1"};
    for(auto p:valid)check(ats::parse_status(p,std::strlen(p),s));
    const char* invalid[]={"H31536001,1","H99999999999999999999999999,1","H-1,1","H1,2","H,1","H10,1x","H10,1\n","H1,","M0","H1,1,1","H 1,1","H1,1\r"};
    for(auto p:invalid)check(!ats::parse_status(p,std::strlen(p),s));
    check(!ats::parse_status(nullptr,4,s));
    ats::State t;t.update({70380,true},1000);check(t.visible());check(t.remaining(2000)==70379);
    check(t.remaining(0)==70380);check(!t.stale(6000));check(t.stale(6001));
    t.update({1,true},1000);check(t.remaining(2000)==0);check(t.remaining(UINT64_MAX)==0);
    t.hide();check(!t.visible());t.update({77,false},1000);check(!t.visible());check(t.remaining(99999)==77);
    std::puts("PASS ATS HUD: strict framing/overflow/countdown/expiry/stale/hide/pause");
}
