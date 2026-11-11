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

package l1j.server.server.serverpackets;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.ActionCodes;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Character;

public class S_UseArrowSkill extends ServerBasePacket {
	
	private static AtomicInteger _sequentialNumber = new AtomicInteger(0);
	
	public static S_UseArrowSkill get(L1Character cha, int targetobj, int spellgfx, int x, int y) {
		
		int action = ActionCodes.ACTION_Attack;
		if (cha.getCurrentSpriteId() == 3860 || cha.getCurrentSpriteId() == 14170) {
			action = ActionCodes.ACTION_BowAttack;
		} else if(cha.getCurrentSpriteId() == 11714) {
			action = ActionCodes.ACTION_AltAttack;
		}
		S_UseArrowSkill s = new S_UseArrowSkill();
		s.writeC(action);
		s.writeD(cha.getId());
		s.writeD(targetobj);
		s.writeC(6);
		s.writeC(0);
		s.writeC(cha.getHeading()); 
		s.writeD(_sequentialNumber.incrementAndGet());
		s.writeH(spellgfx);
		s.writeC(127); // 스킬 사용시의 광원의 넓이?
		s.writeH(cha.getX());
		s.writeH(cha.getY());
		s.writeH(x);
		s.writeH(y); 
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		return s;
	}

	public static S_UseArrowSkill get(L1Character cha, int targetobj, int spellgfx, int x, int y, boolean isHit) {
		int action = ActionCodes.ACTION_Attack;
		// 오크 궁수에만 변경
		if (cha.getCurrentSpriteId() == 3860 || cha.getCurrentSpriteId() == 14170) {
			action = ActionCodes.ACTION_BowAttack;
		}
		S_UseArrowSkill s = new S_UseArrowSkill();
		s.writeC(action);
		s.writeD(cha.getId());
		s.writeD(targetobj);
		s.writeC(isHit ? 6 : 0);
		s.writeC(0);
		s.writeC(cha.getHeading());
		s.writeD(_sequentialNumber.incrementAndGet());
		s.writeH(spellgfx);
		s.writeC(127);
		s.writeH(cha.getX());
		s.writeH(cha.getY());
		s.writeH(x);
		s.writeH(y);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		s.writeC(0);
		return s;
	}
	private S_UseArrowSkill() {
		writeC(Opcodes.S_OPCODE_ATTACKPACKET);
	}
    @Override
	public byte[] getContent() throws IOException {
		return getBytes();
	}
}
