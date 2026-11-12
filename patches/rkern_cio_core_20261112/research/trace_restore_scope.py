from pathlib import Path
import argparse,bisect,json,struct,hashlib
import pefile,capstone
from capstone.x86 import X86_OP_MEM,X86_OP_IMM,X86_REG_RIP
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();a.out.mkdir(exist_ok=True,parents=True)
pe=pefile.PE(str(a.binary));base=pe.OPTIONAL_HEADER.ImageBase
assert hashlib.sha256(a.binary.read_bytes()).hexdigest()=='9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588'
iat={i.address:i.name.decode() for m in pe.DIRECTORY_ENTRY_IMPORT for i in m.imports if i.name}
md=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64);md.detail=True
groups={};ranges=[]
def root(begin,end,data):
 raw=pe.get_data(data,4)
 if raw[0]>>3&4:
  offset=4+((raw[2]*2+3)//4)*4
  return root(*struct.unpack('<III',pe.get_data(data+offset,12)))
 return begin
for entry in pe.DIRECTORY_ENTRY_EXCEPTION:
 s=entry.struct;r=root(s.BeginAddress,s.EndAddress,s.UnwindData)
 groups.setdefault(r,[]).append((s.BeginAddress,s.EndAddress));ranges.append((s.BeginAddress,s.EndAddress,r))
def bounds(va):
 rv=va-base
 for begin,end,r in ranges:
  if begin<=rv<end:return min(v[0] for v in groups[r]),max(v[1] for v in groups[r])
 return rv,rv+768
targets={'buffer_allocate_8k':0x45d7f8,'buffer_recycle_8k':0x45d67c,'socket_close':0x45d534,
 'read_completion_8k':0x45de2c,'object_post_completion':0x704248,'timer_worker_tick':0x705330,'timed_work_queue_candidate':0x70482c,
 'generic_object_completion':0x705d54,'serialized_write_callback':0x45c7ac}
summary={}
for label,va in targets.items():
 begin,end=bounds(va);lines=[f'# Tentative role: {label}; VA {hex(base+begin)}..{hex(base+end)}'];calls=[]
 for ins in md.disasm(pe.get_data(begin,end-begin),base+begin):
  notes=[]
  for op in ins.operands:
   if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP:
    target=ins.address+ins.size+op.mem.disp
    if target in iat:notes.append(iat[target])
   if ins.mnemonic=='call' and op.type==X86_OP_IMM:calls.append(hex(op.imm))
  lines.append(f'{ins.address:016x}: {ins.mnemonic:8s} {ins.op_str}'+(' ; '+','.join(notes) if notes else ''))
 (a.out/(label+'.asm.txt')).write_text('\n'.join(lines)+'\n')
 summary[label]={'start':hex(base+begin),'end':hex(base+end),'bytes':end-begin,'direct_calls':calls}
 print(label,hex(base+begin),end-begin,'bytes')
(a.out/'scope-functions.json').write_text(json.dumps(summary,indent=2)+'\n')
