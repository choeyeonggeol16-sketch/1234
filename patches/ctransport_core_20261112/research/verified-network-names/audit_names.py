"""Bind exact timer/network spellings to validated original references."""
from pathlib import Path
import argparse,hashlib,json,sys,re,struct
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);a=p.parse_args();out=Path(__file__).resolve().parent
sys.argv=['audit','--x64',str(a.binary),'--x86',str(a.binary),'--out',str(out)]
s={};exec(compile((out/'audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0],'audit helper','exec'),s)
o=s['AuditPE'](a.binary,'x64');raw=a.binary.read_bytes()
strings={}
for m in re.finditer(rb'(?:[\x09\x20-\x7e]\x00){10,1000}\x00\x00',raw):
 t=m.group()[:-2].decode('utf-16le')
 if ('__cdecl' in t or '__thiscall' in t)and any(name in t for name in ['CThreadLocalTimer::','CMwmrQueue<','CUserSocketReleaseVerifier::']):strings[o.base+o.pe.get_rva_from_offset(m.start())]=t
refs={va:[]for va in strings}
for begin,data in o.text:
 for m in re.finditer(rb'[\x40-\x4f]?\x8d[\x05\x0d\x15\x1d\x25\x2d\x35\x3d][\s\S]{4}',data):
  at=begin+m.start();size=len(m.group());target=at+size+struct.unpack_from('<i',m.group(),size-4)[0]
  if target not in refs:continue
  ins=o.valid(at);c=o.chunk(at)
  if not ins or not c or ins.mnemonic!='lea' or ins.size!=size:continue
  refs[target].append({'at':hex(at),'entry':hex(c[2]),'bytes':ins.bytes.hex()})
functions={};items=[]
for target,name in strings.items():
 assert refs[target],name
 for ref in refs[target]:
  entry=int(ref['entry'],16)
  if entry not in functions:
   info=o.dump('fn_%x'%entry,entry);info['caller_count']=len(info.pop('callers'));functions[entry]=info
 items.append({'literal':hex(target),'signature':name.strip(),'references':refs[target]})
# Recover the actual RTTI type/vtable, not merely the presence of an ASCII word.
marker=b'.?AVCIOThreadCallback@@\0';hit=raw.index(marker);type_rva=o.pe.get_rva_from_offset(hit)-16
vtables=[];pos=0
while True:
 pos=raw.find(struct.pack('<I',type_rva),pos)
 if pos<0:break
 at=pos;pos+=1
 if at<12:continue
 try:
  col_rva=o.pe.get_rva_from_offset(at-12);col=o.pe.get_data(col_rva,24)
  signature=struct.unpack_from('<I',col)[0]
  if signature not in (0,1) or signature==1 and struct.unpack_from('<I',col,20)[0]!=col_rva:continue
  pointer=struct.pack('<Q',o.base+col_rva);cursor=0
  while True:
   cursor=raw.find(pointer,cursor)
   if cursor<0:break
   address=o.base+o.pe.get_rva_from_offset(cursor)+8;cursor+=8
   slots=[]
   for i in range(24):
    va=struct.unpack('<Q',o.pe.get_data(address-o.base+i*8,8))[0]
    if not(o.base<=va<o.base+o.pe.OPTIONAL_HEADER.SizeOfImage):break
    section=o.pe.get_section_by_rva(va-o.base)
    if not section or not section.Characteristics&0x20000000:break
    slots.append(hex(va));info=o.dump('rtti_%x'%va,va);info['caller_count']=len(info.pop('callers'));functions[va]=info
   if slots:vtables.append({'vtable':hex(address),'col':hex(o.base+col_rva),'slots':slots})
 except Exception:continue
assert vtables
for label,entry in [('CIOThreadCallback_body',0x72c1e0)]:
 info=o.dump(label,entry);info['caller_count']=len(info.pop('callers'));functions[entry]=info
result={'binary_sha256':s['EXPECTED']['x64'],'original_executed':False,'method':'Exact UTF16 signature with validated RIP reference/pdata boundary;validated RTTI COL/vtable for CIOThreadCallback;no name inferred from role alone.','items':items,'CIOThreadCallback':{'type_rva':hex(type_rva),'vtables':vtables},'functions':{hex(k):v for k,v in functions.items()},'CIOThreadCallback_role':'vtable0xc86678 -> thunk0x72c3a8 ->0x72c1e0;World_Sync and garbage collect;not the L1 worker host',
 'unknown_names':['Return routine0x925630 name is not disclosed;Return is an L1 adapter name.','CSlot insertion0x92533c method name is not disclosed.','Heap-record type name is not disclosed.']}
(out/'VERIFIED_NAMES.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'signatures':[(x['signature'],x['references'])for x in items],'CIOThreadCallback':vtables},indent=2))
