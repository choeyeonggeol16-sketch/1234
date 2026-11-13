"""Read-only x64 proof for deliberate faulting and MemoryObject ID consumers."""
from pathlib import Path
import argparse,json,sys,struct
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);a=p.parse_args();out=Path(__file__).resolve().parent
sys.argv=['audit','--x64',str(a.binary),'--x86',str(a.binary),'--out',str(out)]
s={};exec(compile((out/'audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0],'audit helper','exec'),s)
o=s['AuditPE'](a.binary,'x64')
checks=[(0x66db28,'xor','eax, eax'),(0x66db2a,'mov','byte ptr [rax], 1'),(0x66db2d,'ret',''),
 (0x7d6dd9,'mov','dword ptr [r9 + 0x10], eax'),(0x7d6e99,'mov','dword ptr [r9 + 0x10], eax'),
 (0x7d6ddd,'lock add','dword ptr [r9 + 8], 1'),(0x7d6e9d,'lock add','dword ptr [r9 + 8], 1'),
 (0x7d6fac,'mov','ebx, dword ptr [rcx + 0x10]'),(0x9f792d,'mov','eax, dword ptr [rbx + 0x10]'),
 (0x70425b,'mov','r8, rcx'),(0x6707e0,'xor','eax, eax'),(0x6707dc,'call','rax')]
verified=[]
for at,mnemonic,operands in checks:
 ins=o.valid(at);assert ins and (ins.mnemonic,ins.op_str)==(mnemonic,operands),(hex(at),ins)
 verified.append({'at':hex(at),'bytes':ins.bytes.hex(),'instruction':mnemonic+' '+operands})
functions={}
for name,entry in {'fault':0x66db28,'install_filter':0x670808,'exception_filter':0x670750,'callstack_trace':0x6704e0,'critical_callstack':0x670110,'default_constructor':0x7d6d5c,'copy_constructor':0x7d6e1c,'base_release':0x7d6eb4,'user_release':0x9f7770,'post_completion':0x704248}.items():
 info=o.dump(name,entry);info['caller_count']=len(info.pop('callers'));functions[name]=info
counters=[]
from capstone.x86 import X86_OP_MEM,X86_REG_RIP
for name in ['default_constructor','copy_constructor']:
 found=[]
 for b,e in functions[name]['chunks']:
  for ins in o.instructions(int(b,16),int(e,16)):
   if ins.mnemonic=='lock xadd':
    found.extend(ins.address+ins.size+op.mem.disp for op in ins.operands if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP)
 assert len(found)==1;counters.append(found[0])
assert counters[0]==counters[1]
setter=o.valid(0x670838);assert setter and setter.mnemonic=='lea'
assert setter.address+setter.size+setter.operands[1].mem.disp==0x670750
call=o.valid(0x670845);assert o.imports[call.address+call.size+call.operands[0].mem.disp]=='SetUnhandledExceptionFilter'
vtables=[]
for name,table,release in [('CIOObject',0xc71c18,0x7d6eb4),('CSocket',0xd6ab88,0x7d6eb4),('CUserSocket',0xdc1c18,0x9f7770)]:
 assert struct.unpack('<Q',o.pe.get_data(table-o.base+16,8))[0]==release
 vtables.append({'class':name,'vtable':hex(table),'slot':2,'target':hex(release)})
result={'binary_sha256':s['EXPECTED']['x64'],'image_base':hex(o.base),'original_executed':False,
 'scope':'Exact fault/constructor/release/IOCP paths;not a proof that all unnamed code has no additional ID consumers;not a new x86 audit.',
 'verified_instructions':verified,'constructor_id_counter':hex(counters[0]),'network_release_vtables':vtables,'functions':functions,
 'findings':['Fault helper deliberately writes to null;it is not socket-close or allocation recovery.',
 'Installed unhandled filter gathers diagnostics and forwards to the previous filter,or returns CONTINUE_SEARCH(0). This is not evidence of automatic IO recovery.',
 'Both constructors assign a fresh global32 ID and ref1. Base and CUserSocket release use the ID in reference-underflow diagnostics.',
 'Examined IOCP posting passes the object pointer as the completion key;the MemoryObject diagnostic ID is not a replacement for L1 slot-generation handles.']}
(out/'EVIDENCE.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS original SHA;12 instruction assertions;shared constructor counter;3 network release vtable slots;installed exception filter target')
