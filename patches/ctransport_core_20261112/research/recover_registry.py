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

# Locate references to the RTTI-confirmed CUserSocket vtable and the manager
# passed by its close hook. Decode candidate LEAs before assigning roles.
vtable=0xdc1c18
manager=0xa12e65+7+0x26350b4
wanted={vtable:'user_vtable',manager:'socket_manager'}
targets={'base_socket_ctor_90e548':0x90e548,'manager_ctor_8b0f1c':0x8b0f1c}
xrefs=[]
for section in pe.sections:
 if not section.Characteristics&0x20000000:continue
 blob=pe.get_data(section.VirtualAddress,section.Misc_VirtualSize);at=0
 while True:
  hit=blob.find(b'\x48\x8d',at)
  if hit<0:break
  at=hit+1
  if hit+7>len(blob) or blob[hit+2]&0xc7!=5:continue
  va=base+section.VirtualAddress+hit
  target=va+7+struct.unpack_from('<i',blob,hit+3)[0]
  if target not in wanted:continue
  ins=next(md.disasm(blob[hit:hit+15],va),None)
  if not ins or ins.mnemonic!='lea':continue
  begin,end=bounds(va);key=wanted[target]+'_'+hex(base+begin)[2:]
  targets[key]=base+begin;xrefs.append({'reference':hex(va),'target':hex(target),'function':hex(base+begin)})
# Find all direct callers of the register routine, then preserve exact
# pdata bounds; the preceding byte scan is only a candidate filter.
for section in pe.sections:
 if not section.Characteristics&0x20000000:continue
 blob=pe.get_data(section.VirtualAddress,section.Misc_VirtualSize)
 for hit in range(len(blob)-4):
  if blob[hit]!=0xe8:continue
  va=base+section.VirtualAddress+hit
  if va+5+struct.unpack_from('<i',blob,hit+1)[0]!=0x8b0404:continue
  begin,end=bounds(va);targets['register_caller_'+hex(base+begin)[2:]]=base+begin
# Neighbour routines are candidates only until their actual arguments, tree
# accesses, ID writes and reference operations have been read.
near=sorted(set(base+r for r in groups if 0x8afe00<=base+r<=0x8b1000))
for va in near:targets['manager_candidate_'+hex(va)[2:]]=va
(a.out/'manager-xrefs.json').write_text(json.dumps({'manager':hex(manager),'xrefs':xrefs},indent=2)+'\n')
summary={}
for label,va in targets.items():
 begin,end=bounds(va)
 if label in ('tls_set_worker','thread_handle_register'):
  for ins in md.disasm(pe.get_data(begin,end-begin),base+begin):
   if ins.mnemonic=='ret':end=ins.address+ins.size-base;break
 lines=[f'# Tentative role: {label}; VA {hex(base+begin)}..{hex(base+end)}'];calls=[]
 for ins in md.disasm(pe.get_data(begin,end-begin),base+begin):
  notes=[]
  for op in ins.operands:
   if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP:
    target=ins.address+ins.size+op.mem.disp
    if target in iat:notes.append(iat[target])
   if ins.mnemonic=='call' and op.type==X86_OP_IMM:calls.append(hex(op.imm))
  lines.append(f'{ins.address:016x}: {ins.mnemonic:8s} {ins.op_str}'+(' ; '+','.join(notes) if notes else ''))
 (a.out/(label+'.asm.txt')).write_text('\n'.join(line.rstrip() for line in lines)+'\n')
 summary[label]={'start':hex(base+begin),'end':hex(base+end),'bytes':end-begin,'direct_calls':calls}
 print(label,hex(base+begin),end-begin,'bytes')
(a.out/'scope-functions.json').write_text(json.dumps(summary,indent=2)+'\n')
