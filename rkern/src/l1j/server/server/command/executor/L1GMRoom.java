/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.   See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.command.executor;

import java.util.logging.Logger;

import l1j.server.server.GMCommandsConfig;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1GMRoom implements L1CommandExecutor {
	private static Logger _log = Logger.getLogger(L1GMRoom.class.getName());

	private L1GMRoom() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1GMRoom();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			int i = 0;
			try {
				i = Integer.parseInt(arg);
			} catch (NumberFormatException e) {
			}

			 if (i == 1) {
				pc.start_teleport(32737, 32796, 99, 5, 169, false, false);
			} else if (i == 2) {
				pc.start_teleport(33440, 32805, 4, 5, 169, false, false);
			} else if (i == 3) {
				pc.start_teleport(33082, 33390, 4, 5, 169, false, false);
			} else if (i == 4) {
				pc.start_teleport(34055, 32290, 4, 5, 169, false, false);
			} else if (i == 5) {
				pc.start_teleport(33723, 32495, 4, 5, 169, false, false);
			} else if (i == 6) {
				pc.start_teleport(32614, 32788, 4, 5, 169, false, false);
			} else if (i == 7) {
				pc.start_teleport(32678, 32857, 2005, 5, 169, false, false);
			} else if (i == 8) {
				pc.start_teleport(33515, 32858, 4, 5, 169, false, false);
			} else if (i == 9) {
				pc.start_teleport(32763, 32817, 622, 5, 169, false, false);
			} else if (i == 10) {
				pc.start_teleport(32572, 32944, 0, 5, 169, false, false);
			} else if (i == 11) {
				pc.start_teleport(32736, 32796, 16896, 5, 169, false, false);
			} else if (i == 12) {
				pc.start_teleport(32783, 32865, 621, pc.getHeading(), 169, false, false);
			} else if (i == 13) {
				pc.start_teleport(32805, 32814, 5490, 5, 169, false, false);
			} else if (i == 14) {
				pc.start_teleport(32736, 32787, 15, 5, 169, false, false);
			} else if (i == 15) {
				pc.start_teleport(32735, 32788, 29, 5, 169, false, false);
			} else if (i == 16) {
				pc.start_teleport(32730, 32802, 52, 5, 169, false, false);
			} else if (i == 17) {
				pc.start_teleport(32572, 32826, 64, 5, 169, false, false);
			} else if (i == 18) {
				pc.start_teleport(32895, 32533, 300, 5, 169, false, false);
			} else if (i == 19) {
				pc.start_teleport(33168, 32779, 4, 5, 169, false, false);
			} else if (i == 20) {
				pc.start_teleport(32623, 33379, 4, 5, 169, false, false);
			} else if (i == 21) {
//				L1Teleport.teleport(pc , 33630, 32677, (short) 4, 5, true); // 기란 수호탑
				pc.start_teleport(33630, 32677, 4, 5, 169, false, false);
			} else if (i == 22) {
//				L1Teleport.teleport(pc , 33524, 33394, (short) 4, 5, true); // 하이네 수호탑
				pc.start_teleport(33524, 33394, 4, 5, 169, false, false);
			} else if (i == 23) {
//				L1Teleport.teleport(pc , 34090, 33260, (short) 4, 5, true); // 아덴 수호탑
				pc.start_teleport(34090, 33260, 4, 5, 169, false, false);
			} else if (i == 24) {
//				L1Teleport.teleport(pc , 32424, 33068, (short) 440, 5, true); // 해적섬
				pc.start_teleport(32424, 33068, 440, 5, 169, false, false);
			} else if (i == 25) {
//				L1Teleport.teleport(pc , 32800, 32868, (short) 1001, 5, true); // 베헤모스
				pc.start_teleport(32800, 32868, 1001, 5, 169, false, false);
			} else if (i == 26) {
//				L1Teleport.teleport(pc , 32800, 32856, (short) 1000, 5, true); // 실베리아
				pc.start_teleport(32800, 32856, 1000, 5, 169, false, false);
			} else if (i == 27) {
//				L1Teleport.teleport(pc , 32630, 32903, (short) 780, 5, true); // 테베사막
				pc.start_teleport(32630, 32903, 780, 5, 169, false, false);
			} else if (i == 28) {
//				L1Teleport.teleport(pc , 32743, 32799, (short) 781, 5, true); // 테베 피라미드 내부
				pc.start_teleport(32743, 32799, 781, 5, 169, false, false);
			} else if (i == 29) {
//				L1Teleport.teleport(pc , 32735, 32830, (short) 782, 5, true); // 테베 오리시스 제단
				pc.start_teleport(32735, 32830, 782, 5, 169, false, false);
			} else if (i == 30) {
//				L1Teleport.teleport(pc , 32734, 32270, (short) 4, 5, true); // 피닉
				pc.start_teleport(32734, 32270, 4, 5, 169, false, false);
			} else if (i == 31) {
//				L1Teleport.teleport(pc , 32699, 32819, (short) 82, 5, true); // 데몬
				pc.start_teleport(32699, 32819, 82, 5, 169, false, false);
			} else if (i == 32) {
//				L1Teleport.teleport(pc , 32769, 32770, (short) 56, 5, true); // 기감4층
				pc.start_teleport(32769, 32770, 56, 5, 169, false, false);
			} else if (i == 33) {
//				L1Teleport.teleport(pc , 32929, 32995, (short) 410, 5, true); // 마족신전	
				pc.start_teleport(32929, 32995, 410, 5, 169, false, false);
			} else if (i == 34) {
//				L1Teleport.teleport(pc , 32791, 32691, (short) 1005, 5, true); // 레이드 안타라스
				pc.start_teleport(32791, 32691, 1005, 5, 169, false, false);
			} else if (i == 35) {
//				L1Teleport.teleport(pc , 32960, 32840, (short) 1011, 5, true); // 레이드 파푸리온
				pc.start_teleport(32960, 32840, 1011, 5, 169, false, false);
			} else if (i == 36) {
//				L1Teleport.teleport(pc , 32849, 32876, (short) 1017, 5, true); // 린드레이드
				pc.start_teleport(32849, 32876, 1017, 5, 169, false, false);
			} else if (i == 37) {
//				L1Teleport.teleport(pc , 32725, 32800, (short) 67, 5, true); // 발라방
				pc.start_teleport(32725, 32800, 67, 5, 169, false, false);
			} else if (i == 38) {
//				L1Teleport.teleport(pc , 32771, 32831, (short) 65, 5, true); // 파푸방
				pc.start_teleport(32771, 32831, 65, 5, 169, false, false);
			} else if (i == 39) {
//				L1Teleport.teleport(pc , 32696, 32824, (short) 37, 5, true); // 버모스 (용던7층)
				pc.start_teleport(32696, 32824, 37, 5, 169, false, false);
			} else if (i == 40) {
//				L1Teleport.teleport(pc , 32922, 32812, (short) 430, 5, true); // 정령무덤
				pc.start_teleport(32922, 32812, 430, 5, 169, false, false);
			} else if (i == 41) {
//				L1Teleport.teleport(pc , 32737, 32834, (short) 2004, 5, true); // 고라스
				pc.start_teleport(32737, 32834, 2004, 5, 169, false, false);
			} else if (i == 42) {
//				L1Teleport.teleport(pc , 32707, 32846, (short) 2, 5, true); // 섬던2층
				pc.start_teleport(32707, 32846, 2, 5, 169, false, false);
			} else if (i == 43) {
//				L1Teleport.teleport(pc , 32772, 32861, (short) 400, 5, true); // 고대무덤
				pc.start_teleport(32772, 32861, 400, 5, 169, false, false);
			} else if (i == 44) {
//				L1Teleport.teleport(pc , 32982, 32808, (short) 244, 5, true); // 오땅
				pc.start_teleport(32982, 32808, 244, 5, 169, false, false);
			} else if (i == 45) {
//				L1Teleport.teleport(pc , 32811, 32819, (short) 460, 5, true); // 라바2층
				pc.start_teleport(32811, 32819, 460, 5, 169, false, false);
			} else if (i == 46) {
//				L1Teleport.teleport(pc , 32724, 32792, (short) 536, 5, true); // 라던3층
				pc.start_teleport(32724, 32792, 536, 5, 169, false, false);
			} else if (i == 47) {
//				L1Teleport.teleport(pc , 32847, 32793, (short) 532, 5, true); // 라던4층
				pc.start_teleport(32847, 32793, 532, 5, 169, false, false);
			} else if (i == 48) {
//				L1Teleport.teleport(pc , 32843, 32693, (short) 550, 5, true); // 선박무덤
				pc.start_teleport(32843, 32693, 550, 5, 169, false, false);
			} else if (i == 49) {
//				L1Teleport.teleport(pc , 32781, 32801, (short) 558, 5, true); // 심해
				pc.start_teleport(32781, 32801, 558, 5, 169, false, false);
			} else if (i == 50) {
//				L1Teleport.teleport(pc , 32731, 32862, (short) 784, 5, true); // 제브
				pc.start_teleport(32731, 32862, 784, 5, 169, false, false);
			} else if (i == 51) {
//				L1Teleport.teleport(pc , 32728, 32704, (short) 4, 5, true); // 균열 1
				pc.start_teleport(32728, 32704, 4, 5, 169, false, false);
			} else if (i == 52) {
//				L1Teleport.teleport(pc , 32827, 32658, (short) 4, 5, true); // 균열 2
				pc.start_teleport(32827, 32658, 4, 5, 169, false, false);
			} else if (i == 53) {
//				L1Teleport.teleport(pc , 32852, 32713, (short) 4, 5, true); // 균열 3
				pc.start_teleport(32852, 32713, 4, 5, 169, false, false);
			} else if (i == 54) {
//				L1Teleport.teleport(pc , 32914, 33427, (short) 4, 5, true); // 균열 4
				pc.start_teleport(32914, 33427, 4, 5, 169, false, false);
			} else if (i == 55) {
//				L1Teleport.teleport(pc , 32962, 33251, (short) 4, 5, true); // 균열 5
				pc.start_teleport(32962, 33251, 4, 5, 169, false, false);
			} else if (i == 56) {
//				L1Teleport.teleport(pc , 32908, 33169, (short) 4, 5, true); // 균열 6
				pc.start_teleport(32908, 33169, 4, 5, 169, false, false);
			} else if (i == 57) {
//				L1Teleport.teleport(pc , 34272, 33361, (short) 4, 5, true); // 균열 7
				pc.start_teleport(34272, 33361, 4, 5, 169, false, false);
			} else if (i == 58) {
//				L1Teleport.teleport(pc , 34258, 33202, (short) 4, 5, true); // 균열 8
				pc.start_teleport(34258, 33202, 4, 5, 169, false, false);
			} else if (i == 59) {
//				L1Teleport.teleport(pc , 34225, 33313, (short) 4, 5, true); // 균열 9
				pc.start_teleport(34225, 33313, 4, 5, 169, false, false);
			} else if (i == 60) {
//				L1Teleport.teleport(pc , 32682, 32892, (short) 5167, 5, true); // 악마영토
				pc.start_teleport(32682, 32892, 5167, 5, 169, false, false);
			} else if (i == 61) {
//				L1Teleport.teleport(pc , 32862, 32862, (short) 537, 5, true); // 기르타스
				pc.start_teleport(32862, 32862, 537, 5, 169, false, false);
			} else if (i == 62) {
//				L1Teleport.teleport(pc , 32738, 32448, (short) 4, 5, true); // 화전민
				pc.start_teleport(32738, 32448, 4, 5, 169, false, false);
			} else if (i == 63) {
//				L1Teleport.teleport(pc , 32797, 32285, (short) 4, 5, true); // 오성수탑
				pc.start_teleport(32797, 32285, 4, 5, 169, false, false);
			} else if (i == 64) {
//				L1Teleport.teleport(pc , 33052, 32339, (short) 4, 5, true); // 요정숲
				pc.start_teleport(33052, 32339, 4, 5, 169, false, false);
			} else if (i == 65) {
//				L1Teleport.teleport(pc, 32738, 32872, (short) 2236, 5, true); // 서버지기아지트
				pc.start_teleport(32738, 32872, 2236, 5, 169, false, false);
			} else if (i == 66) {
				pc.start_teleport(32737, 32868, 2237, 5, 169, false, false);
			} else if (i == 67) {
				pc.start_teleport(32736, 32796, 16896, 5, 169, false, false);
			} else if (i == 68) {
				pc.start_teleport(32736, 32799, 38, 5, 169, false, false);
			} else if (i == 69) {
				pc.start_teleport(32769, 32827, 610, 5, 169, false, false);
			} else if (i == 70) {
					pc.start_teleport(32764, 32815, 5554, 5, 169, false, false);
			} else {
				L1Location loc = GMCommandsConfig.ROOMS.get(arg.toLowerCase());
				if (loc == null) {
					SC_SystemMessage.send(pc, "\\f:==================<귀환 장소>===================");
					SC_SystemMessage.send(pc, "\\aA1.gm룸 2.기란 3.은말 4.오렌 5.웰던 6.글루딘 7.숨계");
					SC_SystemMessage.send(pc, "\\aA8.버경장 9.깃털말 10.말섬 11.상담소 12.벗꽃 13.낚시");
					SC_SystemMessage.send(pc, "\\aA14.켄트성 15.윈다성 16.기란성 17.하이성 18.아덴성");
					SC_SystemMessage.send(pc, "\\aA19.수호탑 20.수호탑 21.수호탑 22.수호탑 23.수호탑");
					SC_SystemMessage.send(pc, "\\f:24.해섬 25.베히모 26.실베리아 27.테베 28.피라미드");
					SC_SystemMessage.send(pc, "\\f:29.피라미드 30.피닉스 31.데몬 32.기감4층 33.마족방");
					//pc.sendPackets(new SC_SystemMessage("\\aD34.안타 35.파푸 36.린드 37.발라 38.구파푸 39.버모스"));
					SC_SystemMessage.send(pc, "\\f:40.정무 41.고라스 42.섬던2층 43.고대무덤 44.오땅");
					SC_SystemMessage.send(pc, "\\f:45.라던2층 46.라던3층 47.라던4층 48.선박 49.심해");
					SC_SystemMessage.send(pc, "\\f:50.제브레퀴 51~59.균열 60.악마영토 61.기르타스");
					SC_SystemMessage.send(pc, "\\aA62.화전민마을 63.오크성 64.요정숲 65.서버지기아지트");
					SC_SystemMessage.send(pc, "\\aA66.후원안내 67.VIP상담소 68.오토유저");
					SC_SystemMessage.send(pc, "\\aA69.후원문의1 70.후원문의2");
					return;
				}
//				L1Teleport.teleport(pc, loc.getX(), loc.getY(), (short) loc
//						.getMapId(), 5, true);
				pc.start_teleport(loc.getX(), loc.getY(), loc.getMapId(), 5, 169, false, false);
			}
			if (i > 0 && i < 33) {
				SC_SystemMessage.send(pc, "운영자 귀환(" + i + ")번으로 이동했습니다.");
			}
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, ".귀환 [장소명]을 입력 해주세요.");
		}
	}
}
