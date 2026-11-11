package l1j.server.server.model.item;

import java.io.File;
import java.text.DecimalFormat;
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
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.random.RandomGenerator;
import l1j.server.server.random.RandomGeneratorFactory;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.SkillUtil;
import l1j.server.server.utils.MessageUtil;

@XmlAccessorType(XmlAccessType.FIELD)
public class BlessTypeEnchant {

	public static final int Ac = 1;
	public static final int Str = 2;
	public static final int Con = 3;
	public static final int Dex = 4;
	public static final int Int = 5;
	public static final int Wis = 6;
	public static final int Cha = 7;
	public static final int Hp = 8;
	public static final int Mp = 9;
	public static final int Hpr = 10;
	public static final int Mpr = 11;
	public static final int Mr = 12;
	public static final int Sp = 13;
	public static final int Dmg = 14;
	public static final int BowDmg = 15;
	public static final int Hit = 16;
	public static final int BowHit = 17;
	public static final int DmgReduction = 18;
	public static final int WeightReduction = 19;
	
	public static final int regist_technology = 20;
	public static final int technique_hit = 21;
	public static final int regist_spirit = 22;
	public static final int spirit_hit = 23;
	public static final int magic_hit = 24;
	
	
	public static final int PvpDmg = 31;
	public static final int PvpReduction = 32;
	public static final int Exp = 33;
	public static final int Er = 36;
	


	private static RandomGenerator _random = RandomGeneratorFactory.newRandom();

	private static Logger _log = Logger.getLogger(BlessTypeEnchant.class.getName());

	@XmlAttribute(name = "Remove")
	private int _remove;

	@XmlAttribute(name = "Kind")
	private int _kind;

	@XmlElement(name = "Effect")
	private Effect _effect;
	private static final String _path = "./data/xml/Item/BlessTypeEnchant.xml";
	private static HashMap<Integer, BlessTypeEnchant> _dataMap = new HashMap<Integer, BlessTypeEnchant>();

	@XmlAttribute(name = "ItemId")
	private int _itemId;

	private int getRemove() {
		return this._remove;
	}

	private int getKind() {
		return this._kind;
	}

	private Effect getEffect() {
		return this._effect;
	}

	public static BlessTypeEnchant get(int id) {
		return (BlessTypeEnchant) _dataMap.get(Integer.valueOf(id));
	}

	private int getItemId() {
		return this._itemId;
	}

	private static void loadXml(HashMap<Integer, BlessTypeEnchant> dataMap) {
		try {
			JAXBContext context = JAXBContext.newInstance(new Class[] { ItemEffectList.class });

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(_path);
			ItemEffectList list = (ItemEffectList) um.unmarshal(file);

			for (BlessTypeEnchant each : list) {
				if (ItemTable.getInstance().getTemplate(each.getItemId()) == null) {
					System.out.println("존재하지 않는 아이템번호입니다. " + each.getItemId());
				} else {
					dataMap.put(Integer.valueOf(each.getItemId()), each);
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "./data/xml/Item/BlessTypeEnchant.xmlload failed.", e);
		}
	}

	public static void load() {
		loadXml(_dataMap);
	}

	public static void reload() {
		HashMap<Integer, BlessTypeEnchant> dataMap = new HashMap<Integer, BlessTypeEnchant>();
		loadXml(dataMap);
		_dataMap = dataMap;
	}

	public boolean use(L1PcInstance pc, ItemInstance item, ItemInstance target) {

		if(target != null) {
			
		}
		if (getKind() == 1 && target.getItem().getType2() != 1) {
			pc.sendPackets(new S_SystemMessage("인챈트 가능: 무기"));
			return false;
		}
		if (getKind() == 2 && target.getItem().getType2() != 2) {
			pc.sendPackets(new S_SystemMessage("인챈트 가능: 방어구"));
			return false;
		}

		if (getKind() == 3 && !(target.getItem().getType() >= 8 && target.getItem().getType() <= 12
				|| target.getItem().getType() == 30 || target.getItem().getType() == 32
				|| target.getItem().getType() == 33)) {
			pc.sendPackets(new S_SystemMessage("인챈트 가능: 악세사리"));
			return false;
		}

		if (getKind() == 4 && target.getItem().getType2() == 1) {
			pc.sendPackets(new S_SystemMessage("인챈트 가능: 방어구"));
			return false;
		}

		if (getKind() == 4 && target.getItem().getType2() == 2
				&& (target.getItem().getType() >= 8 && target.getItem().getType() <= 12
						|| target.getItem().getType() == 30 || target.getItem().getType() == 32
						|| target.getItem().getType() == 33)) {
			pc.sendPackets(new S_SystemMessage("인챈트 가능: 방어구"));
			return false;
		}
		if (target.getItem().getType2() == 0) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
			return false;
		}

		if (target.getBless() >= 127) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_40));
			return false;
		} 
		
		if (target.getBlessType() != 0) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_41));
			return false;
		}


		SkillUtil.cancelBarrier(pc);

		int maxChargeCount = item.getItem().getMaxChargeCount();
		int chargeCount = item.getChargeCount();
		if (maxChargeCount > 0 && chargeCount <= 0) {
			pc.sendPackets(new S_ServerMessage(79));
			return false;
		}

		Effect effect = getEffect();

		if (effect.isCantUseItem().length() > 1) {
			String[] itemids = effect.isCantUseItem().split(",");

			for (String id : itemids) {
				if (Integer.valueOf(id) == target.getItem().getItemId()) {
					pc.sendPackets(new S_SystemMessage("해당 아이템에는 사용이 불가합니다."));
					return false;
				}
			}

		}

		int rnd = _random.nextInt(1000000) + 1;

		int total_chance = 0;

		String[] chances = effect.getChance().split(",");
		String[] addValues = effect.getAddValue().split(",");

		int chance_size = chances.length;

		int add_value = 0;
		for (int i = 0; i < chance_size; i++) {
			total_chance += Integer.valueOf(chances[i]);
			if (rnd < total_chance * 10000) {
				add_value = Integer.valueOf(addValues[i]);
				break;
			}
		}

		if (add_value != 0) {
			if (target.isEquipped()) {
				pc.getEquipSlot().remove(target);
				target.setBlessType(effect.getType());
				target.setBlessTypeValue(add_value);
				target.setBless(0);
				pc.getEquipSlot().set(target);
			} else {
				target.setBlessType(effect.getType());
				target.setBlessTypeValue(add_value);
				target.setBless(0);

			}

			pc.getInventory().updateItem(target, L1PcInventory.COL_BLESS_SCROLL);
			pc.getInventory().saveItem(target, L1PcInventory.COL_BLESS_SCROLL);
			pc.getInventory().updateItem(target, L1PcInventory.COL_ENCHANTLVL);
			pc.sendPackets(new S_SystemMessage(target.getLogName() + "에 축복의 기운이 스며듭니다."));
		} else {
			pc.sendPackets(new S_SystemMessage("축복의 기운이 스며들지 못하였습니다."));
		}

		if (pc.isGm()) {
			DecimalFormat format = new DecimalFormat(".##");
			pc.sendPackets(new S_SystemMessage("성공확률: " + format.format(total_chance) + " >= 랜덤: " + format.format(rnd / 10000.0D)));
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

		@XmlAttribute(name = "AddValue")
		private String _addvalue;

		@XmlAttribute(name = "Type")
		private int _type;

		@XmlAttribute(name = "Chance")
		private String _chance;

		@XmlAttribute(name = "CantUseItem")
		private String _cantUseItem;

		public String getAddValue() {
			return this._addvalue;
		}

		public int getType() {
			return this._type;
		}

		public String getChance() {
			return this._chance;
		}

		public String isCantUseItem() {
			return _cantUseItem;
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "ItemEffectList")
	private static class ItemEffectList implements Iterable<BlessTypeEnchant> {

		@XmlElement(name = "Item")
		private List<BlessTypeEnchant> _list;

		public Iterator<BlessTypeEnchant> iterator() {
			return this._list.iterator();
		}
	}

}