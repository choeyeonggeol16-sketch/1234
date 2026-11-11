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

package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;

public class S_SkillBuy extends ServerBasePacket {
	public S_SkillBuy(int o, L1PcInstance pc) {	
		int count = Scount(pc);
		int inCount = 0;	
		for (int k = 0; k < count; k++) {
			if (!pc.isSkillMastery((k + 1))) {
				inCount++;
			}
		}
		try {
			writeC(Opcodes.S_OPCODE_SKILLBUY);
			writeD(100);
			writeH(inCount);
			for (int k = 0; k < count; k++) {
				if (!pc.isSkillMastery((k + 1))) {
					writeD(k);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int Scount(L1PcInstance player) {
		int RC = 0;
		switch (player.getType()) {
		case 0: // 군주
			RC = 16;
			break;
		case 1: // 기사
			RC = 8;
			break;
		case 2: // 엘프
			RC = 23;
			break;
		case 3: // 마법사
			RC = 23;
			break;
		case 4:  // 다크엘프
			RC = 16;
			break;
		default:
			break;
		}
		return RC;

	}


	@Override
	public byte[] getContent() {
		return getBytes();
	}
	@Override
	public String getType() {
		return _S__1B_WAR;
	}

	private static final String _S__1B_WAR = "[S] S_SkillBuy";

}
