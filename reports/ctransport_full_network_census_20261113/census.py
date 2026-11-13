"""Inventory all matched network signatures and bounded function bodies.
This is not a claim that every unnamed PE function has a recovered meaning.
"""
from pathlib import Path
import argparse,json,re,struct,sys,hashlib,csv
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);a=p.parse_args();out=Path(__file__).parent
sys.argv=['audit','--x64',str(a.binary),'--x86',str(a.binary),'--out',str(out)]
s={};exec(compile((out/'audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0],'helper','exec'),s)
o=s['AuditPE'](a.binary,'x64');raw=a.binary.read_bytes()
owners=['CIOObject::','CIOSocket::','CIOSocketEx<','CIOServer::','CSocket::','CUserSocket::','CIOBufferEx<','CExecutionSerializerFast<','CAsyncSendConstBuffer','CSocketLimiter::','CPerfMon::','AtomicAddTimer::','MemoryObject::']
strings={}
for m in re.finditer(rb'(?:[\x20-\x7e]\x00){10,700}\x00\x00',raw):
 t=m.group()[:-2].decode('utf-16le')
 if ('__cdecl' in t or '__thiscall' in t) and any(v in t for v in owners):strings[o.base+o.pe.get_rva_from_offset(m.start())]=t
aliases={}
for va,t in strings.items():
 aliases[va]=(va,t)
 for offset in range(min(8,len(t))):
  tail=t[offset:]
  if '__cdecl' in tail and tail.startswith(('void ','bool ','int ','unsigned ','class ','struct ','__cdecl ')):aliases[va+offset*2]=(va,tail)
found={};unlocated=set(strings);refs=[]
for start,data in o.text:
 for m in re.finditer(rb'[\x48-\x4f]\x8d[\x05\x0d\x15\x1d\x25\x2d\x35\x3d]',data):
  at=start+m.start();target=at+7+struct.unpack_from('<i',data,m.start()+3)[0]
  if target not in aliases:continue
  ins=o.valid(at);chunk=o.chunk(at)
  if not ins or ins.mnemonic!='lea' or ins.size!=7:continue
  original,text=aliases[target];refs.append((at,original,text))
  if chunk:found.setdefault(chunk[2],set()).add(text);unlocated.discard(original)
# Audited unnamed structural roots:workers,listener,serializer,pool,timer and EX dispatch.
extra={'IOWorker':0x7056ac,'ListenThread':0x705e4c,'ListenerCreate':0x7066dc,'AcceptFactory':0x705508,'FastSerializer':0x45c7ac,'BufferAllocate':0x41e6e4,'BufferReturn':0x41c6f8,'SetTimeStamp':0x7048cc,'SetShortTimeStamp':0x704990,'UserExPacket':0xa035e4,'PacketAcceptable':0x9ba4f8,'PacketExAcceptable':0x9ba6b0}
for label,va in extra.items():found.setdefault(va,set()).add(label)
types=json.loads((out/'server-rtti.json').read_text())['L2Server(10).exe']
assert types['sha256']==s['EXPECTED']['x64']
for cls,tables in types['rtti'].items():
 for table in tables:
  for slot,target in enumerate(table['slots']):
   va=int(target,16);section=o.pe.get_section_by_rva(va-o.base)
   if not section or not section.Characteristics&0x20000000:break
   # Stop when the next RTTI/data boundary is reached; do not invent slots.
   found.setdefault(va,set()).add('VTable '+cls+' slot'+str(slot))
infos={va:o.dump('fn_%x'%va,va)for va in found}
# Some small/inlined templates have no pdata. Associate their actual references
# only with instructions reachable from known roots or validated direct callees.
for at,original,text in refs:
 if original not in unlocated:continue
 for va,info in list(infos.items()):
  if any(int(b,16)<=at<int(e,16) and any(i.address==at for i in o.instructions(int(b,16),int(e,16)))for b,e in info['chunks']):
   found[va].add(text);unlocated.discard(original);break
callees=sorted({int(c,16)for info in infos.values()for c in info['direct_calls']})
for at,original,text in refs:
 if original not in unlocated:continue
 for va in reversed([v for v in callees if at-512<=v<=at]):
  info=infos.get(va)or o.dump('fn_%x'%va,va)
  if any(int(b,16)<=at<int(e,16) and any(i.address==at for i in o.instructions(int(b,16),int(e,16)))for b,e in info['chunks']):
   infos[va]=info;found.setdefault(va,set()).add(text);unlocated.discard(original);break
rows=[]
for va,names in sorted(found.items()):
 label='fn_%x'%va;info=infos[va];joined='; '.join(sorted(names))
 if 'CUserSocket::' in joined and (any(x in joined for x in ['DestroyItem(','RequestUserCommand(','ConfirmDialog(','SendSystemMessage','ItemDebug(','LoadHtmlCmdHandler(']) or 'Packet(' in joined and not any(x in joined for x in ['VersionPacket(','LoginPacket(','CharacterSelectPacket(','RequestNetPing('])):
  category='L2_GAME_PROFILE';source='src/l1j/server/server/model/network/packet/CPacketHandler.java';note='L1 game handler profile; do not transplant L2 gameplay packet bodies.'
 elif any(x in joined for x in ['PacketAcceptable','PacketExAcceptable','MakePacketBlockList','CheckLoginTime','ResetRelaxTime']):
  category='L1_POLICY_ADAPTATION';source='src/l1j/server/server/model/network/core/CUserSocket.java';note='L2 policy/fields are not one-to-one restored; retain L1 state/time/filter contracts.'
 elif 'MacAddress' in joined:
  category='CLIENT_INTEGRATION_OPEN';source='native/socket_limiter.hpp';note='Server verified-identity API exists; actual L1 client identity supplier is not integrated.'
 elif 'RequestNetPing(' in joined:
  category='L2_CLIENT_SERVICE';source='src/l1j/server/server/model/network/core/sc/packet/SC_Ping.java';note='Original GameGuard service;L1 has its own ping profile,not a missing IOCP engine.'
 elif 'CheckMaxPacketAccept(' in joined:
  category='ORIGINAL_STATS_ONLY';source='src/l1j/server/server/model/network/core/CNetworkDiagnostics.java';note='Examined original returns true;not a flood blocker. Existing L1 rate/filter policy remains.'
 elif 'OnSending(' in joined:
  category='ORIGINAL_INSTRUMENTATION_ONLY';source='src/l1j/server/server/model/network/core/CNetworkDiagnostics.java';note='0x9f8080 reachable leaf body only updates TLS signature-stack metadata;no SEND/crypto/transport action to restore.'
 elif 'CSocketLimiter' in joined:
  category='CORE_ROLE_MAPPED';source='native/socket_limiter.hpp';note='IP/MAC reservation role;L1 terminal ownership guards replace unsafe arbitrary deletion.'
 elif 'CPerfMon' in joined:
  category='PARTIAL_OPERATIONS';source='native/perfmon.hpp';note='Process health exists; full original PDH/CSV/mail operations are not ported.'
 elif any(x in joined for x in ['CIOBufferEx','BufferAllocate','BufferReturn','MemoryObject']):
  category='CORE_ROLE_MAPPED';source='native/cio_core.hpp';note='Native slab/ring/refcount/COW role; latest snapshot observes final returns.'
 elif any(x in joined for x in ['ExecutionSerializer','FastSerializer']):
  category='CORE_ROLE_MAPPED';source='native/execution_serializer.hpp';note='512-slot serializer/504 admission; safe rollback/generation are explicit L1 corrections.'
 elif any(x in joined for x in ['TimeStamp','Timer','CIOObject']):
  category='CORE_ROLE_MAPPED';source='native/cio_core.hpp';note='Wheel/task role; 64-bit monotonic L1 adaptation,not original32-bit clock equivalence.'
 elif any(x in joined for x in ['CIOServer','ListenThread','ListenerCreate','AcceptFactory','IOWorker']):
  category='CORE_ROLE_MAPPED';source='native/CTransport.cpp';note='Accept/IOCP/lifecycle role; original single GQCS versus selected GQCSEx128 adaptation.'
 else:
  category='NETWORK_ADAPTER_MAPPED';source='native/CTransport.cpp';note='Native socket plus Java L1 session/decoder boundary; not blanket branch equivalence.'
 rows.append({'entry':hex(va),'signatures':sorted(names),'category':category,'current_source':source,'comparison':note,'function_scope':info['scope'],'chunks':info['chunks'],'direct_calls':info['direct_calls'],'caller_count':len(info['callers']),'assembly':label+'.asm.txt'})
result={'binary_sha256':s['EXPECTED']['x64'],'original_executed':False,'baseline':'1f5ff36','method':'UTF16 signatures validated by actual RIP references and PE unwind boundaries; additional previously audited structural roots','limits':'Census and source-role comparison are not proof of every unnamed function or every exception branch; x86 remains the previous crosscheck,not a new exhaustive audit.','functions':rows,'unlocated_signatures':[{'va':hex(x),'signature':strings[x]}for x in sorted(unlocated)]}
(out/'FUNCTION_CENSUS.json').write_text(json.dumps(result,indent=2)+'\n')
keep={row['assembly']for row in rows}
for path in out.glob('fn_*.asm.txt'):
 if path.name not in keep:path.unlink()
with (out/'FUNCTION_CENSUS.csv').open('w',newline='')as f:
 w=csv.writer(f,lineterminator="\n");w.writerow(['entry','signatures','category','current_source','scope','comparison'])
 for row in rows:w.writerow([row['entry'],'; '.join(row['signatures']),row['category'],row['current_source'],row['function_scope'],row['comparison']])
print('Function entries:',len(rows),'unlocated signatures:',len(unlocated))
from collections import Counter
print(dict(Counter(r['category']for r in rows)))
print('Unlocated:',[strings[x]for x in sorted(unlocated)])
