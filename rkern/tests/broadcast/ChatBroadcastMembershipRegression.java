package l1j.server.server.model;

import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import l1j.server.Config;
import l1j.server.server.datatables.SpamTable;
import l1j.server.server.model.Instance.L1PcInstance;

public final class ChatBroadcastMembershipRegression {
    public static void main(String[] args) throws Exception {
        Config.MAX_CHAT_PT = 4;
        simultaneousInvitesKeepOneMembership();
        readOnlyMuteChecksDoNotRetainEntries();
        System.out.println("PASS chat broadcast membership and mute cache regression");
    }

    private static void simultaneousInvitesKeepOneMembership() throws Exception {
        final var firstLeader = player(1);
        final var secondLeader = player(2);
        final var invited = player(3);
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int attempt = 0; attempt < 128; attempt++) {
                final var first = new L1ChatParty();
                final var second = new L1ChatParty();
                first.addMember(firstLeader);
                second.addMember(secondLeader);
                final var start = new CyclicBarrier(2);
                final var firstInvite = workers.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    first.addMember(firstLeader, invited);
                    return null;
                });
                final var secondInvite = workers.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    second.addMember(secondLeader, invited);
                    return null;
                });
                firstInvite.get(5, TimeUnit.SECONDS);
                secondInvite.get(5, TimeUnit.SECONDS);
                require(first.isMember(invited) != second.isMember(invited), "invite belongs to exactly one party");
                require(invited.getChatParty() == (first.isMember(invited) ? first : second), "player points to the winner");
                first.leaveMember(firstLeader);
                second.leaveMember(secondLeader);
                require(firstLeader.getChatParty() == null && secondLeader.getChatParty() == null
                        && invited.getChatParty() == null, "all references cleared on leader departure");
                first.addMember(firstLeader, invited);
                require(invited.getChatParty() == null, "an expired invitation cannot revive a dissolved party");
            }
        }
    }

    private static void readOnlyMuteChecksDoNotRetainEntries() throws Exception {
        final var table = SpamTable.getInstance();
        final var field = SpamTable.class.getDeclaredField("_excludes");
        field.setAccessible(true);
        final var entries = (Map<?, ?>) field.get(table);
        final int previousSize = entries.size();
        for (int id = 10_000; id < 15_000; id++) {
            require(!table.excludesChat(id, "sender"), "unknown recipient has no mute entries");
        }
        require(entries.size() == previousSize, "audience checks retain no empty cache entries");
        final var list = table.getExcludeTable(1);
        list.add(0, "sender");
        require(table.excludesChat(1, "sender"), "chat mute is observed");
        list.remove(0, "sender");
        require(!table.excludesChat(1, "sender"), "removed chat mute stops filtering");
    }

    private static L1PcInstance player(int id) {
        final var player = new L1PcInstance();
        player.setId(id);
        player.setName("chat-player-" + id);
        return player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
