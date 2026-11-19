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

import java.util.StringTokenizer;
import l1j.server.server.Account;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
//import java.util.logging.Logger;

public class L1Describe2 implements L1CommandExecutor {
	//private static Logger _log = Logger.getLogger(L1Describe.class.getName());

	private L1Describe2() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Describe2();
	}


	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String name = st.nextToken();
			L1PcInstance target = L1World.getInstance(). getPlayer(name);
			if (target == null) {
				SC_ServerMessage.send(pc, 73, name); // \f1%0은 게임을 하고 있지 않습니다.
				return;
			}		
			int lv = target.getLevel();
			int currentLvExp = ExpTable.getExpByLevel(lv);
			int nextLvExp = ExpTable.getExpByLevel(lv + 1);
			double neededExp = nextLvExp - currentLvExp ;
			double currentExp =  target.getExp() - currentLvExp;
			int per = (int)((currentExp / neededExp) * 100.0);

			String typeName = null;
			switch(target.getType()){
			case 0:
				typeName = "군주";
				break;

			case 1:
				typeName = "기사";
				break;

			case 2:
				typeName = "요정";
				break;

			case 3:
				typeName = "마법사";
				break;

			case 4:
				typeName = "다크엘프";
				break;

			case 5:
				typeName = "용기사";
				break;

			case 6:
				typeName = "환술사";
				break;

			case 7:
				typeName = "전사";
				break;

			default:
				typeName = "????";
			}
			SC_SystemMessage.send(pc, "\\aD--------------------------------------------------");
			SC_SystemMessage.send(pc, "\\aD[ " + target.getName() + " ] 직업:" + typeName + ", 혈맹:" + target.getClanname());
			if (!target.noPlayerCK) {
				SC_SystemMessage.send(pc, "\\aD계정: " + target.getAccountName() + " / " + Account.load(target.getAccountName()).get_Password() + "   IP: " + target.getNetConnection().getIp());
			}	
		
			
			int aa = target.getResistance().getcalcPcDefense();
			
SC_SystemMessage.send(pc, "\\aDBASE:(S: " + target.getAbility().getBaseStr() + " / " + "D: " + target.getAbility().getBaseDex() + " / " 
                        + "C: " + target.getAbility().getBaseCon() + " / " + "I: " + target.getAbility().getBaseInt() + " / " 
                        + "W: " + target.getAbility().getBaseWis() + " / " + "C: " + target.getAbility().getBaseCha() + ')');
SC_SystemMessage.send(pc, "\\aHREAL:(S: " + target.getAbility().getTotalStr() + " / " + "D: " + target.getAbility().getTotalDex() + " / "
						+ "C: " + target.getAbility().getTotalCon() + " / " + "I: " + target.getAbility().getTotalInt() + " / "
						+ "W: " + target.getAbility().getTotalWis() + " / " + "C: " + target.getAbility().getTotalCha() + ')');
			SC_SystemMessage.send(pc, "\\aD--------------------------------------------------");
		} catch (Exception e) {
			SC_ChatPacket.sendDirect(pc, pc, ".유저정보 [캐릭명] 으로 입력.");
		}
	}
}
