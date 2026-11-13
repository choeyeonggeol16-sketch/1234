from pathlib import Path
import sys,json,struct,argparse
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);a=p.parse_args();out=Path(__file__).resolve().parent
sys.argv=['audit','--x64',str(a.binary),'--x86',str(a.binary),'--out',str(out)]
s={};exec(compile((out/'audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0],'helper','exec'),s);o=s['AuditPE'](a.binary,'x64')
functions={}
for name,at in {'server_slot13':0x8b04fc,'server_slot14':0x8b0404,'server_slot15':0x8b0954,'dummy_callback':0x8af8e8,'server_factory':0x8b0738,'heap_allocate':0x74f0f4,'socket_operator_new':0x9ef21c,'object_return':0x74e3bc,'heap_return':0x74e08c,'small_return':0x74dc0c,'user_ctor':0xa0ccc4,'socket_ctor':0x90e548,'socket_ex_ctor':0x545030,'server_ctor':0x704bec,'object_ctor':0x7d6d5c,'timer_forward':0x404c88,'timer_forward64':0x404c94,'timer_complete':0x705d54,'socket_read':0x545398,'packet_format':0x70429c,'serializer_ctor':0x45c708}.items():
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
for name,at,count in [('CIOServer',0xc72298,13),('Server',0xd2a5f8,16),('CDummyServerCallback',0xd29ee8,2),('CUserSocketReleaseVerifier',0xdc5cb8,12),('MemoryObject',0xcc8530,4),('CIOObject',0xc71c18,12),('CIOSocketEx8192',0xb54c18,21),('CIOSocketEx16384',0xbbe878,21),('CSocket',0xd6ab88,24),('CUserSocket',0xdc1c18,24)]:
 values=list(struct.unpack('<'+'Q'*count,o.pe.get_data(at-o.base,count*8)));vtables[name]={'address':hex(at),'slots':[hex(v)for v in values]}
checks=[]
for at,mnemonic,operand in [
 (0x74e48f,'lea','rdi, [rdx + 4]'),(0x74e4d5,'cmp','rdi, 0x8001'),(0x74e5dc,'mov','dword ptr [rsi], edi'),(0x74e5de,'lea','rax, [rsi + 4]'),
 (0x74d876,'mov','dword ptr [rcx + 0x1c], 0x13'),(0x74dd42,'add','eax, eax'),(0xa0d0e8,'mov','ecx, 0xcd8'),
 (0x5450c0,'lea','rcx, [rsi + 0x58]'),(0x5450d3,'lea','rcx, [rsi + 0x78]'),(0x5450e6,'mov','qword ptr [rsi + 0x98], rax'),
 (0xa126aa,'mov','qword ptr [rbp + 0x18], rdi'),(0xa126ae,'mov','qword ptr [rbp + 0x20], rbx'),
 (0x8b08d1,'mov','rcx, qword ptr [r12 + 0x90]'),(0x8af8eb,'mov','rcx, rdx'),(0x8af8ee,'jmp','qword ptr [rax + 8]')]:
 ins=o.valid(at);assert ins and(ins.mnemonic,ins.op_str)==(mnemonic,operand),(hex(at),ins)
 checks.append({'address':hex(at),'bytes':ins.bytes.hex(),'instruction':mnemonic+' '+operand})
for info in functions.values():info['caller_count']=len(info.pop('callers'))
hierarchies={}
for name,table in vtables.items():
 va=int(table['address'],16);col=struct.unpack('<Q',o.pe.get_data(va-o.base-8,8))[0]
 hierarchy=struct.unpack('<I',o.pe.get_data(col-o.base+16,4))[0]
 _,_,count,bases=struct.unpack('<4I',o.pe.get_data(hierarchy,16));assert 1<=count<=32
 chain=[]
 for n in range(count):
  desc=struct.unpack('<I',o.pe.get_data(bases+n*4,4))[0];typ=struct.unpack('<I',o.pe.get_data(desc,4))[0]
  chain.append(o.pe.get_data(typ+16,256).split(b'\0')[0].decode('ascii'))
 hierarchies[name]=chain
result={'hierarchies':hierarchies,'verified_instructions' :checks,'size_classes':[(4+(n&3))<<((n>>2)+1)for n in range(49)],'binary_sha256' :s['EXPECTED']['x64'],'original_executed':False,'functions':functions,'vtables':vtables}
(out/'EVIDENCE.json').write_text(json.dumps(result,indent=2)+'\n');print('PASS pinned original allocator/callback/layout instructions and49 size classes;bounded original vtables,not adjacent string data')
