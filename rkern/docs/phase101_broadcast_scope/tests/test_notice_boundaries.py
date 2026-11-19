#!/usr/bin/env python3
"""Guard optional state-notice boundaries and accidental reintroduction of self duplicate."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
source=lambda p:(ROOT/'src/l1j/server'/p).read_text()
orim=source('IndunSystem/Orim/OrimController.java')
lo=orim.index('private void talkPartyleader(String msg)');hi=orim.index('private void MSGOrimAndPartyleader(',lo)
talk=orim[lo:hi]
assert talk.count('sendRecipientBestEffort(shared, leader)')==1
assert talk.count('sendRecipientBestEffort(shared, viewer)')==1
assert talk.index('sendRecipientBestEffort(shared, leader)') < talk.index('for (L1PcInstance viewer')
assert 'sendRecipient(shared,pc)' not in talk
assert talk.count('closeOptional(shared)')==1
assert orim.count('sendWrittenArrayBestEffort(')==5
assert orim.count('sendWrittenArray(')==0
macro=source('MJServerMacroSystem/MJServerMacroLoader.java')
assert macro.count('CSharedBroadcast.sendWrittenRecipientsBestEffort(col,')==5
assert 'CSharedBroadcast.sendWrittenRecipients(col,' not in macro
assert macro.index('sendWrittenRecipientsBestEffort(col,') < macro.index('obj.nextMs = cur + obj.delay;')
assert source('MJInstanceSystem/MJInstanceEnums.java').count('CSharedBroadcast.sendWrittenRecipientsBestEffort(pcs,')==2
boss=source('server/Controller/BossController.java')
assert 'CSharedBroadcast.sendWrittenAllGmsBestEffort(SC_SystemMessage.nativeWriter(msg))' in boss
fishing=source('server/Controller/FishingTimeController.java')
assert fishing.count('CSharedBroadcast.sendWrittenRecipientsBestEffort(')==2
print('100 production notice callers: Orim 5 + self dedup, macro 5, LFC 2, boss GM, fishing 2 (PASS)')
