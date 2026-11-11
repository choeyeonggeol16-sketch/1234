package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.ThreadPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.L1ReturnStatTemp;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.CIOClient;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;

import l1j.server.server.utils.CalcStat;
import l1j.server.server.utils.CheckInitStat;

public final class CS_ReturnStatus {

	private static final int BASE_STAT_SUM_MAX = 80;

	private static final int BONUS_LEVEL_START = 51;
	private static final int BONUS_LEVEL_BASE = 50;

	private static final int STAT_STR = 1;
	private static final int STAT_INT = 2;
	private static final int STAT_WIS = 3;
	private static final int STAT_DEX = 4;
	private static final int STAT_CON = 5;
	private static final int STAT_CHA = 6;

	private CS_ReturnStatus() {}

	public static void handle(CIOClient client, int type, int levelup, int statusup, long packedStats) {

		if (client == null) {
			return;
		}

		final L1PcInstance pc = client.getActiveChar();

		if (pc == null || pc.getReturnStat() == 0) {
			return;
		}

		try {
			switch (type) {
			case 1:
				handleType1Start(client, pc, packedStats);
				return;

			case 2:
				handleType2LevelUp(client, pc, levelup, statusup);
				return;

			case 3:
				handleType3Elixir(client, pc, packedStats);
				return;

			default:
				return;
			}

		} catch (Throwable ignored) {
			/*
			 * 기존 C_ReturnStatus의 예외 무시(semantics) 기조를 그대로 유지합니다.
			 */
		}
	}

	// =========================================================
	// TYPE 1: 스탯 초기화 시작
	// =========================================================

	private static void handleType1Start(CIOClient client, L1PcInstance pc, long packedStats) {

		final int str = statStr(packedStats);
		final int intel = statInt(packedStats);
		final int wis = statWis(packedStats);
		final int dex = statDex(packedStats);
		final int con = statCon(packedStats);
		final int cha = statCha(packedStats);

		if (isInvalidBaseStats(str, dex, con, wis, intel, cha)) {
			kickStatBug(client, pc);
			return;
		}

		final L1ReturnStatTemp rst = new L1ReturnStatTemp();
		pc.rst = rst;

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

		pc.getAbility().init();

		pc.getAbility().setBaseStr(str);
		pc.getAbility().setBaseInt(intel);
		pc.getAbility().setBaseWis(wis);
		pc.getAbility().setBaseDex(dex);
		pc.getAbility().setBaseCon(con);
		pc.getAbility().setBaseCha(cha);

		rst.baseHp = getInitHp(pc);
		rst.baseMp = getInitMp(pc, rst.basewis);

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

	private static void handleType2LevelUp(CIOClient client, L1PcInstance pc, int levelup, int statusup) {

		if (pc.rst == null) {
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
			if (!incStatOrKick(client, pc, levelup)) {
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

	private static void handleBonusAndMaybeFinish(CIOClient client, L1PcInstance pc, int statusup) {

		if (pc.rst == null) {
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
				if (!incStatOrKick(client, pc, statusup)) {
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
			SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.END);
			return;
		}

		applyFinalAndFinish(client, pc);
	}

	// =========================================================
	// TYPE 3: 엘릭서 사용
	// =========================================================

	private static void handleType3Elixir(CIOClient client, L1PcInstance pc, long packedStats) {

		if (pc.rst == null) {
			return;
		}

		final int str = statStr(packedStats);
		final int intel = statInt(packedStats);
		final int wis = statWis(packedStats);
		final int dex = statDex(packedStats);
		final int con = statCon(packedStats);
		final int cha = statCha(packedStats);

		if (str <= 0 || dex <= 0 || con <= 0 || wis <= 0 || intel <= 0 || cha <= 0) {
			kickStatBug(client, pc);
			return;
		}

		final int addStr = str - pc.getAbility().getStr();
		final int addInt = intel - pc.getAbility().getInt();
		final int addWis = wis - pc.getAbility().getWis();
		final int addDex = dex - pc.getAbility().getDex();
		final int addCon = con - pc.getAbility().getCon();
		final int addCha = cha - pc.getAbility().getCha();

		pc.getAbility().addStr((byte) addStr);
		pc.getAbility().addInt((byte) addInt);
		pc.getAbility().addWis((byte) addWis);
		pc.getAbility().addDex((byte) addDex);
		pc.getAbility().addCon((byte) addCon);
		pc.getAbility().addCha((byte) addCha);

		if (pc.getLevel() >= BONUS_LEVEL_START) {
			pc.setBonusStats(pc.getLevel() - BONUS_LEVEL_BASE);
		} else {
			pc.setBonusStats(0);
		}

		pc.resetBaseAc();
		pc.getAC().setAc(pc.getBaseAc());

		pc.setExp(pc.getReturnStat());

		SC_OwnCharStatus.send(pc);
		SC_OwnCharAttrDef.send(pc);
		SC_OwnCharStatus2.send(pc);
		SC_SPMR.send(pc);

		pc.addBaseMaxHp((short) (pc.rst.baseHp - pc.getBaseMaxHp()));
		pc.addBaseMaxMp((short) (pc.rst.baseMp - pc.getBaseMaxMp()));

		pc.addBaseMaxHp((short) pc.rst.upHp);
		pc.addBaseMaxMp((short) pc.rst.upMp);

		pc.setCurrentHp(pc.getMaxHp());
		pc.setCurrentMp(pc.getMaxMp());

		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());

		pc.start_teleport(33430, 32810, 4, pc.getHeading(), 169, true, false);

		pc.CheckStatus();

		if (!CheckInitStat.CheckPcStat(pc)) {
			kick(client);
			return;
		}

		pc.completeStatResetGrowth(pc.rst.hpMpGrowth);
		pc.setReturnStat(0);
		saveAsync(pc);
		pc.rst = null;
	}

	// =========================================================
	// FINAL: 최종 스탯 반영 및 마무리
	// =========================================================

	private static void applyFinalAndFinish(CIOClient client, L1PcInstance pc) {

		try {
			if (pc.rst.level >= BONUS_LEVEL_START) {
				pc.setBonusStats(pc.rst.level - BONUS_LEVEL_BASE);
			} else {
				pc.setBonusStats(0);
			}

			pc.setExp(pc.getReturnStat());

			pc.addBaseMaxHp((short) (pc.rst.baseHp - pc.getBaseMaxHp()));
			pc.addBaseMaxMp((short) (pc.rst.baseMp - pc.getBaseMaxMp()));

			pc.addBaseMaxHp((short) pc.rst.upHp);
			pc.addBaseMaxMp((short) pc.rst.upMp);

			pc.setCurrentHp(pc.getMaxHp());
			pc.setCurrentMp(pc.getMaxMp());

			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());

			pc.start_teleport(33430, 32810, 4, pc.getHeading(), 169, true, false);

			pc.getAbility().init();
			pc.getAbility().setBaseStr(pc.rst.basestr);
			pc.getAbility().setBaseInt(pc.rst.baseint);
			pc.getAbility().setBaseWis(pc.rst.basewis);
			pc.getAbility().setBaseDex(pc.rst.basedex);
			pc.getAbility().setBaseCon(pc.rst.basecon);
			pc.getAbility().setBaseCha(pc.rst.basecha);

			pc.getAbility().addStr((byte) pc.rst.str);
			pc.getAbility().addInt((byte) pc.rst.Int);
			pc.getAbility().addWis((byte) pc.rst.wis);
			pc.getAbility().addDex((byte) pc.rst.dex);
			pc.getAbility().addCon((byte) pc.rst.con);
			pc.getAbility().addCha((byte) pc.rst.cha);

			pc.resetBaseAc();
			pc.getAC().setAc(pc.getBaseAc());

			SC_SPMR.send(pc);
			SC_OwnCharStatus2.send(pc);
			SC_OwnCharStatus.send(pc);
			SC_OwnCharAttrDef.send(pc);

			SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.END);

			pc.RenewStat();
			pc.CheckStatus();

			if (!CheckInitStat.CheckPcStat(pc)) {
				kick(client);
				return;
			}

			pc.completeStatResetGrowth(pc.rst.hpMpGrowth);
			pc.setReturnStat(0);
			saveAsync(pc);
			pc.rst = null;

		} catch (Throwable ignored) {
			/* 기존 semantics 유지 */
		}
	}

	// =========================================================
	// STAT 로직
	// =========================================================

	private static boolean incStatOrKick(CIOClient client, L1PcInstance pc, int type) {

		final int value;

		switch (type) {
		case STAT_STR:
			value = ++pc.rst.str;
			break;
		case STAT_INT:
			value = ++pc.rst.Int;
			break;
		case STAT_WIS:
			value = ++pc.rst.wis;
			break;
		case STAT_DEX:
			value = ++pc.rst.dex;
			break;
		case STAT_CON:
			value = ++pc.rst.con;
			break;
		case STAT_CHA:
			value = ++pc.rst.cha;
			break;
		default:
			return false;
		}

		if (!checking(pc, value)) {
			return true;
		}

		System.out.print(String.format("%s 스텟 오버 감지.. %s : %d", safeName(pc), statName(type), value));
		kick(client);
		return false;
	}

	private static boolean checking(L1PcInstance pc, int stat) {
		final int maximum = pc.getHighLevel() >= 90 ? 50 : 45;
		return stat > maximum || stat < 1;
	}

	private static void statup(L1PcInstance pc) {

		pc.rst.level++;

		final int hp = CalcStat.increaseHp(pc.getType(), (byte) (pc.rst.con + pc.rst.basecon));
		final int mp = CalcStat.increaseMp(pc.getType(), (byte) (pc.rst.wis + pc.rst.basewis));

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
        return CalcStat.initialHp(pc.getType());
    }

	private static short getInitMp(L1PcInstance pc, int baseWis) {
        return CalcStat.initialMp(pc.getType(), baseWis);
    }

	// =========================================================
	// 유틸리티 (MISC)
	// =========================================================

	private static void kickStatBug(CIOClient client, L1PcInstance pc) {
		kick(client);
		System.out.println("▶ 스텟버그 추방 : " + safeName(pc));
	}

	private static void kick(CIOClient client) {
		try {
			client.kick();
		} catch (Throwable ignored) {
		}
	}

    private static void saveAsync(L1PcInstance pc) {
        try {
            ThreadPool.getInstance().execute(() -> {
                try {
                    pc.save();
                } catch (Exception e) {
                    Logger.getLogger(CS_ReturnStatus.class.getName())
                            .log(Level.SEVERE, "Stat reset save failed", e);
                    SC_SystemMessage.send(pc, "능력치 저장에 실패했습니다. 다시 접속하기 전에 관리자에게 문의해 주세요.");
                }
            });
        } catch (RejectedExecutionException rejected) {
            // The stat reset has already changed the character. Preserve the
            // essential immutable character snapshot without executing JDBC
            // on the NIO.2 completion worker. Auxiliary autoset persistence
            // remains on the normal background save path.
            try {
                CacheD.getInstance().saveCharacter(new CharacterSnapshot(pc));
            } catch (RuntimeException failure) {
                Logger.getLogger(CS_ReturnStatus.class.getName())
                        .log(Level.SEVERE, "Stat reset CacheD fallback admission failed", failure);
                SC_SystemMessage.send(pc, "능력치 저장에 실패했습니다. 재접속 전에 관리자에게 문의해 주세요.");
            }
        }
    }

	private static String statName(int type) {
		switch (type) {
		case STAT_STR: return "STR";
		case STAT_INT: return "INT";
		case STAT_WIS: return "WIS";
		case STAT_DEX: return "DEX";
		case STAT_CON: return "CON";
		case STAT_CHA: return "CHA";
		default:       return "?";
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