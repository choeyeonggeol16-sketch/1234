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
import l1j.server.commons;
import l1j.server.Templates.L1EtcItem;
import l1j.server.configure.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.random.RandomGenerator;
import l1j.server.server.random.RandomGeneratorFactory;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_UseAttackSkill;
import l1j.server.server.utils.SkillUtil;
import l1j.server.server.utils.MessageUtil;

@XmlAccessorType(XmlAccessType.FIELD)
public class SpellWand {
	private static Logger _log = Logger.getLogger(SpellWand.class.getName());

	private static RandomGenerator _random = RandomGeneratorFactory.newRandom();
	private static final String _path = "./data/xml/Item/SpellWand.xml";
	private static HashMap<Integer, SpellWand> _dataMap = new HashMap<Integer, SpellWand>();

	@XmlAttribute(name = "ItemId")
	private int _itemId;

	@XmlAttribute(name = "Remove")
	private int _remove;

	@XmlElement(name = "Effect")
	private Effect _effect;

	public static SpellWand get(int id) {
		return (SpellWand) _dataMap.get(Integer.valueOf(id));
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
		return true;
	}

	private static void loadXml(HashMap<Integer, SpellWand> dataMap) {
		try {
			JAXBContext context = JAXBContext.newInstance(new Class[] { ItemEffectList.class });

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(_path);
			ItemEffectList list = (ItemEffectList) um.unmarshal(file);

			for (SpellWand each : list) {
				if(each.init()) {
					dataMap.put(Integer.valueOf(each.getItemId()), each);
				}
			}
				
		} catch (Exception e) {
			_log.log(Level.SEVERE, "./data/xml/Item/SpellWand.xmlload failed.", e);
		}
	}

	public static void load() {
		loadXml(_dataMap);
	}

	public static void reload() {
		HashMap<Integer, SpellWand> dataMap = new HashMap<Integer, SpellWand>();
		loadXml(dataMap);
		_dataMap = dataMap;
	}

	public boolean use(L1PcInstance pc, ItemInstance item, int spellsc_objid, int spellsc_x, int spellsc_y) {

		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(pc);

		ItemInstance useItem = pc.getInventory().getItem(item.getId());
		int delay_id = 0;
		delay_id = ((L1EtcItem) useItem.getItem()).get_delayid();
		if (delay_id != 0) { 
			if (pc.hasItemDelay(delay_id)) {
				return false;
			}
		}
		
		// 피시가 고스트이고 , 죽은상태이고, 텔레포트 상태일때 
		if (pc.isGhost() || pc.isDead() || pc.getTeleport()) {
			return false;
		}

		// 오픈 대기 상태 중일때
		if (Config.STANDBY_SERVER) {
			return false;
		}
		// 무게가 200이상이거나 인벤토리 사이즈가 180일때 
		if (pc.getInventory().getWeight100() >= 200 || pc.getInventory().getSize() >= 180) {
			pc.sendPackets(new S_ServerMessage(110));
			return false;
		}

		// 인비지 상태거나 인비지 딜레이 상태일때 
		if (pc.isInvisble() || pc.isInvisDelay()) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_3));
			return false;
		}

		Effect effect = getEffect();

		int dmg = effect.getDamage() + _random.nextInt(effect.getRandomDamage() + 1);

		if (pc.getId() != spellsc_objid) {
			pc.setHeading(pc.targetDirection(spellsc_x, spellsc_y));
		}
		L1Object obj = L1World.getInstance().findObject(spellsc_objid);
		if (spellsc_objid == 0 || !pc.glanceCheck(spellsc_x, spellsc_y) || obj == null || !(obj instanceof L1Character)) {
			if (pc.getCurrentSpriteId() != pc.getClassId()) {
				S_UseAttackSkill s = S_UseAttackSkill.get(pc, 0, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Pickup, true);
				pc.sendPackets(s);
				pc.broadcastPacket(s);
			} else {
				S_UseAttackSkill s = S_UseAttackSkill.get(pc, 0, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Wand, true);
				pc.sendPackets(s);
				pc.broadcastPacket(s);
			}
		} else {
			L1Character target = (L1Character) obj;

			if (target.isDead() || target.isParalyzed()) {
				dmg = 0;
			}

			if (target instanceof L1PcInstance) {
				L1PcInstance targetPc = (L1PcInstance) target;

				if (targetPc.hasSkillEffect(SkillId.앱솔루트_배리어)) {

					if (pc.getCurrentSpriteId() != pc.getClassId()) {
						S_UseAttackSkill useAttackSkill = S_UseAttackSkill.gett(pc, spellsc_objid, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Pickup, 0, true);
						targetPc.sendPackets(useAttackSkill);
						targetPc.broadcastPacket(useAttackSkill);  
					} else {
						S_UseAttackSkill useAttackSkill = S_UseAttackSkill.gett(pc, spellsc_objid, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Wand, 0, true);
						targetPc.sendPackets(useAttackSkill);
						targetPc.broadcastPacket(useAttackSkill);  
					}  

					if (getRemove() > 0) {
						if (item.getChargeCount() > 1) {
							item.setChargeCount(item.getChargeCount() - getRemove());
							pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
						} else if (item.getCount() > 1) {
							item.setCount(item.getCount() - getRemove());
							pc.getInventory().updateItem(item, L1PcInventory.COL_COUNT);
						} else {
							pc.getInventory().removeItem(item, getRemove());
						}
					}
					return false;
				}

				if(SkillUtil.isCounterMagic(targetPc)) {

					if (pc.getCurrentSpriteId() != pc.getClassId()) {
						S_UseAttackSkill useAttackSkill = S_UseAttackSkill.gett(pc, spellsc_objid, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Pickup, 0, true);
						pc.sendPackets(useAttackSkill);
						pc.broadcastPacket(useAttackSkill);
					} else {
						S_UseAttackSkill useAttackSkill = S_UseAttackSkill.gett(pc, spellsc_objid, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Wand, 0, true);
						pc.sendPackets(useAttackSkill);
						pc.broadcastPacket(useAttackSkill);
					}
					return false;
				}
				SkillUtil.cancelEraseMagic(targetPc);

				if (pc.getZoneType() == 1 || targetPc.getZoneType() == 1) {
					dmg = 0;	
				}
				// 속성 방어 데미지 감소 처리
				double 속성방어 = wandAttrResistance(targetPc, 8);			
				dmg -= 속성방어;				
				
				if(targetPc != null && !(pc.getZoneType() == 1) || !(targetPc.getZoneType() == 1)) {
					commons.getReducIitems(targetPc, dmg);
					dmg -= targetPc.getDamageReduction();
					if (targetPc != null && targetPc.hasSkillEffect(SkillId.이뮨_투함)) {
						dmg *= Config.IMMNUE_TO_HARM_RATE;
					}
				}	
				if (dmg <= 0) {
					dmg = 0;
				}
				if (pc.getCurrentSpriteId() != pc.getClassId()) {
					S_UseAttackSkill useAttackSkill = S_UseAttackSkill.gett(pc, spellsc_objid, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Pickup, dmg > 0 ? 6 : 0, true);
					pc.sendPackets(useAttackSkill);
					pc.broadcastPacket(useAttackSkill);
				} else {
					S_UseAttackSkill useAttackSkill = S_UseAttackSkill.gett(pc, spellsc_objid, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Wand, dmg > 0 ? 6 : 0, true);
					pc.sendPackets(useAttackSkill);
					pc.broadcastPacket(useAttackSkill);
				}

				if (dmg > 0) {
					targetPc.receiveDamage(pc, dmg);
				}
			} else if (target instanceof MonsterInstance) {
				MonsterInstance mob = (MonsterInstance) target;
				if (pc.getCurrentSpriteId() != pc.getClassId()) {
					S_UseAttackSkill useAttackSkill = S_UseAttackSkill.gett(pc, spellsc_objid, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Pickup, dmg > 0 ? 6 : 0, true);
					pc.sendPackets(useAttackSkill);
					pc.broadcastPacket(useAttackSkill);
				} else {
					S_UseAttackSkill useAttackSkill = S_UseAttackSkill.gett(pc, spellsc_objid, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Wand, dmg > 0 ? 6 : 0, true);
					pc.sendPackets(useAttackSkill);
					pc.broadcastPacket(useAttackSkill);
				}

				if (dmg > 0) {
					mob.receiveDamage(pc, dmg);
				}
			} else if (target instanceof L1NpcInstance) { 
				if (pc.getCurrentSpriteId() != pc.getClassId()) {
					S_UseAttackSkill s = S_UseAttackSkill.gett(pc, spellsc_objid, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Pickup, 6, true);
					pc.sendPackets(s);
					pc.broadcastPacket(s);
				} else {
					S_UseAttackSkill s = S_UseAttackSkill.gett(pc, spellsc_objid, effect.getGfxId(), spellsc_x, spellsc_y, ActionCodes.ACTION_Wand, 6, true);
					pc.sendPackets(s);
					pc.broadcastPacket(s);
				}
			}

		}
		if (getRemove() > 0) {
			if (item.getChargeCount() > 1) {
				item.setChargeCount(item.getChargeCount() - getRemove());
				pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
			} else if (item.getCount() > 1) {
				item.setCount(item.getCount() - getRemove());
				pc.getInventory().updateItem(item, L1PcInventory.COL_COUNT);
			} else {
				pc.getInventory().removeItem(item, getRemove());
			}
		}

		return true;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	private static class Effect {

		@XmlAttribute(name = "GfxId")
		private int _gfxId;

		@XmlAttribute(name = "Damage")
		private int _damage;

		@XmlAttribute(name = "RandomDamage")
		private int _randomDamage;

		public int getGfxId() {
			return this._gfxId;
		}
		public int getDamage() {
			return this._damage;
		}
		public int getRandomDamage() {
			return this._randomDamage;
		}

	}

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "ItemEffectList")
	private static class ItemEffectList implements Iterable<SpellWand> {

		@XmlElement(name = "Item")
		private List<SpellWand> _list;

		public Iterator<SpellWand> iterator() {
			return this._list.iterator();
		}
	}

	/**
	 * 흑단 막대의 속성타입은 바람이므로 바람 속성 방어 감소 처리만 한다.
	 * @param attr
	 * @return
	 */ 
	private double wandAttrResistance(L1Character defender, int attr) {
		int dmg = 0;
		if(defender instanceof L1PcInstance) {
			L1PcInstance targetPc = (L1PcInstance) defender;		
			switch (attr) {
			case 8: 
				dmg = targetPc.getResistance().getWind();
				break;
			}
		}	
		int resistFloor = (int) (0.32 * Math.abs(dmg));
		if (dmg >= 0) {
			resistFloor *= 1;
		} else {
			resistFloor *= -1;
		}
		double attrDeffence = resistFloor / 8.0;
		return attrDeffence;
	}
}