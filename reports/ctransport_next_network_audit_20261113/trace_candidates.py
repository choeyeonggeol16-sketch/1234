"""Read-only candidate trace; signatures are locators, not proof of semantics."""
from pathlib import Path
import argparse,json,re,struct,sys
root=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);p.add_argument('--out',type=Path,default=Path(__file__).resolve().parent);a=p.parse_args()
sys.argv=['audit','--x64',str(a.binary),'--x86',str(a.binary),'--out',str(a.out)]
scope={};exec(compile((root/'research/audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0],'helper','exec'),scope)
obj=scope['AuditPE'](a.binary,'x64')
strings={0xdbe500:'check_max_packet_accept',0xdc0830:'report_packet_log',0xdbe570:'on_sending',0xdbe490:'make_packet_block_list',0xd6ad60:'async_send_const',0xdb9040:'safe_send',0xdc2510:'on_read',0xcebd20:'perf_report_user_count'}
refs={name:[] for name in strings.values()}
for start,data in obj.text:
 for m in re.finditer(rb'[\x48-\x4f]\x8d[\x05\x0d\x15\x1d\x25\x2d\x35\x3d]',data):
  at=m.start();target=start+at+7+struct.unpack_from('<i',data,at+3)[0]
  if target not in strings:continue
  ins=obj.valid(start+at);chunk=obj.chunk(start+at)
  if ins and ins.size==7 and chunk:refs[strings[target]].append({'at':hex(ins.address),'entry':hex(chunk[2])})
functions={}
for label,sites in refs.items():
 for i,site in enumerate(sites):
  key=label if len(sites)==1 else label+'_'+str(i)
  functions[key]=obj.dump(key,int(site['entry'],16))
for label,address in [('read8',0x45de2c),('read16_dispatch',0x545430),('write16',0x545e38),('write_enqueue',0x90e1d0),('io_worker',0x7056ac)]:functions[label]=obj.dump(label,address)
(a.out/'candidates.json').write_text(json.dumps({'signature_refs':refs,'functions':functions},indent=2)+'\n')
checks=[(0xa0395f,'and','ecx, 0x7f'),(0xa03962,'mov','dword ptr [rsi + rcx*4 + 0x804], edx'),
        (0xa03979,'mov','dword ptr [rsi + rcx*4 + 0xa04], eax'),(0xa03980,'add','qword ptr [rsi + 0xc08], 1'),
        (0xa0398d,'call','0x9f7f3c'),(0x9fce9c,'cmp','esi, 0x80'),(0x9f7fb7,'cmp','edx, 0x3e8'),
        (0x9f8024,'mov','al, 1'),(0x90e843,'call','0xabcd50'),(0x90e812,'call','0x90de2c'),
        (0x90e32c,'lock xadd','qword ptr [rip + 0x4342c03], r11'),
        (0x7056fe,'imul','r8, r8, 0x3e8')]
verified=[]
for address,mnemonic,operands in checks:
 ins=obj.valid(address);assert ins and (ins.mnemonic,ins.op_str)==(mnemonic,operands),(hex(address),ins)
 verified.append({'at':hex(address),'bytes':ins.bytes.hex(),'mnemonic':mnemonic,'operands':operands})
(a.out/'verified.json').write_text(json.dumps({'binary_sha256':scope['EXPECTED']['x64'],'image_base':hex(obj.base),'verified':verified,'executed_original':False},indent=2)+'\n')
print('PASS',len(functions),'unwind-bounded function traces and',len(verified),'exact instruction assertions')
