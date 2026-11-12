package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;

import l1j.server.server.model.network.core.CIOClient;

public final class SC_PetMenuPacket {

    private SC_PetMenuPacket() {}

    public static boolean send(L1PcInstance pc, L1NpcInstance npc, int expPercent) {
        return pc != null && send(pc.getNetConnection(), npc, expPercent);
    }

    public static boolean sendTwice(L1PcInstance pc, L1NpcInstance npc, int expPercent) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        final boolean first = send(client, npc, expPercent);
        final boolean second = send(client, npc, expPercent);
        return first && second;
    }

    public static boolean send(CIOClient client, L1NpcInstance npc, int expPercent) {
        if (client == null || !client.isConnected() || npc == null) return false;
        if (npc.getNpcTemplate() != null && npc.getNpcTemplate().get_npcId() == 7420207) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;

        final boolean isPet = npc instanceof L1PetInstance;
        final boolean isSummon = npc instanceof L1SummonInstance;
        if (!isPet && !isSummon) return false;
        final String htmlId = isPet ? "anicom" : "moncom";
        final String status = isPet
                ? resolvePetStatusString(((L1PetInstance) npc).getCurrentPetStatus())
                : resolveSummonStatusString(((L1SummonInstance) npc).get_currentPetStatus());
        final String hp = Integer.toString(npc.getCurrentHp());
        final String maxHp = Integer.toString(npc.getMaxHp());
        final String mp = Integer.toString(npc.getCurrentMp());
        final String maxMp = Integer.toString(npc.getMaxMp());
        final String level = Integer.toString(npc.getLevel());
        final String name = isPet && npc.getName() != null ? npc.getName() : "";
        final String fullness = isPet ? "$612" : "";
        final String percent = isPet ? Integer.toString(expPercent) : "";
        final String lawful = isPet ? Integer.toString(npc.getLawful()) : "";

        long size = 2L + 1 + 4 + 1 + 2 + 1;
        size += 2L * ((long) htmlId.length() + status.length() + hp.length()
                + maxHp.length() + mp.length() + maxMp.length() + level.length()) + 6;
        if (isPet) {
            size += 2L * ((long) name.length() + fullness.length() + percent.length() + lawful.length()) + 4;
        }
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT);
            buffer.writeD(npc.getId());
            buffer.writeS(htmlId);
            buffer.writeC(0);
            buffer.writeH(isPet ? 10 : 6);
            buffer.writeS(status);
            buffer.writeS(hp);
            buffer.writeS(maxHp);
            buffer.writeS(mp);
            buffer.writeS(maxMp);
            buffer.writeS(level);
            if (isPet) {
                buffer.writeS(name);
                buffer.writeS(fullness);
                buffer.writeS(percent);
                buffer.writeS(lawful);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

	private static String resolvePetStatusString(int status) {
		switch (status) {
		case 1:
			return "$469"; // 공격 태세
		case 2:
			return "$470"; // 방어 태세
		case 3:
			return "$471"; // 휴게
		case 5:
			return "$472"; // 경계
		case 8:
			return "$613"; // 수집
		default:
			return "$471"; // 휴게
		}
	}

	private static String resolveSummonStatusString(int status) {
		switch (status) {
		case 1:
			return "$469"; // 공격 태세
		case 2:
			return "$470"; // 방어 태세
		case 3:
			return "$471"; // 휴게
		case 5:
			return "$472"; // 경계
		default:
			return "$471"; // 휴게
		}
	}

}
