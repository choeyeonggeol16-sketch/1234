package l1j.server.server.model.item;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import l1j.server.Templates.L1Skills;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_ServerMessage;

@XmlAccessorType(XmlAccessType.FIELD)
public class BlankScroll {
	private static Logger _log = Logger.getLogger(BlankScroll.class.getName());
	private static final String _path = "./data/xml/Item/BlankScroll.xml";
	private static HashMap<Integer, BlankScroll> _dataMap = new HashMap<Integer, BlankScroll>();

	@XmlAttribute(name = "ItemId")
	private int _itemId;

	@XmlAttribute(name = "ClassInitial")
	private String _classInitial;

	@XmlAttribute(name = "Remove")
	private int _remove;

	@XmlElement(name = "Effect")
	private CopyOnWriteArrayList<Effect> _effects;

	public static BlankScroll get(int id) {
		return (BlankScroll) _dataMap.get(Integer.valueOf(id));
	}

	private int getItemId() {
		return this._itemId;
	}

	private int getRemove() {
		return this._remove;
	}

	private List<Effect> getEffects() {
		return this._effects;
	}

	private boolean init() {
		if (ItemTable.getInstance().getTemplate(getItemId()) == null) {
			System.out.println("존재하지 않는 아이템번호입니다. " + getItemId());

			return false;
		}
		for (Effect each : getEffects()) {
			if (ItemTable.getInstance().getTemplate(each.getItemId()) == null) {
				System.out.println("존재하지 않는 아이템번호입니다. " + each.getItemId());

				return false;
			}
			if (SkillsTable.getInstance().getTemplate(each.getSkillId()) == null) {
				System.out.println("존재하지 않는 스킬번호입니다. " + each.getSkillId());

				return false;
			}
		}
		return true;
	}

	private static void loadXml(HashMap<Integer, BlankScroll> dataMap) {
		try {
			JAXBContext context = JAXBContext.newInstance(new Class[] { ItemEffectList.class });

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(_path);
			ItemEffectList list = (ItemEffectList) um.unmarshal(file);

			for (BlankScroll each : list) {
				if (each.init()) {
					dataMap.put(Integer.valueOf(each.getItemId()), each);
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "./data/xml/Item/BlankScroll.xmlload failed.", e);
		}
	}

	public static void load() {
		loadXml(_dataMap);
	}

	public static void reload() {
		HashMap<Integer, BlankScroll> dataMap = new HashMap<Integer, BlankScroll>();
		loadXml(dataMap);
		_dataMap = dataMap;
	}

	public boolean use(L1PcInstance pc, ItemInstance item, int skillId) {
		int maxChargeCount = item.getItem().getMaxChargeCount();
		int chargeCount = item.getChargeCount();
		if (maxChargeCount > 0 && chargeCount <= 0) {
			pc.sendPackets(new S_ServerMessage(79));
			return false;
		}

		Effect effect = null;
		for (Effect each : getEffects()) {
			if (skillId == each.getSkillId() - 1) {
				effect = each;
				break;
			}
		}

		if (effect == null) {
			pc.sendPackets(new S_ServerMessage(591));

			return false;
		}

		ItemInstance spellsc = ItemTable.getInstance().createItem(effect.getItemId());
		if (spellsc != null) {
			if (pc.getInventory().checkAddItem(spellsc, 1) == 0) {
				L1Skills skill = SkillsTable.getInstance().getTemplate(effect.getSkillId());
				if (pc.getCurrentHp() + 1 < skill.getHpConsume() + 1) {
					pc.sendPackets(new S_ServerMessage(279));

					return false;
				}
				if (pc.getCurrentMp() < skill.getMpConsume()) {
					pc.sendPackets(new S_ServerMessage(278));

					return false;
				}
				if ((skill.getItemConsumeId() != 0) && (!pc.getInventory().checkItem(skill.getItemConsumeId(), skill.getItemConsumeCount()))) {
					pc.sendPackets(new S_ServerMessage(299)); 
					return false;
				}

				pc.setCurrentHp(pc.getCurrentHp() - skill.getHpConsume());
				pc.setCurrentMp(pc.getCurrentMp() - skill.getMpConsume());
				int lawful = pc.getLawful() + skill.getLawful();
				if (lawful > 32767) {
					lawful = 32767;
				}
				if (lawful < -32767) {
					lawful = -32767;
				}
				pc.setLawful(lawful);
				if (skill.getItemConsumeId() != 0) {
					pc.getInventory().consumeItem(skill.getItemConsumeId(), skill.getItemConsumeCount());
				} 
				pc.getInventory().storeItem(spellsc); 
			}
		} else {
			pc.sendPackets(new S_ServerMessage(79));
			return false;
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

		@XmlAttribute(name = "ItemId")
		private int _itemId;

		@XmlAttribute(name = "SkillId")
		private int _skillId;

		public int getItemId() {
			return this._itemId;
		}

		public int getSkillId() {
			return this._skillId;
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "ItemEffectList")
	private static class ItemEffectList implements Iterable<BlankScroll> {

		@XmlElement(name = "Item")
		private List<BlankScroll> _list;

		public Iterator<BlankScroll> iterator() {
			return this._list.iterator();
		}
	}
}