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
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.character.skill.L1SkillId.ABSOLUTE_BARRIER;
import static l1j.server.server.model.item.function.ItemUseDurable.UseDurableConsume;

/** Compatibility entry and shared legacy effect helpers. */
public final class ItemUseCreature {
    private ItemUseCreature() {}

    static final Logger ITEM_USE2_LOG=Logger.getLogger(CItemUse.class.getName());
    static final Random _random=new Random(System.nanoTime());

    public static void UseItem(CIOClient client, L1PcInstance pc, L1ItemInstance item,
            int itemObjid, int targetId, int x, int y, String name, int skillId) {
        ItemUseService.use(client,pc,item,itemObjid,targetId,x,y,name,skillId);
    }

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

public static boolean createNewItem(L1PcInstance pc, int item_id, int count) {
    L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
    if (item != null) {
        item.setCount(count);
        if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
            pc.getInventory().storeItem(item);
        } else {
            SC_ServerMessage.send(pc, 82);
            return false;
        }
        SC_ServerMessage.send(pc, 403, item.getLogName());
        return true;
    } else {
        return false;
    }
}

static boolean createGMItem(L1PcInstance pc, int item_id, int count) {
    L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
    if (item != null) {
        item.setCount(count);
        item.setIdentified(true);
        if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
            pc.getInventory().storeItem(item);
        } else {
            SC_ServerMessage.send(pc, 82);
            return false;
        }
        SC_ServerMessage.send(pc, 403, item.getLogName());
        return true;
    } else {
        return false;
    }
}

static boolean createNewItem2(L1PcInstance pc, int item_id, int count, int EnchantLevel) {
    L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
    if (item != null) {
        item.setCount(count);
        item.setEnchantLevel(EnchantLevel);
        item.setIdentified(true);
        if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
            pc.getInventory().storeItem(item);
        } else {
            SC_ServerMessage.send(pc, 82);

            return false;
        }
        SC_ServerMessage.send(pc, 403, item.getLogName());

        return true;
    } else {
        return false;
    }
}

public static void cancelAbsoluteBarrier(L1PcInstance pc) {
    if (pc.hasSkillEffect(ABSOLUTE_BARRIER)) {
        pc.removeSkillEffect(ABSOLUTE_BARRIER);
    }
}

static void useFieldObjectRemovalWand(L1PcInstance pc, int targetId, L1ItemInstance item) {
    SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
    SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
    L1Object target = L1World.getInstance().findObject(targetId);
    if (target != null && target instanceof L1NpcInstance && !(target instanceof L1DollInstance)) {
        L1NpcInstance npc = (L1NpcInstance) target;
        NpcSpawnTable.getInstance().removeSpawn(npc);
        npc.setRespawn(false);
        npc.startTimedDelete(1 * 1);
        SC_SystemMessage.send(pc, "GM: 해당 엔피씨가 삭제 되었습니다.");
        pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
    }
}

static void use_bug_down_wand(L1PcInstance pc, int target_id, L1ItemInstance item){
    if(!pc.isGm())
        return;

    pc.sendPackets(BugRaceController.getInstance().down_bug(target_id) ? "선택한 버그베어를 넘어뜨렸습니다." : "넘어뜨릴 버그베어를 찾을 수 없습니다.");
}
}
