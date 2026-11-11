package l1j.server.server.utils;

import java.util.ArrayList;
import java.util.StringTokenizer;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_EffectLocation;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_SystemMessage;

// -- TYUTIL -- 자주쓰는 유틸을 모아두는 곳^ㅡ^

public class TYUtil {
	public static ArrayList<Integer> GetIntTokenizeArrayList(String str) {
		String temp2 = "";
		StringTokenizer values = new StringTokenizer(str, " ");
		while (values.hasMoreElements())
			temp2 += values.nextToken();

		StringTokenizer List = new StringTokenizer(temp2, ",");
		ArrayList<Integer> list = new ArrayList<Integer>();
		while (List.hasMoreElements()) {
			String value = List.nextToken();
			list.add(Integer.valueOf(value));
		}
		return list;

	}

	public static int[] GetIntTokenizeIntArray(String str) {
		int[] returnValue;
		ArrayList<Integer> list = GetIntTokenizeArrayList(str);
		returnValue = new int[list.size()];
		for (int i = 0; i < list.size(); i++)
			returnValue[i] = list.get(i);
		return returnValue;
	}

	public static void SetDebugBroadcasting(String msg) {
		SetBroadcastMessage("[DBG]" + msg, MESSAGE_TYPE.ORANGE);
	}

	public static enum MESSAGE_TYPE {
		NOTICE, GREEN_MESSAGE, BOTH, GREEN, YELLOW, ORANGE
	};

	public static void SetBroadcastMessage(String str, MESSAGE_TYPE type) {

		switch (type) {
		case BOTH:
			L1World.getInstance().broadcastPacketToAll(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, str));
			L1World.getInstance().broadcastServerMessage(str);
			break;
		case NOTICE:
			L1World.getInstance().broadcastServerMessage(str);
			break;
		case GREEN:
			L1World.getInstance().broadcastPacketToAll(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, str));
			break;
		case YELLOW:
			L1World.getInstance().broadcastPacketToAll(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "\\f=" + str));
			break;
		case ORANGE:
			L1World.getInstance().broadcastPacketToAll(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "\\aH" + str));
			break;
		default:
			break;
		}
	}

	public static void BrdCst(String str) {
		SetBroadcastMessage(str);
	}

	public static void SetBroadcastMessage(String str) {
		SetBroadcastMessage(str, MESSAGE_TYPE.BOTH);
	}

	public static void HeadMsg(L1PcInstance pc, String msg) {
		pc.sendPackets(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "\\f>" + msg));
	}

	public static void SysMsg(L1PcInstance pc, String msg) {
		pc.sendPackets(new S_SystemMessage(msg));
	}

	public static void SetSystemMessage(L1PcInstance pc, String msg) {
		pc.sendPackets(new S_SystemMessage(msg));
	}

	public static void SetSystemMessageBoth(L1PcInstance pc, String msg) {
		pc.sendPackets(new S_SystemMessage(msg));
		pc.sendPackets(new S_PacketBox(84, msg));
	}

	public static String GetNumberComma3(int num) {
		String value = String.valueOf(num);
		if (value.length() > 6) {
			value = value.replaceFirst("(\\d{1,3})(\\d{3})(\\d{3})", "$1,$2,$3");
		} else if (value.length() >= 5 && value.length() <= 6) {
			value = value.replaceFirst("(\\d{2,3})(\\d{3})", "$1,$2");
		} else {
			value = value.replaceFirst("(\\d{1})(\\d+)", "$1,$2");
		}

		return value;
	}

	public static String GetNumK(int num) {
		if (num > 1000000) {
			num *= 0.001;
			if (num > 1000000) {
				num *= 0.001;
				return String.valueOf(num) + "M";
			}
			return String.valueOf(num) + "K";
		}
		return String.valueOf(num);
	}

	public static String GetNumberComma3K(int num) {
		String value = String.valueOf(num);
		if (value.length() > 6) {
			value = value.replaceFirst("(\\d{1,3})(\\d{3})(\\d{3})", "$1,$2");
			value += "\\f3K\\f>";
		} else if (value.length() >= 5 && value.length() <= 6) {
			value = value.replaceFirst("(\\d{2,3})(\\d{3})", "$1,$2");
		} else {
			value = value.replaceFirst("(\\d{1})(\\d+)", "$1,$2");
		}
		return value;
	}

	public static ItemInstance GetItemInstByID(int itemId) {
		return ItemTable.getInstance().createItem(itemId);
	} 

	public static void SetEffect(L1PcInstance pc, int effectNum) {
		L1World.getInstance().broadcastPacketToAll(S_EffectLocation.get(pc.getX(), pc.getY(), effectNum));
	}

	public static void SetEffect(int effectNum, int posX, int posY) {
		L1World.getInstance().broadcastPacketToAll(S_EffectLocation.get(posX, posY, effectNum));
	}

}
