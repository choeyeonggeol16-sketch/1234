package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;

public final class IntegratedBuff extends L1ItemInstance {

	private static final long serialVersionUID = 1L;
	
	private static final int[] BLOCKED_EFFECTS = {
			157,
			33,
			10101,
			30007,
			30002,
			30006,
			40007,
			30081,
			22055,
			22025,
			22026,
			22027,
			30004,
			87,
			208,
			103,
			212,
			123
	};

	public IntegratedBuff(L1Item item) {
		super(item);
	}

	public static void checkCondition(L1PcInstance pc, L1ItemInstance item) {
		if (pc == null || item == null) {
			return;
		}

		if (pc.hasSkillEffect(71)) {
			SC_ServerMessage.send(pc, 698);
			return;
		}


		for (int i = 0; i < BLOCKED_EFFECTS.length; i++) {
			if (pc.hasSkillEffect(BLOCKED_EFFECTS[i])) {
				return;
			}
		}

		IceBuffItem.use(pc, item);
	}
}