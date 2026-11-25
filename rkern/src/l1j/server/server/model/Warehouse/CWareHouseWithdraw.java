package l1j.server.server.model.Warehouse;

import java.sql.Timestamp;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_ClanWareHouseHistory;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_RetrieveList;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.SC_WAREHOUSE_ITEM_LIST_NOTI;

/** Game-server withdrawal and list policy; no new per-request state or queue. */
public final class CWareHouseWithdraw {
    private CWareHouseWithdraw() {}

    static boolean CheckPledgeUser(L1PcInstance pc, Warehouse warehouse) {
        return warehouse instanceof ClanWarehouse
                && pc.getClanRank() != L1Clan.수련 && !pc.hasSkillEffect(L1SkillId.SetBuff);
    }

    static int FeeItem(int kind) {
        return kind == 3 || kind == 5 ? L1ItemId.ADENA : kind == 9 ? 40494 : 0;
    }

    static int FeeEach(int kind) {
        return kind == 3 || kind == 5 ? 30 : kind == 9 ? 2 : 0;
    }

    static boolean CheckFee(L1PcInstance pc, int kind, int size, int feeItem, int feeEach) {
        final int minimumFee = feeEach * size + (kind == 3 ? 1 : 0);
        if (minimumFee != 0 && pc.getInventory().GetItemAmount(feeItem) < minimumFee) {
            SC_SystemMessage.send(pc, kind == 9 ? "창고 출금 수수료 아이템이 부족합니다." : "아데나가 부족합니다.");
            return false;
        }
        return true;
    }

    static void Request(L1PcInstance pc, int kind, Warehouse warehouse, int feeItem, int feeEach,
            L1ItemInstance item, int count, Timestamp deadline) {
        final var inventory = pc.getInventory();
        if (inventory.CheckAddable(item, count) != CInventory.OK) {
            SC_ServerMessage.send(pc, 270);
            return;
        }
        L1ItemInstance admitted = deadline == null ? warehouse.tradeItem(item, count, inventory, feeItem, feeEach, null)
                : warehouse.tradeSupplementaryItem(item, count, inventory, deadline, null);
        if (admitted == null) {
            if (kind == 9) SC_ServerMessage.send(pc, 337, "$767");
            else SC_ServerMessage.send(pc, 189);
        }
    }

    static void Request(L1PcInstance pc, Warehouse warehouse, int[] ids, int[] counts,
            int feeItem, int feeEach, Timestamp[] deadlines) {
        if (!pc.getInventory().tradeWarehouseWithdrawalBatch(ids, counts, warehouse, feeItem, feeEach, deadlines, null))
            SC_SystemMessage.send(pc, "창고 아이템을 한 번에 찾을 수 없습니다. 무게와 소지 칸을 확인해 주세요.");
    }

    static Timestamp SupplementaryDeadline(L1ItemInstance item, long now) {
        int minutes = switch (item.getItemId()) {
            case 3000214 -> 8640;
            case 3000213, 758, 759, 760, 761 -> 4320;
            case 3000209, 41922, 41923, 41924, 41925, 900077, 772, 773, 774, 775 -> 1440;
            case 210095, 2100950 -> 180;
            default -> 0;
        };
        return minutes == 0 ? null : new Timestamp(now + 60_000L * minutes);
    }

    public static boolean IsListCommand(String s) {
        return s.equalsIgnoreCase("history") || s.equalsIgnoreCase("retrieve")
                || s.equalsIgnoreCase("retrieve-elven") || s.equalsIgnoreCase("retrieve-aib")
                || s.equalsIgnoreCase("retrieve-char") || s.equalsIgnoreCase("retrieve-pledge");
    }
    public static void SendList(L1PcInstance pc, int objid, String s) {
        String htmlid = null;
        if (s.equalsIgnoreCase("history")) {
				if (pc.getClanid() > 0)
					SC_ClanWareHouseHistory.send(pc);
				else
					SC_SystemMessage.send(pc, "혈맹 가입후 이용해 주세요.");
			} else if (s.equalsIgnoreCase("retrieve")) {
				
				if (pc.getLevel() >= 5) {
					SC_WAREHOUSE_ITEM_LIST_NOTI.send_user_warehouse_items(pc, objid);
				}
			} else if (s.equalsIgnoreCase("retrieve-elven")) {
				if (pc.getInventory().getSize() > 180){
					pc.sendPackets("\\f2인벤토리에 아이템이 180개 이하일때만 이용가능 합니다.   현재:(\\aG"+pc.getInventory().getSize()+"/\\aA150)\\f2 소지중입니다.");
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "인벤토리에 아이템이 180개 이하일때만 이용가능 합니다. 현재:(\\aG"+pc.getInventory().getSize()+"/\\aA180)\\f2 소지중입니다.");
					return;
				}
				if (pc.getLevel() >= 5 && pc.isElf()) {
					if (SC_RetrieveList.sendElf(pc, objid))
                        htmlid = "noitemret";
				}
			} else if (s.equalsIgnoreCase("retrieve-aib")) {
				if (SC_RetrieveList.sendPackage(pc, objid))
                    htmlid = "noitemret";
			} else if (s.equalsIgnoreCase("retrieve-char")) { // 특수창고
				if (pc.getInventory().getSize() > 180){
					pc.sendPackets("\\f2인벤토리에 아이템이 180개 이하일때만 이용가능 합니다.   현재:(\\aG"+pc.getInventory().getSize()+"/\\aA180)\\f2 소지중입니다.");
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "인벤토리에 아이템이 180개 이하일때만 이용가능 합니다. 현재:(\\aG"+pc.getInventory().getSize()+"/\\aA180)\\f2 소지중입니다.");
					return;
				}
				if (pc.get_SpecialSize() == 0) {
					SC_ServerMessage.send(pc, 1623);
					return;
				}
			} else if (s.equalsIgnoreCase("retrieve-pledge")) {
				if (pc.getInventory().getSize() > 150){
					pc.sendPackets("\\f2인벤토리에 아이템이 180개 이하일때만 이용가능 합니다.   현재:(\\aG"+pc.getInventory().getSize()+"/\\aA180)\\f2 소지중입니다.");
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "인벤토리에 아이템이 180개 이하일때만 이용가능 합니다. 현재:(\\aG"+pc.getInventory().getSize()+"/\\aA180)\\f2 소지중입니다.");
					return;
				}
				if (pc.getLevel() >= 5) {
					if (pc.getClanid() == 0) {
						SC_SystemMessage.send(pc, "혈맹창고를 사용하려면 혈맹이 있어야 합니다.");
						return;
					}
					if (pc.getClanRank() == L1Clan.수련) {
						SC_ServerMessage.send(pc, 728);
						return;
					}
					SC_WAREHOUSE_ITEM_LIST_NOTI.send_user_pledge_warehouse_items(pc, objid);
					return;
				}
        }
        if (htmlid != null) SC_NPCTalkReturn.send(pc, objid, htmlid, (String[]) null);
    }
}
