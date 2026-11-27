package l1j.server.server.model.network.core.cs.packet;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.HpMpGrowthHistory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character.StatResetVitals;
import l1j.server.server.model.L1ReturnStatTemp;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.mysql.CharacterStatReceipt;
import l1j.server.server.utils.CPCParamDB;
import l1j.server.server.utils.CheckInitStat;

public final class CS_ReturnStatus {
    private static final Logger LOG = Logger.getLogger(CS_ReturnStatus.class.getName());
    private static final int BASE_STAT_SUM_MAX = 75;
    private static final int BASE_STAT_MAX = 20;
    private static final int BONUS_LEVEL_BASE = 50;
    private static final int STAT_STR = 1;
    private static final int STAT_INT = 2;
    private static final int STAT_WIS = 3;
    private static final int STAT_DEX = 4;
    private static final int STAT_CON = 5;
    private static final int STAT_CHA = 6;

    private CS_ReturnStatus() {}

    public static void handle(CUserSocket client, int type, int levelup, int statusup, long packedStats) {
        if (client == null || !client.ownsReadyGameSession() || type < 1 || type > 3) return;
        final L1PcInstance pc = client.getActiveChar();
        if (pc == null) return;

        final int stamp = pc.getSessionStamp();
        final Selection selection;
        pc.lockUserState();
        try {
            if (pc.getReturnStat() == 0 || !pc.isItemTransferSession(client, stamp)) return;
            final L1ReturnStatTemp stage = pc.rst;
            if (stage != null && stage.commitPending) return;
            final int level = pc.getLevel();
            if (level < 1 || level > HpMpGrowthHistory.MAX_LEVEL || ExpTable.getLevelByExp(pc.getExp()) != level)
                return;
            if (stage != null && (stage.targetLevel != level || stage.characterType != pc.getType())) {
                // Reopen the detached preview at the current level using the already-paid marker.
                pc.rst = null;
                if (type != 1) {
                    resendPhase(pc);
                    return;
                }
            }

            selection = switch (type) {
                case 1 -> {
                    start(pc, packedStats);
                    yield null;
                }
                case 2 -> levelUp(pc, levelup, statusup);
                case 3 -> selectElixirs(pc, packedStats);
                default -> throw new AssertionError("Checked reset packet type");
            };
        } finally {
            pc.unlockUserState();
        }

        if (selection != null) submit(client, pc, stamp, selection);
    }

    private static void start(L1PcInstance pc, long packedStats) {
        if (pc.rst != null) {
            resendPhase(pc);
            return;
        }
        final int str = readStat(packedStats, 0);
        final int intel = readStat(packedStats, 8);
        final int wis = readStat(packedStats, 16);
        final int dex = readStat(packedStats, 24);
        final int con = readStat(packedStats, 32);
        final int cha = readStat(packedStats, 40);
        if (!validBaseStat(str) || !validBaseStat(intel) || !validBaseStat(wis)
                || !validBaseStat(dex) || !validBaseStat(con) || !validBaseStat(cha)
                || str + intel + wis + dex + con + cha > BASE_STAT_SUM_MAX
                || !CheckInitStat.CheckBaseStats(pc, str, dex, con, intel, wis, cha)) {
            resendPhase(pc);
            return;
        }

        final L1ReturnStatTemp stage = new L1ReturnStatTemp();
        stage.targetLevel = pc.getLevel();
        stage.characterType = pc.getType();
        stage.basestr = str;
        stage.baseint = intel;
        stage.basewis = wis;
        stage.basedex = dex;
        stage.basecon = con;
        stage.basecha = cha;
        stage.level = 1;
        stage.baseHp = CPCParamDB.initialHp(stage.characterType);
        stage.baseMp = CPCParamDB.initialMp(stage.characterType, wis);
        stage.ac = pc.calculateBaseAc(dex, stage.level);
        pc.rst = stage;
        resendPhase(pc);
    }

    private static boolean validBaseStat(int value) {
        return value >= 3 && value <= BASE_STAT_MAX;
    }

    private static Selection levelUp(L1PcInstance pc, int action, int finalStat) {
        final L1ReturnStatTemp stage = pc.rst;
        if (stage == null || stage.awaitingElixirs) {
            resendPhase(pc);
            return null;
        }
        if (action == 8) return selectFinalLevelPoint(pc, stage, finalStat);
        if (stage.level >= stage.targetLevel || stage.levelSelectionComplete) {
            resendPhase(pc);
            return null;
        }

        switch (action) {
            case 0 -> advanceLevel(pc, stage);
            case STAT_STR, STAT_INT, STAT_WIS, STAT_DEX, STAT_CON, STAT_CHA -> {
                if (stage.level <= BONUS_LEVEL_BASE || !allocateLevelPoint(pc, stage, action)) {
                    resendPhase(pc);
                    return null;
                }
                advanceLevel(pc, stage);
            }
            case 7 -> {
                if (stage.level > 40 || stage.level + 10 >= stage.targetLevel) {
                    resendPhase(pc);
                    return null;
                }
                for (int i = 0; i < 10; i++) advanceLevel(pc, stage);
            }
            default -> { return null; }
        }
        resendPhase(pc);
        return null;
    }

    private static Selection selectFinalLevelPoint(L1PcInstance pc, L1ReturnStatTemp stage, int stat) {
        if (!hasCompleteResetGrowth(stage)) {
            resendPhase(pc);
            return null;
        }
        if (!stage.levelSelectionComplete) {
            if (stage.level > BONUS_LEVEL_BASE && stat != 0 && !allocateLevelPoint(pc, stage, stat)) {
                resendPhase(pc);
                return null;
            }
            stage.ac = pc.calculateBaseAc(stage.basedex + stage.dex, stage.level);
            stage.levelSelectionComplete = true;
            stage.awaitingElixirs = pc.getElixirStats() > 0;
        }
        if (stage.awaitingElixirs) {
            resendPhase(pc);
            return null;
        }
        return new Selection(stage, stage.basestr + stage.str, stage.baseint + stage.Int,
                stage.basewis + stage.wis, stage.basedex + stage.dex,
                stage.basecon + stage.con, stage.basecha + stage.cha);
    }

    private static Selection selectElixirs(L1PcInstance pc, long packedStats) {
        final L1ReturnStatTemp stage = pc.rst;
        if (stage == null || !stage.awaitingElixirs || pc.getElixirStats() <= 0) return null;

        final int str = readStat(packedStats, 0);
        final int intel = readStat(packedStats, 8);
        final int wis = readStat(packedStats, 16);
        final int dex = readStat(packedStats, 24);
        final int con = readStat(packedStats, 32);
        final int cha = readStat(packedStats, 40);
        final int dStr = str - stage.basestr - stage.str;
        final int dInt = intel - stage.baseint - stage.Int;
        final int dWis = wis - stage.basewis - stage.wis;
        final int dDex = dex - stage.basedex - stage.dex;
        final int dCon = con - stage.basecon - stage.con;
        final int dCha = cha - stage.basecha - stage.cha;
        final int maximum = permanentLimit(pc, stage);
        if (dStr < 0 || dInt < 0 || dWis < 0 || dDex < 0 || dCon < 0 || dCha < 0
                || str > maximum || intel > maximum || wis > maximum
                || dex > maximum || con > maximum || cha > maximum
                || dStr + dInt + dWis + dDex + dCon + dCha != pc.getElixirStats()) {
            resendPhase(pc);
            return null;
        }
        return new Selection(stage, str, intel, wis, dex, con, cha);
    }

    private record Selection(L1ReturnStatTemp stage, int str, int intel, int wis, int dex, int con, int cha) {}

    private static void submit(CUserSocket client, L1PcInstance pc, int stamp, Selection selected) {
        final L1ReturnStatTemp stage = selected.stage;
        // Claim the inventory/receipt owner before taking the user-state lock.
        // This is the same acquisition order used by ordinary stat investment.
        try (L1InventoryTransaction tx = L1InventoryTransaction.beginCharacterStat(pc)) {
            pc.lockUserState();
            try {
                final int marker = pc.getReturnStat();
                final int exp = pc.getExp();
                if (marker <= 0 || pc.rst != stage || stage.commitPending || !pc.isItemTransferSession(client, stamp)
                        || pc.getLevel() != stage.targetLevel || ExpTable.getLevelByExp(exp) != stage.targetLevel
                        || pc.getType() != stage.characterType) return;
                final StatResetVitals vitals = pc.projectStatResetVitals(stage.baseHp + stage.upHp,
                        stage.baseMp + stage.upMp, selected.con, selected.wis);
                final int ac = pc.projectEffectiveAc(selected.dex, stage.targetLevel);
                CharacterStatReceipt.stageReset(tx, pc, stage, selected.str, selected.intel, selected.wis,
                        selected.dex, selected.con, selected.cha, vitals.hp(), vitals.mp(), ac);
                stage.commitPending = true;
                try {
                    tx.onFailure(() -> retry(pc, stage));
                    tx.commitAfterInventoryRelease(() -> publish(pc, selected, marker),
                            () -> complete(pc, exp, vitals, ac));
                } catch (RuntimeException failure) {
                    stage.commitPending = false;
                    throw failure;
                }
            } finally {
                pc.unlockUserState();
            }
        } catch (RuntimeException failure) {
            LOG.log(Level.FINE, "Stat reset receipt admission deferred: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "능력치 저장을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static void retry(L1PcInstance pc, L1ReturnStatTemp stage) {
        pc.lockUserState();
        try {
            if (pc.rst != stage || pc.getReturnStat() == 0) return;
            stage.commitPending = false;
            SC_SystemMessage.send(pc, "능력치 저장에 실패했습니다. 확정을 다시 눌러 주세요.");
            resendPhase(pc);
        } finally {
            pc.unlockUserState();
        }
    }

    private static void publish(L1PcInstance pc, Selection selected, int marker) {
        final L1ReturnStatTemp stage = selected.stage;
        pc.lockUserState();
        try {
            if (pc.rst != stage || !stage.commitPending || pc.getReturnStat() != marker
                    || pc.getType() != stage.characterType)
                throw new IllegalStateException("Stat reset stage changed before durable replay");

            pc.setBonusStats(stage.allocatedLevelPoints());
            pc.ResetPrimaryStats(stage.basestr, stage.basedex, stage.basecon,
                    stage.baseint, stage.basewis, stage.basecha);
            pc.SetStatStr(selected.str);
            pc.SetStatInt(selected.intel);
            pc.SetStatWis(selected.wis);
            pc.SetStatDex(selected.dex);
            pc.SetStatCon(selected.con);
            pc.SetStatCha(selected.cha);
            pc.addBaseMaxHp((short) (stage.baseHp + stage.upHp - pc.getBaseMaxHp()));
            pc.addBaseMaxMp(stage.baseMp + stage.upMp - pc.getBaseMaxMp());
            pc.completeStatResetGrowth(stage.hpMpGrowth);
            // A progression update received during the DB round trip still
            // applies its normal growth on top of the newly committed reset.
            final int level = ExpTable.getLevelByExp(pc.getExp());
            if (level != stage.targetLevel)
                pc.applyHpMpLevelTransition(stage.targetLevel, level);
            pc.setLevel(level);
            pc.setHighLevel(Math.max(pc.getHighLevel(), Math.max(level, stage.targetLevel)));
            pc.setCurrentHp(pc.getMaxHp());
            pc.setCurrentMp(pc.getMaxMp());
            pc.setReturnStat(0);
            pc.rst = null;
            pc.publishPermanentStatRevision();
        } finally {
            pc.unlockUserState();
        }
    }

    private static void complete(L1PcInstance pc, int capturedExp, StatResetVitals vitals, int capturedAc) {
        if (pc.getExp() != capturedExp || pc.getCurrentHp() != vitals.hp()
                || pc.getCurrentMp() != vitals.mp() || pc.getAc() != capturedAc) {
            // Preserve concurrent progression or modifier changes after the receipt releases.
            try { pc.save(); }
            catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Post-reset progression save deferred: " + pc.getId(), failure);
            }
        }
        SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
        SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
        SC_SPMR.send(pc);
        SC_OwnCharStatus2.send(pc);
        SC_OwnCharStatus.send(pc);
        SC_OwnCharAttrDef.send(pc);
        SC_ReturnedStat.sendResetComplete(pc);
        pc.start_teleport(33430, 32810, 4, pc.getHeading(), 169, true, false);
        pc.RenewStat();
        pc.CheckStatus();
    }

    private static void resendPhase(L1PcInstance pc) {
        final L1ReturnStatTemp stage = pc.rst;
        SC_ReturnedStat.sendPc(pc, stage == null ? SC_ReturnedStat.START
                : stage.awaitingElixirs ? SC_ReturnedStat.END : SC_ReturnedStat.LEVELUP);
    }

    private static boolean hasCompleteResetGrowth(L1ReturnStatTemp stage) {
        if (stage.level != stage.targetLevel || stage.hpMpGrowth.floorLevel() != 1) return false;
        for (int level = 2; level <= stage.level; level++)
            if (!stage.hpMpGrowth.known(level)) return false;
        return true;
    }

    private static boolean allocateLevelPoint(L1PcInstance pc, L1ReturnStatTemp stage, int stat) {
        final int value = switch (stat) {
            case STAT_STR -> stage.basestr + stage.str;
            case STAT_INT -> stage.baseint + stage.Int;
            case STAT_WIS -> stage.basewis + stage.wis;
            case STAT_DEX -> stage.basedex + stage.dex;
            case STAT_CON -> stage.basecon + stage.con;
            case STAT_CHA -> stage.basecha + stage.cha;
            default -> Integer.MAX_VALUE;
        };
        if (value >= permanentLimit(pc, stage) || stage.allocatedLevelPoints() >= stage.level - BONUS_LEVEL_BASE)
            return false;

        switch (stat) {
            case STAT_STR -> ++stage.str;
            case STAT_INT -> ++stage.Int;
            case STAT_WIS -> ++stage.wis;
            case STAT_DEX -> ++stage.dex;
            case STAT_CON -> ++stage.con;
            case STAT_CHA -> ++stage.cha;
        }
        return true;
    }

    private static int permanentLimit(L1PcInstance pc, L1ReturnStatTemp stage) {
        return Math.max(pc.getHighLevel(), stage.targetLevel) >= 90 ? 50 : 45;
    }

    private static void advanceLevel(L1PcInstance pc, L1ReturnStatTemp stage) {
        stage.level++;
        final int hp = CPCParamDB.increaseHp(stage.characterType, (byte) (stage.basecon + stage.con));
        final int mp = CPCParamDB.increaseMp(stage.characterType, (byte) (stage.basewis + stage.wis));
        final int oldHp = stage.baseHp + stage.upHp;
        final int oldMp = stage.baseMp + stage.upMp;
        final int newHp = Math.min(32767, oldHp + hp);
        final int newMp = Math.min(32767, oldMp + mp);
        stage.hpMpGrowth.record(stage.level, newHp - oldHp, newMp - oldMp);
        stage.upHp = newHp - stage.baseHp;
        stage.upMp = newMp - stage.baseMp;
        stage.ac = pc.calculateBaseAc(stage.basedex + stage.dex, stage.level);
    }

    private static int readStat(long packed, int shift) {
        return (int) ((packed >>> shift) & 0xffL);
    }
}
