package l1j.server.server.model.character.action;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.GameServer;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.CTrade;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.L1GroundInventory;
import l1j.server.server.model.CParty;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_AttackStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.raid.RaidObject;
import l1j.server.server.model.world.instance.raid.RaidSpace;
import l1j.server.server.monitor.Logger.ItemActionType;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.utils.MessageUtil;
import l1j.server.server.utils.TYUtil;

public final class CGetItemAction extends CNewAction {

    private static final long MAX_STACKABLE = 2_000_000_000L;

    private L1ItemInstance pickupItem;
    private CUserInventory pickupInven;
    private CInventory pickupGInven;
    private long effectiveAmount;

    CGetItemAction(CCreatureController controller) {
        super(controller);
    }

    @Override
    public boolean OnStart() {
        final L1PcInstance owner = controller.actor();
        if (owner == null
                || owner.isDead()
                || owner.isGhost()
                || owner.get_teleport()
                || owner.isInvisble()
                || owner.isInvisDelay()
                || owner.getOnlineStatus() != 1
                || owner.getNetConnection() == null
                || !owner.getNetConnection().isConnected()) {
            return false;
        }

        final int x = controller.i0();
        final int y = controller.i1();
        final int tId = controller.i2();
        final long requestedAmount = controller.l0();

        clearTemp();
        CTrade blockedTrade = null;

        try {
            /*
             * 클라이언트 amount는 신뢰하지 않는다.
             * 잘못된 amount 때문에 정상 ground item을 삭제하면 안 된다.
             */
            if (requestedAmount <= 0L) {
                GameServer.disconnectChar(owner);
                return true;
            }

            // OFF User::GetItem: abort the pickup as well as the trade.
            // Close outside the ground monitor; close may resync inventory.
            blockedTrade = owner.GetTrade();
            if (blockedTrade != null) return true;

            pickupGInven =
                    L1World.getInstance().findGroundInventory(x, y, owner.getMapId());

            if (pickupGInven == null) {
                return true;
            }

            final boolean picked;

            synchronized (pickupGInven) {
                if (!validatePickupLocked(
                        owner, x, y, tId, requestedAmount)) {
                    return true;
                }

                final CParty party = owner.getParty();
                final int itemId = pickupItem.getItemId();
                final int dropMobId = pickupItem.isDropMobId();

                final boolean partyAuto = party != null
                        && party.getLeader() != null
                        && party.getLeader().getPartyType() == 1
                        && party.getNumOfMembers() > 1;

                if (!partyAuto && pickupGInven instanceof L1GroundInventory ground
                        && L1GroundInventory.supportsReceiptPickup(pickupItem)) {
                    final L1ItemInstance pickedItem = pickupItem;
                    final int count = (int) effectiveAmount;
                    if (!ground.RequestPickItem(owner, pickedItem, count, () -> {
                        notifySinglePickup(owner, party, pickedItem);
                        finalizePickup(owner, tId, count, pickedItem, false);
                    })) blockedTrade = owner.GetTrade();
                    return true;
                }
                // Prevent remote trade attachment during legacy/party pickup.
                // Receipt pickup reserves its own gate.
                if (!pickupInven.tryBeginEquipmentChange()) {
                    blockedTrade = owner.GetTrade();
                    return true;
                }
                try {
                    blockedTrade = owner.GetTrade();
                    if (blockedTrade != null) return true;
                    if (partyAuto) {
                        picked = handlePartyDistribution(
                                owner, party, itemId, dropMobId, effectiveAmount);
                    } else {
                        picked = handleSinglePickup(
                                owner, party, effectiveAmount);
                    }
                } finally {
                    pickupInven.endEquipmentChange();
                }
            }

            /*
             * source transfer가 실제 성공한 경우에만 애니메이션/레이드 통지/
             * 전역 알림/로그를 발생시킨다.
             */
            if (!picked) {
                return true;
            }

            finalizePickup(owner, tId, effectiveAmount, pickupItem, true);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        } finally {
            clearTemp();
            if (blockedTrade != null) blockedTrade.RejectPickup(owner);
        }
    }

    @Override
    public boolean OnTick(long nowMs) {
        return true;
    }

    /**
     * 반드시 pickupGInven monitor를 잡은 상태에서 호출한다.
     */
    private boolean validatePickupLocked(
            L1PcInstance owner,
            int x,
            int y,
            int tId,
            long requestedAmount) {

        if (owner == null
                || owner.isDead()
                || owner.isGhost()
                || owner.get_teleport()
                || owner.isInvisble()
                || owner.isInvisDelay()
                || owner.getOnlineStatus() != 1
                || owner.getNetConnection() == null
                || !owner.getNetConnection().isConnected()) {
            return false;
        }

        if (requestedAmount <= 0L) {
            return false;
        }

        /*
         * stale L1ItemInstance 참조를 사용하지 않는다.
         * 현재 ground inventory가 소유한 objectId를 lock 안에서 다시 조회한다.
         */
        final L1Object obj = pickupGInven.GetByServerID(tId);
        if (!(obj instanceof L1ItemInstance) || obj.getId() != tId) {
            return false;
        }

        pickupItem = (L1ItemInstance) obj;
        pickupInven = owner.getInventory();

        if (pickupInven == null) {
            return false;
        }

        /* 서버에서 pickup 금지로 표시한 item은 direct packet으로도 획득 불가. */
        if (pickupItem.is_Not_PickUp_Item() || pickupItem.isGroundPickupPending()
                || pickupItem.isGroundDropUnsettled()) {
            return false;
        }

        /*
         * 패킷 좌표가 아니라 실제 ground object 좌표/맵을 진실값으로 사용한다.
         */
        if (pickupItem.getMapId() != owner.getMapId()
                || pickupItem.getX() != x
                || pickupItem.getY() != y) {
            return false;
        }

        final int itemX = pickupItem.getX();
        final int itemY = pickupItem.getY();

        if (Math.abs(itemX - owner.getX()) >= 2
                || Math.abs(itemY - owner.getY()) >= 2) {
            return false;
        }

        if (!(pickupInven.getWeight100() > 90 && owner.is_ranking_buff())) {
            if (pickupInven.getWeight100() > 90
                    || owner.getMaxWeight() <= pickupInven.GetCarryingWeight()) {
                owner.sendPackets("소지품이 너무 무겁습니다.");
                return false;
            }
        }

        if (!pickupItem.TryPickContributedItem(owner)) {
            SC_ServerMessage.send(owner, 623);
            return false;
        }

        final L1PcInstance itemOwner = pickupItem.getItemOwner();
        if (itemOwner != null && itemOwner.getId() != owner.getId()) {
            final CParty ownerParty = itemOwner.getParty();
            if (ownerParty == null || !ownerParty.isMember(owner)) {
                SC_ServerMessage.send(owner, 623);
                return false;
            }
        }

        final long itemCount = pickupItem.getCount();

        /*
         * requestedAmount 오류와 서버 내부 itemCount 오류를 분리한다.
         * 클라이언트가 0/음수를 보냈다는 이유로 정상 ground item을 지우지 않는다.
         */
        if (itemCount <= 0L || itemCount > MAX_STACKABLE) {
            GameServer.disconnectChar(owner);

            pickupGInven.Pop(pickupItem);
            L1World.getInstance().removeObject(pickupItem);
            return false;
        }

        effectiveAmount = Math.min(requestedAmount, itemCount);
        if (effectiveAmount <= 0L) {
            return false;
        }

        if (pickupItem.isStackable()) {
            if (pickupItem.getItemId() != L1ItemId.ADENA
                    && effectiveAmount > 1_000_000L) {
                owner.sendPackets("너무 많은 수량은 한 번에 획득할 수 없습니다.");
                return false;
            }

            final L1ItemInstance invItem =
                    pickupInven.GetFirstItemByClassID(pickupItem.getItemId());

            final long sum = invItem == null ? 0L : invItem.getCount();

            if (effectiveAmount + sum > MAX_STACKABLE) {
                owner.sendPackets("아이템 소지 한도를 초과합니다.");
                return false;
            }
        } else if (effectiveAmount != 1L) {
            return false;
        }

        return pickupInven.CheckAddable(
                pickupItem,
                (int) effectiveAmount) == CInventory.OK;
    }

    private boolean handlePartyDistribution(
            L1PcInstance owner,
            CParty party,
            int itemId,
            int dropMobId,
            long amount) {

        final List<L1PcInstance> members = new ArrayList<>();

        for (L1PcInstance m : party.getMembers()) {
            if (m == null || m.isDead()) {
                continue;
            }

            if (m.getMapId() == owner.getMapId()
                    && m.getLocation().getTileLineDistance(owner.getLocation())
                            <= Config.PARTY_LOOT_RANGE) {
                members.add(m);
            }
        }

        if (members.isEmpty()) {
            return handleSinglePickup(owner, party, amount);
        }

        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final String distTag = isPartyAuto(owner) ? "자동 분배" : "일반 분배";

        if (itemId == L1ItemId.ADENA) {
            final int total = (int) amount;
            final int n = members.size();
            final int each = total / n;
            final int rem = total % n;

            int remainderReceiverId = members.get(0).getId();

            for (L1PcInstance m : members) {
                if (m.getId() == owner.getId()) {
                    remainderReceiverId = owner.getId();
                    break;
                }
            }

            int movedTotal = 0;

            for (L1PcInstance m : members) {
                final int amt = each
                        + (m.getId() == remainderReceiverId ? rem : 0);

                if (amt <= 0) {
                    continue;
                }

                /*
                 * source inventory lock은 OnStart()가 이미 소유하고 있다.
                 * tradeItem()의 synchronized는 reentrant이므로 그대로 호출 가능하다.
                 */
                final L1ItemInstance moved =
                        pickupGInven.tradeItem(
                                pickupItem,
                                amt,
                                m.getInventory());

                if (moved == null) {
                    return false;
                }

                movedTotal += amt;

                if (m.RootMent1) {
                    final String msg = String.format(
                            "%s 획득 : %s(%s)",
                            pickupItem.getLogName(),
                            m.getName(),
                            distTag);

                    SC_SystemMessage.send(m, msg);
                }
            }

            return movedTotal == total;
        }

        if (dropMobId != 0 || itemId == 4100119) {
            final L1PcInstance luckyUser;

            if (Config.PARTY_LOOT_MODE == 2) {
                luckyUser = members.get(
                        party.NextLootIndex(members.size()));
            } else {
                luckyUser = members.get(
                        random.nextInt(members.size()));
            }

            final L1ItemInstance moved =
                    pickupGInven.tradeItem(
                            pickupItem,
                            (int) amount,
                            luckyUser.getInventory());

            if (moved == null) {
                return false;
            }

            final String msg = String.format(
                    "%s 획득 : %s(%s)",
                    pickupItem.getLogName(),
                    luckyUser.getName(),
                    distTag);

            party.broadcastRootMent(msg);
            return true;
        }

        return handleSinglePickup(owner, party, amount);
    }

    private static boolean isPartyAuto(L1PcInstance pc) {
        try {
            return pc != null
                    && pc.getParty() != null
                    && pc.getParty().getLeader() != null
                    && pc.getParty().getLeader().getPartyType() == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean handleSinglePickup(
            L1PcInstance owner,
            CParty party,
            long amount) {

        final L1ItemInstance moved =
                pickupGInven.tradeItem(
                        pickupItem,
                        (int) amount,
                        pickupInven);

        if (moved == null) {
            return false;
        }

        notifySinglePickup(owner, party, pickupItem);
        return true;
    }

    private static void notifySinglePickup(L1PcInstance owner, CParty party, L1ItemInstance item) {
        if (party == null) return;

        final String distTag =
                isPartyAuto(owner) ? "자동 분배" : "일반 분배";

        final String msg = String.format(
                "%s 획득 : %s(%s)",
                item.getLogName(),
                owner.getName(),
                distTag);

        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
       SC_SystemMessage.nativeWriter(msg),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance member : party.getMembers()) {
            if (member == null || member.getId() == owner.getId()) {
                continue;
            }

            if (member.RootMent1) {
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,member);
            }
        }
}

    }

    private static void finalizePickup(
            L1PcInstance owner,
            int tId,
            long amount, L1ItemInstance item, boolean clearGiveItem) {

        final int attackId = owner.getId();
        final int attackHeading = owner.getHeading();
        SC_AttackStatus.send(owner, attackId, attackHeading, tId, ActionCodes.ACTION_Pickup);

        if (!owner.isGmInvis()
                && !owner.isGhost()
                && !owner.isInvisble()) {
            SC_AttackStatus.broadcast(owner, attackId, attackHeading, tId, ActionCodes.ACTION_Pickup);
        }

        if (RaidSpace.getInstance().isInInstance(owner)) {
            final RaidObject obj =
                    RaidSpace.getInstance()
                            .getOpenObject(owner.getMapId());

            if (obj != null) {
                obj.notifyRaidPickupItem(
                        owner,
                        item,
                        (int) amount);
            }
        }

        checkGlobalAnnouncement(owner, item);

        owner.getLight().turnOnOffLight();
        if (clearGiveItem) item.setGiveItem(false);

        LoggerInstance.getInstance().addItemAction(
                ItemActionType.Pickup,
                owner,
                item,
                (int) amount);
    }

    private static void checkGlobalAnnouncement(L1PcInstance owner, L1ItemInstance item) {
        final int[] itemIds =
                TYUtil.GetIntTokenizeIntArray(Config.ItemListId);

        for (int id : itemIds) {
            if (item.getItemId() != id) {
                continue;
            }

            final String locName =
                    MapsTable.getInstance().getMapName(owner.getMapId());

            final String message =
                    (Config.SysMsg1 ? owner.getName() : "어느 아덴 용사")
                            + " 님께서 " + item.getName()
                            + "를(을) 획득하였습니다. [장소: "
                            + locName + "]";

            MessageUtil.toAllPlayers(message);
            MessageUtil.greenMessageToAllPlayers(message);
            break;
        }
    }

    private void clearTemp() {
        pickupItem = null;
        pickupInven = null;
        pickupGInven = null;
        effectiveAmount = 0L;
    }
}
