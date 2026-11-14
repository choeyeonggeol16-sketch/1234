# RKern 전체 SRC 기존 방송 경로 감사

검사 소스 SHA256: 006f9c53143461f0bff5b5d0e6edd588644ef09efe4ca7b791a4724426d18efb
전체 Java: 1711, SC 패킷 제외 Java: 1516
파서 오류 포함 파일: 4
SC 외 반복 송신 후보: 322곳, 108개 소스
AOI·전체 방송 관련 API 호출: 341곳

이 목록은 후보이며 실제 수신자별 직렬화와 게임 상태 변경을 구분하여 수정해야 합니다.

## 상위 200개 후보

| 소스:줄 | 함수 | 점수 | 수신자 루프 | 송신호출 | 상태변경 검사 |
|---|---|---:|---|---|---|
| src/l1j/server/server/GMCommands.java:2954 | allitem | 9 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(gm, 22)) | pc.sendPackets(new S_GMHtml("선물: "+pc.getName()+"" , ""+item.getLogName()+" 를(을) 획득함 인벤토리 확인.")); pc.sendPackets("선물: " + item.getLogName()  | setAttrEnchantLevel(,setCount(,setEnchantLevel( |
| src/l1j/server/server/command/executor/L1Ress.java:46 | execute | 9 | for (L1PcInstance tg : L1World.getInstance(). getVisiblePlayer(pc)) | tg.sendPackets(new SC_SystemMessage("GM이 부활을 해주었습니다. ")); SC_SkillSound.broadcast(tg, tg.getId(), 3944); tg.sendPackets(new SC_SystemMessage | setCurrentHp(,setCurrentMp(,setTempID( |
| src/l1j/server/server/model/Instance/L1PcInstance.java:3885 | caoPenaltyResult | 9 | for (int i = 0; i < count; i++) | sendPackets(new SC_ServerMessage(638, item.getLogName())) |  |
| src/l1j/server/MJTemplate/L1Instance/MJL1LiftGateInstance.java:209 | broadcast | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) | pc.sendPackets(packet, false) |  |
| src/l1j/server/MJTemplate/L1Instance/MJL1LiftGateInstance.java:216 | broadcast | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) | pc.sendPackets(packet, false) |  |
| src/l1j/server/MJTemplate/TreasureChest/MJL1TreasureChest.java:193 | showCloud | 7 | for(L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) | SC_SkillSound.broadcast(pc, getId(), i) |  |
| src/l1j/server/SpecialEventHandler.java:51 | doGiveEventStaff | 7 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) | pc.sendPackets("\\aH메티스님께서 코마의 축복 코인이 지급 하였습니다.") |  |
| src/l1j/server/SpecialEventHandler.java:63 | doGiveEventStaff1 | 7 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) | pc.sendPackets("\\aH메티스님께서 드래곤의 토파즈가 지급 하였습니다.") |  |
| src/l1j/server/SpecialEventHandler.java:75 | doGiveEventStaff2 | 7 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) | pc.sendPackets("\\aG메티스님께서 소환주문서 1장이 지급 하였습니다.") |  |
| src/l1j/server/server/GMCommands.java:2033 | LargeAreaBan | 7 | for (L1PcInstance player : L1World.getInstance().getVisiblePlayer(pc, range)) | pc.sendPackets(player.getName() + ", (" + player.getAccountName() + ")") |  |
| src/l1j/server/server/GMCommands.java:2583 | autofish | 7 | for (L1PcInstance target : L1World.getInstance().getAllPlayers3()) | gm.sendPackets("\\f3귀하의 보조 캐릭터가 자동낚시 상태입니다.") |  |
| src/l1j/server/server/GMCommands.java:3192 | party | 7 | for (L1PcInstance Targetpc : L1World.getInstance().getVisiblePlayer(gm, range)) | gm.sendPackets(Targetpc.getName() + "님을 내파티에 참가시켰습니다.") |  |
| src/l1j/server/server/GMCommands.java:3213 | party | 7 | for (L1PcInstance Targetpc : L1World.getInstance().getVisiblePlayer(gm)) | gm.sendPackets(Targetpc.getName() + "님을 내파티에 참가시켰습니다.") |  |
| src/l1j/server/server/GMCommands.java:3235 | party | 7 | for (L1PcInstance Targetpc : L1World.getInstance().getAllPlayers()) | gm.sendPackets(Targetpc.getName() + "님을 내파티에 참가시켰습니다.") |  |
| src/l1j/server/server/GMCommands.java:3280 | party | 7 | for (L1PcInstance Targetpc : L1World.getInstance().getAllPlayers()) | gm.sendPackets(Targetpc.getName() + "님을 내파티에 참가시켰습니다.") |  |
| src/l1j/server/server/GMCommands.java:3711 | run | 7 | for (int i = 0; i < cycle; i++) | target.sendPackets("오픈 대기 보상: " + item.getLogName() + " " + count + "개 획득 인벤토리 확인하세요") | setCount(,setEnchantLevel( |
| src/l1j/server/server/UserCommands.java:656 | autofish | 7 | for (L1PcInstance target : L1World.getInstance().getAllPlayers3()) | pc.sendPackets("\\f3귀하의 보조 캐릭터가 자동낚시 상태입니다.") |  |
| src/l1j/server/server/UserCommands.java:1435 | privateShop | 7 | for (L1PcInstance target : L1World.getInstance().getAllPlayers3()) | pc.sendPackets("\\f3이미 당신의 보조 캐릭터가 무인상점 상태입니다."); pc.sendPackets("\\f3상점을 종료하시길 바랍니다. /상점") |  |
| src/l1j/server/server/model/Instance/L1MonsterInstance.java:582 | BossCoinGet | 7 | for (int i = 0; i < bossIds.length; i++) | SC_SkillSound.broadcast(pc, pc.getId(), 7783) |  |
| src/l1j/server/server/model/Instance/L1MonsterInstance.java:584 | BossCoinGet | 7 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) | SC_SkillSound.broadcast(pc, pc.getId(), 7783) |  |
| src/l1j/server/server/model/Instance/L1MonsterInstance.java:612 | BossCoinGet1 | 7 | for (int i = 0; i < bossIds.length; i++) | SC_SkillSound.broadcast(pc, pc.getId(), 7783) |  |
| src/l1j/server/server/model/Instance/L1MonsterInstance.java:614 | BossCoinGet1 | 7 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) | SC_SkillSound.broadcast(pc, pc.getId(), 7783) |  |
| src/l1j/server/server/model/L1Character.java:348 | broadcastPacket | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) | pc.sendPackets(pcs[i], false) |  |
| src/l1j/server/server/model/L1Character.java:355 | broadcastPacket | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) | pc.sendPackets(pcs[i], false) |  |
| src/l1j/server/server/model/L1Character.java:363 | broadcastPacket | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) | pc.sendPackets(packet) |  |
| src/l1j/server/server/model/L1Character.java:375 | broadcastPacketM | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) | pc.sendPackets(packet) |  |
| src/l1j/server/server/model/L1Character.java:383 | broadcastPacket | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) | pc.sendPackets(packet, false, clanid) |  |
| src/l1j/server/server/model/L1Character.java:389 | broadcastPacket | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) | pc.sendPackets(packet, false) |  |
| src/l1j/server/server/model/L1Character.java:395 | broadcastPacket | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) | pc.sendPackets(packet, false) |  |
| src/l1j/server/server/model/L1Character.java:405 | broadcastPacketFast | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(this)) | pc.sendPackets(packet) |  |
| src/l1j/server/server/model/L1Character.java:411 | wideBroadcastPacket | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this, 50)) | pc.sendPackets(packet, false) |  |
| src/l1j/server/server/model/L1MobSkillUse.java:377 | poly | 7 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(attacker)) | SC_SkillSound.broadcast(pc, pc.getId(), 230) |  |
| src/l1j/server/server/model/L1World.java:414 | broadcast_map | 7 | for(L1PcInstance pc : getVisiblePlayers(map_id)) | pc.sendPackets(pck, false) |  |
| src/l1j/server/server/model/L1World.java:430 | broadcast_map | 7 | for(L1PcInstance pc : getVisiblePlayers(map_id)) | pc.sendPackets(pcks, false) |  |
| src/l1j/server/server/model/L1World.java:756 | broadcastPacketToAll | 7 | for (L1PcInstance pc : getAllPlayers()) | pc.sendPackets(pck, false) |  |
| src/l1j/server/server/model/L1World.java:766 | broadcastPacketToAll | 7 | for (L1PcInstance pc : getAllPlayers()) | pc.sendPackets(pck, false) |  |
| src/l1j/server/server/model/L1World.java:777 | broadcastPacketToAll | 7 | for (L1PcInstance pc : getAllPlayers()) | pc.sendPackets(packets, false) |  |
| src/l1j/server/server/model/L1World.java:787 | broadcastPacketToAll | 7 | for (L1PcInstance pc : getAllPlayers()) | pc.sendPackets(packet) |  |
| src/l1j/server/server/model/item/function/ItemUseActiveSkills.java:158 | use | 7 | for (L1PcInstance player : L1World.getInstance().getVisiblePlayer(pc, 20)) | player.sendPackets("프라임의 효과가 적용되었습니다."); player.sendPackets("공성존의 프라임 효과가 적용되었습니다."); SC_SkillSound.broadcast(player, player.getId(), 12301) | setSkillEffect( |
| src/l1j/server/server/model/item/function/ItemUseActiveSkills.java:331 | use | 7 | for (L1PcInstance player : L1World.getInstance().getVisiblePlayer(pc, 9)) | SC_SkillSound.broadcast(player, player.getId(), 231); SC_SkillSound.broadcast(player, player.getId(), 21103); player.sendPackets("\\f:[" + p | setLastImmuneLevel(,setSkillEffect( |
| src/l1j/server/server/model/network/core/cs/packet/CS_ActionUi.java:402 | handleNewShop | 7 | for (L1PcInstance target : L1World .getInstance() .getAllPlayers3()) | pc.sendPackets( "\\f3이미 당신의 보조 캐릭터가 무인상점 상태입니다."); SC_DoActionGFX.broadcast( pc, ActionCodes.ACTION_Idle); SC_WorldPutObject.broadcastPc(pc) | setPrivateShop( |
| src/l1j/server/MJCombatSystem/Games/MJBattleTournamentArea.java:228 | broadcast | 6 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayers(_map_id)) |  |  |
| src/l1j/server/MJCombatSystem/Games/MJBattleTournamentArea.java:240 | broadcast | 6 | for(L1PcInstance pc : L1World.getInstance().getVisiblePlayers(_map_id)) |  |  |
| src/l1j/server/MJCompanion/Instance/MJCompanionRegenerator.java:102 | run | 6 | for(L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/MJDungeonTimer/Updator/DungeonTimeUpdator.java:167 | teleportAfterReset | 6 | for (int i = 0; i < 2; ++i) |  |  |
| src/l1j/server/MJDungeonTimer/Updator/DungeonTimeUpdator.java:168 | teleportAfterReset | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/MJTemplate/DateSchedulerModel/Acceptor/AcceptorToNpcListenerAdapter.java:162 | run | 6 | for(int i=0; i<2; ++i) |  |  |
| src/l1j/server/MJTemplate/DateSchedulerModel/Acceptor/AcceptorToNpcListenerAdapter.java:163 | run | 6 | for(L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/MJTemplate/L1Instance/MJEffectTriggerInstance.java:115 | dispose | 6 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) |  | removeKnownObject( |
| src/l1j/server/MJTemplate/L1Instance/MJL1LiftGateInstance.java:172 | notifyDispose | 6 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) |  | removeKnownObject( |
| src/l1j/server/MJTemplate/L1Instance/MJL1LiftGateInstance.java:195 | notifyChanged | 6 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) |  |  |
| src/l1j/server/MJTemplate/TreasureChest/MJL1TreasureChest.java:160 | remove | 6 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) |  | removeKnownObject( |
| src/l1j/server/MJTemplate/TreasureChest/MJL1TreasureChest.java:173 | showOpen | 6 | for(L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) |  |  |
| src/l1j/server/MJWarSystem/MJCastleWar.java:183 | updateDefense | 6 | for(L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/MJWarSystem/MJCastleWar.java:524 | selectMode | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/Stadium/StadiumObject.java:33 | run | 6 | for(int i=m_ready_seconds; i>=0; --i) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv1.java:151 | Start | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv1.java:226 | spawn | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv10.java:150 | Start | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv10.java:229 | spawn | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv11.java:189 | Start | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv11.java:353 | spawn | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv11.java:380 | deleteNpc | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv2.java:150 | Start | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv2.java:228 | spawn | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv3.java:150 | Start | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv3.java:228 | spawn | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv4.java:150 | Start | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv4.java:228 | spawn | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv5.java:150 | Start | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv5.java:228 | spawn | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv6.java:150 | Start | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv6.java:228 | spawn | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv7.java:150 | Start | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv7.java:228 | spawn | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv8.java:150 | Start | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv8.java:227 | spawn | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv9.java:150 | Start | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv9.java:227 | spawn | 6 | for (L1PcInstance pc : L1World.getInstance() .getVisiblePlayer(npc)) |  |  |
| src/l1j/server/server/Controller/BossController.java:281 | notifyGm | 6 | for (final L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/Controller/BossController.java:468 | boss_spawn | 6 | for (final L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(npc)) |  |  |
| src/l1j/server/server/Controller/BossController.java:490 | boss_spawn | 6 | for (final L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  | setBossYN( |
| src/l1j/server/server/Controller/BugRaceController.java:1073 | BroadcastAllUser | 6 | for (L1PcInstance player : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/Controller/FishingTimeController.java:592 | distribute_message | 6 | for (L1PcInstance viewer : L1World.getInstance().getVisiblePlayers(5490)) |  |  |
| src/l1j/server/server/Controller/FishingTimeController.java:595 | distribute_message | 6 | for (L1PcInstance viewer : L1World.getInstance().getVisiblePlayers(5490)) |  |  |
| src/l1j/server/server/Controller/HomeTownTimeController.java:111 | monthlyProc | 6 | for (int townId = 1; townId <= 10; townId++) |  | setContribution( |
| src/l1j/server/server/Controller/HomeTownTimeController.java:114 | monthlyProc | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  | setContribution( |
| src/l1j/server/server/Controller/ShipTimeController.java:132 | checkShipTime | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/Controller/ShipTimeController.java:172 | checkShipTime | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/Controller/ShipTimeController.java:184 | checkShipTime | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/Controller/ShipTimeController.java:195 | checkShipTime | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/Controller/ShipTimeController.java:206 | checkShipTime | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/Controller/ShipTimeController.java:216 | checkShipTime | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/GMCommands.java:3118 | applyGmAreaBuff | 6 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(gm, 22)) |  |  |
| src/l1j/server/server/command/executor/L1BanIp.java:33 | execute | 6 | for (L1PcInstance tg : L1World.getInstance(). getAllPlayers()) |  |  |
| src/l1j/server/server/command/executor/L1ItemDelete.java:47 | execute | 6 | for(L1PcInstance players : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/model/BookQuest/Loader/BQSLoadManager.java:229 | run | 6 | for(L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/model/BookQuest/Loader/BQSWQDecksLoader.java:71 | notifyUpdated | 6 | for(L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/model/Instance/L1ClanJoinInstance.java:138 | dispose | 6 | for (L1PcInstance player : world.getAllPlayers()) |  | removeKnownObject( |
| src/l1j/server/server/model/Instance/L1EffectInstance.java:120 | deleteMe | 6 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(this)) |  | removeKnownObject( |
| src/l1j/server/server/model/Instance/L1NpcInstance.java:1526 | teleport | 6 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(this)) |  | removeKnownObject( |
| src/l1j/server/server/model/Instance/L1PcInstance.java:3892 | caoPenaltyResult | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/model/Instance/L1PcInstance.java:3908 | caoPenaltyResult | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/model/Instance/L1PcInstance.java:8477 | delInvisObject | 6 | for (L1PcInstance viewer : L1World.getInstance() .getVisiblePlayerFast(this)) |  |  |
| src/l1j/server/server/model/L1GroundInventory.java:98 | deleteItem | 6 | for (L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(item)) |  | removeKnownObject( |
| src/l1j/server/server/model/L1Racing.java:406 | endGame | 6 | for(L1PcInstance c : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/model/L1World.java:404 | broadcast_map | 6 | for (L1PcInstance pc : getVisiblePlayers(map_id)) |  |  |
| src/l1j/server/server/model/character/skill/SkillHandlers.java:1036 | onResurrection | 6 | for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(pc, 0)) |  |  |
| src/l1j/server/server/model/character/skill/SkillHandlers.java:1084 | onResurrection | 6 | for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(npc, 0)) |  |  |
| src/l1j/server/server/model/character/skill/SkillHandlers.java:3122 | onGraceAvatar | 6 | for (L1PcInstance member : L1World.getInstance().getVisiblePlayer(pc, 18)) |  | setGraceLv( |
| src/l1j/server/server/model/character/skill/SkillHandlers.java:4148 | onCallOfNature | 6 | for (L1PcInstance v : L1World.getInstance().getVisiblePlayer(pc, 0)) |  |  |
| src/l1j/server/server/model/character/skill/SkillHandlers.java:4172 | onCallOfNature | 6 | for (L1PcInstance v : L1World.getInstance().getVisiblePlayer(npc, 0)) |  |  |
| src/l1j/server/server/model/item/function/ItemUseConsignment.java:143 | register | 6 | for (L1PcInstance pc1 : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/model/item/function/ItemUseTravelCases.java:55 | handleTeleportCompanions | 6 | for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(target, 0)) |  |  |
| src/l1j/server/server/model/item/function/ItemUseTravelCases.java:87 | handleTeleportCompanions | 6 | for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(npc, 0)) |  |  |
| src/l1j/server/server/model/item/function/ItemUseTravelCases.java:98 | handleTeleportCompanions | 6 | for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(npc, 0)) |  |  |
| src/l1j/server/server/model/item/teleport/L1Teleport.java:259 | syncBuffsAndEffects | 6 | for (L1PcInstance visible : L1World.getInstance().getVisiblePlayerFast(pc)) |  |  |
| src/l1j/server/server/model/item/teleport/L1Teleport.java:584 | clearPcFromOldViewers | 6 | for (L1PcInstance v : world.getVisiblePlayerFast(pc)) |  | removeKnownObject( |
| src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java:42 | sendAll | 6 | for (L1PcInstance pc:L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/model/network/core/cs/packet/CS_Channel.java:354 | handleAutoReport | 6 | for (L1PcInstance gm : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/model/network/core/cs/packet/CS_Chat.java:74 | broadcastToWorld | 6 | for (L1PcInstance listener : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/model/network/core/cs/packet/CS_Craft.java:543 | processChatMessage | 6 | for (L1PcInstance listener : L1World.getInstance().getVisiblePlayerFast(pc)) |  |  |
| src/l1j/server/server/model/network/core/cs/packet/CS_Craft.java:809 | chatWorld | 6 | for (L1PcInstance listener : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/model/network/core/cs/packet/CS_Result.java:371 | handle | 6 | for (L1PcInstance player : L1World.getInstance().getAllPlayers()) |  |  |
| src/l1j/server/server/utils/L1SpawnUtil.java:240 | spawn2 | 6 | for (L1PcInstance _pc : L1World.getInstance().getVisiblePlayer(npc)) |  |  |
| src/l1j/server/server/utils/L1SpawnUtil.java:345 | spawnfieldboss | 6 | for (L1PcInstance _pc : L1World.getInstance().getVisiblePlayer(npc)) |  |  |
| src/l1j/server/server/utils/L1SpawnUtil.java:351 | spawnfieldboss | 6 | for (L1PcInstance _pc : L1World.getInstance().getVisiblePlayer(npc)) |  |  |
| src/l1j/server/swing/Give.java:132 | GiveOkActionPerformed | 6 | for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) |  | setCount(,setEnchantLevel(,setIdentified(,setVisible( |
| src/l1j/server/MJRaidSystem/BossSkill/MJWideArea.java:84 | run | 5 | for(int z=_range; z>=1; z-=3) | pc.sendPackets(eff, false) |  |
| src/l1j/server/MJRaidSystem/BossSkill/MJWideArea.java:85 | run | 5 | for(int i=1; i<=360; i += radX) | pc.sendPackets(eff, false) |  |
| src/l1j/server/server/model/Instance/L1PcInstance.java:3170 | GotCounterBarrierDamage | 5 | for (L1PcInstance partymember : this.getParty().getMembers()) | partymember.sendPackets(new S_SkillSound(getId(), 12444), true) |  |
| src/l1j/server/server/model/Instance/L1PcInstance.java:3502 | applyReceivedDamage | 5 | for (L1PcInstance partymember : this.getParty().getMembers()) | partymember.sendPackets(new S_SkillSound(getId(), 12444), true) |  |
| src/l1j/server/IndunSystem/Hadin/Hadin.java:90 | run | 3 | while(Running) | SC_NpcChatPacket.broadcastNpc(Npc_Hadin, Npc_Hadin, "$7598", 0); SC_NpcChatPacket.broadcastNpc(Npc_Hadin, Npc_Hadin, "$8693", 0); SC_NpcChat |  |
| src/l1j/server/MJEffectSystem/Realize/OnEffectThread.java:185 | applyPoint | 3 | for (int i = 0, size = snapshot.size(); i < size; i++) | SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/MJRaidSystem/BossSkill/MJWideArea.java:89 | run | 3 | for(L1PcInstance pc : _pcs) | pc.sendPackets(eff, false) |  |
| src/l1j/server/MJServerMacroSystem/MJServerMacroLoader.java:143 | run | 3 | for(int i=0; i<size; i++) | p.sendPackets((ServerPacket) packet, false) |  |
| src/l1j/server/MJServerMacroSystem/MJServerMacroLoader.java:150 | run | 3 | for (L1PcInstance p : col) | p.sendPackets((ServerPacket) packet, false) |  |
| src/l1j/server/MJTemplate/L1Instance/MJEffectTriggerInstance.java:93 | broadcast | 3 | for(L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) | pc.sendPackets(pck, false) |  |
| src/l1j/server/server/GMCommands.java:3714 | run | 3 | for (L1PcInstance target : player) | target.sendPackets("오픈 대기 보상: " + item.getLogName() + " " + count + "개 획득 인벤토리 확인하세요") | setCount(,setEnchantLevel( |
| src/l1j/server/server/GMCommands.java:4570 | allpresent | 3 | for (L1PcInstance target : player) | target.sendPackets("전체 선물: " + item.getLogName() + " " + count + "개 획득 인벤토리 확인하세요"); target.sendPackets("\\aD운영자의 전체선물 : // "+item.getLogNam | setCount(,setEnchantLevel( |
| src/l1j/server/server/GMCommands.java:4871 | result | 3 | while (rs.next()) | sendPackets(String.format("계정:[%s], 캐릭터명:[%s]", rs.getString(1), rs.getString(2))) |  |
| src/l1j/server/server/UserCommands.java:1165 | BloodParty | 3 | for (L1PcInstance member : clan.getOnlineClanMember()) | member.sendPackets("\\fQ자동분배는 한 화면에 파티원들이 있을 경우 분배됩니다.") | setPartyID( |
| src/l1j/server/server/UserCommands.java:1222 | BloodParty3 | 3 | for (L1PcInstance member : clan.getOnlineClanMember()) | member.sendPackets("\\fQAuto-loot only works when party members are on the same screen.") | setPartyID( |
| src/l1j/server/server/model/Instance/L1EffectInstance.java:56 | applyFireWallDamage | 3 | for (L1Object obj : L1World.getInstance().getClusterGrid().queryNearby(this, 0)) | SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage); SC_DoActionGFX.broadcast(mob, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/server/model/L1ClanJoin.java:157 | joinClanAtomic | 3 | for (L1PcInstance other : clan.getOnlineClanMember()) | SC_ReturnedStat.broadcastEmblem(other, other.getId(), clan.getEmblemId()) |  |
| src/l1j/server/server/model/L1WeaponSkill.java:153 | 실렌의결의 | 3 | for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) | SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage); SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/server/model/L1WeaponSkill.java:208 | 사이하의집념 | 3 | for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) | SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage); SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/server/model/L1WeaponSkill.java:502 | 파아그리오공포 | 3 | for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) | SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage); SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/server/model/L1WeaponSkill.java:567 | 단테스일격 | 3 | for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) | SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage); SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/server/model/L1WeaponSkill.java:648 | 아인하사드고독 | 3 | for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) | SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage); SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/server/model/L1WeaponSkill.java:736 | 아인하사드의섬광 | 3 | for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) | SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage); SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/server/model/L1WeaponSkill.java:800 | 에바의서약 | 3 | for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) | SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage); SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/server/model/L1WeaponSkill.java:882 | 그랑카인의심판 | 3 | for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) | SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage); SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/server/model/L1WeaponSkill.java:953 | 아우라키아의초월 | 3 | for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) | SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage); SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/server/model/L1World.java:796 | broadcastPacketToAll | 3 | for (L1PcInstance pc : pclist) | pc.sendPackets(packet, false) |  |
| src/l1j/server/server/model/character/skill/SkillHandlers.java:842 | onCancellation | 3 | for (int skillNum = skillsBegin; skillNum <= skillsEnd; skillNum++) | SC_SkillSound.broadcastFastPacked(pc, ss) |  |
| src/l1j/server/server/model/item/function/CItemUseClan.java:107 | applyClanBuff | 3 | for (L1PcInstance member : clan.getOnlineClanMember()) | SC_SkillSound.broadcast(member, member.getId(), 13566) | setSkillEffect( |
| src/l1j/server/server/model/item/function/ItemUseConsumableRewards.java:143 | use | 3 | for (L1PcInstance member : pointsClan.getOnlineClanMember()) | member.sendPackets("\\fH" + pc.getName() + "\\f2님이 혈맹포인트상자에서 \\fH[" + points + "]\\f2 포인트를 획득하였습니다.") |  |
| src/l1j/server/server/model/item/function/ItemUseMiscActions.java:315 | 군주광역스턴 | 3 | for (L1Object obj : L1World.getInstance().getClusterGrid().queryNearby(pc, 10)) | SC_SkillSound.broadcast(target, target.getId(), 15101); SC_SkillSound.broadcast(targetnpc, obj.getId(), 15101) | setParalyzed(,setSkillEffect( |
| src/l1j/server/server/model/network/core/cs/packet/CS_Emblem.java:77 | handle | 3 | for (L1PcInstance pc : clan.getOnlineClanMember()) | SC_ReturnedStat.broadcastEmblem(pc, pc.getId(), newEmblemId) |  |
| src/l1j/server/server/model/network/core/cs/packet/CS_ExtraCommand.java:91 | handleAttackTrap | 3 | for (int i = 0; i < members.length; i++) | SC_SkillSound.broadcastPacked(member, sound) |  |
| src/l1j/server/server/model/network/core/cs/packet/CS_ExtraCommand.java:138 | handleDefenceTrap | 3 | for (int i = 0; i < members.length; i++) | SC_SkillSound.broadcastPacked(member, sound) |  |
| src/l1j/server/server/model/network/core/cs/packet/CS_NpcAcion2.java:2889 | getShellDmg | 3 | for (L1Object object : L1World.getInstance().getVisibleObjects(effect, 3)) | SC_DoActionGFX.broadcast( targetPc, ActionCodes.ACTION_Damage); SC_DoActionGFX.broadcast( targetNpc, ActionCodes.ACTION_Damage) |  |
| src/l1j/server/IndunSystem/Hadin/Hadin.java:725 | Effect | 2 | for(L1PcInstance c : getParty().getMembers()) |  |  |
| src/l1j/server/IndunSystem/Hadin/Hadin.java:731 | BonginSendPacekt | 2 | for(L1PcInstance pc : getParty().getMembers()) |  |  |
| src/l1j/server/IndunSystem/Orim/OrimController.java:438 | eventSpawn | 2 | for (L1PcInstance pc : getPlayMemberArray()) try |  |  |
| src/l1j/server/IndunSystem/Orim/OrimController.java:519 | sendMessage | 2 | for (L1PcInstance pc : getPlayMemberArray()) SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, msg); |  |  |
| src/l1j/server/IndunSystem/Orim/OrimController.java:524 | sendMessage1 | 2 | for (L1PcInstance pc : getPlayMemberArray()) SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE,SC_PacketBox.YELLOW_MESSAG |  |  |
| src/l1j/server/IndunSystem/Orim/OrimController.java:529 | sendMessage2 | 2 | for (L1PcInstance pc : getPlayMemberArray()) SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, SC_PacketBox.RED_MESSAGE, |  |  |
| src/l1j/server/IndunSystem/Orim/OrimController.java:772 | stageMark | 2 | for (L1PcInstance pc : getPlayMemberArray()) SC_PacketBox.send(pc, SC_PacketBox.ROUND1, stageNum); |  |  |
| src/l1j/server/IndunSystem/Orim/OrimController.java:778 | scoreMark | 2 | for (L1PcInstance pc : getPlayMemberArray()) SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE,SC_PacketBox.SCORE_MARK, I |  |  |
| src/l1j/server/IndunSystem/Orim/OrimController.java:795 | Effect | 2 | for (L1PcInstance c : getPlayMemberArray()) try |  |  |
| src/l1j/server/IndunSystem/Orim/OrimController.java:818 | spawnShelling | 2 | for (int shellCount = 0; shellCount < 5 - attackCount / 2; shellCount++) |  |  |
| src/l1j/server/IndunSystem/Orim/OrimController.java:819 | spawnShelling | 2 | for (L1PcInstance pc : getPlayMemberArray()) try |  |  |
| src/l1j/server/MJCombatSystem/MJCombatObserver.java:609 | on_closing | 2 | for(L1PcInstance pc : col) |  |  |
| src/l1j/server/MJInstanceSystem/MJInstanceEnums.java:96 | sendSystemMsgToList | 2 | for (L1PcInstance pc : pcs) SC_SystemMessage.send(pc, get()); |  |  |
| src/l1j/server/MJInstanceSystem/MJInstanceEnums.java:100 | sendSystemMsgToList | 2 | for (L1PcInstance pc : pcs) SC_SystemMessage.send(pc, message); |  |  |
| src/l1j/server/MJInstanceSystem/MJInstanceEnums.java:109 | sendGreenMsgToList | 2 | for (L1PcInstance pc : pcs) SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, get()); |  |  |
| src/l1j/server/MJInstanceSystem/MJInstanceEnums.java:113 | sendGreenMsgToList | 2 | for (L1PcInstance pc : pcs) SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message); |  |  |
| src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCObject.java:375 | broadcastPacket | 2 | for (L1PcInstance pc : _red) if (pc != null) SC_PacketBox.send(pc, subCode, value); |  |  |
| src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCObject.java:376 | broadcastPacket | 2 | for (L1PcInstance pc : _blue) if (pc != null) SC_PacketBox.send(pc, subCode, value); |  |  |
| src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCObject.java:380 | broadcastPacket | 2 | for (L1PcInstance pc : _red) if (pc != null) SC_PacketBox.send(pc, subCode, value); |  |  |
| src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCObject.java:381 | broadcastPacket | 2 | for (L1PcInstance pc : _blue) if (pc != null) SC_PacketBox.send(pc, subCode, value); |  |  |
| src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCWarSquareTrap.java:73 | run | 2 | while(!_thorn_Q.isEmpty()) |  |  |
| src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCWarSquareTrap.java:76 | run | 2 | for (L1PcInstance pc : _red) |  |  |
| src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCWarSquareTrap.java:79 | run | 2 | for (L1PcInstance pc : _blue) |  |  |
| src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCWarSquareTrap.java:143 | broadcastEffect | 2 | for (L1PcInstance pc : _red) if (pc != null) SC_DisplayEffect.send(pc, effect); |  |  |
| src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCWarSquareTrap.java:144 | broadcastEffect | 2 | for (L1PcInstance pc : _blue) if (pc != null) SC_DisplayEffect.send(pc, effect); |  |  |
| src/l1j/server/MJKDASystem/Chart/MJKDAChartUpdator.java:109 | run | 2 | for (L1PcInstance pc : cols) |  |  |
| src/l1j/server/MJRaidSystem/MJRaidObject.java:544 | notifyRaidPickupItem | 2 | for(L1PcInstance p : getUsersView()) |  |  |
| src/l1j/server/MJRaidSystem/Objects/MJRaidValakas.java:389 | SpawnProductTask | 2 | for(int i=0; i<size; i++) |  | setValakasProduct( |
| src/l1j/server/MJRaidSystem/Util/L1MJRaidPoisonInstance.java:40 | deleteMe | 2 | for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) |  | removeKnownObject( |
| src/l1j/server/MJTemplate/L1Instance/MJEffectTriggerInstance.java:72 | do_move | 2 | for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) |  |  |
| src/l1j/server/MJTemplate/L1Instance/MJEffectTriggerInstance.java:81 | do_action | 2 | for(L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) |  |  |
| src/l1j/server/Stadium/StadiumObject.java:46 | run | 2 | for(L1PcInstance pc : col) |  |  |
| src/l1j/server/Stadium/StadiumObject.java:83 | do_ended | 2 | for(L1PcInstance pc : col) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv1.java:84 | runEvent | 2 | while (Running && !Thread.currentThread().isInterrupted()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv1.java:89 | runEvent | 2 | for (L1PcInstance pc : PcCK()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv1.java:97 | runEvent | 2 | for (L1PcInstance pc : PcCK()) |  |  |
| src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv1.java:101 | runEvent | 2 | for (L1PcInstance pc : PcCK()) |  |  |

## 파일별 후보

- src/l1j/server/server/GMCommands.java: 15
- src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv11.java: 12
- src/l1j/server/server/model/L1Racing.java: 10
- src/l1j/server/server/model/Instance/L1PcInstance.java: 9
- src/l1j/server/server/model/L1Character.java: 9
- src/l1j/server/server/model/L1WeaponSkill.java: 9
- src/l1j/server/IndunSystem/Orim/OrimController.java: 9
- src/l1j/server/server/model/L1World.java: 8
- src/l1j/server/server/model/network/core/cs/packet/CS_Craft.java: 8
- src/l1j/server/server/UserCommands.java: 7
- src/l1j/server/server/model/character/skill/SkillHandlers.java: 7
- src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv1.java: 6
- src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv10.java: 6
- src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv2.java: 6
- src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv3.java: 6
- src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv4.java: 6
- src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv5.java: 6
- src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv6.java: 6
- src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv7.java: 6
- src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv8.java: 6
- src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv9.java: 6
- src/l1j/server/server/Controller/ShipTimeController.java: 6
- src/l1j/server/server/model/Instance/L1MonsterInstance.java: 5
- src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCWarSquareTrap.java: 5
- src/l1j/server/MJTemplate/L1Instance/MJL1LiftGateInstance.java: 4
- src/l1j/server/MJTemplate/L1Instance/MJEffectTriggerInstance.java: 4
- src/l1j/server/server/model/L1ClanJoin.java: 4
- src/l1j/server/MJInstanceSystem/MJInstanceEnums.java: 4
- src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCObject.java: 4
- src/l1j/server/server/model/L1HauntedHouse.java: 4
- src/l1j/server/MJTemplate/TreasureChest/MJL1TreasureChest.java: 3
- src/l1j/server/SpecialEventHandler.java: 3
- src/l1j/server/MJWarSystem/MJCastleWar.java: 3
- src/l1j/server/Stadium/StadiumObject.java: 3
- src/l1j/server/server/Controller/BossController.java: 3
- src/l1j/server/server/model/Instance/L1NpcInstance.java: 3
- src/l1j/server/server/model/item/function/ItemUseTravelCases.java: 3
- src/l1j/server/server/utils/L1SpawnUtil.java: 3
- src/l1j/server/MJRaidSystem/BossSkill/MJWideArea.java: 3
- src/l1j/server/IndunSystem/Hadin/Hadin.java: 3
- src/l1j/server/server/model/network/core/cs/packet/CS_ExtraCommand.java: 3
- src/l1j/server/server/model/item/function/ItemUseActiveSkills.java: 2
- src/l1j/server/server/model/network/core/cs/packet/CS_ActionUi.java: 2
- src/l1j/server/MJCombatSystem/Games/MJBattleTournamentArea.java: 2
- src/l1j/server/MJDungeonTimer/Updator/DungeonTimeUpdator.java: 2
- src/l1j/server/MJTemplate/DateSchedulerModel/Acceptor/AcceptorToNpcListenerAdapter.java: 2
- src/l1j/server/server/Controller/FishingTimeController.java: 2
- src/l1j/server/server/Controller/HomeTownTimeController.java: 2
- src/l1j/server/server/model/Instance/L1EffectInstance.java: 2
- src/l1j/server/server/model/item/teleport/L1Teleport.java: 2
- src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java: 2
- src/l1j/server/server/model/network/core/cs/packet/CS_Channel.java: 2
- src/l1j/server/MJServerMacroSystem/MJServerMacroLoader.java: 2
- src/l1j/server/server/model/network/core/cs/packet/CS_NpcAcion2.java: 2
- src/l1j/server/server/command/executor/L1CheckCharacter.java: 2
- src/l1j/server/server/model/Instance/L1SummonInstance.java: 2
- src/l1j/server/server/model/L1Party.java: 2
- src/l1j/server/server/model/LocalWorldMap.java: 2
- src/l1j/server/server/model/character/action/CGetItemAction.java: 2
- src/l1j/server/server/model/network/core/cs/packet/CS_LeavePledge.java: 2
- src/l1j/server/server/command/executor/L1Ress.java: 1
- src/l1j/server/server/model/L1MobSkillUse.java: 1
- src/l1j/server/MJCompanion/Instance/MJCompanionRegenerator.java: 1
- src/l1j/server/server/Controller/BugRaceController.java: 1
- src/l1j/server/server/command/executor/L1BanIp.java: 1
- src/l1j/server/server/command/executor/L1ItemDelete.java: 1
- src/l1j/server/server/model/BookQuest/Loader/BQSLoadManager.java: 1
- src/l1j/server/server/model/BookQuest/Loader/BQSWQDecksLoader.java: 1
- src/l1j/server/server/model/Instance/L1ClanJoinInstance.java: 1
- src/l1j/server/server/model/L1GroundInventory.java: 1
- src/l1j/server/server/model/item/function/ItemUseConsignment.java: 1
- src/l1j/server/server/model/network/core/cs/packet/CS_Chat.java: 1
- src/l1j/server/server/model/network/core/cs/packet/CS_Result.java: 1
- src/l1j/server/swing/Give.java: 1
- src/l1j/server/MJEffectSystem/Realize/OnEffectThread.java: 1
- src/l1j/server/server/model/item/function/CItemUseClan.java: 1
- src/l1j/server/server/model/item/function/ItemUseConsumableRewards.java: 1
- src/l1j/server/server/model/item/function/ItemUseMiscActions.java: 1
- src/l1j/server/server/model/network/core/cs/packet/CS_Emblem.java: 1
- src/l1j/server/MJCombatSystem/MJCombatObserver.java: 1
- src/l1j/server/MJKDASystem/Chart/MJKDAChartUpdator.java: 1
- src/l1j/server/MJRaidSystem/MJRaidObject.java: 1
- src/l1j/server/MJRaidSystem/Objects/MJRaidValakas.java: 1
- src/l1j/server/MJRaidSystem/Util/L1MJRaidPoisonInstance.java: 1
- src/l1j/server/server/Announcecycle.java: 1
- src/l1j/server/server/command/executor/L1GlobalBan.java: 1
- src/l1j/server/server/command/executor/L1Recall.java: 1
- src/l1j/server/server/model/Instance/L1CompanionInstance.java: 1
- src/l1j/server/server/model/Instance/L1DoorInstance.java: 1
- src/l1j/server/server/model/Instance/L1FieldObjectInstance.java: 1
- src/l1j/server/server/model/Instance/L1TowerInstance.java: 1
- src/l1j/server/server/model/Instance/L1TrapInstance.java: 1
- src/l1j/server/server/model/L1NewUltimateBattle.java: 1
- src/l1j/server/server/model/L1UltimateBattle.java: 1
- src/l1j/server/server/model/character/skill/L1BuffUtil.java: 1
- src/l1j/server/server/model/item/function/ItemUseCommandCombat.java: 1
- src/l1j/server/server/model/item/function/ItemUseWorldHelpers.java: 1
- src/l1j/server/server/model/network/core/cs/packet/CS_EnterWorld.java: 1
- src/l1j/server/server/model/network/core/cs/packet/CS_NpcAction.java: 1
- src/l1j/server/server/model/network/core/cs/packet/CS_RankControl.java: 1
- src/l1j/server/server/model/network/core/cs/packet/CS_Title.java: 1
- src/l1j/server/server/utils/FaceToFace.java: 1
- src/l1j/server/IndunSystem/Gludio/GludioInstanceDungeon.java: 1
- src/l1j/server/MJWarSystem/MJWar.java: 1
- src/l1j/server/server/model/item/function/ItemUseAreaDamage.java: 1
- src/l1j/server/server/model/item/function/ItemUseTravelAndRecovery.java: 1
- src/l1j/server/server/model/item/function/ItemUseUtilityCases.java: 1
- src/l1j/server/server/model/npc/action/ActionControlHandler.java: 1

## 주의

정적 AST 감사는 호출된 메서드의 내부 구현과 런타임 데이터를 증명하지 않습니다.
수신자별 다른 내용의 방송, 게임 객체 등록·삭제 작업은 공통 공유 버퍼로 직접 전환하면 안 됩니다.
