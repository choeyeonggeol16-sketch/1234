#!/usr/bin/env python3
"""Conservative follow-up classification for source-traced direct-send candidates.
Static evidence only, not claim of end-to-end gameplay or IOCP verification.
"""
from pathlib import Path
import csv,collections
path=Path(__file__).with_name('CLASSIFIED_567_LOOP_SITES.tsv')
rows=list(csv.DictReader(path.open(encoding='utf8'),delimiter='\t'))
# Targeted direct-send sites checked with adjacent payload/recipient expressions.
specific={
 'l1j/server/server/model/world/instance/dungeon/bossraid2/Queen1.java:195':'Per-recipient clan name is part of GREEN_MESSAGE payload.',
 'l1j/server/server/model/world/instance/dungeon/hadin/Hadin.java:725':'Effect uses each party member own object ID.',
 'l1j/server/server/model/world/instance/dungeon/orim/OrimController.java:827':'Nested shell loop generates per-recipient random coordinates.',
 'l1j/server/server/model/world/instance/dungeon/orim/OrimController.java:829':'Random x/y are generated inside recipient loop; original effect cannot be one frozen frame.',
 'l1j/server/server/model/character/combat/arena/CombatObserver.java:173':'Identical S_EVENT quit time fanout. Converted to one native frame in phase89.',
 'l1j/server/server/Controller/BossController.java:391':'sendEvents writes each recipient own player/object ID in shop/event payload.',
 'l1j/server/server/model/Instance/L1PcInstance.java:4944':'Identical clan-leave server message. Converted to shared array frame in phase89.',
 'l1j/server/server/model/world/instance/lobby/creator/LobbyCreator.java:289':'Same team cancellation text and per-member state update, converted to one shared frame preserving interleave.',
 'l1j/server/server/model/world/instance/lobby/creator/LobbyCreator.java:295':'Same as blue team; single shared frame drains both groups in original order.',
 'l1j/server/server/model/world/instance/dungeon/gludio/GludioInstanceDungeon.java:547':'Same status body for all gameInfoSent members; converted in phase89.',
 'l1j/server/server/model/world/instance/dungeon/gludio/GludioInstanceDungeon.java:605':'Same end-status per member, but status must precede that member teleport; converted preserving per-member order.',
 'l1j/server/server/model/world/instance/dungeon/gludio/GludioInstanceDungeon.java:504':'Each member receives status only on its own map-arrival check; preserve admission/timing.',
 'l1j/server/server/model/world/instance/dungeon/gludio/GludioInstanceDungeon.java:354':'Per-member room exit ack followed by individual teleport; preserve per-recipient state transition.',
 'l1j/server/server/model/character/companion/CompanionCommandExecutor.java:199':'Companion status notification corresponds to individual companion state.',
 'l1j/server/server/model/character/companion/CompanionCommandExecutor.java:226':'Companion status notification corresponds to individual companion state.',
 'l1j/server/server/model/character/companion/CompanionCommandExecutor.java:248':'Companion status notification corresponds to individual companion state.',
 'l1j/server/server/model/character/action/CGetItemAction.java:307':'Distribution amount and recipient name depend on member and transaction.',
 'l1j/server/server/model/character/Item/Drop/DropShareEngine.java:148':'Per-player item split and name/status; retain inventory mutation interleave.',
 'l1j/server/server/model/Instance/L1SummonInstance.java:580':'sendNewInstance receives viewer twice; object visibility is viewer-specific.',
 'l1j/server/server/model/item/teleport/L1Teleport.java:411':'sendNewInstance takes each visiblePc as per-viewer argument.',
 'l1j/server/server/model/network/core/cs/packet/CS_EnterWorld.java:1072':'sendNewInstance takes each visiblePc as per-viewer argument.',
 'l1j/server/server/model/Instance/L1DoorInstance.java:236':'Door segment x coordinate differs on each send.',
 'l1j/server/server/model/Instance/L1DoorInstance.java:245':'Door segment y coordinate differs on each send.',
 'l1j/server/server/shared/Party.java:14':'Party leave message follows per-member roster removal and leader transition.',
 'l1j/server/server/model/character/skill/SkillHandlers.java:2092':'Each member receives individual effect, buff duration and status change.',
 'l1j/server/server/model/Instance/L1PcInstance.java:2851':'Sequential distinct queued packets to one connected player, not a multicast.',
 'l1j/server/server/model/Instance/L1PcInstance.java:2895':'Sequential packet array to one connected player, not a multicast.',
 'l1j/server/server/model/network/core/cs/packet/CS_Mail.java:105':'False positive: sender.getName contains substring send, no packet submission in identified loop.',
 'l1j/server/server/model/L1ClanJoin.java:207':'Source-leader validation notice on error; recipient is fixed outside roster loop.',
 'l1j/server/server/model/network/core/cs/packet/CS_NpcAcion2.java:1161':'Error message to initiating pc for invalid party member; not sent to party.',
 'l1j/server/server/model/network/core/cs/packet/CS_NpcAction.java:858':'Error message to initiating pc for invalid party member; not sent to party.',
 'l1j/server/server/model/network/core/cs/packet/CS_Craft.java:233':'Clan buff implementation is per-member, async and state mutation is interleaved.',
 'l1j/server/server/model/network/core/cs/packet/CS_LeavePledge.java:166':'Member-specific clan clear, confirmation and possible disconnection.',
 'l1j/server/server/model/character/combat/arena/CombatObserver.java:305':'Each distinct box-attribute frame sent to a single late-entering player.',
 'l1j/server/server/model/character/combat/arena/CombatObserver.java:310':'Each distinct team box-attribute frame sent to one player.',
}
for row in rows:
    c=row['classification'];key=row['file']+':'+row['line']
    if key in specific:
        row['classification']='CONVERTED_89' if 'Converted' in specific[key] or 'converted' in specific[key] else 'EXCLUDED_RECIPIENT_OR_SEQUENCE'
        row['reason']=specific[key]
    elif c=='DIRECT_MESSAGE_REVIEW':
        row['classification']='NOT_GROUP_BROADCAST'
        row['reason']='Direct message in a loop over items/DB/status or to one caller; the surrounding per-row source context is retained for inspection.'
    elif c=='DIRECT_PACKET_REVIEW':
        row['classification']='NOT_PROVEN_IDENTICAL'
        row['reason']='Packet payload depends on item/companion/viewer/status or targets one caller; conservative exclusion from shared-source conversion.'
    elif c=='OTHER_EXECUTION_REVIEW':
        row['classification']='INDIRECT_OR_SEQUENTIAL'
        row['reason']='Indirect function, sequential per-member transition, per-coordinate message, or repeated tick; original order preserved.'
with path.open('w',encoding='utf8',newline='') as f:
    w=csv.DictWriter(f,fieldnames=list(rows[0]),delimiter='\t');w.writeheader();w.writerows(rows)
print('Classification with context:',*collections.Counter(x['classification'] for x in rows).items(),sep='\n')
print('N=',len(rows))
