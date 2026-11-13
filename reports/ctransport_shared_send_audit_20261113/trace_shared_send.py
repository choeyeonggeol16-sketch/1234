"""Pinned, read-only trace of shared constant-send RTTI and dispatch."""
from pathlib import Path
import argparse,json,struct,re,sys,hashlib
root=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);p.add_argument('--out',type=Path,default=Path(__file__).resolve().parent);p.add_argument('--source-root',type=Path);args=p.parse_args()
sys.argv=['audit','--x64',str(args.binary),'--x86',str(args.binary),'--out',str(args.out)]
helper=Path(__file__).with_name('audit_helpers.py')
if not helper.exists():helper=root/'research/audit_network_abi5.py'
helpers={};exec(compile(helper.read_text().split("x64=AuditPE(a.x64,'x64')")[0],'audit_helpers','exec'),helpers)
obj=helpers['AuditPE'](args.binary,'x64');pe=obj.pe;base=obj.base;raw=args.binary.read_bytes()
type_name=b'.?AVCAsyncSendConstBufferExceptSelf@@';offset=raw.index(type_name)-16;type_rva=pe.get_rva_from_offset(offset)
cols=[];tables=[];functions={}
for section in pe.sections:
 if section.Characteristics&0x20000000:continue
 data=section.get_data();begin=section.PointerToRawData
 for match in re.finditer(re.escape(struct.pack('<I',type_rva)),data):
  pos=begin+match.start()-12
  if pos<0:continue
  sig,off,cd,td,hierarchy=struct.unpack_from('<IIIII',raw,pos)
  self_rva=pe.get_rva_from_offset(pos)
  if sig not in (0,1) or td!=type_rva:continue
  if sig==1 and struct.unpack_from('<I',raw,pos+20)[0]!=self_rva:continue
  if hierarchy<0x1000 or hierarchy>=pe.OPTIONAL_HEADER.SizeOfImage:continue
  _,_,count,array=struct.unpack('<IIII',pe.get_data(hierarchy,16));bases=[]
  if not 1<=count<=32 or not 0x1000<=array<pe.OPTIONAL_HEADER.SizeOfImage:continue
  for n in range(count):
   desc=struct.unpack('<I',pe.get_data(array+n*4,4))[0];typ=struct.unpack('<I',pe.get_data(desc,4))[0];bases.append(pe.get_string_at_rva(typ+16).decode())
  cols.append({'col':hex(base+self_rva),'offset':off,'bases':bases})
for col in cols:
 target=int(col['col'],16)
 for section in pe.sections:
  if section.Characteristics&0x20000000:continue
  data=section.get_data()
  for match in re.finditer(re.escape(struct.pack('<Q',target)),data):
   table=base+section.VirtualAddress+match.start()+8;slots=[]
   for n in range(24):
    method=struct.unpack('<Q',pe.get_data(table-base+n*8,8))[0]
    if not any(begin<=method<begin+len(data) for begin,data in obj.text):break
    if not obj.valid(method):break
    slots.append(hex(method));label='functor_slot_'+str(len(tables))+'_'+str(n);functions[label]=obj.dump(label,method)
   tables.append({'table':hex(table),'col':col,'slots':slots})
# Instruction-boundary verified references to the installed functor table.
refs=[];wanted={int(t['table'],16) for t in tables}
for start,data in obj.text:
 for match in re.finditer(rb'[\x48-\x4f]\x8d[\x05\x0d\x15\x1d\x25\x2d\x35\x3d]',data):
  at=match.start();target=start+at+7+struct.unpack_from('<i',data,at+3)[0]
  if target not in wanted:continue
  ins=obj.valid(start+at);chunk=obj.chunk(start+at)
  if ins and ins.size==7 and chunk:
   refs.append({'at':hex(start+at),'target':hex(target),'entry':hex(chunk[2])})
   label='functor_table_ref_'+hex(chunk[2])[2:];functions[label]=obj.dump(label,chunk[2])
assert len(tables)==1 and tables[0]['table']=='0xbcdf68'
assert tables[0]['col']['bases']==['.?AVCAsyncSendConstBufferExceptSelf@@','.?AVCAsyncSendConstBuffer@@','.?AVCTLMFunc@@']
assert tables[0]['slots']==['0x9454fc','0x9241f8','0x56daa8']
assert refs==[{'at':'0x57e420','target':'0xbcdf68','entry':'0x57e1d8'}]
targets={'broadcast_format':0x41f694,'buffer_alloc':0x41e6e4,'neighbor_base_ctor':0x922fc0,'neighbor_ctor':0x923468,'neighbor_each':0x924a9c,'neighbor_scan':0x924318,'const_send':0x90e6c4,'serialize_callback':0x45c7ac,'overflow_callstack_logging':0x6704e0}
targets.update({'caller_'+hex(v)[2:]:v for v in [0x5805c8,0x580aa4,0x580fb8,0x70736c,0x707724,0x967b90]})
classes={}
# Resolve User/filter virtual slots from their own COL, rather than guessing
# IsUser/GetUser/GetSocket names from the call offsets alone.
for name,slots in {'User':[0xb0,0x168,0x208,0xb28],'CObject':[0xb0,0x168,0x208],'CUserSocket':[0x70],'CAsyncSendConstBuffer':[0,8,16],'CTLMFilterContainer':[0,8]}.items():
 td=pe.get_rva_from_offset(raw.index(('.?AV'+name+'@@').encode())-16);entries=[]
 for sec in pe.sections:
  if sec.Characteristics&0x20000000:continue
  for m in re.finditer(re.escape(struct.pack('<I',td)),sec.get_data()):
   pos=sec.PointerToRawData+m.start()-12
   if pos<0:continue
   sig,off,cd,typ,h=struct.unpack_from('<IIIII',raw,pos)
   if sig not in (0,1) or typ!=td or not 0x1000<h<pe.OPTIONAL_HEADER.SizeOfImage:continue
   if sig==1 and struct.unpack_from('<I',raw,pos+20)[0]!=pe.get_rva_from_offset(pos):continue
   _,_,count,array=struct.unpack('<IIII',pe.get_data(h,16))
   if not 1<=count<=32 or not 0x1000<array<pe.OPTIONAL_HEADER.SizeOfImage:continue
   col=base+pe.get_rva_from_offset(pos)
   for sec2 in pe.sections:
    if sec2.Characteristics&0x20000000:continue
    for ref in re.finditer(re.escape(struct.pack('<Q',col)),sec2.get_data()):
     table=base+sec2.VirtualAddress+ref.start()+8;methods={}
     for slot in slots:
      va=struct.unpack('<Q',pe.get_data(table-base+slot,8))[0]
      if not any(b<=va<b+len(d)for b,d in obj.text):continue
      label=name+'_slot_'+hex(slot)[2:];targets[label]=va;methods[hex(slot)]=hex(va)
     entries.append({'table':hex(table),'offset':off,'slots':methods})
 classes[name]=entries
for label,va in targets.items():functions[label]=obj.dump(label,va)
checks=[
 (0x57e261,'mov','eax, dword ptr [rdx + 0x18]'),
 (0x57e2e9,'call','0x41f694'),
 (0x57e410,'mov','qword ptr [rsp + 0x38], rbp'),
 (0x57e436,'call','0x924a9c'),
 (0x57e43b,'lock add','dword ptr [rbp + 0x4020], -1'),
 (0x92420c,'call','qword ptr [rax + 0x168]'),
 (0x92421c,'call','qword ptr [rax + 0x208]'),
 (0x924225,'cmp','dword ptr [rax + 0x18], ecx'),
 (0x92422b,'je','0x92427f'),
 (0x924233,'call','qword ptr [rdx + 0xb28]'),
 (0x924239,'mov','ecx, dword ptr [rax + 0x20]'),
 (0x92423c,'cmp','dword ptr [rsi + 0x18], ecx'),
 (0x92423f,'jle','0x92427f'),
 (0x924245,'lock add','dword ptr [rax + 0x4020], 1'),
 (0x92427a,'call','0x45c7ac'),
 (0x9244a7,'call','qword ptr [r8 + 8]'),
 (0x9244bd,'call','qword ptr [r8 + 8]'),
 (0x90e758,'add','dword ptr [rcx + 0x20], eax'),
 (0x9ef5ca,'cmp','dword ptr [rcx + 0x20], 0x200000'),
 (0x90e812,'call','0x90de2c'),
 (0x90e843,'call','0xabcd50'),
 (0x90e8e6,'call','qword ptr [rip + 0x20dd94]'),
 (0x90eb08,'lock xadd','dword ptr [rbx + 0x4020], eax'),
 (0x90eb4c,'lock xadd','dword ptr [rdx + 0x4020], eax'),
 (0x45c82c,'call','r11'),
 (0x45c994,'call','r8'),
 (0x58090e,'mov','r8d, 0x5000'),
 (0x7074ae,'mov','r8d, 0x80000'),
]
verified=[]
for va,mnemonic,operand in checks:
 ins=obj.valid(va)
 assert ins and (ins.mnemonic,ins.op_str)==(mnemonic,operand),(hex(va),ins)
 verified.append({'at':hex(va),'bytes':bytes(ins.bytes).hex(),'instruction':mnemonic+' '+operand})
for info in functions.values():
 info['caller_count']=len(info['callers']);info['callers']=info['callers'][:24];info['callers_truncated']=info['caller_count']>24
result={'binary_sha256':helpers['EXPECTED']['x64'],'image_base':hex(base),'type_descriptor':hex(base+type_rva),'cols':cols,'tables':tables,'table_refs':refs,'classes':classes,'functions':functions,'verified_instructions':verified,'original_executed':False}
(args.out/'trace-evidence.json').write_text(json.dumps(result,indent=2)+'\n')
if args.source_root:
 index={}
 for folder in ['src','native']:
  for path in sorted((args.source_root/folder).rglob('*')):
   if path.is_file():index[str(path.relative_to(args.source_root))]=hashlib.sha256(path.read_bytes()).hexdigest()
 encoded=''.join(k+'\0'+v+'\n'for k,v in sorted(index.items())).encode()
 manifest={'baseline_git_commit':'fcec7bd7d6a443a09743d53d899d186796934c99','files':index,'file_count':len(index),'index_sha256':hashlib.sha256(encoded).hexdigest()}
 (args.out/'production-source-hashes.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'instruction_assertions':len(verified),'functions_dumped':len(functions),'classes':classes,'original_executed':False},indent=2))
