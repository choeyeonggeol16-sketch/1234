package l1j.server.server.model.network.core.cs.packet;

import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.server.datatables.NoTradable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Trade;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class CS_TradeAddItem {

    private CS_TradeAddItem() {
    }

    public static void handle(
            CIOClient client,
            int itemObjectId,
            int requestedCount) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()
                || pc.isItemTransferBusy()
                || pc.isTradeResolving()) {

            return;
        }

        final L1ItemInstance item =
                pc.getInventory()
                        .getItem(itemObjectId);

        if (item == null
                || item.getItem() == null) {

            return;
        }

        /*
         * objectId 위변조 방지.
         */
        if (itemObjectId
                != item.getId()) {

            return;
        }

        int itemCount =
                requestedCount;

        if (!item.isStackable()
                && itemCount != 1) {

            return;
        }

        if (itemCount <= 0
                || item.getCount() <= 0) {

            return;
        }

        if (itemCount
                > item.getCount()) {

            itemCount =
                    item.getCount();
        }

        if (itemCount > 2_000_000_000) {
            return;
        }

        final int templateId =
                item.getItemId();

        /*
         * 캐릭터 교환 주문서 레벨 제한.
         */
        if (templateId
                == L1ItemId.HIGH_CHARACTER_TRADE
                || templateId
                == L1ItemId.LOW_CHARACTER_TRADE) {

            if (pc.getLevel() >= 70
                    && templateId
                    == L1ItemId.LOW_CHARACTER_TRADE) {

                SC_ChatPacket.sendDirect(pc, pc, "70레벨 이상은 상급 캐릭터 교환주문서를 사용하셔야 합니다.");

                return;
            }

            if (pc.getLevel() < 70
                    && templateId
                    == L1ItemId.HIGH_CHARACTER_TRADE) {

                SC_ChatPacket.sendDirect(pc, pc, "70레벨 미만은 하급 캐릭터 교환주문서를 사용하셔야 합니다.");

                return;
            }
        }

        /*
         * DB 기반 교환 금지.
         */
        if (!pc.isGm()
                && NoTradable
                        .getInstance()
                        .isNoTradable(templateId)) {

            SC_SystemMessage.send(pc, "해당 아이템은 교환 불가능합니다.");

            return;
        }

        if (!item.getItem()
                .isTradable()) {

            SC_ServerMessage.send(pc, 210, item.getItem()
                                    .getName());

            return;
        }

        if (item.getBless() >= 128) {

            SC_ServerMessage.send(pc, 210, item.getItem()
                                    .getName());

            return;
        }

        if (item.isEquipped()) {

            SC_ServerMessage.send(pc, 906);

            return;
        }

        if (!MJCompanionInstanceCache
                .is_companion_oblivion(
                        item.getId())) {

            SC_ServerMessage.send(pc, 210, item.getItem()
                                    .getName());

            return;
        }

        /*
         * 현재 소환된 pet 관련 item 검사.
         *
         * values()를 그대로 순회.
         * toArray() 같은 임시 allocation 없음.
         */
        for (Object value :
                pc.getPetList()
                        .values()) {

            if (!(value instanceof L1PetInstance)) {
                continue;
            }

            final L1PetInstance pet =
                    (L1PetInstance) value;

            if (item.getId()
                    == pet.getItemObjId()) {

                SC_ServerMessage.send(pc, 210, item.getItem()
                                        .getName());

                return;
            }
        }

        /*
         * 현재 소환된 magic doll 관련 item 검사.
         */
        for (Object value :
                pc.getDollList()
                        .values()) {

            if (!(value instanceof L1DollInstance)) {
                continue;
            }

            final L1DollInstance doll =
                    (L1DollInstance) value;

            if (item.getId()
                    == doll.getItemObjId()) {

                SC_ServerMessage.send(pc, 210, item.getItem()
                                        .getName());

                return;
            }
        }

        final L1Object partnerObject =
                L1World
                        .getInstance()
                        .findObject(
                                pc.getTradeID());

        if (!(partnerObject
                instanceof L1PcInstance)) {

            return;
        }

        final L1PcInstance tradingPartner =
                (L1PcInstance) partnerObject;
        if (tradingPartner == pc || tradingPartner.getTradeID() != pc.getId()
                || tradingPartner.isItemTransferBusy() || tradingPartner.isTradeResolving()) return;

        if (pc.getTradeOk()
                || tradingPartner.getTradeOk()) {

            SC_SystemMessage.send(pc, "올리기 불가능 : 한쪽이 완료를 누른 상태");

            SC_SystemMessage.send(tradingPartner, "올리기 불가능 : 한쪽이 완료를 누른 상태");

            return;
        }

        if (tradingPartner
                .getInventory()
                .checkAddItem(
                        item,
                        itemCount)
                != L1Inventory.OK) {

            SC_ServerMessage.send(tradingPartner, 270);

            SC_ServerMessage.send(pc, 271);

            return;
        }

        new L1Trade()
                .TradeAddItem(
                        pc,
                        itemObjectId,
                        itemCount);
    }
}