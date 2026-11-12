package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;

public final class SC_ReturnedStat {

	public static final int START = 1;
	public static final int LEVELUP = 2;
	public static final int END = 3;
	public static final int LOGIN = 4;
	public static final int PET_PARTY = 12;
	public static final int Unknown_LOGIN2 = 0x44; // 문제
	public static final int RING_RUNE_SLOT = 0x43;
	public static final int UI4 = 68;
	public static final int UI5 = 65;
	public static final int 파워북검색 = 25;
	public static final int CharNameChange = 29; // 29,31,52,

	public static final int SUBTYPE_RING = 1;
	public static final int SUBTYPE_RUNE = 2;

	public static final int OPEN_SLOT_LRING	= 0x04;	// 왼쪽 반지.
	public static final int OPEN_SLOT_RRING	= 0x08;	// 오른쪽 반지
	public static final int OPEN_SLOT_EARRING = 0x10;	// 오른쪽 귀걸이/반지류
	public static final int OPEN_SLOT_SHOULD = 0x40;	// 견갑
	public static final int OPEN_SLOT_BADGE	= 0x80;	// 휘장
	public static final int OPEN_SLOT_ALL = OPEN_SLOT_LRING | OPEN_SLOT_RRING | OPEN_SLOT_EARRING | OPEN_SLOT_SHOULD | OPEN_SLOT_BADGE;

    private SC_ReturnedStat() {}

    public static boolean sendPc(L1PcInstance pc, int type) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(64);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_VOICE_CHAT);
            buffer.writeC(type);
            switch (type) {
                case START: {
short initHp = 0;
			short initMp = 0;
			if (pc.isCrown()) {
				initHp = 14;
				int wis = pc.getAbility() != null ? pc.getAbility().getBaseWis() : 12;
				if (wis == 11) initMp = 2;
				else if (wis >= 12 && wis <= 15) initMp = 3;
				else if (wis >= 16 && wis <= 18) initMp = 4;
				else initMp = 2;
			} else if (pc.isKnight()) {
				initHp = 16;
				int wis = pc.getAbility() != null ? pc.getAbility().getBaseWis() : 10;
				if (wis >= 9 && wis <= 11) initMp = 1;
				else if (wis == 12 || wis == 13) initMp = 2;
				else initMp = 1;
			} else if (pc.isElf()) {
				initHp = 15;
				int wis = pc.getAbility() != null ? pc.getAbility().getBaseWis() : 12;
				if (wis >= 12 && wis <= 15) initMp = 4;
				else if (wis >= 16 && wis <= 18) initMp = 6;
				else initMp = 4;
			} else if (pc.isWizard()) {
				initHp = 12;
				int wis = pc.getAbility() != null ? pc.getAbility().getBaseWis() : 12;
				if (wis >= 12 && wis <= 15) initMp = 6;
				else if (wis >= 16 && wis <= 18) initMp = 8;
				else initMp = 6;
			} else if (pc.isDarkelf()) {
				initHp = 12;
				int wis = pc.getAbility() != null ? pc.getAbility().getBaseWis() : 10;
				if (wis == 10 || wis == 11) initMp = 3;
				else if (wis >= 12 && wis <= 15) initMp = 4;
				else if (wis >= 16 && wis <= 18) initMp = 6;
				else initMp = 3;
			} else if (pc.isDragonknight()) {
				initHp = 16;
				initMp = 2;
			} else if (pc.isBlackwizard()) {
				initHp = 14;
				int wis = pc.getAbility() != null ? pc.getAbility().getBaseWis() : 10;
				if (wis >= 10 && wis <= 15) initMp = 5;
				else if (wis >= 16 && wis <= 18) initMp = 6;
				else initMp = 5;
			} else if (pc.isWarrior()) {
				initHp = 16;
				int wis = pc.getAbility() != null ? pc.getAbility().getBaseWis() : 10;
				if (wis >= 9 && wis <= 11) initMp = 1;
				else if (wis == 12 || wis == 13) initMp = 2;
				else initMp = 1;
			}
                    buffer.writeH(initHp);
                    buffer.writeH(initMp);
                    buffer.writeC(10);
                    buffer.writeC(ExpTable.getLevelByExp(pc.getReturnStat()));
                    break;
                }
                case LEVELUP: {
                    if (pc.rst != null) {
                        buffer.writeC(pc.rst.level);
                        buffer.writeC(ExpTable.getLevelByExp(pc.getReturnStat()));
                        buffer.writeH(pc.rst.baseHp + pc.rst.upHp);
                        buffer.writeH(pc.rst.baseMp + pc.rst.upMp);
                        buffer.writeH(pc.rst.ac);
                        buffer.writeC(pc.rst.str + pc.rst.basestr);
                        buffer.writeC(pc.rst.Int + pc.rst.baseint);
                        buffer.writeC(pc.rst.wis + pc.rst.basewis);
                        buffer.writeC(pc.rst.dex + pc.rst.basedex);
                        buffer.writeC(pc.rst.con + pc.rst.basecon);
                        buffer.writeC(pc.rst.cha + pc.rst.basecha);
                    } else {
                        for (int i = 0; i < 14; i++) buffer.writeC(0);
                    }
                    break;
                }
                case END:
                    buffer.writeC(pc.getElixirStats());
                    break;
                case LOGIN: {
                    int first = 0, second = 0, third = 0;
                    if (pc.getAbility() != null) {
                        int[] stat = pc.getAbility().getMinStat(pc.getClassId());
                        if (stat != null && stat.length >= 6) {
                            first = stat[0] + stat[5] * 16;
                            second = stat[3] + stat[1] * 16;
                            third = stat[2] + stat[4] * 16;
                        }
                    }
                    buffer.writeC(first);
                    buffer.writeC(second);
                    buffer.writeC(third);
                    buffer.writeC(0);
                    break;
                }
                case UI4:
                    buffer.writeD(1); buffer.writeC(12); buffer.writeH(2240);
                    break;
                case UI5:
                    buffer.writeC(0); buffer.writeH(45500);
                    break;
                case CharNameChange:
                    buffer.writeC(0);
                    break;
                default:
                    return false;
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static boolean sendSimple(L1PcInstance pc, int type, int a, int b, int c, boolean flag, int mode) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(64);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_VOICE_CHAT);
            buffer.writeC(type);
            if (mode == 1) { // pet party
                buffer.writeC(a);
                buffer.writeC(0);
                if (flag) buffer.writeD(0);
                else { buffer.writeC(1); buffer.writeC(0); buffer.writeC(0); buffer.writeC(0); }
                buffer.writeD(b);
            } else if (mode == 2) { // ring/rune slot
                if (type == RING_RUNE_SLOT) {
                    buffer.writeD(a);
                    if (a == SUBTYPE_RING) buffer.writeC(b == 2 ? 15 : b == 1 ? 7 : 3);
                    else if (a == SUBTYPE_RUNE) buffer.writeC(1);
                    for (int i = 0; i < 6; i++) buffer.writeD(0);
                    buffer.writeH(0);
                } else if (type == Unknown_LOGIN2) {
                    buffer.writeD(1); buffer.writeC(OPEN_SLOT_ALL); buffer.writeH(0);
                }
            } else if (mode == 3) { // clan emblem
                buffer.writeD(a); buffer.writeD(b);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean sendPetParty(L1PcInstance pc, int count, int petId, boolean added) {
        return sendSimple(pc, PET_PARTY, count, petId, 0, added, 1);
    }

    public static boolean sendSlot(L1PcInstance pc, int type, int subType, int value) {
        return sendSimple(pc, type, subType, value, 0, false, 2);
    }

    public static boolean sendEmblem(L1PcInstance pc, int objId, int emblemId) {
        return sendSimple(pc, 0x3c, objId, emblemId, 0, false, 3);
    }

    public static void broadcastEmblem(L1Character source, int objId, int emblemId) {
        if (source == null) return;
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(source))
            sendEmblem(pc, objId, emblemId);
    }

}
