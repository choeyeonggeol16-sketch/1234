from pathlib import Path
import sys,json,struct,re,hashlib,argparse
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);p.add_argument('--source-root',type=Path,required=True);p.add_argument('--out',type=Path,required=True);cli=p.parse_args()
root=cli.source_root;out=cli.out;out.mkdir(parents=True,exist_ok=True)
x64=cli.binary;x86=cli.binary # helper parser requires this argument; only x64 is analyzed
sys.argv=['audit','--x64',str(x64),'--x86',str(x86),'--out',str(out)]
scope={};src=(root/'research/audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0];exec(compile(src,'audit_helper','exec'),scope);obj=scope['AuditPE'](x64,'x64');raw=x64.read_bytes();result={}
for name,addr in [('creature_add_executable',0x466d44),('socket_add_executable',0x45c7ac),('fast_serializer_constructor',0x45c708),('user_socket_constructor',0xa0ccc4),('socket_constructor',0x90e548),('io_socket_constructor',0x545030)]:
 result[name]=obj.dump(name,addr)
print('Validated direct callers:',len(result['creature_add_executable']['callers']))
for idx,caller in enumerate(result['creature_add_executable']['callers']):
 if caller['function']:obj.dump('creature_add_caller_'+caller['function'][2:],int(caller['function'],16))
# Resolve primary CCreatureController RTTI vtable and every validated direct LEA reference.
needle=b'.?AVCCreatureController@@\0';offset=raw.find(needle);assert offset>=0;type_rva=obj.pe.get_rva_from_offset(offset)-16;tables=[]
for m in re.finditer(re.escape(struct.pack('<I',type_rva)),raw):
 if m.start()<12:continue
 try:
  crva=obj.pe.get_rva_from_offset(m.start()-12);d=obj.pe.get_data(crva,24);sig=struct.unpack_from('<I',d)[0]
  if sig not in [0,1] or(sig==1 and struct.unpack_from('<I',d,20)[0]!=crva)or struct.unpack_from('<I',d,4)[0]!=0:continue
  for v in re.finditer(re.escape(struct.pack('<Q',obj.base+crva)),raw):
   vrva=obj.pe.get_rva_from_offset(v.start())+8;slots=struct.unpack('<QQQ',obj.pe.get_data(vrva,24))
   if all(obj.pe.get_section_by_rva(x-obj.base) and obj.pe.get_section_by_rva(x-obj.base).Characteristics&0x20000000 for x in slots):tables.append(obj.base+vrva)
 except Exception:pass
vtrefs=[]
for table in sorted(set(tables)):
 for begin,data in obj.text:
  for m in re.finditer(b'[\x48\x4c]\x8d[\x05\x0d\x15\x1d\x25\x2d\x35\x3d]',data):
   at=m.start()
   if at+7<=len(data) and begin+at+7+struct.unpack_from('<i',data,at+3)[0]==table:
    ins=obj.valid(begin+at);chunk=obj.chunk(begin+at)
    if ins and chunk:
     entry=chunk[2];label='controller_vtable_ref_'+hex(entry)[2:];info=obj.dump(label,entry);vtrefs.append({'table':hex(table),'at':hex(begin+at),'function':hex(entry),'calls':info['direct_calls']});
     for c in info['direct_calls'][:5]:obj.dump(label+'_call_'+c[2:],int(c,16))
result['controller_vtables']=list(map(hex,tables));result['controller_vtable_refs']=vtrefs
result['input']={'file':x64.name,'sha256':hashlib.sha256(raw).hexdigest(),'image_base':hex(obj.base),'analysis':'static PE unwind/instruction-boundary validation; original executable not run'}
result['java_source_sha256']=hashlib.sha256((root/'src/l1j/server/server/model/character/action/CCreatureController.java').read_bytes()).hexdigest()
(out/'findings-raw.json').write_text(json.dumps(result,indent=2)+'\n');print('Controller vtables:',list(map(hex,tables)));print('saved',str(out))

checks=[(0x466efa,'cmp','eax, 0x18'),(0x466fc6,'and','ebx, 0x1f'),(0x4671cc,'and','ebx, 0x1f'),(0x466dc9,'cmp','eax, dword ptr [r14 + r13]'),(0x466dcd,'jne','0x466ef7'),(0x466edb,'call','rbp'),(0x466fbd,'lock xadd','qword ptr [rcx + 8], rbx'),(0x466fe9,'lock xadd','dword ptr [r12 + 0x10], eax'),(0x46721a,'call','rax'),(0x5a84b0,'call','0x5a6e00'),(0x5a6e2b,'mov','r8d, 0xa00'),(0x5a6e3f,'mov','r8d, 0x100'),(0x5723ae,'call','0x466d44'),(0x5ad62f,'call','0x466d44'),(0x8af63a,'call','0x466d44'),(0x466f96,'call','0x734514'),(0x466faf,'jmp','0x467258')]
verified=[]
for addr,mnemonic,operands in checks:
 ins=obj.valid(addr);assert ins and(ins.mnemonic,ins.op_str)==(mnemonic,operands),(hex(addr),ins)
 verified.append({'address':hex(addr),'bytes':ins.bytes.hex(),'mnemonic':mnemonic,'operands':operands})
assert 'CExecutionSerializer<class CCreatureController,32>::AddExecutable' in(out/'creature_add_executable.asm.txt').read_text()
assert 'CCreatureController::CCreatureController(void)' in(out/'controller_vtable_ref_5a844c.asm.txt').read_text()
(out/'verified-instructions.json').write_text(json.dumps({'input':result['input'],'checks':verified,'java_source_sha256':result['java_source_sha256'],'source_modified':False,'original_executable_run':False},indent=2)+'\n')
print('PASS',len(checks),'exact instructions,32-slot signature,controller constructor and movement/timer/damage call paths')
