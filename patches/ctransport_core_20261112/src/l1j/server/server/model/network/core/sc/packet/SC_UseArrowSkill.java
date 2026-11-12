package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;

public final class SC_UseArrowSkill {

	private static final AtomicInteger SEQ =
			new AtomicInteger(0);

	private static final int WIRE_SIZE =
			ConnectionConfig.HEADER_SIZE + 33;

	private SC_UseArrowSkill() {
	}

	public static boolean send(
			CIOClient client,
			int attackerId,
			int ox,
			int oy,
			int oh,
			int actId,
			int targetId,
			int spellGfx,
			int tx,
			int ty,
			boolean isHit) {

		final int seq = nextSeq();

		return send0(
				client,
				attackerId,
				ox,
				oy,
				oh,
				actId,
				targetId,
				spellGfx,
				tx,
				ty,
				isHit,
				seq);
	}

	public static boolean send(
			CIOClient client,
			L1Character cha,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			boolean isHit) {

		int attackerId = 0;
		int ox = 0;
		int oy = 0;
		int oh = 0;
		int actId = 1;

		if (cha != null) {
			attackerId = cha.getId();
			ox = cha.getX();
			oy = cha.getY();
			oh = cha.getHeading();

			actId =
					resolveActIdBySprite(
							cha.getCurrentSpriteId());
		}

		final int seq = nextSeq();

		return send0(
				client,
				attackerId,
				ox,
				oy,
				oh,
				actId,
				targetObj,
				spellGfx,
				x,
				y,
				isHit,
				seq);
	}

	public static boolean sendAndBroadcast(
			L1PcInstance pc,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			boolean isHit) {

		if (pc == null) {
			return false;
		}

		final int attackerId = pc.getId();
		final int ox = pc.getX();
		final int oy = pc.getY();
		final int oh = pc.getHeading();
		final int actId =
				resolveActIdBySprite(
						pc.getCurrentSpriteId());

		final int seq = nextSeq();

		final boolean selfSent =
				send0(
						pc.getNetConnection(),
						attackerId, ox, oy, oh, actId,
						targetObj, spellGfx, x, y, isHit, seq);

		{
		    final L1World __cioWorld0 = L1World.getInstance();
		    final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(pc);
		    try {
		        for (L1PcInstance visible : __cioVisible0) {
        			if (visible == null) {
        				continue;
        			}
        
        			send0(
        					visible.getNetConnection(),
        					attackerId, ox, oy, oh, actId,
        					targetObj, spellGfx, x, y, isHit, seq);
        		}
		    } finally {
		        __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
		    }
		}

		return selfSent;
	}

	public static void broadcast(
			L1Character cha,
			int targetObj,
			int spellGfx,
			int x,
			int y,
			boolean isHit) {

		if (cha == null) {
			return;
		}

		final int attackerId = cha.getId();
		final int ox = cha.getX();
		final int oy = cha.getY();
		final int oh = cha.getHeading();

		final int actId =
				resolveActIdBySprite(
						cha.getCurrentSpriteId());

		final int seq = nextSeq();

		{
		    final L1World __cioWorld1 = L1World.getInstance();
		    final java.util.List<L1PcInstance> __cioVisible1 = __cioWorld1.borrowVisiblePlayerFast(cha);
		    try {
		        for (L1PcInstance pc : __cioVisible1) {
        
        			if (pc == null) {
        				continue;
        			}
        
        			send0(
        					pc.getNetConnection(),
        					attackerId,
        					ox,
        					oy,
        					oh,
        					actId,
        					targetObj,
        					spellGfx,
        					x,
        					y,
        					isHit,
        					seq);
        		}
		    } finally {
		        __cioWorld1.releaseVisiblePlayerFast(__cioVisible1);
		    }
		}
	}

	public static void broadcast(
			L1Character anchor,
			int attackerId,
			int ox,
			int oy,
			int oh,
			int actId,
			int targetId,
			int spellGfx,
			int tx,
			int ty,
			boolean isHit) {

		if (anchor == null) {
			return;
		}

		final int seq = nextSeq();

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
        					attackerId,
        					ox,
        					oy,
        					oh,
        					actId,
        					targetId,
        					spellGfx,
        					tx,
        					ty,
        					isHit,
        					seq);
        		}
		    } finally {
		        __cioWorld2.releaseVisiblePlayerFast(__cioVisible2);
		    }
		}
	}

	private static boolean send0(
			CIOClient client,
			int attackerId,
			int ox,
			int oy,
			int oh,
			int actId,
			int targetId,
			int spellGfx,
			int tx,
			int ty,
			boolean isHit,
			int seq) {

		if (client == null || !client.isConnected()) {
			return false;
		}

		final CUserSocket connection = client.getConnection();
		if (connection == null || !connection.isClientConnected()) {
			return false;
		}

		final CIOFrame frame =
				connection.beginDirectFrame(
						WIRE_SIZE);

		if (frame == null) {
			return false;
		}

		final WriteBuffer buffer = frame.buffer();
		boolean committed = false;

		try {
			buffer.writeC(Opcodes.S_ATTACK);
			buffer.writeC(actId);
			buffer.writeD(attackerId);
			buffer.writeD(targetId);
			buffer.writeC(isHit ? 6 : 0);
			buffer.writeC(0x00);
			buffer.writeC(oh);
			buffer.writeD(seq);
			buffer.writeH(spellGfx);

			buffer.writeC(0);

			buffer.writeH(ox);
			buffer.writeH(oy);
			buffer.writeH(tx);
			buffer.writeH(ty);

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
	
	public static void sendToReceivers(
			ArrayList<L1Object> receivers,
			int attackerId,
			int ox,
			int oy,
			int oh,
			int actId,
			int targetId,
			int spellGfx,
			int tx,
			int ty,
			boolean isHit) {

		if (receivers == null || receivers.isEmpty()) {
			return;
		}

		final int seq = nextSeq();

		for (L1Object o : receivers) {
			if (!(o instanceof L1PcInstance)) {
				continue;
			}

			final L1PcInstance pc =
					(L1PcInstance) o;

			send0(
					pc.getNetConnection(),
					attackerId,
					ox,
					oy,
					oh,
					actId,
					targetId,
					spellGfx,
					tx,
					ty,
					isHit,
					seq);
		}
	}

	private static int nextSeq() {
		return SEQ.incrementAndGet();
	}

	private static int resolveActIdBySprite(
			int spriteId) {

		switch (spriteId) {
		case 3860:
		case 15053:
			return 21;

		case 21090:
		case 21094:
			return 82;

		case 16198:
			return 18;

		default:
			return 1;
		}
	}
}
