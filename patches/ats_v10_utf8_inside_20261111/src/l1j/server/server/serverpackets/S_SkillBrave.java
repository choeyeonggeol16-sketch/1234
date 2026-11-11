
package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;

public class S_SkillBrave extends ServerBasePacket {

	public static S_SkillBrave get(int i, int j, int k) {
		S_SkillBrave s = new S_SkillBrave();
		s.writeD(i);
		s.writeC(j);
		s.writeH(k);
		return s;
	}
    private S_SkillBrave() {
    	writeC(Opcodes.S_OPCODE_SKILLBRAVE);
    }
	
	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
