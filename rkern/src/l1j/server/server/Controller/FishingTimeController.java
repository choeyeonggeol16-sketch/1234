package l1j.server.server.Controller;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.GameServerSetting;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.experience.fishing.EFishingType;
import l1j.server.server.model.character.experience.fishing.FishingExpInfo;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.Exp.CalcExp;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.packets.Protocol.MainServer.Pledge.Proto.SC_REST_EXP_INFO_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_FISHING_NOT;
import l1j.server.server.storage.CacheD;

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
    private static final int DROP_SPECIAL = 10;
    private static final int DROP_FULL = -1;
    private static final int MODE_NORMAL = 7;
    private static final int FISHING_MAX_SLOTS = 180 - 16;

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

    public boolean addMember(L1PcInstance pc) {
        if (pc == null || !pc.isFishing()) return false;
        return CIOThreadCallback.getInstance().startFishing(pc) != null;
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
            endFishing(pc);
            return -1L;
        }

        final L1ItemInstance rod = pc._fishingRod;
        if (rod == null || pc.getInventory().GetByServerID(rod.getId()) != rod
                || rod.getCount() <= 0) {
            endFishing(pc);
            return -1L;
        }
        // Never mutate charge, bait or rewards while an economy receipt owns
        // the character inventory. Retry on the existing fishing timer.
        if (pc.isItemTransferBusy() || pc.isTradeResolving() || pc.getTradeID() != 0) {
            return TICK_MS;
        }

        final long fishingTime = pc.getFishingTime();
        if (nowMs <= fishingTime + FISHING_GRACE_MS) {
            return nextDelayMs(pc, nowMs);
        }

        final int rodId = rod.getItemId();

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
                    EXP_GROWN_UP);
            break;

        case 4100293:
            processGrowthRod(
                    pc,
                    rod,
                    Config.FISH_TIME2,
                    Config.FISH_EXP2,
                    EXP_HIGH_GROWN_UP);
            break;

        case 4200293:
            processGrowthRod(
                    pc,
                    rod,
                    Config.FISH_TIME2,
                    Config.FISH_EXP2,
                    EXP_HIGH_GROWN_UP);
            break;

        case 41293:
            prepareRodCycle(pc, rod, 0, 240, MODE_NORMAL, 0, EXP_NONE);
            break;

        default:
            endFishing(pc);
            return -1L;
        }

        if (!pc.isFishing()) {
            return -1L;
        }

        return pc.isItemTransferBusy() ? TICK_MS : nextDelayMs(pc, System.currentTimeMillis());
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

        prepareRodCycle(pc, rod, replacementItemId, nextSeconds, mode, 0, expType);
    }

    private void processGrowthRod(
            final L1PcInstance pc,
            final L1ItemInstance rod,
            final int nextSeconds,
            final int directExp,
            final byte expType) {
        // No-charge-growth rods keep their existing charge policy. The reward
        // still uses the same durable receipt when a fish was selected.
        prepareRodCycle(pc, rod, 41293, nextSeconds, 0, directExp, expType);
    }

    private void prepareRodCycle(final L1PcInstance pc, final L1ItemInstance rod,
            int replacementItemId, int nextSeconds, int mode, int directExp, byte expType) {
        final long cycleTime = pc.getFishingTime();
        final int charge = rod.getChargeCount();
        final boolean normal = mode == MODE_NORMAL;
        final boolean exchange = !normal && charge <= 0;
        final boolean unlimited = rod.getItemId() == 4200293 && !exchange;
        final int drop;
        final String specialName;
        // Baitless unlimited rod: no inventory work on a missed catch or
        // a catch rejected by the existing 164-slot fishing limit.
        final int unlimitedRoll = unlimited ? rollFishingDrop(mode, expType) : 0;
        if (unlimited && (unlimitedRoll == 0 || pc.getInventory().getSize() > FISHING_MAX_SLOTS)) {
            finishRodCycle(pc, rod, cycleTime, nextSeconds, mode, directExp, expType,
                    false, unlimitedRoll == 0 ? 0 : DROP_FULL, null);
            return;
        }
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            int slots = pc.getInventory().getSize();
            if (!isNoBaitRod(rod.getItemId())) {
                // Same first eligible stack as the transaction template debit.
                // Track its removal for the original post-debit slot limit.
                final L1ItemInstance bait = pc.getInventory().GetFirstUnequippedItemByClassID(41295);
                if (bait == null || !tx.debit(bait, 1)) {
                    endFishing(pc);
                    return;
                }
                if (bait.getCount() == 1) slots--;
            }
            if (exchange) {
                if (!tx.debit(rod, 1) || tx.grant(replacementItemId, 1) == null) {
                    endFishing(pc);
                    return;
                }
                drop = 0;
                specialName = null;
            } else {
                if (!normal && !unlimited) tx.changeChargeCount(rod, charge - 1);
                final int rolled = unlimited ? unlimitedRoll : rollFishingDrop(mode, expType);
                drop = rolled != 0 && slots > FISHING_MAX_SLOTS ? DROP_FULL : rolled;
                if (drop > 0) {
                    // Commit the fish together with the bait/charge, never as
                    // a separate post-commit storeItem operation.
                    final L1ItemInstance granted = tx.grant(drop >>> 4, 1);
                    if (granted == null) {
                        endFishing(pc);
                        return;
                    }
                    specialName = (drop & 15) == DROP_SPECIAL ? granted.getItem().getName() : null;
                } else specialName = null;
            }
            tx.onFailure(() -> {
                if (sameCycle(pc, rod, cycleTime)) endFishing(pc);
                SC_SystemMessage.send(pc, "낚시 아이템 처리를 저장하지 못했습니다.");
            });
            CDB.RequestUseItem(pc, tx, () -> finishRodCycle(pc, rod, cycleTime,
                    nextSeconds, mode, directExp, expType, exchange, drop, specialName));
        } catch (RuntimeException failure) {
            if (sameCycle(pc, rod, cycleTime)) endFishing(pc);
            _log.log(Level.WARNING, "Fishing rod receipt prepare failed", failure);
        }
    }

    private void finishRodCycle(L1PcInstance pc, L1ItemInstance rod, long cycleTime,
            int nextSeconds, int mode, int directExp, byte expType, boolean exchange,
            int drop, String specialName) {
        if (exchange) {
            if (sameCycle(pc, rod, cycleTime)) endFishing(pc);
            return;
        }
        if (sameCycle(pc, rod, cycleTime))
            setNextFishing(pc, System.currentTimeMillis(), nextSeconds);
        // Growth-rod direct experience applies even on a missed catch.
        if (mode == 0 && !(pc.getLevel() >= Config.LIMITLEVEL
                && pc.getExp() >= ExpTable.getExpByLevel(Config.LIMITLEVEL + 1))) {
            CalcExp.AddExp(pc, directExp, 0);
        }
        if (drop == DROP_FULL) SC_ServerMessage.send(pc, 263);
        else if (drop == 0) SC_ServerMessage.send(pc, 1136);
        else notifyFishingReward(pc, drop, specialName, expType);
        if (mode == MODE_NORMAL) SC_ServerMessage.send(pc, 1147);
    }

    private static boolean sameCycle(L1PcInstance pc, L1ItemInstance rod, long cycleTime) {
        return pc.isFishing() && pc._fishingRod == rod && pc.getFishingTime() == cycleTime;
    }

    private static void setNextFishing(
            final L1PcInstance pc,
            final long nowMs,
            final int seconds) {

        pc.setFishingTime(nowMs + ((long) seconds * 1000L));
        try {
            SC_FISHING_NOT.sendCreate(pc, seconds);
        } catch (RuntimeException noticeFailure) {
            _log.log(Level.WARNING, "Fishing timer notification failed", noticeFailure);
        }
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

    /** Packed reward: item ID in upper bits, existing message index in low four. */
    private static int selectDrop(final int[] table, final int chance) {
        for (int i = 0; i < table.length; i += 3) {
            if (chance < table[i]) return (table[i + 1] << 4) | table[i + 2];
        }
        return 0;
    }

    private static int selectSpecialDrop(boolean grown, int chance, int limit) {
        return chance < limit ? ((grown ? 667799 : 779988) << 4) | DROP_SPECIAL : 0;
    }

    private static int rollFishingDrop(int mode, byte expType) {
        if (mode == 0 && Config.FISH_COMS) {
            final int chance = ThreadLocalRandom.current().nextInt(1, 1001);
            final boolean grown = expType == EXP_GROWN_UP;
            final int limit = grown ? Config.Fishchance1 : Config.Fishchance;
            return selectSpecialDrop(grown, chance, limit);
        }
        return selectDrop(fishingRewardTable(mode, expType), ThreadLocalRandom.current().nextInt(1, 10001));
    }

    private static int[] fishingRewardTable(int mode, byte expType) {
        return switch (mode) {
            case 0 -> expType == EXP_GROWN_UP ? DROP_GROW : DROP_HIGH_GROW;
            case 1, 4 -> DROP_FROG_OR_REEL_HIGH;
            case 2 -> DROP_REEL_SILVER;
            case 3 -> DROP_REEL_GOLD;
            case 5 -> DROP_ANCIENT_SILVER;
            case 6 -> DROP_ANCIENT_GOLD;
            case MODE_NORMAL -> DROP_NORMAL;
            default -> throw new IllegalArgumentException("Invalid fishing reward mode: " + mode);
        };
    }

    // ---------------------------------------------------------------------
    // reward helpers
    // ---------------------------------------------------------------------

    private void distribute_message(
            int owner_object_id,String message,int effect_id){
        // Preserve two independent AOI lookups, first all notices, then all FX.
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenRecipientsBestEffort(
                CWorld.getInstance().getVisiblePlayers(5490),
                SC_PacketBox.nativeGreenWriter(message));
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenRecipientsBestEffort(
                CWorld.getInstance().getVisiblePlayers(5490),
                SC_SkillSound.nativeWriter(owner_object_id,effect_id));
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

    private void calculate_exp1(L1PcInstance pc, EFishingType f_type) {
        try {
            final int level = Math.max(pc.getLevel(), 2);
            final FishingExpInfo eInfo =
                    FishingExpInfo.find_fishing_exp_info(f_type, level);

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

    private void calculate_exp(L1PcInstance pc, EFishingType f_type) {
        try {
            final int level = Math.max(pc.getLevel(), 2);
            final FishingExpInfo eInfo =
                    FishingExpInfo.find_fishing_exp_info(f_type, level);

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

            // Wait until the current item receipt has acknowledged owner replay.
            // save() captures a snapshot; CacheD performs the JDBC write.
            final var session = pc.getNetConnection();
            if (session == null) return;
            final int stamp = pc.getSessionStamp();
            CIOThreadCallback.getInstance().completeOnShard(session.affinityShard(), 1L, () -> {
                if (session.getActiveChar() == pc && pc.getNetConnection() == session
                        && pc.getSessionStamp() == stamp && !pc.isLogoutStarted()) pc.save();
            }, failure -> {
                if (failure != null) _log.log(Level.WARNING, "[Fishing] async save failed", failure);
            });

        } catch (Exception e) {
            _log.log(Level.WARNING, "[Fishing] calculate_exp failed", e);
        }
    }

    private void notifyFishingReward(L1PcInstance pc, int drop, String specialName, byte expType) {
        final int itemid = drop >>> 4;
        final int messageIndex = drop & 15;
        if (messageIndex == DROP_SPECIAL) {
            SC_SystemMessage.send(pc, "낚시: " + specialName + "를 획득하였습니다.");
        } else {
            SC_ServerMessage.send(pc, 1185, DROP_MESSAGES[messageIndex]);
        }
        if (Config.낚시이펙트) {
            SC_SkillSound.send(pc, pc.getId(), 763);
            SC_SkillSound.broadcast(pc, pc.getId(), 763);
        }

        distribute_message(pc.getId(), itemid);

        switch (expType) {
        case EXP_GROWN_UP:
            calculate_exp1(pc, EFishingType.GROWN_UP);
            break;

        case EXP_HIGH_GROWN_UP:
            calculate_exp1(pc, EFishingType.HIGH_GROWN_UP);
            break;

        case EXP_ANCIENT_SILVER:
            calculate_exp(pc, EFishingType.ACIENT_SILVER);
            break;

        case EXP_ANCIENT_GOLD:
            calculate_exp(pc, EFishingType.ACIENT_GOLD);
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
