package l1j.server.server.model.world.instance.dungeon.fanstasyisland;

import l1j.server.server.datatables.CObjectDB;
import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.world.instance.CInstanceEvents;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.L1SpawnUtil;

public final class FantasyIsland implements Runnable {
    private static final Logger LOG = Logger.getLogger(FantasyIsland.class.getName());
    private final CInstantZone<FantasyIsland> zone;
    private final short _map;
    private final L1Map entranceMap;
    private final int sessionStamp;
    private boolean began;
    private boolean started;
    private boolean returnRequested;
	private int stage = 1;
	private static final int WAIT_RAID = 1;
	private static final int FIRST_STEP = 2;
	private static final int SECOND_STEP = 3;
	private static final int THIRD_STEP = 4;
	private static final int FOURTH_STEP = 5;
	private static final int LAST_STEP = 6;
	private static final int END = 7;

	private Random _rnd = new Random(System.nanoTime());
	private int[] bossInt = {14905, 14907, 7200020};
	private int _status;
	private L1NpcInstance unicorn;
	private L1NpcInstance boss;
	private final L1PcInstance pc;

    private ArrayList<L1NpcInstance> BasicNpcList;
    private ArrayList<L1NpcInstance> NpcList;

    FantasyIsland(CInstantZone<FantasyIsland> zone, L1PcInstance pc) {
        this.zone = zone;
        this._map = (short) zone.mapId();
        this.pc = pc;
        this.entranceMap = pc.getMap();
        this.sessionStamp = pc.getSessionStamp();
    }

    @Override
    public void run() {
        try {
            if (!ownsPlayer() || pc.getMap() != entranceMap || CInstanceEvents.isStopping()
                    || Thread.currentThread().isInterrupted()) return;
            began = true;
            BasicNpcList = FantasyIslandSpawn.getInstance().fillSpawnTable(_map, 0, true);
            setting();
            if (unicorn == null) throw new IllegalStateException("Missing Fantasy Island unicorn");
            if (!zone.enter(() -> {
                if (!ownsPlayer() || pc.getMap() != entranceMap || Thread.currentThread().isInterrupted()) return;
                pc.isInFantasy = true;
                started = true;
                pc.start_teleport(32798, 32865, _map, pc.getHeading(), 169, true, false);
            }) || !started) return;
            LOG.info(() -> "Fantasy Island opened for " + pc.getName() + " on map " + _map);
            Thread.sleep(2000L);
            if (!inside()) return;

            NpcList = FantasyIslandSpawn.getInstance().fillSpawnTable(_map, 1, true);
            while (inside() && !CInstanceEvents.isStopping() && !Thread.currentThread().isInterrupted()) {
                if (NpcList != null) NpcList.removeIf(npc -> npc == null || npc.isDead());
                if (stage != END) {
                    if (unicorn.isDead()) {
                        zone.setExit(33968, 32961, 4);
                        finishPlayer(33968, 32961);
                        return;
                    }
                    checkHp();
                }
				switch(stage){
				case WAIT_RAID:
					if(NpcList.size() > 0)
						break;
					Sleep(5000);
					SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17691", 0);
					//도와주러 와 주셔서 감사합니다.
					Sleep(2000);
					SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17692", 0);
					//이계의 존재가 곧 돌아올겁니다.
					Sleep(2000);
					SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17693", 0);
					//그전에 제가 봉인을 풀 수 있도록 시간을 벌어주세요.
					Sleep(3000);
					SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17947", 0);
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17947");//마법 막대를 이용해 적을 처치해주세요.
					pc.getInventory().CreateOrAddItem(810006, 1);
					SC_SystemMessage.send(pc, "$17948");
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "경고! 오토방지 코드는 무시하세요.");
					
					Sleep(3000);
					SC_SystemMessage.send(pc, "\\aA팁: 오토방지 코드는 무시하시면 됩니다.(\\aG자동인증\\aA)");
					
					Sleep(5000);
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17701");
					SC_PacketBox.send(pc, SC_PacketBox.ROUND, 1, 4);
					//적들이 몰려오고 있습니다.
					//11시 방향 스폰 포르시스, 베네보스
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200008, 0, 0, 5);
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200012, 0, 0, 5);
					stage = 2;
					break;
				case FIRST_STEP:
					Sleep(10000);
					//1시방향 스폰 스콜피온+ 메두사
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200009, 0, 0, 5);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200013, 0, 0, 5);
					Sleep(10000);
					//5시방향 코트룻+페르페르 스폰
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200010, 0, 0, 5);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200014, 0, 0, 5);
					Sleep(10000);
					//7시방향 메가+비아
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200011, 0, 0, 5);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200015, 0, 0, 5);
					//땅의 대정령이 나타났습니다!!!
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17944");
					stage = 3;
					break;
					/** 2번째 단계진행 **/
				case SECOND_STEP:
					Sleep(10000);
					SC_PacketBox.send(pc, SC_PacketBox.ROUND, 2, 4);
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17703");
					//적들이 더 몰려옵니다. 준비해 주세요
					pc.getInventory().CreateOrAddItem(810006, 1);
					SC_SystemMessage.send(pc, "$17948");
					Sleep(5000);
					//11시 포르시스, 베네보스
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200008, 0, 0, 7);
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200012, 0, 0, 7);
					//1시방향 메두사+스콜피온 + 땅의대정령
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200009, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200013, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200018, 0, 0, 1);
					Sleep(10000);
					//5시 코트룻+페르페르
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200010, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200014, 0, 0, 7);
					Sleep(20000);
					//7시 메가+비아
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200011, 0, 0, 7);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200015, 0, 0, 7);
					Sleep(5000);
					//11시 포르시스, 베네보스
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200008, 0, 0, 7);
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200012, 0, 0, 7);
					Sleep(5000);
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17942");
					//바람의 대정령이 나타났습니다!!!
					stage = 4;
					break;
					/** 3단계 **/
				case THIRD_STEP:
					Sleep(3000);
					SC_PacketBox.send(pc, SC_PacketBox.ROUND, 3, 4);
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17703");
					//적들이 더 몰려옵니다. 준비해 주세요
					pc.getInventory().CreateOrAddItem(810006, 1);
					SC_SystemMessage.send(pc, "$17948");
					//7시 메가+비아
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200011, 0, 0, 7);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200015, 0, 0, 7);
					Sleep(3000);
					//11시 포르시스, 베네보스
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200008, 0, 0, 7);
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200012, 0, 0, 7);
					Sleep(5000);
					//1시 메두사+스콜피온, 5시 코트룻+페르페르 스폰, 바람의대정령
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200009, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200013, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200010, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200014, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200016, 0, 0, 1);
					Sleep(10000);
					//7시 메가+비아
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200011, 0, 0, 7);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200015, 0, 0, 7);

					Sleep(5000);
					//11시, 1시, 5시
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200008, 0, 0, 7);
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200012, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200009, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200013, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200010, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200014, 0, 0, 7);

					stage = 5;
					break;
					/** 4단계 **/
				case FOURTH_STEP:
					Sleep(3000);
					SC_PacketBox.send(pc, SC_PacketBox.ROUND, 4, 4);
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17703");
					//적들이 더 몰려옵니다. 준비해 주세요
					pc.getInventory().CreateOrAddItem(810006, 1);
					SC_SystemMessage.send(pc, "$17948");
					SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17706", 0);
					//얼마 남지 않았습니다. 조금만 더 버텨주세요

					//4군대 동시 스폰
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200008, 0, 0, 7);
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200012, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200009, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200013, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200010, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200014, 0, 0, 7);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200011, 0, 0, 7);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200015, 0, 0, 7);
					Sleep(5000);
					//4군대 동시 스폰 + 바람의대정령
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200008, 0, 0, 7);
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200012, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200009, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200013, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200010, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200014, 0, 0, 7);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200011, 0, 0, 7);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200015, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200016, 0, 0, 1);
					Sleep(5000);
					//4군대 동시 스폰
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200008, 0, 0, 7);
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200012, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200009, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200013, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200010, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200014, 0, 0, 7);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200011, 0, 0, 7);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200015, 0, 0, 7);
					Sleep(15000);
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17995 : $17713");
					//유니콘을 빼앗아가려고? 그렇게 놔둘 순 없지!!
					Sleep(5000);
					//몽환의 지배자+ 4군대 스폰
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200008, 0, 0, 7);
					L1SpawnUtil.spawnCount(32799, 32852, _map, 7200012, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200009, 0, 0, 7);
					L1SpawnUtil.spawnCount(32810, 32863, _map, 7200013, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200010, 0, 0, 7);
					L1SpawnUtil.spawnCount(32801, 32873, _map, 7200014, 0, 0, 7);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200011, 0, 0, 7);
					L1SpawnUtil.spawnCount(32789, 32861, _map, 7200015, 0, 0, 7);
					int chance = _rnd.nextInt(100) ;
					if(chance <= 30) {
						boss = L1SpawnUtil.spawnCount(32789, 32861, _map, bossInt[0], 0, 0, 1);
					}else if(chance > 30 && chance <= 70) {
						boss = L1SpawnUtil.spawnCount(32789, 32861, _map, bossInt[1], 0, 0, 1);
					}else {
						boss = L1SpawnUtil.spawnCount(32789, 32861, _map, bossInt[2], 0, 0, 1);
					}
					stage = 6;
					break;
				case LAST_STEP:
					if (boss == null) throw new IllegalStateException("Missing Fantasy Island boss");
                    if (boss.isDead()) {
						Sleep(5000);
//						pc.sendPackets(new RPacketBox(RPacketBox.GREEN_MESSAGE, "$17707"));////몽환의 지배자가 퇴치 되었습니다.
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "지배자가 퇴치 되었습니다.");
						Sleep(5000);
						SC_SkillSound.broadcast(unicorn, unicorn.getId(), 1911);
						Sleep(1000);
						unicorn.sendShape(12493);
						//Sleep(5000);
						//감사합니다!
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17708");
						SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17708", 0);

						Sleep(3000);
						//당분간 그것은 돌아올 수 없을 것입니다.
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17709");
						SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17709", 0);

						Sleep(3000);
						//어서 몽환의 섬으로 돌아가 봐야겠군요.
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17710");
						SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17710", 0);

						Sleep(3000);
						//선물을 드리고 싶군요. 마음에 드셨으면 좋겠네요.
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "$17712");
						SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17712", 0);

						Sleep(3000);
						SC_SkillSound.broadcast(unicorn, unicorn.getId(), 169);

						int itemId = 0;
						int rnd = new Random().nextInt(1000) + 1;
						if (rnd < 300) { itemId = 40074; //갑옷 마법 주문서
						} else if (rnd < 600) { itemId = 40087; //무기 마법 주문서
						} else if (rnd < 610) { itemId = 600; //뇌신검
						} else if (rnd < 620) { itemId = 601; //파멸의 대검
						} else if (rnd < 630) { itemId = 605; //광풍의 도끼
						} else if (rnd < 640) { itemId = 604; //혹한의 창
						} else if (rnd < 650) { itemId = 603; //천사의 지팡이
						} else if (rnd < 660) { itemId = 191;// 살천의 활
						} else if (rnd < 670) { itemId = 1125; //파괴의 이도류
						} else if (rnd < 680) { itemId = 1124; //파괴의 크로우
						} else if (rnd < 682) { itemId = 1136; //악몽의 장궁
						} else if (rnd < 684) { itemId = 1137; //진노의 크로우
						}
						L1ItemInstance item = CObjectDB.LoadItem().createItem(810010);
						CWorld.getInstance().getInventory(unicorn.getX(), unicorn.getY(), unicorn.getMapId()).CreateOrAddItem(item);
						L1ItemInstance item1 = CObjectDB.LoadItem().createItem(itemId);
						if (item1 != null) {
							CWorld.getInstance().getInventory(unicorn.getX(), unicorn.getY(), unicorn.getMapId()).CreateOrAddItem(item1);
						} 

						unicorn.deleteMe();
						stage = 7;
					}
					break;
				case END:
					Sleep(2000);
					if(pc.getMapId() == _map){ 
						//pc.sendPackets(new SC_ServerMessage(1480));  
						//시스템 메시지: 5초 후에 텔레포트 합니다.
						SC_SystemMessage.send(pc, "잠시 후 마을로 이동됩니다.");
					}
					Sleep(10000);
					//					Sleep(3000);
					//					if (pc.getMapId() == _map) {
					//						pc.sendPackets(new SC_ServerMessage(1478));
					//						//시스템 메시지: 10초 후에 마을로 텔레포트 합니다.
					//					}
					Sleep(5000);
					if (pc.getMapId() == _map) {
						SC_ServerMessage.send(pc, 1480);
						//시스템 메시지: 5초 후에 마을로 텔레포트 합니다.
					}
					//					Sleep(1000);
					//					if (pc.getMapId() == _map) {
					//						pc.sendPackets(new SC_ServerMessage(1481));
					//						//시스템 메시지: 4초 후에 마을로 텔레포트 합니다.
					//					}
					//					Sleep(1000);
					//					if (pc.getMapId() == _map) {
					//						pc.sendPackets(new SC_ServerMessage(1482));
					//						//시스템 메시지: 3초 후에 마을로 텔레포트 합니다.
					//					}
					//					Sleep(1000);
					//					if (pc.getMapId() == _map) {
					//						pc.sendPackets(new SC_ServerMessage(1483));
					//						//시스템 메시지: 2초 후에 마을로 텔레포트 합니다.
					//					}
					//					Sleep(1000);
					//					if (pc.getMapId() == _map) {
					//						pc.sendPackets(new SC_ServerMessage(1484));
					//						//시스템 메시지: 1초 후에 마을로 텔레포트 합니다.
					//					}
                    finishPlayer(33437, 32813);
                    return;
                default:
                    break;
                }
                Sleep(1500);
            }
        } catch (InterruptedException stopping) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Fantasy Island stage failed for map " + _map, failure);
        }
    }

    private void Sleep(int time) throws InterruptedException {
        Thread.sleep(time);
        if (!inside() || CInstanceEvents.isStopping()) throw new InterruptedException("Player left Fantasy Island");
    }

    private boolean ownsPlayer() {
        return pc.getSessionStamp() == sessionStamp && !pc.isLogoutStarted()
                && CWorld.getInstance().findObject(pc.getId()) == pc;
    }

    private boolean inside() {
        return ownsPlayer() && pc.getMap() == zone.map();
    }

	private void setting(){
		for(L1NpcInstance npc : BasicNpcList){
			if(npc != null){
				if(npc.getName().equalsIgnoreCase("유니콘")){
					unicorn = npc;
				}
			}
		}
	}

	private void checkHp() {
		if ((unicorn.getMaxHp() * 1 / 5) > unicorn.getCurrentHp()) { //2000
			if (_status != 4) {
				SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17949", 0);
				//더 이상은 힘들 것 같습니다.
				_status = 4;
			}
		} else if ((unicorn.getMaxHp() * 2 / 5) > unicorn.getCurrentHp()) { //4000
			if (_status != 3) {
				SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17950", 0);
				//조금만 더 버틸 수 있다면...
				_status = 3;
			}
		} else if ((unicorn.getMaxHp() * 3 / 5) > unicorn.getCurrentHp()) { //6000
			if (_status != 2) {
				SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17952", 0);
				_status = 2;
			}
		} else if ((unicorn.getMaxHp() * 4 / 5) > unicorn.getCurrentHp()) { //8000
			if (_status != 1) {
				SC_NpcChatPacket.broadcastNpc(unicorn, unicorn, "$17952", 0);
				_status = 1;
			}
		}
	}

    private void finishPlayer(int x, int y) {
        if (!inside()) return;
        pc.start_teleport(x, y, 4, 2, 169, true, false);
        returnRequested = true;
        pc.getInventory().DeleteItemInInventory(810006);
        pc.getInventory().DeleteItemInInventory(810007);
    }

    void cleanup() {
        if (!began) return;
        Thread.interrupted();
        if (started && !returnRequested && inside()) {
            try {
                finishPlayer(33437, 32813);
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Fantasy Island player cleanup failed for map " + _map, failure);
            }
        }
        if (started && ownsPlayer() && !inside()) pc.isInFantasy = false;
        for (L1Object object : CWorld.getInstance().snapshotZoneOccupants(zone.map())) {
            if (object == null || object.getMap() != zone.map()) continue;
            try {
                if (object instanceof L1ItemInstance item) {
                    CInventory ground = CWorld.getInstance().findGroundInventory(item.getX(), item.getY(), _map);
                    if (ground != null && ground.getMap() == zone.map()) ground.DeleteItemAmount(item);
                } else if (object instanceof L1NpcInstance npc && !CInstantZoneManager.isPlayerCompanion(npc)) {
                    npc.deleteMe();
                }
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Fantasy Island object cleanup failed for map " + _map, failure);
            }
        }
        BasicNpcList = null;
        NpcList = null;
        unicorn = null;
        boss = null;
        if (started) LOG.info(() -> "Fantasy Island closed for " + pc.getName() + " on map " + _map);
    }
}
