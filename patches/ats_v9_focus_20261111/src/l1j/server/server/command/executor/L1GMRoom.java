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

import l1j.server.server.GMCommandsConfig;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;

public class L1GMRoom implements L1CommandExecutor {

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
            // 스위츠문 으로 변경
			switch (i) {
			case 1: pc.teleport(32737, 32796, (short) 99, pc.getHeading(), 169, false, false); break;
			case 2: pc.teleport(33440, 32805, (short) 4, pc.getHeading(), 169, false, false); break;
			case 3: pc.teleport(33082, 33390, (short) 4, pc.getHeading(), 169, false, false); break;
			case 4: pc.teleport(34055, 32290, (short) 4, pc.getHeading(), 169, false, false); break;
			case 5: pc.teleport(33723, 32495, (short) 4, pc.getHeading(), 169, false, false); break;
			case 6: pc.teleport(32614, 32788, (short) 4, pc.getHeading(), 169, false, false); break;
			case 7: pc.teleport(32678, 32857, (short) 2005, pc.getHeading(), 169, false, false); break;
			case 8: pc.teleport(33515, 32858, (short) 4, pc.getHeading(), 169, false, false); break;
			case 9: pc.teleport(32763, 32817, (short) 622, pc.getHeading(), 169, false, false); break;
			case 10: pc.teleport(32572, 32944, (short) 0, pc.getHeading(), 169, false, false); break;
			case 11: pc.teleport(32736, 32796, (short) 16896, pc.getHeading(), 169, false, false); break;
			case 12: pc.teleport(32783, 32865, (short) 621, pc.getHeading(), 169, false, false); break;
			case 13: pc.teleport(32805, 32814, (short) 5490, pc.getHeading(), 169, false, false); break;
			case 14: pc.teleport(32736, 32787, (short) 15, pc.getHeading(), 169, false, false); break;
			case 15: pc.teleport(32735, 32788, (short) 29, pc.getHeading(), 169, false, false); break;
			case 16: pc.teleport(32730, 32802, (short) 52, pc.getHeading(), 169, false, false); break;
			case 17: pc.teleport(32572, 32826, (short) 64, pc.getHeading(), 169, false, false); break;
			case 18: pc.teleport(32895, 32533, (short) 300, pc.getHeading(), 169, false, false); break;
			case 19: pc.teleport(33168, 32779, (short) 4, pc.getHeading(), 169, false, false); break;
			case 20: pc.teleport(32623, 33379, (short) 4, pc.getHeading(), 169, false, false); break;	
			case 21: pc.teleport(33630, 32677, (short) 4, pc.getHeading(), 169, false, false); break;
			case 22: pc.teleport(33524, 33394, (short) 4, pc.getHeading(), 169, false, false); break;
			case 23: pc.teleport(34090, 33260, (short) 4, pc.getHeading(), 169, false, false); break;
			case 24: pc.teleport(32424, 33068, (short) 440, pc.getHeading(), 169, false, false); break;
			case 25: pc.teleport(32800, 32868, (short) 1001, pc.getHeading(), 169, false, false); break;
			case 26: pc.teleport(32800, 32856, (short) 1000, pc.getHeading(), 169, false, false); break;
			case 27: pc.teleport(32630, 32903, (short) 780, pc.getHeading(), 169, false, false); break;
			case 28: pc.teleport(32743, 32799, (short) 781, pc.getHeading(), 169, false, false); break;
			case 29: pc.teleport(32735, 32830, (short) 782, pc.getHeading(), 169, false, false); break;
			case 30: pc.teleport( 32734, 32270, (short) 4, pc.getHeading(), 169, false, false); break;		
			case 31: pc.teleport( 32699, 32819, (short) 82, pc.getHeading(), 169, false, false); break;
			case 32: pc.teleport( 32769, 32770, (short) 56, pc.getHeading(), 169, false, false); break;
			case 33: pc.teleport( 32929, 32995, (short) 410, pc.getHeading(), 169, false, false); break;	
			case 34: pc.teleport( 32791, 32691, (short) 1005, pc.getHeading(), 169, false, false); break;
			case 35: pc.teleport( 32960, 32840,(short) 1011, pc.getHeading(), 169, false, false); break;		
			case 36: pc.teleport( 32849, 32876, (short) 1017, pc.getHeading(), 169, false, false); break;		
			case 37: pc.teleport( 32725, 32800, (short) 67, pc.getHeading(), 169, false, false); break;
			case 38: pc.teleport(  32771, 32831, (short) 65, pc.getHeading(), 169, false, false); break;			
			case 39: pc.teleport(  32696, 32824, (short) 37, pc.getHeading(), 169, false, false); break;
			case 40: pc.teleport( 32922, 32812, (short) 430, pc.getHeading(), 169, false, false); break;
			case 41: pc.teleport( 32737, 32834,  (short) 2004, pc.getHeading(), 169, false, false); break;		
			case 42: pc.teleport( 32707, 32846, (short) 2, pc.getHeading(), 169, false, false); break;		
			case 43: pc.teleport( 32772, 32861, (short) 400, pc.getHeading(), 169, false, false); break;			
			case 44: pc.teleport( 32982, 32808, (short) 244, pc.getHeading(), 169, false, false); break;		
			case 45: pc.teleport( 32837, 32770, (short) 479, pc.getHeading(), 169, false, false); break;		
			case 46: pc.teleport(  32724, 32792, (short) 536, pc.getHeading(), 169, false, false); break;			
			case 47: pc.teleport( 32847, 32793,(short) 532, pc.getHeading(), 169, false, false); break;
			case 48: pc.teleport( 32843, 32693,(short) 550, pc.getHeading(), 169, false, false); break;
			case 49: pc.teleport(  32781, 32801, (short) 558, pc.getHeading(), 169, false, false); break;
			case 50: pc.teleport( 32731, 32862, (short) 784, pc.getHeading(), 169, false, false); break;		
			case 51: pc.teleport( 32728, 32704, (short) 4, pc.getHeading(), 169, false, false); break;		
			case 52: pc.teleport( 32827, 32658, (short) 4, pc.getHeading(), 169, false, false); break;		
			case 53: pc.teleport( 32852, 32713, (short) 4, pc.getHeading(), 169, false, false); break;		
			case 54: pc.teleport( 32914, 33427, (short) 4, pc.getHeading(), 169, false, false); break;		
			case 55: pc.teleport( 32962, 33251,  (short) 4, pc.getHeading(), 169, false, false); break;		
			case 56: pc.teleport( 32908, 33183,(short) 4, pc.getHeading(), 169, false, false); break;		
			case 57: pc.teleport( 34272, 33361, (short) 4, pc.getHeading(), 169, false, false); break;		
			case 58: pc.teleport(  34258, 33202, (short) 4, pc.getHeading(), 169, false, false); break;
			case 59: pc.teleport( 34225, 33313, (short) 4, pc.getHeading(), 169, false, false); break;
			case 60: pc.teleport( 32682, 32892, (short) 5167, pc.getHeading(), 169, false, false); break;			
			case 61: pc.teleport( 33052, 32339, (short) 4, pc.getHeading(), 169, false, false); break;
			case 62: pc.teleport( 32769, 32827, (short) 610, pc.getHeading(), 169, false, false); break;
			case 63: pc.teleport( 32736, 32796, (short) 16896, pc.getHeading(), 169, false, false);
			break;
			default:
				L1Location loc = GMCommandsConfig.ROOMS.get(arg.toLowerCase());
				if (loc == null) {
					pc.sendPackets(new S_SystemMessage("==================<귀환 장소>==================="));
					pc.sendPackets(new S_SystemMessage("1.gm룸 2.기란 3.은말 4.오렌 5.웰던 6.글루딘 7.숨계"));
					pc.sendPackets(new S_SystemMessage("8.버경장 9.깃털말 10.말섬 11.상담소 12.벗꽃 13.낚시"));
					pc.sendPackets(new S_SystemMessage("14.켄트성 15.윈다성 16.기란성 17.하이성 18.아덴성"));
					pc.sendPackets(new S_SystemMessage("19.수호탑 20.수호탑 21.수호탑 22.수호탑 23.수호탑"));
					pc.sendPackets(new S_SystemMessage("24.해섬 25.베히모 26.실베리아 27.테베 28.피라미드"));
					pc.sendPackets(new S_SystemMessage("29.피라미드 30.피닉스 31.데몬 32.기감4층 33.마족방"));
					pc.sendPackets(new S_SystemMessage("34.안타 35.파푸 36.린드 37.발라 38.구파푸 39.버모스"));
					pc.sendPackets(new S_SystemMessage("40.정무 41.고라스 42.섬던2층 43.고대무덤 44.오땅"));
					pc.sendPackets(new S_SystemMessage("45.라던중앙 46.라던3층 47.라던4층 48.선박 49.심해"));
					pc.sendPackets(new S_SystemMessage("50.제브레퀴 51~59.균열 60.악마영토 61.요정숲"));
					pc.sendPackets(new S_SystemMessage("62.(상담소) 63.VIP상담소")); 
					return;
				}
				pc.teleport(loc.getX(), loc.getY(), (short) loc.getMapId(), pc.getHeading(), 169, false, false);
				break;
			}
			if (i > 0 && i < 33) {
				pc.sendPackets(new S_SystemMessage("운영자 귀환(" + i + ")번으로 이동했습니다."));
			}
		} catch (Exception exception) {
			pc.sendPackets(new S_SystemMessage(".귀환 [장소명]을 입력 해주세요.(장소명은 GMCommands.xml을 참조)"));
		}
	}
}
