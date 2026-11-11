/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model;

import java.util.ArrayList;

import l1j.server.SetItems.SetItemsLoader;
import l1j.server.Templates.L1Item;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_Ability;
import l1j.server.server.serverpackets.S_AddSkill;
import l1j.server.server.serverpackets.S_DelSkill;
import l1j.server.server.serverpackets.S_Invis;
import l1j.server.server.serverpackets.S_SkillHaste;

public class EquipmentSlot {
	private L1PcInstance _owner;

	private ItemInstance _weapon;
	private ArrayList<ItemInstance> _armors;

	public EquipmentSlot(L1PcInstance owner) {
		_owner = owner;
		_armors = new ArrayList<ItemInstance>();
	}

	private void setWeapon(ItemInstance weapon) {
		_owner.setWeapon(weapon);
		_owner.settingdmg();
		_owner.setCurrentWeapon(weapon.getItem().getType1());
		weapon.startEquipmentTimer(_owner);
		_weapon = weapon;
	}

	public ItemInstance getWeapon() {
		return _weapon;
	}

	private void setArmor(ItemInstance armor) {
		int itemId = armor.getItem().getItemId();

		_owner.getAC().addAc(armor.getAc() - armor.getAcByEnchantLevel() + armor.getDurability());
		_armors.add(armor);
		
		if (itemId == ItemId.투명_망토 || itemId == ItemId.발록_핏빛_망토 || itemId == ItemId.축복_투명_망토) {
			if (!_owner.hasSkillEffect(SkillId.인비지블리티)) {
				_owner.killSkillEffectTimer(SkillId.블라인드_하이딩);
				_owner.setSkillEffect(SkillId.인비지블리티, 0);
				_owner.sendPackets(new S_Invis(_owner.getId(), 1), true);
				L1World.getInstance().broadcastPacketToAll(new S_Invis(_owner.getId(), 1));
			}
		}
		if (itemId == ItemId.순간_이동_조종_반지 || itemId == ItemId.순간_이동_지배_반지) {
			_owner.sendPackets(new S_Ability(1, true));
		}
		if (itemId == ItemId.기마용_투구) {
			if (armor.getChargeCount() != 0) {
				armor.setChargeCount(armor.getChargeCount() - 1);
				_owner.getInventory().updateItem(armor, L1PcInventory.COL_CHARGE_COUNT);
			}
		}
		armor.startEquipmentTimer(_owner);
	}

	public ArrayList<ItemInstance> getArmors() {
		return _armors;
	}

	private void removeWeapon(ItemInstance weapon) {
		_owner.setWeapon(null);
		_owner.setCurrentWeapon(0);
		weapon.stopEquipmentTimer(_owner);
		_weapon = null;
		if (_owner.hasSkillEffect(SkillId.카운터_배리어)) {
			_owner.removeSkillEffect(SkillId.카운터_배리어);
		}
	}

	private void removeArmor(ItemInstance armor) {
	
		_armors.remove(armor);
		
		int itemId = armor.getItem().getItemId();
		_owner.getAC().addAc(-(armor.getAc() - armor.getAcByEnchantLevel() + armor.getDurability()));

		if (itemId == ItemId.투명_망토 || itemId == ItemId.발록_핏빛_망토 || itemId == ItemId.축복_투명_망토) {
			_owner.delInvis();
		}
		if (itemId == ItemId.순간_이동_조종_반지 || itemId == ItemId.순간_이동_지배_반지) {
			_owner.sendPackets(new S_Ability(1, false));
		} 
		armor.stopEquipmentTimer(_owner); 
		
	}

	public void set(ItemInstance equipment) {
		L1Item item = equipment.getItem();

		if (item.getType2() == L1Item.CATEGORY_ETC) { 
			return;
		}
		if (equipment.getWeaponMeleeDmg() != 0) {
			_owner.addMeleeWeaponDmg(equipment.getWeaponMeleeDmg());
		}
		if (equipment.getArmorMeleeDmg() != 0) {
			_owner.addMeleeDmg(equipment.getArmorMeleeDmg());
		}	
		if (equipment.getHitModifier() != 0) {
			_owner.addMeleeHit(equipment.getHitModifier());
		}		
		if (equipment.getWeaponRangedDmg() != 0) {
			_owner.addRangedWeaponDmg(equipment.getWeaponRangedDmg());
		}		
		if (equipment.getBowHitModifier() != 0) {
			_owner.addRangedHit(equipment.getBowHitModifier());
		}
		if (equipment.getArmorRegendDmg() != 0) {
			_owner.addRangedDmg(equipment.getArmorRegendDmg());
		}		
		if (equipment.getPvPDamage() != 0) {
			_owner.addDmgupPvP(equipment.getPvPDamage());
		}
		if (equipment.getPvpReduction() != 0) {
			_owner.addDmgDownPvP(equipment.getPvpReduction());
		}
		if (equipment.getStr() != 0) {
			_owner.getAbility().addAddedStr(equipment.getStr());
		}
		if (equipment.getCon() != 0) {
			_owner.getAbility().addAddedCon(equipment.getCon());
		}
		if (equipment.getDex() != 0) {
			_owner.getAbility().addAddedDex(equipment.getDex());
		}
		if (equipment.getInt() != 0) {
			_owner.getAbility().addAddedInt(equipment.getInt());
		}
		if (equipment.getWis() != 0) {
			_owner.getAbility().addAddedWis(equipment.getWis());
		}
		if (equipment.getCha() != 0) {
			_owner.getAbility().addAddedCha(equipment.getCha());
		}
		if (equipment.getHp() != 0) {
			_owner.addMaxHp(equipment.getHp());
		}
		if (equipment.getMp() != 0) {
			_owner.addMaxMp(equipment.getMp());
		}
		if (equipment.getHpr() != 0) {
			_owner.addHpr(equipment.getHpr());
		} 
		if (equipment.getMpr() != 0) {
			_owner.addMpr(equipment.getMpr());
		}
		if (equipment.getExpByItem() != 0) {
			_owner.addExpEmblem(equipment.getExpByItem());
		}
		if (equipment.getMr() != 0) {
			_owner.getResistance().addMr(equipment.getMr());
		}
		if (equipment.getSp() != 0) {
			_owner.addSp(equipment.getSp()); 
		}
		if (equipment.getDamageReduction() != 0) {
			_owner.addDamageReduction(equipment.getDamageReduction());
		}
		if(equipment.getPotionRecoveryAmount() != 0) {
			_owner.addPotionRecoveryAmount(equipment.getPotionRecoveryAmount());
		}
		if(equipment.getPotionRecovery() != 0) {
			_owner.addPotionRecovery(equipment.getPotionRecovery());
		}
		if (equipment.getMeleeCritical() != 0) {
			_owner.addMeleeCritical(equipment.getMeleeCritical());
		}
		if (equipment.getRangedCritical() != 0) {
			_owner.addRangedCritical(equipment.getRangedCritical());
		}
		if (equipment.getMagicCritical() != 0) {
			_owner.addMagicCritical(equipment.getMagicCritical());
		}
		if (equipment.getWeightReduction() != 0) {
			_owner.addWeightReduction(equipment.getWeightReduction());
		}
		if (equipment.getReductionCancel() != 0) {
			_owner.addReducCancel(equipment.getReductionCancel());
		}
		if (equipment.getDefenseFire() != 0) {
			_owner.getResistance().addFire(equipment.getDefenseFire());
		}
		if (equipment.getDefenseWater() != 0) {
			_owner.getResistance().addWater(equipment.getDefenseWater());
		}
		if (equipment.getDefenseWind() != 0) {
			_owner.getResistance().addWind(equipment.getDefenseWind());
		}
		if (equipment.getDefenseEarth() != 0) {
			_owner.getResistance().addEarth(equipment.getDefenseEarth());
		}
		if (equipment.get_regist_technology() != 0)	{
			_owner.getResistance().addregist_technology(equipment.get_regist_technology());
		}
		if (equipment.get_technique_hit() != 0) {
			_owner.getResistance().addtechnique_hit(equipment.get_technique_hit());
		}
		if (equipment.get_regist_spirit() != 0)	{
			_owner.getResistance().addregist_spirit(equipment.get_regist_spirit()); 
		}
		if (equipment.get_spirit_hit() != 0) {
			_owner.getResistance().addspirit_hit(equipment.get_spirit_hit());
		}
		if (equipment.get_magic_hit() != 0) {
			_owner.addMagicHit(equipment.get_magic_hit());
		}	
		if(equipment.getProbabilityMagicEvasion() != 0) {
			_owner.getResistance().addProbabilityMagicEvasion(equipment.getProbabilityMagicEvasion());
		}
		switch (item.getItemId()) {
		case ItemId.파푸리온_완력: case ItemId.파푸리온_인내력: case ItemId.파푸리온_예지력: case ItemId.파푸리온_마력:
			_owner.startPapurionBlessing();
			_owner.toArmorEquipped().setPapurionArmor(true);
			_owner.toArmorEquipped().setPapurionArmorEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			 break;
		case ItemId.린드비오르_완력: case ItemId.린드비오르_예지력: case ItemId.린드비오르_인내력: case ItemId.린드비오르_마력:
			_owner.startLindviorBlessing();
			_owner.toArmorEquipped().setLindviorArmor(true);
			_owner.toArmorEquipped().setLindviorArmorEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel()); 
			_owner.toArmorEquipped().setArmor(equipment);
			 break;
		case ItemId.발라카스_인내력:
			_owner.startValakasBlessing();
			_owner.toArmorEquipped().setValakasArmorRegend(true);
			_owner.toArmorEquipped().setValakasArmorRegendEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		case ItemId.발라카스_완력: case ItemId.발라카스_예지력:	
			_owner.startValakasBlessing();
			_owner.toArmorEquipped().setValakasArmorMelee(true);
			_owner.toArmorEquipped().setValakasArmorMeleeEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		case ItemId.발라카스_마력:
			_owner.startValakasBlessing();
			_owner.toArmorEquipped().setValakasArmorMagic(true);
			_owner.toArmorEquipped().setValakasArmorMagicEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		case ItemId.룸티스_검은빛_귀걸이:	
			_owner.toArmorEquipped().setRoomtisblackArmor(true);
			_owner.toArmorEquipped().setRoomtisblackEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		case ItemId.축복_룸티스_검은빛_귀걸이:	
			_owner.toArmorEquipped().setBlessRoomtisBlackArmor(true);
			_owner.toArmorEquipped().setBlessRoomtisBlackEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		case ItemId.룸티스_붉은빛_귀걸이:	
			_owner.toArmorEquipped().setRoomtisRedArmor(true);
			_owner.toArmorEquipped().setRoomtisRedEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		case ItemId.축복_룸티스_붉은빛_귀걸이:	
			_owner.toArmorEquipped().setBlessRoomtisRedArmor(true);
			_owner.toArmorEquipped().setBlessRoomtisRedEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		case ItemId.스냅퍼_체력_반지:	
			_owner.toArmorEquipped().setSnapperHealthRing(true);
			_owner.toArmorEquipped().setSnapperHealthRingEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		case ItemId.축복_스냅퍼_체력_반지:	
			_owner.toArmorEquipped().setBlessSnapperHealthRing(true);
			_owner.toArmorEquipped().setBlessSnapperHealthRingEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		case ItemId.반역자_방패:	
			_owner.toArmorEquipped().setTraitorshield(true);
			_owner.toArmorEquipped().setTraitorShieldEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		case ItemId.마법_투구_신속:	
			_owner.toArmorEquipped().setMagicHelmFast(true);
			break;
		case ItemId.마법_투구_치유:	
			_owner.toArmorEquipped().setMagicHelmCure(true);
			break;
		case ItemId.마법_투구_힘:	
			_owner.toArmorEquipped().setMagicHelmStr(true);
			break;
		case ItemId.낡은_바람_투구:	
			_owner.toArmorEquipped().setOldWindHelm(true);
			break;
		case ItemId.바람_투구:	
			_owner.toArmorEquipped().setWindHelm(true);
			break;
		case ItemId.수중_부츠:	
			_owner.toArmorEquipped().setUnderwaterBreathArmor(true);
			break;
		case ItemId.변신_조종_반지:	
			_owner.toArmorEquipped().setPolyControlRing(true);
			break;
		case ItemId.소환_조종_반지:	
			_owner.toArmorEquipped().setSummonControlRing(true);
			break;
		case ItemId.제니스_반지: case ItemId.바포메트_갑옷: case ItemId.안타라스_완력: 
		case ItemId.안타라스_예지력: case ItemId.안타라스_인내력: case ItemId.안타라스_마력:
			_owner.toArmorEquipped().setPoisonRegistArmor(true);
			break;
		case ItemId.포효_이도류:
			equipment.getDoubleDmgChance();
			break;
		case ItemId.신성한_요정족_판금_갑옷:
			_owner.toArmorEquipped().setHolyFairyRlateArmor(true);
			_owner.toArmorEquipped().setHolyFairyRlateArmorEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		case ItemId.신성한_요정족_방패:
			_owner.toArmorEquipped().setHolyFairyShield(true);
			_owner.toArmorEquipped().setHolyFairyShieldEn(equipment.getEnchantLevel() < 0 ? 0 : equipment.getEnchantLevel());
			break;
		}
		if (item.isHasteItem()) {
			_owner.addHasteItemEquipped(1);
			_owner.removeHasteSkillEffect();
			if (_owner.getMoveSpeed() != 1) {
				_owner.setMoveSpeed(1);
				S_SkillHaste haste = S_SkillHaste.get(_owner.getId(), 1, -1);
				_owner.sendPackets(haste);
				_owner.broadcastPacket(haste, true);
			}
		}
		_owner.getEquipSlot().setMagicHelm(equipment);

		if (item.getType2() == L1Item.CATEGORY_WEAPON) {
			setWeapon(equipment);
		} else if (item.getType2() == L1Item.CATEGORY_ARMOR) {
			setArmor(equipment);
		}	
		if (SetItemsLoader.getInstance().send_view_set_items_check(equipment)) {
			SetItemsLoader.getInstance().equip_set_items(_owner, equipment, true);
		}
	}

	public void remove(ItemInstance equipment) {
		L1Item item = equipment.getItem();
		
		if (item.getType2() == L1Item.CATEGORY_ETC) {
			return;
		}		
		if (equipment.getWeaponMeleeDmg() != 0) {
			_owner.addMeleeWeaponDmg(-equipment.getWeaponMeleeDmg());
		}	
		if(equipment.getArmorMeleeDmg() != 0) {
			_owner.addMeleeDmg(-equipment.getArmorMeleeDmg());
		}	
		if (equipment.getHitModifier() != 0) {
			_owner.addMeleeHit(-equipment.getHitModifier());
		}		
		if (equipment.getWeaponRangedDmg() != 0) {
			_owner.addRangedWeaponDmg(-equipment.getWeaponRangedDmg());
		}			
		if (equipment.getBowHitModifier() != 0) {
			_owner.addRangedHit(-equipment.getBowHitModifier());
		}		
		if (equipment.getArmorRegendDmg() != 0) {
			_owner.addRangedDmg(-equipment.getArmorRegendDmg());
		}
		if (equipment.getPvPDamage() != 0) {
			_owner.addDmgupPvP(-equipment.getPvPDamage());
		}
		if (equipment.getPvpReduction() != 0) {
			_owner.addDmgDownPvP(-equipment.getPvpReduction());
		}
		if (equipment.getStr() != 0) {
			_owner.getAbility().addAddedStr(-equipment.getStr());
		}
		if (equipment.getCon() != 0) {
			_owner.getAbility().addAddedCon(-equipment.getCon());
		}
		if (equipment.getDex() != 0) {
			_owner.getAbility().addAddedDex(-equipment.getDex());
		}
		if (equipment.getInt() != 0) {
			_owner.getAbility().addAddedInt(-equipment.getInt());
		}
		if (equipment.getWis() != 0) {
			_owner.getAbility().addAddedWis(-equipment.getWis());
		}
		if (equipment.getCha() != 0) {
			_owner.getAbility().addAddedCha(-equipment.getCha());
		}
		if (equipment.getHp() != 0) {
			_owner.addMaxHp(-equipment.getHp());
		}
		if (equipment.getMp() != 0) {
			_owner.addMaxMp(-equipment.getMp());
		}
		if (equipment.getHpr() != 0) {
			_owner.addHpr(-equipment.getHpr());
		}
		if (equipment.getMpr() != 0) {
			_owner.addMpr(-equipment.getMpr());
		}
		if (equipment.getExpByItem() != 0) {
			_owner.addExpEmblem(-equipment.getExpByItem());
		}
		if (equipment.getMr() != 0) {
			_owner.getResistance().addMr(-equipment.getMr());
		}
		if (equipment.getSp() != 0) {
			_owner.addSp(-equipment.getSp());
		}
		if(equipment.getPotionRecoveryAmount() != 0) {
			_owner.addPotionRecoveryAmount(-equipment.getPotionRecoveryAmount());
		}
		if(equipment.getPotionRecovery() != 0) {
			_owner.addPotionRecovery(-equipment.getPotionRecovery());
		}
		if (equipment.getDamageReduction() != 0) {
			_owner.addDamageReduction(-equipment.getDamageReduction());
		}
		if (equipment.getMeleeCritical() != 0) {
			_owner.addMeleeCritical(-equipment.getMeleeCritical());
		}
		if (equipment.getRangedCritical() != 0) {
			_owner.addRangedCritical(-equipment.getRangedCritical());
		}
		if (equipment.getMagicCritical() != 0) {
			_owner.addMagicCritical(-equipment.getMagicCritical());
		}
		if (equipment.getWeightReduction() != 0) {
			_owner.addWeightReduction(-equipment.getWeightReduction());
		}
		if (equipment.getReductionCancel() != 0) {
			_owner.addReducCancel(-equipment.getReductionCancel());
		}
		if (equipment.getDefenseFire() != 0) {
			_owner.getResistance().addFire(-equipment.getDefenseFire());
		}
		if (equipment.getDefenseWater() != 0) {
			_owner.getResistance().addWater(-equipment.getDefenseWater());
		}
		if (equipment.getDefenseWind() != 0) {
			_owner.getResistance().addWind(-equipment.getDefenseWind());
		}
		if (equipment.getDefenseEarth() != 0) {
			_owner.getResistance().addEarth(-equipment.getDefenseEarth());
		}
		if (equipment.get_regist_technology() != 0)	{//스턴내성
			_owner.getResistance().addregist_technology(-equipment.get_regist_technology());
		}
		if(equipment.get_technique_hit() != 0)	{//스턴적중
			_owner.getResistance().addtechnique_hit(-equipment.get_technique_hit());
		}
		if(equipment.get_regist_spirit() != 0)	{
			_owner.getResistance().addregist_spirit(-equipment.get_regist_spirit()); 
		}
		if(equipment.get_spirit_hit() != 0) {
			_owner.getResistance().addspirit_hit(-equipment.get_spirit_hit());
		}
		if(equipment.get_magic_hit() != 0) {
			_owner.addMagicHit(-equipment.get_magic_hit());
		}
		if(equipment.getProbabilityMagicEvasion() != 0) {
			_owner.getResistance().addProbabilityMagicEvasion(-equipment.getProbabilityMagicEvasion());
		}
		switch (item.getItemId()) {
		case ItemId.파푸리온_완력: case ItemId.파푸리온_인내력: case ItemId.파푸리온_예지력: case ItemId.파푸리온_마력:
			_owner.stopPapurionBlessing();
			_owner.toArmorEquipped().setPapurionArmor(false);
			_owner.toArmorEquipped().setPapurionArmorEn(0);
			 break;
		case ItemId.린드비오르_완력: case ItemId.린드비오르_예지력: case ItemId.린드비오르_인내력: case ItemId.린드비오르_마력:
			_owner.stopLindviorBlessing();
			_owner.toArmorEquipped().setLindviorArmor(false);
			_owner.toArmorEquipped().setLindviorArmorEn(0);
			_owner.toArmorEquipped().setArmor(null);
			 break;
		case ItemId.발라카스_인내력:
			_owner.stopValakasBlessing();
			_owner.toArmorEquipped().setValakasArmorRegend(false);
			_owner.toArmorEquipped().setValakasArmorRegendEn(0);
			break;
		case ItemId.발라카스_완력: case ItemId.발라카스_예지력:	
			_owner.stopValakasBlessing();
			_owner.toArmorEquipped().setValakasArmorMelee(false);
			_owner.toArmorEquipped().setValakasArmorMeleeEn(0);
			break;
		case ItemId.발라카스_마력:
			_owner.stopValakasBlessing();
			_owner.toArmorEquipped().setValakasArmorMagic(false);
			_owner.toArmorEquipped().setValakasArmorMagicEn(0);
			break;
		case ItemId.룸티스_검은빛_귀걸이:	
			_owner.toArmorEquipped().setRoomtisblackArmor(false);
			_owner.toArmorEquipped().setRoomtisblackEn(0);
			break;
		case ItemId.축복_룸티스_검은빛_귀걸이:	
			_owner.toArmorEquipped().setBlessRoomtisBlackArmor(false);
			_owner.toArmorEquipped().setBlessRoomtisBlackEn(0);
			break;
		case ItemId.룸티스_붉은빛_귀걸이:	
			_owner.toArmorEquipped().setRoomtisRedArmor(false);
			_owner.toArmorEquipped().setRoomtisRedEn(0);
			break;
		case ItemId.축복_룸티스_붉은빛_귀걸이:	
			_owner.toArmorEquipped().setBlessRoomtisRedArmor(false);
			_owner.toArmorEquipped().setBlessRoomtisRedEn(0);
			break;
		case ItemId.스냅퍼_체력_반지:	
			_owner.toArmorEquipped().setSnapperHealthRing(false);
			_owner.toArmorEquipped().setSnapperHealthRingEn(0);
			break;
		case ItemId.축복_스냅퍼_체력_반지:	
			_owner.toArmorEquipped().setBlessSnapperHealthRing(false);
			_owner.toArmorEquipped().setBlessSnapperHealthRingEn(0);
			break;
		case ItemId.반역자_방패:	
			_owner.toArmorEquipped().setTraitorshield(false);
			_owner.toArmorEquipped().setTraitorShieldEn(0);
			break;
		case ItemId.마법_투구_신속:	
			_owner.toArmorEquipped().setMagicHelmFast(false);
			break;
		case ItemId.마법_투구_치유:	
			_owner.toArmorEquipped().setMagicHelmCure(false);
			break;
		case ItemId.마법_투구_힘:	
			_owner.toArmorEquipped().setMagicHelmStr(false);
			break;
		case ItemId.낡은_바람_투구:	
			_owner.toArmorEquipped().setOldWindHelm(false);
			break;
		case ItemId.바람_투구:	
			_owner.toArmorEquipped().setWindHelm(false);
			break;
		case ItemId.수중_부츠:	
			_owner.toArmorEquipped().setUnderwaterBreathArmor(false);
			break;
		case ItemId.변신_조종_반지:	
			_owner.toArmorEquipped().setPolyControlRing(false);
			break;
		case ItemId.소환_조종_반지:	
			_owner.toArmorEquipped().setSummonControlRing(false);
			break;
		case ItemId.제니스_반지: case ItemId.바포메트_갑옷: case ItemId.안타라스_완력: 
		case ItemId.안타라스_예지력: case ItemId.안타라스_인내력: case ItemId.안타라스_마력:
			_owner.toArmorEquipped().setPoisonRegistArmor(false);
			break;
		case ItemId.신성한_요정족_판금_갑옷:
			_owner.toArmorEquipped().setHolyFairyRlateArmor(false);
			_owner.toArmorEquipped().setHolyFairyRlateArmorEn(0);
			break;
		case ItemId.신성한_요정족_방패:
			_owner.toArmorEquipped().setHolyFairyShield(false);
			_owner.toArmorEquipped().setHolyFairyShieldEn(0);
			break;
		}
		if (item.isHasteItem()) {
			_owner.addHasteItemEquipped(-1);
			if (_owner.getHasteItemEquipped() == 0) {
				_owner.setMoveSpeed(0);
				S_SkillHaste haste = S_SkillHaste.get(_owner.getId(), 0, 0);
				_owner.sendPackets(haste);
				_owner.broadcastPacket(haste, true);
			}
		}
		_owner.getEquipSlot().removeMagicHelm(_owner.getId(), equipment);
		if (item.getType2() == L1Item.CATEGORY_WEAPON) {
			removeWeapon(equipment);
		} else if (item.getType2() == L1Item.CATEGORY_ARMOR) {
			removeArmor(equipment);
		}
		if(SetItemsLoader.getInstance().send_view_set_items_check(equipment)) {
			SetItemsLoader.getInstance().equip_set_items(_owner, equipment, false);
		}
	}

	public void setMagicHelm(ItemInstance item) {
		if (item.getItemId() == ItemId.마법_투구_신속) {
			_owner.setSkillMastery(SkillId.인챈트_덱스터리티);
			_owner.setSkillMastery(SkillId.헤이스트);
			_owner.sendPackets(new S_AddSkill(0, 0, 0, 2, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
		}
		if (item.getItemId() == ItemId.마법_투구_치유) {
			_owner.setSkillMastery(SkillId.힐);
			_owner.setSkillMastery(SkillId.익스트라_힐);
			_owner.sendPackets(new S_AddSkill(1, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
		}
		if (item.getItemId() == ItemId.마법_투구_힘) {
			_owner.setSkillMastery(SkillId.인챈트_웨폰);
			_owner.setSkillMastery(SkillId.인챈트_마이티);
			_owner.setSkillMastery(SkillId.디텍션);
			_owner.sendPackets(new S_AddSkill(0, 24, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
		}
		if (item.getItemId() == ItemId.낡은_바람_투구) {
			_owner.sendPackets(new S_AddSkill(0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
		}
		if (item.getItemId() == ItemId.바람_투구) {
			_owner.sendPackets(new S_AddSkill(0, 0, 0, 0, 0, 0, 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
		}
	}

	public void removeMagicHelm(int objectId, ItemInstance item) {
		if (item.getItemId() == ItemId.마법_투구_신속) {
			if (!SkillsTable.getInstance().spellCheck(objectId, SkillId.인챈트_덱스터리티)) {
				_owner.removeSkillMastery(SkillId.인챈트_덱스터리티);
				_owner.sendPackets(new S_DelSkill(0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);;
			}
			if (!SkillsTable.getInstance().spellCheck(objectId, SkillId.헤이스트)) {
				_owner.removeSkillMastery(SkillId.헤이스트);
				_owner.sendPackets(new S_DelSkill(0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
			}
		}
		if (item.getItemId() == ItemId.마법_투구_치유) {
			if (!SkillsTable.getInstance().spellCheck(objectId, SkillId.힐)) { 
				_owner.removeSkillMastery(SkillId.힐);
				_owner.sendPackets(new S_DelSkill(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
			}
			if (!SkillsTable.getInstance().spellCheck(objectId, SkillId.익스트라_힐)) { 
				_owner.removeSkillMastery(SkillId.익스트라_힐);
				_owner.sendPackets(new S_DelSkill(0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
			}
		}
		if (item.getItemId() == ItemId.마법_투구_힘) {
			if (!SkillsTable.getInstance().spellCheck(objectId, SkillId.인챈트_웨폰)) {
				_owner.removeSkillMastery(SkillId.인챈트_웨폰);
				_owner.sendPackets(new S_DelSkill(0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
			}
			if (!SkillsTable.getInstance().spellCheck(objectId, SkillId.디텍션)) {
				_owner.removeSkillMastery(SkillId.디텍션);
				_owner.sendPackets(new S_DelSkill(0, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
			}
			if (!SkillsTable.getInstance().spellCheck(objectId, SkillId.인챈트_마이티)) {
				_owner.removeSkillMastery(SkillId.인챈트_마이티);
				_owner.sendPackets(new S_DelSkill(0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
			}
		}
		if (item.getItemId() == ItemId.낡은_바람_투구) {
			if (!SkillsTable.getInstance().spellCheck(objectId, SkillId.헤이스트)) {
				_owner.sendPackets(new S_DelSkill(0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
			}
		}
		if (item.getItemId() == ItemId.바람_투구) {
			if (!SkillsTable.getInstance().spellCheck(objectId,  SkillId.그레이터_헤이스트)) {
				_owner.sendPackets(new S_DelSkill(0, 0, 0, 0, 0, 0, 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), true);
			}
		}
	}
}
