package l1j.server.server;


import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.L1CardA;
import l1j.server.server.model.item.Card.L1CardAlchemy2;
import l1j.server.server.model.item.Card.L1CardB;
import l1j.server.server.model.item.Card.L1CardC;
import l1j.server.server.model.item.Card.L1CardHPast;
import l1j.server.server.model.item.Card.L1CardHeroList;
import l1j.server.server.model.item.Card.L1CardLegendList;
import l1j.server.server.model.item.Card.L1CardStep2;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;


//환부 만들기
public class NeigeTower{

	boolean spawnTF = false;  


	private static NeigeTower _instance; 
	
	private NeigeTower() {
	}

	public static NeigeTower getInstance() {
		if (_instance == null) {
			_instance = new NeigeTower(); 
		}
		return _instance;
	}
	


	public static void Create(L1PcInstance pc) {
	
		int num = 830022;
		int cnt = 0;
		int cnt2 = 0;
		while(cnt2 < 10){
			if(pc.getInventory().HaveItemByClassId(num, 1)){cnt += 1;}
			cnt2++;
			num++;
		}
		if(cnt != 10){
			pc.sendPackets("부적이 모자랍니다.");
			return;
		}else{
			if(!pc.getInventory().HaveItemByClassId(40308, 10000000)){
				pc.sendPackets("아데나가 모자랍니다.");
				return;
			}
			num = 830022;
			cnt = 0;
			cnt2 = 0;
			while(cnt2 < 10){
				pc.getInventory().consumeItem(num, 1);
				cnt2++;
				num++;
			}
			pc.getInventory().consumeItem(40308, 10000000);
			SC_SystemMessage.send(pc, "아이템 제작에 성공했습니다.");
			SC_SkillSound.send(pc, pc.getId(), 7976);
			SC_SkillSound.broadcast(pc, pc.getId(), 7976);
			pc.getInventory().CreateOrAddItem(560028, 1);
			SC_ChatPacket.sendDirect(pc, pc, "환상의 오만의 탑 지배 부적을 획득하였습니다.", 1);
			
		}

}
	
	
}
