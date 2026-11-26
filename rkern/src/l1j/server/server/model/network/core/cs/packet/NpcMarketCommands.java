package l1j.server.server.model.network.core.cs.packet;

import java.util.List;

import l1j.server.server.model.character.combat.arena.dogfight.DogFightSettings;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistory;
import l1j.server.server.model.shop.ShopTable;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.network.core.sc.packet.SC_ClassShop;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_NoSell;
import l1j.server.server.model.network.core.sc.packet.SC_PremiumShopSellList;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_ShopBuyList;
import l1j.server.server.model.network.core.sc.packet.SC_ShopSellList;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.shop.L1AssessedItem;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.SC_PERSONAL_SHOP_ITEM_LIST_NOTI;

final class NpcMarketCommands {
    private NpcMarketCommands() {}

	static boolean isNpcSellOnly(L1NpcInstance npc) {
		int npcId = npc.getNpcTemplate().get_npcId();
		String npcName = npc.getNpcTemplate().get_name();
		if (npcId == 70027 || "에덴상단".equals(npcName)) {
			return true;
		}
		return false;
	}

    /** True stops the packet; ordinary shop listing keeps legacy fallthrough. */
    static boolean buy(L1PcInstance pc, L1Object obj, int objid) {
		int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
		if (npcid == 7320055) {
			SC_SystemMessage.send(pc, "이 상점은 이용할 수 없습니다.");
			return true;
		}
		if (pc.getInventory().getWeight100() > 90 && !pc.is_top_ranker()) {
			SC_ServerMessage.send(pc, 3561);
			return true;
		}
		if(npcid == DogFightSettings.CASTER_NPC_ID){
			DogFightHistory.getInstance().on_show_ticket_view(pc, (L1NpcInstance)obj);
			return true;
		}
		L1NpcInstance npc = (L1NpcInstance) obj;
		if (isNpcSellOnly(npc)) {
			return true;
		}
		/** 프리미엄 상점목록 상점 추가 [3] **/
		if (npcid == 5 || npcid == 200000 || npcid == 200060 || npcid == 200061 || npcid == 200062 || npcid == 200063
				|| npcid == 5000000 || npcid == 5000006 || npcid == 900047 || npcid == 5072 || npcid == 5073 || npcid == 523
				|| npcid == 7000077 || npcid == 900107 || npcid == 7200002 || npcid == 6000002 || npcid == 7310101 || npcid == 8500140
				/*|| npcid == 7310103*/ || npcid == 7310113 || npcid == 91245 || npcid == 7310119 || npcid == 7310120
				|| npcid == 7310126 || npcid == 7310124 || npcid == 7310128 || npcid == 70017 || npcid == 200005 || npcid == 7320250
				|| npcid == 7320222 || npcid == 7320223 || npcid == 460000361 || npcid == 460000360
				|| npcid == 7320124 || npcid == 519 || npcid == 520 || npcid == 521 || npcid == 224 || npcid == 2999 || npcid == 2998) {
			SC_PremiumShopSellList.send(pc, npcid, objid);
			return true;
		} else if (npcid == 81005 ||npcid == 81008 || npcid == 200004 || npcid == 900171 || npcid == 70010 || npcid == 81006
				|| npcid == 5000009 || npcid == 5000007 || npcid == 5000008 || npcid == 5000010 || npcid == 779199
				|| npcid >= 5000001 && npcid <= 5000004 || npcid >= 5000011 && npcid <= 5000018 
				|| npcid == 900173 || npcid == 7310103 || npcid == 7310200 || npcid == 7310201|| npcid == 7310202 || npcid == 7310104 || npcid == 7310199 || npcid == 7310102 || npcid == 7310125) {
			SC_PERSONAL_SHOP_ITEM_LIST_NOTI.do_send_for_npc(pc, objid, SC_PERSONAL_SHOP_ITEM_LIST_NOTI.ePersonalShopType.TRADE_BUY);
			return true;
		}
		if (npcid == 7320121 || npcid == 7320085) {
			SC_ClassShop.send(pc, objid);
			return true;
		}
		if (npcid == 2020561) {// 훈련 교관 레벨 제한
			SC_ClassShop.send(pc, objid);
			return true;
		}
		if (npcid == 415333) {// 방어구 상점
			SC_ClassShop.send(pc, objid);
			return true;				
		}
		SC_ShopSellList.send(objid, pc);
        return false;
    }


    static void sell(L1PcInstance pc, L1Object obj, int objid) {
        String htmlid = null;
				int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
				if (npcid == 7320055) {
					SC_SystemMessage.send(pc, "이 상점은 이용할 수 없습니다.");
					return;
				}
				if (npcid == 70523 || npcid == 70805) {
					htmlid = "ladar2";
				} else if (npcid == 70537 || npcid == 70807) {
					htmlid = "farlin2";
				} else if (npcid == 70525 || npcid == 70804) {
					htmlid = "lien2";
				} else if (npcid == 50527 || npcid == 50505 || npcid == 50519 || npcid == 50545 || npcid == 50531
						|| npcid == 50529 || npcid == 50516 || npcid == 50538 || npcid == 50518 || npcid == 50509
						|| npcid == 50536 || npcid == 50520 || npcid == 50543 || npcid == 50526 || npcid == 50512
						|| npcid == 50510 || npcid == 50504 || npcid == 50525 || npcid == 50534 || npcid == 50540
						|| npcid == 50515 || npcid == 50513 || npcid == 50528 || npcid == 50533 || npcid == 50542
						|| npcid == 50511 || npcid == 50501 || npcid == 50503 || npcid == 50508 || npcid == 50514
						|| npcid == 50532 || npcid == 50544 || npcid == 50524 || npcid == 50535 || npcid == 50521
						|| npcid == 50517 || npcid == 50537 || npcid == 50539 || npcid == 50507 || npcid == 50530
						|| npcid == 50502 || npcid == 50506 || npcid == 50522 || npcid == 50541 || npcid == 50523
						|| npcid == 50620 || npcid == 50623 || npcid == 50619 || npcid == 50621 || npcid == 50622
						|| npcid == 50624 || npcid == 50617 || npcid == 50614 || npcid == 50618 || npcid == 50616
						|| npcid == 50615 || npcid == 50626 || npcid == 50627 || npcid == 50628 || npcid == 50629
						|| npcid == 50630 || npcid == 50631) {
					String sellHouseMessage = NpcCastleActions.sellHouse(pc, objid, npcid);
					if (sellHouseMessage != null) {
						htmlid = sellHouseMessage;
					}
				} else {
					if(npcid == DogFightSettings.CASTER_NPC_ID){
						DogFightHistory.getInstance().on_show_sells_view(pc, (L1NpcInstance) obj);
						return;
					}
					
					if (npcid == 70041 || npcid == 70042 || npcid == 70035) {
						if (obj instanceof L1NpcInstance) {
							L1NpcInstance npc = (L1NpcInstance) obj;
							L1Shop shop = ShopTable.getInstance().get(npc.getNpcId());
							if (shop == null) {
								SC_NoSell.send(pc, npc);
								return;
							}
							List<L1AssessedItem> assessedItems = shop.assessItems(pc.getInventory());
							if (assessedItems.isEmpty()) {
								SC_NoSell.send(pc, npc);
								return;
							} else {
								SC_ShopBuyList.send(objid, pc);
							}
						}
						return;
					}
					SC_ShopBuyList.send(objid, pc);
				}
        if (htmlid != null) SC_NPCTalkReturn.send(pc, objid, htmlid, (String[]) null);
    }

}
