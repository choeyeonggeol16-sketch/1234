package l1j.server.server.packets.Protocol.MainServer.Proto;

import l1j.server.server.model.Attendance.AttendanceRewardClaims;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.AttendanceGroupType;

public final class CS_ATTENDANCE_REWARD_REQ {
    private CS_ATTENDANCE_REWARD_REQ() {}

    public static void handleRequest(CUserSocket client, ProtoReader reader) {
        handleRequest(client, reader, true);
    }

    static void handleRequest(CUserSocket client, ProtoReader reader, boolean sendAck) {
        if (client == null) return;

        int rewardIndex = -1;
        AttendanceGroupType groupType = null;
        while (!reader.isAtEnd()) {
            final int tag = reader.tag();
            final int wireType = tag & 7;
            switch (tag >>> 3) {
                case 1 -> {
                    if (wireType != 0 || rewardIndex >= 0) return;
                    final long index = reader.varint64();
                    if (index <= 0 || index > Integer.MAX_VALUE) return;
                    rewardIndex = (int) index - 1;
                }
                case 2 -> {
                    if (wireType != 0 || groupType != null) return;
                    final long group = reader.varint64();
                    if (group < 0 || group > Integer.MAX_VALUE) return;
                    groupType = AttendanceGroupType.fromInt((int) group);
                    if (groupType == null) return;
                }
                default -> reader.skip(wireType);
            }
        }
        if (rewardIndex >= 0 && groupType != null)
            AttendanceRewardClaims.claim(client.getActiveChar(), rewardIndex, groupType, sendAck);
    }
}
