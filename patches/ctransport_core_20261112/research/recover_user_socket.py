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
targets={'user_socket_parser':0xa0372c,'user_close_hook':0xa12dec,'deferred_user_close':0xa12674,
'user_cleanup':0xa123e8,'socket_unregister':0x8b04fc,'opcode_map':0xa0340c,'packet_session_gate':0x9f7f3c,'packet_actor_gate':0x9ba4f8,
'login_registry_unregister':0xa0dc04,'account_registry_unregister':0xa0ded4,
'tls_set_worker':0x9250f8,'thread_handle_register':0x925118,'worker_start_helper':0xa22ae8,
'worker_count':0x7044c4,'worker_start':0x705b70,'worker_entry':0x705b38,'worker_main':0x7056ac,'service_main':0x72f874,'service_entry':0x73029c}

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
