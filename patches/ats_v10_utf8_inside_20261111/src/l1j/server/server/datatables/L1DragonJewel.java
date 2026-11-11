package l1j.server.server.datatables;

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

import l1j.server.server.Account;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.MessageUtil;

@XmlAccessorType(XmlAccessType.FIELD)
public class L1DragonJewel {
	private static Logger _log = Logger.getLogger(L1DragonJewel.class.getName());
	private static final String _path = "./data/xml/Item/DragonJewel.xml";
	private static HashMap<Integer, L1DragonJewel> _dataMap = new HashMap<Integer, L1DragonJewel>();

	@XmlAttribute(name = "ItemId")
	private int _itemId;

	@XmlAttribute(name = "Remove")
	private int _remove;

	@XmlElement(name = "Effect")
	private Effect _effect;

	public static L1DragonJewel get(int id) {
		return (L1DragonJewel) _dataMap.get(Integer.valueOf(id));
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

	private static void loadXml(HashMap<Integer, L1DragonJewel> dataMap) {
		try {
			JAXBContext context = JAXBContext.newInstance(new Class[] { ItemEffectList.class });

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(_path);
			ItemEffectList list = (ItemEffectList) um.unmarshal(file);

			for (L1DragonJewel each : list) {
				if (ItemTable.getInstance().getTemplate(each.getItemId()) == null) {
					System.out.println("존재하지 않는 아이템번호입니다. " + each.getItemId());
				} else {
					dataMap.put(Integer.valueOf(each.getItemId()), each);
				}				
			}	
		} catch (Exception e) {
			_log.log(Level.SEVERE, "./data/xml/Item/DragonJewel.xmlload failed.", e);
			System.exit(0);
		}
	}

	public static void load() {
		loadXml(_dataMap);
	}

	public static void reload() {
		HashMap<Integer, L1DragonJewel> dataMap = new HashMap<Integer, L1DragonJewel>();
		loadXml(dataMap);
		_dataMap = dataMap;
	}

	public boolean use(L1PcInstance pc, ItemInstance item) {
		if (pc.hasSkillEffect(SkillId.디케이_포션)) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_4));
			return false;
		}

		Effect effect = getEffect();

		if (effect == null) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1));
			return false;
		}

		//int itemId = item.getItemId();
		 


		Account account = pc.getAccount();
		if (account.getBlessOfAin() + effect.getAinHasad() * 10000 > 2000000) {
			pc.sendPackets(new S_ServerMessage(4384)); 
			return false;
		}
 

		account.addBlessOfAin(pc, effect.getAinHasad() * 10000);
		pc.sendPackets(new S_PacketBox(S_PacketBox.EINHASAD, pc.getAccount().getBlessOfAin()));
		if (effect.getTime() > 0) {
			pc.setSkillEffect(effect.getSkillId(), effect.getTime() * 1000);
		}
  
		pc.sendPackets(new S_ServerMessage(effect.getMsgId()));
		S_SkillSound ss = S_SkillSound.get(pc.getId(), effect.getGfxId());
		pc.sendPackets(ss);
		pc.broadcastPacket(ss);

		if (getRemove() > 0) {
			pc.getInventory().removeItem(item, getRemove());
		}

		return true;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	private static class Effect {

		@XmlAttribute(name = "AinHasad")
		private int _ainHasad;

		@XmlAttribute(name = "SkillId")
		private int _skillId;

		@XmlAttribute(name = "Time")
		private int _time;

		@XmlAttribute(name = "GfxId")
		private int _gfxid;

		@XmlAttribute(name = "Type")
		private int _type;

		@XmlAttribute(name = "Type2")
		private int _type2;

		@XmlAttribute(name = "MsgId")
		private int _msgId;

		private int getAinHasad() {
			return this._ainHasad;
		}

		private int getSkillId() {
			return this._skillId;
		}

		private int getTime() {
			return this._time;
		}

		private int getGfxId() {
			return this._gfxid;
		}

		private int getMsgId() {
			return this._msgId;
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "ItemEffectList")
	private static class ItemEffectList implements Iterable<L1DragonJewel> {

		@XmlElement(name = "Item")
		private List<L1DragonJewel> _list;

		public Iterator<L1DragonJewel> iterator() {
			return this._list.iterator();
		}
	}
}