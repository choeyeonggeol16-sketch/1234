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

import l1j.server.Templates.L1Item;
import l1j.server.server.ActionCodes;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;


public class S_AttackMissPacket extends ServerBasePacket {
	
	public static S_AttackMissPacket miss(L1PcInstance attacker, int targetId, int weaponType) {
		S_AttackMissPacket s = new S_AttackMissPacket();
		int 公扁咀记 = 0;
		switch (weaponType) {
		case L1Item.TYPE_WEAPONID_SWORD: 公扁咀记 = ActionCodes.ACTION_SwordAttack; break;
		case L1Item.TYPE_WEAPONID_DAGGER: 公扁咀记 = ActionCodes.ACTION_DaggerAttack; break;
		case L1Item.TYPE_WEAPONID_TOHANDSWORD: 公扁咀记 = ActionCodes.ACTION_TwoHandSwordAttack; break;
		case L1Item.TYPE_WEAPONID_EDORYU: 公扁咀记 = ActionCodes.ACTION_EdoryuAttack; break;
		case L1Item.TYPE_WEAPONID_CLAW: 公扁咀记 = ActionCodes.ACTION_ClawAttack; break;
		default: 公扁咀记 = ActionCodes.ACTION_Attack; break;
		}	
		s.writeC(公扁咀记);
		s.writeD(attacker.getId());
		s.writeD(targetId);
		s.writeC(0);
		s.writeC(0);
		s.writeC(attacker.getHeading());
		s.writeD(0);
		s.writeC(0);
		return s;
	}
    
	public static S_AttackMissPacket miss(L1Character attacker, int targetId) {
		S_AttackMissPacket s = new S_AttackMissPacket();
		s.writeC(ActionCodes.ACTION_Attack);
		s.writeD(attacker.getId());
		s.writeD(targetId);
		s.writeC(0);
		s.writeC(0);
		s.writeC(attacker.getHeading());
		s.writeD(0);
		s.writeC(0);
		return s;
	}

	public static S_AttackMissPacket miss(L1Character attacker, int targetId, int actId) {
		S_AttackMissPacket s = new S_AttackMissPacket();
		s.writeC(actId);
		s.writeD(attacker.getId());
		s.writeD(targetId);
		s.writeC(0);
		s.writeC(0);
		s.writeC(attacker.getHeading());
		s.writeD(0);
		s.writeC(0);
		return s;
	}
    private S_AttackMissPacket() {
    	writeC(Opcodes.S_OPCODE_ATTACKPACKET);
    }
	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
