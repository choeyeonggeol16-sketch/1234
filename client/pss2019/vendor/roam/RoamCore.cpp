#define ROAM_EXPORTS
#include "RoamCore.h"
#include <vector>
#include <deque>
#include <map>
#include <unordered_map>
#include <algorithm>
#include <cstdlib>
#include <utility>
struct P {int x,y;bool operator==(const P& b)const{return x==b.x&&y==b.y;}};
static uint64_t key(P p){return(uint64_t(uint32_t(p.x))<<32)|uint32_t(p.y);}
struct SRState {int lookahead=1;SRConfig cfg;uint32_t rng;bool bound=false,pending=false;int map=0;P anchor{},last{},expected{};uint64_t pending_at=0,next_plan=0;std::deque<P> path,recent;std::map<std::pair<uint64_t,uint64_t>,uint64_t> blocked;};
static uint32_t rnd(SRState& s){s.rng^=s.rng<<13;s.rng^=s.rng>>17;s.rng^=s.rng<<5;return s.rng;}
SRState* __cdecl sr_create(const SRConfig* c){try{auto s=new SRState;s->cfg=c?*c:SRConfig{12,2048,300,2000,0x51a7};s->cfg.radius=(std::max)(1,(std::min)(32,s->cfg.radius));s->cfg.search_budget=(std::max)(16,(std::min)(8192,s->cfg.search_budget));s->cfg.retry_ms=(std::max)(100,s->cfg.retry_ms);s->cfg.stuck_ms=(std::max)(500,s->cfg.stuck_ms);s->rng=s->cfg.seed?s->cfg.seed:0x51a7;return s;}catch(...){return nullptr;}}
void __cdecl sr_destroy(SRState* s){delete s;}
void __cdecl sr_set_lookahead(SRState* s,int tiles){if(s)s->lookahead=(std::max)(1,(std::min)(4,tiles));}
void __cdecl sr_reset(SRState* s){if(!s)return;s->bound=false;s->pending=false;s->path.clear();s->recent.clear();s->blocked.clear();s->next_plan=0;}
static bool in_range(P a,P b,int radius){long long dx=(long long)a.x-b.x,dy=(long long)a.y-b.y;return std::llabs(dx)<=radius&&std::llabs(dy)<=radius;}
static bool plan(SRState& s,const SRInput& i,const SRHost& h){
 for(auto p=s.blocked.begin();p!=s.blocked.end();)if(p->second<=i.now_ms)p=s.blocked.erase(p);else ++p;
 struct Node {P p;int parent,dist;};std::vector<Node> nodes;nodes.push_back({{i.x,i.y},-1,0});std::unordered_map<uint64_t,int> seen;seen[key(nodes[0].p)]=0;
 static const int dx[8]={0,1,1,1,0,-1,-1,-1},dy[8]={-1,-1,0,1,1,1,0,-1};
 for(size_t head=0;head<nodes.size()&&nodes.size()<size_t(s.cfg.search_budget);++head){const Node cur=nodes[head];unsigned start=rnd(s)%8;for(int d0=0;d0<8&&nodes.size()<size_t(s.cfg.search_budget);++d0){int d=(d0+start)%8;long long nx=(long long)cur.p.x+dx[d],ny=(long long)cur.p.y+dy[d];if(nx<INT32_MIN||nx>INT32_MAX||ny<INT32_MIN||ny>INT32_MAX)continue;P q{int(nx),int(ny)};if(!in_range(q,s.anchor,s.cfg.radius)||seen.count(key(q))||s.blocked.count({key(cur.p),key(q)}))continue;if(!h.can_step(h.user,i.map,cur.p.x,cur.p.y,q.x,q.y))continue;int at=int(nodes.size());nodes.push_back({q,int(head),cur.dist+1});seen[key(q)]=at;}}
 std::vector<int> choices;for(int n=1;n<int(nodes.size());n++){if(nodes[n].dist<2)continue;if(std::find(s.recent.begin(),s.recent.end(),nodes[n].p)!=s.recent.end())continue;choices.push_back(n);}if(choices.empty())for(int n=1;n<int(nodes.size());n++)choices.push_back(n);if(choices.empty())return false;
 int n=choices[rnd(s)%choices.size()];s.recent.push_back(nodes[n].p);if(s.recent.size()>8)s.recent.pop_front();s.path.clear();while(nodes[n].parent>=0){s.path.push_front(nodes[n].p);n=nodes[n].parent;}return !s.path.empty();
}
// Extend a command only through consecutive, host-approved collinear edges.
// The default remains one tile for existing hosts; the native adapter opts in.
static P command_goal(const SRState& s,const SRInput& i,const SRHost& h){
 P cur{i.x,i.y},next=s.path.front();
 long long dx=(long long)next.x-cur.x,dy=(long long)next.y-cur.y;
 if(dx < -1||dx>1||dy < -1||dy>1||(!dx&&!dy))return next;
 for(int n=1;n<s.lookahead&&n<int(s.path.size());++n){
  P q=s.path[n];
  if((long long)q.x-next.x!=dx||(long long)q.y-next.y!=dy||
     !h.can_step(h.user,i.map,next.x,next.y,q.x,q.y))break;
  next=q;
 }
 return next;
}
static bool consume_progress(SRState& s,P cur){
 for(auto it=s.path.begin();it!=s.path.end();++it){
  if(*it==cur){auto end=it;++end;s.path.erase(s.path.begin(),end);return true;}
  if(*it==s.expected)break;
 }
 return false;
}
static void exclude_next(SRState& s,P cur,uint64_t until){
 P next=s.path.empty()?s.expected:s.path.front();
 s.blocked[{key(cur),key(next)}]=until;
}
int __cdecl sr_tick(SRState* sp,const SRInput* ip,const SRHost* hp){if(!sp||!ip||!hp||!hp->can_step||!hp->request_step)return SR_INVALID;try{auto& s=*sp;const auto& i=*ip;const auto& h=*hp;P cur{i.x,i.y};if(!i.enabled||i.dead){sr_reset(&s);return SR_PAUSED;}if(!s.bound||s.map!=i.map){sr_reset(&s);s.bound=true;s.map=i.map;s.anchor=cur;s.last=cur;}if(i.has_target){s.path.clear();s.pending=false;s.next_plan=0;s.last=cur;return SR_YIELD_TARGET;}if(!i.can_move){s.pending=false;s.path.clear();s.last=cur;return SR_PAUSED;}
 if(!in_range(cur,s.anchor,s.cfg.radius)){s.anchor=cur;s.path.clear();s.pending=false;s.blocked.clear();}
 if(s.pending){if(cur==s.expected){s.pending=false;consume_progress(s,cur);s.last=cur;}else if(!(cur==s.last)){if(consume_progress(s,cur)){s.last=cur;s.pending_at=i.now_ms;return SR_WAIT;}s.pending=false;s.path.clear();s.last=cur;}else if(i.now_ms-s.pending_at<uint64_t(s.cfg.stuck_ms))return SR_WAIT;else{exclude_next(s,cur,i.now_ms+2000);s.path.clear();s.pending=false;s.next_plan=i.now_ms+s.cfg.retry_ms;return SR_NO_ROUTE;}}
 if(i.now_ms<i.earliest_move_ms||i.now_ms<s.next_plan)return SR_WAIT;
 if(s.path.empty()&&!plan(s,i,h)){s.next_plan=i.now_ms+s.cfg.retry_ms;return SR_NO_ROUTE;}
 P next=s.path.front();if(!h.can_step(h.user,i.map,cur.x,cur.y,next.x,next.y)){s.blocked[{key(cur),key(next)}]=i.now_ms+2000;s.path.clear();s.next_plan=i.now_ms+s.cfg.retry_ms;return SR_NO_ROUTE;}
 next=command_goal(s,i,h);
 if(!h.request_step(h.user,i.map,next.x,next.y)){exclude_next(s,cur,i.now_ms+2000);s.path.clear();s.next_plan=i.now_ms+s.cfg.retry_ms;return SR_NO_ROUTE;}s.pending=true;s.pending_at=i.now_ms;s.expected=next;s.last=cur;return SR_STEP;
 }catch(...){return SR_INVALID;}}
