from pathlib import Path
import sys,json,struct,argparse
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);a=p.parse_args();out=Path(__file__).resolve().parent
sys.argv=['audit','--x64',str(a.binary),'--x86',str(a.binary),'--out',str(out)]
s={};exec(compile((out/'audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0],'helper','exec'),s);o=s['AuditPE'](a.binary,'x64')
functions={}
for name,at in {'heap_allocate':0x74f0f4,'socket_operator_new':0x9ef21c,'object_return':0x74e3bc,'heap_return':0x74e08c,'small_return':0x74dc0c,'user_ctor':0xa0ccc4,'socket_ctor':0x90e548,'socket_ex_ctor':0x545030,'server_ctor':0x704bec,'object_ctor':0x7d6d5c,'timer_forward':0x404c88,'timer_forward64':0x404c94,'timer_complete':0x705d54,'socket_read':0x545398,'packet_format':0x70429c,'serializer_ctor':0x45c708}.items():
 functions[name]=o.dump(name,at)
pending=[0x74f0f4,0x74dc0c,0x74e08c];seen=set()
while pending:
 at=pending.pop()
 if at in seen:continue
 seen.add(at);info=o.dump('allocator_'+hex(at)[2:],at);functions[hex(at)]=info
 for target in info['direct_calls']:
  v=int(target,16)
  if 0x74d000<=v<0x74f600 and v not in seen:pending.append(v)
vtables={}
for name,at,count in [('MemoryObject',0xcc8530,4),('CIOObject',0xc71c18,12),('CIOSocketEx8192',0xb54c18,21),('CIOSocketEx16384',0xbbe878,21),('CSocket',0xd6ab88,24),('CUserSocket',0xdc1c18,24)]:
 values=list(struct.unpack('<'+'Q'*count,o.pe.get_data(at-o.base,count*8)));vtables[name]={'address':hex(at),'slots':[hex(v)for v in values]}
result={'binary_sha256':s['EXPECTED']['x64'],'original_executed':False,'functions':functions,'vtables':vtables}
(out/'EVIDENCE.json').write_text(json.dumps(result,indent=2)+'\n');print('PASS pinned original functions dumped;vtable extents require explicit type validation')
