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
 'generic_object_completion':0x705d54,'serialized_write_callback':0x45c7ac,
 'timer_queue_tick':0x926844,'timer_guard_enter':0x926e2c,'timer_guard_leave':0x926e54,
 'worker_main':0x7057c9, 'timer_bucket_dispatch':0x9256d4,'timer_bucket_append':0x92533c,
 'timer_overflow_heap_pop':0x92611c,'timer_block_allocate':0x925498,
 'user_socket_parser':0xa0372c,'user_socket_io_dispatch':0x54655c,
 'atomic_timer_entry':0x43f5b8,'atomic_timer_apply':0x43f758,
 'user_close_hook':0xa12dec,'fatal_diagnostic':0x66db28,'pool_unlock':0x45d3b4,
 'timer_block_release':0x925630,'timer_bucket_execute':0x925374,
 'pool_setup_worker':0x7046d2,'pool_setup_candidate':0x7303a0,'timer_register':0x926d36,
 'pool8_constructor':0x6b5094,'pool16_constructor':0x6b4fb8,'deferred_user_close':0xa12674,
 'owner17_loop_a':0x73029c,'owner17_loop_b':0x72f874}
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
