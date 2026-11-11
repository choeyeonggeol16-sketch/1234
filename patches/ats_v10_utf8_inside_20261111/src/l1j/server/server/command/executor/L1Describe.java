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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.StringTokenizer;
import l1j.server.L1DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.SQLUtil;

public class L1Describe implements L1CommandExecutor {

	private L1Describe() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Describe();
	}

	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String name = st.nextToken();
			L1PcInstance target = L1World.getInstance().getPlayer(name);
			if (target == null) {
				pc.sendPackets(new S_ServerMessage(73, name)); // \f1%0은 게임을 하고 있지 않습니다.
				return;
			}
			
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;

			//String password = null;
			String quiz = null;
			//String ip = null;
			//String host = null;
			//boolean banned = false;
			try {

				con = L1DatabaseFactory.getInstance().getConnection();
				
				pstm = con.prepareStatement("select password, quiz, ip, host, banned from accounts where login='" + target.getAccountName() + "'");			
				rs = pstm.executeQuery();
				
				if(rs.next())
				{
					//password = rs.getString("password");
					quiz = rs.getString("quiz");
					//ip = rs.getString("ip");
					//host = rs.getString("host");
					//banned = rs.getInt("banned") == 1 ? true : false;
				}
			} catch(Exception e) {
			} finally {
				SQLUtil.close(rs);
				SQLUtil.close(pstm);
				SQLUtil.close(con);
			}
			
			
			int lv = target.getLevel();
			int currentLvExp = ExpTable.getExpByLevel(lv);
			int nextLvExp = ExpTable.getExpByLevel(lv + 1);
			double neededExp = nextLvExp - currentLvExp;
			double currentExp = target.getExp() - currentLvExp;
			int per = (int) ((currentExp / neededExp) * 100.0);

			String typeName = null;
			switch (target.getType()) {
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

			default:
				typeName = "????";
			}

			String clan_name = target.getClanname() != null ? target.getClanname() : "\\f5[!]혈맹없음";
			String account_password = target.getAccountName().equalsIgnoreCase("인공지능") ? "인공지능"
					: Account.load(target.getAccountName()).get_Password();
			String ip_address = target.getAccountName().equalsIgnoreCase("인공지능") ? "인공지능" : target.getNetConnection().getIp();
		
			pc.sendPackets(new S_SystemMessage("--------------------------------------------------"));
			pc.sendPackets(
					new S_SystemMessage("[ " + target.getName() + " ] 직업:" + typeName + ", 혈맹:" + clan_name + " 퀴즈 : " + quiz));
			pc.sendPackets(new S_SystemMessage(
					"계정: " + target.getAccountName() + " /  비번: " + account_password + "   IP: " + ip_address));
			pc.sendPackets(new S_SystemMessage("--------------------------------------------------"));
			pc.sendPackets(new S_SystemMessage(" Lv: " + lv + " (" + per + "%)" + "   방어: "
					+ target.getAC().getAc() + "   마방 방어 : " + (int) (target.getResistance().getMr())));
			int hpr = target.getHpr();
			int mpr = target.getMpr();
			int sp = Math.max(0, target.getSp() - target.getTrueSp());
			
			int dmgup = ((int) target.getMeleeWeaponDmg() + target.getMeleeDmg() + CalcUtil.getMeleeDmgStat(target.getAbility().getTotalStr()));  //--근거리 데미지 
			int hitup = (target.getMeleeHit() + CalcUtil.getMeleeHitStat(target.getAbility().getTotalStr()));  //-- 근거리 명중
			int Bowdmg = ((int) target.getRangedWeaponDmg() + target.getRangedDmg() + CalcUtil.getRangedDmgStat(target.getAbility().getTotalDex()));  //--원거리 데미지
			int Bowhit = (target.getRangedHit() + (int) CalcUtil.getRangedHitStat(target.getAbility().getTotalDex()));  //--원거리 명중
			
			pc.sendPackets(new S_SystemMessage("* 피: " + target.getCurrentHp() + '/' + target.getMaxHp() + " (틱: "
					+ hpr + ')' + "   엠: " + target.getCurrentMp() + '/' + target.getMaxMp() + " (틱: " + mpr + ')'));
			pc.sendPackets(new S_SystemMessage(
					"* 힘: " + target.getAbility().getTotalStr() + "  " + "덱: " + target.getAbility().getTotalDex()
							+ "  " + "콘: " + target.getAbility().getTotalCon() + "   " + "인: "
							+ target.getAbility().getTotalInt() + "   " + "위: " + target.getAbility().getTotalWis()
							+ "  " + "카: " + target.getAbility().getTotalCha()));
			pc.sendPackets(new S_SystemMessage(
					"* 불 : " + target.getResistance().getFire() + "  물 : " + target.getResistance().getWater() + "  바람 : "
							+ target.getResistance().getWind() + "  땅 : " + target.getResistance().getEarth()));
			pc.sendPackets(new S_SystemMessage("* 물약 회복량 : " + (target.getPotionRecoveryAmount() + "  물약 회복량 보조 : "+ (target.getPotionRecovery())))); 
			pc.sendPackets(new S_SystemMessage("* 스텟(근)대미지 : " + CalcUtil.getMeleeDmgStat(target.getAbility().getTotalStr()) + " 스텟(근)명중 : " + CalcUtil.getMeleeHitStat(target.getAbility().getTotalStr())));
			pc.sendPackets(new S_SystemMessage("* 스텟(원)대미지 : " + CalcUtil.getRangedDmgStat(target.getAbility().getTotalDex()) + " 스텟(원)명중 : " + CalcUtil.getRangedHitStat(target.getAbility().getTotalDex())));
			pc.sendPackets(new S_SystemMessage("--------------------------------------------------"));
			pc.sendPackets(new S_SystemMessage("\\fW* (근)대미지: " + (dmgup) + "  (근)명중: "+ (hitup) + "  (근)치명타: " + (target.getMeleeCritical()))); 
			pc.sendPackets(new S_SystemMessage("\\fW* (원)대미지: " + (Bowdmg) + "  (원)명중: "+ (Bowhit) + "  (원)치명타: " + (target.getRangedCritical())));
			pc.sendPackets(new S_SystemMessage("\\fW* 마법적중: " + target.getMagicHit() + "  스펠: " + sp + "  마법치명타: " + target.getMagicCritical()));
			pc.sendPackets(new S_SystemMessage("\\fR* 대미지감소: " + target.getDamageReduction() + " PVP대미지: " + target.getDmgupPvP() + "  PVP대미지감소: " + target.getDmgDownPvP()));
			pc.sendPackets(new S_SystemMessage("\\fS* 스턴내성: " + target.getResistance().getregist_technology() + " 스턴적중: " + target.getResistance().gettechnique_hit() + "\\fT 정령내성: " + target.getResistance().getregist_spirit() + " 정령적중: " + target.getResistance().getspirit_hit()));
			pc.sendPackets(new S_SystemMessage("\\fY* 물리공격력: " + target.LawfulAT +  " 주문력: " + target.LawfulSP +  " \\fU물방: " + target.LawfulAC +  " 마방: " + target.LawfulMR));
			
			
			pc.sendPackets(new S_SystemMessage("--------------------------------------------------"));
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".정보 [캐릭명]으로 입력."));
		}
	}
}
