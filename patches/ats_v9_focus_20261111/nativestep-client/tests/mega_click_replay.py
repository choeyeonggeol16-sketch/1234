"""Replay real native DOWN routing, including a label stealing a ground click.

This is a controlled CPU fixture, not Windows message delivery or live gameplay.
Object classification, rendered bounds and the final movement actuator are
fixture boundaries. The actual native goal selector and DOWN router execute.
"""
from pathlib import Path
import contextlib,io,json,re,runpy,struct
from unicorn import UC_HOOK_CODE
from unicorn.x86_const import *

capture=io.StringIO()
with contextlib.redirect_stdout(capture):
    state=runpy.run_path(str(Path(__file__).with_name('mega_native_replay.py')))
result=json.loads(capture.getvalue())
u=state['u'];call=state['call'];w=state['w'];point=state['point'];r=state['r']
offset_text=Path(__file__).parents[1].joinpath('include/click_points.h').read_text().split('kClickOffsets[] = {',1)[1].split('};',1)[0]
offsets=[tuple(map(int,p)) for p in re.findall(r'\{(-?\d+),(-?\d+)\}',offset_text)]
assert len(offsets)==10
actor=state['actor'];source=state['source'];target=state['target'];screen=state['screen'];world=state['world']
current=(65536,32768)
roundtrips=0
for camera_dx,camera_dy in [(-6,18),(10,16),(6,21)]:
    point(source,current);point(actor+0x34,current)
    point(0x8d6420,(current[0]-camera_dx,current[1]-camera_dy))
    for mode in range(8):
        w(0x796488,mode)
        for heading in range(8):
            call(0x49f810,source,(target,heading));expected=point(target)
            call(0x49f4c0,target,(screen,));left=point(screen)
            for dx,dy in offsets:
                pixel=(left[0]+24+dx,left[1]+12+dy)
                for cx,cy in [(0,0),(-2,-2),(2,-2),(-2,2),(2,2)]:
                    point(screen,(pixel[0]+cx,pixel[1]+cy));call(0x49f590,screen,(world,));actual=point(world)
                    assert (actual[0]&~1,actual[1])==expected,(camera_dx,mode,heading,dx,dy)
                x,y=pixel
                raw=[(x//2,y//2),(x,y),((x*3+1)//2,(y*3+1)//2),(x*2,y*2)][mode%4]
                call(0x45e2b0,state['raw_result'],raw)
                assert point(state['raw_result'])==pixel
                roundtrips+=1

def ret(eax=0,nargs=0):
    sp=u.reg_read(UC_X86_REG_ESP)
    u.reg_write(UC_X86_REG_EAX,eax)
    u.reg_write(UC_X86_REG_EIP,r(sp));u.reg_write(UC_X86_REG_ESP,sp+4+nargs*4)
label_box=(0,0,0,0)
def fixture(u,pc,size,_):
    if pc==0x4d31a0:ret(9) # Item classification boundary.
    elif pc==0x4d13a0:
        sp=u.reg_read(UC_X86_REG_ESP);out=r(sp+4)
        u.mem_write(out,struct.pack('<iiii',*label_box));ret(out,2)
    else:ret() # Non-action state, interaction predicate, final movement actuator.
for pc in (0x4d31a0,0x4d13a0,0x4d0a60,0x4d3440,0x4cc8a0):
    u.hook_add(UC_HOOK_CODE,fixture,begin=pc,end=pc)

point(actor+0x34,current);point(source,current)
point(0x8d6420,(current[0]+6,current[1]-18));w(0x796488,2)
for address in (0x8d621c,0x8d6228,0x8d6354,0x8d634c,0x8d6368,0x8d636c):w(address,0)
for address in (0x7c260c,0x7c2616,0x7c2945,0x7c295b,0x9b41e9):u.mem_write(address,b'\x00')
w(0x10003000,0x12345678) # Actual native busy query returns ready.
objects=0x10004000;item=0x10005000;name=0x10005800
w(objects,item);w(item+0x60,name);u.mem_write(name,b'item\x00')
point(item+0x34,(current[0]+8,current[1]+4))
w(0x9b4280,1);w(0x9b4288,objects)
u.mem_write(0x7c2979,b'\x01');u.mem_write(0x79a3b0,b'\x00')
reproduced=0;avoided=0
for heading in range(8):
    call(0x49f810,source,(target,heading));expected=point(target)
    call(0x49f4c0,target,(screen,));left=point(screen);center=(left[0]+24,left[1]+12)
    # Native item-name hit box covers the old fixed center point.
    label_box=(center[0]-24,center[1]+1,center[0]+24,center[1]+5)
    w(0x8d6354,0);call(0x472070,0,center)
    assert point(0x7c28e8)!=expected,(heading,'expected old center to select label')
    reproduced+=1
    shift=(label_box[2]-label_box[0]-4*6)//2
    bounds=(label_box[0]+shift,label_box[1]-3,label_box[2]+shift,label_box[3]-3)
    clear=[(center[0]+dx,center[1]+dy) for dx,dy in offsets
           if not(bounds[0]<=center[0]+dx<bounds[2] and bounds[1]<=center[1]+dy<bounds[3])]
    assert clear
    w(0x8d6354,0);call(0x472070,0,clear[0])
    assert point(0x7c28e8)==expected,(heading,clear[0],point(0x7c28e8),expected)
    assert r(0x8d6354)==0,'native label hover restoration'
    avoided+=1
result.update(candidate_roundtrips=roundtrips,old_center_label_failures_reproduced=reproduced,
              same_tile_label_conflicts_avoided=avoided,
              extra_fixture_boundaries=['object kind','rendered label bounds','non-action query','interaction predicate','final movement actuator'],
              windows_message_delivery_tested=False,live_game_movement_or_camera_tested=False)
print(json.dumps(result,indent=2))
