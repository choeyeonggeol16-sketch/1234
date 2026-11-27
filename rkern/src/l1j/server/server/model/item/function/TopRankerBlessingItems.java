package l1j.server.server.model.item.function;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;

/** Domain-specific item handler; original transaction ordering preserved. */
final class TopRankerBlessingItems {
    private static final Logger LOG = Logger.getLogger(TopRankerBlessingItems.class.getName());
    private TopRankerBlessingItems() {}

@SuppressWarnings("deprecation")
static void 정상의가호(L1PcInstance pc, L1ItemInstance useItem) {
    if (!pc.is_top_ranker()) {
        pc.sendPackets(String.format("현재 전체 랭킹 %d위 이므로 사용할 수 없습니다.", pc.getRankLevel()));
        pc.getInventory().DeleteItemAmount(useItem);
        return;
    }

    Calendar currentDate = CGameTime.getRealTimeCalendar();
    Timestamp lastUsed = pc.getLastTopBless();
        if (lastUsed == null || currentDate.getTimeInMillis() > lastUsed.getTime() + (1000 * 60 * 60 * 1)) {
        SC_SkillSound.send(pc, pc.getId(), 12536);
        SC_SkillSound.broadcast(pc, pc.getId(), 12536);
        pc.setSkillEffect(L1SkillId.TOP_RANKER, 600 * 1000);

        pc.setLastTopBless(new Timestamp(currentDate.getTimeInMillis()));
    } else {
        long i = (lastUsed.getTime() + (1000 * 60 * 60 * 1)) - currentDate.getTimeInMillis();
        Calendar cal = (Calendar) currentDate.clone();
        cal.setTimeInMillis(cal.getTimeInMillis() + i);
        SC_SystemMessage.send(pc, i / 60000 + "분 동안(" + cal.getTime().getHours() + ":" + cal.getTime().getMinutes() + " 까지)은 사용할 수 없습니다.");
    }
}

static void 정상의가호2(L1PcInstance pc, L1ItemInstance useItem) {
    final long now = CGameTime.getRealTimeCalendar().getTimeInMillis();
    final Timestamp lastUsed = pc.getLastTopBless();
    final long nextAllowed = lastUsed == null ? Long.MIN_VALUE : lastUsed.getTime() + 3600000L;
    if (now <= nextAllowed) {
        final long remaining = nextAllowed - now;
        final Calendar cal = CGameTime.getRealTimeCalendar();
        cal.setTimeInMillis(nextAllowed);
        SC_SystemMessage.send(pc, remaining / 60000 + "분 동안(" + cal.get(Calendar.HOUR_OF_DAY)
                + ":" + cal.get(Calendar.MINUTE) + " 까지)은 사용할 수 없습니다.");
        return;
    }

    UseDurableConsume(pc, useItem, () -> {
        // First publish committed gameplay state; optional sound packets must
        // not turn a successfully consumed blessing into a quarantined receipt.
        pc.setSkillEffect(L1SkillId.TOP_RANKER, 600000);
        pc.setLastTopBless(new Timestamp(now));
        try {
            pc.save();
        } catch (RuntimeException saveFailure) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Committed top-ranker blessing: character save admission failed", saveFailure);
        }
        // The dispatcher returns STOP: apply common delay exactly once,
        // after the durable debit has committed, not while its gate is held.
        ItemActionCompletion.finishCommittedConsumable(pc, useItem);
        try {
            SC_SkillSound.send(pc, pc.getId(), 12536);
        } catch (RuntimeException noticeFailure) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Committed top-ranker blessing: self sound failed", noticeFailure);
        }
        try {
            SC_SkillSound.broadcast(pc, pc.getId(), 12536);
        } catch (RuntimeException noticeFailure) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Committed top-ranker blessing: broadcast sound failed", noticeFailure);
        }
    });
}
}
