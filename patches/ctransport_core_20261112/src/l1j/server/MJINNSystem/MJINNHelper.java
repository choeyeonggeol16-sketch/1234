package l1j.server.MJINNSystem;

import java.io.IOException;
import java.util.ArrayList;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.utils.CommonUtil;

public class MJINNHelper {
	public static final int 	INN_KEYID 			= 40312;		// 여관 열쇠 아이템 아이디
	public static final int 	INN_PRICE 			= 300;		// 여관 빌리는 가격(열쇠 하나당)
	public static final int 	HALL_PRICE 			= 600;		// 홀 빌리는 가격(열쇠 하나당)
	public static final int	SELL_PRICE			= 10;			// 되팔았을때의 가격
	public static int 			USAGECOUNT;						// 여관 룸 최대 생성갯수(마을마다 그리고 홀/여관 따로 각각 적용된다.)
	public static final long RENTAL_MINUTE 	= 240;		// 빌리고 반환될때까지의 유지시간(분)
	public static MJINNHelper create(){
		return new MJINNHelper();
	}
	
	private int 			_helperId;
	private int				_innId;
	private MJINNMapInfo[]	_mInfo;
	
	
	private MJINNHelper(){
	}
	
	public void setHelperId(int helperid){
		_helperId = helperid;
	}
	
	public int getHelperId(){
		return _helperId;
	}
	
	public void setInnId(int innid){
		_innId = innid;
	}
	
	public int getInnId(){
		return _innId;
	}
	
	public void setMapInfo(MJINNMapInfo[] mInfo){
		_mInfo = mInfo;
	}
	
	private ArrayList<Integer> rental(L1PcInstance pc, int price, int amount, int keyId){
		ArrayList<Integer> list = new ArrayList<Integer>(amount);
		L1Inventory inv = pc.getInventory();
		for(int i = amount - 1; i>=0; --i){
			L1ItemInstance item = ItemTable.getInstance().createItem(keyId);
			if(inv.checkAddItem(item, 1) != L1Inventory.OK || !inv.consumeItem(L1ItemId.ADENA, price)){
				pc.sendPackets("소지품이 너무 많아 더 이상 들 수 없습니다.");
				break;
			}
			CommonUtil.SetTodayDeleteTime(item, (int)RENTAL_MINUTE);
			CommonUtil.SetHotelTownName(item, _mInfo[0].town);
			list.add(item.getId());
			inv.storeItem(item);
		}
		return list;
	}
	
	@SuppressWarnings("resource")
	public void onResult(L1NpcInstance npc, L1PcInstance pc, String s, int amount) throws IOException{
		switch(s){
		case "inn2":{
			int price = amount * INN_PRICE;
			if(!pc.getInventory().checkItem(L1ItemId.ADENA, price)){
				sendNormal(pc, npc.getId(), "inn3");
				break;
			}
			ArrayList<Integer> list = rental(pc, INN_PRICE, amount, INN_KEYID);
			if(list.size() > 0){
				MJINNRoom.create(_mInfo[0], list.toArray(new Integer[list.size()]));
				_mInfo[0].usageCount.incrementAndGet();
				sendNormal(pc, npc.getId(), "inn4");
			}
			list.clear();
			break;
		}
		case "inn12":{
			int price = amount * HALL_PRICE;
			if(!pc.getInventory().checkItem(L1ItemId.ADENA, price)){
				sendNormal(pc, npc.getId(), "inn3");
				break;
			}
			ArrayList<Integer> list = rental(pc, HALL_PRICE, amount, INN_KEYID);
			if(list.size() > 0){
				MJINNRoom.create(_mInfo[1], list.toArray(new Integer[list.size()]));
				_mInfo[1].usageCount.incrementAndGet();
				sendNormal(pc, npc.getId(), "inn4");
			}
			list.clear();
			break;
		}
		}
	}
	
	public void onTalk(L1NpcInstance npc, L1PcInstance pc){
		try{
			sendNormal(pc, npc.getId(), pc.getLawful() < 0 ? "inn11" : "inn");
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public void onAction(L1NpcInstance npc, L1PcInstance pc, String s) throws IOException{
		L1ItemInstance[] keys = pc.getInventory().findItemsId(INN_KEYID);
		
		switch(s){
		
		case "room":
			if(keys != null && keys.length > 0){
				sendNormal(pc, npc.getId(), "inn5");
				break;
			}
			
			if(_mInfo[0].usageCount.get() >= USAGECOUNT){
				sendNormal(pc, npc.getId(), "inn6");
				break;
			}
			
            sendInput(pc, npc.getId(), INN_PRICE, "inn2", npc.getName());
			break;
		case "hall":
			if(keys != null && keys.length > 0){
				sendNormal(pc, npc.getId(), "inn15");
				break;
			}
			
			if(_mInfo[1].usageCount.get() >= USAGECOUNT){
				sendNormal(pc, npc.getId(), "inn6");
				break;
			}
			
            if (pc.isCrown()) sendInput(pc, npc.getId(), HALL_PRICE, "inn12", npc.getName());
            else sendNormal(pc, npc.getId(), "inn10");
		case "return":
			if(keys == null || keys.length <= 0)
				return;

			for(L1ItemInstance key : keys){
				MJINNRoom.remove(key);
				pc.getInventory().removeItem(key);
			}
			int price = keys.length * SELL_PRICE;
			pc.getInventory().storeItem(L1ItemId.ADENA, keys.length * SELL_PRICE);
            sendReturn(pc, npc.getId(), npc.getName(), price);
			break;
		case "enter":
			if(keys == null || keys.length <= 0)
				return;
			
			MJINNRoom.input(keys, pc);
			break;
		}
	}
	
    private static boolean sendNormal(L1PcInstance pc, int objId, String html) {
        if (pc == null || html == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket conn = socket.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final long size = 16L + 4L * html.length();
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = conn.beginDirectFrame((int) size);
        if (frame == null) return false;
        boolean done = false;
        try {
            final WriteBuffer b = frame.buffer();
            b.writeC(Opcodes.S_HYPERTEXT);
            b.writeD(objId);
            b.writeS(html);
            b.writeH(0); b.writeH(0);
            done = conn.commitDirectFrame(frame);
            return done;
        } finally {
            if (!done) conn.abortDirectFrame(frame);
        }
    }

    private static boolean sendInput(L1PcInstance pc, int objId, int cost, String html, String name) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket conn = socket.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final String price = Integer.toString(cost);
        final long size = 48L + 4L * (html.length() * 2 + name.length() + price.length());
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = conn.beginDirectFrame((int)size);
        if (frame == null) return false;
        boolean done = false;
        try {
            final WriteBuffer b = frame.buffer();
            b.writeC(Opcodes.S_HYPERTEXT_INPUT);
            b.writeD(objId);
            b.writeD(cost); b.writeD(1); b.writeD(1); b.writeD(20); b.writeH(0);
            b.writeS(html); b.writeS(html);
            b.writeH(2); b.writeS(name); b.writeS(price);
            done = conn.commitDirectFrame(frame);
            return done;
        } finally {
            if (!done) conn.abortDirectFrame(frame);
        }
    }

    private static boolean sendReturn(L1PcInstance pc, int objId, String name, int value) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket conn = socket.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final String cost = Integer.toString(value);
        final long size = 32L + 4L * (name.length() + cost.length());
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = conn.beginDirectFrame((int)size);
        if (frame == null) return false;
        boolean done = false;
        try {
            final WriteBuffer b = frame.buffer();
            b.writeC(Opcodes.S_HYPERTEXT);
            b.writeD(objId); b.writeS("inn20"); b.writeH(1); b.writeH(2);
            b.writeS(name); b.writeS(cost);
            done = conn.commitDirectFrame(frame);
            return done;
        } finally {
            if (!done) conn.abortDirectFrame(frame);
        }
    }

}
