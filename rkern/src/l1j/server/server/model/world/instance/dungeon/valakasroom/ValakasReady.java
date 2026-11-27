package l1j.server.server.model.world.instance.dungeon.valakasroom;
import l1j.server.server.model.world.instance.CInstanceEvents;


import java.util.ArrayList;

import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class ValakasReady {
	private final CInstantZone<ValakasReady> zone;
	private final short _map;
	private int stage = 1;
	private static final int READY_START = 1;
	private static final int WAIT_RAID = 2;
	private static final int END = 3;

	private L1NpcInstance death;

	private L1PcInstance pc;

	private boolean Running = false;

	public ArrayList<L1NpcInstance> BasicNpcList;

	ValakasReady(CInstantZone<ValakasReady> zone, L1PcInstance pc) {
		this.zone = zone;
		_map = (short) zone.mapId();
		this.pc = pc;
	}

	public int mapId() { return _map; }

	private void runRaid() {
		setting();
		Running = true;
		while(Running && !CInstanceEvents.isStopping() && !Thread.currentThread().isInterrupted()){
			try {
				switch(stage){
				case READY_START:
					Thread.sleep(2000);
					//모..두 파괴하라..모든 것을..파괴하라..
					SC_NpcChatPacket.broadcastCharacter(death, death, "$18861");
					Thread.sleep(3000);
					//당신은 누구시죠?
					if (pc.getMapId() == _map) {
						SC_NpcChatPacket.sendCharacter(pc, pc, "$18862");
					}
					Thread.sleep(3000);
					//나? 나는..누구?? 기사..그래..나는 기사였다..그런데 나는 왜 여기에 있지?
					SC_NpcChatPacket.broadcastCharacter(death, death, "$18863");
					Thread.sleep(3000);
					//기..사?
					if (pc.getMapId() == _map) {
						SC_NpcChatPacket.sendCharacter(pc, pc, "$18864");
					}
					Thread.sleep(3000);
					//그래..나는 기사다... 자신의 작은 재주 하나만 믿고 발라카스에게 도전한 어리석은 기사..
					SC_NpcChatPacket.broadcastCharacter(death, death, "$18865");
					Thread.sleep(3000);
					//발라카스에게 패배하신겁니까?
					if (pc.getMapId() == _map) {
						SC_NpcChatPacket.sendCharacter(pc, pc, "$18866");
					}
					Thread.sleep(3000);
					//패배라..그래..결국엔 나의 패배겠지..나의 기억과 이성이 점차 사라지는 것을 보니..
					SC_NpcChatPacket.broadcastCharacter(death, death, "$18867");
					Thread.sleep(3000);
					//내가 이성을 잃기 전에 어서 이 검을 가지고 이 곳을 피하게.
					SC_NpcChatPacket.broadcastCharacter(death, death, "$18868");
					stage = WAIT_RAID;
					break;
				case WAIT_RAID:
					break;
				case END:
					if(pc.getMapId() == _map){ 
//						pc.sendPackets(new SC_ServerMessage(1478));  
						SC_SystemMessage.send(pc, "시스템 메시지: 곧 이어 텔레포트 됩니다.");
						//시스템 메시지: 30초 후에 텔레포트 합니다.
					} 
					Thread.sleep(30000);
					if(pc.getMapId() == _map){ 
//						pc.sendPackets(new SC_ServerMessage(1480));  
						SC_SystemMessage.send(pc, "시스템 메시지: 30초 후에 텔레포트 합니다.");
						//시스템 메시지:10초 후에 텔레포트 합니다.
					} 
					Thread.sleep(20000);
					if(pc.getMapId() == _map){ 
//						pc.sendPackets(new SC_ServerMessage(1481));  
						SC_SystemMessage.send(pc, "시스템 메시지: 20초 후에 텔레포트 합니다.");
						//시스템 메시지: 10초 후에 텔레포트 합니다.
					} 
					Thread.sleep(10000);
					if(pc.getMapId() == _map){ 
//						pc.sendPackets(new SC_ServerMessage(1482));  
						SC_SystemMessage.send(pc, "시스템 메시지: 10초 후에 텔레포트 합니다.");
						//시스템 메시지: 3초 후에 텔레포트 합니다.
					} 
					Thread.sleep(5000);
					if(pc.getMapId() == _map){ 
//						pc.sendPackets(new SC_ServerMessage(1483));  
						SC_SystemMessage.send(pc, "시스템 메시지: 5초 후에 텔레포트 합니다.");
						//시스템 메시지: 2초 후에 텔레포트 합니다.
					} 
					Thread.sleep(3000);
					if(pc.getMapId() == _map){ 
//						pc.sendPackets(new SC_ServerMessage(1484));  
						SC_SystemMessage.send(pc, "시스템 메시지: 3초 후에 텔레포트 합니다.");
						//시스템 메시지: 1초 후에 텔레포트 합니다.
					} 
					Thread.sleep(1000);
					if(pc.getMapId() == _map){ 
//						L1Teleport.teleport(pc, 33705, 32504, (short)4, 5, true);
						pc.start_teleport(33705, 32504, 4, 5, 169, true, false);
					}
					Running = false;
					break;
				default:
					break;
				}
				Thread.sleep(1000);
				checkPc();
			}catch(InterruptedException interrupted){
				Thread.currentThread().interrupt();
				break;
			}catch(Exception e){
			}finally{
				if (!Thread.currentThread().isInterrupted()) {
					try { Thread.sleep(1500); }
					catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
				}
			}
		}
	}

    void enter(L1PcInstance entrant) {
        if (pc != entrant) return;
        zone.enter(() -> {
            boolean wasInValakas = entrant.isInValakas;
            try {
                zone.start(this::runRaid, this::cleanup);
                entrant.isInValakas = true;
                entrant.start_teleport(32624, 33059, mapId(), 5, 169, true, false);
            } catch (RuntimeException failure) {
                entrant.isInValakas = wasInValakas;
                endRaid();
                throw failure;
            }
        });
    }


	private void setting(){
		for(L1NpcInstance npc : BasicNpcList){
			if(npc != null){
				if(npc.getName().equalsIgnoreCase("진데스나이트")){
					death = npc;
				}
			}
		}
	}

	private void checkPc() {
        if (!ValakasRoomSpawn.hasPlayers(zone)) Running = false;
    }

    void endRaid() {
        zone.close(this::cleanup);
    }

    void cleanup() {
        ValakasRoomSpawn.clearRoom(zone);

		pc = null;
		death = null;
		if (BasicNpcList != null) BasicNpcList.clear();
		Running = false;
	}
}
