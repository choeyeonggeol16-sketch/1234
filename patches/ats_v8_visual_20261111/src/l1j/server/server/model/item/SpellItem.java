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

import l1j.server.Templates.L1Skills;
import l1j.server.configure.Config;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.random.RandomGenerator;
import l1j.server.server.random.RandomGeneratorFactory;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.SkillUtil;

@XmlAccessorType(XmlAccessType.FIELD)
public class SpellItem {
	private static Logger _log = Logger.getLogger(SpellItem.class.getName());

	private static RandomGenerator _random = RandomGeneratorFactory.newRandom();
	private static final String _path = "./data/xml/Item/SpellItem.xml";
	private static HashMap<Integer, SpellItem> _dataMap = new HashMap<Integer, SpellItem>();

	@XmlAttribute(name = "ItemId")
	private int _itemId;

	@XmlAttribute(name = "Remove")
	private int _remove;

	@XmlElement(name = "Effect")
	private Effect _effect;

	public static SpellItem get(int id) {
		return (SpellItem) _dataMap.get(Integer.valueOf(id));
	}

	private int getItemId() {
		return this._itemId;
	}

	private int getRemove() {
		return this._remove;
	}

	private Effect getEffect() {
		return this._effect;
	}

	private boolean init() {
		if (ItemTable.getInstance().getTemplate(getItemId()) == null) {
			System.out.println("존재하지 않는 아이템번호입니다. " + getItemId());

			return false;
		}
		Effect effect = getEffect();
		if (SkillsTable.getInstance().getTemplate(effect.getSkillId()) == null) {
			System.out.println("존재하지 않는 스킬번호입니다. " + effect.getSkillId());

			return false;
		}
		return true;
	}

	private static void loadXml(HashMap<Integer, SpellItem> dataMap) {
		try {
			JAXBContext context = JAXBContext.newInstance(new Class[] { ItemEffectList.class });

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(_path);
			ItemEffectList list = (ItemEffectList) um.unmarshal(file);

			for (SpellItem each : list)
				if (each.init())
					dataMap.put(Integer.valueOf(each.getItemId()), each);
		} catch (Exception e) {
			_log.log(Level.SEVERE, "./data/xml/Item/SpellItem.xmlload failed.", e);
		}
	}

	public static void load() {
		loadXml(_dataMap);
	}

	public static void reload() {
		HashMap<Integer, SpellItem> dataMap = new HashMap<Integer, SpellItem>();
		loadXml(dataMap);
		_dataMap = dataMap;
	}

	public boolean use(L1PcInstance pc, ItemInstance item, int spellsc_objid, int spellsc_x, int spellsc_y) {
		if (Config.STANDBY_SERVER) {
			pc.sendPackets(new S_SystemMessage("오픈 대기중에 취할 수 없는 행동입니다."));
			return false;
		}
		int maxChargeCount = item.getItem().getMaxChargeCount();
		int chargeCount = item.getChargeCount();
		if (maxChargeCount > 0 && chargeCount <= 0) {
			pc.sendPackets(new S_ServerMessage(79));
			return false;
		}

		if (spellsc_objid == pc.getId() && item.getItem().getUseType() != 30) {
			pc.sendPackets(new S_ServerMessage(281)); 
			return false;
		}

		if (spellsc_objid == 0 && item.getItem().getUseType() != 0 && item.getItem().getUseType() != 26
				&& item.getItem().getUseType() != 27 && item.getItem().getUseType() != 39) {
			pc.sendPackets(new S_ServerMessage(281));
			return false;
		}

//		if (pc.isSkillDelay()) {
//			return false;
//		}

		SkillUtil.cancelBarrier(pc);

		Effect effect = getEffect();

		int skillId = effect.getSkillId();
		L1Skills skill = SkillsTable.getInstance().getTemplate(skillId);
		int skillChance = skill.getSkillLevel() > 10 ? 10 : 30 - skill.getSkillLevel() * 3;
		int intChance = pc.getAbility().getTotalInt() < 25 ? pc.getAbility().getTotalInt() : 25;
		int totalChance = 40 + skillChance + intChance * 2;
		if (totalChance > 95)
			totalChance = 95;

		if (_random.nextInt(1) < totalChance) {
			new SkillUse().handleCommands(pc, skillId, spellsc_objid, spellsc_x, spellsc_y, null, 0, SkillUse.TYPE_SPELLSC);
 
		} else {
			pc.sendPackets(new S_ServerMessage(281));
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

		@XmlAttribute(name = "SkillId")
		private int _skillId;

		public int getSkillId() {
			return this._skillId;
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "ItemEffectList")
	private static class ItemEffectList implements Iterable<SpellItem> {

		@XmlElement(name = "Item")
		private List<SpellItem> _list;

		public Iterator<SpellItem> iterator() {
			return this._list.iterator();
		}
	}
}