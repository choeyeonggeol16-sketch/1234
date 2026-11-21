#!/usr/bin/env python3
"""Source-context decisions for direct packet candidates remaining after generic triage."""
from pathlib import Path
import csv,collections
p=Path(__file__).with_name('CLASSIFIED_567_LOOP_SITES.tsv')
rows=list(csv.DictReader(p.open(encoding='utf8'),delimiter='\t'))
reason_by_file={
 'RaidValakas.java':'Own pc object ID in skill sound and individual Valakas product state mutation.',
 'L1LiftGateInstance.java':'Per-coordinate tile update is different packet for the same viewer.',
 'AttendanceLoadManager.java':'Individual attendance-group rewards and state notifications to owner.',
 'BQSLoadManager.java':'Per-criterion character progress differs each iteration.',
 'L1PcInstance.java':'Per-known-object removal to self, level gift notice, or owner inventory/status stream.',
 'L1GroundInventory.java':'Distinct item world-put per item to one observer.',
 'L1Trade.java':'Per-traded-item status, not one identical frame to multiple sockets.',
 'PcInventoryExpiryScheduler.java':'Per-doll removal/status owned by one character.',
 'L1BuffUtil.java':'Per-target buff/timer/stat mutation and per-skill packet within loop.',
 'CS_ActionUi.java':'Per-player rune validation or individual action response.',
 'CS_Craft.java':'Character-exclusion list entry and owner-specific packet.',
 'CS_EnterWorld.java':'Per-inventory-item/encoded item stream to joining client.',
 'CS_ExchangeSpell.java':'Per-bit/skill validation or owner response, not fanout.',
 'CS_Mail.java':'Per-mail item/letter-state update to one account; not identical fanout.',
 'CS_NpcAcion2.java':'Per-skill GM buff sound to owner, not one payload for group.',
 'CS_Restart.java':'Restore specific buff icons for reconnecting character.',
 'ActionControlHandler.java':'Per-NPC board result to interacting character.',
 'RankBusiness.java':'Distinct rank-stream chunks to a single receiver.',
 'L1PetRaceTrap.java':'Participant-specific racing slot index is encoded.',
 'MonsterBookV2Info.java':'Per-card criterion update to the card owner.',
 'L1BookMark.java':'Each bookmark has different coordinates/id for the same pc.',
}
for row in rows:
 if row['classification']!='NOT_PROVEN_IDENTICAL':continue
 basename=row['file'].split('/')[-1]
 if basename not in reason_by_file:raise SystemExit('unreviewed:'+row['file']+':'+row['line'])
 row['classification']='EXCLUDED_RECIPIENT_OR_SEQUENCE'
 row['reason']=reason_by_file[basename]
with p.open('w',encoding='utf8',newline='') as f:
 w=csv.DictWriter(f,fieldnames=rows[0].keys(),delimiter='\t');w.writeheader();w.writerows(rows)
print('after direct triage:',collections.Counter(r['classification'] for r in rows))
