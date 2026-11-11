package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Character;

public class S_MoveCharPacket extends ServerBasePacket {
	
	public static S_MoveCharPacket get(L1Character cha, int head) {
		S_MoveCharPacket s = new S_MoveCharPacket();
		s.writeD(cha.getId());
		s.writeH(toLocX(cha, head));
		s.writeH(toLocY(cha, head));
		s.writeC(head);
		s.writeH(0);
		return s;
	}	
	
	private S_MoveCharPacket() {
		writeC(Opcodes.S_OPCODE_MOVEOBJECT);
	}	
	private static int toLocX(L1Character cha, int h) {
		int x = cha.getX();
		switch(h) {
		case 1: case 2: case 3: x--; break;
		case 5: case 6: case 7: x++; break;
		}
		return x;
	}
	private static int toLocY(L1Character cha, int h) {
		int y = cha.getY();
		switch(h) {
		case 0: case 1: case 7: y++; break;
		case 3: case 4: case 5: y--; break;
		}
		return y;
	}
	@Override
	public byte[] getContent() {
		return getBytes();
	}
}