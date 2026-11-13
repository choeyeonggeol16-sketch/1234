"""Read-only PE/source audit. Candidate scans are validated against instructions.
This tool never runs the supplied executables or writes production sources.
"""
from pathlib import Path
from functools import lru_cache
from bisect import bisect_right
from collections import defaultdict
import argparse,hashlib,json,re,struct
import pefile,capstone
from capstone.x86 import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
EXPECTED={'x64':'9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588','x86':'8eecd317fb152b76f32a0a6a65fbf916e39d20511e203b4a0ea602fe041a22f5'}
p=argparse.ArgumentParser();p.add_argument('--x64',type=Path,required=True);p.add_argument('--x86',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
class AuditPE:
 def __init__(self,path,arch):
  self.path=path;self.pe=pefile.PE(str(path));self.base=self.pe.OPTIONAL_HEADER.ImageBase;self.bits=64 if arch=='x64'else 32
  digest=hashlib.sha256(path.read_bytes()).hexdigest();assert digest==EXPECTED[arch],(path,digest)
  self.imports={i.address:i.name.decode()for m in self.pe.DIRECTORY_ENTRY_IMPORT for i in m.imports if i.name}
  self.md=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64 if self.bits==64 else capstone.CS_MODE_32);self.md.detail=True
  self.ranges=[];self.groups=defaultdict(list);self.start=[];self.direct=defaultdict(list);self.indirect=defaultdict(list);self.resolved={}
  if self.bits==64:
   @lru_cache(None)
   def root(begin,end,unwind):
    raw=self.pe.get_data(unwind,4)
    if raw[0]>>3&4:
     offset=4+((raw[2]*2+3)//4)*4
     return root(*struct.unpack('<III',self.pe.get_data(unwind+offset,12)))
    return begin
   for entry in self.pe.DIRECTORY_ENTRY_EXCEPTION:
    s=entry.struct;r=root(s.BeginAddress,s.EndAddress,s.UnwindData)
    chunk=(self.base+s.BeginAddress,self.base+s.EndAddress);self.ranges.append((*chunk,self.base+r));self.groups[self.base+r].append(chunk)
   self.ranges.sort();self.start=[r[0]for r in self.ranges]
  self.text=[]
  for sec in self.pe.sections:
   if sec.Characteristics&0x20000000:self.text.append((self.base+sec.VirtualAddress,self.pe.get_data(sec.VirtualAddress,sec.Misc_VirtualSize)))
  for start,data in self.text:
   for m in re.finditer(b'[\xe8\xe9]',data):
    pos=m.start()
    if pos+5<=len(data):self.direct[start+pos+5+struct.unpack_from('<i',data,pos+1)[0]].append(start+pos)
   for m in re.finditer(b'\xff[\x15\x25]',data):
    pos=m.start()
    if pos+6>len(data):continue
    operand=struct.unpack_from('<i'if self.bits==64 else '<I',data,pos+2)[0]
    target=start+pos+6+operand if self.bits==64 else operand;self.indirect[target].append(start+pos)
 def chunk(self,va):
  at=bisect_right(self.start,va)-1
  if at>=0 and self.ranges[at][0]<=va<self.ranges[at][1]:return self.ranges[at]
  return None
 @lru_cache(None)
 def instructions(self,begin,end):return list(self.md.disasm(self.pe.get_data(begin-self.base,end-begin),begin))
 def valid(self,va):
  chunk=self.chunk(va)
  if chunk:return next((ins for ins in self.instructions(chunk[0],chunk[1])if ins.address==va),None)
  # x86 has no pdata: import xrefs below are explicitly candidate-only until
  # enclosing function entry and reachability are checked by disassembly.
  return next(self.md.disasm(self.pe.get_data(va-self.base,15),va),None)
 def calls_to(self,target):
  found=[]
  for va in self.direct[target]:
   ins=self.valid(va)
   if ins and ins.mnemonic in ('call','jmp') and ins.operands[0].type==X86_OP_IMM and ins.operands[0].imm==target:
    c=self.chunk(va);found.append({'at':hex(va),'op':ins.mnemonic,'function':hex(c[2])if c else None,'validation':'pdata instruction boundary'if c else'entry-only candidate'})
  return found
 def import_calls(self,name):
  result=[]
  for iat,label in self.imports.items():
   if label!=name:continue
   for va in self.indirect[iat]:
    ins=self.valid(va)
    if ins and ins.mnemonic in ('call','jmp'):
     c=self.chunk(va);result.append({'at':hex(va),'op':ins.mnemonic,'function':hex(c[2])if c else None,'validation':'pdata instruction boundary'if c else'entry-only candidate'})
  return result
 def annotate(self,ins):
  notes=[]
  for op in ins.operands:
   if op.type!=X86_OP_MEM:continue
   target=ins.address+ins.size+op.mem.disp if op.mem.base==X86_REG_RIP else op.mem.disp if self.bits==32 and op.mem.base==0 else None
   if target in self.imports:notes.append('IAT '+self.imports[target])
   if target is not None:
    try:
     data=self.pe.get_data(target-self.base,640)
     if len(data)>=8:
      text=data.split(b'\x00\x00')[0]
      # Decode strings only when the initial UTF16 characters are printable.
      chars=[]
      for at in range(0,len(data)-1,2):
       word=int.from_bytes(data[at:at+2],'little')
       if word==0:break
       if word<32 or word>126:chars=[];break
       chars.append(chr(word))
      if len(chars)>=4:notes.append('UTF16 '+repr(''.join(chars)))
    except Exception:pass
  return notes
 def leaf_chunks(self,va):
  pending=[va];seen={}
  while pending and len(seen)<512:
   at=pending.pop()
   if at in seen or not va<=at<va+512:continue
   ins=next(self.md.disasm(self.pe.get_data(at-self.base,15),at),None)
   if not ins:continue
   seen[at]=ins
   if ins.mnemonic in ('ret','retf','int3','ud2'):continue
   jump=ins.mnemonic.startswith('j')or ins.mnemonic in ('loop','loope','loopne')
   if jump and ins.operands and ins.operands[0].type==X86_OP_IMM:
    pending.append(ins.operands[0].imm)
   if ins.mnemonic!='jmp':pending.append(at+ins.size)
  chunks=[]
  for at,ins in sorted(seen.items()):
   if chunks and chunks[-1][1]==at:chunks[-1]=(chunks[-1][0],at+ins.size)
   else:chunks.append((at,at+ins.size))
  return chunks
 def dump(self,label,va):
  c=self.chunk(va);lines=[];calls=[]
  if c:
   chunks=sorted(self.groups[c[2]]);proof='PE pdata/unwind';entry=c[2]
  else:
   # Bounded leaf slice: do not invent a 768-byte function extent. End at
   # first return/unconditional jump and mark the scope as incomplete.
   chunks=self.leaf_chunks(va)
   proof='reachable leaf CFG within 512 bytes; no pdata function boundary';entry=va
  lines.append('# '+label+'; '+proof+'; entry '+hex(entry))
  for begin,end in chunks:
   lines.append('# chunk '+hex(begin)+'..'+hex(end))
   for ins in self.instructions(begin,end):
    notes=self.annotate(ins)
    if ins.mnemonic=='call'and ins.operands[0].type==X86_OP_IMM:calls.append(hex(ins.operands[0].imm))
    lines.append(f'{ins.address:016x}: {ins.mnemonic:8s} {ins.op_str}'+(' ; '+' | '.join(notes)if notes else''))
  (a.out/(label+'.asm.txt')).write_text('\n'.join(line.rstrip()for line in lines)+'\n')
  result={'entry':hex(entry),'scope':proof,'chunks':[[hex(b),hex(e)]for b,e in chunks],'direct_calls':calls,'callers':self.calls_to(entry)};self.resolved[label]=result;return result

x64=AuditPE(a.x64,'x64');x86=AuditPE(a.x86,'x86')
imports=['accept','WSAEventSelect','WSACreateEvent','listen','bind','socket','closesocket','WSAEnumNetworkEvents','WSAWaitForMultipleEvents','CreateIoCompletionPort','GetQueuedCompletionStatus','GetQueuedCompletionStatusEx','PostQueuedCompletionStatus','WaitForMultipleObjects','WaitForSingleObject','SetThreadAffinityMask','WSARecv','WSASend','ResetEvent','SetEvent','ExitProcess','CreateThread','CloseHandle']
import_refs={arch:{name:obj.import_calls(name)for name in imports}for arch,obj in [('x64',x64),('x86',x86)]}
(a.out/'import-xrefs.json').write_text(json.dumps(import_refs,indent=2)+'\n')
targets={'wndproc_service_control':0x72d880,'socket_number_map_add':0xa08138,'user_ctor_tail_a':0x9f7f04,'user_ctor_tail_b':0xa07ee8,'user_destructor':0xa0169c,'user_timer':0xa0d164,'ip_socket_limiter_decrement':0xa0dc04,'mac_socket_limiter_decrement':0xa0ded4,'callback_adapter_allocate':0x8af8e8,'callback_timing_enter':0x926e2c,'callback_timing_leave':0x926e54,'listener_run':0x8af8f4,'listener_base_ctor':0x704bec,'listener_base_close':0x704c7c,'manager_tree_find':0x96986c,'manager_destructor':0x8b0e54,'listener_base_destructor':0x7054bc,'worker_alt_entry':0x705ab0,'event_register':0x706564,'candidate_705318':0x705318,'wait_thread_state':0x7053e8,'io_worker_entry':0x705a4c,'io_worker_body':0x7056ac,'io_bootstrap':0x704754,'service_thread_boot':0x730300,'thread_seed_crt':0xab9520,'thread_seed_custom':0xa22898,'socket_init_io':0x545260,'user_write_validity':0x9ef544,'user_write_capacity':0x9ef620,'manager_tree_find':0x96986c,'receive_submit_16k':0x5456d0,'enqueue_async_write_16k':0x545a18,'write_completion_16k':0x545e38,'socket_write_packet':0x90e1d0,'io_dispatch_16k':0x54655c,'serialized_callback':0x45c7ac,'user_ctor':0xa0ccc4,'socket_base_ctor':0x90e548,'manager_register':0x8b0404,'manager_unregister':0x8b04fc,'manager_snapshot_a':0x8b0954,'manager_snapshot_b':0x8b0bac,'manager_ctor':0x8b0f1c,'npc_add_override':0x7ad220,'listener_start':0x8af9cc,'service_main':0x72f874,'service_entry':0x73029c,'worker_count':0x7044c4,'worker_start':0x705b70,'worker_entry':0x705b38,'worker_main':0x7056ac,'thread_start_crt_candidate':0xabf890,'thread_rng_seed_init':0xa22ae8,'service_listener_factory_a':0x72fec9+7+0x80774,'service_listener_factory_b':0x72feed+7+0x2dd188,'service_listener_factory_c':0x72ff19+7-0xd6d8}
for name,va in targets.items():x64.dump(name,va)
# Import call sites have exact x64 pdata boundaries. Dump the whole enclosing
# function; no role is assigned merely because it is a nearby routine.
for api in ['accept','WSAEventSelect','WSACreateEvent','listen','bind','WaitForMultipleObjects']:
 for item in import_refs['x64'][api]:
  if item['function']:x64.dump('api_'+api+'_'+item['function'][2:],int(item['function'],16))
for name in ['user_ctor','listener_start','npc_add_override','service_entry','thread_rng_seed_init']:
 for ref in x64.resolved[name]['callers']:
  if ref['function']:x64.dump('caller_'+name+'_'+ref['function'][2:],int(ref['function'],16))
# Virtual dispatch is resolved from the Server table installed by its
# constructor. Stop at a non-code target rather than reading adjacent data.
vtables={}
for label,table in [('Server',0x8b0f3b+7+0x4796b6),('CUserSocket',0xdc1c18),('IServerCallbackAdapter',0x8afa53+7+0x47a48e)]:
 entries=[]
 for slot in range({'Server':16,'CUserSocket':24,'IServerCallbackAdapter':2}[label]):
  target=struct.unpack('<Q',x64.pe.get_data(table-x64.base+slot*8,8))[0]
  if not any(begin<=target<begin+len(data)for begin,data in x64.text):break
  entries.append({'slot':slot,'offset':hex(slot*8),'target':hex(target)})
  if label in ('Server','IServerCallbackAdapter') and slot in (0,8,9,10,11,12,13,14,15):x64.dump('vtable_'+label+'_'+str(slot),target)
 vtables[label]={'address':hex(table),'entries':entries}
(a.out/'vtables.json').write_text(json.dumps(vtables,indent=2)+'\n')
# x86 import sites are cross-checked within explicitly bounded reachable CFGs.
# Function starts below are determined from prologues, not the x64 offsets.
x86_xref_sources={}
for name in ['accept','WSAEventSelect','listen','WaitForMultipleObjects']:
 for ref in import_refs['x86'][name]:
  call=int(ref['at'],16);raw=x86.pe.get_data(call-x86.base-1024,1024);prologues=[m.start()for m in re.finditer(b'\x55\x8b\xec',raw)]
  for relative in reversed(prologues):
   entry=call-1024+relative;chunks=x86.leaf_chunks(entry)
   if not any(b<=call<e for b,e in chunks):continue
   result=x86.dump('x86_'+name+'_'+hex(entry)[2:],entry)
   x86_xref_sources[name]={'call':hex(call),'entry':hex(entry),'scope':result['scope']};break
(a.out/'x86-crosscheck.json').write_text(json.dumps(x86_xref_sources,indent=2)+'\n')
# Record only instruction-validated references to the game Server instance
# used by CUserSocket::OnClose. These catch virtual call sites that have no E8.
game_manager=0xa12e65+7+0x26350b4
refs=[]
for begin,blob in x64.text:
 for m in re.finditer(b'\x48\x8d',blob):
  pos=m.start()
  if pos+7>len(blob) or blob[pos+2]&0xc7!=5:continue
  va=begin+pos
  if va+7+struct.unpack_from('<i',blob,pos+3)[0]!=game_manager:continue
  ins=x64.valid(va);c=x64.chunk(va)
  if not ins or ins.mnemonic!='lea' or not c:continue
  refs.append({'at':hex(va),'function':hex(c[2])})
  if c[2] not in [int(v['entry'],16)for v in x64.resolved.values()]:x64.dump('game_Server_ref_'+hex(c[2])[2:],c[2])
(a.out/'game-manager-xrefs.json').write_text(json.dumps({'game_Server':hex(game_manager),'references':refs},indent=2)+'\n')
# Signature strings identify separate user number/login/account map variants.
# Each reference must decode as a real instruction before a role is recorded.
raw=a.x64.read_bytes();needle='CObjectMap<class CUserSocket'.encode('utf-16le');at=0;signatures=[]
while True:
 hit=raw.find(needle,at)
 if hit<0:break
 at=hit+len(needle);begin=end=hit
 while begin>=2 and hit-begin<800 and raw[begin-2:begin]!=b'\0\0':begin-=2
 while end+2<len(raw)and end-hit<1200 and raw[end:end+2]!=b'\0\0':end+=2
 try:signature=raw[begin:end].decode('utf-16le');address=x64.base+x64.pe.get_rva_from_offset(begin)
 except Exception:continue
 refs=[]
 for section_begin,blob in x64.text:
  for m in re.finditer(b'\x48\x8d',blob):
   pos=m.start()
   if pos+7>len(blob)or blob[pos+2]&0xc7!=5:continue
   va=section_begin+pos
   if va+7+struct.unpack_from('<i',blob,pos+3)[0]!=address:continue
   ins=x64.valid(va);c=x64.chunk(va)
   if not ins or ins.mnemonic!='lea'or not c:continue
   refs.append({'at':hex(va),'function':hex(c[2])})
   if any(t in signature for t in ['ExistKey','DeleteObject','Insert']):x64.dump('user_map_'+hex(c[2])[2:],c[2])
 signatures.append({'signature':signature,'address':hex(address),'references':refs})
(a.out/'user-object-maps.json').write_text(json.dumps(signatures,ensure_ascii=False,indent=2)+'\n')
# The structural claims below are deterministic evidence checks, not game tests.
assert vtables['Server']['entries'][9]['target']=='0x704d1c'
assert vtables['Server']['entries'][10]['target']=='0x705508'
assert vtables['Server']['entries'][12]['target']=='0x8b0738'
assert vtables['Server']['entries'][14]['target']=='0x8b0404'
assert vtables['Server']['entries'][15]['target']=='0x8b0954'
assert import_refs['x64']['accept'][0]['at']=='0x705585'
assert import_refs['x64']['WSAEventSelect'][0]['at']=='0x706819'
assert 'accept'in x86_xref_sources and 'WSAEventSelect'in x86_xref_sources
assert any('8192,26' in v['signature']and'Insert' in v['signature']for v in signatures)
# Preserve exactly which implementation files were read. Native/source files
# are only hashed; the audit does not edit the engine or rebuild the DLL.
root=Path(__file__).resolve().parents[1];selected=list((root/'native').glob('*'))
core=root/'src/l1j/server/server/model/network/core'
selected += [p for p in core.glob('*.java')]
selected += [root/'src/l1j/server/Server.java',root/'src/l1j/server/server/model/network/client/manager/NetworkClientCounter.java',root/'build/CTransport.dll',root/'build/CIOPacketCodec.dll']
source_hashes={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(selected)if p.is_file()}
(a.out/'source-snapshot.json').write_text(json.dumps({'implementation_commit':'227c67bcab237c409d9741dcd1b2e091481e10e0','files':source_hashes},indent=2)+'\n')
print('PASS input hashes, pdata-validated game listener/virtual dispatch, x86 event/accept crosscheck, separate CUserSocket maps; production files read-only')
(a.out/'functions.json').write_text(json.dumps(x64.resolved,indent=2)+'\n')
(a.out/'inputs.json').write_text(json.dumps({'binary_hashes':EXPECTED,'image_bases':{'x64':hex(x64.base),'x86':hex(x86.base)},'sources':'CTransport ABI5 at 227c67bcab237c409d9741dcd1b2e091481e10e0','method':'read-only static disassembly; x64 import/direct xrefs validated at pdata instruction boundaries; x86 entries marked candidate-only'},indent=2)+'\n')
print(json.dumps({'functions':len(x64.resolved),'imports':import_refs,'key_callers':{k:x64.resolved[k]['callers']for k in ['user_ctor','listener_start','npc_add_override','service_entry','thread_rng_seed_init']}},indent=2))
