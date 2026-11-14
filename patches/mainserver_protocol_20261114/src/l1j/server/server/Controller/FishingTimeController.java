package l1j.server.server.Controller;

import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.MJExpRevision.MJEFishingType;
import l1j.server.MJExpRevision.MJFishingExpInfo;
import l1j.server.server.GameServerSetting;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.item.Exp.CalcExp;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.packets.Protocol.Packet.SC_FISHING_NOT;
import l1j.server.server.packets.Protocol.Packet.SC_REST_EXP_INFO_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 낚시 컨트롤러
 */
public final class FishingTimeController {

    public static final int SLEEP_TIME = 1000;

    private static final Logger _log =
            Logger.getLogger(FishingTimeController.class.getName());

    private static final long TICK_MS = 1000L;
    private static final long FISHING_GRACE_MS = 1000L;

    private static final byte EXP_NONE = 0;
    private static final byte EXP_GROWN_UP = 1;
    private static final byte EXP_HIGH_GROWN_UP = 2;
    private static final byte EXP_ANCIENT_SILVER = 3;
    private static final byte EXP_ANCIENT_GOLD = 4;

    private static final int DROP_MSG_15564 = 0;
    private static final int DROP_MSG_15565 = 1;
    private static final int DROP_MSG_15566 = 2;
    private static final int DROP_MSG_RED = 3;
    private static final int DROP_MSG_17523 = 4;
    private static final int DROP_MSG_20462 = 5;
    private static final int DROP_MSG_15815 = 6;
    private static final int DROP_MSG_17521 = 7;
    private static final int DROP_MSG_17522 = 8;
    private static final int DROP_MSG_17524 = 9;

    private static final String[] DROP_MESSAGES = {
            "$15564",
            "$15565",
            "$15566",
            "레드 베리아나",
            "$17523",
            "$20462",
            "$15815",
            "$17521",
            "$17522",
            "$17524"
    };

    private static final int[] DROP_ANCIENT_SILVER = {
            6000, 41297, DROP_MSG_15565,
            8000, 41296, DROP_MSG_15564,
            8040, 49092, DROP_MSG_RED,
            8070, 41298, DROP_MSG_15566,
            8350, 41296, DROP_MSG_15564,
            8351, 41298, DROP_MSG_15566,
            8352, 41300, DROP_MSG_17523
    };

    private static final int[] DROP_ANCIENT_GOLD = {
            6000, 41297, DROP_MSG_15565,
            8000, 41296, DROP_MSG_15564,
            8020, 49092, DROP_MSG_RED,
            8050, 41298, DROP_MSG_15566,
            8350, 41296, DROP_MSG_15564,
            8351, 49092, DROP_MSG_RED,
            8352, 41300, DROP_MSG_17523
    };

    private static final int[] DROP_GROW = {
            6000, 41296, DROP_MSG_15564,
            8000, 41297, DROP_MSG_15565,
            8020, 41298, DROP_MSG_15566,
            10001, 41296, DROP_MSG_15564
    };

    private static final int[] DROP_HIGH_GROW = {
            6000, 41296, DROP_MSG_15564,
            8000, 41297, DROP_MSG_15565,
            8020, 41298, DROP_MSG_15566,
            8350, 49093, DROP_MSG_20462,
            8370, 41298, DROP_MSG_15566,
            10001, 41296, DROP_MSG_15564
    };

    private static final int[] DROP_FROG_OR_REEL_HIGH = {
            6000, 41296, DROP_MSG_15564,
            8000, 41297, DROP_MSG_15565,
            8020, 41298, DROP_MSG_15566,
            8350, 41301, DROP_MSG_15815,
            8351, 41299, DROP_MSG_17521,
            8352, 41300, DROP_MSG_17523
    };

    private static final int[] DROP_REEL_SILVER = {
            4000, 41296, DROP_MSG_15564,
            8000, 41297, DROP_MSG_15565,
            8040, 41298, DROP_MSG_15566,
            8350, 41301, DROP_MSG_15815,
            8352, 41299, DROP_MSG_17521,
            8353, 41303, DROP_MSG_17522
    };

    private static final int[] DROP_REEL_GOLD = {
            3500, 41297, DROP_MSG_15565,
            8000, 41296, DROP_MSG_15564,
            8050, 41298, DROP_MSG_15566,
            8350, 41301, DROP_MSG_15815,
            8352, 41300, DROP_MSG_17523,
            8354, 41304, DROP_MSG_17524
    };

    private static final int[] DROP_NORMAL = {
            4000, 41296, DROP_MSG_15564,
            8000, 41297, DROP_MSG_15565,
            8010, 41298, DROP_MSG_15566,
            8350, 41301, DROP_MSG_15815
    };

    private static final class Holder {
        private static final FishingTimeController INSTANCE =
                new FishingTimeController();
    }

    public static FishingTimeController getInstance() {
        return Holder.INSTANCE;
    }

    private FishingTimeController() {
    }


    // ---------------------------------------------------------------------
    // membership -> CIO owner timer
    // ---------------------------------------------------------------------

    public void addMember(L1PcInstance pc) {
        if (pc == null || !pc.isFishing()) {
            return;
        }
        CIOThreadCallback.getInstance().startFishing(pc);
    }

    /**
     * 별도 member map을 유지하지 않는다.
     * 낚시 상태의 source of truth는 L1PcInstance 자체다.
     */
    public boolean isMember(L1PcInstance pc) {
        return pc != null && pc.isFishing() && pc._fishingRod != null;
    }

    public void removeMember(L1PcInstance pc) {
        if (pc == null) {
            return;
        }
        CIOThreadCallback.getInstance().stopFishing(pc);
    }

    /**
     * CIOThread의 FishingTask에서 호출한다.
     *
     * @return 다음 실행까지 delay(ms), 종료면 -1
     */
    public long processFromCio(final L1PcInstance pc, final long nowMs) {
        if (pc == null) {
            return -1L;
        }

        try {
            return processPlayer(pc, nowMs);
        } catch (Throwable t) {
            _log.log(
                    Level.WARNING,
                    "[FishingTimeController] fishing failed pcId=" + safePcId(pc),
                    t);

            if (safeIsFishing(pc)) {
                return TICK_MS;
            }
            return -1L;
        }
    }

    public static long nextDelayMs(final L1PcInstance pc, final long nowMs) {
        if (pc == null || !pc.isFishing()) {
            return -1L;
        }

        final long fishingTime = pc.getFishingTime();
        if (fishingTime <= 0L) {
            return TICK_MS;
        }

        final long dueMs = fishingTime + FISHING_GRACE_MS + 1L;
        final long delay = dueMs - nowMs;
        return delay > 0L ? delay : 1L;
    }

    // ---------------------------------------------------------------------
    // player processing
    // ---------------------------------------------------------------------

    private long processPlayer(final L1PcInstance pc, final long nowMs) {
        if (!pc.isFishing()) {
            return -1L;
        }

        if ((pc.getMapId() != 5490 && pc.getMapId() != 4)
                || pc.isDead()
                || pc.getCurrentHp() == 0) {
            return TICK_MS;
        }

        final long fishingTime = pc.getFishingTime();
        if (nowMs <= fishingTime + FISHING_GRACE_MS) {
            return nextDelayMs(pc, nowMs);
        }

        final L1ItemInstance rod = pc._fishingRod;
        if (rod == null) {
            endFishing(pc);
            return -1L;
        }

        final int rodId = rod.getItemId();

        if (!isNoBaitRod(rodId) && !pc.getInventory().consumeItem(41295, 1)) {
            endFishing(pc);
            return -1L;
        }

        switch (rodId) {
        case 41294:
            processChargeRod(pc, rod, 41293, 80, EXP_NONE, 1);
            break;

        case 41305:
            processChargeRod(pc, rod, 41293, 80, EXP_NONE, 2);
            break;

        case 41306:
            processChargeRod(pc, rod, 41293, 80, EXP_NONE, 3);
            break;

        case 9991:
            processChargeRod(pc, rod, 9993, 80, EXP_NONE, 4);
            break;

        case 87058:
            processChargeRod(pc, rod, 41293, 90, EXP_ANCIENT_SILVER, 5);
            break;

        case 87059:
            processChargeRod(pc, rod, 41293, 90, EXP_ANCIENT_GOLD, 6);
            break;

        case 600229:
            processGrowthRod(
                    pc,
                    rod,
                    Config.FISH_TIME1,
                    Config.FISH_EXP1,
                    EXP_GROWN_UP,
                    false);
            break;

        case 4100293:
            processGrowthRod(
                    pc,
                    rod,
                    Config.FISH_TIME2,
                    Config.FISH_EXP2,
                    EXP_HIGH_GROWN_UP,
                    false);
            break;

        case 4200293:
            processGrowthRod(
                    pc,
                    rod,
                    Config.FISH_TIME2,
                    Config.FISH_EXP2,
                    EXP_HIGH_GROWN_UP,
                    true);
            break;

        case 41293:
            setNextFishing(pc, nowMs, 240);
            고탄력낚싯대(pc);
            break;

        default:
            endFishing(pc);
            return -1L;
        }

        if (!pc.isFishing()) {
            return -1L;
        }

        return nextDelayMs(pc, System.currentTimeMillis());
    }

    private static boolean isNoBaitRod(final int rodId) {
        return rodId == 600229
                || rodId == 87058
                || rodId == 87059
                || rodId == 4100293
                || rodId == 4200293;
    }

    /**
     * mode:
     * 1 릴장착 고탄력
     * 2 릴장착 은빛
     * 3 릴장착 금빛
     * 4 황소개구리
     * 5 고대 은빛
     * 6 고대 금빛
     */
    private void processChargeRod(
            final L1PcInstance pc,
            final L1ItemInstance rod,
            final int replacementItemId,
            final int nextSeconds,
            final byte expType,
            final int mode) {

        if (rod.getChargeCount() <= 0) {
            pc.getInventory().removeItem(rod, 1);
            pc._fishingRod = pc.getInventory().storeItem(replacementItemId, 1);
            endFishing(pc);
            return;
        }

        rod.setChargeCount(rod.getChargeCount() - 1);
        pc.getInventory().updateItem(rod, L1PcInventory.COL_CHARGE_COUNT);

        setNextFishing(pc, System.currentTimeMillis(), nextSeconds);

        switch (mode) {
        case 1:
            릴장착고탄력낚싯대(pc);
            break;
        case 2:
            릴장착은빛낚싯대(pc);
            break;
        case 3:
            릴장착금빛낚싯대(pc);
            break;
        case 4:
            황소개구리낚싯대(pc);
            break;
        case 5:
            Ancientsilver(pc, expType);
            break;
        case 6:
            Ancientgold(pc, expType);
            break;
        default:
            break;
        }
    }

    private void processGrowthRod(
            final L1PcInstance pc,
            final L1ItemInstance rod,
            final int nextSeconds,
            final int directExp,
            final byte expType,
            final boolean keepCharge) {

        if (rod.getChargeCount() <= 0) {
            pc.getInventory().removeItem(rod, 1);
            pc._fishingRod = pc.getInventory().storeItem(41293, 1);
            endFishing(pc);
            return;
        }

        if (!keepCharge) {
            rod.setChargeCount(rod.getChargeCount() - 1);
            pc.getInventory().updateItem(rod, L1PcInventory.COL_CHARGE_COUNT);
        }

        setNextFishing(pc, System.currentTimeMillis(), nextSeconds);

        if (!(pc.getLevel() >= Config.LIMITLEVEL
                && pc.getExp() >= ExpTable.getExpByLevel(Config.LIMITLEVEL + 1))) {
            CalcExp.AddExp(pc, directExp, 0);
        }

        if (expType == EXP_GROWN_UP) {
            if (Config.FISH_COMS) {
                성장의낚시대1(pc, expType);
            } else {
                성장의낚시대(pc, expType);
            }
        } else {
            if (Config.FISH_COMS) {
                고급성장의낚시대1(pc, expType);
            } else {
                고급성장의낚시대(pc, expType);
            }
        }
    }

    private static void setNextFishing(
            final L1PcInstance pc,
            final long nowMs,
            final int seconds) {

        pc.setFishingTime(nowMs + ((long) seconds * 1000L));
        SC_FISHING_NOT.sendCreate(pc, seconds);
    }

    // ---------------------------------------------------------------------
    // stop
    // ---------------------------------------------------------------------

    public void endFishing(L1PcInstance pc) {
        if (pc == null) {
            return;
        }

        final L1ItemInstance rod = pc._fishingRod;
        final int rodId = (rod != null) ? rod.getItemId() : 0;

        pc.setFishingTime(0L);
        pc.setFishingReady(false);
        pc.setFishing(false);
        pc._fishingRod = null;
        removeMember(pc);

        if (needsFishingIconEnd(rodId)) {
            try {
                SC_InventoryIcon.icoEnd(pc, L1SkillId.Fishing_etc);
            } catch (Throwable ignore) {
            }
        }

        try {
            SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
        } catch (Throwable ignore) {
        }

        try {
        	SC_CharVisualUpdate.broadcastPacked(pc, SC_CharVisualUpdate.pack(pc));
        } catch (Throwable ignore) {
        }

        try {
            SC_ServerMessage.send(pc, 1163);
        } catch (Throwable ignore) {
        }
    }

    private static boolean needsFishingIconEnd(final int rodId) {
        return rodId == 600229
                || rodId == 4100293
                || rodId == 4200293
                || rodId == 87058
                || rodId == 87059;
    }

    // ---------------------------------------------------------------------
    // fishing reward tables - flat primitive data
    // ---------------------------------------------------------------------

    private void Ancientsilver(L1PcInstance pc, byte expType) {
        rollDropTable(pc, DROP_ANCIENT_SILVER, expType);
    }

    private void Ancientgold(L1PcInstance pc, byte expType) {
        rollDropTable(pc, DROP_ANCIENT_GOLD, expType);
    }

    private void 성장의낚시대(L1PcInstance pc, byte expType) {
        rollDropTable(pc, DROP_GROW, expType);
    }

    private void 고급성장의낚시대(L1PcInstance pc, byte expType) {
        rollDropTable(pc, DROP_HIGH_GROW, expType);
    }

    private void 성장의낚시대1(L1PcInstance pc, byte expType) {
        final int chance = ThreadLocalRandom.current().nextInt(1, 1001);

        if (chance < Config.Fishchance1) {
            successFishing(pc, 667799, "를 획득하였습니다.", expType);
        } else {
            SC_ServerMessage.send(pc, 1136);
        }
    }

    private void 고급성장의낚시대1(L1PcInstance pc, byte expType) {
        final int chance = ThreadLocalRandom.current().nextInt(1, 1001);

        if (chance < Config.Fishchance) {
            successFishing(pc, 779988, "를 획득하였습니다.", expType);
        } else {
            SC_ServerMessage.send(pc, 1136);
        }
    }

    private void 황소개구리낚싯대(L1PcInstance pc) {
        rollDropTable(pc, DROP_FROG_OR_REEL_HIGH, EXP_NONE);
    }

    private void 릴장착고탄력낚싯대(L1PcInstance pc) {
        rollDropTable(pc, DROP_FROG_OR_REEL_HIGH, EXP_NONE);
    }

    private void 릴장착은빛낚싯대(L1PcInstance pc) {
        rollDropTable(pc, DROP_REEL_SILVER, EXP_NONE);
    }

    private void 릴장착금빛낚싯대(L1PcInstance pc) {
        rollDropTable(pc, DROP_REEL_GOLD, EXP_NONE);
    }

    private void 고탄력낚싯대(L1PcInstance pc) {
        rollDropTable(pc, DROP_NORMAL, EXP_NONE);
        SC_ServerMessage.send(pc, 1147);
    }

    /**
     * table: upperExclusive, itemId, messageIndex 3-int stride.
     * 원본의 nextInt(10000)+1 및 chance < upper 의미를 그대로 보존한다.
     */
    private void rollDropTable(
            final L1PcInstance pc,
            final int[] table,
            final byte expType) {

        final int chance = ThreadLocalRandom.current().nextInt(1, 10001);

        for (int i = 0; i < table.length; i += 3) {
            if (chance < table[i]) {
                successFishing(
                        pc,
                        table[i + 1],
                        DROP_MESSAGES[table[i + 2]],
                        expType);
                return;
            }
        }

        SC_ServerMessage.send(pc, 1136);
    }

    // ---------------------------------------------------------------------
    // reward helpers
    // ---------------------------------------------------------------------

    private boolean check_weight(L1PcInstance pc) {
        if (pc.getInventory().getSize() > (180 - 16)) {
            SC_ServerMessage.send(pc, 263);
            return false;
        }
        return true;
    }

    private void distribute_message(
            int owner_object_id,
            String message,
            int effect_id) {

        for (L1PcInstance viewer : L1World.getInstance().getVisiblePlayers(5490)) {
            if (viewer != null) SC_PacketBox.send(viewer, SC_PacketBox.GREEN_MESSAGE, message);
        }
        for (L1PcInstance viewer : L1World.getInstance().getVisiblePlayers(5490)) {
            if (viewer != null) SC_SkillSound.send(viewer, owner_object_id, effect_id);
        }
    }

    private void distribute_message(int owner_object_id, int itemid) {
        switch (itemid) {
        case 41303:
            distribute_message(
                    owner_object_id,
                    "누군가가 큰 은빛 베리아나를 낚아 올렸습니다!",
                    13641);
            break;
        case 41304:
            distribute_message(
                    owner_object_id,
                    "누군가가 큰 금빛 베리아나를 낚아 올렸습니다!",
                    13639);
            break;
        case 49094:
            distribute_message(
                    owner_object_id,
                    "누군가가 고대의 은빛 베리아나를 낚아 올렸습니다!",
                    13641);
            break;
        case 49095:
            distribute_message(
                    owner_object_id,
                    "누군가가 고대의 금빛 베리아나를 낚아 올렸습니다!",
                    13639);
            break;
        default:
            break;
        }
    }

    private void calculate_exp1(L1PcInstance pc, MJEFishingType f_type) {
        try {
            final int level = Math.max(pc.getLevel(), 2);
            final MJFishingExpInfo eInfo =
                    MJFishingExpInfo.find_fishing_exp_info(f_type, level);

            if (eInfo == null) {
                return;
            }

            if (Config.STANDBY_SERVER) {
                SC_SystemMessage.send(pc, "오픈대기중에는 경험치를 획득할 수 없으며 물품은 낚입니다");
                return;
            }

            if (pc.getLevel() >= Config.LIMITLEVEL) {
                SC_SystemMessage.send(pc, "더 이상 경험치를 획득 할 수 없습니다.");
            }

        } catch (Exception e) {
            _log.log(Level.WARNING, "[Fishing] calculate_exp1 failed", e);
        }
    }

    private void calculate_exp(L1PcInstance pc, MJEFishingType f_type) {
        try {
            final int level = Math.max(pc.getLevel(), 2);
            final MJFishingExpInfo eInfo =
                    MJFishingExpInfo.find_fishing_exp_info(f_type, level);

            if (eInfo == null) {
                return;
            }

            if (Config.STANDBY_SERVER) {
                SC_SystemMessage.send(pc, "오픈대기중에는 경험치를 획득할 수 없으며 물품은 낚입니다");
                return;
            }

            final int needExp = ExpTable.getNeedExpNextLevel(52);
            double exp = needExp * eInfo.get_default_exp();

            final int ain = pc.getAccount().getBlessOfAin();

            if (ain >= 10000) {
                double ainEffect = 1D;

                if (ain > 20000) {
                    ainEffect += 0.3D;
                }

                if (pc.hasSkillEffect(L1SkillId.DRAGON_TOPAZ) && ain >= 20000) {
                    ainEffect += 0.8D;
                }

                exp += (exp * ainEffect);

                pc.getAccount().addBlessOfAin(
                        -(int) SC_REST_EXP_INFO_NOTI.calcDecreaseCharacterEinhasad(
                                pc,
                                exp * eInfo.get_ain_ration()),
                        pc);
            }

            if (pc.PC방_버프) {
                exp *= 1.1D;
            }

            exp *= eInfo.get_addition_exp();
            exp = Math.max(1D, exp);

            final int maxLevel =
                    GameServerSetting.getInstance().get_maxLevel();

            if (pc.getLevel() >= maxLevel) {
                final int maxExp =
                        ExpTable.getExpByLevel(maxLevel + 1);

                if (pc.getExp() + exp >= maxExp) {
                    return;
                }
            }

            final int nextLevelExp =
                    ExpTable.getExpByLevel(level + 1);

            if ((exp + pc.getExp()) > nextLevelExp) {
                exp = nextLevelExp - pc.getExp();
            }

            pc.addExp((int) exp);

            /* CIO owner를 DB I/O로 막지 않는다. */
            ThreadPool.getInstance().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        pc.save();
                    } catch (Throwable t) {
                        _log.log(Level.WARNING, "[Fishing] async save failed", t);
                    }
                }
            });

        } catch (Exception e) {
            _log.log(Level.WARNING, "[Fishing] calculate_exp failed", e);
        }
    }

    private void successFishing(
            L1PcInstance pc,
            int itemid,
            String message,
            byte expType) {

        if (!check_weight(pc)) {
            return;
        }

        final L1ItemInstance item =
                pc.getInventory().storeItem(itemid, 1);

        if (item != null) {
            if (itemid == 667799 || itemid == 779988) {
                SC_SystemMessage.send(pc, "낚시: " + item.getItem().getName() + "를 획득하였습니다.");

                if (Config.낚시이펙트) {
                    SC_SkillSound.send(pc, pc.getId(), 763);
                    SC_SkillSound.broadcast(pc, pc.getId(), 763);
                }

            } else {
                SC_ServerMessage.send(pc, 1185, message);

                if (Config.낚시이펙트) {
                    SC_SkillSound.send(pc, pc.getId(), 763);
                    SC_SkillSound.broadcast(pc, pc.getId(), 763);
                }
            }
        }

        distribute_message(pc.getId(), itemid);

        switch (expType) {
        case EXP_GROWN_UP:
            calculate_exp1(pc, MJEFishingType.GROWN_UP);
            break;

        case EXP_HIGH_GROWN_UP:
            calculate_exp1(pc, MJEFishingType.HIGH_GROWN_UP);
            break;

        case EXP_ANCIENT_SILVER:
            calculate_exp(pc, MJEFishingType.ACIENT_SILVER);
            break;

        case EXP_ANCIENT_GOLD:
            calculate_exp(pc, MJEFishingType.ACIENT_GOLD);
            break;

        default:
            break;
        }
    }

    // ---------------------------------------------------------------------
    // safe helpers
    // ---------------------------------------------------------------------

    private static int safePcId(L1PcInstance pc) {
        try {
            return (pc != null) ? pc.getId() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean safeIsFishing(L1PcInstance pc) {
        try {
            return pc != null && pc.isFishing();
        } catch (Throwable t) {
            return false;
        }
    }
}
