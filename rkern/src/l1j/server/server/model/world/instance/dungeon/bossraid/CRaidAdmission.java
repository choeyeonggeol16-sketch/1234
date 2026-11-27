package l1j.server.server.model.world.instance.dungeon.bossraid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.CParty;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.dungeon.ice.CIceCaveSpawn;

/** Reserve an encounter before consuming its leader's ticket, and hold the admitted identities across the notice. */
public final class CRaidAdmission {
    private static final Logger LOG = Logger.getLogger(CRaidAdmission.class.getName());
    private static final int TICKET_ID = 7005254;

    private CRaidAdmission() {}

    public static L1ItemInstance findTicket(L1PcInstance leader) {
        final L1ItemInstance ticket = leader.getInventory().GetFirstItemByClassID(TICKET_ID);
        if (ticket == null) SC_SystemMessage.send(leader, "보스 레이드 입장권(1)이 부족합니다.");
        return ticket;
    }

    public static void launch(CInstantZone<?> zone, L1PcInstance leader,
            L1ItemInstance ticket, Runnable enter, Runnable cleanup) {
        final CompletableFuture<Boolean> paid = new CompletableFuture<>();
        boolean submitted = false;
        try {
            final var future = zone.start(() -> {
                try {
                    if (paid.get()) enter.run();
                } catch (InterruptedException stopping) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException failure) {
                    throw new IllegalStateException("Raid ticket admission failed", failure.getCause());
                }
            }, cleanup);
            if (future.isCancelled()) {
                SC_SystemMessage.send(leader, "던전 입장을 시작하지 못했습니다. 다시 시도해 주세요.");
                return;
            }

            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(leader, ticket.getId())) {
                if (!tx.debit(ticket, 1)) {
                    SC_SystemMessage.send(leader, "보스 레이드 입장권(1)이 부족합니다.");
                    return;
                }
                tx.onDurableCommit(() -> paid.complete(true));
                tx.onTerminalFailure(() -> paid.complete(false));
                tx.onFailure(() -> SC_SystemMessage.send(leader, "입장권 저장에 실패했습니다. 다시 시도해 주세요."));
                tx.commit(null);
                submitted = true;
            }
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Raid admission deferred", failure);
            SC_SystemMessage.send(leader, "던전 입장을 시작하지 못했습니다. 다시 시도해 주세요.");
        } finally {
            // If the receipt was prepared successfully, its terminal callbacks
            // release the script. Otherwise the script must retire this zone.
            if (!submitted) paid.complete(false);
        }
    }

    public static Runnable travel(CParty party, L1PcInstance leader, int mapId, int x, int y,
            boolean clanOnly, Runnable play) {
        final int clanId = leader.getClanid();
        final int clanMemberId = leader.getClanMemberId();
        final List<Participant> admitted = new ArrayList<>();
        for (L1PcInstance member : party.getList()) {
            final CUserSocket session = member.getNetConnection();
            if (session != null) admitted.add(new Participant(member, session, member.getSessionStamp()));
        }
        return () -> {
            final String notice = clanOnly ? "3초 후에 클랜레이드 맵으로 이동합니다."
                    : "3초 후에 보스레이드 맵으로 이동합니다.";
            final String refused = "같은 혈원이 아니기에 입장에서 제외 됩니다.";
            try (var accepted = new CAsyncSendConstBuffer(
                         SC_SystemMessage.nativeWriter(notice), Integer.MIN_VALUE, Integer.MAX_VALUE);
                 var rejected = clanOnly ? new CAsyncSendConstBuffer(
                         SC_SystemMessage.nativeWriter(refused), Integer.MIN_VALUE, Integer.MAX_VALUE) : null) {
                for (Participant member : admitted) {
                    if (!member.isCurrent(party)) continue;
                    CSharedBroadcast.sendRecipientBestEffort(
                            !clanOnly || member.pc.getClanid() == clanId ? accepted : rejected, member.pc);
                }
            }
            if (!CIceCaveSpawn.pause(3000L)) return;

            boolean entered = false;
            for (Participant member : admitted) {
                if (!member.isCurrent(party)) continue;
                if (clanOnly && (member.pc.getClanid() != clanId || member.pc.getClanMemberId() != clanMemberId)) {
                    SC_SystemMessage.send(member.pc, refused);
                    continue;
                }
                member.pc.start_teleport(x, y, mapId, 5, 169, false, false);
                entered = true;
            }
            if (entered && CIceCaveSpawn.pause(2000L)) play.run();
        };
    }

    private record Participant(L1PcInstance pc, CUserSocket session, int stamp) {
        boolean isCurrent(CParty party) {
            return pc.getParty() == party && party.isMember(pc)
                    && pc.isItemTransferSession(session, stamp) && session.ownsReadyGameSession();
        }
    }
}
