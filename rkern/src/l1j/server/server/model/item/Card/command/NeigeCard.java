package l1j.server.server.model.item.Card.command;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.L1CardA;
import l1j.server.server.model.item.Card.L1CardAlchemy2;
import l1j.server.server.model.item.Card.L1CardHPast;
import l1j.server.server.model.item.Card.L1CardHeroList;
import l1j.server.server.model.item.Card.L1CardStep2;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

public class NeigeCard{

	boolean spawnTF = false;  


	private static NeigeCard _instance; 
	
	private NeigeCard() {
	}

	public static NeigeCard getInstance() {
		if (_instance == null) {
			_instance = new NeigeCard(); 
		}
		return _instance;
	}
	


public static boolean Neige(L1PcInstance pc, String s) {

		String s2 = s;
		int a = 0;
		// Pending CacheD card receipts own the selection until their CIO completion.
		// Do not let UI changes overwrite a committed synthesis selection.
		if (pc.isItemTransferBusy() && s2 != null
		        && (s2.equals("rnlpolyfir")
                || s2.equals("rnlpolyph")
                || s2.startsWith("rnlpolych")
                || s2.equals("rnlpolysh")
                || s2.startsWith("rnalce"))) return true;
		switch (s2) {
		case "rnlpolyshm00":
			SC_NPCTalkReturn.send(pc, pc.getId(), "2008bonus1");
			return true;
		case "rnlpolyshm01":
			L1CardHeroList.cardlistA(pc);
			return true;
		case "rnlpolyshm02":
			L1CardHeroList.cardlistB(pc);
			return true;
		case "rnlpolyshm03":
			L1CardHeroList.cardlistC(pc);
			return true;
		//영웅*********************************************************************************************
			//만능 3가지
		case "rnlpolyh01":
			if(pc.getAccount().getInfo_heaven_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 20528);
			}
			return false;
		case "rnlpolyh02":
			if(pc.getAccount().gethero01() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21226);
			}
			return false;
		case "rnlpolyh03":

			if(pc.getAccount().gethero02() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21233);
			}
			return false;
			//근거리
		case "rnlpolyh04":
			if(pc.getAccount().gethero03() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21184);
			}
			return false;
		case "rnlpolyh05":
			if(pc.getAccount().gethero04() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21187);
			}
			return false;
		case "rnlpolyh06":
			if(pc.getAccount().gethero05() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21218);
			}
			return false;
		case "rnlpolyh07":
			if(pc.getAccount().gethero06() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21180);
			}
			return false;
		case "rnlpolyh08":
			if(pc.getAccount().gethero07() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21194);
			}
			return false;
		case "rnlpolyh09":
			if(pc.getAccount().getInfo_assm_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18347);
			}
			return false;
		case "rnlpolyh10":
			if(pc.getAccount().gethero08() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21198);
			}
			return false;
		case "rnlpolyh11":
			if(pc.getAccount().gethero09() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21202);
			}
			return false;
		case "rnlpolyh12":
			if(pc.getAccount().getInfo_unik_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18375);
			}
			return false;
		case "rnlpolyh13":
			if(pc.getAccount().gethero10() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21237);
			}
			return false;
		case "rnlpolyh14":
			if(pc.getAccount().gethero11() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21241);
			}
			return false;
		case "rnlpolyh15":
			if(pc.getAccount().getInfo_val_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18320);
			}
			return false;
		case "rnlpolyh16":
			if(pc.getAccount().getInfo_larry_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18323);
			}
			return false;
		case "rnlpolyh17":
			if(pc.getAccount().getInfo_gunter_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18326);
			}
			return false;
		case "rnlpolyh18":
			if(pc.getAccount().getInfo_depor_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18329);
			}
			return false;
		case "rnlpolyh19":
			if(pc.getAccount().getInfo_deathred_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18340);
			}
			return false;
		case "rnlpolyh20":
			if(pc.getAccount().getInfo_ecil_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18344);
			}
			return false;
		case "rnlpolyh21":
			if(pc.getAccount().gethero12() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21249);
			}
			return false;
			//원거리
		case "rnlpolyh22":
			if(pc.getAccount().gethero13() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21189);
			}
			return false;
		case "rnlpolyh23":
			if(pc.getAccount().gethero14() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21192);
			}
			return false;
		case "rnlpolyh24":
			if(pc.getAccount().gethero15() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21209);
			}
			return false;
		case "rnlpolyh25":
			if(pc.getAccount().gethero16() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21222);
			}
			return false;
		case "rnlpolyh26":
			if(pc.getAccount().getInfo_unir_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18388);
			}
			return false;
		case "rnlpolyh27":
			if(pc.getAccount().getInfo_yozil_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18391);
			}
			return false;
		case "rnlpolyh28":
			if(pc.getAccount().getInfo_owen_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18394);
			}
			return false;
		case "rnlpolyh29":
			if(pc.getAccount().getInfo_helv_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18398);
			}
			return false;
			//마법
		case "rnlpolyh30":
			if(pc.getAccount().gethero17() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21205);
			}
			return false;
		case "rnlpolyh31":
			if(pc.getAccount().gethero18() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21213);
			}
			return false;
		case "rnlpolyh32":
			if(pc.getAccount().gethero19() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21229);
			}
			return false;
		case "rnlpolyh33":
			if(pc.getAccount().gethero20() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21245);
			}
			return false;
		case "rnlpolyh34":
			if(pc.getAccount().getInfo_unim_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18379);
			}
			return false;
		case "rnlpolyh35":
			if(pc.getAccount().getInfo_kerep_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18368);
			}
			return false;
		case "rnlpolyh36":
			if(pc.getAccount().getInfo_jowo_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18372);
			}
			return false;
			//첫 선택************************************************************************************
		case "rnlpolyfir":
			pc.getAccount().setInfo_card_a(0);
			pc.getAccount().setInfo_card_b(0);
			pc.getAccount().setInfo_card_c(0);
			pc.getAccount().setInfo_card_d(0);
			SC_SkillSound.send(pc, pc.getId(), 21174);
			L1CardStep2.LegendAlchemyF(pc);
			return true;
			//랜덤 선택***********************************************************************************
		case "rnlpolyph":
			L1CardHPast.L1PastSelct(pc);
			return true;
			//카드 취소***********************************************************************************
		case "rnlpolych1":
			pc.getAccount().setInfo_card_a(0);
			L1CardA.cardlist(pc);
			return true;
		case "rnlpolych2":
			pc.getAccount().setInfo_card_b(0);
			L1CardA.cardlist(pc);
			return true;
		case "rnlpolych3":
			pc.getAccount().setInfo_card_c(0);
			L1CardA.cardlist(pc);
			return true;
		case "rnlpolych4":
			pc.getAccount().setInfo_card_d(0);
			L1CardA.cardlist(pc);
			return true;
			//합성하기 클릭 ********************************************************************************
		case "rnlpolysh":
			SC_SkillSound.send(pc, pc.getId(), 21174);
			L1CardAlchemy2.HeroAlchemy1(pc);
			return true;
			//*******************합성 시도 ****************************************************************************************************
		case "rnalce01":
			if(pc.getAccount().getInfo_heaven_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == 1 || pc.getAccount().getInfo_card_b() == 1 || pc.getAccount().getInfo_card_c() == 1 || pc.getAccount().getInfo_card_d() == 1){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(1);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(1);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(1);
				}else{
					pc.getAccount().setInfo_card_d(1);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce02":
			
			if(pc.getAccount().gethero01() > 1){
				if(pc.getAccount().getInfo_card_a() == 2 || pc.getAccount().getInfo_card_b() == 2 || pc.getAccount().getInfo_card_c() == 2|| pc.getAccount().getInfo_card_d() == 2){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(2);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(2);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(2);
				}else{
					pc.getAccount().setInfo_card_d(2);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce03":
			
			if(pc.getAccount().gethero02() > 1){
				if(pc.getAccount().getInfo_card_a() == 3 || pc.getAccount().getInfo_card_b() == 3 || pc.getAccount().getInfo_card_c() == 3|| pc.getAccount().getInfo_card_d() == 3){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(3);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(3);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(3);
				}else{
					pc.getAccount().setInfo_card_d(3);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce04":
			
			if(pc.getAccount().gethero03() > 1){
				if(pc.getAccount().getInfo_card_a() == 4 || pc.getAccount().getInfo_card_b() == 4 || pc.getAccount().getInfo_card_c() == 4|| pc.getAccount().getInfo_card_d() == 4){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(4);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(4);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(4);
				}else{
					pc.getAccount().setInfo_card_d(4);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce05":
			
			if(pc.getAccount().gethero04() > 1){
				if(pc.getAccount().getInfo_card_a() == 5 || pc.getAccount().getInfo_card_b() == 5 || pc.getAccount().getInfo_card_c() == 5|| pc.getAccount().getInfo_card_d() == 5){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(5);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(5);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(5);
				}else{
					pc.getAccount().setInfo_card_d(5);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce06":
			
			if(pc.getAccount().gethero13() > 1){
				if(pc.getAccount().getInfo_card_a() == 6 || pc.getAccount().getInfo_card_b() == 6 || pc.getAccount().getInfo_card_c() == 6|| pc.getAccount().getInfo_card_d() == 6){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(6);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(6);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(6);
				}else{
					pc.getAccount().setInfo_card_d(6);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce07":
			
			if(pc.getAccount().gethero14() > 1){
				if(pc.getAccount().getInfo_card_a() == 7 || pc.getAccount().getInfo_card_b() == 7 || pc.getAccount().getInfo_card_c() == 7|| pc.getAccount().getInfo_card_d() == 7){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(7);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(7);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(7);
				}else{
					pc.getAccount().setInfo_card_d(7);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce08":
			
			if(pc.getAccount().gethero05() > 1){
				if(pc.getAccount().getInfo_card_a() == 8 || pc.getAccount().getInfo_card_b() == 8 || pc.getAccount().getInfo_card_c() == 8|| pc.getAccount().getInfo_card_d() == 8){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(8);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(8);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(8);
				}else{
					pc.getAccount().setInfo_card_d(8);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce09":
			
			if(pc.getAccount().gethero06() > 1){
				if(pc.getAccount().getInfo_card_a() == 9 || pc.getAccount().getInfo_card_b() == 9 || pc.getAccount().getInfo_card_c() == 9|| pc.getAccount().getInfo_card_d() == 9){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(9);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(9);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(9);
				}else{
					pc.getAccount().setInfo_card_d(9);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce10":
			
			if(pc.getAccount().gethero07() > 1){
				if(pc.getAccount().getInfo_card_a() == 10 || pc.getAccount().getInfo_card_b() == 10 || pc.getAccount().getInfo_card_c() == 10|| pc.getAccount().getInfo_card_d() == 10){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(10);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(10);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(10);
				}else{
					pc.getAccount().setInfo_card_d(10);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce11":
			
			if(pc.getAccount().getInfo_assm_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == 11 || pc.getAccount().getInfo_card_b() == 11 || pc.getAccount().getInfo_card_c() == 11|| pc.getAccount().getInfo_card_d() == 11){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(11);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(11);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(11);
				}else{
					pc.getAccount().setInfo_card_d(11);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce12":
			
			if(pc.getAccount().gethero08() > 1){
				if(pc.getAccount().getInfo_card_a() == 12 || pc.getAccount().getInfo_card_b() == 12 || pc.getAccount().getInfo_card_c() == 12|| pc.getAccount().getInfo_card_d() == 12){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(12);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(12);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(12);
				}else{
					pc.getAccount().setInfo_card_d(12);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce13":
			
			if(pc.getAccount().gethero09() > 1){
				if(pc.getAccount().getInfo_card_a() == 13 || pc.getAccount().getInfo_card_b() == 13 || pc.getAccount().getInfo_card_c() == 13|| pc.getAccount().getInfo_card_d() == 13){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(13);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(13);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(13);
				}else{
					pc.getAccount().setInfo_card_d(13);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce14":
			
			if(pc.getAccount().getInfo_unik_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == 14 || pc.getAccount().getInfo_card_b() == 14 || pc.getAccount().getInfo_card_c() == 14|| pc.getAccount().getInfo_card_d() == 14){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(14);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(14);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(14);
				}else{
					pc.getAccount().setInfo_card_d(14);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce15":
			
			if(pc.getAccount().gethero10() > 1){
				if(pc.getAccount().getInfo_card_a() == 15 || pc.getAccount().getInfo_card_b() == 15 || pc.getAccount().getInfo_card_c() == 15|| pc.getAccount().getInfo_card_d() == 15){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(15);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(15);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(15);
				}else{
					pc.getAccount().setInfo_card_d(15);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce16":
			if(pc.getAccount().getInfo_unir_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == 16 || pc.getAccount().getInfo_card_b() == 16 || pc.getAccount().getInfo_card_c() == 16|| pc.getAccount().getInfo_card_d() == 16){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(16);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(16);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(16);
				}else{
					pc.getAccount().setInfo_card_d(16);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce17":
			a = 17;
			if(pc.getAccount().getInfo_unim_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce18":
			a = 18;
			if(pc.getAccount().gethero11() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce19":
			a = 19;
			if(pc.getAccount().getInfo_val_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce20":
			a = 20;
			if(pc.getAccount().getInfo_larry_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce21":
			a = 21;
			if(pc.getAccount().getInfo_gunter_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce22":
			a = 22;
			if(pc.getAccount().getInfo_depor_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce23":
			a = 23;
			if(pc.getAccount().getInfo_deathred_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce24":
			a = 24;
			if(pc.getAccount().getInfo_ecil_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce25":
			a = 25;
			if(pc.getAccount().gethero12() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce26":
			a = 26;
			if(pc.getAccount().gethero15() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce27":
			a = 27;
			if(pc.getAccount().gethero16() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce28":
			a = 28;
			if(pc.getAccount().getInfo_yozil_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce29":
			a = 29;
			if(pc.getAccount().getInfo_owen_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce30":
			a = 30;
			if(pc.getAccount().getInfo_helv_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce31":
			a = 31;
			if(pc.getAccount().gethero17() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce32":
			a = 32;
			if(pc.getAccount().gethero18() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce33":
			a = 33;
			if(pc.getAccount().gethero19() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce34":
			a = 34;
			if(pc.getAccount().gethero20() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce35":
			a = 35;
			if(pc.getAccount().getInfo_kerep_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
		case "rnalce36":
			a = 36;
			if(pc.getAccount().getInfo_jowo_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a || pc.getAccount().getInfo_card_c() == a|| pc.getAccount().getInfo_card_d() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else if(pc.getAccount().getInfo_card_b() ==0){
					pc.getAccount().setInfo_card_b(a);
				}else if(pc.getAccount().getInfo_card_c() == 0){
					pc.getAccount().setInfo_card_c(a);
				}else{
					pc.getAccount().setInfo_card_d(a);
				}
				L1CardA.cardlist(pc);
			}
			return true;
			
	     default: {
	    	
	            return false;
	        }
	     
	    }
		
	}
	
	
}
