package l1j.server.server.model.network.core.sc.packet;

import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.ActionCodes;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;

public final class SC_UseAttackSkill {

	private static final AtomicInteger SEQ =
			new AtomicInteger(0);

	private static final int WIRE_SIZE =
			ConnectionConfig.HEADER_SIZE + 33;

	private SC_UseAttackSkill() {
	}

	public static boolean send(
			CIOClient client,
			L1Character cha,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId) {

		return send(
				client,
				cha,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				6,
				true);
	}

	public static boolean send(
			CIOClient client,
			L1Character cha,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			boolean motion) {

		return send(
				client,
				cha,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				0,
				motion);
	}

	public static boolean send(
			CIOClient client,
			L1Character cha,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			int isHit) {

		return send(
				client,
				cha,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				isHit,
				true);
	}

	public static boolean send(
			CIOClient client,
			L1Character cha,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			int isHit,
			boolean withCastMotion) {

		if (cha == null) {
			return false;
		}

		final int fixedActionId =
				fixActionId(
						cha,
						actionId);

		final int myX = cha.getX();
		final int myY = cha.getY();

		final boolean invalidXY =
				(x == 0 && y == 0)
						|| (x == myX && y == myY);

		final int oldHeading =
				cha.getHeading();

		final int heading =
				invalidXY
						? oldHeading
						: calcHeading(
								myX,
								myY,
								x,
								y);

		cha.setHeading(heading);

		final int seq =
				SEQ.incrementAndGet();

		final int chaId =
				withCastMotion
						? cha.getId()
						: 0;

		return send0(
				client,
				fixedActionId,
				chaId,
				targetObj,
				isHit,
				heading,
				seq,
				spellGfx,
				myX,
				myY,
				x,
				y);
	}

	public static void broadcast(
			L1Character cha,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId) {

		broadcast(
				cha,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				6,
				true);
	}

	public static void broadcast(
			L1Character cha,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			boolean motion) {

		broadcast(
				cha,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				0,
				motion);
	}

	public static void broadcast(
			L1Character cha,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			int isHit) {

		broadcast(
				cha,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				isHit,
				true);
	}

	public static void broadcast(
			L1Character cha,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			int isHit,
			boolean withCastMotion) {

		if (cha == null) {
			return;
		}

		final int fixedActionId =
				fixActionId(
						cha,
						actionId);

		final int myX = cha.getX();
		final int myY = cha.getY();

		final boolean invalidXY =
				(x == 0 && y == 0)
						|| (x == myX && y == myY);

		final int oldHeading =
				cha.getHeading();

		final int heading =
				invalidXY
						? oldHeading
						: calcHeading(
								myX,
								myY,
								x,
								y);

		cha.setHeading(heading);

		final int seq =
				SEQ.incrementAndGet();

		final int chaId =
				withCastMotion
						? cha.getId()
						: 0;

		{
		    final L1World __cioWorld0 = L1World.getInstance();
		    final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(cha);
		    try {
		        for (L1PcInstance pc : __cioVisible0) {
        
        			if (pc == null) {
        				continue;
        			}
        
        			send0(
        					pc.getNetConnection(),
        					fixedActionId,
        					chaId,
        					targetObj,
        					isHit,
        					heading,
        					seq,
        					spellGfx,
        					myX,
        					myY,
        					x,
        					y);
        		}
		    } finally {
		        __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
		    }
		}
	}

	public static boolean sendAndBroadcast(
			L1PcInstance pc,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId) {

		return sendAndBroadcast(
				pc,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				6,
				true);
	}

	public static boolean sendAndBroadcast(
			L1PcInstance pc,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			boolean motion) {

		return sendAndBroadcast(
				pc,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				0,
				motion);
	}

	public static boolean sendAndBroadcast(
			L1PcInstance pc,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			int isHit) {

		return sendAndBroadcast(
				pc,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				isHit,
				true);
	}

	public static boolean sendAndBroadcast(
			L1PcInstance pc,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			int isHit,
			boolean withCastMotion) {

		if (pc == null) {
			return false;
		}

		final int fixedActionId =
				fixActionId(
						pc,
						actionId);

		final int myX = pc.getX();
		final int myY = pc.getY();

		final boolean invalidXY =
				(x == 0 && y == 0)
						|| (x == myX && y == myY);

		final int oldHeading =
				pc.getHeading();

		final int heading =
				invalidXY
						? oldHeading
						: calcHeading(
								myX,
								myY,
								x,
								y);

		pc.setHeading(heading);

		final int seq =
				SEQ.incrementAndGet();

		final int chaId =
				withCastMotion
						? pc.getId()
						: 0;

		final boolean selfSent =
				send0(
						pc.getNetConnection(),
						fixedActionId,
						chaId,
						targetObj,
						isHit,
						heading,
						seq,
						spellGfx,
						myX,
						myY,
						x,
						y);

		{
		    final L1World __cioWorld1 = L1World.getInstance();
		    final java.util.List<L1PcInstance> __cioVisible1 = __cioWorld1.borrowVisiblePlayerFast(pc);
		    try {
		        for (L1PcInstance visible : __cioVisible1) {
        
        			if (visible == null) {
        				continue;
        			}
        
        			send0(
        					visible.getNetConnection(),
        					fixedActionId,
        					chaId,
        					targetObj,
        					isHit,
        					heading,
        					seq,
        					spellGfx,
        					myX,
        					myY,
        					x,
        					y);
        		}
		    } finally {
		        __cioWorld1.releaseVisiblePlayerFast(__cioVisible1);
		    }
		}

		return selfSent;
	}


	/**
	 * Source character snapshot and broadcast anchor are intentionally separate.
	 * This preserves legacy sites that built S_UseAttackSkill from one character
	 * and broadcast it around another character.
	 */
	public static void broadcast(
			L1Character anchor,
			L1Character source,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId) {

		broadcast(
				anchor,
				source,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				6,
				true);
	}

	public static void broadcast(
			L1Character anchor,
			L1Character source,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			boolean motion) {

		broadcast(
				anchor,
				source,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				0,
				motion);
	}

	public static void broadcast(
			L1Character anchor,
			L1Character source,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			int isHit) {

		broadcast(
				anchor,
				source,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				isHit,
				true);
	}

	public static void broadcast(
			L1Character anchor,
			L1Character source,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			int isHit,
			boolean withCastMotion) {

		if (anchor == null || source == null) {
			return;
		}

		final int fixedActionId =
				fixActionId(source, actionId);

		final int myX = source.getX();
		final int myY = source.getY();

		final boolean invalidXY =
				(x == 0 && y == 0)
						|| (x == myX && y == myY);

		final int heading =
				invalidXY
						? source.getHeading()
						: calcHeading(myX, myY, x, y);

		source.setHeading(heading);

		final int seq = SEQ.incrementAndGet();

		final int chaId =
				withCastMotion
						? source.getId()
						: 0;

		broadcastPrepared(
				anchor,
				fixedActionId,
				chaId,
				targetObj,
				isHit,
				heading,
				seq,
				spellGfx,
				myX,
				myY,
				x,
				y);
	}

	/**
	 * One legacy packet instance sent to one explicit client and then broadcast
	 * around an arbitrary anchor. One sequence value is shared by all recipients.
	 */
	public static boolean sendAndBroadcast(
			CIOClient selfClient,
			L1Character source,
			L1Character anchor,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			boolean motion) {

		return sendAndBroadcast(
				selfClient,
				source,
				anchor,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				0,
				motion);
	}

	public static boolean sendAndBroadcast(
			CIOClient selfClient,
			L1Character source,
			L1Character anchor,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			int isHit,
			boolean withCastMotion) {

		if (source == null || anchor == null) {
			return false;
		}

		final int fixedActionId =
				fixActionId(source, actionId);

		final int myX = source.getX();
		final int myY = source.getY();

		final boolean invalidXY =
				(x == 0 && y == 0)
						|| (x == myX && y == myY);

		final int heading =
				invalidXY
						? source.getHeading()
						: calcHeading(myX, myY, x, y);

		source.setHeading(heading);

		final int seq = SEQ.incrementAndGet();

		final int chaId =
				withCastMotion
						? source.getId()
						: 0;

		final boolean selfSent =
				send0(
						selfClient,
						fixedActionId,
						chaId,
						targetObj,
						isHit,
						heading,
						seq,
						spellGfx,
						myX,
						myY,
						x,
						y);

		broadcastPrepared(
				anchor,
				fixedActionId,
				chaId,
				targetObj,
				isHit,
				heading,
				seq,
				spellGfx,
				myX,
				myY,
				x,
				y);

		return selfSent;
	}

	/**
	 * Preserves legacy reuse of one packet instance across two independent
	 * broadcast anchors. Overlapping viewers intentionally receive both sends,
	 * exactly as two legacy broadcast calls would.
	 */
	public static void broadcastPair(
			L1Character source,
			L1Character firstAnchor,
			L1Character secondAnchor,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			boolean motion) {

		broadcastPair(
				source,
				firstAnchor,
				secondAnchor,
				targetObj,
				spellGfx,
				x,
				y,
				actionId,
				0,
				motion);
	}

	public static void broadcastPair(
			L1Character source,
			L1Character firstAnchor,
			L1Character secondAnchor,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			int actionId,
			int isHit,
			boolean withCastMotion) {

		if (source == null) {
			return;
		}

		final int fixedActionId =
				fixActionId(source, actionId);

		final int myX = source.getX();
		final int myY = source.getY();

		final boolean invalidXY =
				(x == 0 && y == 0)
						|| (x == myX && y == myY);

		final int heading =
				invalidXY
						? source.getHeading()
						: calcHeading(myX, myY, x, y);

		source.setHeading(heading);

		final int seq = SEQ.incrementAndGet();

		final int chaId =
				withCastMotion
						? source.getId()
						: 0;

		if (firstAnchor != null) {
			broadcastPrepared(
					firstAnchor,
					fixedActionId,
					chaId,
					targetObj,
					isHit,
					heading,
					seq,
					spellGfx,
					myX,
					myY,
					x,
					y);
		}

		if (secondAnchor != null) {
			broadcastPrepared(
					secondAnchor,
					fixedActionId,
					chaId,
					targetObj,
					isHit,
					heading,
					seq,
					spellGfx,
					myX,
					myY,
					x,
					y);
		}
	}

	private static void broadcastPrepared(
			L1Character anchor,
			int actionId,
			int chaId,
			int targetObjId,
			int isHit,
			int heading,
			int seq,
			int spellGfx,
			int myX,
			int myY,
			int targetX,
			int targetY) {

		{
		    final L1World __cioWorld2 = L1World.getInstance();
		    final java.util.List<L1PcInstance> __cioVisible2 = __cioWorld2.borrowVisiblePlayerFast(anchor);
		    try {
		        for (L1PcInstance pc : __cioVisible2) {
        
        			if (pc == null) {
        				continue;
        			}
        
        			send0(
        					pc.getNetConnection(),
        					actionId,
        					chaId,
        					targetObjId,
        					isHit,
        					heading,
        					seq,
        					spellGfx,
        					myX,
        					myY,
        					targetX,
        					targetY);
        		}
		    } finally {
		        __cioWorld2.releaseVisiblePlayerFast(__cioVisible2);
		    }
		}
	}

	private static boolean send0(
			CIOClient client,
			int actionId,
			int chaId,
			int targetObjId,
			int isHit,
			int heading,
			int seq,
			int spellGfx,
			int myX,
			int myY,
			int targetX,
			int targetY) {

		if (client == null || !client.isConnected()) {
			return false;
		}

		final CUserSocket connection =
				client.getConnection();

		if (connection == null
				|| !connection.isClientConnected()) {
			return false;
		}


		final CIOFrame frame =
				connection.beginDirectFrame(
						WIRE_SIZE);

		if (frame == null) {
			return false;
		}

		final WriteBuffer buffer =
				frame.buffer();

		boolean committed = false;

		try {
			buffer.writeC(Opcodes.S_ATTACK);
			buffer.writeC(actionId);
			buffer.writeD(chaId);
			buffer.writeD(targetObjId);
			buffer.writeC(isHit);
			buffer.writeC(0);
			buffer.writeC(heading);
			buffer.writeD(seq);
			buffer.writeH(spellGfx);
			buffer.writeC(6);
			buffer.writeH(myX);
			buffer.writeH(myY);
			buffer.writeH(targetX);
			buffer.writeH(targetY);
			buffer.writeC(0);
			buffer.writeC(0);
			buffer.writeC(0);
			buffer.writeC(0);
			buffer.writeC(0);

			committed =
					connection.commitDirectFrame(
							frame);

			return committed;

		} finally {
			if (!committed) {
				connection.abortDirectFrame(frame);
			}
		}
	}

	private static int fixActionId(
			L1Character cha,
			int actionId) {

		int fixed = actionId;

		if (cha instanceof L1PcInstance) {
			if (cha.hasSkillEffect(
					L1SkillId.SHAPE_CHANGE)
					&& fixed
							== ActionCodes.ACTION_SkillAttack) {

				final int gfx =
						cha.getCurrentSpriteId();

				if (gfx == 5727
						|| gfx == 5730) {

					fixed =
							ActionCodes.ACTION_SkillBuff;

				} else if (gfx == 5733
						|| gfx == 5736) {

					fixed =
							ActionCodes.ACTION_Attack;
				}
			}
		}

		if (cha.equalsCurrentSprite(4013)) {
			fixed =
					ActionCodes.ACTION_Attack;
		}

		return fixed;
	}

	private static int calcHeading(
			int myX,
			int myY,
			int targetX,
			int targetY) {

		final int dx =
				Integer.compare(
						targetX,
						myX);

		final int dy =
				Integer.compare(
						targetY,
						myY);

		if (dx == 0 && dy < 0) {
			return 0;
		}

		if (dx > 0 && dy < 0) {
			return 1;
		}

		if (dx > 0 && dy == 0) {
			return 2;
		}

		if (dx > 0 && dy > 0) {
			return 3;
		}

		if (dx == 0 && dy > 0) {
			return 4;
		}

		if (dx < 0 && dy > 0) {
			return 5;
		}

		if (dx < 0 && dy == 0) {
			return 6;
		}

		if (dx < 0 && dy < 0) {
			return 7;
		}

		return 0;
	}
}
