"""Read-only, pinned original/current audit. Presence is not semantic equivalence."""
from pathlib import Path
import argparse,json,hashlib,re,sys,struct,csv,subprocess
from collections import Counter
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);p.add_argument('--repo',type=Path);a=p.parse_args();out=Path(__file__).resolve().parent;repo=(a.repo or out.parents[1]).resolve();project=repo/'patches/ctransport_core_20261112'
baseline='03b14538d2a813007f4b964ff207ae6aeb8f54ac'
def git(*args):return subprocess.check_output(['git',*args],cwd=repo,text=True).strip()
for tree in ['patches','downloads','.github']:
 assert git('rev-parse','HEAD:'+tree)==git('rev-parse',baseline+':'+tree),'audit needs pinned runtime tree '+tree
 subprocess.run(['git','diff','--exit-code','HEAD','--',tree],cwd=repo,check=True,stdout=subprocess.DEVNULL)
helper=repo/'reports/ctransport_verified_network_names_20261113/audit_network_abi5.py'
sys.argv=['audit','--x64',str(a.binary),'--x86',str(a.binary),'--out',str(out)]
s={};exec(compile(helper.read_text().split("x64=AuditPE(a.x64,'x64')")[0],'audit helper','exec'),s);o=s['AuditPE'](a.binary,'x64');raw=a.binary.read_bytes()
old_dir=repo/'reports/ctransport_full_network_census_20261113';named_dir=project/'research/verified-network-names'
old=json.loads((old_dir/'FUNCTION_CENSUS.json').read_text());names=json.loads((named_dir/'VERIFIED_NAMES.json').read_text())
assert old['binary_sha256']==names['binary_sha256']==s['EXPECTED']['x64']
items={}
for row in old['functions']:
 items[int(row['entry'],16)]={'entry':row['entry'],'names':row['signatures'],'chunks':row['chunks'],'scope':row['function_scope'],'previous_category':row['category'],'assembly':str((old_dir/row['assembly']).relative_to(repo))}
for row in names['items']:
 for ref in row['references']:
  at=int(ref['at'],16);entry=int(ref['entry'],16);ins=o.valid(at);assert ins and ins.bytes.hex()==ref['bytes']and o.chunk(at)[2]==entry
  info=names['functions'][hex(entry)]
  item=items.setdefault(entry,{'entry':hex(entry),'names':[],'chunks':info['chunks'],'scope':info['scope'],'previous_category':None,'assembly':str((named_dir/('fn_%x.asm.txt'%entry)).relative_to(repo))})
  item['names']=sorted(set(item['names']+[row['signature']]))
assert len(items)==154
# Add worker signatures omitted by the old owner filter. This is an explicit
# bounded scope expansion, not a claim that all PE functions were enumerated.
worker_strings={}
for m in re.finditer(rb'(?:[\x09\x20-\x7e]\x00){10,1000}\x00\x00',raw):
 t=m.group()[:-2].decode('utf-16le')
 if '__cdecl' in t and 'IOThreadCallback::'in t:worker_strings[o.base+o.pe.get_rva_from_offset(m.start())]=t.strip()
worker_refs=[]
for begin,data in o.text:
 for m in re.finditer(rb'[\x40-\x4f]?\x8d[\x05\x0d\x15\x1d\x25\x2d\x35\x3d][\s\S]{4}',data):
  at=begin+m.start();size=len(m.group());target=at+size+struct.unpack_from('<i',m.group(),size-4)[0]
  if target not in worker_strings:continue
  ins=o.valid(at);c=o.chunk(at)
  if not ins or ins.mnemonic!='lea'or ins.size!=size:continue
  entry=c[2]if c else None
  if entry is None:
   for candidate in sorted((v for v in o.direct if at-512<=v<=at),reverse=True):
    if not any(r['validation']=='pdata instruction boundary'for r in o.calls_to(candidate)):continue
    if any(i.address==at for b,e in o.leaf_chunks(candidate)for i in o.instructions(b,e)):
     entry=candidate;break
  if entry is None:continue
  worker_refs.append({'at':hex(at),'entry':hex(entry),'signature':worker_strings[target],'bytes':ins.bytes.hex()})
  if entry not in items:
   info=o.dump('worker_%x'%entry,entry)
   items[entry]={'entry':hex(entry),'names':[],'chunks':info['chunks'],'scope':info['scope'],'previous_category':None,'assembly':str((out/('worker_%x.asm.txt'%entry)).relative_to(repo))}
  items[entry]['names']=sorted(set(items[entry]['names']+[worker_strings[target]]))
assert len(worker_strings)==4 and {r['signature']for r in worker_refs}==set(worker_strings.values()),(worker_strings,worker_refs)
virtual_reviews=json.loads((out/'VIRTUAL_REVIEW.json').read_text())
verified_instructions=0
for entry,row in items.items():
 chunks=[(int(b,16),int(e,16))for b,e in row['chunks']]
 c=o.chunk(entry)
 if c:assert c[2]==entry and sorted(o.groups[entry])==sorted(chunks)
 else:assert o.leaf_chunks(entry)==chunks
 decoded={i.address:i for b,e in chunks for i in o.instructions(b,e)}
 actual={}
 for line in (repo/row['assembly']).read_text().splitlines():
  m=re.match(r'^([0-9a-fA-F]{16}):\s*(.*)',line)
  if m:actual[int(m[1],16)]=' '.join(m[2].split(';',1)[0].split())
 assert actual.keys()==decoded.keys(),row['entry']
 for address,text in actual.items():
  i=decoded[address];assert text==' '.join((i.mnemonic+' '+i.op_str).split()),(row['entry'],hex(address))
 row['verified_instruction_count']=len(decoded);verified_instructions+=len(decoded)
 joined=' | '.join(row['names']);old_category=row['previous_category']
 if 'CUserSocketReleaseVerifier::'in joined:status='ORIGINAL_CLOSE_VERIFIER_NOT_PORTED'
 elif entry in (0x9f773c,0x9f7770,0x9f7998,0x9fcc98):status='REFERENCE_OR_PACKET_DIAGNOSTICS_PARTIAL'
 elif old_category=='L2_GAME_PROFILE'or 'CExecutionSerializer<class 'in joined or 'AtomicAddTimer::'in joined or all(n.startswith('VTable AtomicAddTimer ')for n in row['names']):status='L2_PROFILE_EXCLUDED'
 elif old_category=='L2_CLIENT_SERVICE':status='L2_CLIENT_SERVICE_EXCLUDED'
 elif old_category=='CLIENT_INTEGRATION_OPEN':status='CLIENT_IDENTITY_INTEGRATION_OPEN'
 elif old_category=='PARTIAL_OPERATIONS':status='OPERATIONAL_PARTIAL'
 elif old_category in ('ORIGINAL_STATS_ONLY','ORIGINAL_INSTRUMENTATION_ONLY'):status=old_category
 elif entry in (0x56daa8,0x53a8fc):status='SHARED_DEFAULT_STUB'
 elif 'IOThread_debug' in joined:status='ORIGINAL_TRACE_WRAPPER_ADAPTED'
 elif all(n.startswith('VTable ')for n in row['names']) and entry not in (0x7d6d50,0x7d6eb4,0x704248,0x404ca0,0x54655c):status='VIRTUAL_DETAIL_UNVERIFIED'
 else:status='CURRENT_MODULE_ROLE_LINKED_NOT_FULL_EQUIVALENCE'
 if row['entry']in virtual_reviews:
  status=virtual_reviews[row['entry']]['status'];row['manual_virtual_review']=virtual_reviews[row['entry']]
 row['status']=status
# Recheck the specific remaining positive-ref diagnostic, not merely a name.
critical=[]
for at,mnemonic,operands in [(0xa12a56,'call','qword ptr [rax + 0x10]'),(0xa12a59,'test','eax, eax'),(0xa12a5b,'jle','0xa12ac1'),(0x66db28,'xor','eax, eax'),(0x66db2a,'mov','byte ptr [rax], 1')]:
 ins=o.valid(at);assert (ins.mnemonic,ins.op_str)==(mnemonic,operands)
 critical.append({'at':hex(at),'bytes':ins.bytes.hex(),'instruction':mnemonic+' '+operands})
rows=json.loads((out/'ASSESSMENTS.json').read_text())['rows'];source_index={};historical_nonboundaries=[]
for row in rows:
 row['original_address_checks']=[]
 for val in row['original_addresses']:
  if not re.fullmatch('0x[0-9a-f]+',val):continue
  at=int(val,16);ins=o.valid(at);c=o.chunk(at)
  good=bool(c and ins)
  row['original_address_checks'].append({'address':val,'pdata_instruction_boundary':good,'root':hex(c[2])if c else None,'instruction':ins.mnemonic+' '+ins.op_str if ins else None})
  if c and not ins:historical_nonboundaries.append({'row':row['id'],'address':val})
 for anchor in row['source_anchors']:
  path=project/anchor['path'];lines=path.read_text().splitlines();matches=[n+1 for n,line in enumerate(lines)if anchor['text']in line]
  assert matches,(row['id'],anchor)
  anchor['line']=matches[0];anchor['matching_line_count']=len(matches)
  source_index[anchor['path']]=hashlib.sha256(path.read_bytes()).hexdigest()
# Snapshot the actual current implementations used for the H01/H02 comparison.
for path in ['native/CTransport.cpp','native/cio_core.hpp','native/user_registry.hpp','src/l1j/server/server/model/network/core/CUserSocket.java','src/l1j/server/server/model/network/core/CSocket.java','native/timer_wheel.hpp']:
 source_index[path]=hashlib.sha256((project/path).read_bytes()).hexdigest()
verification=json.loads((project/'WINDOWS_VERIFICATION.json').read_text());assert verification['status']=='passed'
subprocess.run(['git','diff','--exit-code',verification['tested_source_commit'],baseline,'--',*[str((project/p).relative_to(repo))for p in ['native','src','config','build/CTransport.dll','build/CTransport_SelfTest.exe','build/CIOPacketCodec.dll','build/pss-client/Pss2019.dll']]],cwd=repo,check=True,stdout=subprocess.DEVNULL)
dll=project/'build/CTransport.dll';dllsha=hashlib.sha256(dll.read_bytes()).hexdigest();assert dllsha==verification['dll_sha256']
pe=s['pefile'].PE(str(dll));imports=sorted({i.name.decode()for m in pe.DIRECTORY_ENTRY_IMPORT for i in m.imports if i.name});exports=sorted(i.name.decode()for i in pe.DIRECTORY_ENTRY_EXPORT.symbols if i.name)
assert len(exports)==67 and 'GetQueuedCompletionStatusEx'in imports and 'GetQueuedCompletionStatus'not in imports
original_gqcs=[r for r in o.import_calls('GetQueuedCompletionStatus')if r['validation']=='pdata instruction boundary']
assert any(r['function']=='0x7056ac'for r in original_gqcs)
result={'target_commit':baseline,'original_binary_sha256':s['EXPECTED']['x64'],'original_executed':False,'current_dll_sha256':dllsha,'known_inventory_before_worker_extension':154,'selected_function_entries':len(items),'verified_original_instructions':verified_instructions,'inventory_counts':dict(Counter(row['status']for row in items.values())),'assessment_counts':dict(Counter(row['status']for row in rows)),'not_a_restoration_percentage':True,'semantic_assessments_are_manual':True,'worker_name_references':worker_refs,'critical_instructions':critical,'historical_address_hints_not_instruction_boundaries':historical_nonboundaries,'original_gqcs_pdata_references':original_gqcs,'current_dll_exports':len(exports),'current_dll_imports':imports,'windows_verification':verification,'rows':rows,'functions':list(items.values()),'limits':'Selected known functions and38 overlapping audit rows only;not complete enumeration of every unnamed PE function or all exception paths;role association is not semantic equivalence;no new x86 exhaustive audit.'}
(out/'CURRENT_COMPARISON.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
(out/'SOURCE_INDEX.json').write_text(json.dumps({'target_commit':baseline,'files':source_index,'runtime_git_trees':{p:git('rev-parse',baseline+':'+p)for p in ['patches','downloads','.github']},'input_hashes':{str(p.relative_to(repo)):hashlib.sha256(p.read_bytes()).hexdigest()for p in [old_dir/'FUNCTION_CENSUS.json',named_dir/'VERIFIED_NAMES.json',helper]}},indent=2)+'\n')
with(out/'FUNCTION_INVENTORY.csv').open('w',newline='')as f:
 w=csv.writer(f,lineterminator='\n');w.writerow(['entry','names','status','scope','assembly'])
 for row in sorted(items.values(),key=lambda r:int(r['entry'],16)):w.writerow([row['entry'],' | '.join(row['names']),row['status'],row['scope'],row['assembly']])
print('PASS pinned original SHA,all selected disassembly instructions,current runtime trees,source anchors and DLL ABI/export/imports.')
print('Selected entries:',len(items),'original instructions:',verified_instructions,'assessment rows:',len(rows))
print('Assessment counts:',result['assessment_counts']);print('Inventory counts:',result['inventory_counts'])
print('Historical non-boundary address hints:',historical_nonboundaries)
