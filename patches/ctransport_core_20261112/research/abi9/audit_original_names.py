"""Pin original names to RTTI/unwind/call sites; never execute the EXE."""
from pathlib import Path
import argparse,json,re,struct,sys
root=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);p.add_argument('--out',type=Path,default=Path(__file__).resolve().parent);args=p.parse_args()
sys.argv=['audit','--x64',str(args.binary),'--x86',str(args.binary),'--out',str(args.out)]
scope={};exec(compile((root/'research/audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0],'helper','exec'),scope)
obj=scope['AuditPE'](args.binary,'x64');pe=obj.pe;base=obj.base
def name(rva):return pe.get_string_at_rva(rva+16).decode()
def rtti(col):
 td,hierarchy=struct.unpack('<II',pe.get_data(col-base+12,8))
 _,_,count,array=struct.unpack('<IIII',pe.get_data(hierarchy,16))
 bases=[]
 for n in range(count):
  desc=struct.unpack('<I',pe.get_data(array+4*n,4))[0];typ=struct.unpack('<I',pe.get_data(desc,4))[0];bases.append(name(typ))
 return {'col':hex(col),'type':name(td),'bases':bases}
index=json.loads((root/'research/server-rtti.json').read_text())['L2Server(10).exe']['rtti']
types={'CIOObject':rtti(int(index['CIOObject'][0]['col'],16)),
       'IServerCallback':rtti(0xec0b78),'CDummyServerCallback':rtti(0xec0be8)}
assert types['CDummyServerCallback']['type']=='.?AVCDummyServerCallback@@'
assert '.?AUIServerCallback@@' in types['CDummyServerCallback']['bases']
assert '.?AVMemoryObject@@' in types['CIOObject']['bases']
functions={}
for label,address in [('const_send',0x90e6c4),('send_v',0x90ee9c),('send_mutable',0x90ed10),('server_run',0x8af9cc),('callback_allocate',0x8af8e8)]:functions[label]=obj.dump(label,address)
targets={}
for match in re.finditer(rb'(?:[\x20-\x7e]\x00){12,}',args.binary.read_bytes()):
 text=match.group().decode('utf-16le')
 for needle in ['CIOObject::SetTimeStamp','CIOObject::SetShortTimeStamp']:
  if needle in text:targets[base+pe.get_rva_from_offset(match.start())]=needle.split('::')[1]
for start,data in obj.text:
 for match in re.finditer(rb'[\x48-\x4f]\x8d[\x05\x0d\x15\x1d\x25\x2d\x35\x3d]',data):
  at=match.start();target=start+at+7+struct.unpack_from('<i',data,at+3)[0]
  if target not in targets:continue
  ins=obj.valid(start+at);chunk=obj.chunk(start+at)
  if ins and ins.size==7 and chunk:functions[targets[target]]=obj.dump(targets[target],chunk[2])
assert all(x in functions for x in ['SetTimeStamp','SetShortTimeStamp'])
verified=[]
for address,mnemonic,operands in [(0x8afa53,'lea','rax, [rip + 0x47a48e]'),(0x8af8ee,'jmp','qword ptr [rax + 8]'),(0x90e812,'call','0x90de2c'),(0x90e843,'call','0xabcd50'),(0x90f025,'call','0x45c7ac')]:
 ins=obj.valid(address);assert ins and (ins.mnemonic,ins.op_str)==(mnemonic,operands)
 verified.append({'at':hex(address),'bytes':ins.bytes.hex(),'mnemonic':mnemonic,'operands':operands})
(args.out/'original-name-evidence.json').write_text(json.dumps({'binary_sha256':scope['EXPECTED']['x64'],'types':types,'functions':functions,'checks':verified,'original_executed':False},indent=2)+'\n')
print('PASS original CDummyServerCallback -> IServerCallback and CIOObject -> MemoryObject RTTI;five exact call/name assertions;constant send and two remaining timestamp helper traces')
