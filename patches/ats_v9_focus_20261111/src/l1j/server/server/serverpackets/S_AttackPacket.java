package l1j.server.server.serverpackets;

import l1j.server.Templates.L1Item;
import l1j.server.server.ActionCodes;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Character;

public class S_AttackPacket extends ServerBasePacket {
	
	/**
	 * 공격 미스 패킷
	 * @return miss
	 */
	public static S_AttackPacket get(L1Character cha, int objid, int type) {
		S_AttackPacket s = new S_AttackPacket();
		s.writeC(type);
		s.writeD(objid);
		s.writeD(cha.getId());
		s.writeC(78);
		s.writeC(0);
		s.writeC(cha.getHeading());
		s.writeD(0);
		s.writeC(0);
		return s;	
	}
	
	public static S_AttackPacket cri(L1Character cha, int objid, int weaponType, int attacktype) {
		S_AttackPacket s = new S_AttackPacket();
		int 무기액션 = 0;
		switch (weaponType) {
		case L1Item.TYPE_WEAPONID_CLAW: 무기액션 = ActionCodes.ACTION_ClawAttack; attacktype = 2;break; 
		case L1Item.TYPE_WEAPONID_EDORYU: 무기액션 = ActionCodes.ACTION_EdoryuAttack; attacktype = 4; break;		
		}
		s.writeC(무기액션);
		s.writeD(cha.getId());
		s.writeD(objid);
		s.writeH(2);
		s.writeC(cha.getHeading());
		s.writeH(0x0000);
		s.writeH(0x0000);
		s.writeC(attacktype);
		s.writeH(0x00);
		return s;	
	}		
	/**
	 * 공격 패킷 
	 * @param pc
	 * @return get
	 */
	public static S_AttackPacket get(L1Character cha, int objid, int weaponType, int attacktype) {
		S_AttackPacket s = new S_AttackPacket();
		int 무기액션 = 0;
		switch (weaponType) {
		case L1Item.TYPE_WEAPONID_SWORD: 무기액션 = ActionCodes.ACTION_SwordAttack; break;
		case L1Item.TYPE_WEAPONID_DAGGER: 무기액션 = ActionCodes.ACTION_DaggerAttack; break;
		case L1Item.TYPE_WEAPONID_TOHANDSWORD: 무기액션 = ActionCodes.ACTION_TwoHandSwordAttack; break;
		case L1Item.TYPE_WEAPONID_CLAW: 무기액션 = ActionCodes.ACTION_ClawAttack; break; 
		case L1Item.TYPE_WEAPONID_EDORYU: 무기액션 = ActionCodes.ACTION_EdoryuAttack; break;		
		default: 무기액션 = ActionCodes.ACTION_Attack; break;
		}
		s.writeC(무기액션);
		s.writeD(cha.getId());
		s.writeD(objid);
		s.writeH(2);
		s.writeC(cha.getHeading());
		s.writeH(0x0000);
		s.writeH(0x0000);
		s.writeC(attacktype);
		s.writeH(0x00);
		return s;	
	}
	private S_AttackPacket() {
		writeC(Opcodes.S_OPCODE_ATTACKPACKET);
	}
	@Override
	public byte[] getContent() {
		return getBytes();
	}
}