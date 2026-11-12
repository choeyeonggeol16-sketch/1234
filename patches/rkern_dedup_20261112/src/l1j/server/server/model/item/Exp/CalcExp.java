package l1j.server.server.model.item.Exp;

import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.Arrays;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.MJCompanion.CompanionSettings;
import l1j.server.server.GameServerSetting;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.Packet.SC_WorldPutObjectNoti;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.templates.L1Pet;
import l1j.server.server.utils.IntRange;

public class CalcExp {

	private static Logger _log = Logger.getLogger(CalcExp.class.getName());
	public static final int MAX_EXP = ExpTable.getExpByLevel(100) - 1;

	private CalcExp() {}

	// =========================================================
	// AddExp (실 지급)
	// =========================================================
	public static void AddExp(L1PcInstance pc, int exp, int lawful) {
		if (pc == null) return;
		if (Config.STANDBY_SERVER) return;
		if (pc.isGhost()) return;
		if (exp <= 0) return;

		final int addLawful = (int) ((long) lawful * (long) Config.RATE_LAWFUL) * -1;
		pc.addLawful(addLawful);

		final int add_exp = ExpGainCalculator.calcAddExpAndApplySideEffects(pc, exp);
		if (add_exp <= 0) return;

		if (pc.get_companion() != null) {
			pc.get_companion().update_exp((int) (add_exp * CompanionSettings.EXP_BY_MASTER_EXP));
		}

		pc.addExp(add_exp);
		pc.addMonsterKill(1);
	}

	private static void AddExpPet(L1PetInstance pet, int exp) {
		if (pet == null) return;
		if (exp <= 0) return;

		if (!(pet.getMaster() instanceof L1PcInstance)) return;
		final L1PcInstance pc = (L1PcInstance) pet.getMaster();

		final int petItemObjId = pet.getItemObjId();
		final int levelBefore = pet.getLevel();

		int totalExp = (int) (exp * 50L + pet.getExp());
		final int cap = ExpTable.getExpByLevel(51) - 1;
		if (totalExp > cap) totalExp = cap;

		pet.setExp(totalExp);
		pet.setLevel(ExpTable.getLevelByExp(totalExp));

		final int expPercentage = ExpTable.getExpPercentage(pet.getLevel(), totalExp);
		final int gap = pet.getLevel() - levelBefore;

		for (int i = 1; i <= gap; i++) {
			final IntRange hpUpRange = pet.getPetType().getHpUpRange();
			final IntRange mpUpRange = pet.getPetType().getMpUpRange();
			pet.addMaxHp(hpUpRange.randomValue());
			pet.addMaxMp(mpUpRange.randomValue());
		}

		pet.setExpPercent(expPercentage);

		byte[] data = SC_WorldPutObjectNoti.newInstance(pet).encode();
		pc.broadcastPacket(data);

		if (gap != 0) {
			SC_SkillSound.send(pc, pet.getId(), 6353);
			SC_SkillSound.broadcast(pc, pet.getId(), 6353);

			final L1Pet petTemplate = PetTable.getInstance().getTemplate(petItemObjId);
			if (petTemplate == null) {
				_log.warning("L1Pet == null");
				return;
			}
			petTemplate.set_exp(pet.getExp());
			petTemplate.set_level(pet.getLevel());
			petTemplate.set_hp(pet.getMaxHp());
			petTemplate.set_mp(pet.getMaxMp());
			PetTable.getInstance().storePet(petTemplate);

			SC_ServerMessage.send(pc, 320, pet.getName());
		}
	}

	/**
	 * 클라우디아 경험치 지급용
	 * @param pc
	 * @param exp
	 */
	public static void AddExp(L1PcInstance pc, int exp) {
		if (pc == null) return;
		if (Config.STANDBY_SERVER) return;
		if (pc.getLevel() > GameServerSetting.getInstance().get_maxLevel()) return;
		if (pc.isDead()) return;
		if (exp <= 0) return;

		final int add_exp = exp;
		final int max = ExpTable.getExpByLevel(GameServerSetting.getInstance().get_maxLevel() + 1);
        if ((long) max <= (long) pc.getExp() + add_exp) {
            pc.setExp(max - 1);
            pc.onChangeExp();
        } else {
            // addExp already publishes the level/stat transition.
            pc.addExp(add_exp);
        }
	}

	// =========================================================
	// ThreadLocal Work
	// =========================================================
	private static final ThreadLocal<Work> TL = ThreadLocal.withInitial(Work::new);

	private static final class Work {
		int busy; // 재진입 방어

		// 파티인원수를 늘릴수 있으니 바구니를 넉넉히 잡아줌
		int initialSize = (Config.MAX_PT > 0) ? (Config.MAX_PT * 2) : 16; 

		// 분배용 재사용 배열
		L1Character[] acq = new L1Character[initialSize];
		int[] hate = new int[initialSize];

		// 파티 화면내 멤버 재사용 배열
		L1PcInstance[] inScreen = new L1PcInstance[initialSize];
		int[] inScreenLv = new int[initialSize];
		long[] inScreenLvSq = new long[initialSize];

		// ---- 파티 분배 최적화용 캐시 ----
		int[] ownerIdxOfAcq = new int[initialSize];
		long[] ownedHateSum = new long[initialSize];
		int[] memberExpArr = new int[initialSize];
		int[] memberLawfulArr = new int[initialSize];

		final PartyScreenInfo psi = new PartyScreenInfo();

		void ensureAcqCap(int n) {
			if (acq.length >= n) return;
			int cap = acq.length;
			while (cap < n) cap <<= 1;
			acq = new L1Character[cap];
			hate = new int[cap];
			ownerIdxOfAcq = new int[cap];
		}

		void ensureScreenCap(int n) {
			if (inScreen.length >= n) return;
			int cap = inScreen.length;
			while (cap < n) cap <<= 1;
			inScreen = new L1PcInstance[cap];
			inScreenLv = new int[cap];
			inScreenLvSq = new long[cap];

			ownedHateSum = new long[cap];
			memberExpArr = new int[cap];
			memberLawfulArr = new int[cap];
		}

		void clearAcq() {
			Arrays.fill(acq, null);
		}

		void clearScreen() {
			Arrays.fill(inScreen, null);
		}

		void clearPartyCaches(int k) {
			for (int i = 0; i < k; i++) {
				ownedHateSum[i] = 0L;
				memberExpArr[i] = 0;
				memberLawfulArr[i] = 0;
			}
		}
	}
	// =========================================================
	// calcExp (분배)
	// =========================================================
	public static void calcExp(L1PcInstance killerPc, int targetid, ArrayList<?> acquisitorList, ArrayList<?> hateList, int baseExp) {
		if (killerPc == null) return;
		if (baseExp <= 0) return;
		if (acquisitorList == null || hateList == null) return;

		final int n = hateList.size();
		if (n <= 0) return;
		if (acquisitorList.size() != n) return;

		final L1Object obj = L1World.getInstance().findObject(targetid);
		if (!(obj instanceof L1NpcInstance)) return;
		final L1NpcInstance npc = (L1NpcInstance) obj;

		if (npc instanceof L1PetInstance || npc instanceof L1SummonInstance) return;

		// 저레벨 즉시 지급
		if (killerPc.getLevel() < 10) {
			final int lawful = npc.getLawful();
			final int addLawful = (int) ((long) lawful * (long) Config.RATE_LAWFUL) * -1;
			killerPc.addLawful(addLawful);

			final double penalty = ExpTable.getPenaltyRate(killerPc.getLevel());
			int addExp = (int) (baseExp * penalty * Config.RATE_XP_CLAUDIA);
			if (killerPc.getLevel() < 5) addExp *= 5;
			if (addExp > 0) killerPc.addExp(addExp);
			return;
		}

		final Work w = TL.get();

		// 재진입 감지 -> 세이프 경로
		if (w.busy++ != 0) {
			w.busy--;
			calcExpSafePath(killerPc, npc, acquisitorList, hateList, baseExp);
			return;
		}

		try {
			w.ensureAcqCap(n);

			final L1Character[] acq = w.acq;
			final int[] hate = w.hate;
			final int[] ownerIdxOfAcq = w.ownerIdxOfAcq;

			int m = 0;
			long totalHateExp = 0L;
			long totalHateLawful = 0L;

			for (int i = 0; i < n; i++) {
				final Object aObj = acquisitorList.get(i);
				final Object hObj = hateList.get(i);

				if (!(aObj instanceof L1Character)) continue;
				if (!(hObj instanceof Integer)) continue;

				L1Character cha = (L1Character) aObj;
				final int ht = (Integer) hObj;
				if (ht <= 0) continue;

				if (cha instanceof L1CompanionInstance) {
					final L1PcInstance master = ((L1CompanionInstance) cha).get_master();
					if (master != null) cha = master;
				}

				if (cha == null || cha.isDead()) continue;

				acq[m] = cha;
				hate[m] = ht;
				ownerIdxOfAcq[m] = -1; // 기본값
				m++;

				totalHateExp += (long) ht;
				if (cha instanceof L1PcInstance) totalHateLawful += (long) ht;
			}

			if (m == 0 || totalHateExp <= 0L) {
				return;
			}

			if (!L1World.getInstance().isProcessingContributionTotal() && killerPc.getHomeTownId() > 0) {
				final int contribution = npc.getLevel() / 10;
				killerPc.addContribution(contribution);
			}

			final int lawful = npc.getLawful();

			// 파티 분배
			final boolean isParty = killerPc.isInParty() && killerPc.getParty() != null;
			if (isParty) {
				final L1PcInstance[] members = killerPc.getParty().getMembers();
				if (members != null && members.length > 0) {
					final PartyScreenInfo psi = PartyScreenInfo.buildInto(killerPc, members, w);
					if (psi.inScreenCount > 1) {
						distributePartyOptimized(w, psi, acq, hate, ownerIdxOfAcq, m, baseExp, lawful, totalHateExp, totalHateLawful);
						return;
					}
				}
			}

			distributeSolo(acq, hate, m, baseExp, lawful, totalHateExp, totalHateLawful);

		} finally {
			w.busy--;
			w.clearAcq();   
			w.clearScreen(); 
		}
	}

	// =========================================================
	// 세이프 경로 (재진입/이상 호출 시)
	// =========================================================
	private static void calcExpSafePath(L1PcInstance killerPc, L1NpcInstance npc, ArrayList<?> acquisitorList, ArrayList<?> hateList, int baseExp) {
		try {
			final int n = hateList.size();
			if (n <= 0) return;

			final L1Character[] acq = new L1Character[n];
			final int[] hate = new int[n];

			int m = 0;
			long totalHateExp = 0L;
			long totalHateLawful = 0L;

			for (int i = 0; i < n; i++) {
				final Object aObj = acquisitorList.get(i);
				final Object hObj = hateList.get(i);

				if (!(aObj instanceof L1Character)) continue;
				if (!(hObj instanceof Integer)) continue;

				L1Character cha = (L1Character) aObj;
				final int ht = (Integer) hObj;
				if (ht <= 0) continue;

				if (cha instanceof L1CompanionInstance) {
					final L1PcInstance master = ((L1CompanionInstance) cha).get_master();
					if (master != null) cha = master;
				}

				if (cha == null || cha.isDead()) continue;

				acq[m] = cha;
				hate[m] = ht;
				m++;

				totalHateExp += (long) ht;
				if (cha instanceof L1PcInstance) totalHateLawful += (long) ht;
			}

			if (m == 0 || totalHateExp <= 0L) return;

			if (!L1World.getInstance().isProcessingContributionTotal() && killerPc.getHomeTownId() > 0) {
				final int contribution = npc.getLevel() / 10;
				killerPc.addContribution(contribution);
			}

			final int lawful = npc.getLawful();

			final boolean isParty = killerPc.isInParty() && killerPc.getParty() != null;
			if (isParty) {
				final L1PcInstance[] members = killerPc.getParty().getMembers();
				if (members != null && members.length > 0) {
					PartyScreenInfo psi = PartyScreenInfo.buildLocal(killerPc, members);
					if (psi.inScreenCount > 1) {
						distributePartySafeLocal(psi, acq, hate, m, baseExp, lawful, totalHateExp, totalHateLawful);
						return;
					}
				}
			}

			distributeSolo(acq, hate, m, baseExp, lawful, totalHateExp, totalHateLawful);

		} catch (Throwable t) {
			_log.warning("CalcExp safePath error: " + t.getMessage());
		}
	}
	// =========================================================
	// Solo 분배
	// =========================================================
	private static void distributeSolo(L1Character[] acq, int[] hate, int m, int exp, int lawful, long totalHateExp, long totalHateLawful) {
		for (int i = 0; i < m; i++) {
			final L1Character a = acq[i];
			final int ht = hate[i];

			final int acquireExp = (totalHateExp > 0L)
					? (int) (((long) exp * (long) ht) / totalHateExp)
							: 0;

					int acquireLawful = 0;

					if (a instanceof L1PcInstance) {
						if (totalHateLawful > 0L) {
							acquireLawful = (int) (((long) lawful * (long) ht) / totalHateLawful);
						}
						AddExp((L1PcInstance) a, acquireExp, acquireLawful);

					} else if (a instanceof L1PetInstance) {
						AddExpPet((L1PetInstance) a, acquireExp);

					} else if (a instanceof L1SummonInstance) {
						final L1SummonInstance s = (L1SummonInstance) a;
						if (s.getNpcTemplate() != null && s.getNpcTemplate().get_npcId() == 7320000) {
							if (s.getMaster() instanceof L1PcInstance) {
								if (totalHateLawful > 0L) {
									acquireLawful = (int) (((long) lawful * (long) ht) / totalHateLawful);
								}
								AddExp((L1PcInstance) s.getMaster(), acquireExp, acquireLawful);
							}
						}
					}
		}
	}
	// =========================================================
	// Party 분배
	// =========================================================
	private static void distributePartyOptimized(
			Work w,
			PartyScreenInfo psi,
			L1Character[] acq,
			int[] hate,
			int[] ownerIdxOfAcq,
			int m,
			int exp,
			int lawful,
			long totalHateExp,
			long totalHateLawful) {

		final int k = psi.inScreenCount;
		w.ensureScreenCap(k);
		w.clearPartyCaches(k);

		long partyHateExp = 0L;
		long partyHateLawful = 0L;

		// 1) 파티원/비파티원 분리 + partyHate 계산
		for (int i = 0; i < m; i++) {
			final L1Character a = acq[i];
			final int ht = hate[i];

			// ownerPc 추출 (PC / Pet.master / Summon.master)
			final L1PcInstance ownerPc = getOwnerPcOrNull(a);

			if (a instanceof L1PcInstance) {
				final L1PcInstance pc = (L1PcInstance) a;
				if (psi.isPartyMember(pc)) {
					partyHateExp += (long) ht;
					partyHateLawful += (long) ht;

					// 화면 내면 소유자 idx 기록
					final int idx = psi.indexOfInScreen(pc);
					if (idx >= 0) {
						ownerIdxOfAcq[i] = idx;
						w.ownedHateSum[idx] += (long) ht;
					} else {
						ownerIdxOfAcq[i] = -1;
					}
				} else {
					ownerIdxOfAcq[i] = -1;
					final int acquireExp = (totalHateExp > 0L) ? (int) (((long) exp * (long) ht) / totalHateExp) : 0;
					final int acquireLawful = (totalHateLawful > 0L) ? (int) (((long) lawful * (long) ht) / totalHateLawful) : 0;
					AddExp(pc, acquireExp, acquireLawful);
				}

			} else if (a instanceof L1PetInstance) {
				final L1PetInstance pet = (L1PetInstance) a;
				final L1PcInstance master = ownerPc;

				if (master != null && psi.isPartyMember(master)) {
					partyHateExp += (long) ht;

					// 화면 내 소유자면 기록 (펫도 소유자 그룹에 포함)
					final int idx = psi.indexOfInScreen(master);
					if (idx >= 0) {
						ownerIdxOfAcq[i] = idx;
						w.ownedHateSum[idx] += (long) ht;
					} else {
						ownerIdxOfAcq[i] = -1;
					}
				} else {
					ownerIdxOfAcq[i] = -1;
					final int acquireExp = (totalHateExp > 0L) ? (int) (((long) exp * (long) ht) / totalHateExp) : 0;
					AddExpPet(pet, acquireExp);
				}

			} else if (a instanceof L1SummonInstance) {

				final L1PcInstance master = ownerPc;

				if (master != null && psi.isPartyMember(master)) {
					partyHateExp += (long) ht;

					// 화면 내 소유자면 기록 (서먼도 소유자 그룹에 포함)
					final int idx = psi.indexOfInScreen(master);
					if (idx >= 0) {
						ownerIdxOfAcq[i] = idx;
						w.ownedHateSum[idx] += (long) ht;
					} else {
						ownerIdxOfAcq[i] = -1;
					}
				} else {
					ownerIdxOfAcq[i] = -1;
				}

			} else {
				ownerIdxOfAcq[i] = -1;
			}
		}

		final int partyExp = (totalHateExp > 0L) ? (int) (((long) exp * partyHateExp) / totalHateExp) : 0;
		final int partyLawful = (totalHateLawful > 0L) ? (int) (((long) lawful * partyHateLawful) / totalHateLawful) : 0;

		int adjPartyExp = (int) (partyExp * (0.5 + psi.ptBonus + psi.priBonus));
		if (Config.IS_PARTY_EXP) {
			adjPartyExp *= (int) Config.ADD_PARTY_EXP;
		}

		for (int idx = 0; idx < k; idx++) {
			final double dist = (psi.partyLevelSqSum > 0L)
					? ((double) psi.inScreenLvSq[idx] / (double) psi.partyLevelSqSum)
							: 0.0;

					w.memberExpArr[idx] = (int) (adjPartyExp * dist);
					w.memberLawfulArr[idx] = (int) (partyLawful * dist);
		}

		for (int i = 0; i < m; i++) {
			final int ownerIdx = ownerIdxOfAcq[i];
			if (ownerIdx < 0) continue;

			final long denom = w.ownedHateSum[ownerIdx];
			if (denom <= 0L) continue;

			final int ht = hate[i];
			final int memberExp = w.memberExpArr[ownerIdx];
			if (memberExp <= 0) continue;

			final int acquireExp = (int) (((long) memberExp * (long) ht) / denom);
			if (acquireExp <= 0) continue;

			final int memberLawful = w.memberLawfulArr[ownerIdx];
			final L1Character a = acq[i];

			if (a instanceof L1PcInstance) {
				AddExp((L1PcInstance) a, acquireExp, memberLawful);

			} else if (a instanceof L1PetInstance) {
				AddExpPet((L1PetInstance) a, acquireExp);

			} else if (a instanceof L1SummonInstance) {
				final L1SummonInstance s = (L1SummonInstance) a;
				if (s.getNpcTemplate() != null && s.getNpcTemplate().get_npcId() == 7320000) {
					final L1PcInstance ownerPc = psi.inScreenMembers[ownerIdx];
					if (ownerPc != null) {
						AddExp(ownerPc, acquireExp, memberLawful);
					}
				}
			}
		}

		for (int idx = 0; idx < k; idx++) {
			if (w.ownedHateSum[idx] > 0L) continue;

			final L1PcInstance member = psi.inScreenMembers[idx];
			if (member == null) continue;

			final int memberExp = w.memberExpArr[idx];
			final int memberLawful = w.memberLawfulArr[idx];

			AddExp(member, memberExp, memberLawful);
		}
	}

	// safePath용
	private static void distributePartySafeLocal(
			PartyScreenInfo psi,
			L1Character[] acq,
			int[] hate,
			int m,
			int exp,
			int lawful,
			long totalHateExp,
			long totalHateLawful) {

		final int k = psi.inScreenCount;
		final int[] ownerIdxOfAcq = new int[m];
		final long[] ownedHateSum = new long[k];
		final int[] memberExpArr = new int[k];
		final int[] memberLawfulArr = new int[k];

		long partyHateExp = 0L;
		long partyHateLawful = 0L;

		for (int i = 0; i < m; i++) {
			ownerIdxOfAcq[i] = -1;

			final L1Character a = acq[i];
			final int ht = hate[i];

			final L1PcInstance ownerPc = getOwnerPcOrNull(a);

			if (a instanceof L1PcInstance) {
				final L1PcInstance pc = (L1PcInstance) a;
				if (psi.isPartyMember(pc)) {
					partyHateExp += (long) ht;
					partyHateLawful += (long) ht;

					final int idx = psi.indexOfInScreen(pc);
					if (idx >= 0) {
						ownerIdxOfAcq[i] = idx;
						ownedHateSum[idx] += (long) ht;
					}
				} else {
					final int acquireExp = (totalHateExp > 0L) ? (int) (((long) exp * (long) ht) / totalHateExp) : 0;
					final int acquireLawful = (totalHateLawful > 0L) ? (int) (((long) lawful * (long) ht) / totalHateLawful) : 0;
					AddExp(pc, acquireExp, acquireLawful);
				}

			} else if (a instanceof L1PetInstance) {
				final L1PetInstance pet = (L1PetInstance) a;
				final L1PcInstance master = ownerPc;

				if (master != null && psi.isPartyMember(master)) {
					partyHateExp += (long) ht;

					final int idx = psi.indexOfInScreen(master);
					if (idx >= 0) {
						ownerIdxOfAcq[i] = idx;
						ownedHateSum[idx] += (long) ht;
					}
				} else {
					final int acquireExp = (totalHateExp > 0L) ? (int) (((long) exp * (long) ht) / totalHateExp) : 0;
					AddExpPet(pet, acquireExp);
				}

			} else if (a instanceof L1SummonInstance) {

				final L1PcInstance master = ownerPc;

				if (master != null && psi.isPartyMember(master)) {
					partyHateExp += (long) ht;

					final int idx = psi.indexOfInScreen(master);
					if (idx >= 0) {
						ownerIdxOfAcq[i] = idx;
						ownedHateSum[idx] += (long) ht;
					}
				}
			}
		}

		final int partyExp = (totalHateExp > 0L) ? (int) (((long) exp * partyHateExp) / totalHateExp) : 0;
		final int partyLawful = (totalHateLawful > 0L) ? (int) (((long) lawful * partyHateLawful) / totalHateLawful) : 0;

		int adjPartyExp = (int) (partyExp * (0.5 + psi.ptBonus + psi.priBonus));
		if (Config.IS_PARTY_EXP) {
			adjPartyExp *= (int) Config.ADD_PARTY_EXP;
		}

		for (int idx = 0; idx < k; idx++) {
			final double dist = (psi.partyLevelSqSum > 0L)
					? ((double) psi.inScreenLvSq[idx] / (double) psi.partyLevelSqSum)
							: 0.0;
					memberExpArr[idx] = (int) (adjPartyExp * dist);
					memberLawfulArr[idx] = (int) (partyLawful * dist);
		}

		for (int i = 0; i < m; i++) {
			final int ownerIdx = ownerIdxOfAcq[i];
			if (ownerIdx < 0) continue;

			final long denom = ownedHateSum[ownerIdx];
			if (denom <= 0L) continue;

			final int memberExp = memberExpArr[ownerIdx];
			if (memberExp <= 0) continue;

			final int ht = hate[i];
			final int acquireExp = (int) (((long) memberExp * (long) ht) / denom);
			if (acquireExp <= 0) continue;

			final int memberLawful = memberLawfulArr[ownerIdx];
			final L1Character a = acq[i];

			if (a instanceof L1PcInstance) {
				AddExp((L1PcInstance) a, acquireExp, memberLawful);

			} else if (a instanceof L1PetInstance) {
				AddExpPet((L1PetInstance) a, acquireExp);

			} else if (a instanceof L1SummonInstance) {
				final L1SummonInstance s = (L1SummonInstance) a;
				if (s.getNpcTemplate() != null && s.getNpcTemplate().get_npcId() == 7320000) {
					final L1PcInstance ownerPc = psi.inScreenMembers[ownerIdx];
					if (ownerPc != null) {
						AddExp(ownerPc, acquireExp, memberLawful);
					}
				}
			}
		}

		for (int idx = 0; idx < k; idx++) {
			if (ownedHateSum[idx] > 0L) continue;

			final L1PcInstance member = psi.inScreenMembers[idx];
			if (member == null) continue;

			AddExp(member, memberExpArr[idx], memberLawfulArr[idx]);
		}
	}

	private static L1PcInstance getOwnerPcOrNull(L1Character a) {
		if (a instanceof L1PcInstance) return (L1PcInstance) a;

		if (a instanceof L1PetInstance) {
			final L1PetInstance p = (L1PetInstance) a;
			return (p.getMaster() instanceof L1PcInstance) ? (L1PcInstance) p.getMaster() : null;
		}
		if (a instanceof L1SummonInstance) {
			final L1SummonInstance s = (L1SummonInstance) a;
			return (s.getMaster() instanceof L1PcInstance) ? (L1PcInstance) s.getMaster() : null;
		}
		return null;
	}
	// =========================================================
	// PartyScreenInfo
	// =========================================================
	private static final class PartyScreenInfo {
		L1PcInstance[] inScreenMembers;
		int inScreenCount;

		L1PcInstance[] partyMembersRef;

		long partyLevelSqSum;
		double ptBonus;
		double priBonus;

		int[] inScreenLv;
		long[] inScreenLvSq;

		void reset(L1PcInstance[] inScreenArr, int[] lvArr, long[] lvSqArr) {
			inScreenMembers = inScreenArr;
			inScreenLv = lvArr;
			inScreenLvSq = lvSqArr;
			inScreenCount = 0;
			partyMembersRef = null;
			partyLevelSqSum = 0L;
			ptBonus = 0.0;
			priBonus = 0.0;
		}

		static PartyScreenInfo buildInto(L1PcInstance viewer, L1PcInstance[] members, Work w) {
			w.ensureScreenCap(members.length);

			final PartyScreenInfo psi = w.psi;
			psi.reset(w.inScreen, w.inScreenLv, w.inScreenLvSq);
			psi.partyMembersRef = members;

			int count = 0;
			long sumSq = 0L;
			double ptBonus = 0.0;

			for (int i = 0; i < members.length; i++) {
				final L1PcInstance each = members[i];
				if (each == null) continue;

				if (viewer == each || viewer.knownsObject(each)) {
					psi.inScreenMembers[count] = each;

					final int lv = each.getLevel();
					psi.inScreenLv[count] = lv;

					final long lvSq = (long) lv * (long) lv;
					psi.inScreenLvSq[count] = lvSq;

					sumSq += lvSq;
					ptBonus += 0.04;
					count++;
				}
			}

			double priBonus = 0.0;
			try {
				final L1PcInstance leader = (viewer.getParty() != null) ? viewer.getParty().getLeader() : null;
				if (leader != null && leader.isCrown() && (viewer == leader || viewer.knownsObject(leader))) {
					priBonus = 0.059;
				}
			} catch (Throwable t) { }

			psi.inScreenCount = count;
			psi.partyLevelSqSum = sumSq;
			psi.ptBonus = ptBonus;
			psi.priBonus = priBonus;

			return psi;
		}

		static PartyScreenInfo buildLocal(L1PcInstance viewer, L1PcInstance[] members) {
			PartyScreenInfo psi = new PartyScreenInfo();
			psi.inScreenMembers = new L1PcInstance[members.length];
			psi.inScreenLv = new int[members.length];
			psi.inScreenLvSq = new long[members.length];
			psi.partyMembersRef = members;

			int count = 0;
			long sumSq = 0L;
			double ptBonus = 0.0;

			for (int i = 0; i < members.length; i++) {
				final L1PcInstance each = members[i];
				if (each == null) continue;

				if (viewer == each || viewer.knownsObject(each)) {
					psi.inScreenMembers[count] = each;

					final int lv = each.getLevel();
					psi.inScreenLv[count] = lv;

					final long lvSq = (long) lv * (long) lv;
					psi.inScreenLvSq[count] = lvSq;

					sumSq += lvSq;
					ptBonus += 0.04;
					count++;
				}
			}

			double priBonus = 0.0;
			try {
				final L1PcInstance leader = (viewer.getParty() != null) ? viewer.getParty().getLeader() : null;
				if (leader != null && leader.isCrown() && (viewer == leader || viewer.knownsObject(leader))) {
					priBonus = 0.059;
				}
			} catch (Throwable t) { }

			psi.inScreenCount = count;
			psi.partyLevelSqSum = sumSq;
			psi.ptBonus = ptBonus;
			psi.priBonus = priBonus;

			return psi;
		}

		boolean isPartyMember(L1PcInstance pc) {
			if (pc == null) return false;

			for (int i = 0; i < inScreenCount; i++) {
				if (inScreenMembers[i] == pc) return true;
			}
			final L1PcInstance[] members = partyMembersRef;
			if (members != null) {
				for (int i = 0; i < members.length; i++) {
					if (members[i] == pc) return true;
				}
			}
			return false;
		}

		int indexOfInScreen(L1PcInstance pc) {
			if (pc == null) return -1;
			for (int i = 0; i < inScreenCount; i++) {
				if (inScreenMembers[i] == pc) return i;
			}
			return -1;
		}
	}

}