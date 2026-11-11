package l1j.server.server.model.item;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import l1j.server.Templates.L1House;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.SkillUtil;
import l1j.server.server.utils.SystemUtil;

@XmlAccessorType(XmlAccessType.FIELD)
public class MemorialStone {
	private static Logger _log = Logger.getLogger(MemorialStone.class.getName());

	@XmlAttribute(name = "Remove")
	private int _remove;

	@XmlElement(name = "Effect")
	private Effect _effect;
	private static final String _path = "./data/xml/Item/MemorialStone.xml";
	private static HashMap<Integer, MemorialStone> _dataMap = new HashMap<Integer, MemorialStone>();

	@XmlAttribute(name = "ItemId")
	private int _itemId;

	private int getRemove() {
		return this._remove;
	}

	private Effect getEffect() {
		return this._effect;
	}

	public static MemorialStone get(int id) {
		return (MemorialStone) _dataMap.get(Integer.valueOf(id));
	}

	private int getItemId() {
		return this._itemId;
	}

	private static void loadXml(HashMap<Integer, MemorialStone> dataMap) {
		try {
			JAXBContext context = JAXBContext.newInstance(new Class[] { ItemEffectList.class });

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(_path);
			ItemEffectList list = (ItemEffectList) um.unmarshal(file);

			for (MemorialStone each : list)
				if (ItemTable.getInstance().getTemplate(each.getItemId()) == null) {
					System.out.println("존재하지 않는 아이템번호입니다. " + each.getItemId());
				} else
					dataMap.put(Integer.valueOf(each.getItemId()), each);
		} catch (Exception e) {
			_log.log(Level.SEVERE, "./data/xml/Item/MemorialStone.xmlload failed.", e);
		}
	}

	public static void load() {
		// TimeLine.start("loading " +
		// _log.getName().substring(_log.getName().lastIndexOf(".") + 1) + "...");

		loadXml(_dataMap);

		// TimeLine.end();
	}

	public static void reload() {
		//TimeLine.start("reloading " + _log.getName().substring(_log.getName().lastIndexOf(".") + 1) + "...");

		HashMap<Integer, MemorialStone> dataMap = new HashMap<Integer, MemorialStone>();
		loadXml(dataMap);
		_dataMap = dataMap;

		//TimeLine.end();
	}

	public boolean use(L1PcInstance pc, ItemInstance item) {

		L1Clan clan = pc.getClan();
		if (clan == null) {
			pc.sendPackets(new S_SystemMessage("혈맹이 없습니다. 아지트내에서만 가능합니다."));
			return false;
		} 
		int houseId = clan.getHouseId();
		int catleId = clan.getCastleId();
		if (houseId == 0 && catleId == 0) {
			pc.sendPackets(new S_SystemMessage("아지트내에서만 가능합니다."));
			return false;
		}
		int[] loc = null;
		if (catleId > 0) {
			loc = L1CastleLocation.getCastleLoc(pc.getClan().getCastleId());
		} else if (houseId > 0) {
			L1House house = HouseTable.getInstance().getHouseTable(houseId);
			if (!house.isPurchaseBasement()) {
				pc.sendPackets(new S_ServerMessage(1098), true);// 아지트: 지하 아지트 없음
				return false;
			}
			loc = L1HouseLocation.getBasementLoc(houseId);
		}
		if (pc.getMapId() != loc[2]) {
			pc.sendPackets(new S_SystemMessage("내성 또는 지하 아지트에서만 사용 가능합니다."));
			return false;
		}

		SkillUtil.cancelBarrier(pc);

		int maxChargeCount = item.getItem().getMaxChargeCount();
		int chargeCount = item.getChargeCount();
		if ((maxChargeCount > 0) && (chargeCount <= 0)) {
			pc.sendPackets(new S_ServerMessage(79));
			return false;
		}

		Effect effect = getEffect();

		/**
		 * 영혼석 훈련장 키 사용에 따른 맵아이디 추출을 위해서
		 */
		ItemInstance boss_key = pc.getInventory().findItemId(80500);
		if (effect.isKeyMatch()) {
			if (boss_key == null) {
				pc.sendPackets(new S_ServerMessage(79));
				return false;
			}
		}

		int npcid = effect.getNpcId();
		int count = effect.getCount();
		int time = effect.getTime();
		int mapid = effect.getMapId();
		int locx = effect.getLocX();
		int locy = effect.getLocY();
		String message = effect.getMessage();

		if (mapid != 0) {
			if (pc.getMapId() != mapid) {
				pc.sendPackets(new S_ServerMessage(79));
				return false;
			}

			SystemUtil.spawnCount(locx, locy, (short) mapid, npcid, 0, time * 1000, count);
			if (message != null)
				pc.sendPackets(new S_SystemMessage(message));
			pc.sendPackets(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, message));
		} else {
			SystemUtil.spawnCount(pc.getX() + CommonUtil.random(5), pc.getY() + CommonUtil.random(5),
					(short) pc.getMapId(), npcid, 0, time * 1000, count);
			if (message != null)
				pc.sendPackets(new S_SystemMessage(message));
			pc.sendPackets(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, message));
		}

		if (getRemove() > 0) {
			if (chargeCount > 0) {
				item.setChargeCount(chargeCount - getRemove());
				pc.getInventory().updateItem(item, 128);
			} else {
				pc.getInventory().removeItem(item, getRemove());
			}
		}

		return true;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	private static class Effect {

		@XmlAttribute(name = "NpcId")
		private int _npcid;

		@XmlAttribute(name = "Count")
		private int _count;

		@XmlAttribute(name = "Time")
		private int _time;

		@XmlAttribute(name = "LocX")
		private int _locx;

		@XmlAttribute(name = "LocY")
		private int _locy;

		@XmlAttribute(name = "MapId")
		private int _mapid;

		@XmlAttribute(name = "Message")
		private String _message;

		@XmlAttribute(name = "KeyMatch")
		private boolean _ketmatch;

		private boolean isKeyMatch() {
			return _ketmatch;
		}

		private int getTime() {
			return this._time;
		}

		private int getNpcId() {
			return _npcid;
		}

		private int getCount() {
			return _count;
		}

		private int getLocX() {
			return _locx;
		}

		private int getLocY() {
			return _locy;
		}

		private int getMapId() {
			return _mapid;
		}

		private String getMessage() {
			return _message;
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "ItemEffectList")
	private static class ItemEffectList implements Iterable<MemorialStone> {

		@XmlElement(name = "Item")
		private List<MemorialStone> _list;

		public Iterator<MemorialStone> iterator() {
			return this._list.iterator();
		}
	}
}