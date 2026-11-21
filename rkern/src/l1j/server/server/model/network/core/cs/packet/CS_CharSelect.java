package l1j.server.server.model.network.core.cs.packet;


import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.UserCommands;
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

        final L1Clan clan = L1World.getInstance().getClan(pc.getClanid());

        if (clan != null) {
            pc.setOnlineStatus(0);
            clan.updateClanMemberOnline(pc);
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Disconnect from: " + pc.getName());
        }

        final CUserSocket client = pc.getNetConnection();

        if (client == null) {
            return;
        }

        try {
            if (pc.isPrivateShop() && pc.isPrivateReady()) {
                UserCommands.privateShop(pc);
                return;
            }

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
        }

    }
}