package l1j.server.server.model.world.instance.dungeon.bossraid;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.teleport.L1Teleport;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class BossRaidSystem {
	
	private static BossRaidSystem _instance;
	private final ArrayList<Integer> _map = new ArrayList<Integer>();
	private final Map<Integer, BossRaid> _list = new ConcurrentHashMap<Integer, BossRaid>();

	public static BossRaidSystem getInstance() {
		if (_instance == null) {
			_instance = new BossRaidSystem();
		}
		return _instance;
	} 

	public BossRaidSystem(){
		_map.add(12358);
	}

	public void startBossRaid(L1PcInstance pc){
		if(countBossRaid() >= 49){
			SC_ChatPacket.sendDirect(pc, pc, "인스턴스 던전에 진입한 인원이 너무 많습니다");
			return;
		} 
		int id = blankMapId();
		if(id != 12358) 
			L1WorldMap.getInstance().cloneMap(12358, id);
		BossRaid bossraid = new BossRaid(id);
		try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_SystemMessage.nativeWriter("3초 후에 보스레이드 맵으로 이동합니다."),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance partymember:pc.getParty().getMembers())
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,partymember);
        }
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for (L1PcInstance partymember : pc.getParty().getMembers()) {
			partymember.start_teleport(32801, 32797, (short) id, 5, 169, false, false);
		}
		_list.put(id, bossraid);
		bossraid.Start();
	}

	public int blankMapId(){
		if(_list.size() == 0)
			return 12358;
		for(int i = 12358 ; i <= 12407; i++){
			BossRaid h = _list.get(i);
			if(h == null)
				return i;
		}  
		return 12407;
	}

	public BossRaid getBossRaid(int id){
		return _list.get(id);
	}

	public void removeBossRaid(int id){
		_list.remove(id);
	}

	public int countBossRaid(){
		return _list.size();
	}
}
