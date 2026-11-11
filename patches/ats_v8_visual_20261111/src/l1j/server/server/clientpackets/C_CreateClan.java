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

import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.configure.Config;
import l1j.server.server.BadNamesList;
import l1j.server.server.GameClient;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;

// Referenced classes of package l1j.server.server.clientpackets:
// ClientBasePacket

public class C_CreateClan extends ClientBasePacket {

	private static final String C_CREATE_CLAN = "[C] C_CreateClan";
	private static Logger _log = Logger.getLogger(C_CreateClan.class.getName());


	private static boolean isAlphaNumeric(String s) {
		boolean flag = true;
		char ac[] = s.toCharArray();
		int i = 0;
		do {
			if (i >= ac.length) {
				break;
			}
			if (!Character.isLetterOrDigit(ac[i])) {
				flag = false;
				break;
			}
			i++;
		} while (true);
		return flag;
	}

	private static boolean isInvalidName(String name) {

		for (int i = 0;i<name.length();i++) {  
			if (name.charAt(i) == 'ㄱ' || name.charAt(i) == 'ㄲ' || name.charAt(i) == 'ㄴ' || name.charAt(i) == 'ㄷ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㄸ' || name.charAt(i) == 'ㄹ' || name.charAt(i) == 'ㅁ' || name.charAt(i) == 'ㅂ' ||    //한문자(char)단위로 비교 
					name.charAt(i) == 'ㅃ' || name.charAt(i) == 'ㅅ' || name.charAt(i) == 'ㅆ' || name.charAt(i) == 'ㅇ' ||    //한문자(char)단위로 비교
					name.charAt(i) == 'ㅈ' || name.charAt(i) == 'ㅉ' || name.charAt(i) == 'ㅊ' || name.charAt(i) == 'ㅋ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅌ' || name.charAt(i) == 'ㅍ' || name.charAt(i) == 'ㅎ' || name.charAt(i) == 'ㅛ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅕ' || name.charAt(i) == 'ㅑ' || name.charAt(i) == 'ㅐ' || name.charAt(i) == 'ㅔ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅗ' || name.charAt(i) == 'ㅓ' || name.charAt(i) == 'ㅏ' || name.charAt(i) == 'ㅣ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅠ' || name.charAt(i) == 'ㅜ' || name.charAt(i) == 'ㅡ' || name.charAt(i) == 'ㅒ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅖ' || name.charAt(i) == 'ㅢ' || name.charAt(i) == 'ㅟ' || name.charAt(i) == 'ㅝ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == 'ㅞ' || name.charAt(i) == 'ㅙ' || name.charAt(i) == 'ㅚ' || name.charAt(i) == 'ㅘ' ||    //한문자(char)단위로 비교.
					name.charAt(i) == '씹' || name.charAt(i) == '좃' || name.charAt(i) == '좆' || name.charAt(i) == 'ㅤ'){
				return false; 
			}
		}
		
		if (name.length() == 0) {
			return false;
		}

		int numOfNameBytes = 0;
		try {
			numOfNameBytes = name.getBytes("EUC-KR").length;
		} catch (UnsupportedEncodingException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			return false;
		}

		if (isAlphaNumeric(name)) {
			return false;
		}

		// XXX - 본청의 사양과 동등한가 미확인
		// 전각 문자가 5 문자를 넘는지, 전체로 12바이트를 넘으면(자) 무효인 이름으로 한다
		if (5 < (numOfNameBytes - name.length()) || 12 < numOfNameBytes) {
			return false;
		}

		if (BadNamesList.getInstance().isBadName(name)) {
			return false;
		}
		return true;
	}

	public C_CreateClan(byte abyte0[], GameClient clientthread)
			throws Exception {
		super(abyte0);
		String s = readS(); 

		L1PcInstance l1pcinstance = clientthread.getActiveChar();
		if (l1pcinstance == null) {
			return;
		}		
				if( isInvalidName(s))
		{
			l1pcinstance.sendPackets(new S_SystemMessage("잘못된 혈맹 이름입니다."));
			return;
		}

		if (l1pcinstance.isCrown()) { // 프린스 또는 프린세스
			if (l1pcinstance.getClanid() == 0 && l1pcinstance.getLevel() >= Config.ALT_ClanLevel) { // 레벨60이상군주만 창설가능

				if (!l1pcinstance.getInventory().checkItem(40308, 30000)) {
					l1pcinstance.sendPackets(new S_ServerMessage(337, "$4")); // \f1%0이 부족합니다.
					return;
				}
			for (L1Clan clan : L1World.getInstance().getAllClans()) { // \f1 같은 이름의 혈맹이 존재합니다.
					if (clan.getClanName().toLowerCase()
							.equals(s.toLowerCase())) {
						l1pcinstance.sendPackets(new S_ServerMessage(99)); // \f1 같은 이름의 혈맹이 존재합니다.
						return;
					}
				}
				L1Clan clan = ClanTable.getInstance().createClan(l1pcinstance,s); // 크란창설
				l1pcinstance.getInventory().consumeItem(40308, 30000);
				l1pcinstance.teleport(l1pcinstance.getX(), l1pcinstance.getY(), l1pcinstance.getMapId(), l1pcinstance.getHeading(),169, false, false);
				if (clan != null) {
					l1pcinstance.sendPackets(new S_ServerMessage(84, s)); // \f1%0 혈맹이 창설되었습니다.
				}
			} else {
				l1pcinstance.sendPackets(new S_SystemMessage("이미 혈맹이 있거나 레벨 " + Config.ALT_ClanLevel + "부터 이상만 창설할 수 있습니다.")); // 추가
			}
		} else {
			l1pcinstance.sendPackets(new S_ServerMessage(85)); // \f1프린스와 프린세스만이 혈맹을 창설할 수 있습니다.
		}
	}

	@Override
	public String getType() {
		return C_CREATE_CLAN;
	}

}
