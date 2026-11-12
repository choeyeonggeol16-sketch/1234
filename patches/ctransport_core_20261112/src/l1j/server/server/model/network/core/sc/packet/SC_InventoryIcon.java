package l1j.server.server.model.network.core.sc.packet;

import java.util.HashMap;

import l1j.server.MJCTSystem.MJCTSpell;
import l1j.server.MJCTSystem.Loader.MJCTSpellLoader;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;

public final class SC_InventoryIcon {

	public static final int SHOW_INVEN_BUFFICON = 110;

	public static final int TYPE_EFF_NONE = 0x00;
	public static final int TYPE_EFF_PERCENT = 0x01;
	public static final int TYPE_EFF_MINUTE = 0x02;
	public static final int TYPE_EFF_PERCENT_ORC_SERVER = 0x03;
	public static final int TYPE_EFF_EINHASAD_COOLTIME_MINUTE = 0x04;
	public static final int TYPE_EFF_LEGACY_TIME = 0x05;
	public static final int TYPE_EFF_VARIABLE_VALUE = 0x06;
	public static final int TYPE_EFF_DAY_HOUR_MIN = 0x07;
	public static final int TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC = 0x08;
	public static final int TYPE_EFF_NSERVICE_TOPPING = 0x09;
	public static final int TYPE_EFF_UNLIMIT = 0x0A;
	public static final int TYPE_EFF_CUSTOM = 0x0B;
	public static final int TYPE_EFF_COUNT = 0x0C;

	/*
	 * iconId:
	 *   0이면 MJCTSpell.xicon 사용
	 *   0보다 크면 여기 등록된 아이콘 번호를 우선 사용
	 *
	 * tooltipId / newId / endId:
	 *   클라이언트 string 번호
	 */
	private static final HashMap<Integer, StrMessageId> _skillIdToMsgIds =
			new HashMap<Integer, StrMessageId>(32);

	static {
		_skillIdToMsgIds.put(L1SkillId.ABSOLUTE_BLADE,
				new StrMessageId(4735, 0, 4742));
		_skillIdToMsgIds.put(L1SkillId.DEATH_HEAL,
				new StrMessageId(4737, 0, 4744));
		_skillIdToMsgIds.put(L1SkillId.GRACE_AVATAR,
				new StrMessageId(4734, 0, 4741));
		_skillIdToMsgIds.put(L1SkillId.SOUL_BARRIER,
				new StrMessageId(4736, 0, 4743));
		_skillIdToMsgIds.put(L1SkillId.IMPACT,
				new StrMessageId(4761, 0, 4754));
		_skillIdToMsgIds.put(L1SkillId.TITANL_RISING,
				new StrMessageId(4740, 0, 4747));
		_skillIdToMsgIds.put(L1SkillId.Tam_Fruit1,
				new StrMessageId(4181, 0, 0));
		_skillIdToMsgIds.put(L1SkillId.CUBE_OGRE,
				new StrMessageId(3074, 0, 0));
		_skillIdToMsgIds.put(L1SkillId.CUBE_GOLEM,
				new StrMessageId(3075, 0, 0));
		_skillIdToMsgIds.put(L1SkillId.CUBE_RICH,
				new StrMessageId(1348, 0, 0));
		_skillIdToMsgIds.put(L1SkillId.CUBE_AVATAR,
				new StrMessageId(3073, 0, 0));
		_skillIdToMsgIds.put(L1SkillId.FOCUS_SPRITS,
				new StrMessageId(5272, 0, 5273));
		_skillIdToMsgIds.put(L1SkillId.DEATH_HEAL_Mob,
				new StrMessageId(4737, 0, 4744));

		_skillIdToMsgIds.put(
				L1SkillId.BUFF_특별한성장축복,
				new StrMessageId(8135, 4992, 0, 0));

		_skillIdToMsgIds.put(
				L1SkillId.BUFF_특별한방어축복,
				new StrMessageId(8136, 4993, 0, 0));

		_skillIdToMsgIds.put(
				L1SkillId.BUFF_특별한공격축복,
				new StrMessageId(8137, 4994, 0, 0));
	}

	private static final StrMessageId _dummyId =
			new StrMessageId(0, 0, 0, 0);

	public static class StrMessageId {
		public final int iconId;
		public final int tooltipId;
		public final int newId;
		public final int endId;

		/*
		 * 기존 등록 코드 호환용.
		 * 아이콘은 MJCTSpell.xicon을 사용한다.
		 */
		public StrMessageId(int tooltipId, int newId, int endId) {
			this(0, tooltipId, newId, endId);
		}

		/*
		 * 아이콘까지 직접 지정할 때 사용.
		 */
		public StrMessageId(
				int iconId,
				int tooltipId,
				int newId,
				int endId) {

			this.iconId = iconId;
			this.tooltipId = tooltipId;
			this.newId = newId;
			this.endId = endId;
		}
	}


	private static StrMessageId getInfo(int skillId) {
		final StrMessageId sid = _skillIdToMsgIds.get(skillId);
		return sid != null ? sid : _dummyId;
	}

	private static int resolveIconId(int skillId, StrMessageId sid) {
		if (sid != null && sid.iconId > 0) {
			return sid.iconId;
		}

		final MJCTSpell sp =
				MJCTSpellLoader.getInstance().get(skillId);

		return sp != null ? sp.xicon : 0;
	}

    private SC_InventoryIcon() {}

    public static boolean on(L1PcInstance receiver, int priority, int type, int skillId, int showType,
                             long time, boolean isGood, int invGfx, int tooltip, int startId, int endId) {
        return sendRaw(receiver, 1, priority, type, skillId, showType, time,
                       isGood, invGfx, tooltip, startId, endId);
    }

    public static boolean on(L1PcInstance receiver, int type, int skillId, long time,
                             boolean isGood, int invGfx) {
        final StrMessageId sid = getInfo(skillId);
        return on(receiver, 3, type, skillId, TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC,
                  time, isGood, invGfx, sid.tooltipId, sid.newId, sid.endId);
    }

    public static boolean on(L1PcInstance receiver, int type, int skillId, long time, boolean isGood) {
        final StrMessageId sid = getInfo(skillId);
        return on(receiver, 3, type, skillId, TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC, time,
                  isGood, resolveIconId(skillId, sid), sid.tooltipId, sid.newId, sid.endId);
    }

    public static boolean onUnLimit(L1PcInstance receiver, int type, int skillId, long time,
                                    boolean isGood, int invGfx) {
        final StrMessageId sid = getInfo(skillId);
        return on(receiver, 3, type, skillId, TYPE_EFF_UNLIMIT, time, isGood,
                  invGfx, sid.tooltipId, sid.newId, sid.endId);
    }

    public static boolean onUnLimit(L1PcInstance receiver, int priority, int type, int skillId,
                                    long time, boolean isGood) {
        final StrMessageId sid = getInfo(skillId);
        return on(receiver, priority, type, skillId, TYPE_EFF_UNLIMIT, time, isGood,
                  resolveIconId(skillId, sid), sid.tooltipId, sid.newId, sid.endId);
    }

    public static boolean icoNew(L1PcInstance receiver, int skillId, long time, boolean isGood) {
        return on(receiver, 1, skillId, time, isGood);
    }

    public static boolean icoNew(L1PcInstance receiver, int skillId, int tooltip,
                                 long time, boolean isGood) {
        return on(receiver, 3, 1, skillId, TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC, time,
                  isGood, skillId, tooltip, 0, 0);
    }

    public static boolean iconNewUnLimit(L1PcInstance receiver, int skillId, boolean isGood) {
        return onUnLimit(receiver, 3, 1, skillId, TYPE_EFF_UNLIMIT, isGood);
    }

    public static boolean iconNewUnLimitAndPriority(L1PcInstance receiver, int priority,
                                                    int skillId, boolean isGood) {
        return onUnLimit(receiver, priority, 1, skillId, TYPE_EFF_UNLIMIT, isGood);
    }

    public static boolean iconNewUnLimit(L1PcInstance receiver, int skillId, int tooltip,
                                         boolean isGood) {
        return on(receiver, 3, 1, skillId, TYPE_EFF_UNLIMIT, 1, isGood, skillId, tooltip, 0, 0);
    }

    public static boolean iconNewUnLimitAndPriority(L1PcInstance receiver, int priority,
                                                    int skillId, int tooltip, boolean isGood) {
        return on(receiver, priority, 1, skillId, TYPE_EFF_UNLIMIT, 1,
                  isGood, skillId, tooltip, 0, 0);
    }

    public static boolean icoReset(L1PcInstance receiver, int skillId, int tooltip,
                                   long time, boolean isGood) {
        return on(receiver, 3, 2, skillId, TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC, time,
                  isGood, skillId, tooltip, 0, 0);
    }

    public static boolean icoReset(L1PcInstance receiver, int skillId, long time, boolean isGood) {
        return on(receiver, 2, skillId, time, isGood);
    }

    public static boolean icoEnd(L1PcInstance receiver, int skillId) {
        return sendRaw(receiver, 2, 0, 0, skillId, 0, 0L, false, 0, 0, 0, 0);
    }

    private static boolean sendRaw(L1PcInstance pc, int packetType, int priority, int type,
                                   int skillId, int showType, long time, boolean good,
                                   int gfx, int tooltip, int startId, int endId) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(packetType == 1 ? 128 : 64);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writePayload(frame.buffer(), packetType, priority, type, skillId, showType,
                    time, good, gfx, tooltip, startId, endId);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static void writePayload(WriteBuffer buffer, int packetType, int priority, int type,
                                     int skillId, int showType, long time, boolean good,
                                     int gfx, int tooltip, int startId, int endId) {
        buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
        buffer.writeH(SHOW_INVEN_BUFFICON);
        buffer.writeC(0x08);
        buffer.writeC(packetType == 1 ? type : 0x03);
        buffer.writeC(0x10);
        buffer.writeBit(skillId);
        if (packetType == 1) {
            buffer.writeC(0x18);
            buffer.writeBit(time);
            buffer.writeC(0x20);
            buffer.writeBit(showType);
            buffer.writeC(0x28);
            buffer.writeBit(gfx);
        }
        buffer.writeC(0x30);
        buffer.writeC(0);
        if (packetType == 1) {
            buffer.writeC(0x38);
            buffer.writeC(priority);
            buffer.writeC(0x40);
            buffer.writeBit(tooltip);
            buffer.writeC(0x48);
            buffer.writeBit(startId);
            buffer.writeC(0x50);
            buffer.writeBit(endId);
            buffer.writeC(0x58);
            buffer.writeC(good ? 1 : 0);
            buffer.writeC(0x60);
            buffer.writeC(0);
            buffer.writeC(0x68);
            buffer.writeC(0);
            buffer.writeC(0x70);
            buffer.writeC(0);
        } else {
            buffer.writeC(0x50);
            buffer.writeC(0);
            buffer.writeH(0);
        }
    }
}
