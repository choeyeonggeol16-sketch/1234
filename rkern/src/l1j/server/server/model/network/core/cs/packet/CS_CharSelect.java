package l1j.server.server.model.network.core.cs.packet;


import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.combat.arena.CombatObserver;
import l1j.server.server.model.character.combat.arena.loader.CombatLoadManager;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_Unknown2;
import l1j.server.server.model.network.utill.CConnectionState;
import l1j.server.server.model.world.instance.InstanceEnums.InstStatus;
import l1j.server.server.model.world.instance.InstanceSpace;
import l1j.server.server.model.world.instance.raid.RaidSpace;
import l1j.server.server.shared.observer.CopyMapObservable;
public final class CS_CharSelect {

    private static final Logger LOGGER = Logger.getLogger(CS_CharSelect.class.getName());

    private CS_CharSelect() {}

    public static void handle(CUserSocket client) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        if (pc == null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Disconnect Request from Account : " + client.getAccountName());
            }
            return;
        }

        /*
         * 데스페라도 상태에서는 리스 불가.
         */
        if (pc.hasSkillEffect(L1SkillId.DESPERADO)) {
            return;
        }

        /*
         * 전투장 이탈.
         */
        if (pc.is_combat_field()) {
            final CombatObserver observer = CombatLoadManager.getInstance().get_current_observer(pc.get_current_combat_id());

            if (observer != null) {
                observer.remove(pc);
            }
        }

        CopyMapObservable.getInstance().resetPosition(pc);


        RaidSpace.getInstance().getBackPc(pc);

        if (InstanceSpace.isInInstance(pc) && pc.getInstStatus() != InstStatus.INST_USERSTATUS_NONE) {
            return;
        }

        restartProcess(pc);
    }

    /**
     * 캐릭터 리스 / 선택창 이동.
     *
     * 다른 게임 로직에서도 직접 사용하므로 public static 유지.
     */
    public static void restartProcess(L1PcInstance pc) {

        if (pc == null) {
            return;
        }

        final CUserSocket client = pc.getNetConnection();
        if (client == null) return;

        final boolean privateStore = pc.isPrivateShop();
        if (privateStore && !pc.getInventory().tryBeginEquipmentChange()) {
            pc.sendPackets("상점 거래 처리 중입니다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        try {
            if (privateStore && !pc.isItemTransferSession(client, pc.getSessionStamp())) return;
            if (privateStore && pc.isPrivateReady()) {
                detachPrivateShop(pc, client);
                return;
            }
            if (privateStore && !User.RemovePrivateStoreList(pc)) return;

            final L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
            if (clan != null) {
                pc.setOnlineStatus(0);
                clan.updateClanMemberOnline(pc);
            }
            if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("Disconnect from: " + pc.getName());

            client.clearGameSessionReady();
            pc.logout();

            client.setActiveChar(null);
            client.setConnectionState(CConnectionState.AUTHENTICATED);
            SC_Unknown2.send(client, 1);

            if (client.getAccount() != null && client.getAccount().is_changed_slot()) {
                CS_CommonClick.sendCharAmountAndPacks(client);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "restartProcess 처리 중 예외 발생: " + pc.getName(), e);
        } finally {
            if (privateStore) pc.getInventory().endEquipmentChange();
        }

    }
    /** Explicit .무인상점 command: keep the merchant and inventory, retire only its session. */
    public static void RequestOfflinePrivateStore(L1PcInstance pc) {
        if (pc == null) return;
        final CUserSocket client = pc.getNetConnection();
        if (client == null || !pc.isItemTransferSession(client, pc.getSessionStamp())) return;
        if (!pc.isPrivateShop() || pc.getMapId() != 800) {
            pc.sendPackets("시장에서 개인상점을 연 뒤 사용할 수 있습니다.");
            return;
        }
        if (pc.isDead() || pc.isGhost() || pc.get_teleport() || pc.is무인상점()) return;
        if (!pc.getInventory().tryBeginEquipmentChange()) {
            pc.sendPackets("상점 거래 처리 중입니다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        try {
            detachPrivateShop(pc, client);
        } finally {
            pc.getInventory().endEquipmentChange();
        }
    }

    /** Caller owns the same reentrant inventory gate as private-store settlement. */
    private static void detachPrivateShop(L1PcInstance pc, CUserSocket client) {
        if (!pc.isItemTransferSession(client, pc.getSessionStamp()) || !pc.isPrivateShop()
                || pc.getMapId() != 800 || pc.isDead() || pc.isGhost() || pc.get_teleport()
                || pc.is무인상점() || pc.GetTrade() != null
                || l1j.server.server.model.inventory.CTrade.HasPendingTradeBlocker(pc)
                || l1j.server.server.storage.CacheD.getInstance().hasPendingOwnerApply(pc.getId())) return;
        for (L1PcInstance target : L1World.getInstance().getAllPlayers3()) {
            if (target.getId() != pc.getId()
                    && target.getAccountName().equalsIgnoreCase(pc.getAccountName())
                    && target.isPrivateShop()) {
                pc.sendPackets("\\f3이미 당신의 보조 캐릭터가 무인상점 상태입니다.");
                pc.sendPackets("\\f3상점을 종료하시길 바랍니다.");
                return;
            }
        }
        // A rejected attendance write must not detach the only owner of that snapshot.
        try {
            pc.savePrivateStoreAttendance();
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Offline shop attendance save rejected", failure);
            pc.sendPackets("무인상점 전환에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        final int online = pc.getOnlineStatus();
        pc.setOnlineStatus(0);
        try {
            pc.save();
            pc.saveInventory();
        } catch (RuntimeException failure) {
            pc.setOnlineStatus(online);
            LOGGER.log(Level.WARNING, "Offline shop save rejected", failure);
            pc.sendPackets("무인상점 전환에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        pc.set무인상점(true);
        pc.setPrivateReady(false);
        final L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
        try {
            if (clan != null) clan.updateClanMemberOnline(pc);
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Offline shop clan update failed", failure);
        }
        // Unregister while the original socket still identifies its CIO shard.
        pc.releasePrivateStoreSessionResources();
        try {
            client.clearGameSessionReady();
            pc.setNetConnection(null);
            client.setActiveChar(null);
            // Same state as ordinary character selection; EnterWorld accepts it.
            client.setConnectionState(CConnectionState.AUTHENTICATED);
            SC_Unknown2.send(client, 1);
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Offline shop session detach failed", failure);
            client.close();
        }
    }
}