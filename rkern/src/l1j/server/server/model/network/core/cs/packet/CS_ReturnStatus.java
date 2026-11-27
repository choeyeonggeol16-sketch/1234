package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.L1ReturnStatTemp;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;

import l1j.server.server.utils.CPCParamDB;
import l1j.server.server.utils.CheckInitStat;
import l1j.server.server.storage.CacheD;

public final class CS_ReturnStatus {

	private static final int BASE_STAT_SUM_MAX = 80;

	private static final int BONUS_LEVEL_BASE = 50;

	private static final int STAT_STR = 1;
	private static final int STAT_INT = 2;
	private static final int STAT_WIS = 3;
	private static final int STAT_DEX = 4;
	private static final int STAT_CON = 5;
	private static final int STAT_CHA = 6;

	private CS_ReturnStatus() {}

	public static void handle(CUserSocket client, int type, int levelup, int statusup, long packedStats) {

		if (client == null || !client.ownsReadyGameSession()) {
			return;
		}

		final L1PcInstance pc = client.getActiveChar();

		if (pc == null) {
			return;
		}

        final int stamp = pc.getSessionStamp();
        final boolean finalSelection = type == 3 || type == 2 && levelup == 8;
        final var inventory = pc.getInventory();
        if (finalSelection && !inventory.tryBeginEquipmentChange()) return;
		pc.lockUserState();
		try {
			if (pc.getReturnStat() == 0 || !pc.isItemTransferSession(client, stamp)
                    || finalSelection && CacheD.getInstance().hasPendingOwnerApply(pc.getId())) return;
			switch (type) {
			case 1:
				handleType1Start(client, pc, packedStats);
				return;

			case 2:
				handleType2LevelUp(client, pc, levelup, statusup);
				return;

			case 3:
				handleType3Elixir(pc, packedStats);
				return;

			default:
				return;
			}

		} catch (Throwable ignored) {
			/*
			 * 기존 C_ReturnStatus의 예외 무시(semantics) 기조를 그대로 유지합니다.
			 */
		} finally {
			pc.unlockUserState();
            if (finalSelection) inventory.endEquipmentChange();
		}
	}

	// =========================================================
	// TYPE 1: 스탯 초기화 시작
	// =========================================================

	private static void handleType1Start(CUserSocket client, L1PcInstance pc, long packedStats) {

		final int str = statStr(packedStats);
		final int intel = statInt(packedStats);
		final int wis = statWis(packedStats);
		final int dex = statDex(packedStats);
		final int con = statCon(packedStats);
		final int cha = statCha(packedStats);

		if (isInvalidBaseStats(str, dex, con, wis, intel, cha)
                || !CheckInitStat.CheckBaseStats(pc, str, dex, con, intel, wis, cha)) {
			kickStatBug(client, pc);
			return;
		}

		final L1ReturnStatTemp rst = new L1ReturnStatTemp();

		rst.basestr = str;
		rst.baseint = intel;
		rst.basewis = wis;
		rst.basedex = dex;
		rst.basecon = con;
		rst.basecha = cha;

		rst.str = 0;
		rst.Int = 0;
		rst.wis = 0;
		rst.dex = 0;
		rst.con = 0;
		rst.cha = 0;

		rst.level = 1;
		rst.upHp = 0;
		rst.upMp = 0;
		rst.ac = 10;

		rst.baseHp = getInitHp(pc);
		rst.baseMp = getInitMp(pc, rst.basewis);
        pc.rst = rst;

		SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.LEVELUP);
	}

	private static boolean isInvalidBaseStats(int str, int dex, int con, int wis, int intel, int cha) {

		if (str <= 0 || dex <= 0 || con <= 0 || wis <= 0 || intel <= 0 || cha <= 0) {
			return true;
		}

		return str + dex + con + wis + intel + cha > BASE_STAT_SUM_MAX;
	}

	// =========================================================
	// TYPE 2: 레벨업 시 스탯 분배
	// =========================================================

	private static void handleType2LevelUp(CUserSocket client, L1PcInstance pc, int levelup, int statusup) {

		if (pc.rst == null) return;
        if (pc.rst.awaitingElixirs) {
            if (levelup == 8) SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.END);
            return;
        }

		/*
		 * 레벨 범위 방어 로직.
		 */
		if (pc.rst.level > pc.getHighLevel() || (pc.rst.level == pc.getHighLevel() && levelup != 8)) {
			System.out.println(String.format(
					"%s님이 잘못된 레벨로 인해 스초를 다시 시작합니다.(원본레벨 : %d, 시도한 레벨 : %d)",
					safeName(pc), pc.getHighLevel(), pc.rst.level));

			kick(client);
			return;
		}

		/*
		 * 50 레벨 이하에서 스탯 추가 코드 방지.
		 */
		if (pc.rst.level <= BONUS_LEVEL_BASE && levelup != 0 && levelup != 7 && levelup != 8) {
			System.out.println(String.format(
					"%s님이 50레벨 이하인데 스탯 추가를 시도했습니다. 시도레벨 : %d",
					safeName(pc), pc.rst.level));

			kick(client);
			return;
		}

		switch (levelup) {
		case 0:
			statup(pc);
			SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.LEVELUP);
			return;

		case STAT_STR:
		case STAT_INT:
		case STAT_WIS:
		case STAT_DEX:
		case STAT_CON:
		case STAT_CHA:
			if (!allocateLevelPoint(pc, levelup)) {
				return;
			}

			statup(pc);
			SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.LEVELUP);
			return;

		case 7:
			/*
			 * +10 레벨 한 번에 올리기.
			 */
			if (pc.rst.level > 40) {
				return;
			}

			if ((pc.rst.level + 10) < pc.getHighLevel()) {
				for (int i = 0; i < 10; i++) {
					statup(pc);
				}
				SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.LEVELUP);
			}
			return;

		case 8:
			handleBonusAndMaybeFinish(client, pc, statusup);
			return;

		default:
			return;
		}
	}

	private static void handleBonusAndMaybeFinish(CUserSocket client, L1PcInstance pc, int statusup) {

		if (pc.rst == null) {
			return;
		}
        if (!hasCompleteResetGrowth(pc)) {
            kick(client);
            return;
        }

		if (pc.rst.level > BONUS_LEVEL_BASE) {
			switch (statusup) {
			case STAT_STR:
			case STAT_INT:
			case STAT_WIS:
			case STAT_DEX:
			case STAT_CON:
			case STAT_CHA:
				if (!allocateLevelPoint(pc, statusup)) {
					return;
				}
				break;
			default:
				break;
			}
		}

		/*
		 * 엘릭서가 있으면 클라이언트에 엘릭서 스탯 입력 단계를 요청합니다.
		 */
		if (pc.getElixirStats() > 0) {
            pc.rst.awaitingElixirs = true;
			SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.END);
			return;
		}

        final L1ReturnStatTemp stage = pc.rst;
        applyFinalAndFinish(pc, stage, stage.basestr + stage.str, stage.baseint + stage.Int,
                stage.basewis + stage.wis, stage.basedex + stage.dex,
                stage.basecon + stage.con, stage.basecha + stage.cha, false);
	}

	// =========================================================
	// TYPE 3: 엘릭서 사용
	// =========================================================

    private static void handleType3Elixir(L1PcInstance pc, long packedStats) {
        final L1ReturnStatTemp stage = pc.rst;
        if (stage == null || !stage.awaitingElixirs || pc.getElixirStats() <= 0
                || !hasCompleteResetGrowth(pc)) return;

        final int str = statStr(packedStats), intel = statInt(packedStats), wis = statWis(packedStats);
        final int dex = statDex(packedStats), con = statCon(packedStats), cha = statCha(packedStats);
        final int dStr = str - stage.basestr - stage.str, dInt = intel - stage.baseint - stage.Int;
        final int dWis = wis - stage.basewis - stage.wis, dDex = dex - stage.basedex - stage.dex;
        final int dCon = con - stage.basecon - stage.con, dCha = cha - stage.basecha - stage.cha;
        final int maximum = permanentLimit(pc);
        if (dStr < 0 || dInt < 0 || dWis < 0 || dDex < 0 || dCon < 0 || dCha < 0
                || str > maximum || intel > maximum || wis > maximum
                || dex > maximum || con > maximum || cha > maximum
                || dStr + dInt + dWis + dDex + dCon + dCha != pc.getElixirStats()) {
            SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.END);
            return;
        }
        applyFinalAndFinish(pc, stage, str, intel, wis, dex, con, cha, true);
    }

    /** The reset window owns a detached allocation. Publish only a validated final selection. */
    private static void applyFinalAndFinish(L1PcInstance pc, L1ReturnStatTemp stage,
            int str, int intel, int wis, int dex, int con, int cha, boolean withElixirs) {
        pc.setBonusStats(stage.allocatedLevelPoints());
        pc.setExp(pc.getReturnStat());
        pc.ResetPrimaryStats(stage.basestr, stage.basedex, stage.basecon, stage.baseint, stage.basewis, stage.basecha);
        pc.SetStatStr(str);
        pc.SetStatInt(intel);
        pc.SetStatWis(wis);
        pc.SetStatDex(dex);
        pc.SetStatCon(con);
        pc.SetStatCha(cha);
        pc.addBaseMaxHp((short) (stage.baseHp + stage.upHp - pc.getBaseMaxHp()));
        pc.addBaseMaxMp((short) (stage.baseMp + stage.upMp - pc.getBaseMaxMp()));
        pc.setCurrentHp(pc.getMaxHp());
        pc.setCurrentMp(pc.getMaxMp());
        pc.completeStatResetGrowth(stage.hpMpGrowth);
        pc.setReturnStat(0);
        pc.rst = null;
        pc.publishPermanentStatRevision();
        saveCharacterState(pc);

        SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
        SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
        SC_SPMR.send(pc);
        SC_OwnCharStatus2.send(pc);
        SC_OwnCharStatus.send(pc);
        SC_OwnCharAttrDef.send(pc);
        if (!withElixirs) SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.END);
        pc.start_teleport(33430, 32810, 4, pc.getHeading(), 169, true, false);
        if (!withElixirs) pc.RenewStat();
        pc.CheckStatus();
    }

	// =========================================================
	// STAT 로직
	// =========================================================

    private static boolean hasCompleteResetGrowth(L1PcInstance pc) {
        final L1ReturnStatTemp stage = pc.rst;
        if (stage == null || stage.hpMpGrowth == null
                || stage.level != pc.getHighLevel() || stage.level != pc.getLevel()
                || stage.level < 1 || stage.level > l1j.server.server.model.HpMpGrowthHistory.MAX_LEVEL
                || stage.hpMpGrowth.floorLevel() != 1) return false;
        for (int level = 2; level <= stage.level; level++)
            if (!stage.hpMpGrowth.known(level)) return false;
        return true;
    }

    private static boolean allocateLevelPoint(L1PcInstance pc, int type) {
        final L1ReturnStatTemp stage = pc.rst;
        final int value = switch (type) {
            case STAT_STR -> stage.basestr + stage.str;
            case STAT_INT -> stage.baseint + stage.Int;
            case STAT_WIS -> stage.basewis + stage.wis;
            case STAT_DEX -> stage.basedex + stage.dex;
            case STAT_CON -> stage.basecon + stage.con;
            case STAT_CHA -> stage.basecha + stage.cha;
            default -> throw new IllegalArgumentException("Unknown stat " + type);
        };
        if (value >= permanentLimit(pc) || stage.allocatedLevelPoints() >= stage.level - BONUS_LEVEL_BASE) {
            SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.LEVELUP);
            return false;
        }
        switch (type) {
            case STAT_STR -> ++stage.str;
            case STAT_INT -> ++stage.Int;
            case STAT_WIS -> ++stage.wis;
            case STAT_DEX -> ++stage.dex;
            case STAT_CON -> ++stage.con;
            case STAT_CHA -> ++stage.cha;
        }
        return true;
    }

    private static int permanentLimit(L1PcInstance pc) {
        return pc.getHighLevel() >= 90 ? 50 : 45;
    }

	private static void statup(L1PcInstance pc) {

		pc.rst.level++;

		final int hp = CPCParamDB.increaseHp(pc.getType(), (byte) (pc.rst.con + pc.rst.basecon));
		final int mp = CPCParamDB.increaseMp(pc.getType(), (byte) (pc.rst.wis + pc.rst.basewis));

        int oldHp = Math.min(32767, pc.rst.baseHp + pc.rst.upHp);
        int oldMp = Math.min(32767, pc.rst.baseMp + pc.rst.upMp);
        int nextHp = Math.min(32767, oldHp + hp), nextMp = Math.min(32767, oldMp + mp);
        pc.rst.hpMpGrowth.record(pc.rst.level, nextHp - oldHp, nextMp - oldMp);
        pc.rst.upHp = nextHp - pc.rst.baseHp;
        pc.rst.upMp = nextMp - pc.rst.baseMp;
	}

	// =========================================================
	// PACKED STAT 추출
	// =========================================================

	private static int statStr(long value) {
		return (int) (value & 0xFFL);
	}

	private static int statInt(long value) {
		return (int) ((value >>> 8) & 0xFFL);
	}

	private static int statWis(long value) {
		return (int) ((value >>> 16) & 0xFFL);
	}

	private static int statDex(long value) {
		return (int) ((value >>> 24) & 0xFFL);
	}

	private static int statCon(long value) {
		return (int) ((value >>> 32) & 0xFFL);
	}

	private static int statCha(long value) {
		return (int) ((value >>> 40) & 0xFFL);
	}

	// =========================================================
	// INIT HP / MP (클래스별 초기 수치)
	// =========================================================

	private static short getInitHp(L1PcInstance pc) {
        return CPCParamDB.initialHp(pc.getType());
    }

	private static short getInitMp(L1PcInstance pc, int baseWis) {
        return CPCParamDB.initialMp(pc.getType(), baseWis);
    }

	// =========================================================
	// 유틸리티 (MISC)
	// =========================================================

	private static void kickStatBug(CUserSocket client, L1PcInstance pc) {
		kick(client);
		System.out.println("▶ 스텟버그 추방 : " + safeName(pc));
	}

	private static void kick(CUserSocket client) {
		try {
			client.kick();
		} catch (Throwable ignored) {
		}
	}

    private static void saveCharacterState(L1PcInstance pc) {
        try {
            pc.save();
        } catch (RuntimeException failure) {
            Logger.getLogger(CS_ReturnStatus.class.getName())
                    .log(Level.SEVERE, "Stat reset save admission failed", failure);
            SC_SystemMessage.send(pc, "능력치 저장에 실패했습니다. 다시 접속하기 전에 관리자에게 문의해 주세요.");
        }
    }

	private static String safeName(L1PcInstance pc) {
		try {
			return pc != null ? pc.getName() : "null";
		} catch (Throwable ignored) {
			return "unknown";
		}
	}
}
