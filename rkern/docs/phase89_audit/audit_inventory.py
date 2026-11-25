#!/usr/bin/env python3
"""Reproducible, conservative source-trace triage of the phase-88 567 send-in-loop entries.
Does not turn keyword classes into claims about runtime correctness.
"""
import csv,os,re,collections,sys
from pathlib import Path
root=Path(__file__).resolve().parents[2]
inv=root/'docs/phase88_audit/CURRENT_AST_SEND_LOOPS.tsv'
output=root/'docs/phase89_audit/CLASSIFIED_567_LOOP_SITES.tsv'
rows=list(csv.reader(open(inv,encoding='utf-8'),delimiter='\t'))
NATIVE=['CSharedBroadcast','CAsyncSendConstBufferExceptSelf','shared.send','source.send','broadcast.send','actionFrame.send','removeFrame.send','frame.send','sendRecipient','sendWrittenTo','sendSharedAttrs']
PERSONAL_ROOTS=('/GMCommands.java','/UserCommands.java','/command/executor/','/model/item/','/model/CInventory.java','/model/CUserInventory.java','/datatables/','/swing/','/network/core/cs/packet/CS_Result.java','/network/core/cs/packet/CS_NpcAction.java')
NETWORK=('/model/network/core/connection/CUserSocket.java','/model/network/core/session/CIOClient.java','/model/network/thread/CThreadLocalTimer.java')
BROADCAST_METHODS=['.broadcast','broadcastEndWar','broadcastServerMessage','send_caster_message','send_messenger_message','sendRoundMessage']
out=[]
for path,line,loop,methods in rows:
 # Per-entry source context deliberately retained so triage decisions are inspectable.
 f=root/'src'/path
 try:
  lines=f.read_text(encoding='utf8',errors='replace').splitlines()
  ix=int(line)-1
  context=' '.join(x.strip() for x in lines[ix:min(ix+10,len(lines))])
 except OSError:context='[source missing]'
 if '/model/network/core/broadcast/CSharedBroadcast.java' in path:
  category='ENGINE_INTERNAL';reason='Inside shared-broadcast engine itself; not a legacy application fanout.'
 elif any(x in methods for x in NATIVE):
  category='SHARED_SOURCE';reason='Existing shared-frame object/send or named Native fanout path.'
 elif any(x in path for x in NETWORK):
  category='NETWORK_INTERNAL';reason='Network queue/completion or burst handling; no game-recipient loop.'
 elif 'Proto.commit' in methods or 'commitDirectFrame' in methods:
  category='DIRECT_PROTO_REVIEW';reason='Direct framed Proto commit; verify repeated recipients and payload equality.'
 elif any(x in methods for x in BROADCAST_METHODS):
  category='BROADCAST_WRAPPER_OR_EVENT';reason='Broadcast wrapper per distinct event/tick: separately audit underlying wrapper for native sharing.'
 elif any(x in path for x in PERSONAL_ROOTS):
  category='PERSONAL_OR_GAME_OPERATION';reason='Command/CS/item/database loop with a fixed caller or varying item/status; not inferred as identical-recipient fanout.'
 elif 'SC_WORLD_PUT_OBJECT_NOTI.sendNewInstance' in methods:
  category='RECIPIENT_DEPENDENT';reason='Per-viewer world-put packet, recipient object is passed as a semantic argument.'
 elif 'SC_ReturnedStat' in methods or 'SC_HPMeter' in methods or 'SC_Paralysis' in methods:
  category='RECIPIENT_DEPENDENT';reason='Per-target status, health, or emblem notification.'
 elif 'SC_SystemMessage.send' in methods or 'SC_ServerMessage.send' in methods or 'pc.sendPackets' in methods or 'gm.sendPackets' in methods:
  category='DIRECT_MESSAGE_REVIEW';reason='Direct message within loop; inspect target and interpolated arguments (not safe to assume a broadcast).'
 elif 'SC_' in methods or 'Proto.' in methods or 'sendTime' in methods:
  category='DIRECT_PACKET_REVIEW';reason='Packet send within loop; inspect recipient, payload, and interleaving.'
 else:
  category='OTHER_EXECUTION_REVIEW';reason='Indirect send or game control loop; inspect callee and side effects.'
 out.append((path,line,loop,methods,category,reason,context[:1000]))
with open(output,'w',encoding='utf8',newline='') as f:
 w=csv.writer(f,delimiter='\t');w.writerow(('file','line','loop','call_sites','classification','reason','source_context'));w.writerows(out)
print('Inventory totals:',len(out));print(*collections.Counter(x[4] for x in out).most_common(),sep='\n')
