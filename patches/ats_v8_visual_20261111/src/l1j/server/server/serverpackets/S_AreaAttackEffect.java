package l1j.server.server.serverpackets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Character;

public class S_AreaAttackEffect extends ServerBasePacket {
	private static AtomicInteger _sequence = new AtomicInteger(0);

	public static S_AreaAttackEffect getNoDir(L1Character own, ArrayList<L1Character> tars, int spr, int act) {
		S_AreaAttackEffect s = new S_AreaAttackEffect();
		s.writeC(act);
		s.writeD(own.getId());
		s.writeH(own.getX());
		s.writeH(own.getY());
		s.writeC(own.getHeading());
		s.writeD(_sequence.incrementAndGet());
		s.writeH(spr);
		s.writeC(0);
		s.writeH(0);
		s.writeH(tars.size());
		for (L1Character c : tars) {
			s.writeD(c.getId());
			s.writeC(32);
			s.writeC(2);
		}
		s.writeH(0);
		return s;
	}
	private S_AreaAttackEffect() {
		writeC(Opcodes.S_OPCODE_RANGESKILLS);
	}

	public byte[] getContent() throws IOException {
		return getBytes();
	}
}
