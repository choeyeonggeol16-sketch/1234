/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */

package l1j.server.server.clientpackets;

import l1j.server.server.GameClient;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.L1StatReset;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_HPUpdate;
import l1j.server.server.serverpackets.S_MPUpdate;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_ReturnedStat;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.CalcUtil;
import manager.SJManager;

public class C_ReturnStaus extends ClientBasePacket {
	public C_ReturnStaus(byte[] decrypt, GameClient client) {
		super(decrypt);
		int type = readC();
		L1PcInstance pc = client.getActiveChar();

		if (pc == null || pc.getReturnStat() == 0) {
			return;
		}

		if (pc.getReturnStatus() != type) {
			return;
		}
 
		if (type == 1) {
			byte str = (byte) readC();
			byte intel = (byte) readC();
			byte wis = (byte) readC();
			byte dex = (byte) readC();
			byte con = (byte) readC();
			byte cha = (byte) readC();

			int statusAmount = str + intel + wis + dex + con + cha;

			if (str > 20 || intel > 18 || dex > 18 || wis > 18 || con > 18 || cha > 18 || statusAmount != 75) {
				pc.sendPackets(new S_SystemMessage("잘못된 베이스 스텟입니다."));
				pc.setReturnStatus(1);
				pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.START));

				return;
			}

			pc.setStatReset(new L1StatReset());

			int endLevel = ExpTable.getLevelByExp(pc.getReturnStat());

			L1StatReset sr = pc.getStatReset();
			if (sr == null) {
				return;
			}

			sr.setStr(str);
			sr.setWis(wis);
			sr.setDex(dex);
			sr.setCon(con);
			sr.setIntel(intel);
			sr.setCha(cha);

			sr.setBaseStr(str);
			sr.setBaseWis(wis);
			sr.setBaseDex(dex);
			sr.setBaseCon(con);
			sr.setBaseIntel(intel);
			sr.setBaseCha(cha);

			setBaseHpMp(pc, sr);
			sr.setAC(10);
			sr.setNowLevel(1);
			sr.setEndLevel(endLevel);

			pc.setReturnStatus(2);
			pc.sendPackets(new S_ReturnedStat(sr));
		} else if (type == 2) {
			L1StatReset sr = pc.getStatReset();

			if (sr == null) {
				return;
			}

			int levelup = readC();

			if (levelup != 8 && sr.getEndLevel() <= sr.getNowLevel()) {
				pc.sendPackets(new S_ReturnedStat(sr));
				return;
			} else if (levelup >= 0 && levelup <= 6) {
				if (levelup != 0 && sr.getNowLevel() <= 50) {
					pc.setReturnStatus(1);
					pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.START));
					return;
				}

				switch (levelup) {
				case 1:
					sr.setStr(sr.getStr() + 1);
					break;
				case 2:
					sr.setIntel(sr.getIntel() + 1);
					break;
				case 3:
					sr.setWis(sr.getWis() + 1);
					break;
				case 4:
					sr.setDex(sr.getDex() + 1);
					break;
				case 5:
					sr.setCon(sr.getCon() + 1);
					break;
				case 6:
					sr.setCha(sr.getCha() + 1);
					break;
				}

				if (!checkStatusBug(pc, sr)) {
					pc.setReturnStatus(1);
					pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.START));
					return;
				}

				statup(pc, sr);
				pc.sendPackets(new S_ReturnedStat(sr));
				return;
			} else if (levelup == 7) {
				int upCount = sr.getEndLevel() - sr.getNowLevel();

				if (sr.getNowLevel() + 10 < sr.getEndLevel()) {
					upCount = 10;
				}

				for (int m = 0; m < upCount; m++)
					statup(pc, sr);

				pc.sendPackets(new S_ReturnedStat(sr));
				return;
			} else if (levelup == 8) {
				if (sr.getEndLevel() != sr.getNowLevel()) {
					pc.sendPackets(new S_ReturnedStat(sr));
					return;
				}

				int statusup = readC();
				if (pc.getLevel() > 50) {
					switch (statusup) {
					case 1:
						sr.setStr(sr.getStr() + 1);
						break;
					case 2:
						sr.setIntel(sr.getIntel() + 1);
						break;
					case 3:
						sr.setWis(sr.getWis() + 1);
						break;
					case 4:
						sr.setDex(sr.getDex() + 1);
						break;
					case 5:
						sr.setCon(sr.getCon() + 1);
						break;
					case 6:
						sr.setCha(sr.getCha() + 1);
						break;
					}
				}
				if (pc.getElixirStats() > 0) {
					pc.setReturnStatus(3);
					pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.END)); 
					return;
				} else {
					try {
						if (!checkStatusBug(pc, sr)) {
							pc.setReturnStatus(1);
							pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.START));
							return;
						}

						setCharStatus(pc, sr);
						pc.setExp(pc.getReturnStat());
						pc.setReturnStatus(0);
						pc.setReturnStat(0);

						if (pc.getLevel() >= 51)
							pc.setBonusStats(pc.getLevel() - 50);
						else
							pc.setBonusStats(0);

						pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.END));
						pc.sendPackets(new S_OwnCharStatus(pc));
						pc.sendPackets(new S_OwnCharAttrDef(pc));
						pc.setCurrentHp(pc.getMaxHp());
						pc.setCurrentMp(pc.getMaxHp());
						pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
						pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp())); 
						pc.teleport(32612, 32734, (short) 4, pc.getHeading(),169, true, false); 
						pc.save();
						SJManager.getInstance().ReturnStausAppend("스초완료" , pc.getName());
					} catch (Exception exception) {
					}
				}
			}
		} else if (type == 3) { // 스텟 초기화시 엘릭서 처리
			try {
				L1StatReset sr = pc.getStatReset();

				if (sr == null) {
					return;
				}

				if (sr.getEndLevel() != sr.getNowLevel()) {
					pc.setReturnStatus(1);
					pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.START));
					return;
				}

				int str = readC();
				int intel = readC();
				int wis = readC();
				int dex = readC();
				int con = readC();
				int cha = readC();

				sr.setStr(str);
				sr.setIntel(intel);
				sr.setWis(wis);
				sr.setDex(dex);
				sr.setCon(con);
				sr.setCha(cha);

				if (!checkStatusBug(pc, sr)) {
					pc.setReturnStatus(1);
					pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.START));
					return;
				}

				setCharStatus(pc, sr);
				pc.setExp(pc.getReturnStat());
				pc.setReturnStatus(0);
				pc.setReturnStat(0);

				if (pc.getLevel() >= 51) {
					pc.setBonusStats(pc.getLevel() - 50);
				}
				else {
					pc.setBonusStats(0);
				}
				pc.sendPackets(new S_OwnCharStatus(pc));
				pc.sendPackets(new S_OwnCharAttrDef(pc));
				pc.setCurrentHp(pc.getMaxHp());
				pc.setCurrentMp(pc.getMaxHp());
				pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
				pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp())); 
				pc.teleport(32612, 32734, (short) 4, pc.getHeading(),169, true, false); 
				pc.save();
				SJManager.getInstance().ReturnStausAppend("스초완료" , pc.getName());
			} catch (Exception exception) {
			}
		}
	}

	public void statup(L1PcInstance pc, L1StatReset sr) {
		int Stathp = 0;
		int Statmp = 0;
		sr.setNowLevel(sr.getNowLevel() + 1);
		Stathp = CalcUtil.calcStatHp(pc.getType(), sr.getMaxHp(), (byte) sr.getCon());
		Statmp = CalcUtil.calcStatMp(pc.getType(), sr.getMaxMp(), (byte) sr.getWis());

		sr.setAC(CalcUtil.calcAc(sr.getNowLevel(), sr.getDex()));

		sr.setMaxHp(sr.getMaxHp() + Stathp);
		sr.setMaxMp(sr.getMaxMp() + Statmp);
	}

	private void setCharStatus(L1PcInstance pc, L1StatReset sr) {
		int addHp = sr.getMaxHp() - pc.getBaseMaxHp();
		int addMp = sr.getMaxMp() - pc.getBaseMaxMp();

		pc.getAbility().init();

		pc.getAbility().setBaseStr(sr.getBaseStr());
		pc.getAbility().setBaseInt(sr.getBaseIntel());
		pc.getAbility().setBaseWis(sr.getBaseWis());
		pc.getAbility().setBaseDex(sr.getBaseDex());
		pc.getAbility().setBaseCon(sr.getBaseCon());
		pc.getAbility().setBaseCha(sr.getBaseCha());

		pc.getAbility().setStr(sr.getStr());
		pc.getAbility().setInt(sr.getIntel());
		pc.getAbility().setWis(sr.getWis());
		pc.getAbility().setDex(sr.getDex());
		pc.getAbility().setCon(sr.getCon());
		pc.getAbility().setCha(sr.getCha());

		pc.addBaseMaxHp((short) addHp);
		pc.setCurrentHp(pc.getBaseMaxHp());
		pc.addBaseMaxMp((short) addMp);
		pc.setCurrentMp(pc.getBaseMaxMp());

		pc.setLevel(sr.getNowLevel());
	
		pc.resetBaseHit();
		pc.resetBaseDmg();
		pc.resetBaseMagicCritical();
		pc.resetBaseMagicHit();
		pc.resetBaseAc();
		pc.resetBaseMr();
	}

	private void setBaseHpMp(L1PcInstance pc, L1StatReset sr) {
		short init_hp = 0;
		short init_mp = 0;
		if (pc.isCrown()) { // 군주
			init_hp = 14;
			switch (sr.getWis()) {
			case 11:
				init_mp = 2;
				break;
			case 12:
			case 13:
			case 14:
			case 15:
				init_mp = 3;
				break;
			case 16:
			case 17:
			case 18:
				init_mp = 4;
				break;
			default:
				init_mp = 2;
				break;
			}
		} else if (pc.isKnight()) { // 기사
			init_hp = 16;
			switch (sr.getWis()) {
			case 9:
			case 10:
			case 11:
				init_mp = 1;
				break;
			case 12:
			case 13:
				init_mp = 2;
				break;
			default:
				init_mp = 1;
				break;
			}
		} else if (pc.isElf()) { // 요정
			init_hp = 15;
			switch (sr.getWis()) {
			case 12:
			case 13:
			case 14:
			case 15:
				init_mp = 4;
				break;
			case 16:
			case 17:
			case 18:
				init_mp = 6;
				break;
			default:
				init_mp = 4;
				break;
			}
		} else if (pc.isWizard()) { // 마법사
			init_hp = 12;
			switch (sr.getWis()) {
			case 12:
			case 13:
			case 14:
			case 15:
				init_mp = 6;
				break;
			case 16:
			case 17:
			case 18:
				init_mp = 8;
				break;
			default:
				init_mp = 6;
				break;
			}
		} else if (pc.isDarkelf()) { // 다크엘프
			init_hp = 12;
			switch (sr.getWis()) {
			case 10:
			case 11:
				init_mp = 3;
				break;
			case 12:
			case 13:
			case 14:
			case 15:
				init_mp = 4;
				break;
			case 16:
			case 17:
			case 18:
				init_mp = 6;
				break;
			default:
				init_mp = 3;
				break;
			}
		}
		sr.setMaxHp(init_hp);
		sr.setMaxMp(init_mp);
	}

	private boolean checkStatusBug(L1PcInstance pc, L1StatReset sr) {
		int baseAll = sr.getStr() + sr.getDex() + sr.getIntel() + sr.getWis() + sr.getCha() + sr.getCon();

		int _lvl_status = sr.getEndLevel() - 50; // 무버그 보너스 스테이터스

		if (_lvl_status < 0) {
			_lvl_status = 0;
		}

		int _old_status = 75 + pc.getElixirStats() + _lvl_status; // 케릭의 정확한 총 스테이터스 결과값.

		if (_old_status < baseAll) {
			pc.sendPackets(new S_SystemMessage("스테이터스 수치가 정상적이지 않습니다."));
			return false;
		}

		int basestr = 0;
		int basedex = 0;
		int basecon = 0;
		int baseint = 0;
		int basewis = 0;
		int basecha = 0;
		switch (pc.getType()) {
		case 0: // 군주
			basestr = 13;
			basedex = 10;
			basecon = 10;
			basewis = 11;
			basecha = 13;
			baseint = 10;
			break;
		case 1: // 기사
			basestr = 16;
			basedex = 12;
			basecon = 14;
			basewis = 9;
			basecha = 12;
			baseint = 8;
			break;
		case 2: // 요정
			basestr = 11;
			basedex = 12;
			basecon = 12;
			basewis = 12;
			basecha = 9;
			baseint = 12;
			break;
		case 3: // 법사
			basestr = 8;
			basedex = 7;
			basecon = 12;
			basewis = 12;
			basecha = 8;
			baseint = 12;
			break;
		case 4: // 다크엘프
			basestr = 12;
			basedex = 15;
			basecon = 8;
			basewis = 10;
			basecha = 9;
			baseint = 11;
			break;
		}
		if (sr.getStr() < basestr || sr.getDex() < basedex || sr.getCon() < basecon || sr.getWis() < basewis
				|| sr.getCha() < basecha || sr.getIntel() < baseint) {
			pc.sendPackets(new S_SystemMessage("스테이터스 수치가 정상적이지 않습니다."));
			return false;
		}

		if (sr.getBaseStr() < basestr || sr.getBaseDex() < basedex || sr.getBaseCon() < basecon
				|| sr.getBaseWis() < basewis || sr.getBaseCha() < basecha || sr.getBaseIntel() < baseint) {
			pc.sendPackets(new S_SystemMessage("스테이터스 수치가 정상적이지 않습니다."));
			return false;
		}

		return true;
	}
}