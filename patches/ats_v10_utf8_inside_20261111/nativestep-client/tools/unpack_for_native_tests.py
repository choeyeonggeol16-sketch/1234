from pathlib import Path
import struct,json,collections,argparse,hashlib
import pefile
from unicorn import Uc, UC_ARCH_X86, UC_MODE_32, UC_HOOK_CODE, UC_HOOK_MEM_INVALID, UC_HOOK_BLOCK, UcError
from unicorn.x86_const import *

p=argparse.ArgumentParser(description='Offline, API-stubbed unpacking for this exact Mega.bin native replay')
p.add_argument('--client',type=Path,required=True);p.add_argument('--output-directory',type=Path,required=True)
a=p.parse_args();root=a.output_directory;root.mkdir(parents=True,exist_ok=True)
assert hashlib.sha256(a.client.read_bytes()).hexdigest()=='09804d9579446fb9a630c39c1f1b2d08083718261d2f3e57b2e36f80ba865490'
pe=pefile.PE(str(a.client))
base=pe.OPTIONAL_HEADER.ImageBase
uc=Uc(UC_ARCH_X86,UC_MODE_32)
uc.mem_map(base,0xe50000)
uc.mem_write(base,pe.get_memory_mapped_image())
uc.mem_map(0x70000000,0x200000)
uc.reg_write(UC_X86_REG_ESP,0x701ff000)
uc.mem_write(0x701ff000,struct.pack('<I',0x600ff000))
uc.mem_map(0x60000000,0x100000)
uc.mem_map(0,0x10000)
uc.mem_map(0x7ff00000,0x100000)
uc.mem_write(0x30,struct.pack('<I',0x7ff01000))
uc.mem_write(0x7ff01008,struct.pack('<I',base))
uc.mem_write(0x7ff01018,struct.pack('<I',0x7ff10000))
uc.reg_write(UC_X86_REG_EFLAGS,0x202)
apis={};api_addresses={};modules={};module_names={};calls=[];heap=0x20000000
blocks=collections.deque(maxlen=25)

def read32(p):return struct.unpack('<I',uc.mem_read(p,4))[0]
def w32(p,x):uc.mem_write(p,struct.pack('<I',x&0xffffffff))
def cstr(p):
    if not p:return ''
    b=bytearray()
    for i in range(1024):
        c=uc.mem_read(p+i,1)[0]
        if not c:break
        b.append(c)
    return b.decode('latin1')

def module(name):
    name=name.lower()
    if name not in modules:
        addr=0x50000000+len(modules)*0x10000
        modules[name]=addr;module_names[addr]=name
        uc.mem_map(addr,0x10000);uc.mem_write(addr,b'MZ');w32(addr+0x3c,0x100)
    return modules[name]

def stub(mod,name):
    key=(mod.lower(),name)
    if key not in api_addresses:
        addr=0x60000000+len(api_addresses)*16
        api_addresses[key]=addr;apis[addr]=key
        uc.mem_write(addr,b'\xc3')
    return api_addresses[key]

for entry in pe.DIRECTORY_ENTRY_IMPORT:
    mod=entry.dll.decode().lower();module(mod)
    for imp in entry.imports:
        name=imp.name.decode() if imp.name else '#'+str(imp.ordinal)
        w32(imp.address,stub(mod,name))

def api_hook(u,address,size,data):
    global heap
    if address not in apis:
        print('STOP unknown stub',hex(address),flush=True);u.emu_stop();return
    mod,name=apis[address];esp=u.reg_read(UC_X86_REG_ESP);ret=read32(esp)
    args=[read32(esp+4+i*4) for i in range(8)]
    calls.append({'module':mod,'name':name,'return':hex(ret),'args':[hex(x) for x in args[:4]]})
    n=0;result=0
    if name=='GetProcAddress':
        n=2;symbol=cstr(args[1]) if args[1]>0xffff else '#'+str(args[1])
        result=stub(module_names.get(args[0],'unknown'),symbol)
    elif name in ('GetModuleHandleA','LoadLibraryA','LoadLibraryExA'):
        n=3 if name=='LoadLibraryExA' else 1
        result=module(cstr(args[0])) if args[0] else base
    elif name=='VirtualAlloc':
        n=4;size=(args[1]+0xfff)&~0xfff
        if size>0x8000000:raise RuntimeError('Implausible allocation '+hex(size))
        result=args[0] or heap
        if not args[0]:
            heap+=(size+0xffff)&~0xffff
            u.mem_map(result,size)
        else:
            try:u.mem_map(result&~0xfff,size)
            except UcError:pass
    elif name in ('VirtualProtect','VirtualProtectEx'):
        n=5 if name.endswith('Ex') else 4
        if args[n-1]:w32(args[n-1],0x40)
        result=1
    elif name=='VirtualFree':n=3;result=1
    elif name=='FlushInstructionCache':n=3;result=1
    elif name=='GetCurrentProcess':result=0xffffffff
    elif name=='GetCurrentThread':result=0xfffffffe
    elif name in ('ExitProcess','ExitThread','TerminateProcess'):
        print('STOP termination',name,args[:2],flush=True);u.emu_stop();return
    else:
        print('STOP unsupported API',mod,name,'caller',hex(ret),'args',[hex(x) for x in args[:4]],flush=True)
        u.emu_stop();return
    if len(calls)<12:print('API',name,'=>',hex(result),flush=True)
    u.reg_write(UC_X86_REG_EAX,result);u.reg_write(UC_X86_REG_ESP,esp+4+4*n);u.reg_write(UC_X86_REG_EIP,ret)

def invalid(u,access,address,size,value,data):
    print('UNMAPPED',access,hex(address),size,'eip',hex(u.reg_read(UC_X86_REG_EIP)),flush=True)
    return False

def block(u,address,size,data):blocks.append((hex(address),size))
def pause(u,address,size,data):
    print('STAGE ENTRY reached',hex(address),flush=True);u.emu_stop()

uc.hook_add(UC_HOOK_CODE,api_hook,begin=0x60000000,end=0x600fffff)
uc.hook_add(UC_HOOK_MEM_INVALID,invalid)
# Block tracing disabled during bulk decompression.
uc.hook_add(UC_HOOK_CODE,pause,begin=0x1231600,end=0x1231600)
try:uc.emu_start(base+pe.OPTIONAL_HEADER.AddressOfEntryPoint,0,count=0,timeout=45_000_000)
except Exception as e:print(type(e).__name__,str(e),flush=True)
assert uc.reg_read(UC_X86_REG_EIP)==0x1231600, 'Unpacking did not reach the validated stage boundary'
print('Final EIP',hex(uc.reg_read(UC_X86_REG_EIP)),'calls',len(calls),'heap',hex(heap),'blocks',list(blocks),flush=True)
(root/'stage1-image.bin').write_bytes(uc.mem_read(base,0xe46000))
(root/'stage1-api-map.json').write_text(json.dumps({hex(k):list(v) for k,v in apis.items()},indent=2))
(root/'stage1-calls.json').write_text(json.dumps(calls,indent=2))
for reg,name in [(UC_X86_REG_EAX,'eax'),(UC_X86_REG_EBX,'ebx'),(UC_X86_REG_ECX,'ecx'),(UC_X86_REG_EDX,'edx'),(UC_X86_REG_ESI,'esi'),(UC_X86_REG_EDI,'edi'),(UC_X86_REG_EBP,'ebp'),(UC_X86_REG_ESP,'esp')]:print(name,hex(uc.reg_read(reg)))
