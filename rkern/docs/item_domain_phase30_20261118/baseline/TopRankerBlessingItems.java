package l1j.server.server.model.item.function;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Random;
import java.util.logging.Logger;
import l1j.server.server.ActionCodes;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.character.skill.L1SkillId.ABSOLUTE_BARRIER;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;

/** Domain-specific item handler; original transaction ordering preserved. */
final class TopRankerBlessingItems {
    private TopRankerBlessingItems() {}

@SuppressWarnings("deprecation")
static void 정상의가호(L1PcInstance pc, L1ItemInstance useItem) {
    if (!pc.is_top_ranker()) {
        pc.sendPackets(String.format("현재 전체 랭킹 %d위 이므로 사용할 수 없습니다.", pc.getRankLevel()));
        pc.getInventory().removeItem(useItem);
        return;
    }

    Calendar currentDate = RealTimeClock.getInstance().getRealTimeCalendar();
    Timestamp lastUsed = pc.getLastTopBless();
        if (lastUsed == null || currentDate.getTimeInMillis() > lastUsed.getTime() + (1000 * 60 * 60 * 1)) {
        SC_SkillSound.send(pc, pc.getId(), 12536);
        SC_SkillSound.broadcast(pc, pc.getId(), 12536);
        if (pc.hasSkillEffect(L1SkillId.TOP_RANKER)) {
            pc.setSkillEffect(L1SkillId.TOP_RANKER, 600 * 1000);
        } else {
            pc.setSkillEffect(L1SkillId.TOP_RANKER, 600 * 1000);
        }

        pc.setLastTopBless(new Timestamp(currentDate.getTimeInMillis()));
    } else {
        long i = (lastUsed.getTime() + (1000 * 60 * 60 * 1)) - currentDate.getTimeInMillis();
        Calendar cal = (Calendar) currentDate.clone();
        cal.setTimeInMillis(cal.getTimeInMillis() + i);
        SC_SystemMessage.send(pc, i / 60000 + "분 동안(" + cal.getTime().getHours() + ":" + cal.getTime().getMinutes() + " 까지)은 사용할 수 없습니다.");
    }
}

static void 정상의가호2(L1PcInstance pc, L1ItemInstance useItem) {
    final long now = RealTimeClock.getInstance().getRealTimeCalendar().getTimeInMillis();
    final Timestamp lastUsed = pc.getLastTopBless();
    final long nextAllowed = lastUsed == null ? Long.MIN_VALUE : lastUsed.getTime() + 3600000L;
    if (now <= nextAllowed) {
        final long remaining = nextAllowed - now;
        final Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
        cal.setTimeInMillis(nextAllowed);
        SC_SystemMessage.send(pc, remaining / 60000 + "분 동안(" + cal.get(Calendar.HOUR_OF_DAY)
                + ":" + cal.get(Calendar.MINUTE) + " 까지)은 사용할 수 없습니다.");
        return;
    }

    UseDurableConsume(pc, useItem, () -> {
        SC_SkillSound.send(pc, pc.getId(), 12536);
        SC_SkillSound.broadcast(pc, pc.getId(), 12536);
        pc.setSkillEffect(L1SkillId.TOP_RANKER, 600000);
        pc.setLastTopBless(new Timestamp(now));
    });
}
}
