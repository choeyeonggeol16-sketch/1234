from pathlib import Path
import argparse,json,re,struct,sys,hashlib
root=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);p.add_argument('--helper',type=Path);a=p.parse_args();out=Path(__file__).parent
helper=a.helper or root/'research/audit_network_abi5.py'
sys.argv=['audit','--x64',str(a.binary),'--x86',str(a.binary),'--out',str(out)]
s={};exec(compile(helper.read_text().split("x64=AuditPE(a.x64,'x64')")[0],'helper','exec'),s)
o=s['AuditPE'](a.binary,'x64');raw=a.binary.read_bytes()
wanted={'SafeSend':'CUserSocket::SafeSend(','OnSending':'CUserSocket::OnSending(','RequestNetPing':'CUserSocket::RequestNetPing(','ForceClose':'CUserSocket::ForceClose(','OnClose':'CUserSocket::OnClose('}
strings={}
for m in re.finditer(rb'(?:[\x20-\x7e]\x00){10,700}\x00\x00',raw):
 text=m.group()[:-2].decode('utf-16le')
 for label,needle in wanted.items():
  if needle in text:strings[o.base+o.pe.get_rva_from_offset(m.start())]={'label':label,'signature':text}
functions={};refs=[]
for start,data in o.text:
 for m in re.finditer(rb'[\x48-\x4f]\x8d[\x05\x0d\x15\x1d\x25\x2d\x35\x3d]',data):
  at=m.start();target=start+at+7+struct.unpack_from('<i',data,at+3)[0]
  if target not in strings:continue
  ins=o.valid(start+at);c=o.chunk(start+at)
  if not ins or ins.mnemonic!='lea' or ins.size!=7 or not c:continue
  entry=strings[target];label=entry['label'];refs.append({'label':label,'signature':entry['signature'],'string':hex(target),'at':hex(start+at),'function':hex(c[2])})
  functions[label]=o.dump(label,c[2])
extra={'SafeSendCapacity':o.dump('SafeSendCapacity',0x9ef620),'HardWriteCheck':o.dump('HardWriteCheck',0x9ef544)}
for caller in functions['SafeSend']['callers']:
 if caller['function']:
  va=int(caller['function'],16);label='SafeSendCaller_%x'%va;extra[label]=o.dump(label,va)
(out/'extra-functions.json').write_text(json.dumps(extra,indent=2)+'\n')
vtable=0xdc1c18
slots={hex(off):hex(struct.unpack('<Q',o.pe.get_data(vtable+off-o.base,8))[0]) for off in (0x70,0x78,0x88)}
assert slots['0x78']=='0x9ef620'
assert slots['0x70']=='0x9ef544' and slots['0x88']=='0x90ee9c'
expected={0x9ef493:('call','qword ptr [rax + 0x78]'),0x9ef62d:('lea','ecx, [rdx + rax]'),0x9ef630:('cmp','ecx, 0x200000'),0x9ef636:('setle','al'),0x9fcc52:('mov','dword ptr [rcx + 0x53c], edx'),0x9fcc58:('call','0x45d41c'),0x97e626:('mov','edx, 0x80000'),0x97e62b:('call','0x9ef41c'),0x97e7aa:('mov','edx, 0x80000'),0x97e7af:('call','0x9ef41c'),0xa06d85:('mov','edx, 0x80000'),0xa06d8d:('call','0x9ef41c')}
# Leaf instruction validation below uses the entry's reachable disassembly,
# rather than treating an arbitrary byte offset as an instruction boundary.
leaf={i.address:i for i in o.instructions(0x9ef620,0x9ef63a)}
verified=[]
for at,pair in expected.items():
 ins=leaf.get(at) if 0x9ef620<=at<0x9ef63a else o.valid(at)
 assert ins is not None and (ins.mnemonic,ins.op_str)==pair,(hex(at),pair,ins)
 verified.append({'at':hex(at),'bytes':bytes(ins.bytes).hex(),'instruction':' '.join(pair)})
(out/'verified.json').write_text(json.dumps({'binary_sha256':s['EXPECTED']['x64'],'checks':verified},indent=2)+'\n')
contexts={}
for label,info in extra.items():
 if not label.startswith('SafeSendCaller_'):continue
 notes=[]
 for begin,end in info['chunks']:
  for ins in o.instructions(int(begin,16),int(end,16)):
   note=o.annotate(ins)
   if note:notes.append({'at':hex(ins.address),'note':note})
 contexts[label]=notes
result={'binary_sha256':s['EXPECTED']['x64'],'original_executed':False,'signature_refs':refs,'functions':functions,'vtable':hex(vtable),'slots':slots,'caller_strings':contexts,'limits':'Validated direct call references are not an exhaustive proof of all indirect callers.'}
(out/'candidates.json').write_text(json.dumps(result,indent=2)+'\n')
for label,info in functions.items():print(label,info['entry'],'lines',sum(len(o.instructions(int(b,16),int(e,16)))for b,e in info['chunks']),'callers',len(info['callers']),'calls',info['direct_calls'])
