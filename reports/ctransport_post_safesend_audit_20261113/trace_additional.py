"""Static followup; this script never executes the supplied PE."""
from pathlib import Path
import argparse,json,re,struct,sys
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);a=p.parse_args()
out=Path(__file__).parent
sys.argv=['audit','--x64',str(a.binary),'--x86',str(a.binary),'--out',str(out)]
s={};exec(compile((out/'audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0],'helper','exec'),s)
o=s['AuditPE'](a.binary,'x64');raw=a.binary.read_bytes()
needles={'UserExPacket':'UserExPacket(', 'CheckLoginTime':'CUserSocket::CheckLoginTime(', 'OnSending':'CUserSocket::OnSending('}
strings={}
for m in re.finditer(rb'(?:[\x20-\x7e]\x00){10,700}\x00\x00',raw):
 text=m.group()[:-2].decode('utf-16le')
 for label,needle in needles.items():
  if needle in text:strings[o.base+o.pe.get_rva_from_offset(m.start())]={'label':label,'text':text}
refs=[];functions={}
for begin,data in o.text:
 for m in re.finditer(rb'[\x48-\x4f]\x8d[\x05\x0d\x15\x1d\x25\x2d\x35\x3d]',data):
  at=begin+m.start();target=at+7+struct.unpack_from('<i',data,m.start()+3)[0]
  registration=target==0xa035e4
  if target not in strings and not registration:continue
  ins=o.valid(at);chunk=o.chunk(at)
  if not ins or ins.mnemonic!='lea' or ins.size!=7 or not chunk:continue
  item={'label':'UserExPacketRegistration','text':'LEA code pointer 0xa035e4'} if registration else strings[target]
  refs.append(dict(item,at=hex(at),entry=hex(chunk[2])))
  functions[item['label']]=o.dump(item['label'],chunk[2])
for label,entry in [('CanUseBasePacket',0x9ba4f8),('CanUseExtendedPacket',0x9ba6b0),('CheckLoginElapsed',0x9f8210)]:functions[label]=o.dump(label,entry)
functions['ReportPacketLog']=o.dump('ReportPacketLog',0x9fcc98)
functions['RegisterBaseHandler']=o.dump('RegisterBaseHandler',0x9f8eac)
functions['OnRead']=o.dump('OnRead',0xa0372c)
checks=[(0xa03695,'and','edx, 0x7f'),(0xa0369b,'add','dword ptr [rbx + rdx*4 + 0x804], ecx'),
        (0xa036a2,'cmp','edi, 0x97'),(0xa036d4,'call','0x9ba6b0'),
        (0x9fce30,'movzx','ecx, dx'),(0x9fce33,'sar','edx, 0x10'),(0x9fcbb5,'mov','al, 1'),
        (0xb15c78,'lea','r8, [rip - 0x11269b]'),(0xb15c86,'mov','edx, 0xd0'),(0xb15c8b,'call','0x9f8eac'),
        (0x9f8f0f,'mov','qword ptr [rcx + r11*8 + 0x10], rbx'),
        (0xa03956,'shl','edx, 0x10'),(0xa03962,'mov','dword ptr [rsi + rcx*4 + 0x804], edx')]
verified=[]
for at,mnemonic,operands in checks:
 ins=o.valid(at);assert ins and (ins.mnemonic,ins.op_str)==(mnemonic,operands),(hex(at),ins)
 verified.append({'at':hex(at),'bytes':ins.bytes.hex(),'mnemonic':mnemonic,'operands':operands})
(out/'verified.json').write_text(json.dumps({'binary_sha256':s['EXPECTED']['x64'],'instructions':verified},indent=2)+'\n')
result={'binary_sha256':s['EXPECTED']['x64'],'executed_original':False,'strings':{hex(k):v for k,v in strings.items()},'refs':refs,'functions':functions}
(out/'candidates.json').write_text(json.dumps(result,indent=2)+'\n')
for label,info in functions.items():print(label,info['entry'],'callers',len(info['callers']))
