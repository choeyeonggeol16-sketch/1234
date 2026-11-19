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
import l1j.server.server.utils.eKind;

public class L1Describe implements L1CommandExecutor {
	//private static Logger _log = Logger.getLogger(L1Describe.class.getName());

	private L1Describe() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Describe();
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
			if (!pc.isGm() && pc.getId() != target.getId()) {
				SC_ChatPacket.sendDirect(pc, pc, ".정보 명령어는 본인 캐릭터만 조회할 수 있습니다.");
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
			if (!target.noPlayerCK && pc.isGm()) {
				SC_SystemMessage.send(pc, "\\aD계정: " + target.getAccountName() + " / " + Account.load(target.getAccountName()).get_Password() + "   IP: " + target.getNetConnection().getIp());
			}	
			SC_SystemMessage.send(pc, "\\aD--------------------------------------------------");
			SC_SystemMessage.send(pc, "\\aL* Lv: " + lv +" (" + per + "%)" + "   방어: " + target.getAC().getAc() + "   마방: " + target.getResistance().getEffectedMrBySkill());  // 원본 target.getResistance().getMr()
			int hpr = target.getHpr();
			int mpr = target.getMpr();
			SC_ChatPacket.sendDirect(pc, pc, "* 피: " + target.getCurrentHp() + '/' + target.getMaxHp() + " (틱: " + hpr + ')' + "   엠: " + target.getCurrentMp() + '/' + target.getMaxMp() + " (틱: " + mpr + ')');
			SC_ChatPacket.sendDirect(pc, pc, "* 힘: " + target.getAbility().getTotalStr() + "  " + "덱: " + target.getAbility().getTotalDex() + "   "
					+ "콘: " + target.getAbility().getTotalCon() + "   " + "인: " + target.getAbility().getTotalInt() + "   "
					+ "위: " + target.getAbility().getTotalWis() + "   " + "카: " + target.getAbility().getTotalCha());
			SC_ChatPacket.sendDirect(pc, pc, "* 불: " + target.getResistance().getFire() + "  물: " + target.getResistance().getWater() + "  바람: " + target.getResistance().getWind() + "  땅: " + target.getResistance().getEarth());		
			
			SC_ChatPacket.sendDirect(pc, pc, String.format("* 기술내성:%d 정령내성:%d 용언내성:%d 공포내성:%d 전체내성:%d", 
					target.getResistance().getSpecialResistance(eKind.ABILITY), 
					target.getResistance().getSpecialResistance(eKind.SPIRIT), 
					target.getResistance().getSpecialResistance(eKind.DRAGON_SPELL), 
					target.getResistance().getSpecialResistance(eKind.FEAR),
					target.getResistance().getSpecialResistance(eKind.ALL))); 
			
			SC_ChatPacket.sendDirect(pc, pc, String.format("* 기술적중:%d 정령적중:%d 용언적중:%d 공포적중:%d 전체적중:%d", 
					target.getResistance().getSpecialPierce(eKind.ABILITY), 
					target.getResistance().getSpecialPierce(eKind.SPIRIT), 
					target.getResistance().getSpecialPierce(eKind.DRAGON_SPELL), 
					target.getResistance().getSpecialPierce(eKind.FEAR),
					target.getResistance().getSpecialPierce(eKind.ALL))); 
			
			SC_ChatPacket.sendDirect(pc, pc, "* 추타: " + target.getDmgup() + "  공성: " + target.getHitup() + "  활타: " + target.getBowDmgup() + "  활명: " + target.getBowHitup() + "  주술: " + target.getAbility().getSp());
			SC_ChatPacket.sendDirect(pc, pc, "* 대미지감소: " + target.getDamageReductionByArmor() + " * 근거리치명타: " + target.get_melee_critical_rate());
			SC_ChatPacket.sendDirect(pc, pc, "* 원거리치명타: " + target.get_missile_critical_rate() + " * 마법치명타: " + target.getBaseMagicCritical());
			SC_ChatPacket.sendDirect(pc, pc, "* 대미지리덕션무시: " + target.getDamageReductionIgnore());
			SC_ChatPacket.sendDirect(pc, pc, "* PVP대미지: " + target.getResistance().getPVPweaponTotalDamage() +  " * PVP대미지감소: " + target.getResistance().getcalcPcDefense());
			SC_SystemMessage.send(pc, "* HP: (" + target.getCurrentHp() + '/' + target.getMaxHp() + ") (HPr: " + hpr + ')' 
					+ " / MP: (" + target.getCurrentMp() + '/' + target.getMaxMp() + ") (MPr: " + mpr + ')');
SC_SystemMessage.send(pc, "\\aDBASE:(S: " + target.getAbility().getBaseStr() + " / " + "D: " + target.getAbility().getBaseDex() + " / " 
                        + "C: " + target.getAbility().getBaseCon() + " / " + "I: " + target.getAbility().getBaseInt() + " / " 
                        + "W: " + target.getAbility().getBaseWis() + " / " + "C: " + target.getAbility().getBaseCha() + ')');
SC_SystemMessage.send(pc, "\\aHREAL:(S: " + target.getAbility().getTotalStr() + " / " + "D: " + target.getAbility().getTotalDex() + " / "
						+ "C: " + target.getAbility().getTotalCon() + " / " + "I: " + target.getAbility().getTotalInt() + " / "
						+ "W: " + target.getAbility().getTotalWis() + " / " + "C: " + target.getAbility().getTotalCha() + ')');
			SC_SystemMessage.send(pc, "\\aD--------------------------------------------------");
		} catch (Exception e) {
			SC_ChatPacket.sendDirect(pc, pc, ".정보 [캐릭명] 으로 입력.");
		}
	}
}
