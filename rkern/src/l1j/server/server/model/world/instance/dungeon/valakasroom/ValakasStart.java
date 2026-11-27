package l1j.server.server.model.world.instance.dungeon.valakasroom;

import java.util.ArrayList;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.world.instance.CInstanceEvents;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class ValakasStart {
	private final CInstantZone<ValakasStart> zone;
	private final short _map;
	private int stage = 1;
	private static final int FIRST_STEP = 1;
	private static final int WAIT_RAID = 2;
	private static final int VALAKAS = 3;
	private static final int END = 4;

	private L1NpcInstance death;
	private L1NpcInstance ifrit;
	private L1NpcInstance phoenix;
	private L1NpcInstance valakas;
	private L1NpcInstance leo1;
	private L1NpcInstance leo2;
	private L1NpcInstance leo3;

	private L1DoorInstance briddge1;
	private L1DoorInstance briddge2;
	private L1DoorInstance briddge3;

	private L1PcInstance pc;

	private boolean Running = true;

	public ArrayList<L1NpcInstance> BasicNpcList;
	ArrayList<L1NpcInstance> NpcList;
	public ArrayList<L1NpcInstance> BossList;

	ValakasStart(CInstantZone<ValakasStart> zone, L1PcInstance pc) {
		this.zone = zone;
		_map = (short) zone.mapId();
		this.pc = pc;
	}

	public int mapId() { return _map; }

	private void runRaid() {
		setting();
		while (Running && !CInstanceEvents.isStopping() && !Thread.currentThread().isInterrupted()) {
			try {

				if(NpcList != null){
					NpcList.removeIf(npc -> npc == null || npc.isDead());
				}

				if(leo1 != null && leo1.isDead() 
						&& ((briddge1.getOpenStatus() == ActionCodes.ACTION_Close))) {
					NpcList.remove(leo1);
					briddge1.open();
				}
				if(leo2 != null && leo2.isDead()
						&& ((briddge2.getOpenStatus() == ActionCodes.ACTION_Close))) {
					NpcList.remove(leo2);
					briddge2.open();
				}
				if(leo3 != null && leo3.isDead()
						&& ((briddge3.getOpenStatus() == ActionCodes.ACTION_Close))) {
					NpcList.remove(leo3);
					briddge3.open();
				}
				
				if (BossList != null && valakas != null && hasBossTrigger()) {
					stage = VALAKAS;
				}

				if(ifrit != null && ifrit.isDead()) {
					BossList.remove(ifrit);
					stage = END;
				} 
				if(phoenix != null && phoenix.isDead()) {
					BossList.remove(phoenix);
					stage = END;
				} 
				if(valakas != null && valakas.isDead()) {
					BossList.remove(valakas);
					stage = END;
				}
				switch(stage){
				case FIRST_STEP:
					Thread.sleep(2000); 
					//그 검의 힘을 이용하여 어서 이 곳을 빠져나가게.
					SC_NpcChatPacket.broadcastCharacter(death, death, "$18645");
					Thread.sleep(3000); 
					//사용하면 저주의 영향 때문인지 과거 나와 발라카스의 전투가 자꾸 떠오르더군...
					SC_NpcChatPacket.broadcastCharacter(death, death, "$18646");
					Thread.sleep(3000);
					
					//어쩌면 자네도 그 전투를 볼 수 있겠군. 가게나..더 이상 버틸 수 없을 것 같네.
					SC_NpcChatPacket.broadcastCharacter(death, death, "$18647");
					stage = WAIT_RAID;
					break;
				case WAIT_RAID:
					break;
				case VALAKAS:
					//발라카스:누가 나를 깨우는가?
					SC_NpcChatPacket.broadcastCharacter(valakas, valakas, "$18869");
					Thread.sleep(2000);
					if(pc.getMapId() == _map){ 
						//데스나이트:발라카스! 드디어 너를 만나게 되는구나..
						SC_NpcChatPacket.sendCharacter(pc, pc, "$18870");
					}

					Thread.sleep(2000);
					//발라카스:나의 잠을 깨운 댓가는..나의 노예가 되어 평생 갚게 되리라..
					SC_NpcChatPacket.broadcastCharacter(valakas, valakas, "$18871");
					Thread.sleep(2000);
					if(pc.getMapId() == _map){ 
						//데스나이트:그런 말은 내가 패배했을 때 해도 늦지 않는다.
						SC_NpcChatPacket.sendCharacter(pc, pc, "$18872");
					}
					Thread.sleep(2000);
					//발라카스:크크..자신감이 넘치는구나..
					SC_NpcChatPacket.broadcastCharacter(valakas, valakas, "$18873");
					clearBossTrigger();
					stage = WAIT_RAID;
					break;
				case END:
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
						pc.getInventory().DeleteItemInInventory(203003);
					}
					Running = false;
					break;
				default:
					break;
				}
				Thread.sleep(1000);
				checkPc();
			} catch (InterruptedException stopping) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
			} finally {
				if (!CInstanceEvents.isStopping() && !Thread.currentThread().isInterrupted()) {
					try { Thread.sleep(1500L); }
					catch (InterruptedException stopping) { Thread.currentThread().interrupt(); }
				}
			}
		}
	}

    void enter(L1PcInstance entrant) {
        if (pc != entrant) return;
        zone.enter(() -> {
            boolean wasInValakas = entrant.isInValakas;
            try {
                zone.start(this::runRaid, this::cleanup, 2000L);
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
				if(npc.getName().equalsIgnoreCase("데스나이트")){
					death = npc;
				}
				if(npc.getName().equalsIgnoreCase("화룡의 레오")){
					leo1 = npc;
				}
				if(npc.getName().equalsIgnoreCase("화룡의 붉은 레오")){
					leo2 = npc;
				}
				if(npc.getName().equalsIgnoreCase("화룡의 불타는 레오")){
					leo3 = npc;
				}
				if (npc instanceof L1DoorInstance) {
					L1DoorInstance door = (L1DoorInstance) npc;
					if(npc.getName().equalsIgnoreCase("다리1")){
						briddge1 = door;
					}
					if(npc.getName().equalsIgnoreCase("다리2")){
						briddge2 = door;
					}
					if(npc.getName().equalsIgnoreCase("다리3")){
						briddge3 = door;
					}
				}
			}
		}
		
		for(L1NpcInstance npc : BossList){
			if(npc != null){
				if(npc.getName().equalsIgnoreCase("이프리트")){
					ifrit = npc;
				}
				if(npc.getName().equalsIgnoreCase("피닉스")){
					phoenix = npc;
				}
				if(npc.getName().equalsIgnoreCase("발라카스")){
					valakas = npc;
				}
			}
		}
	}

	private boolean hasBossTrigger() {
		synchronized (pc) {
			return pc.getMap() == zone.map() && pc.isInValakasBoss;
		}
	}

	private void clearBossTrigger() {
		// World relocation uses the same monitor as this post-dialogue update.
		synchronized (pc) {
			if (pc.getMap() == zone.map()) pc.isInValakasBoss = false;
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
		ifrit = null;
		phoenix = null;
		valakas = null;
		leo1 = null;
		leo2 = null;
		leo3 = null;
		briddge1 = null;
		briddge2 = null;
		briddge3 = null;
		if (NpcList != null) NpcList.clear();
		if (BasicNpcList != null) BasicNpcList.clear();
		if (BossList != null) BossList.clear();
	}
}
