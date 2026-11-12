package l1j.server.server.model.network.core.sc.packet;

import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;

public final class SC_AttackCritical {

	private static final AtomicInteger SEQUENTIAL_NUMBER =
			new AtomicInteger(0);

	private static final int WIRE_SIZE_MODE_1 =
			ConnectionConfig.HEADER_SIZE + 24;

	private static final int WIRE_SIZE_MODE_2 =
			ConnectionConfig.HEADER_SIZE + 32;

	private SC_AttackCritical() {
	}

	// ----------------------------------------------------------------
	// mode 1
	// S_AttackCritical(L1PcInstance pc, int objid, int type, boolean doubleWeapon)
	// ----------------------------------------------------------------

	public static boolean send(
			CIOClient client,
			L1PcInstance pc,
			int targetId,
			int type,
			boolean doubleWeapon) {

		if (pc == null) {
			return false;
		}

		return sendMode1(
				client,
				pc.getId(),
				targetId,
				pc.getHeading(),
				resolveMode1Gfx(
						type,
						doubleWeapon));
	}

	public static void broadcast(
			L1PcInstance pc,
			int targetId,
			int type,
			boolean doubleWeapon) {

		if (pc == null) {
			return;
		}

		broadcastMode1(
				pc,
				pc.getId(),
				targetId,
				pc.getHeading(),
				resolveMode1Gfx(
						type,
						doubleWeapon));
	}

	public static void broadcast(
			L1Character anchor,
			L1PcInstance pc,
			int targetId,
			int type,
			boolean doubleWeapon) {

		if (anchor == null || pc == null) {
			return;
		}

		broadcastMode1(
				anchor,
				pc.getId(),
				targetId,
				pc.getHeading(),
				resolveMode1Gfx(
						type,
						doubleWeapon));
	}

	public static boolean sendAndBroadcast(
			L1PcInstance pc,
			int targetId,
			int type,
			boolean doubleWeapon) {

		if (pc == null) {
			return false;
		}

		final int attackerId = pc.getId();
		final int heading = pc.getHeading();
		final int gfxId =
				resolveMode1Gfx(
						type,
						doubleWeapon);

		final boolean selfSent =
				sendMode1(
						pc.getNetConnection(),
						attackerId,
						targetId,
						heading,
						gfxId);

		broadcastMode1(
				pc,
				attackerId,
				targetId,
				heading,
				gfxId);

		return selfSent;
	}

	// ----------------------------------------------------------------
	// mode 2
	// S_AttackCritical(L1Character cha, int targetobj, int x, int y,
	//                  int type, boolean isHit)
	// ----------------------------------------------------------------

	public static boolean send(
			CIOClient client,
			L1Character cha,
			int targetId,
			int x,
			int y,
			int type,
			boolean isHit) {

		if (cha == null) {
			return false;
		}

		final int seq =
				SEQUENTIAL_NUMBER.incrementAndGet();

		return sendMode2(
				client,
				cha.getId(),
				targetId,
				cha.getHeading(),
				resolveAid(
						cha.getCurrentSpriteId()),
				isHit,
				seq,
				resolveMode2Gfx(
						cha.getCurrentSpriteId(),
						type),
				x,
				y,
				cha.getX(),
				cha.getY());
	}

	public static void broadcast(
			L1Character cha,
			int targetId,
			int x,
			int y,
			int type,
			boolean isHit) {

		if (cha == null) {
			return;
		}

		broadcast(
				cha,
				cha,
				targetId,
				x,
				y,
				type,
				isHit);
	}

	public static void broadcast(
			L1Character anchor,
			L1Character cha,
			int targetId,
			int x,
			int y,
			int type,
			boolean isHit) {

		if (anchor == null || cha == null) {
			return;
		}

		final int spriteId =
				cha.getCurrentSpriteId();

		final int seq =
				SEQUENTIAL_NUMBER.incrementAndGet();

		broadcastMode2(
				anchor,
				cha.getId(),
				targetId,
				cha.getHeading(),
				resolveAid(spriteId),
				isHit,
				seq,
				resolveMode2Gfx(
						spriteId,
						type),
				x,
				y,
				cha.getX(),
				cha.getY());
	}

	public static boolean sendAndBroadcast(
			L1PcInstance pc,
			int targetId,
			int x,
			int y,
			int type,
			boolean isHit) {

		if (pc == null) {
			return false;
		}

		final int spriteId =
				pc.getCurrentSpriteId();

		final int seq =
				SEQUENTIAL_NUMBER.incrementAndGet();

		final int attackerId =
				pc.getId();

		final int heading =
				pc.getHeading();

		final int aid =
				resolveAid(spriteId);

		final int gfxId =
				resolveMode2Gfx(
						spriteId,
						type);

		final int chaX =
				pc.getX();

		final int chaY =
				pc.getY();

		final boolean selfSent =
				sendMode2(
						pc.getNetConnection(),
						attackerId,
						targetId,
						heading,
						aid,
						isHit,
						seq,
						gfxId,
						x,
						y,
						chaX,
						chaY);

		broadcastMode2(
				pc,
				attackerId,
				targetId,
				heading,
				aid,
				isHit,
				seq,
				gfxId,
				x,
				y,
				chaX,
				chaY);

		return selfSent;
	}

	private static void broadcastMode1(
			L1Character anchor,
			int attackerId,
			int targetId,
			int heading,
			int gfxId) {

		{
		    final L1World __cioWorld0 = L1World.getInstance();
		    final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(anchor);
		    try {
		        for (L1PcInstance viewer : __cioVisible0) {
        
        			if (viewer == null) {
        				continue;
        			}
        
        			sendMode1(
        					viewer.getNetConnection(),
        					attackerId,
        					targetId,
        					heading,
        					gfxId);
        		}
		    } finally {
		        __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
		    }
		}
	}

	private static void broadcastMode2(
			L1Character anchor,
			int attackerId,
			int targetId,
			int heading,
			int aid,
			boolean isHit,
			int seq,
			int gfxId,
			int targetX,
			int targetY,
			int chaX,
			int chaY) {

		{
		    final L1World __cioWorld1 = L1World.getInstance();
		    final java.util.List<L1PcInstance> __cioVisible1 = __cioWorld1.borrowVisiblePlayerFast(anchor);
		    try {
		        for (L1PcInstance viewer : __cioVisible1) {
        
        			if (viewer == null) {
        				continue;
        			}
        
        			sendMode2(
        					viewer.getNetConnection(),
        					attackerId,
        					targetId,
        					heading,
        					aid,
        					isHit,
        					seq,
        					gfxId,
        					targetX,
        					targetY,
        					chaX,
        					chaY);
        		}
		    } finally {
		        __cioWorld1.releaseVisiblePlayerFast(__cioVisible1);
		    }
		}
	}

	private static boolean sendMode1(
			CIOClient client,
			int attackerId,
			int targetId,
			int heading,
			int gfxId) {

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
						WIRE_SIZE_MODE_1);

		if (frame == null) {
			return false;
		}

		final WriteBuffer buffer =
				frame.buffer();

		boolean committed = false;

		try {
			buffer.writeC(Opcodes.S_ATTACK);
			buffer.writeC(1);
			buffer.writeD(attackerId);
			buffer.writeD(targetId);
			buffer.writeH(0x01);
			buffer.writeC(heading);
			buffer.writeH(0x0000);
			buffer.writeH(0x0000);
			buffer.writeC(2);
			buffer.writeD(gfxId);
			buffer.writeH(0);

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

	private static boolean sendMode2(
			CIOClient client,
			int attackerId,
			int targetId,
			int heading,
			int aid,
			boolean isHit,
			int seq,
			int gfxId,
			int targetX,
			int targetY,
			int chaX,
			int chaY) {

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
						WIRE_SIZE_MODE_2);

		if (frame == null) {
			return false;
		}

		final WriteBuffer buffer =
				frame.buffer();

		boolean committed = false;

		try {
			buffer.writeC(Opcodes.S_ATTACK);
			buffer.writeC(aid);
			buffer.writeD(attackerId);
			buffer.writeD(targetId);
			buffer.writeC(isHit ? 6 : 0);
			buffer.writeC(0x00);
			buffer.writeC(heading);
			buffer.writeD(seq);
			buffer.writeH(gfxId);
			buffer.writeC(0);
			buffer.writeH(targetX);
			buffer.writeH(targetY);
			buffer.writeH(chaX);
			buffer.writeH(chaY);
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

	private static int resolveMode1Gfx(
			int type,
			boolean doubleWeapon) {

		switch (type) {
		case 4:
			return 13411;

		case 11:
			return doubleWeapon
					? 13415
					: 13414;

		case 24:
			return 13402;

		case 40:
			return 13413;

		case 46:
			return 13412;

		case 50:
			return 13410;

		case 54:
			return 13417;

		case 58:
			return 13416;

		case 83:
			return 13410;

		case 90:
			return 13409;

		case 91:
			return 13396;

		case 92:
			return 13398;

		case 99:
			return 13415;

		default:
			return 0;
		}
	}

	private static int resolveAid(
			int spriteId) {

		return (spriteId == 3860
				|| spriteId == 7959)
						? 21
						: 1;
	}

	private static int resolveMode2Gfx(
			int spriteId,
			int type) {

		if (type == 20) {
			if (spriteId == 21209
					|| spriteId == 21258
					|| spriteId == 21405
					|| spriteId == 21409
					|| spriteId == 19514) {

				return 20544;
			}

			return 13392;
		}

		if (type == 62) {
			return 13398;
		}

		return 0;
	}
}
