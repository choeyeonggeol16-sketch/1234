package l1j.server.server.model.network.core.cs.packet;

import l1j.server.Config;
import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NoDropItem;
import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.monitor.Logger.ItemActionType;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

public final class CS_DropItem {

    private static final int ENTRY_SIZE = 12;

    /*
     * PC 인벤토리 최대 슬롯 수보다 큰 batch drop은 정상 클라이언트에서
     * 나올 이유가 없다. 비정상 length로 CPU를 태우는 것도 여기서 차단한다.
     */
    private static final int MAX_BATCH_ENTRIES = 180;

    private static final long STACKABLE_DROP_DELAY_MS = 2000L;

    private CS_DropItem() {
    }

    public static void handle(
            CIOClient client,
            int length,
            int x,
            int y,
            int objectId,
            int count,
            byte[] entries) {

        if (client == null || length <= 0 || length > MAX_BATCH_ENTRIES) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        /*
         * 비정상 상태라고 접속을 끊지 않는다.
         * 해당 DROP 요청만 조용히 폐기한다.
         */
        if (pc == null
                || pc.isDead()
                || pc.isGhost()
                || pc.get_teleport()
                || pc.getOnlineStatus() != 1) {
            return;
        }

        if (length == 1) {
            dropOne(pc, x, y, objectId, count);
            return;
        }

        if (entries == null) {
            return;
        }

        /*
         * required = length * ENTRY_SIZE 식은 int overflow가 가능하다.
         * 나눗셈 기준으로 검사해서 overflow 자체를 없앤다.
         */
        if (length > entries.length / ENTRY_SIZE) {
            return;
        }

        int offset = 0;

        for (int i = 0; i < length; i++) {
            final int dropX = readH(entries, offset);
            final int dropY = readH(entries, offset + 2);
            final int dropObjectId = readD(entries, offset + 4);
            final int dropCount = readD(entries, offset + 8);

            offset += ENTRY_SIZE;

            /*
             * 한 entry라도 유효하지 않으면 남은 batch는 더 이상 처리하지 않는다.
             * 이미 성공한 이전 entry를 되돌리는 트랜잭션 구조는 현재 Inventory
             * 구조에 없으므로, 최소한 이후 비정상 요청의 연쇄 실행은 차단한다.
             */
            if (!dropOne(pc, dropX, dropY, dropObjectId, dropCount)) {
                return;
            }
        }
    }

    /**
     * true  = 다음 drop entry 처리를 계속 진행
     * false = 비정상/거절 조건. 현재 C_DROP batch 처리를 즉시 종료
     */
    private static boolean dropOne(
            L1PcInstance pc,
            int x,
            int y,
            int objectId,
            int count) {

        if (pc == null
                || pc.isDead()
                || pc.isGhost()
                || pc.get_teleport()
                || pc.getOnlineStatus() != 1) {
            return false;
        }

        if (objectId <= 0 || count <= 0 || count > L1Inventory.MAX_AMOUNT) {
            return false;
        }

        /*
         * 클라이언트가 지정한 좌표를 그대로 믿지 않는다.
         *
         * 1) 현재 서버 map의 실제 좌표 범위 안
         * 2) 현재 PC 기준 인접 1칸 이내
         * 3) 벽/지형을 관통해서 버리지 못하도록 서버 시야 검사
         */
        if (!pc.getMap().isInMap(x, y)) {
            return false;
        }

        final int pcX = pc.getX();
        final int pcY = pc.getY();

        if (Math.abs(x - pcX) > 1 || Math.abs(y - pcY) > 1) {
            return false;
        }

        if (!pc.glanceCheck(x, y)) {
            return false;
        }

        final L1Inventory inventory = pc.getInventory();
        final L1ItemInstance item = inventory.getItem(objectId);

        /*
         * objectId는 반드시 현재 PC inventory가 실제로 소유하고 있어야 한다.
         */
        if (item == null || item.getId() != objectId) {
            return false;
        }

        final long now = System.currentTimeMillis();

        if (item.getItemdelay3() >= now) {
            return true;
        }

        final L1Item template = item.getItem();

        if (template == null) {
            return false;
        }

        final int itemId = template.getItemId();

        /*
         * 실제 보유 수량과 요청 수량은 정확히 서버 상태 기준으로 검사한다.
         * 초과 요청을 서버가 임의로 보정하지 않는다.
         */
        if (item.getCount() <= 0 || count > item.getCount()) {
            return false;
        }

        if (!item.isStackable() && count != 1) {
            return false;
        }

        if ((!pc.isGm() && !template.isTradable())
                || itemId == L1ItemId.HIGH_CHARACTER_TRADE
                || itemId == L1ItemId.LOW_CHARACTER_TRADE) {

            SC_ServerMessage.send(pc, 210, template.getName());
            return false;
        }

        final int itemType = template.getType2();

        if (Config.ItemDrops && itemType != 0) {
            SC_SystemMessage.send(pc, "\\aJ경고 : 현재 서버특성상 잡템만 버릴 수 있습니다.");
            return false;
        }

        if (!pc.isGm() && NoDropItem.getInstance().isNoDropItem(itemId)) {
            final String itemName =
                    ItemTable.getInstance().findItemIdByName(itemId);

            SC_SystemMessage.send(pc, "서버 특성상 ["
                            + (itemName != null ? itemName : "아이템")
                            + "] 아이템(은)는 버릴수 없습니다.");
            return false;
        }

        /*
         * 시장 / 서버 지정 드랍 금지 맵.
         */
        final int mapId = pc.getMapId();

        if (mapId == 350
                || mapId == 340
                || mapId == 370
                || mapId == 800) {

            SC_SystemMessage.send(pc, "시장에서는 아이템을 버릴수 없습니다.");
            return false;
        }

        if (hasActiveChangeItem(pc)) {
            pc.sendPackets(
                    "변경권을 사용중이거나, 취소하셨으므로 리스후에 해당 행동이 가능합니다.");
            return false;
        }

        if (itemId == 80500) {
            SC_SystemMessage.send(pc, "훈련소 열쇠는 드랍이 불가능 합니다.");
            return false;
        }

        if (!pc.isGm() && item.getBless() >= 128) {
            SC_ServerMessage.send(pc, 210, template.getName());
            return false;
        }

        if (!MJCompanionInstanceCache.is_companion_oblivion(item.getId())) {
            SC_ServerMessage.send(pc, 210, template.getName());
            return false;
        }

        if (isPetItem(pc, item.getId())) {
            SC_ServerMessage.send(pc, 210, template.getName());
            return false;
        }

        if (isDollItem(pc, item.getId())) {
            SC_ServerMessage.send(pc, 210, template.getName());
            return false;
        }

        if (item.isEquipped()) {
            SC_ServerMessage.send(pc, 125);
            return false;
        }

        /*
         * 모든 일반 검증이 끝난 뒤, transfer 직전에 exact ownership과
         * 수량을 다시 확인한다. 앞의 검증 도중 다른 경로에서 아이템 상태가
         * 바뀌었더라도 stale L1ItemInstance로 드랍하지 못한다.
         */
        final L1ItemInstance current = inventory.getItem(objectId);

        if (current != item
                || current.getCount() <= 0
                || count > current.getCount()
                || current.isEquipped()) {
            return false;
        }

        final L1GroundInventory ground =
                L1World.getInstance().getInventory(
                        x,
                        y,
                        (short) mapId);

        if (ground == null) {
            return false;
        }

        /*
         * 실제 소유권 이전.
         *
         * 강화된 L1Inventory.tradeItem()이 내부에서 exact object ownership을
         * 다시 검증하고, 실패하면 null을 반환한다.
         */
        final L1ItemInstance moved =
                inventory.tradeItem(
                        current,
                        count,
                        ground);

        if (moved == null) {
            return false;
        }

        /*
         * 여기부터는 transfer 성공 후처리만 한다.
         * 실패 요청이 source item의 giveItem/delay/log 상태를 오염시키지 않는다.
         */
        if (!pc.isGm()) {
            moved.setGiveItem(true);
        }

        if (current.isStackable()) {
            final long nextDropTime = now + STACKABLE_DROP_DELAY_MS;

            /*
             * ground로 이동된 실제 아이템에도 delay를 남긴다.
             */
            moved.setItemdelay3(nextDropTime);

            /*
             * 부분 드랍이면 source stack이 inventory에 그대로 남아 있으므로
             * 남은 stack에도 기존 의미의 재드랍 delay를 적용한다.
             */
            final L1ItemInstance remain = inventory.getItem(objectId);

            if (remain == current) {
                remain.setItemdelay3(nextDropTime);
            }
        }

        pc.getLight().turnOnOffLight();

        LoggerInstance.getInstance().addItemAction(
                ItemActionType.Drop,
                pc,
                moved,
                count);

        return true;
    }

    private static boolean isPetItem(
            L1PcInstance pc,
            int objectId) {

        for (Object value : pc.getPetList().values()) {
            if (!(value instanceof L1PetInstance)) {
                continue;
            }

            final L1PetInstance pet = (L1PetInstance) value;

            if (pet.getItemObjId() == objectId) {
                return true;
            }
        }

        return false;
    }

    private static boolean isDollItem(
            L1PcInstance pc,
            int objectId) {

        for (Object value : pc.getDollList().values()) {
            if (!(value instanceof L1DollInstance)) {
                continue;
            }

            final L1DollInstance doll = (L1DollInstance) value;

            if (doll.getItemObjId() == objectId) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasActiveChangeItem(L1PcInstance pc) {
        return pc.getIsChangeItem()
                || pc.getIsChangeItem1()
                || pc.getIsChangeItem2()
                || pc.getIsChangeItem3()
                || pc.getIsChangeItem4()
                || pc.getIsChangeItem5()
                || pc.getIsChangeItem6()
                || pc.getIsChangeItem7()
                || pc.getIsChangeItem8()
                || pc.getIsChangeItem9()
                || pc.getIsChangeItem10()
                || pc.getIsChangeItem11()
                || pc.getIsChangeItem12()
                || pc.getIsChangeItem13()
                || pc.getIsChangeItem14()
                || pc.getIsChangeItem15()
                || pc.getIsChangeItem16()
                || pc.getIsChangeItem17()
                || pc.getIsChangeItem18()
                || pc.getIsChangeItem19()
                || pc.getIsChangeItem20();
    }

    // =========================================================
    // RAW WIRE
    // =========================================================

    private static int readH(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readD(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}
