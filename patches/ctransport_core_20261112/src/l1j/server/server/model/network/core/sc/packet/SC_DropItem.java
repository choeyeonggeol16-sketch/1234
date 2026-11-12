package l1j.server.server.model.network.core.sc.packet;

import java.sql.Timestamp;
import java.util.Calendar;

import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.PrideSystem.PrideLoadManager;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;

public final class SC_DropItem {

	/*
	 * Payload fixed bytes including opcode and writeS trailing NUL,
	 * excluding only the encoded item-name bytes.
	 *
	 * Total wire reserve:
	 *   2-byte frame header + 43 fixed payload bytes + MS949 item-name bytes.
	 */
	private static final int FIXED_PAYLOAD_BYTES = 43;
	private static final int FIXED_WIRE_BYTES =
			ConnectionConfig.HEADER_SIZE + FIXED_PAYLOAD_BYTES;

	/*
	 * Reused only when the visible ground name needs decoration.
	 * Plain one-count items return the template name directly and never touch it.
	 */
	private static final ThreadLocal<StringBuilder> NAME_BUILDER =
			ThreadLocal.withInitial(() -> new StringBuilder(96));

	private static final ThreadLocal<Calendar> DATE_CALENDAR =
			ThreadLocal.withInitial(Calendar::getInstance);

	private SC_DropItem() {
	}

	public static boolean send(
			CIOClient client,
			L1ItemInstance item) {

		if (item == null) {
			return false;
		}

		final int x = item.getX();
		final int y = item.getY();
		final int objectId = item.getId();

		final int groundGfxId;
		final int lightRange;
		final String itemName;

		if (item.getItem() != null) {
			groundGfxId = item.getItem().getGroundGfxId();
			lightRange = item.isNowLighting()
					? item.getItem().getLightRange()
					: 0;
			itemName = parseItemName(item);
		} else {
			groundGfxId = 0;
			lightRange = 0;
			itemName = "";
		}

		final int count = (int) item.getCount();

		return send(
				client,
				x,
				y,
				objectId,
				groundGfxId,
				lightRange,
				count,
				itemName);
	}

	/**
	 * Ground-item fanout.
	 *
	 * Item state and visible name are captured once, then the same primitive/string
	 * snapshot is serialized independently into each recipient's outbound frame.
	 */
	public static void broadcast(L1ItemInstance item) {
		broadcast(item, false);
	}

	/**
	 * Ground-item fanout with optional known-object registration.
	 *
	 * insertItem() uses addKnownObject=true so AOI lookup and item-name snapshot
	 * happen exactly once for the whole fanout.
	 */
	public static int broadcast(
			L1ItemInstance item,
			boolean addKnownObject) {

		if (item == null) {
			return 0;
		}

		final int x = item.getX();
		final int y = item.getY();
		final int objectId = item.getId();

		final int groundGfxId;
		final int lightRange;
		final String itemName;

		if (item.getItem() != null) {
			groundGfxId = item.getItem().getGroundGfxId();
			lightRange = item.isNowLighting()
					? item.getItem().getLightRange()
					: 0;
			itemName = parseItemName(item);
		} else {
			groundGfxId = 0;
			lightRange = 0;
			itemName = "";
		}

		final int count = (int) item.getCount();
		int viewers = 0;

		{
		    final L1World __cioWorld0 = L1World.getInstance();
		    final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(item);
		    try {
		        for (L1PcInstance pc : __cioVisible0) {
        
        			if (pc == null) {
        				continue;
        			}
        
        			final CIOClient client = pc.getNetConnection();
        			if (client == null || !client.isConnected()) {
        				continue;
        			}
        
        			if (send(
        					client,
        					x,
        					y,
        					objectId,
        					groundGfxId,
        					lightRange,
        					count,
        					itemName)) {
        
        				if (addKnownObject) {
        					pc.addKnownObject(item);
        				}
        
        				viewers++;
        			}
        		}
		    } finally {
		        __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
		    }
		}

		return viewers;
	}

	private static boolean send(
			CIOClient client,
			int x,
			int y,
			int objectId,
			int groundGfxId,
			int lightRange,
			int count,
			String itemName) {

		if (client == null || !client.isConnected()) {
			return false;
		}

		if (itemName == null) {
			itemName = "";
		}

		final long reserveLong =
				(long) FIXED_WIRE_BYTES
						+ ((long) itemName.length() << 1);

		if (reserveLong > ConnectionConfig.MAX_WIRE_PACKET_SIZE) {
			return false;
		}

		final CUserSocket connection = client.getConnection();
		if (connection == null || !connection.isClientConnected()) {
			return false;
		}

		final CIOFrame frame =
				connection.beginDirectFrame(
						(int) reserveLong);

		if (frame == null) {
			return false;
		}

		final WriteBuffer buffer = frame.buffer();
		boolean committed = false;

		try {
			buffer.writeC(Opcodes.S_PUT_OBJECT);
			buffer.writeH(x);
			buffer.writeH(y);
			buffer.writeD(objectId);
			buffer.writeH(groundGfxId);
			buffer.writeC(0);
			buffer.writeC(0);
			buffer.writeC(lightRange);
			buffer.writeC(0);
			buffer.writeD(count);
			buffer.writeC(0);
			buffer.writeC(0);

			buffer.writeS(itemName);
			buffer.writeC(0);
			buffer.writeD(0);
			buffer.writeD(0);
			buffer.writeC(255);
			buffer.writeC(0);
			buffer.writeC(0);
			buffer.writeC(0);
			buffer.writeH(65535);
			buffer.writeD(0);
			buffer.writeC(8);
			buffer.writeC(0);

			committed =
					connection.commitDirectFrame(
							frame);

			return committed;

		} catch (Throwable ignored) {
			return false;

		} finally {
			if (!committed) {
				connection.abortDirectFrame(frame);
			}
		}
	}

	private static String parseItemName(L1ItemInstance item) {
		if (PrideLoadManager.USE_PRIDE_SYSTEM
				&& item.getItemId() == PrideLoadManager.PRIDE_ITEM_ID) {
			return item.getViewName();
		}

		final int count = (int) item.getCount();
		final int itemId = item.getItem().getItemId();
		final boolean identified = item.isIdentified();
		final int type2 = item.getItem().getType2();

		final boolean equipmentPrefix =
				identified && (type2 == 1 || type2 == 2);

		final boolean specialSuffix;
		if (count > 1) {
			specialSuffix = true;
		} else if (itemId == 20383 && identified) {
			specialSuffix = true;
		} else if ((itemId == 40006
				|| itemId == 40007
				|| itemId == 40008
				|| itemId == 46091
				|| itemId == 40009
				|| itemId == 45464
				|| itemId == 140006
				|| itemId == 140008) && identified) {
			specialSuffix = true;
		} else if (itemId == MJINNHelper.INN_KEYID) {
			specialSuffix = item.getEndTime() != null;
		} else if (itemId == 80500) {
			specialSuffix = item.getEndTime() != null;
		} else {
			specialSuffix =
					item.getItem().getLightRange() != 0
							&& item.isNowLighting();
		}

		/*
		 * Most plain one-count drops need no decoration.
		 * Reuse the immutable template name directly: no builder, no new String.
		 */
		if (!equipmentPrefix && !specialSuffix) {
			return item.getItem().getNameId();
		}

		final StringBuilder sb = NAME_BUILDER.get();
		sb.setLength(0);

		if (equipmentPrefix) {
			switch (item.getAttrEnchantLevel()) {
			case 1:  sb.append("$6115"); break;
			case 2:  sb.append("$6116"); break;
			case 3:  sb.append("$6117"); break;
			case 4:  sb.append("$14361"); break;
			case 5:  sb.append("$14365"); break;
			case 6:  sb.append("$6118"); break;
			case 7:  sb.append("$6119"); break;
			case 8:  sb.append("$6120"); break;
			case 9:  sb.append("$14362"); break;
			case 10: sb.append("$14366"); break;
			case 11: sb.append("$6121"); break;
			case 12: sb.append("$6122"); break;
			case 13: sb.append("$6123"); break;
			case 14: sb.append("$14363"); break;
			case 15: sb.append("$14367"); break;
			case 16: sb.append("$6124"); break;
			case 17: sb.append("$6125"); break;
			case 18: sb.append("$6126"); break;
			case 19: sb.append("$14364"); break;
			case 20: sb.append("$14368"); break;
			default:
				sb.append(' ');
				break;
			}

			final int enchant = item.getEnchantLevel();
			if (enchant > 0) {
				sb.append('+').append(enchant).append(' ');
			} else if (enchant < 0) {
				sb.append(enchant).append(' ');
			}
		}

		sb.append(item.getItem().getNameId());

		if (count > 1) {
			sb.append(" (").append(item.getCount()).append(')');
			return sb.toString();
		}

		if (itemId == 20383 && identified) {
			sb.append(" [").append(item.getChargeCount()).append(']');
			return sb.toString();
		}

		if ((itemId == 40006
				|| itemId == 40007
				|| itemId == 40008
				|| itemId == 46091
				|| itemId == 40009
				|| itemId == 45464
				|| itemId == 140006
				|| itemId == 140008) && identified) {

			sb.append(" (").append(item.getChargeCount()).append(')');
			return sb.toString();
		}

		if (itemId == MJINNHelper.INN_KEYID) {
			final Timestamp endTime = item.getEndTime();
			if (endTime != null) {
				final Calendar cal = DATE_CALENDAR.get();
				cal.setTimeInMillis(endTime.getTime());

				sb.append(" [");
				append2(sb, cal.get(Calendar.MONTH) + 1);
				sb.append('-');
				append2(sb, cal.get(Calendar.DAY_OF_MONTH));
				sb.append(' ');
				append2(sb, cal.get(Calendar.HOUR_OF_DAY));
				sb.append(':');
				append2(sb, cal.get(Calendar.MINUTE));
				sb.append(']');
			}
			return sb.toString();
		}

		if (itemId == 80500) {
			final Timestamp endTime = item.getEndTime();
			if (endTime != null) {
				sb.append(" [")
						.append(endTime.getTime())
						.append(']');
			}
			return sb.toString();
		}

		if (item.getItem().getLightRange() != 0
				&& item.isNowLighting()) {
			sb.append(" ($10)");
		}

		return sb.toString();
	}

	private static void append2(
			StringBuilder sb,
			int value) {

		if (value < 10) {
			sb.append('0');
		}
		sb.append(value);
	}
}
