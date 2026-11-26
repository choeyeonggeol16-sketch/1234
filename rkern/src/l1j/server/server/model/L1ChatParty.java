package l1j.server.server.model;

import java.util.Arrays;
import java.util.Objects;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public class L1ChatParty {
    private static final L1PcInstance[] EMPTY = new L1PcInstance[0];
    // Invite replies for different parties can reach the same player from
    // different shards. Membership changes share one short admission lock.
    private static final Object MEMBERSHIP = new Object();
    private volatile L1PcInstance[] members = EMPTY;

    public void addMember(L1PcInstance pc) {
        addMember(null, pc);
    }

    void addMember(L1PcInstance inviter, L1PcInstance pc) {
        Objects.requireNonNull(pc);
        final L1PcInstance[] added;
        synchronized (MEMBERSHIP) {
            final L1PcInstance[] current = members;
            if ((inviter != null && (current.length == 0 || current[0] != inviter))
                    || pc.isLogoutStarted() || pc.getChatParty() != null
                    || (current.length >= Config.MAX_CHAT_PT && (current.length == 0 || !current[0].isGm()))) return;
            added = Arrays.copyOf(current, current.length + 1);
            added[current.length] = pc;
            pc.setChatParty(this);
            members = added;
        }
        CSharedBroadcast.sendWrittenArrayBestEffort(added, SC_ServerMessage.nativeWriter(424, pc.getName()));
    }

    public boolean isVacancy() { return getVacancy() > 0; }
    public int getVacancy() { return Config.MAX_CHAT_PT - members.length; }
    public boolean isMember(L1PcInstance pc) { return indexOf(members, pc) >= 0; }

    public L1PcInstance getLeader() {
        final L1PcInstance[] current = members;
        return current.length == 0 ? null : current[0];
    }

    public boolean isLeader(L1PcInstance pc) { return pc != null && getLeader() == pc; }

    public String getMembersNameList() {
        final StringBuilder names = new StringBuilder();
        for (L1PcInstance member : members) names.append(member.getName()).append(' ');
        return names.toString();
    }

    public void leaveMember(L1PcInstance pc) {
        final L1PcInstance[] recipients;
        final boolean leaderLeft;
        synchronized (MEMBERSHIP) {
            recipients = members;
            final int index = indexOf(recipients, pc);
            if (index < 0) return;
            leaderLeft = index == 0;
            if (leaderLeft || recipients.length == 2) clearMembers(recipients);
            else removeMember(recipients, index);
        }
        // A failed notification cannot prevent the remaining members from leaving.
        final var writer = leaderLeft ? SC_ServerMessage.nativeWriter(418)
                : SC_ServerMessage.nativeWriter(420, pc.getName());
        CSharedBroadcast.sendWrittenArrayBestEffort(recipients, writer);
    }

    public void kickMember(L1PcInstance pc) {
        synchronized (MEMBERSHIP) {
            final L1PcInstance[] current = members;
            final int index = indexOf(current, pc);
            if (index <= 0) return;
            if (current.length == 2) clearMembers(current);
            else removeMember(current, index);
        }
        CSharedBroadcast.sendWrittenArrayBestEffort(new L1PcInstance[] {pc}, SC_ServerMessage.nativeWriter(419));
    }

    private void clearMembers(L1PcInstance[] current) {
        for (L1PcInstance member : current) member.setChatParty(null);
        members = EMPTY;
    }

    private void removeMember(L1PcInstance[] current, int index) {
        final L1PcInstance[] remaining = new L1PcInstance[current.length - 1];
        System.arraycopy(current, 0, remaining, 0, index);
        System.arraycopy(current, index + 1, remaining, index, remaining.length - index);
        current[index].setChatParty(null);
        members = remaining;
    }

    private static int indexOf(L1PcInstance[] current, L1PcInstance pc) {
        for (int i = 0; i < current.length; i++) if (current[i] == pc) return i;
        return -1;
    }

    public L1PcInstance[] getMembers() { return members.clone(); }
    public int getNumOfMembers() { return members.length; }
}
