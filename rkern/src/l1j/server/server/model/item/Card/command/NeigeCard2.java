package l1j.server.server.model.item.Card.command;





import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.L1CardAlchemy1;
import l1j.server.server.model.item.Card.L1CardB;
import l1j.server.server.model.item.Card.L1CardLPast;
import l1j.server.server.model.item.Card.L1CardLegendList;
import l1j.server.server.model.item.Card.L1CardStep1;
import l1j.server.server.model.item.Card.L1CardSyarna;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
public class NeigeCard2{

	boolean spawnTF = false;  


	private static NeigeCard2 _instance; 


	private NeigeCard2() {
	}

	public static NeigeCard2 getInstance() {
		if (_instance == null) {
			_instance = new NeigeCard2(); 
		}
		return _instance;
	}
	


public static boolean NeigeLegend(L1PcInstance pc, String s) {

		String s2 = s;
		int a = 0;
		// Pending CacheD card receipts own the selection until their CIO completion.
		// Do not let UI changes overwrite a committed synthesis selection.
		if (pc.isItemTransferBusy() && s2 != null
		        && (s2.equals("rnllfir")
                || s2.equals("rnllpl")
                || s2.startsWith("rnllch")
                || s2.equals("rnllsh")
                || s2.startsWith("rnllsce"))) return true;
		switch (s2) {
		case "rnllm00":
			SC_SkillSound.send(pc, pc.getId(), 21174);
			L1CardLegendList.cardlist(pc);
			return true;
		case "rnllm01":
			L1CardLegendList.cardlistA(pc);
			return true;
		case "rnllm02":
			L1CardLegendList.cardlistB(pc);
			return true;
		case "rnllm03":
			L1CardLegendList.cardlistC(pc);
			return true;
			//전설*********************************************************************************************
			//근거리
		case "rnlll01":
			if(pc.getAccount().getInfo_ds_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);

			NeigeMorph.apply(pc, 19094);
			}
			return false;
		case "rnlll02":
			if(pc.getAccount().getInfo_dru_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 19168);
			}
			return false;
		case "rnlll03":
			if(pc.getAccount().getInfo_dev_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18291);
			}
			return false;
		case "rnlll04":
			if(pc.getAccount().getInfo_pen_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18273);
			}
			return false;
		case "rnlll05":
			if(pc.getAccount().getInfo_atun_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18269);
			}
			return false;
		case "rnlll06":
			if(pc.getAccount().getInfo_mar_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 18295);
			}
			return false;
		case "rnlll07":
			if(pc.getAccount().getlegend01() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21262);
			}
			return false;
		case "rnlll08":
			if(pc.getAccount().getlegend02() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21270);
			}
			return false;
		case "rnlll09":
			if(pc.getAccount().getlegend03() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21274);
			}
			return false;
		case "rnlll10":
			if(pc.getAccount().getlegend04() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21282);
			}
			return false;
		case "rnlll11":
			if(pc.getAccount().getlegend05() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21286);
			}
			return false;
		case "rnlll12":
			if(pc.getAccount().getlegend06() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21290);
			}
			return false;
		case "rnlll13":
			if(pc.getAccount().getlegend07() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21294);
			}
			return false;
		case "rnlll14":
			if(pc.getAccount().getlegend08() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21297);
			}
			return false;
		case "rnlll15":
			L1CardSyarna.polyclass(pc);
			return false;
		case "rnlll16":
			if(pc.getAccount().getlegend10() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21310);
			}
			return false;
			//원거리
		case "rnlll17":
			if(pc.getAccount().getInfo_ga_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 19995);
			}
			return false;
		case "rnlll18":
			if(pc.getAccount().getInfo_moon_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 19510);
			}
			return false;
		case "rnlll19":
			if(pc.getAccount().getlegend11() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21258);
			}
			return false;
		case "rnlll20":
			if(pc.getAccount().getlegend12() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21266);
			}
			return false;
		case "rnlll21":
			if(pc.getAccount().getlegend13() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21278);
			}
			return false;
			//마법
		case "rnlll22":
			if(pc.getAccount().getInfo_ger_poly() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 19955);
			}
			return false;
		case "rnlll23":
			if(pc.getAccount().getlegend14() != 0){
				if (!(pc.getInventory().HaveItemByClassId(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return false;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return false;
				}
				pc.getInventory().DeleteItemInInventory(875640379, 1);
			NeigeMorph.apply(pc, 21304);
			}
			return false;
			//첫 선택************************************************************************************
		case "rnllfir":
			pc.getAccount().setInfo_card_a(0);
			pc.getAccount().setInfo_card_b(0);
			pc.getAccount().setInfo_card_c(0);
			pc.getAccount().setInfo_card_d(0);
			L1CardStep1.LegendAlchemyF(pc);
			return true;
			//랜덤 선택***********************************************************************************
		case "rnllpl":
			L1CardLPast.L1PastSelct(pc);
			return true;
			//카드 취소***********************************************************************************
		case "rnllch1":
			pc.getAccount().setInfo_card_a(0);
			L1CardB.cardlist(pc);
			return true;
		case "rnllch2":
			pc.getAccount().setInfo_card_b(0);
			L1CardB.cardlist(pc);
			return true;
			//합성하기 클릭 ********************************************************************************
		case "rnllsh":
			SC_SkillSound.send(pc, pc.getId(), 21174);
			if(Config.myth_possible == 1){
				SC_ChatPacket.sendDirect(pc, pc, "\\aG현재 제한된 컨텐츠 입니다.");
				return false;
			}
			L1CardAlchemy1.LegendAlchemy(pc);
			return true;
			//*******************합성 시도 ****************************************************************************************************

		case "rnllsce101":
			a = 101;
			if(pc.getAccount().getInfo_ds_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce102":
			a = 102;
			if(pc.getAccount().getInfo_dru_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce103":
			a = 103;
			if(pc.getAccount().getInfo_ga_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce104":
			a = 104;
			if(pc.getAccount().getInfo_ger_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce105":
			a = 105;
			if(pc.getAccount().getInfo_dev_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce106":
			a = 106;
			if(pc.getAccount().getInfo_pen_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce107":
			a = 107;
			if(pc.getAccount().getInfo_atun_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce108":
			a = 108;
			if(pc.getAccount().getInfo_moon_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce109":
			a = 109;
			if(pc.getAccount().getInfo_mar_poly() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce110":
			a = 110;
			if(pc.getAccount().getlegend01() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce111":
			a = 111;
			if(pc.getAccount().getlegend12() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce112":
			a = 112;
			if(pc.getAccount().getlegend02() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce113":
			a = 113;
			if(pc.getAccount().getlegend03() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce114":
			a = 114;
			if(pc.getAccount().getlegend04() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce115":
			a = 115;
			if(pc.getAccount().getlegend05() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce116":
			a = 116;
			if(pc.getAccount().getlegend06() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce117":
			a = 117;
			if(pc.getAccount().getlegend07() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce118":
			a = 118;
			if(pc.getAccount().getlegend08() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce119":
			a = 119;
			if(pc.getAccount().getlegend09() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce120":
			a = 120;
			if(pc.getAccount().getlegend10() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce121":
			a = 121;
			if(pc.getAccount().getlegend11() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce122":
			a = 122;
			if(pc.getAccount().getlegend13() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce123":
			a = 123;
			if(pc.getAccount().getlegend14() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce124":
			a = 124;
			if(pc.getAccount().getlegend15() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce125":
			a = 125;
			if(pc.getAccount().getlegend16() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce126":
			a = 126;
			if(pc.getAccount().getlegend17() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce127":
			a = 127;
			if(pc.getAccount().getlegend18() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce128":
			a = 128;
			if(pc.getAccount().getlegend19() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce129":
			a = 129;
			if(pc.getAccount().getlegend20() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce130":
			a = 130;
			if(pc.getAccount().getlegend21() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce131":
			a = 131;
			if(pc.getAccount().getlegend22() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce132":
			a = 132;
			if(pc.getAccount().getlegend23() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce133":
			a = 133;
			if(pc.getAccount().getlegend24() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
		case "rnllsce134":
			a = 134;
			if(pc.getAccount().getlegend25() > 1){
				if(pc.getAccount().getInfo_card_a() == a || pc.getAccount().getInfo_card_b() == a){
					return false;
				}
				if(pc.getAccount().getInfo_card_a() == 0){
					pc.getAccount().setInfo_card_a(a);
				}else{
					pc.getAccount().setInfo_card_b(a);
				}
				L1CardB.cardlist(pc);
			}
			return true;
	     default: {
	    	
	            return false;
	        }
	     
	    }
		
	}
	
	
}
