package l1j.server.server.model.world.instance.inn;

import l1j.server.server.datatables.CObjectDB;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.templates.L1Item;

public class InnHelper {
    private static final Logger LOG = Logger.getLogger(InnHelper.class.getName());
	public static final int 	INN_KEYID 			= 40312;		// 여관 열쇠 아이템 아이디
	public static final int 	INN_PRICE 			= 300;		// 여관 빌리는 가격(열쇠 하나당)
	public static final int 	HALL_PRICE 			= 600;		// 홀 빌리는 가격(열쇠 하나당)
	public static final int	SELL_PRICE			= 10;			// 되팔았을때의 가격
	public static int 			USAGECOUNT;						// 여관 룸 최대 생성갯수(마을마다 그리고 홀/여관 따로 각각 적용된다.)
	public static final long RENTAL_MINUTE 	= 240;		// 빌리고 반환될때까지의 유지시간(분)
	public static InnHelper create(){
		return new InnHelper();
	}
	
	private int 			_helperId;
	private int				_innId;
	private InnMapInfo[]	_mInfo;
	
	
	private InnHelper(){
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
	
	public void setMapInfo(InnMapInfo[] mInfo){
		_mInfo = mInfo;
	}
	
    private static final int MAX_RENTAL_KEYS = 20;

    public void onResult(L1NpcInstance npc, L1PcInstance pc, String s, int amount) throws IOException {
        if (amount <= 0 || amount > MAX_RENTAL_KEYS || pc.isItemTransferBusy()) return;
        final boolean hall = "inn12".equals(s);
        if (!hall && !"inn2".equals(s)) return;
        if (hall && !pc.isCrown()) {
            sendNormal(pc, npc.getId(), "inn10");
            return;
        }
        final int index = hall ? 1 : 0;
        if (_mInfo == null || _mInfo.length <= index || _mInfo[index] == null) return;
        final InnMapInfo info = _mInfo[index];
        final L1Inventory inv = pc.getInventory();
        if (inv.checkItem(INN_KEYID)) {
            sendNormal(pc, npc.getId(), hall ? "inn15" : "inn5");
            return;
        }
        if (info.usageCount.get() >= USAGECOUNT) {
            sendNormal(pc, npc.getId(), "inn6");
            return;
        }
        final int price = hall ? HALL_PRICE : INN_PRICE;
        if (!inv.checkItem(L1ItemId.ADENA, amount * price)) {
            sendNormal(pc, npc.getId(), "inn3");
            return;
        }
        final int objectId = npc.getId();
        final L1Item definition = CObjectDB.LoadItem().getTemplate(INN_KEYID);
        if (definition == null || definition.isStackable()) return;
        InnRoom reserved = null;
        boolean submitted = false;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, INN_KEYID)) {
            if (inv.checkItem(INN_KEYID)) {
                sendNormal(pc, objectId, hall ? "inn15" : "inn5");
                return;
            }
            if (!tx.debit(L1ItemId.ADENA, amount * price)) {
                sendNormal(pc, objectId, "inn3");
                return;
            }
            final int[] ids = new int[amount];
            for (int i = 0; i < amount; i++) {
                L1ItemInstance key = tx.grant(INN_KEYID, 1);
                if (key == null || key.isStackable()) return;
                // grant() preserves the 180-minute expiry of storeItem().
                key.setHotel_Town(info.town);
                ids[i] = key.getId();
            }
            reserved = InnRoom.reserve(info);
            if (reserved == null) {
                sendNormal(pc, objectId, "inn6");
                return;
            }
            final InnRoom room = reserved;
            tx.onFailure(() -> {
                room.notifyObservable();
                pc.sendPackets("여관 임대를 완료하지 못했습니다.");
            });
            // Publish key identities only after durable commit. No packets here.
            tx.onDurableCommit(() -> room.finishRental(ids));
            tx.commitAfterInventoryRelease(() -> sendNormal(pc, objectId, "inn4"));
            submitted = true;
        } catch (RuntimeException failure) {
            LOG.log(Level.FINE, "Inn rental preparation failed", failure);
            if (!submitted) pc.sendPackets("여관 임대를 완료하지 못했습니다.");
        } finally {
            if (!submitted && reserved != null) reserved.notifyObservable();
        }
    }

    private static void returnKeys(L1PcInstance pc, int objectId, String name, L1ItemInstance[] keys) {
        if (keys == null || keys.length == 0) return;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, INN_KEYID)) {
            int price = 0;
            for (L1ItemInstance key : keys) {
                if (key == null || key.getItemId() != INN_KEYID || key.getCount() != 1
                        || key.isStackable() || !tx.debit(key, 1)) return;
                price += SELL_PRICE;
            }
            if (tx.grant(L1ItemId.ADENA, price) == null) return;
            final int refund = price;
            // Committed replay removes world objects and room registrations
            // through CWorld.removeObject, once per key.
            tx.onFailure(() -> pc.sendPackets("여관 열쇠 반환을 완료하지 못했습니다."));
            tx.commitAfterInventoryRelease(() -> sendReturn(pc, objectId, name, refund));
        } catch (RuntimeException failure) {
            LOG.log(Level.FINE, "Inn return preparation failed", failure);
            pc.sendPackets("여관 열쇠 반환을 완료하지 못했습니다.");
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
        if (pc.isItemTransferBusy()) return;
		L1ItemInstance[] keys = pc.getInventory().findItemsId(INN_KEYID);
		
		switch(s){
		
		case "room":
			if(keys != null && keys.length > 0){
				sendNormal(pc, npc.getId(), "inn5");
				break;
			}
			
			if(_mInfo == null || _mInfo.length == 0 || _mInfo[0] == null
                    || _mInfo[0].usageCount.get() >= USAGECOUNT){
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
			
			if(_mInfo == null || _mInfo.length <= 1 || _mInfo[1] == null
                    || _mInfo[1].usageCount.get() >= USAGECOUNT){
				sendNormal(pc, npc.getId(), "inn6");
				break;
			}
			
            if (pc.isCrown()) sendInput(pc, npc.getId(), HALL_PRICE, "inn12", npc.getName());
            else sendNormal(pc, npc.getId(), "inn10");
            break;
        case "return":
            returnKeys(pc, npc.getId(), npc.getName(), keys);
            break;
		case "enter":
			if(keys == null || keys.length <= 0)
				return;
			
			InnRoom.input(keys, pc);
			break;
		}
	}
	
    private static boolean sendNormal(L1PcInstance pc, int objId, String html) {
        if (pc == null || html == null) return false;
        final CUserSocket socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket conn = socket;
        if (conn == null || !conn.isClientConnected()) return false;
        final long size = 16L + 4L * html.length();
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = conn.beginDirectFrame((int) size);
        if (frame == null) return false;
        boolean done = false;
        try {
            final Assemble b = frame.buffer();
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
        final CUserSocket socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket conn = socket;
        if (conn == null || !conn.isClientConnected()) return false;
        final String price = Integer.toString(cost);
        final long size = 48L + 4L * (html.length() * 2 + name.length() + price.length());
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = conn.beginDirectFrame((int)size);
        if (frame == null) return false;
        boolean done = false;
        try {
            final Assemble b = frame.buffer();
            b.writeC(Opcodes.S_HYPERTEXT_INPUT);
            b.writeD(objId);
            b.writeD(cost); b.writeD(1); b.writeD(1); b.writeD(MAX_RENTAL_KEYS); b.writeH(0);
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
        final CUserSocket socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket conn = socket;
        if (conn == null || !conn.isClientConnected()) return false;
        final String cost = Integer.toString(value);
        final long size = 32L + 4L * (name.length() + cost.length());
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = conn.beginDirectFrame((int)size);
        if (frame == null) return false;
        boolean done = false;
        try {
            final Assemble b = frame.buffer();
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
