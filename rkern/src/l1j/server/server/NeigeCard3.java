package l1j.server.server;





import l1j.server.Config;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.L1CardAlchemy1;
import l1j.server.server.model.item.Card.L1CardAlchemy2;
import l1j.server.server.model.item.Card.L1CardAlchemy3;
import l1j.server.server.model.item.Card.L1CardB;
import l1j.server.server.model.item.Card.L1CardC;
import l1j.server.server.model.item.Card.L1CardHPast;
import l1j.server.server.model.item.Card.L1CardLPast;
import l1j.server.server.model.item.Card.L1CardLegendList;
import l1j.server.server.model.item.Card.L1CardMyPast;
import l1j.server.server.model.item.Card.L1CardMythClass;
import l1j.server.server.model.item.Card.L1CardMythList;
import l1j.server.server.model.item.Card.L1CardStep1;
import l1j.server.server.model.item.Card.L1CardStep3;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

public class NeigeCard3{

	boolean spawnTF = false;  


	private static NeigeCard3 _instance; 
	
	private NeigeCard3() {
	}

	public static NeigeCard3 getInstance() {
		if (_instance == null) {
			_instance = new NeigeCard3(); 
		}
		return _instance;
	}
	


public static boolean NeigeMyth(L1PcInstance pc, String s) {

	
	
		String s2 = s;
		int a = 0;
		// Pending CacheD card receipts own the selection until their CIO completion.
		// Do not let UI changes overwrite a committed synthesis selection.
		if (pc.isItemTransferBusy() && s2 != null
		        && (s2.equals("rnmyfir")
                || s2.equals("rnmypl")
                || s2.startsWith("rnmych")
                || s2.equals("rnmysh")
                || s2.startsWith("rnmysce"))) return true;
		switch (s2) {
		case "rnmym01":
			L1CardMythList.cardlist(pc);
			return true;
			//신화*********************************************************************************************
		case "rnmyl01":
				L1CardMythClass.polyclass(pc);
			return false;
		case "rnmyl02":
			if(pc.getAccount().getInfo_bapo_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			L1PolyMorph.doPoly(pc, 21417, -1, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			}
			
			return false;
		case "rnmyl03":
			if(pc.getAccount().getInfo_dsn_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			L1PolyMorph.doPoly(pc, 21413, -1, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			}
			return false;
		case "rnmyl04":
			int num = 0;
			if(pc.getAccount().getmyth01() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
				if(pc.get_sex() == 0){num = 21405;}else{num = 21409;}
			L1PolyMorph.doPoly(pc, num, -1, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			}
			return false;
		case "rnmyl05":
			int num2 = 0;
			if(pc.getAccount().getInfo_il_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
				if(pc.get_sex() == 0){num2 = 21132;}else{num2 = 21136;}
			L1PolyMorph.doPoly(pc, num2, -1, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			}
			return false;
			//첫 선택************************************************************************************
		case "rnmyfir":
			pc.getAccount().setInfo_card_a(0);
			pc.getAccount().setInfo_card_b(0);
			pc.getAccount().setInfo_card_c(0);
			pc.getAccount().setInfo_card_d(0);
			L1CardStep3.LegendAlchemyF(pc);
			return true;
			//랜덤 선택***********************************************************************************
		case "rnmypl":
			L1CardMyPast.L1PastSelct(pc);
			return true;
			//카드 취소***********************************************************************************
		case "rnmych1":
			pc.getAccount().setInfo_card_a(0);
			L1CardC.cardlist(pc);
			return true;
		case "rnmych2":
			pc.getAccount().setInfo_card_b(0);
			L1CardC.cardlist(pc);
			return true;
		case "rnmych3":
			pc.getAccount().setInfo_card_c(0);
			L1CardC.cardlist(pc);
			return true;
		case "rnmych4":
			pc.getAccount().setInfo_card_d(0);
			L1CardC.cardlist(pc);
			return true;
			//합성하기 클릭 ********************************************************************************
		case "rnmysh":
			SC_SkillSound.send(pc, pc.getId(), 21174);
			if(Config.sole_possible == 1){
				SC_ChatPacket.sendDirect(pc, pc, "\\aG현재 제한된 컨텐츠 입니다.");
				return false;
			}
			L1CardAlchemy3.MythAlchemy(pc);
			return true;
			//*******************합성 시도 ****************************************************************************************************

		case "rnmysce201":
			a = 201;
			if(pc.getAccount().getInfo_class_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a || pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() ==0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardC.cardlist(pc);
			}
			return true;
		case "rnmysce202":
			a = 202;
			if(pc.getAccount().getInfo_ki_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() ==0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardC.cardlist(pc);
			}
			return true;
		case "rnmysce203":
			a = 203;
			if(pc.getAccount().getInfo_el_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() ==0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardC.cardlist(pc);
			}
			return true;
		case "rnmysce204":
			a = 204;
			if(pc.getAccount().getInfo_wi_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() ==0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardC.cardlist(pc);
			}
			return true;
		case "rnmysce205":
			a = 205;
			if(pc.getAccount().getInfo_da_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() ==0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardC.cardlist(pc);
			}
			return true;
		case "rnmysce206":
			a = 206;
			if(pc.getAccount().getInfo_wa_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() ==0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardC.cardlist(pc);
			}
			return true;
		case "rnmysce207":
			a = 207;
			if(pc.getAccount().getInfo_drag_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() ==0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardC.cardlist(pc);
			}
			return true;
		case "rnmysce208":
			a = 208;
			if(pc.getAccount().getInfo_il_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() ==0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardC.cardlist(pc);
			}
			return true;
		case "rnmysce209":
			a = 209;
			if(pc.getAccount().getInfo_dsn_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() ==0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardC.cardlist(pc);
			}
			return true;
		case "rnmysce210":
			a = 210;
			if(pc.getAccount().getInfo_bapo_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() ==0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardC.cardlist(pc);
			}
			return true;
		case "rnmysce211":
			a = 211;
			if(pc.getAccount().getmyth01() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() ==0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardC.cardlist(pc);
			}
			return true;
		case "rsnmyguide1": //0 >1p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop03");
			return false;
		case "rsnmyguide2": //1 <0p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop02");
			return false; //03
		case "rsnmyguide3": // 1 2p>
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop04");
			return false; 
		case "rsnmyguide4": // 2 <1p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop03");
			return false;//04
		case "rsnmyguide5": // 2 > 3p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop05");
			return false;
		case "rsnmyguide6": // 3 <2p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop04");
			return false; //5
		case "rsnmyguide7": // 3 > 4p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop06");
			return false;
		case "rsnmyguide8": // 4 <3p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop05");
			return false; //6
		case "rsnmyguide9": // 4 > 5p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop07");
			return false;
		case "rsnmyguide10": // 5 <4p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop06");
			return false;
		case "rsnmyguide11": // 5 > 6p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop08");
			return false;
		case "rsnmyguide12": // 6 <5p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop07");
			return false;
		case "rsnmyguide13": // 6 > 7p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop09");
			return false;
		case "rsnmyguide14": // 7 <6p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop08");
			return false;
		case "rsnmyguide15": // 7> 8p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop10");
			return false;
		case "rsnmyguide16": // 8 <7p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop09");
			return false;
		case "rsnmyguide17": // 8 > 9p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop11");
			return false;
		case "rsnmyguide18": // 9 <8p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop10");
			return false;
		case "rsnmyguide19": // 9 > 10p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop12");
			return false;
		case "rsnmyguide20": // 10 <9p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop11");
			return false;
		case "rsnmyguide21": // 10 > 11p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop13");
			return false;
		case "rsnmyguide22": // 11 <10p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop12");
			return false;
		case "rsnmyguide23": // 11 > 12p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop14");
			return false;
		case "rsnmyguide24": // 12 <11p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop13");
			return false;
		case "rsnmyguide25": // 12 > 13p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop15");
			return false;
		case "rsnmyguide26": // 13 <12p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop14");
			return false;
		case "rsnmyguide27": // 13 > 14p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop16");
			return false;
		case "rsnmyguide28": // 14 <13p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop15");
			return false;
		case "rsnmyguide29": // 14 > 15p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop17");
			return false;
		case "rsnmyguide30": // 15 <14p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop16");
			return false;
		case "rsnmyguide31": // 15 > 16p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop18");
			return false;
		case "rsnmyguide32": // 16 <15p
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop17");
			return false;
			
			
	     default: {
	    	
	            return false;
	        }
	     
	    }
		
	}
	
	
}
