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

import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Character;

public class S_RangeSkill extends ServerBasePacket {

	private static AtomicInteger _sequentialNumber = new AtomicInteger(0);

	public static final int TYPE_NODIR = 0;

	public static final int TYPE_DIR = 8;

	public static S_RangeSkill get(L1Character cha, L1Character[] target, int spellgfx, int actionId, int type) {
		S_RangeSkill s = new S_RangeSkill();
		s.writeC(actionId);
		s.writeD(cha.getId());
		s.writeH(cha.getX());
		s.writeH(cha.getY());
		switch (type) {
		case TYPE_NODIR: 
			s.writeC(cha.getHeading()); break;	
		case TYPE_DIR: 
			int newHeading = heading(cha.getX(), cha.getY(), target[0].getX(), target[0].getY());
			cha.setHeading(newHeading);
			s.writeC(cha.getHeading()); break;
		}
		s.writeD(_sequentialNumber.incrementAndGet()); // 번호가 겹치지 않게 보낸다.
		s.writeH(spellgfx);
		s.writeC(type); // 0:범위 6:원거리 8:범위&원거리
		s.writeH(0);
		s.writeH(target.length);
		for (int i = 0; i < target.length; i++) {
			s.writeD(target[i].getId());
			s.writeC(0x20); // 0:데미지 모션 있음 0이외:없음
		}
		return s;
	}
	private static int heading(int myx, int myy, int tx, int ty) {
		if (tx > myx && ty > myy) {
			return 3;
		} else if (tx < myx && ty < myy) {
			return 7;
		} else if (tx > myx && ty == myy) {
			return 2;
		} else if (tx < myx && ty == myy) {
			return 6;
		} else if (tx == myx && ty < myy) {
			return 0;
		} else if (tx == myx && ty > myy) {
			return 4;
		} else if (tx < myx && ty > myy) {
			return 5;
		} else {
			return 1;
		}
	}

	private S_RangeSkill() {
		writeC(Opcodes.S_OPCODE_RANGESKILLS);
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}