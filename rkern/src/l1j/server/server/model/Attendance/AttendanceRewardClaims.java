package l1j.server.server.model.Attendance;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.AttendanceBonusType;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.AttendanceGroupType;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceDataExtend;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceDataGroup;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceState;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceTimeStatus;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ATTENDANCE_DATA_UPDATE_EXTEND;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ATTENDANCE_REWARD_ACK;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ATTENDANCE_USER_DATA_EXTEND;
import l1j.server.server.storage.CacheD;

public final class AttendanceRewardClaims {
    private static final Logger LOG = Logger.getLogger(AttendanceRewardClaims.class.getName());
    private static final String UNAVAILABLE = "이미 보상을 받았거나 수령할 수 없는 상태입니다.";
    private static final String RETRY = "출석 보상을 지급하지 못했습니다. 인벤토리 상태를 확인하고 다시 시도해 주세요.";

    private AttendanceRewardClaims() {}

    public static void claim(L1PcInstance pc, int index, AttendanceGroupType type, boolean sendAck) {
        if (pc == null || type == null || index < 0) return;
        final var data = pc.getAttendanceData();
        final String account = pc.getAccountName();
        final var reward = type.getBonus(index);
        if (data == null || account == null || account.isEmpty() || reward == null) return;

        // Read the mutable state while holding the same monitor as the timer.
        final boolean complete;
        synchronized (data) {
            final var slot = findSlot(findGroup(data, type), index);
            if (slot == null) return;
            complete = slot.get_state() == UserAttendanceState.COMPLETE;
        }
        if (!complete || pc.isItemTransferBusy()) {
            pc.sendPackets(UNAVAILABLE);
            return;
        }

        try (var tx = L1InventoryTransaction.begin(pc, CacheD.accountOrderKey(account))) {
            if (reward.get_bonuses().isEmpty()) {
                pc.sendPackets(RETRY);
                return;
            }
            for (var item : reward.get_bonuses()) {
                if (item == null || item.get_bonusType() != AttendanceBonusType.GiveItem
                        || tx.grant(item.get_itemId(), item.get_itemCount()) == null) {
                    pc.sendPackets(RETRY);
                    return;
                }
            }
            tx.requireTables("attendance_userinfo");
            tx.sql(con -> {
                if (!SC_ATTENDANCE_USER_DATA_EXTEND.stageRewardClaim(con, account, type, index))
                    throw new SQLException("Attendance reward is no longer claimable");
            });
            tx.onFailure(() -> pc.sendPackets(RETRY));
            tx.commitAfterInventoryRelease(() -> publish(pc, data, type, index), () -> {
                if (pc.getAttendanceData() != data) return;
                SC_ATTENDANCE_DATA_UPDATE_EXTEND.sendFields(pc, index + 1, UserAttendanceState.CLEAR, type);
                if (sendAck)
                    SC_ATTENDANCE_REWARD_ACK.sendFields(pc, index + 1, type.toInt(), UserAttendanceState.CLEAR);
            });
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "Attendance reward was not admitted for character " + pc.getId(), ex);
            pc.sendPackets(RETRY);
        }
    }

    private static void publish(L1PcInstance pc, SC_ATTENDANCE_USER_DATA_EXTEND data,
                                AttendanceGroupType type, int index) {
        synchronized (data) {
            if (pc.getAttendanceData() != data) return;
            final var group = findGroup(data, type);
            final var slot = findSlot(group, index);
            if (slot != null) markClaimed(group, slot);
        }
    }

    public static UserAttendanceDataGroup findGroup(SC_ATTENDANCE_USER_DATA_EXTEND data, AttendanceGroupType type) {
        if (data.get_groups() == null) return null;
        for (var group : data.get_groups()) {
            if (group.get_groupType() == type) return group;
        }
        return null;
    }

    public static UserAttendanceDataExtend findSlot(UserAttendanceDataGroup group, int index) {
        if (group == null || index < 0 || index >= group.get_groupData().size()) return null;
        final var slot = group.get_groupData().get(index);
        return slot.get_index() == index + 1 ? slot : null;
    }

    public static void markClaimed(UserAttendanceDataGroup group, UserAttendanceDataExtend slot) {
        slot.set_state(UserAttendanceState.CLEAR);
        reconcileGroupStatus(group);
    }

    public static void reconcileGroupStatus(UserAttendanceDataGroup group) {
        if (group.get_groupData().isEmpty()) return;
        for (var slot : group.get_groupData()) {
            if (slot.get_state() != UserAttendanceState.CLEAR) {
                if (group.get_resultCode() == UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR)
                    group.set_resultCode(UserAttendanceTimeStatus.ATTENDANCE_ALL_COMPLETE);
                return;
            }
        }
        group.set_resultCode(UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR);
    }
}
