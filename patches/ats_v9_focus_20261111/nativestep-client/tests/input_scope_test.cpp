#include "input_scope.h"
#include "step_core.h"
#include <cstdio>
#include <cstdlib>
using namespace nativestep;
void check(bool b,const char* reason){if(!b){std::fprintf(stderr,"FAIL %s\n",reason);std::abort();}}
int main(){
    ManualPressScope s;
    check(!s.sample(false,true,true),"idle game");
    check(!s.sample(true,false,false),"outside/background down ignored");
    check(!s.sample(true,true,true),"outside press dragged into client is still outside-owned");
    check(!s.sample(false,true,true),"release outside-owned press");
    check(s.sample(true,true,true),"intentional already-foreground in-client click");
    check(s.sample(true,false,false),"press ownership follows origin, not later cursor movement");
    check(!s.sample(false,false,false),"release clears origin");
    check(!s.sample(true,true,true),"activation-only click does not stop hunt");
    check(!s.sample(false,true,true),"activation release");
    check(s.sample(true,true,true),"next in-client press can stop hunt");
    s.sample(false,true,true);
    check(!s.sample(true,true,false),"title bar/covered client/outside is not a game press");
    StepEngine legacy;legacy.submit(-2,0);legacy.tick(0,false);legacy.submit(2,1);
    legacy.tick(1,true);check(legacy.has_manual_override(),"reproduce V8 global-button misclassification");
    StepEngine fixed;fixed.submit(-2,0);fixed.tick(0,false,false);fixed.submit(2,1);
    check(fixed.tick(1,true,false).type==Action::move_and_press,"outside press does not block authorized background step");
    check(fixed.confirm_press(1),"background step can confirm");
    check(!fixed.has_manual_override(),"outside button never sets manual flag");
    fixed.submit(-1,2);check(fixed.tick(2,true,false).type==Action::release,"background CANCEL still releases");
    fixed.submit(3,3);check(fixed.tick(3,true,false).type==Action::move_and_press,"new offer while outside press is held");
    check(fixed.confirm_press(3),"second background confirm");
    check(fixed.tick(4,true,true).type==Action::release,"actual in-client manual input releases automation");
    check(fixed.has_manual_override(),"intentional manual override retained");
    StepEngine start;start.submit(-2,0);start.submit(1,1);
    check(start.tick(1,true,false).type==Action::none,"initial item/start press must release, even if outside-owned");
    check(start.tick(2,false,false).type==Action::move_and_press,"initial press release permits one step");
    std::puts("PASS input scope: outside click/drag/activation/title-bar/in-client override/background steps/start-release/CANCEL");
}
