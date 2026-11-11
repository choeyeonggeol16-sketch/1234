"""Execute the uploaded client's actual x86 coordinate/heading routines offline.

Requires unicorn==2.1.4. The memory image is produced by the companion unpack
script; no Windows APIs, network, game assets or real mouse input are executed.
Only collision and busy-state boundaries are fixture stubs. This is not a live
game movement/camera test, nor a test of SendMessage delivery on Windows.
"""
from pathlib import Path
import argparse,hashlib,json,struct
from unicorn import Uc,UC_ARCH_X86,UC_MODE_32,UC_HOOK_CODE
from unicorn.x86_const import *

p=argparse.ArgumentParser();p.add_argument('--client',type=Path,required=True)
p.add_argument('--image',type=Path,required=True);a=p.parse_args()
assert hashlib.sha256(a.client.read_bytes()).hexdigest()=='09804d9579446fb9a630c39c1f1b2d08083718261d2f3e57b2e36f80ba865490'
image=a.image.read_bytes();u=Uc(UC_ARCH_X86,UC_MODE_32)
u.mem_map(0x400000,(len(image)+4095)&~4095);u.mem_write(0x400000,image)
u.mem_map(0x10000000,0x10000);u.mem_map(0x70000000,0x10000)
STOP=0x60000000;u.mem_map(STOP,0x1000)
def w(p,v):u.mem_write(p,struct.pack('<I',v&0xffffffff))
def r(p):return struct.unpack('<I',u.mem_read(p,4))[0]
def point(p,value=None):
    if value is not None:u.mem_write(p,struct.pack('<ii',*value))
    return struct.unpack('<ii',u.mem_read(p,8))
stub_busy=True
def hook(u,pc,size,_):
    if pc==0x45cbd0 and stub_busy: # Busy-state query: fixture is an idle character.
        sp=u.reg_read(UC_X86_REG_ESP);u.reg_write(UC_X86_REG_EAX,0)
        u.reg_write(UC_X86_REG_EIP,r(sp));u.reg_write(UC_X86_REG_ESP,sp+4)
    elif pc==0x471700: # Fixture has no blocked neighboring cells.
        sp=u.reg_read(UC_X86_REG_ESP);u.reg_write(UC_X86_REG_EAX,0)
        u.reg_write(UC_X86_REG_EIP,r(sp));u.reg_write(UC_X86_REG_ESP,sp+12)
    elif pc==0x4cc528: # All eight actual candidate/distance calculations done.
        selected.append(r(u.reg_read(UC_X86_REG_EBP)-8));u.emu_stop()
for pc in [0x45cbd0,0x471700,0x4cc528]:u.hook_add(UC_HOOK_CODE,hook,begin=pc,end=pc)
def call(pc,this=0,args=()):
    sp=0x7000ff00
    for reg in [UC_X86_REG_EAX,UC_X86_REG_EBX,UC_X86_REG_EDX,UC_X86_REG_ESI,UC_X86_REG_EDI,UC_X86_REG_EBP]:u.reg_write(reg,0)
    u.reg_write(UC_X86_REG_ECX,this);u.reg_write(UC_X86_REG_ESP,sp);u.reg_write(UC_X86_REG_EFLAGS,0x202)
    u.mem_write(sp,struct.pack('<'+'I'*(len(args)+1),STOP,*args))
    u.emu_start(pc,STOP,count=100000)
    return u.reg_read(UC_X86_REG_EAX)

call(0x49f3f0) # Actual native heading delta initializer.
delta=[point(0x8e3938+i*8) for i in range(8)]
assert delta==[(0,-1),(2,-1),(2,0),(2,1),(0,1),(-2,1),(-2,0),(-2,-1)]
actor=0x10001000;source=0x10002000;target=0x10002010
screen=0x10002020;world=0x10002030;raw_result=0x10002040
w(0x9b4214,actor);w(0x8d6354,0);w(0x8d6228,0)
cases=0;selected=[];samples=[]
for camera_dx,camera_dy in [(-6,18),(10,16),(6,21)]:
    current=(65536,32768)
    point(actor+0x34,current);point(source,current)
    point(0x8d6420,(current[0]-camera_dx,current[1]-camera_dy))
    for mode in range(8):
        w(0x796488,mode)
        for heading in range(8):
            call(0x49f810,source,(target,heading));next_tile=point(target)
            call(0x49f4c0,target,(screen,));logical=point(screen)
            x,y=logical
            logical=(x+24,y+12);x,y=logical;point(screen,logical)
            if mode%4==0:
                assert not(x&1 or y&1);raw=(x//2,y//2)
            elif mode%4==1:raw=logical
            elif mode%4==2:raw=((x*3+1)//2,(y*3+1)//2)
            else:raw=(x*2,y*2)
            call(0x45e2b0,raw_result,raw)
            assert point(raw_result)==logical,(mode,heading,raw,point(raw_result),logical)
            call(0x49f590,screen,(world,));roundtrip=point(world)
            assert (roundtrip[0]&~1,roundtrip[1])==next_tile,(mode,heading,logical,roundtrip,next_tile)
            for dx,dy in [(-2,-2),(2,-2),(-2,2),(2,2)]:
                point(screen,(logical[0]+dx,logical[1]+dy));call(0x49f590,screen,(world,));edge=point(world)
                assert (edge[0]&~1,edge[1])==next_tile
            point(screen,logical)
            call(0x471a70,args=logical)
            assert point(0x7c28e8)==next_tile,(heading,point(0x7c28e8),next_tile)
            assert u.mem_read(0x7c28e3,1)==b'\x01'
            selected.clear();call(0x4cc490,0x10002080)
            assert selected==[heading],(mode,heading,selected)
            call(0x4cc830)
            assert u.mem_read(0x7c28e3,1)==b'\x00' and r(0x7c2610)==0
            assert point(actor+0x34)==current
            if camera_dx==-6:assert point(0x7c28e8)==current
            if camera_dx==-6 and mode==2:samples.append({'heading':heading,'logical':logical,'window_960x720':raw})
            cases+=1
# Exercise the actual busy-state query used by the V5 readiness gate too.
# Encoded game-state storage is a controlled fixture; the query instructions
# and all early-return instructions below remain the original binary's code.
stub_busy=False
w(0x9b0990,0xc0017921);w(0x9b0994,0x10003000);w(0x9b0998,0x12345678)
readiness_cases=0
for state,blocked in [(0,False),(0x1000,False),(1,True),(0x1001,True)]:
    w(0x10003000,state^0x12345678)
    assert bool(call(0x45cbd0,0x18)&0xff)==blocked
    readiness_cases+=1
w(0x10003000,0x12345678)
for gate in ('input-blocked','busy','interaction'):
    u.mem_write(0x7c260c,b'\x01' if gate=='input-blocked' else b'\x00')
    w(0x10003000,0x12345679 if gate=='busy' else 0x12345678)
    w(0x8d6228,actor if gate=='interaction' else 0)
    sentinel=(12340,5678);point(0x7c28e8,sentinel)
    call(0x471a70,args=(312,240))
    assert point(0x7c28e8)==sentinel,gate
    readiness_cases+=1
print(json.dumps({'result':'PASS','actual_x86_cases':cases,'camera_fixtures':3,'resolution_modes':8,
 'headings':8,'native_readiness_cases':readiness_cases,'native_960x720_samples':samples,'fixture_boundaries':['idle-state query','all eight neighboring cells passable'],
 'verified':['native world step','native world/screen roundtrip','native resolution scaling','native goal selection','native eight-candidate heading choice','native stop-target clears pending','native busy query and three goal-selection early returns'],
 'windows_message_delivery_tested':False,'live_game_movement_or_camera_tested':False},indent=2))
