#!/usr/bin/env python3
"""Static call-boundary assertions for gameplay paths whose optional visual
broadcasts must not make authoritative game-state processing abort.
These are source guards, NOT live game execution tests.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[3]/'src'

def read(name):return (root/name).read_text(encoding='utf-8')

def contains(name,*needles):
    s=read(name)
    for needle in needles:
        assert needle in s, (name,needle)
    return s

raid=contains('l1j/server/MJRaidSystem/Objects/MJRaidValakas.java',
              'createStartMeteor()', 'closeOptional(shared)',
              'closeOptional(blackShared)', 'closeOptional(valakasShared)',
              'sendRecipientBestEffort(shared,pc)', '_boss = _bossSpawn.spawnBoss')
assert 'try(var blackShared=' not in raid
castle=contains('l1j/server/MJWarSystem/MJCastleWar.java',
                'updateDefense(L1Clan nextDefense)', 'closeOptional(ownerShared)',
                'closeOptional(shared)', 'sendRecipientBestEffort(ownerShared,pc)',
                'ThreadPool.getInstance().scheduleRequired(new Runnable()')
assert castle.index('closeOptional(shared)') < castle.index('ThreadPool.getInstance().scheduleRequired(new Runnable()')
contains('l1j/server/server/model/Instance/L1PcInstance.java',
         'sendRecipientBestEffort(shared,partymember)',
         'sendWrittenAllGmsBestEffort(', 'closeOptional(shared)')
contains('l1j/server/server/model/network/core/cs/packet/CS_LeavePledge.java',
         'ClearPlayerClanData(clan)', 'closeOptional(shared)',
         'sendRecipientBestEffort(shared,member)')
contains('l1j/server/server/model/network/core/cs/packet/CS_ExtraCommand.java',
         'orim.addScore(50)', 'orim.addAtCount()',
         'sendWrittenArrayBestEffort(')
contains('l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java',
         'sendWrittenOptionalTo(', 'sendWrittenAllGmsBestEffort(',
         'sendWrittenAllPlayableBestEffort(', 'first.close()',
         'sendWrittenGroupsBestEffort(')
for i in range(1,12):
    s=read(f'l1j/server/TowerOfDominance/BossController/DominanceFloorLv{i}.java')
    assert 'BestEffort(' in s and 'visualFailure' in s,i
print('93 static gameplay/source guard: raid, castle, clan, death-penalty, Orim, 11 dominance floors PASS')
