#include "step_core.h"
#include <cstring>
using namespace nativestep;
int main(int argc,char**argv){
 StepEngine e; e.submit(-2,1000); e.submit(2,1000);
 if(e.tick(1000,false).type!=Action::move_and_press)return 1;
 if(argc>1 && std::strcmp(argv[1],"delay")==0)
  return e.tick(1310,false).type==Action::none?0:2;
 e.submit(-1,1001);e.submit(5,1002);
 if(e.tick(1002,false).type!=Action::release)return 3;
 auto a=e.tick(1003,false);
 return a.type==Action::move_and_press&&a.heading==5?0:4;
}
